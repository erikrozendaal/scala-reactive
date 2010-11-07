package org.deler.reactive

/**
 * Observers can subscribe to [[org.deler.reactive.Observable]]s and will be notified of zero or more values, optionally
 * followed by an completion or error.
 */
trait Observer[-A] {
  /**
   * Invoked for every value produced by an `Observable`
   */
  def onNext(value: A)

  /**
   * Invoked when an error is produced by an `Observable`. No further notifications will happen.
   */
  def onError(error: Exception)

  /**
   * Invoked when an `Observable` completes. No further notifications will happen.
   */
  def onCompleted()
}
