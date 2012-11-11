package org.deler.reactive.example

import org.deler.reactive.Observable
import org.joda.time.{LocalDateTime, Duration}

object Merge extends App {
  val every200ms = Observable.interval(new Duration(200)).map(n => "timer 1, tick " + n)
  val every300ms = Observable.interval(new Duration(300)).map(n => "timer 2, tick " + n)

  val merged = Observable(every200ms, every300ms).merge.take(10)

  merged subscribe { n =>
    println("Received " + n + " at " + new LocalDateTime().toString("HH:mm:ss.SSS"))
  }

  println("Press enter to stop")
  readLine()
}
