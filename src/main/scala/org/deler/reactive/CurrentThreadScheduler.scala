package org.deler.reactive

import org.joda.time.Instant

/**
 * Schedules actions to run on the current thread (the thread that owns this instance). Instances of this class must be thread-safe!
 */
class CurrentThreadScheduler extends Scheduler {

  import ThreadLocalOps._

  private val schedule = new ThreadLocal[Schedule]

  def now = new Instant

  override def scheduleAt(at: Instant)(action: => Unit): Subscription = {
    var currentSchedule = schedule.get
    if (currentSchedule != null) {
      currentSchedule.enqueue(at, () => action)
    } else {
      schedule.withValue(new Schedule) { schedule =>
        schedule.enqueue(at, () => action)
        runQueued(schedule)
      }
      NoopSubscription
    }
  }

  private def runQueued(schedule: Schedule) {
    schedule.dequeue match {
      case None =>
      case Some(scheduled) => {
    	val delay = scheduled.time.getMillis - now.getMillis
    	if (delay > 0) {
          Thread.sleep(delay)
        }
        scheduled.action()
        runQueued(schedule)
      }
    }
  }

}
