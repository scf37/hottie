package me.scf37.hottie.impl

import scala.tools.nsc.Main

private[impl] object CompilerBridge {
  def compile(args: Array[String]): Boolean = Main.process(args)
}

