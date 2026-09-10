package com.weixu.ueatsmonitor.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleJudgeTest {

    @Test
    fun `given a destination in the allowed list, when judged, then take it`() {
        // arrange
        val card = card(pickup = "McDonald's (Sandown)", dropoff = "Dandenong Road & Dunblane Road, Noble Park")

        // act
        val ruling = RuleJudge.judge(card, RULES, GAZETTEER)

        // assert
        assertTrue(ruling is Ruling.Take)
    }

    @Test
    fun `given a destination outside the list, when judged, then leave it and name the suburb`() {
        // arrange
        val card = card(pickup = "Mad Shak's Cafe", dropoff = "Bangholme Road, Dandenong South")

        // act
        val ruling = RuleJudge.judge(card, RULES, GAZETTEER)

        // affirm
        assertTrue(ruling is Ruling.Leave)

        // assert
        assertEquals("Dandenong South 不在名单里", RulingText.reason(ruling))
    }

    @Test
    fun `given a denied pickup, when judged, then the store is the reason`() {
        // arrange
        val card = card(pickup = "Walrus BBQ", dropoff = "Some Street, Noble Park")
        val rules = RULES.copy(deniedStores = listOf("Walrus BBQ"))

        // act
        val ruling = RuleJudge.judge(card, rules, GAZETTEER)

        // assert
        assertEquals("Walrus BBQ 在黑名单里", RulingText.reason(ruling))
    }

    @Test
    fun `given a denied pickup and a bad suburb, when judged, then the store is reported first`() {
        // arrange
        val card = card(pickup = "Walrus BBQ", dropoff = "Bangholme Road, Dandenong South")
        val rules = RULES.copy(deniedStores = listOf("Walrus BBQ"))

        // act
        val ruling = RuleJudge.judge(card, rules, GAZETTEER)

        // assert
        assertEquals("Walrus BBQ 在黑名单里", RulingText.reason(ruling))
    }

    @Test
    fun `given no rules at all, when judged, then the app says so rather than deciding`() {
        // arrange
        val card = card(pickup = "Anything", dropoff = "Anywhere, Noble Park")

        // act
        val ruling = RuleJudge.judge(card, Rules(emptySet(), emptyList(), emptyList()), GAZETTEER)

        // assert
        assertEquals(Ruling.NoRules, ruling)
    }

    @Test
    fun `given a destination with no recognisable suburb, when judged, then it says so`() {
        // arrange
        val card = card(pickup = "Anything", dropoff = "12 Nowhere Street")

        // act
        val ruling = RuleJudge.judge(card, RULES, GAZETTEER)

        // assert
        assertEquals(Ruling.Unknown, ruling)
    }

    @Test
    fun `given a cheap offer inside the area, when judged, then money is not considered`() {
        // arrange
        val card = card(
            pickup = "McDonald's (Sandown)",
            dropoff = "Dandenong Road, Noble Park",
            payout = Cents(150),
        )

        // act
        val ruling = RuleJudge.judge(card, RULES, GAZETTEER)

        // assert
        assertTrue(ruling is Ruling.Take)
    }

    @Test
    fun `given a chain that always has parking, when it is on the deny list, then it is still taken`() {
        // arrange
        val card = card(pickup = "McDonald's® (Dandenong)", dropoff = "Some Street, Noble Park")
        val rules = RULES.copy(
            deniedStores = listOf("McDonald's"),
            alwaysOkStores = listOf("McDonald"),
        )

        // act
        val ruling = RuleJudge.judge(card, rules, GAZETTEER)

        // assert
        assertTrue(ruling is Ruling.Take)
    }

    @Test
    fun `given a whitelisted chain with a destination outside the area, when judged, then the suburb still rules`() {
        // arrange
        val card = card(pickup = "KFC (Dandenong)", dropoff = "Bangholme Road, Dandenong South")
        val rules = RULES.copy(deniedStores = listOf("KFC"), alwaysOkStores = listOf("KFC"))

        // act
        val ruling = RuleJudge.judge(card, rules, GAZETTEER)

        // assert
        assertEquals("Dandenong South 不在名单里", RulingText.reason(ruling))
    }

    private fun card(pickup: String, dropoff: String, payout: Cents = Cents(907)) = OfferCard(
        payout = payout,
        duration = Minutes(16),
        distance = Miles(2.86),
        pickup = pickup,
        dropoff = dropoff,
        stops = listOf(pickup, dropoff),
    )

    private companion object {
        val RULES = Rules(
            allowedSuburbs = setOf("Noble Park", "Keysborough", "Dandenong"),
            deniedStores = emptyList(),
            alwaysOkStores = emptyList(),
        )
        val GAZETTEER = listOf(
            Suburb("Noble Park", GeoPoint(-37.9695, 145.1767)),
            Suburb("Keysborough", GeoPoint(-38.0054, 145.1674)),
            Suburb("Dandenong", GeoPoint(-37.9875, 145.2148)),
            Suburb("Dandenong South", GeoPoint(-38.028, 145.2209)),
        )
    }
}
