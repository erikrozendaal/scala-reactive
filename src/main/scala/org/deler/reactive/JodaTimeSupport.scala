package org.deler.reactive

import org.joda.time.{ Instant, Duration }

object JodaTimeSupport {
  def now = new Instant()

  implicit class RichLong(underlying: Long) {
    def milliseconds = new Duration(underlying)

    def seconds = new Duration(underlying * 1000)
  }

  implicit class RichDuration(underlying: Duration) {
    def +(that: Duration) = underlying.plus(that)

    def -(that: Duration) = underlying.minus(that)

    def ago = now.minus(underlying)

    def later: Instant = from(now)

    def from(instant: Instant) = instant.plus(underlying)
  }

  implicit class RichInstant(underlying: Instant) {
    def +(that: Duration) = underlying plus that

    def -(that: Duration) = underlying minus that
  }
}
