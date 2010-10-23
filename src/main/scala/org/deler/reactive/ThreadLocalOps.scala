package org.deler.reactive

class ThreadLocalOps[T](val threadLocal: ThreadLocal[T]) {

  def withValue[U](value: T)(callback: T => U): U = {
    try {
      threadLocal.set(value)
      callback(value)
    } finally {
      threadLocal.remove()
    }
  }

}

object ThreadLocalOps {

  implicit def threadLocalOps[T](threadLocal: ThreadLocal[T]) = new ThreadLocalOps(threadLocal)

}
