# Hottie
![Build status](https://travis-ci.org/scf37/hottie.svg?branch=master)

Automatic hot reloading of Scala classes

## What is this?
Hottie is small library to recompile, reload and reinstantiate individual Scala classes on the fly without application restart.

It works by creating dynamic class proxies which watches on scala source files, reloading them when necessary and switching to new implementation.

I use Hottie to reload Scalatags templates but it surely can be used more widely. 

## Usage
### Update build.sbt
```
resolvers += "Scf37" at "https://dl.bintray.com/scf37/maven/"
libraryDependencies += "me.scf37.hottie" %% "hottie" % "1.0.1"
```
### Write class to be watched
[Hello.scala](https://github.com/scf37/hottie/blob/master/src/test/scala/me/scf37/hottie/demo/Hello.scala)
```scala
package me.scf37.hottie.demo

class Hello {
  def sayHello(): String = "hello, world!"
}
```
### Write watcher code
[Main.scala](https://github.com/scf37/hottie/blob/master/src/test/scala/me/scf37/hottie/demo/Main.scala)
```scala
package me.scf37.hottie.demo

import java.nio.file.Paths
import me.scf37.hottie.Hottie

object Main {
  // handler to be called when reload occurs
  @volatile var onChange: () =>  Unit = () => ()

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
```
### See it working
Run Main and change `sayHello` function in `Hello.scala` in your IDE. Changes will be recompiled and new value of `sayHello` function will be printed to console.

