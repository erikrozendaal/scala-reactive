package org.deler.events

import org.joda.time._
import org.junit.runner.RunWith
import org.specs._
import org.specs.mock.Mockito
import org.specs.runner.{ JUnitSuiteRunner, JUnit }
import org.mockito.Matchers._
import scala.collection._

@RunWith(classOf[JUnitSuiteRunner])
class CurrentThreadSchedulerTest extends Specification with JUnit with Mockito {

  val subject = new CurrentThreadScheduler

  "current thread scheduler" should {
    "run initial action immediately" in {
      var initialCompleted = false
      subject.schedule {
        initialCompleted = true
      }

      initialCompleted must be equalTo true
    }

    "run actions scheduled by other actions" in {
      var initialCompleted = false
      var scheduledCompleted = false
      subject schedule {
        subject schedule {
          initialCompleted must be equalTo true
          scheduledCompleted = true
        }
        
        initialCompleted = true
      }

      initialCompleted must beTrue
    }

    "run actions scheduled at the same time in scheduling order" in {
      var count = 0
      subject schedule {
        for (i <- 0 until 10) {
          subject schedule {
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
      subject.scheduleAfter(new Duration(50)) {
    	scheduledCompleted =  true
        (System.currentTimeMillis - start) must be greaterThan 50
      }
      
      scheduledCompleted must be equalTo true
    }
  }

}
