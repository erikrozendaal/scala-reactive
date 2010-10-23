package org.deler.reactive

/**
 * Represents a notification from an [[org.deler.reactive.Observable]] to an [[org.deler.reactive.Observer]].
 */
sealed abstract class Notification[+T]

/**
 * Notification indicating that the notifying [[org.deler.reactive.Observable]] has completed.
 */
case object OnCompleted extends Notification[Nothing]

/**
 * Notification indicating that the notifying [[org.deler.reactive.Observable]] has terminated with an error.
 */
case class OnError(error: Exception) extends Notification[Nothing]

/**
 * Notification indicating the next value of an [[org.deler.reactive.Observable]].
 */
case class OnNext[T](value: T) extends Notification[T]
