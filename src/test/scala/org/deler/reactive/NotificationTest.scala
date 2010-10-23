package org.deler.reactive

import org.specs.mock.Mockito
import org.specs.Specification
import org.specs.runner.{JUnitSuiteRunner, JUnit}
import org.junit.runner.RunWith

@RunWith(classOf[JUnitSuiteRunner])
class NotificationTest extends Specification with JUnit with Mockito {

  val observer = mock[Observer[String]]

  "OnCompleted" should {
    "invoke onComplete when accepting observer" in {
      OnCompleted.accept(observer)

      there was one(observer).onCompleted()
    }
  }

  "OnError" should {
    "invoke onError when accepting observer" in {
      val error = new Exception()

      OnError(error).accept(observer)

      there was one(observer).onError(error)
    }
  }

  "OnNext" should {
    "invoke onNext when accepting observer" in {
      OnNext("value").accept(observer)

      there was one(observer).onNext("value")
    }
  }
}
