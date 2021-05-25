package me.scf37.hottie.impl

import dotty.tools.dotc.Main

private[impl] object CompilerBridge:
  def compile(args: Array[String]): Boolean = !Main.process(args).hasErrors

