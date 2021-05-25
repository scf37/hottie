package me.scf37.hottie

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.ConcurrentLinkedQueue

import me.scf37.hottie.model.TestClass
import me.scf37.hottie.model.TestClassDependency
import org.scalatest.BeforeAndAfterAll
import org.scalatest.freespec.AnyFreeSpec

import scala.concurrent.Await
import scala.concurrent.duration.Duration

class HottieTest extends AnyFreeSpec with BeforeAndAfterAll{
  val changes = new ConcurrentLinkedQueue[String]()
  val h = Hottie(
    onChange = changes.add
  )

  override def afterAll(): Unit = {
    Await.result(h.close(), Duration.Inf)
  }

  "proxy instance of TestClass with chainedClassLoader" - {

    val src = Paths.get("src/test/scala/me/scf37/hottie/model/TestClass.scala")
    val depSrc = Paths.get("src/test/scala/me/scf37/hottie/model/TestClassDependency.scala")

    val instance: TestClass with HottieProxy = h.newInstance(
      classOf[TestClass],
      src
    )(_.getConstructor(classOf[Int], classOf[String]).newInstance(Int.box(1), "a"))

    val depInstance: TestClassDependency with HottieProxy = h.newInstance(
      classOf[TestClassDependency],
      depSrc
    )(_.getConstructor().newInstance())

    def setPrefix(prefix: String): Unit = {
      replaceInFile(src, "val prefix = \".+\"", "val prefix = \"" + prefix + "\"")
    }

    def setDepPrefix(prefix: String): Unit = {
      replaceInFile(depSrc, "val prefix = \".+\"", "val prefix = \"" + prefix + "\"")
    }

    def waitForPrefix(prefix: String): Unit = {
      waitFor(instance.concat("b").startsWith(prefix))
    }

    def waitForDepPrefix(prefix: String): Unit = {
      waitFor(instance.depPrefix.startsWith(prefix))
    }

    "correctly invokes proxy target from current classpath" in {
      val prefix = new TestClass(0, "").prefix
      assert(instance.concat("b") == prefix + "1ab")
      assert(instance.multiargConcat("b")("c") == prefix + "1abc")
    }

    "proxy target is still from current classpath" in {
      assert(instance.getHottieProxyTarget().isInstanceOf[TestClass])
    }

    "when modified" - {
      val prefix = "p-" + System.nanoTime()

      "recompiles proxy target" in {
        setPrefix(prefix)
        waitForPrefix(prefix)

        assert(instance.concat("b") == prefix + "1ab")
        assert(instance.multiargConcat("b")("c") == prefix + "1abc")

      }

      "proxy target belongs to another classpath now" in {
        assert(!instance.getHottieProxyTarget().isInstanceOf[TestClass])
      }

      "calls onChange with full class name" in {
        assert(changes.contains("me.scf37.hottie.model.TestClass"))
      }
    }

    "when dependency modified" - {
      val originalPrefix = instance.depPrefix
      val depPrefix = "depPrefix-" + System.nanoTime()

      "dependency recompiled" in {
        setDepPrefix(depPrefix)
        waitFor(depInstance.prefix.startsWith(depPrefix))
        assert(depInstance.prefix == depPrefix)
      }

      "but dependency instance in parent is not updated" in {
        setDepPrefix(depPrefix)
        waitForDepPrefix(originalPrefix)
        assert(instance.depPrefix == originalPrefix)
      }

      "and original class modified" -  {
        "parent class recompiled with dependency already on compilation classpath" in {
          setPrefix("p" + System.nanoTime())
          waitForDepPrefix(depPrefix)
          assert(instance.depPrefix == depPrefix)
        }
      }

    }

    "set original prefixes" in {
      setPrefix("prefix-placeholder")
      setDepPrefix("prefix-placeholder")
      waitForPrefix("prefix-placeholder")
      waitForDepPrefix("prefix-placeholder")
      assert(instance.prefix == "prefix-placeholder")
      assert(depInstance.prefix == "prefix-placeholder")
    }

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
