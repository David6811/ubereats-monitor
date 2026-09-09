package com.weixu.ueatsmonitor.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfferEvaluatorTest {

    @Test
    fun `given an offer that clears every bar, when evaluated, then the verdict is accept`() {
        // arrange
        val offer = Offer(
            payout = Cents(1000),
            distance = Miles(4.0),
            duration = Minutes(20),
            pickup = "McDonald's",
            dropoff = null,
        )

        // act
        val verdict = OfferEvaluator.evaluate(offer, BAR)

        // assert
        assertTrue(verdict is Verdict.Accept)
    }

    @Test
    fun `given a ten dollar four mile offer, when evaluated, then pay per mile is two fifty`() {
        // arrange
        val offer = Offer(
            payout = Cents(1000),
            distance = Miles(4.0),
            duration = Minutes(20),
            pickup = null,
            dropoff = null,
        )

        // act
        val metrics = OfferEvaluator.metricsOf(offer)

        // assert
        assertEquals(2.5, metrics.payPerMile!!, 0.0001)
    }

    @Test
    fun `given a ten dollar twenty minute offer, when evaluated, then pay per hour is thirty`() {
        // arrange
        val offer = Offer(
            payout = Cents(1000),
            distance = Miles(4.0),
            duration = Minutes(20),
            pickup = null,
            dropoff = null,
        )

        // act
        val metrics = OfferEvaluator.metricsOf(offer)

        // assert
        assertEquals(30.0, metrics.payPerHour!!, 0.0001)
    }

    @Test
    fun `given a payout under the minimum, when evaluated, then min payout is the failing rule`() {
        // arrange
        val offer = Offer(
            payout = Cents(400),
            distance = Miles(0.5),
            duration = Minutes(5),
            pickup = null,
            dropoff = null,
        )

        // act
        val verdict = OfferEvaluator.evaluate(offer, BAR)

        // affirm
        val decline = verdict as? Verdict.Decline
        assertNotNull(decline)

        // assert
        assertEquals(listOf(Rule.MIN_PAYOUT), decline!!.failures.map { it.rule })
    }

    @Test
    fun `given a long low paying offer, when evaluated, then every missed rule is reported`() {
        // arrange
        val offer = Offer(
            payout = Cents(600),
            distance = Miles(12.0),
            duration = Minutes(45),
            pickup = null,
            dropoff = null,
        )

        // act
        val verdict = OfferEvaluator.evaluate(offer, BAR) as Verdict.Decline

        // assert
        assertEquals(
            listOf(Rule.MAX_DISTANCE, Rule.MIN_PAY_PER_MILE, Rule.MIN_PAY_PER_HOUR),
            verdict.failures.map { it.rule },
        )
    }

    @Test
    fun `given a missing distance, when evaluated, then distance rules are uncheckable not passed`() {
        // arrange
        val offer = Offer(
            payout = Cents(1000),
            distance = null,
            duration = Minutes(20),
            pickup = null,
            dropoff = null,
        )

        // act
        val verdict = OfferEvaluator.evaluate(offer, BAR)

        // affirm
        val uncertain = verdict as? Verdict.Uncertain
        assertNotNull(uncertain)

        // assert
        assertEquals(listOf(Rule.MAX_DISTANCE, Rule.MIN_PAY_PER_MILE), uncertain!!.uncheckable)
    }

    @Test
    fun `given a missing duration and a failing payout, when evaluated, then decline outranks uncertain`() {
        // arrange
        val offer = Offer(
            payout = Cents(300),
            distance = Miles(2.0),
            duration = null,
            pickup = null,
            dropoff = null,
        )

        // act
        val verdict = OfferEvaluator.evaluate(offer, BAR)

        // assert
        assertTrue(verdict is Verdict.Decline)
    }

    @Test
    fun `given a zero mile offer, when evaluated, then pay per mile is not computed`() {
        // arrange
        val offer = Offer(
            payout = Cents(1000),
            distance = Miles(0.0),
            duration = Minutes(20),
            pickup = null,
            dropoff = null,
        )

        // act
        val metrics = OfferEvaluator.metricsOf(offer)

        // assert
        assertEquals(null, metrics.payPerMile)
    }

    private companion object {
        val BAR = Thresholds(
            minPayout = Cents(500),
            minPayPerMile = 1.50,
            minPayPerHour = 20.0,
            maxDistance = Miles(8.0),
        )
    }
}
