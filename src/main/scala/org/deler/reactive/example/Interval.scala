package org.deler.reactive.example

import org.deler.reactive.{Scheduler, Observable}
import org.joda.time.{LocalDateTime, Duration}

object Interval extends App {
  val observable = Observable.interval(new Duration(333))

  observable subscribe { n =>
    println("Received tick " + n + " at " + new LocalDateTime().toString("HH:mm:ss.SSS"))
  }

  println("Press enter to stop")
  readLine()
}
