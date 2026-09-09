package com.weixu.ueatsmonitor.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The fixture is the real card photographed on the phone at 19:38 on 9 Sept,
 * transcribed exactly - including the whole-dollar payout with no decimals.
 */
class OfferCardReaderTest {

    @Test
    fun `given the real offer card, when read, then the payout is five dollars`() {
        // arrange
        val lines = REAL_CARD

        // act
        val card = OfferCardReader.read(lines)

        // affirm
        assertNotNull(card)

        // assert
        assertEquals(Cents(500), card!!.payout)
    }

    @Test
    fun `given the real offer card, when read, then the total minutes are ten`() {
        // arrange
        val lines = REAL_CARD

        // act
        val card = OfferCardReader.read(lines)!!

        // assert
        assertEquals(Minutes(10), card.duration)
    }

    @Test
    fun `given the real offer card, when read, then one point six km becomes miles`() {
        // arrange
        val lines = REAL_CARD

        // act
        val card = OfferCardReader.read(lines)!!

        // assert
        assertEquals(0.9942, card.distance.value, 0.001)
    }

    @Test
    fun `given the real offer card, when read, then the pickup is the restaurant`() {
        // arrange
        val lines = REAL_CARD

        // act
        val card = OfferCardReader.read(lines)!!

        // assert
        assertEquals("Mario's Pizza And Pasta", card.pickup)
    }

    @Test
    fun `given the real offer card, when read, then the dropoff is the street address`() {
        // arrange
        val lines = REAL_CARD

        // act
        val card = OfferCardReader.read(lines)!!

        // assert
        assertEquals("Cole Street & Nockolds Crescent, Noble Park", card.dropoff)
    }

    @Test
    fun `given the real offer card, when evaluated against the bar, then it is worth accepting`() {
        // arrange
        val card = OfferCardReader.read(REAL_CARD)!!

        // act
        val verdict = OfferEvaluator.evaluate(OfferCardReader.toOffer(card), Thresholds.STARTER)

        // assert
        assertTrue(verdict is Verdict.Accept)
    }

    @Test
    fun `given the real offer card, when evaluated, then the hourly rate is thirty dollars`() {
        // arrange
        val card = OfferCardReader.read(REAL_CARD)!!

        // act
        val metrics = OfferEvaluator.metricsOf(OfferCardReader.toOffer(card))

        // assert
        assertEquals(30.0, metrics.payPerHour!!, 0.001)
    }

    @Test
    fun `given the dropoff of the real card, when matched, then Noble Park is inside the area`() {
        // arrange
        val card = OfferCardReader.read(REAL_CARD)!!
        val gazetteer = listOf(
            Suburb("Noble Park", GeoPoint(-37.9695, 145.1767)),
            Suburb("Springvale", GeoPoint(-37.9456, 145.158)),
        )

        // act
        val call = AreaJudge.call(SuburbIndex.findAll(card.dropoff, gazetteer), ServiceArea.SOUTH_EAST)

        // assert
        assertTrue(call is AreaCall.AllInside)
    }

    @Test
    fun `given the uber home screen, when read, then it is not an offer card`() {
        // arrange
        val lines = listOf("Home", "$44.49", "Finding trips", "Coles", "KFC")

        // act
        val card = OfferCardReader.read(lines)

        // assert
        assertNull(card)
    }

    @Test
    fun `given a card whose payout carries cents, when read, then the cents survive`() {
        // arrange
        val lines = listOf("Delivery", "$18.03", "27 min (6.0 km) total", "Coles - Keysborough", "8 Fern St, Keysborough")

        // act
        val card = OfferCardReader.read(lines)!!

        // assert
        assertEquals(Cents(1803), card.payout)
    }

    private companion object {
        val REAL_CARD = listOf(
            "Delivery",
            "Exclusive",
            "\$5",
            "Est. earnings for completed trip",
            "10 min (1.6 km) total",
            "Mario's Pizza And Pasta",
            "Cole Street & Nockolds Crescent, Noble Park",
            "Accept",
        )
    }
}
