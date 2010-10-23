package org.deler.reactive

sealed abstract class Notification[+T]
case object OnCompleted extends Notification[Nothing]
case class OnError(error: Exception) extends Notification[Nothing]
case class OnNext[T](value: T) extends Notification[T]

