package me.scf37.hottie

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.ConcurrentLinkedQueue

import me.scf37.hottie.model2.TestClass
import me.scf37.hottie.model2.TestClassDependency
import org.scalatest.BeforeAndAfterAll
import org.scalatest.freespec.AnyFreeSpec

import scala.collection.mutable
import scala.concurrent.Await
import scala.concurrent.duration.Duration

class HottieTest2 extends AnyFreeSpec with BeforeAndAfterAll {
  val changes = new ConcurrentLinkedQueue[String]()
  val h = Hottie(
    onChange = changes.add
  )

  override def afterAll(): Unit = {
    Await.result(h.close(), Duration.Inf)
  }

  val src = Paths.get("src/test/scala/me/scf37/hottie/model2/TestClass.scala")
  val depSrc = Paths.get("src/test/scala/me/scf37/hottie/model2/TestClassDependency.scala")

  "aggregated proxies" - {

    val depInstanceCache = mutable.Map.empty[Class[_], AnyRef]
    def getDepInstance(cls: Class[_]) = synchronized {
      depInstanceCache.getOrElseUpdate(cls, h.newInstance[AnyRef](cls, depSrc)
        (_.getConstructor().newInstance()))
    }


    val depInstance: TestClassDependency with HottieProxy = h.newInstance(
      classOf[TestClassDependency],
      depSrc
    )(_.getConstructor().newInstance())


    val instance: TestClass with HottieProxy = h.newInstance(
      classOf[TestClass],
      src
    ) { cls =>
      val depCls = cls.getClassLoader.loadClass(classOf[TestClassDependency].getName)
      val depCls2 = cls.getMethod("dep").getReturnType
      // classloader should return correct type for parent class
      require(depCls eq depCls2)

      val depInstance = getDepInstance(depCls)
      // created instance is really of requested type
      require(depCls.isAssignableFrom(depInstance.getClass))

      try {
        cls.getConstructor(depCls).newInstance(depInstance)
      } catch {
        case e: Throwable =>
          e.printStackTrace()
          throw e
      }
    }



    "correctly invokes proxy target from current classpath" in {
      assert(depInstance.prefix == instance.depPrefix)
    }

    "when dep reloads, parent reflects reload" in {
      val depPrefix = "dep" + System.nanoTime()
      setDepPrefix(depPrefix)
      waitFor(depInstance.prefix == depPrefix)
      assert(depInstance.prefix == depPrefix)

      assert(instance.depPrefix == depPrefix)
    }

    "when parent reloads, reloaded dep is still available" in {
      val depPrefix = "dep" + System.nanoTime()
      val prefix = "prefix" + System.nanoTime()

      setDepPrefix(depPrefix)
      waitFor(depInstance.prefix == depPrefix)
      assert(depInstance.prefix == depPrefix)
      assert(instance.depPrefix == depPrefix)

      setPrefix(prefix)
      waitFor(instance.prefix == prefix)
      assert(instance.prefix == prefix)
      assert(instance.depPrefix == depPrefix)
    }

    "when parent reloads, reloaded dep is still available (2)" in {
      val depPrefix = "dep" + System.nanoTime()
      val prefix = "prefix" + System.nanoTime()
      val depPrefix2 = "dep2" + System.nanoTime()
      val prefix2 = "prefix2" + System.nanoTime()

      setDepPrefix(depPrefix)
      waitFor(depInstance.prefix == depPrefix)
      assert(depInstance.prefix == depPrefix)
      assert(instance.depPrefix == depPrefix)

      setDepPrefix(depPrefix2)
      waitFor(depInstance.prefix == depPrefix2)
      assert(depInstance.prefix == depPrefix2)
      assert(instance.depPrefix == depPrefix2)

      setPrefix(prefix)
      waitFor(instance.prefix == prefix)
      assert(instance.prefix == prefix)
      assert(instance.depPrefix == depPrefix2)

      setPrefix(prefix2)
      waitFor(instance.prefix == prefix2)
      assert(instance.prefix == prefix2)
      assert(instance.depPrefix == depPrefix2)
    }

    "set original prefixes" in {
      setPrefix("prefix-placeholder")
      setDepPrefix("prefix-placeholder")
      waitFor(instance.prefix =="prefix-placeholder")
      waitFor(depInstance.prefix == "prefix-placeholder")
      assert(instance.prefix == "prefix-placeholder")
      assert(depInstance.prefix == "prefix-placeholder")

    }

  }

  def setPrefix(prefix: String): Unit = {
    replaceInFile(src, "val prefix = \".+\"", "val prefix = \"" + prefix + "\"")
  }

  def setDepPrefix(prefix: String): Unit = {
    replaceInFile(depSrc, "val prefix = \".+\"", "val prefix = \"" + prefix + "\"")
  }

  private def waitFor(cnd: => Boolean): Unit = {
    val t = System.currentTimeMillis()
    while (System.currentTimeMillis() < t + 10 * 1000) {
      if (cnd) return
      Thread.sleep(200)
    }
  }

  private def replaceInFile(f: Path, what: String, replacement: String) = {
    val s = new String(Files.readAllBytes(f), "utf-8")
    val ss = s.replaceAll(what, replacement)
    Files.write(f, ss.getBytes("utf-8"))
  }

}
