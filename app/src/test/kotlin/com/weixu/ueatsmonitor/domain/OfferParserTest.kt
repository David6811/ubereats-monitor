package com.weixu.ueatsmonitor.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfferParserTest {

    @Test
    fun `given a full uber offer notification, when parsed, then payout is read in cents`() {
        // arrange
        val raw = notification("New delivery request", "\$8.25 · 3.4 mi · 18 min · from McDonald's")

        // act
        val result = OfferParser.parse(raw)

        // affirm
        val parsed = result as? ParseResult.Parsed
        assertNotNull(parsed)

        // assert
        assertEquals(Cents(825), parsed!!.offer.payout)
    }

    @Test
    fun `given a full uber offer notification, when parsed, then distance is read in miles`() {
        // arrange
        val raw = notification("New delivery request", "\$8.25 · 3.4 mi · 18 min · from McDonald's")

        // act
        val result = OfferParser.parse(raw) as ParseResult.Parsed

        // assert
        assertEquals(3.4, result.offer.distance!!.value, 0.0001)
    }

    @Test
    fun `given a full uber offer notification, when parsed, then duration is read in minutes`() {
        // arrange
        val raw = notification("New delivery request", "\$8.25 · 3.4 mi · 18 min · from McDonald's")

        // act
        val result = OfferParser.parse(raw) as ParseResult.Parsed

        // assert
        assertEquals(Minutes(18), result.offer.duration)
    }

    @Test
    fun `given a full uber offer notification, when parsed, then the pickup name is read`() {
        // arrange
        val raw = notification("New delivery request", "\$8.25 · 3.4 mi · 18 min · from McDonald's")

        // act
        val result = OfferParser.parse(raw) as ParseResult.Parsed

        // assert
        assertEquals("McDonald's", result.offer.pickup)
    }

    @Test
    fun `given a metric offer, when parsed, then kilometers are converted to miles`() {
        // arrange
        val raw = notification("New delivery request", "\$9.00 · 8 km · 20 min")

        // act
        val result = OfferParser.parse(raw) as ParseResult.Parsed

        // assert
        assertEquals(4.970968, result.offer.distance!!.value, 0.0001)
    }

    @Test
    fun `given an offer without distance, when parsed, then distance stays null`() {
        // arrange
        val raw = notification("New delivery request", "\$8.25 · 18 min")

        // act
        val result = OfferParser.parse(raw) as ParseResult.Parsed

        // assert
        assertEquals(null, result.offer.distance)
    }

    @Test
    fun `given two amounts in one notification, when parsed, then the headline amount wins`() {
        // arrange
        val raw = notification("New delivery request", "\$8.25 incl. \$2.50 expected tip · 3.4 mi")

        // act
        val result = OfferParser.parse(raw) as ParseResult.Parsed

        // assert
        assertEquals(Cents(825), result.offer.payout)
    }

    @Test
    fun `given offer wording but no amount, when parsed, then the result is unreadable`() {
        // arrange
        val raw = notification("New delivery request", "Tap to view")

        // act
        val result = OfferParser.parse(raw)

        // assert
        assertTrue(result is ParseResult.Unreadable)
    }

    @Test
    fun `given an unrelated uber notification, when parsed, then it is not an offer`() {
        // arrange
        val raw = notification("You are online", "Waiting for orders")

        // act
        val result = OfferParser.parse(raw)

        // assert
        assertEquals(ParseResult.NotAnOffer, result)
    }

    @Test
    fun `given a blank notification, when parsed, then it is not an offer`() {
        // arrange
        val raw = RawNotification(
            packageName = OfferParser.UBER_DRIVER_PACKAGE,
            title = null,
            text = null,
            postedAtMillis = FIXED_TIME,
        )

        // act
        val result = OfferParser.parse(raw)

        // assert
        assertEquals(ParseResult.NotAnOffer, result)
    }

    @Test
    fun `given the real offer card wording, when parsed, then the payout is read`() {
        // arrange
        val raw = notification("Delivery request", "\$18.03 · 27 min (6.0 km) total · Coles - Keysborough")

        // act
        val result = OfferParser.parse(raw) as ParseResult.Parsed

        // assert
        assertEquals(Cents(1803), result.offer.payout)
    }

    @Test
    fun `given the real offer card wording, when parsed, then the kilometres become miles`() {
        // arrange
        val raw = notification("Delivery request", "\$18.03 · 27 min (6.0 km) total · Coles - Keysborough")

        // act
        val result = OfferParser.parse(raw) as ParseResult.Parsed

        // assert
        assertEquals(3.7282, result.offer.distance!!.value, 0.001)
    }

    @Test
    fun `given the real offer card wording, when parsed, then the total minutes are read`() {
        // arrange
        val raw = notification("Delivery request", "\$18.03 · 27 min (6.0 km) total · Coles - Keysborough")

        // act
        val result = OfferParser.parse(raw) as ParseResult.Parsed

        // assert
        assertEquals(Minutes(27), result.offer.duration)
    }

    private fun notification(title: String, text: String) = RawNotification(
        packageName = OfferParser.UBER_DRIVER_PACKAGE,
        title = title,
        text = text,
        postedAtMillis = FIXED_TIME,
    )

    private companion object {
        const val FIXED_TIME = 1_700_000_000_000L
    }
}
