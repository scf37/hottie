package me.scf37.hottie.model

class TestClass(a: Int, b: String) {
  //ensure reloader compiler takes java cp in account
  private val dep = new me.scf37.hottie.model.TestClassDependency
  private val dep2 = new java.util.Date()
  private val dep3 = classOf[org.scalatest.FunSuite]
  val nowarns: String = "" + dep2 + dep3

  val prefix = "prefix-placeholder"

  def concat(c: String) = prefix + a.toString + b + c
  def multiargConcat(c: String)(d: String) = concat(c) + d

  def depPrefix = dep.prefix
}