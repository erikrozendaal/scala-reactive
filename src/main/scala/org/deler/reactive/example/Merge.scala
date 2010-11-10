package org.deler.reactive.example

import org.deler.reactive.Observable
import org.joda.time.{LocalDateTime, Duration}

object Merge extends Application {
  val every200ms = Observable.interval(new Duration(200))
  val every300ms = Observable.interval(new Duration(300))

  val merged = Observable(every200ms, every300ms).flatten.take(10)

  merged subscribe { n =>
    println("Received tick " + n + " at " + new LocalDateTime().toString("HH:mm:ss.SSS"))
  }
}
