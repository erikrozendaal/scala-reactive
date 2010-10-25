package org.deler.reactive

import org.joda.time._
import org.junit.runner.RunWith
import org.specs._
import org.specs.mock.Mockito
import org.specs.runner.{JUnitSuiteRunner, JUnit}
import org.mockito.Matchers._
import scala.collection._

@RunWith(classOf[JUnitSuiteRunner])
class SchedulingTest extends Specification with JUnit with Mockito {
  val INITIAL = new Instant(100)

  val subject = new VirtualScheduler(INITIAL)

  var count = 0

  def action(expectedTime: Instant = INITIAL) {
    subject.now must be equalTo expectedTime
    count += 1
  }

  "virtual schedule" should {
    "not run an action when it is scheduled" in {
      subject schedule action()

      count must be equalTo 0
    }
    "run scheduled action" in {
      subject schedule action()

      subject.run

      count must be equalTo 1
    }
    "run scheduled action at specified time" in {
      subject.scheduleAfter(new Duration(1000L)) {action(INITIAL.plus(1000))}

      subject.run

      subject.now must be equalTo INITIAL.plus(1000)
    }
    "never take the clock backwards" in {
      subject.scheduleAt(INITIAL minus 1000) {action(INITIAL)}

      subject.run

      subject.now must be equalTo INITIAL
    }
    "run actions in scheduled ordered" in {
      subject.scheduleAfter(new Duration(2000L)) {action(INITIAL.plus(2000))}
      subject.scheduleAfter(new Duration(1000L)) {action(INITIAL.plus(1000))}

      subject.run

      count must be equalTo 2
      subject.now must be equalTo INITIAL.plus(2000)
    }
    "run actions that are scheduled by other actions" in {
      subject schedule {
        subject.scheduleAfter(new Duration(1000L)) {action(INITIAL.plus(1000))}
      }

      subject.run

      count must be equalTo 1
      subject.now must be equalTo INITIAL.plus(1000)
    }
    "run actions upto the specified instant (exclusive)" in {
      subject.scheduleAfter(new Duration(1000L)) {action(INITIAL.plus(1000))}

      subject.runTo(INITIAL.plus(1000))

      count must be equalTo 0
      subject.now must be equalTo INITIAL.plus(1000)
    }
    "not run actions after the specified instant" in {
      subject.scheduleAfter(new Duration(2000L)) {action(INITIAL.plus(2000))}
      subject.scheduleAfter(new Duration(1000L)) {action(INITIAL.plus(1000))}

      subject.runTo(INITIAL.plus(1500))

      count must be equalTo 1
      subject.now must be equalTo INITIAL.plus(1500)
    }
    "not run actions that have been cancelled" in {
      val subscription = subject.scheduleAfter(new Duration(2000L)) {action(INITIAL.plus(2000))}
      subject.scheduleAfter(new Duration(1000L)) {subscription.close(); action(INITIAL.plus(1000))}

      subject.run()

      count must be equalTo 1
      subject.now must be equalTo INITIAL.plus(1000)
    }
    "not cancel actions that have already run" in {
      val subscription = subject.scheduleAfter(new Duration(1000L)) {action(INITIAL.plus(1000))}
      subject.scheduleAfter(new Duration(2000L)) {subscription.close(); action(INITIAL.plus(2000))}

      subject.run()

      count must be equalTo 2
      subject.now must be equalTo INITIAL.plus(2000)
    }

    "schedule recursive action" in {
      var count = 0
      subject.scheduleRecursive {
        self =>
          count += 1
          if (count < 2) {
            self()
          }
      }

      subject.run()

      count must be equalTo 2
    }

    "schedule recursive action with delay" in {
      var count = 0
      var timestamps = immutable.Queue[(Long, Int)]()
      subject.scheduleRecursiveAfter(new Duration(100)) { self =>
        count += 1
        timestamps = timestamps enqueue Tuple2(subject.now.getMillis, count)
        if (count < 3) {
          self(new Duration(count * 100))
        }
      }

      subject.run()

      count must be equalTo 3
      timestamps must be equalTo immutable.Queue(
        200L -> 1,
        300L -> 2,
        500L -> 3)
    }
  }

  "schedule recursive" should {
    val scheduler = new TestScheduler

    var count = 0
    def recursiveAction(self: () => Unit) {
      count += 1
      if (count < 5) {
        self()
      }
    }

    "recursively schedule same action" in {
      scheduler scheduleRecursive recursiveAction

      scheduler.run()

      count must be equalTo 5
      scheduler.now.getMillis must be equalTo 5
    }

    "cancel recursively scheduled action when subscription is closed" in {
      val subscription = scheduler scheduleRecursive recursiveAction
      scheduler.scheduleAt(new Instant(3)) {
        subscription.close()
      }

      scheduler.run()

      count must be equalTo 2
      scheduler.now.getMillis must be equalTo 3
    }
  }

}
