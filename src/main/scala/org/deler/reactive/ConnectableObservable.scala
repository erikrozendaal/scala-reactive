package org.deler.reactive

trait ConnectableObservable[+A] extends Observable[A] {

  /**
   * Connects this observable sequence.
   *
   * @return a closeable that can be used to disconnect this observable sequence. 
   */
  def connect(): Closeable

}

private case class PublishConnectableObservable[A](source: ConformingObservable[A], scheduler: Scheduler = Scheduler.immediate)
        extends ScheduledDispatcher[A](scheduler) with ConnectableObservable[A] with ConformingObservable[A] {
  override def connect: Closeable = source.subscribe(this)
}
