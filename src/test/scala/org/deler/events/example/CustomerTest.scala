package org.deler.events.example

import java.util.UUID
import org.junit.runner.RunWith
import org.specs._
import org.specs.mock.Mockito
import org.specs.runner.{ JUnitSuiteRunner, JUnit }
import org.mockito.Matchers._
import scala.collection._

@RunWith(classOf[JUnitSuiteRunner])
class CustomerTest extends Specification with JUnit with Mockito {

  val customerId = UUID.randomUUID()
  val customerView = new CustomerView

  "customer view" should {

    "set current address to registered address" in {
      customerView.onNext(CustomerRegisteredEvent(customerId, "address"))

      customerView.address.current must be equalTo Some("address")
    }
    "set current address on move" in {
      customerView.onNext(CustomerRegisteredEvent(customerId, "old address"))
      customerView.onNext(CustomerMovedEvent(customerId, "new address"))

      customerView.address.current must be equalTo Some("new address")
    }
  }

}
