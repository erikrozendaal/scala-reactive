package org.deler.events

import org.joda.time._
import scala.collection._

trait Scheduler {
  def now: Instant

  def schedule(action: => Unit): Subscription = schedule(action, now)
  def schedule(action: => Unit, at: Instant): Subscription = schedule(action, new Duration(now, at))
  def schedule(action: => Unit, delay: Duration): Subscription = schedule(action, now.plus(delay))
}

object Scheduler {

  protected[events] var _currentThread: ThreadLocal[CurrentThreadScheduler] = new ThreadLocal

  val immediate: Scheduler = new ImmediateScheduler

  def currentThread: Scheduler = {
    val result = _currentThread.get
    if (result == null)
      throw new IllegalStateException("no CurrentThreadScheduler started on this thread: " + Thread.currentThread)
    result
  }
}

/**
 * Scheduler that invokes the specified action immediately. Actions scheduled for the future will be block
 * the caller until the scheduled action has run.
 */
class ImmediateScheduler extends Scheduler {

  def now = new Instant(DateTimeUtils.currentTimeMillis)

  override def schedule(action: => Unit): Subscription = {
    action
    Observable.noopSubscription
  }

  override def schedule(action: => Unit, delay: Duration): Subscription = {
    if (delay.getMillis > 0) {
      Thread.sleep(delay.getMillis)
    }
    schedule(action)
  }

}

private class ScheduledAction(val time: Instant, val action: () => Unit) extends Ordered[ScheduledAction] {
  def compare(that: ScheduledAction) = this.time.compareTo(that.time)
}

private class Schedule { self =>

  private var schedule = SortedSet[ScheduledAction]()

  def enqueue(action: ScheduledAction): Subscription = self.synchronized {
    schedule += action
    new Subscription { def close() = self.synchronized { schedule -= action } }
  }

  def dequeue: Option[ScheduledAction] = self.synchronized {
    if (schedule.isEmpty) {
      None
    } else {
      val result = schedule.head
      schedule = schedule.tail
      Some(result)
    }
  }

  def dequeue(noLaterThan: Instant): Option[ScheduledAction] = self.synchronized {
    if (schedule.isEmpty || (schedule.head.time isAfter noLaterThan)) {
      None
    } else {
      dequeue
    }
  }
}

/**
 * Schedules actions to run on the current thread (the thread that owns this instance). Instances of this class must be thread-safe!
 */
private class CurrentThreadScheduler extends Scheduler { self =>

  private var schedule = new Schedule

  def now = new Instant(DateTimeUtils.currentTimeMillis)

  override def schedule(action: => Unit, at: Instant): Subscription = {
    schedule enqueue (new ScheduledAction(at, () => action))
  }

  def run() {
    schedule.dequeue match {
      case None =>
      case Some(scheduled) => {
        if (scheduled.time isAfter now) {
          Thread.sleep(scheduled.time.getMillis - now.getMillis)
        }
        scheduled.action()
        run()
      }
    }

  }

}

object CurrentThreadScheduler {
  def runOnCurrentThread(action: Scheduler => Unit) {
    val scheduler = new CurrentThreadScheduler
    try {
      Scheduler._currentThread.set(scheduler)
      action(scheduler)
      scheduler.run()
    } finally {
      Scheduler._currentThread.remove()
    }
  }
}

class VirtualScheduler(initialNow: Instant = new Instant(0)) extends Scheduler { self =>

  private var schedule = new Schedule
  private var _now = initialNow

  def now: Instant = _now

  override def schedule(action: => Unit, at: Instant): Subscription = {
    schedule enqueue (new ScheduledAction(at, () => action))
  }

  def run() {
    schedule.dequeue match {
      case None =>
      case Some(scheduled) => {
        if (scheduled.time isAfter _now) {
          _now = scheduled.time
        }
        scheduled.action()
        run()
      }
    }
  }

  def runTo(instant: Instant) {
    schedule.dequeue(instant) match {
      case None => _now = instant
      case Some(scheduled) => {
        if (scheduled.time isAfter _now) {
          _now = scheduled.time
        }
        scheduled.action()
        runTo(instant)
      }
    }
  }

}
