package org.deler.reactive

trait ObservableLike[+T] {

  import ObservableLike._

  def subscribe(observer: Observer[T]): Subscription

  def subscribe(onNext: T => Unit = defaultOnNext, onCompleted: () => Unit = defaultOnCompleted, onError: Exception => Unit = defaultOnError): Subscription = {
    val completed = onCompleted
    val error = onError
    val next = onNext
    this subscribe (new Observer[T] {
      override def onCompleted() = completed()
      override def onError(ex: Exception) = error(ex)
      override def onNext(value: T) = next(value)
    })
  }

}

object ObservableLike {

  def defaultOnCompleted() {}
  def defaultOnError(error: Exception) {
    throw error
  }
  def defaultOnNext[T](value: T) {}

}
