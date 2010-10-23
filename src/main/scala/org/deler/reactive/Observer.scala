package org.deler.reactive

trait Observer[-T] {
  def onCompleted() {}
  def onError(error: Exception) {}
  def onNext(value: T) {}
}

class Relay[-T](target: Observer[T]) extends Observer[T] {
  private var completed = false

  override def onCompleted() {
    if (completed) return
    completed = true
    target.onCompleted()
  }

  override def onError(error: Exception) {
    if (completed) return
    completed = true
    target.onError(error);
  }

  override def onNext(value: T) {
    if (completed) return
    target.onNext(value)
  }
  
}
