package org.deler.reactive

@org.junit.runner.RunWith(classOf[org.specs2.runner.JUnitRunner])
class NotificationTest extends Test {
  isolated

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
