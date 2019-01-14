package me.scf37.hottie

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.ConcurrentLinkedQueue

import org.scalatest.FreeSpec
import model3._
import org.scalatest.BeforeAndAfterAll

import scala.concurrent.Await
import scala.concurrent.duration.Duration

class HottieTest3 extends FreeSpec with BeforeAndAfterAll {
  val changes = new ConcurrentLinkedQueue[String]()
  val h = Hottie(
    onChange = changes.add
  )

  def path(name: String) = Paths.get(s"src/test/scala/me/scf37/hottie/model3/$name.scala")
  def atClassLoader(at: Class[_], to: Class[_]) = at.getClassLoader.loadClass(to.getName).asInstanceOf[Class[AnyRef]]

  def makeC[T <: AnyRef](cls: Class[T]): T = h.newInstance[T](cls, path("C"))(_.newInstance())

  def makeB[T <: AnyRef](cls: Class[T]): T = h.newInstance[T](cls, path("B")) { cls =>
    cls.getConstructors.head.newInstance(makeC[AnyRef](atClassLoader(cls, classOf[C]))).asInstanceOf[Object]
  }

  def makeA[T <: AnyRef](cls: Class[T]): T = h.newInstance[T](cls, path("A")) { cls =>
    cls.getConstructors.head.newInstance(makeB[AnyRef](atClassLoader(cls, classOf[B]))).asInstanceOf[Object]
  }

  val c: C = makeC(classOf[C])
  val b: B = makeB(classOf[B])
  val a: A = makeA(classOf[A])

  override def afterAll(): Unit = {
    Await.result(h.close(), Duration.Inf)
    set("A", "a")
    set("B", "b")
    set("C", "c")
  }

  "modification of dependencies implementation works" - {
    "initial state as expected" in {
      assert(a.f == "abc")
      assert(b.f == "bc")
      assert(c.f == "c")
    }
    "changing dependency reflects on dependants as well" in {
      set("C", "C")
      waitFor(c.f == "C")
      assert(a.f == "abC")
      assert(b.f == "bC")
      assert(c.f == "C")
    }

    "changing dependency reflects on dependants as well (2)" in {
      set("B", "B")
      waitFor(b.f == "BC")
      assert(a.f == "aBC")
      assert(b.f == "BC")
      assert(c.f == "C")
    }

    "recompilation of dependant works with new dependencies" in {
      set("A", "A")
      waitFor(a.f == "ABC")
      assert(a.f == "ABC")
      assert(b.f == "BC")
      assert(c.f == "C")
    }

    "recompilation of dependencies works with recompiled dependant (1)" in {
      set("A", "1")
      waitFor(a.f == "1BC")
      assert(a.f == "1BC")
      assert(b.f == "BC")
      assert(c.f == "C")
    }

    "recompilation of dependencies works with recompiled dependant (2)" in {
      set("B", "2")
      waitFor(a.f == "12C")
      assert(a.f == "12C")
      assert(b.f == "2C")
      assert(c.f == "C")
    }

    "recompilation of dependencies works with recompiled dependant (3)" in {
      set("C", "3")
      waitFor(a.f == "123")
      assert(a.f == "123")
      assert(b.f == "23")
      assert(c.f == "3")
    }
  }

  "modification of dependencies API works" - {
    "adding function to C works" in {
      set("A", "a")
      set("B", "b")
      setExpr("C", "\"C\";val x=0")
      waitFor(c.f == "C")
      waitFor(a.f == "abC")
    }

    "calling added function from B works" in {
      setExpr("B", "c.x")
      waitFor(b.f == "0C")
      assert(b.f == "0C")
      assert(a.f == "a0C")
      assert(c.f == "C")
    }
  }

  def set(where: String, value: String): Unit = {
    setExpr(where, "\"" + value + "\"")
  }

  def setExpr(where: String, expr: String): Unit = {
    replaceInFile(path(where), "def f: String = .+/\\*eoe\\*/", "def f: String = " + expr + "/*eoe*/")
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
