package org.deler.reactive

/**
 * An observable can be subscribed to by an [[org.deler.reactive.Observer]]. Observables produce zero or more values
 * using <code>onNext</code> optionally terminated by either a call to <code>onCompleted</code> or <code>onError</code>.   
 */
trait ObservableLike[+T] {
  import ObservableLike._

  /**
   * Subscribes <code>observer</code>.
   */
  def subscribe(observer: Observer[T]): Subscription

  /**
   * Subscribes <code>onNext</code> to every value produced by this Observable.
   */
  def subscribe(onNext: T => Unit): Subscription = subscribe(onNext, defaultOnError, defaultOnCompleted)

  /**
   * Subscribes <code>onNext</code> and <code>onError</code> to this Observable.
   */
  def subscribe(onNext: T => Unit, onError: Exception => Unit): Subscription = subscribe(onNext, onError, defaultOnCompleted)

  /**
   * Subscribes <code>onNext</code> and <code>onCompleted</code> to this Observable.
   */
  def subscribe(onNext: T => Unit, onCompleted: () => Unit): Subscription = subscribe(onNext, defaultOnError, onCompleted)

  /**
   * Subscribes <code>onNext</code>, <code>onError</code>, and <code>onCompleted</code> to this Observable.
   */
  def subscribe(onNext: T => Unit, onError: Exception => Unit, onCompleted: () => Unit): Subscription = {
    val completedCallback = onCompleted
    val errorCallback = onError
    val nextCallback = onNext
    this subscribe (new Observer[T] {
      override def onCompleted() = completedCallback()

      override def onError(error: Exception) = errorCallback(error)

      override def onNext(value: T) = nextCallback(value)
    })
  }

}

object ObservableLike {
  /**
   * The default <code>onNext</code> handler does nothing.
   */
  def defaultOnNext[T](value: T) {}

  /**
   * The default <code>onError</code> handler throws the <code>error</code>.
   */
  def defaultOnError(error: Exception) {
    throw error
  }

  /**
   * The default <code>onCompleted</code> handler does nothing.
   */
  def defaultOnCompleted() {}

}
