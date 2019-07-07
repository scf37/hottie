package me.scf37.hottie.impl

import java.lang.reflect.Method

import javassist.util.proxy.MethodHandler
import javassist.util.proxy.ProxyFactory
import javassist.util.proxy.ProxyObject
import me.scf37.hottie.HottieProxy
import org.objenesis.ObjenesisStd

/**
  * Creates Proxy class with replaceable instance to proxy to
  *
  * @param cls class to use
  * @param delegate0 initial delegate instance, can be changed later with setDelegate
  * @tparam T proxied type
  */
private[hottie] class ReloaderProxy[T <: AnyRef](
  cls: Class[T],
  delegate0: AnyRef
) {

  @volatile
  private[this] var delegate: AnyRef = delegate0

  private[this] val proxy = ReloaderProxy.generateProxy(cls) { (method, args) =>
    val methodSig = this.methodSig(method)

    if (methodSig == "getHottieProxyTarget ") {
      delegate
    } else {

      val target = if (doNotDelegate(methodSig)) {
        getProxy()
      } else delegate

      target.getClass.getMethod(method.getName, method.getParameterTypes: _*).invoke(target, args: _*)
    }
  }

  // e.g. "hashCode " or "equals java.lang.Object " or "wait long int "
  private[this] def methodSig(m: Method): String = {
    val sb = new StringBuilder(m.getName)
    sb.append(' ')
    m.getParameterTypes.foreach(cls => sb.append(cls.getName).append(' '))
    sb.toString()
  }

  private[this] def doNotDelegate(methodSig: String): Boolean = {
    ReloaderProxy.shallNotPass.contains(methodSig)
  }

  /**
    * Set delegate to pass all proxy calls to
    *
    * @param obj
    */
  def setDelegate(obj: AnyRef): Unit = this.delegate = obj

  /**
    *
    * @return proxy that extends T but passes all calls to `delegate`
    */
  def getProxy(): T with HottieProxy = proxy.asInstanceOf[T with HottieProxy]
}

private[hottie] object ReloaderProxy {
  private val objenesis = new ObjenesisStd

  private def generateProxy[T](cls: Class[T])
    (handler: (Method, Array[AnyRef]) => AnyRef): T = {

    val f = new ProxyFactory

    f.setSuperclass(cls)

    f.setInterfaces(Array(classOf[HottieProxy]))

    val c = f.createClass()

    val obj = objenesis.newInstance(c).asInstanceOf[ProxyObject]

    obj.setHandler(new MethodHandler {
      override def invoke(self: scala.Any, thisMethod: Method, proceed: Method, args: Array[AnyRef]): AnyRef =
        handler(thisMethod, args)
    })

    obj.asInstanceOf[T]
  }
  // java.lang.Object methods should not be delegated
  // except for equals/hashCode/toString
  private val shallNotPass = Set(
    "getClass ",
    "clone ",
    "notify ",
    "notifyAll ",
    "wait ",
    "wait long ",
    "wait long int ",
    "finalize "
  )
}