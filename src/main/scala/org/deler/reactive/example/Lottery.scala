package org.deler.reactive.example

import org.deler.reactive._
import java.util.UUID

trait LotteryEvent {
    val lotteryId: UUID
}
case class LotteryCreated(lotteryId: UUID, ticketPrize: BigDecimal, prizeAmount: BigDecimal) extends LotteryEvent
case class TicketPurchased(lotteryId: UUID, customerId: UUID, ticketNumber: String) extends LotteryEvent

class LotteryTicket(lottery: Lottery, customerId: UUID, ticketNumber: String)

class Lottery extends BasicSubject[LotteryEvent] {

	val prizeAmount = collect { case event: LotteryCreated => event.prizeAmount }
	val ticketPrize = collect { case event: LotteryCreated => event.ticketPrize }
	val tickets = collect { case event: TicketPurchased => new LotteryTicket(this, event.customerId, event.ticketNumber) }
	
}
