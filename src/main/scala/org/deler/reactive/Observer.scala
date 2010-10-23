package org.deler.reactive

/**
 * Observers can subscribe to [[org.deler.reactive.Observable]]s and will be notified of zero or more values, optionally
 * followed by an completion or error.
 */
trait Observer[-T] {
  /**
   * Invoked for every value produced by an <code>Observable</code>
   */
  def onNext(value: T) {}

  /**
   * Invoked when an error is produced by an <code>Observable</code>. No further notifications will happen.
   */
  def onError(error: Exception) {}

  /**
   * Invoked when an <code>Observable</code> completes. No further notifications will happen.
   */
  def onCompleted() {}
}
