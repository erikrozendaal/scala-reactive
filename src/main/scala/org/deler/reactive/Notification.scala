package org.deler.reactive

/**
 * Represents a notification from an [[org.deler.reactive.Observable]] to an [[org.deler.reactive.Observer]].
 */
sealed abstract class Notification[+A] {
  def accept(observer: Observer[A])
}

/**
 * Notification indicating that the notifying [[org.deler.reactive.Observable]] has completed.
 */
case object OnCompleted extends Notification[Nothing] {
  def accept(observer: Observer[Nothing]) {
    observer.onCompleted()
  }
}

/**
 * Notification indicating that the notifying [[org.deler.reactive.Observable]] has terminated with an error.
 */
case class OnError(error: Exception) extends Notification[Nothing] {
  def accept(observer: Observer[Nothing]) {
    observer.onError(error)
  }
}

/**
 * Notification indicating the next value of an [[org.deler.reactive.Observable]].
 */
case class OnNext[A](value: A) extends Notification[A] {
  def accept(observer: Observer[A]) {
    observer.onNext(value)
  }
}
