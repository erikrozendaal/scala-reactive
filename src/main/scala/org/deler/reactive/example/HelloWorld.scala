package org.deler.reactive.example

import org.deler.reactive.Observable

object HelloWorld extends Application {
  val observable = Observable("hello", " ", "world", "\n")

  observable.subscribe(print _)
}
