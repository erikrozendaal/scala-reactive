package org.deler.reactive

@org.junit.runner.RunWith(classOf[org.specs2.runner.JUnitRunner])
class SubjectTest extends Test {
  isolated

  val Event = "event"
  val Error = new Exception("error")

  trait Fixture {
    val observer = mock[Observer[String]]
    val subject: Subject[String] = new PublishSubject[String]
  }

  trait ReplaySubjectFixture extends Fixture {
    override val subject = new ReplaySubject[String]
  }

  trait SingleObserverFixture extends Fixture {
    val subscription: Closeable = subject.subscribe(observer)
  }

  trait MultipleObserversFixture extends SingleObserverFixture {
    val observer2 = mock[Observer[String]]
    subject.subscribe(observer2)
  }

  "subjects with a single observer" should workWithSingleObserver(new SingleObserverFixture {})
  "subjects with multiple observers" should workWithMultipleObservers(new MultipleObserversFixture {})
  "replay subjects" should {
    workWithSingleObserver(new ReplaySubjectFixture with SingleObserverFixture {})
    workWithMultipleObservers(new ReplaySubjectFixture with MultipleObserversFixture {})
    rememberAndReplayPastEvents
  }

  def workWithSingleObserver(fixture: SingleObserverFixture) = {
    import fixture._

    "publish events to observer" in {
      subject.onNext(Event)

      there was one(observer).onNext(Event)
    }
    "stop publishing events after onCompleted" in {
      subject.onCompleted()
      subject.onNext(Event)

      there was one(observer).onCompleted()
      there were noMoreCallsTo(observer)
    }
    "stop publishing events after onError" in {
      subject.onError(Error)
      subject.onNext(Event)

      there was one(observer).onError(Error)
      there were noMoreCallsTo(observer)
    }
    "stop publishing onCompleted after onError" in {
      subject.onError(Error)
      subject.onCompleted()

      there was one(observer).onError(Error)
      there were noMoreCallsTo(observer)
    }
    "stop publishing after a subscription is closed" in {
      subscription.close()

      subject.onNext(Event)

      there were noMoreCallsTo(observer)
    }
  }

  def workWithMultipleObservers(fixture: MultipleObserversFixture) {
    import fixture._

    "publish onNext to all observers" in {
      subject.onNext(Event)

      there was one(observer).onNext(Event)
      there was one(observer2).onNext(Event)
    }
    "publish onCompleted to all observers" in {
      subject.onCompleted()

      there was one(observer).onCompleted()
      there was one(observer2).onCompleted()
    }
    "publish onError to all observers" in {
      subject.onError(Error)

      there was one(observer).onError(Error)
      there was one(observer2).onError(Error)
    }
    "continue publishing events after one observer unsubscribes" in {
      subscription.close()

      subject.onNext(Event)

      there were noMoreCallsTo(observer)
      there was one(observer2).onNext(Event)
    }
  }

  def rememberAndReplayPastEvents = {
    val fixture = new ReplaySubjectFixture {}
    import fixture._

    "replay all past events to new subscriptions" in {
      subject.onNext("a")
      subject.onNext("b")

      subject.subscribe(observer)

      there was one(observer).onNext("a") andThen one(observer).onNext("b")
      there were noMoreCallsTo(observer)
    }
    "replay all past events to a new subscription followed by onComplete when completed" in {
      subject.onNext(Event)
      subject.onCompleted()

      subject.subscribe(observer)

      there was one(observer).onNext(Event) andThen one(observer).onCompleted()
      there were noMoreCallsTo(observer)
    }
    "replay all past events to a new subscription followed by onError when failed" in {
      subject.onNext(Event)
      subject.onError(Error)

      subject.subscribe(observer)

      there was one(observer).onNext(Event) andThen one(observer).onError(Error)
      there were noMoreCallsTo(observer)
    }
    "stop remembering new notifications once completed" in {
      subject.onNext("a")
      subject.onCompleted()
      subject.onNext("b")

      subject.subscribe(observer)
      there was one(observer).onNext("a") andThen one(observer).onCompleted()
      there were noMoreCallsTo(observer)
    }
  }
}
