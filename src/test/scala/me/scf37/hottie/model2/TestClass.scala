package me.scf37.hottie.model2

class TestClass(val dep: TestClassDependency) {
  val prefix = "prefix-placeholder"

  def depPrefix = dep.prefix
}