package org.deler.reactive

import org.joda.time._
import org.junit.runner.RunWith
import org.specs._
import org.specs.runner.{JUnitSuiteRunner, JUnit}
import scala.collection._

@RunWith(classOf[JUnitSuiteRunner])
class SchedulingTest extends Specification with JUnit {
  val INITIAL = new Instant(100)

  val virtualScheduler = new VirtualScheduler(INITIAL) with LoggingScheduler

  var count = 0

  def action(expectedTime: Instant = INITIAL) {
    virtualScheduler.now must be equalTo expectedTime
    count += 1
  }

  "virtual schedule" should {
    "not run an action when it is scheduled" in {
      virtualScheduler schedule action()

      count must be equalTo 0
    }
    "run scheduled action" in {
      virtualScheduler schedule action()

      virtualScheduler.run

      count must be equalTo 1
    }
    "run scheduled action at specified time" in {
      virtualScheduler.scheduleAfter(new Duration(1000L)) {action(INITIAL.plus(1000))}

      virtualScheduler.run

      virtualScheduler.now must be equalTo INITIAL.plus(1000)
    }
    "never take the clock backwards" in {
      virtualScheduler.scheduleAt(INITIAL minus 1000) {action(INITIAL)}

      virtualScheduler.run

      virtualScheduler.now must be equalTo INITIAL
    }
    "run actions in scheduled ordered" in {
      virtualScheduler.scheduleAfter(new Duration(2000L)) {action(INITIAL.plus(2000))}
      virtualScheduler.scheduleAfter(new Duration(1000L)) {action(INITIAL.plus(1000))}

      virtualScheduler.run

      count must be equalTo 2
      virtualScheduler.now must be equalTo INITIAL.plus(2000)
    }
    "run actions that are scheduled by other actions" in {
      virtualScheduler schedule {
        virtualScheduler.scheduleAfter(new Duration(1000L)) {action(INITIAL.plus(1000))}
      }

      virtualScheduler.run

      count must be equalTo 1
      virtualScheduler.now must be equalTo INITIAL.plus(1000)
    }
    "run actions upto the specified instant (exclusive)" in {
      virtualScheduler.scheduleAfter(new Duration(1000L)) {action(INITIAL.plus(1000))}

      virtualScheduler.runTo(INITIAL.plus(1000))

      count must be equalTo 0
      virtualScheduler.now must be equalTo INITIAL.plus(1000)
    }
    "not run actions after the specified instant" in {
      virtualScheduler.scheduleAfter(new Duration(2000L)) {action(INITIAL.plus(2000))}
      virtualScheduler.scheduleAfter(new Duration(1000L)) {action(INITIAL.plus(1000))}

      virtualScheduler.runTo(INITIAL.plus(1500))

      count must be equalTo 1
      virtualScheduler.now must be equalTo INITIAL.plus(1500)
    }
    "not run actions that have been cancelled" in {
      val subscription = virtualScheduler.scheduleAfter(new Duration(2000L)) {action(INITIAL.plus(2000))}
      virtualScheduler.scheduleAfter(new Duration(1000L)) {subscription.close(); action(INITIAL.plus(1000))}

      virtualScheduler.run()

      count must be equalTo 1
      virtualScheduler.now must be equalTo INITIAL.plus(1000)
    }
    "not cancel actions that have already run" in {
      val subscription = virtualScheduler.scheduleAfter(new Duration(1000L)) {action(INITIAL.plus(1000))}
      virtualScheduler.scheduleAfter(new Duration(2000L)) {subscription.close(); action(INITIAL.plus(2000))}

      virtualScheduler.run()

      count must be equalTo 2
      virtualScheduler.now must be equalTo INITIAL.plus(2000)
    }

    "schedule recursive action" in {
      var count = 0
      virtualScheduler.scheduleRecursive {
        self =>
          count += 1
          if (count < 2) {
            self()
          }
      }

      virtualScheduler.run()

      count must be equalTo 2
    }

  }

  "schedule recursive" should {
    val scheduler = new TestScheduler with LoggingScheduler

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

    "schedule recursive action with delay" in {
      var count = 0
      var timestamps = immutable.Queue[(Long, Int)]()
      scheduler.scheduleRecursiveAfter(new Duration(100)) {
        self =>
          count += 1
          timestamps = timestamps enqueue Tuple2(scheduler.now.getMillis, count)
          if (count < 3) {
            self(new Duration(count * 100))
          }
      }

      scheduler.run()

      count must be equalTo 3
      timestamps must be equalTo immutable.Queue(
        100L -> 1,
        200L -> 2,
        400L -> 3)
    }

    "cancel recursive actions with delay when subscription is closed" in {
      var count = 0
      var timestamps = immutable.Queue[(Long, Int)]()
      val subscription = scheduler.scheduleRecursiveAfter(new Duration(100)) {
        self =>
          count += 1
          timestamps = timestamps enqueue Tuple2(scheduler.now.getMillis, count)
          self(new Duration(count * 100))
      }
      scheduler.scheduleAt(new Instant(500)) {
        subscription.close()
      }

      scheduler.run()

      count must be equalTo 3
      timestamps must be equalTo immutable.Queue(
        100L -> 1,
        200L -> 2,
        400L -> 3)
    }
  }

  "current thread scheduler" should {
    val immediate = new ImmediateScheduler with LoggingScheduler
    val currentThread = new CurrentThreadScheduler with LoggingScheduler

    "run initial action immediately" in {
      var initialCompleted = false
      currentThread schedule {
        initialCompleted = true
      }

      initialCompleted must be equalTo true
    }

    "run actions scheduled by other actions after initial action completes" in {
      var initialCompleted = false
      var scheduledCompleted = false
      currentThread schedule {
        currentThread schedule {
          initialCompleted must beTrue
          scheduledCompleted = true
        }

        scheduledCompleted must beFalse
        initialCompleted = true
      }

      initialCompleted must beTrue
    }

    "run actions that were scheduled to run at the same time in scheduling order" in {
      var count = 0
      currentThread schedule {
        for (i <- 0 until 10) {
          currentThread schedule {
            count must be equalTo i
            count += 1
          }
        }
      }

      count must be equalTo 10
    }

    "run actions scheduled with delay by sleeping the current thread" in {
      var scheduledCompleted = false
      val start = System.currentTimeMillis
      currentThread.scheduleAfter(new Duration(50)) {
        scheduledCompleted = true
        (System.currentTimeMillis - start) must be greaterThan 50
      }

      scheduledCompleted must be equalTo true
    }

    "cancel actions scheduled in the future" in {
      var shouldNotBeCalled = false
      currentThread schedule {
        val subscription = currentThread.scheduleAfter(new Duration(500)) {
          shouldNotBeCalled = true
        }
        subscription.close()
      }

      shouldNotBeCalled must beFalse
    }

    "run actions scheduled inside an ImmediateScheduler action after the scheduling action completes" in {
      var immediateCompleted = false
      var currentThreadCompleted = false
      immediate schedule {
        currentThread schedule {
          immediateCompleted must beTrue
          currentThreadCompleted = true
        }
        immediateCompleted = true
      }

      immediateCompleted must beTrue
      currentThreadCompleted must beTrue
    }
  }

}
