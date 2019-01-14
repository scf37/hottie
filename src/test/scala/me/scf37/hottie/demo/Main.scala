package me.scf37.hottie.demo

import java.nio.file.Paths
import me.scf37.hottie.Hottie

object Main {
  // handler to be called when reload occurs
  @volatile var onChange: () =>  Unit = () => Unit

  // create new Hottie instance with reload handler
  val h: Hottie = Hottie(_ => onChange())

  def main(args: Array[String]): Unit = {
    // create instance of watched class
    val hello: Hello = h.newInstance[Hello](
      classOf[Hello],
      // source file to watch
      Paths.get("src/test/scala/me/scf37/hottie/demo/Hello.scala")
    ) { cls =>
      // create Hello instance by Class[Hello]
      cls.newInstance()
    }

    // say hello
    println(hello.sayHello())

    // when reload occurs, say hello again
    onChange = () => println(hello.sayHello())

    Thread.sleep(Long.MaxValue)
  }
}
