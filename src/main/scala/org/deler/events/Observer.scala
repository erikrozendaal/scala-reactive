package org.deler.events

trait Observer[-T] {
  def onCompleted() {}
  def onError(error: Exception) {}
  def onNext(value: T) {}
}
