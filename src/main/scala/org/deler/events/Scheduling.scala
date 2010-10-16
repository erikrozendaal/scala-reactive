package org.deler.events

import org.joda.time._
import scala.collection._

trait Scheduler {

  def now: Instant

  def schedule(action: => Unit, at: Instant = now): Subscription
  def schedule(action: => Unit, delay: Duration): Subscription

}

private class ScheduledAction(val time: Instant, val action: () => Unit) extends Ordered[ScheduledAction] {
  def compare(that: ScheduledAction) = this.time.compareTo(that.time)
}

class VirtualScheduler(initialNow: Instant = new Instant(0)) extends Scheduler { self =>

  private var schedule = SortedSet[ScheduledAction]()
  private var _now = initialNow

  def now: Instant = synchronized { _now }

  def schedule(action: => Unit, at: Instant = now): Subscription = synchronized {
    val scheduledAction = new ScheduledAction(at, () => action)
    schedule += scheduledAction
    new Subscription { def close() = self.synchronized { schedule -= scheduledAction } }
  }

  def schedule(action: => Unit, delay: Duration): Subscription = synchronized {
    schedule(action, _now.plus(delay))
  }

  def run() {
    this.takeNext() foreach { scheduled =>
      scheduled.action()
      run()
    }
  }

  def runTo(instant: Instant) {
    this.takeNext() foreach { scheduled =>
      if (scheduled.time isAfter instant) {
    	_now = instant
    	schedule += scheduled
      } else synchronized {
        scheduled.action()
        runTo(instant)
      }
    }
  }

  private def takeNext(): Option[ScheduledAction] = synchronized {
    val action = schedule.firstOption
    if (action.isDefined) {
      schedule = schedule.tail
      if (action.get.time isAfter _now) {
        _now = action.get.time
      }
    }
    action
  }

}
