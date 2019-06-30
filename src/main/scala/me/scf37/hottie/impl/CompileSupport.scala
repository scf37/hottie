package me.scf37.hottie.impl

import java.net.URL
import java.net.URLClassLoader
import java.nio.file.Path

import scala.tools.nsc.Main
import scala.util.Try
import scala.util.control.NoStackTrace

private[hottie] object CompileSupport {

  /**
    * Load class using given classloader from given classPath directories.
    * Search order:
    * 1. classPath directories in order provided
    * 2. classloader cl
    *
    * @param cl base classloader
    * @param classPath list of directories to search for class files beforehand
    * @param className class name to load
    * @return loaded class
    */
  def load(cl: ClassLoader, classPath: Seq[Path], className: String): Class[Object] = {

    val compiledCl = new URLClassLoader(classPath.map(p => new URL("file://" + p.toString + "/")).toArray, cl) {
      override def loadClass(name: String, resolve: Boolean): Class[_] = {
        Option(findLoadedClass(name)).orElse {
          Try(findClass(name)).toOption
        }.getOrElse {
          super.loadClass(name, resolve)
        }
      }
    }

    compiledCl.loadClass(className).asInstanceOf[Class[Object]]
  }


  /**
    * Compile source(s) to target directory
    *
    * @param sourceFile .scala source that contains class to load
    * @param className full class name
    * @param dependencies .scala sources to compile together with sourceFile
    * @param classPath list of directories to search for class files beforehand
    * @param targetDir where to put compiled bytecode
    * @param logger how to log errors
    * @return loaded instance. May not be cast to cl.getClass(className)
    */
  def compile(
    sourceFile: Path,
    className: String,
    dependencies: Set[Path],
    classPath: Seq[Path],
    targetDir: Path,
    logger: Throwable => Unit
  ): Boolean = {

    // scalac uses java.class.path system property to extract classpath for -usejavacp
    // Generally that works but in case of SBT it is more complicated - SBT runs tests with empty classpath
    // and scalac fails as it can't find nor scala library nor user classes
    // So, for SBT java classpath needs to be provided manually from SBT via 'sbt-classpath'
    // property populated by following SBT task:
    //
    // lazy val sbtClasspath = taskKey[Unit]("sbtClasspath")
    // sbtClasspath := {
    //   val cp = (fullClasspath in Test).value.map(x => x.data.getAbsolutePath).mkString(":")
    //   System.setProperty("sbt-classpath", cp)
    // }
    //
    // test := Def.sequential(sbtClasspath, test in Test).value
    //
    // Scala 2.12.4 is the last version that works with running this via sbt
    // See https://github.com/scala/bug/issues/10058
    //
    // UPDATE: Seems to be fixed in 2.13.0

    val systemCp = Option(System.getProperty("sbt-classpath")).getOrElse(System.getProperty("java.class.path"))

    val cp = (classPath.map(_.toString) :+ systemCp).mkString(":")
    val args = Array(
      "-classpath", cp,
      "-d", targetDir.toString) ++ Seq(
      sourceFile.toAbsolutePath.toString
    ) ++ dependencies.map(_.toAbsolutePath.toString)

    val r = Main.process(args)

    if (!r) {
      logger(new RuntimeException(s"Compilation failed for $className") with NoStackTrace)
    }

    r

  }
}