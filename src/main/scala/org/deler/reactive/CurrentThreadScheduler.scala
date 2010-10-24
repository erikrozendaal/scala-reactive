package org.deler.reactive

import org.joda.time.Instant

/**
 * Schedules actions to run as soon as possible on the calling thread. As soon as possible means:
 *
 * <ol>
 * <li>Immediately if no action is currently execution,
 * <li>or directly after the currently executing action (and any other scheduled actions) has completed.
 * </ol>
 *
 * Actions scheduled for a later time will cause the current thread to sleep. 
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
      schedule.withValue(new Schedule) {
        schedule =>
          schedule.enqueue(at, () => action)
          runQueued(schedule)
      }
      NullSubscription
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
