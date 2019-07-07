package me.scf37.hottie

import java.nio.file.Files
import java.nio.file.Path

import me.scf37.filewatch.ChangeEvent
import me.scf37.filewatch.FileWatcher
import me.scf37.filewatch.FileWatcherEvent
import me.scf37.hottie.impl.CompileSupport
import me.scf37.hottie.impl.ReloaderProxy
import me.scf37.hottie.impl.WatchEventDedup

import scala.collection.mutable
import scala.concurrent.Future

/**
  * Hottie working instance. It can be used to create proxied classes with hot reload capability by calling
  *   [[newInstance()]].
  *
  * To get an instance of this interface, use `apply` method on `Hottie` object.
  *
  * Requirements for classes being proxied:
  * - No subclasses - they won't be affected by hot reload
  * - No mutable state - it will be reset on reload
  *
  * Implementation starts new threads for operation, call [[close()]] to properly clean up.
  */
trait Hottie {
  /**
    * Create proxy of specified `className` which delegates all methods to instance taken from `scalaSourceName`.
    *
    * Every time change of `scalaSourceName` is detected, it is recompiled and used by returned proxy.
    *
    * When proxied class takes other proxied classes as constructor parameter,
    * care must be taken to pass instance of correct type as reloading creates multiple different classes of the same name.
    * Simplest way to get dependency of correct type is to call [[newInstance]] for dependency as well. See HottieTest3 for details.
    *
    * @param cls class for proxy to be compatible to
    * @param scalaSourceFile .scala file that contains implementation of that class
    * @param instanceFactory create instance by class, using appropriate constructor and parameter types
    * @return proxy that can be cast to `cls`
    */
  def newInstance[T <: AnyRef](
    cls: Class[_],
    scalaSourceFile: Path
  )(instanceFactory: Class[Object] => Object): T with HottieProxy

  def close(): Future[Unit]
}

trait HottieProxy {
  /**
    *
    * @return proxy target object, the same obtained via `instanceFactory`
    */
  def getHottieProxyTarget(): AnyRef
}


object Hottie {

  /**
    * Create new instance of [[Hottie]]. Single Hottie instance uses the same file watch service, classloader
    *   and onChange handler.
    *
    * @param onChange callback to be called after class successfully reloaded. Argument is full class name
    * @param logger logger for compilation errors
    * @return Hottie instance
    */
  def apply(
    onChange: String => Unit,
    logger: Throwable => Unit = _.printStackTrace()
  ): Hottie = {

    new HottieImpl(
      onChange = onChange,
      logger = logger
    )
  }
}

private[hottie] class HottieImpl (
  onChange: String => Unit,
  logger: Throwable => Unit
) extends Hottie {
  private[this] val watch = FileWatcher(
    listener = new WatchEventDedup[Path](watchListener, deduplicateEvents, logger),
    onError = e => logger(e)
  )

  private[this] val watchMap = mutable.Map.empty[Path, ReloadHandler]
  private[this] val latestClass = mutable.Map.empty[String, Class[Object]]

  private[this] var compilerClasspath = List.empty[Path]

  private[this] val baseDirectory = Files.createTempDirectory("hottie")
  private[this] var workingDirectoryNum = 0

  override def close(): Future[Unit] = {
    watch.close()
  }

  override def newInstance[T <: AnyRef](
    cls: Class[_],
    scalaSourceFile: Path
  )(instanceFactory: Class[Object] => Object): T with HottieProxy = synchronized {
    val delegateCls = latestClass.getOrElse(cls.getName, cls.asInstanceOf[Class[Object]])

    val proxy = new ReloaderProxy[T](
      // it is PROXY type, not delegate - must be compatible with other user classes
      cls = cls.asInstanceOf[Class[T]],
      delegate0 = instanceFactory(delegateCls)
    )

    // no need to watch multiple times for the same file
    if (!watchMap.contains(scalaSourceFile)) {
      watch.watch(scalaSourceFile.getParent, _.getFileName == scalaSourceFile.getFileName)
    }

    val h = watchMap.getOrElse(scalaSourceFile, new ReloadHandler(cls.getName, scalaSourceFile, cls.getClassLoader))

    watchMap.put(scalaSourceFile, h.addProxy(proxy, instanceFactory))

    proxy.getProxy()
  }

  private[this] def invokeOnChange(className: String): Unit = {
    try {
      onChange(className)
    } catch {
      case e: Throwable => logger(e)
    }
  }

  private[this] def deduplicateEvents(events: Seq[FileWatcherEvent]): Seq[Path] = {
    events.flatMap {
      case ChangeEvent(path) => Some(path)
      case _ => None
    }.distinct
  }

  private[this] def watchListener(path: Path): Unit = {
    synchronized(watchMap.get(path)).foreach(_())
  }

  private class ReloadHandler(
    className: String,
    scalaSourceFile: Path,
    cl: ClassLoader,
    proxies: List[(ReloaderProxy[_], Class[Object] => Object)] = Nil
  ) {

    def addProxy[T <: AnyRef](proxy: ReloaderProxy[T], newInstance: Class[Object] => Object): ReloadHandler = {
      new ReloadHandler(className, scalaSourceFile, cl,
        (proxy, newInstance) :: proxies
      )
    }
    def apply(): Unit = {
      val cls: Class[Object] = HottieImpl.this.synchronized {

        val targetDir = newWorkingDirectory()
        CompileSupport.compile(
          sourceFile = scalaSourceFile,
          className = className,
          classPath = compilerClasspath,
          dependencies = Set.empty,
          targetDir = targetDir,
          logger = logger
        )

        val cls = CompileSupport.load(
          cl = cl,
          classPath = targetDir :: compilerClasspath,
          className = className
        )

        latestClass += className -> cls

        compilerClasspath = targetDir :: compilerClasspath

        cls
      }

      proxies.foreach { case (proxy, newInstance) =>
        try {
          proxy.setDelegate(newInstance(cls))
        } catch {
          case t: Throwable => logger(t)
        }
      }

      invokeOnChange(className)

    }
  }

  private def newWorkingDirectory(): Path = synchronized {
    val p = baseDirectory.resolve(workingDirectoryNum.toString)
    Files.createDirectories(p)
    workingDirectoryNum += 1
    p
  }

}

