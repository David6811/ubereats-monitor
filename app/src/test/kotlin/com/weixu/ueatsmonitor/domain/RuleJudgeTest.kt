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
        val ruling = RuleJudge.judge(card, Rules(emptySet(), emptySet(), Cents.ofDollars(30.0), emptyList(), emptyList(), emptyList(), refuseLanes = true), GAZETTEER)

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
        isMatch = false,
        payout = payout,
        duration = Minutes(16),
        distance = Miles(2.86),
        pickup = pickup,
        dropoff = dropoff,
        stops = listOf(pickup, dropoff),
    )

    @Test
    fun `given a payout over the threshold, when judged, then the far set decides`() {
        // arrange  Dandenong South is not in the ordinary set
        val rules = RULES.copy(farSuburbs = setOf("Dandenong South"))
        val card = card(pickup = "Some Shop", dropoff = "Bennet Street, Dandenong South", payout = Cents.ofDollars(31.0))

        // act
        val ruling = RuleJudge.judge(card, rules, GAZETTEER)

        // assert
        assertEquals(Ruling.Take("Dandenong South", far = true), ruling)
    }

    @Test
    fun `given a payout at the threshold, when judged, then the ordinary set still decides`() {
        // arrange  over, not at: thirty dollars exactly is an ordinary offer
        val rules = RULES.copy(farSuburbs = setOf("Dandenong South"))
        val card = card(pickup = "Some Shop", dropoff = "Bennet Street, Dandenong South", payout = Cents.ofDollars(30.0))

        // act
        val ruling = RuleJudge.judge(card, rules, GAZETTEER)

        // assert
        assertEquals(Ruling.Leave(Ruling.Reason.SuburbNotAllowed("Dandenong South")), ruling)
    }

    @Test
    fun `given a big payout to somewhere in neither set, when judged, then it is still refused`() {
        // arrange
        val rules = RULES.copy(farSuburbs = setOf("Dandenong South"))
        val card = card(pickup = "Some Shop", dropoff = "Somewhere in Keysborough", payout = Cents.ofDollars(45.0))

        // affirm  Keysborough is in the ordinary set, which no longer applies
        assertEquals(true, RULES.allowedSuburbs.contains("Keysborough"))

        // act
        val ruling = RuleJudge.judge(card, rules, GAZETTEER)

        // assert
        assertEquals(Ruling.Leave(Ruling.Reason.SuburbNotAllowed("Keysborough")), ruling)
    }

    @Test
    fun `given no far set drawn, when a big payout is judged, then the ordinary set decides`() {
        // arrange
        val card = card(pickup = "Some Shop", dropoff = "Bennet Street, Dandenong South", payout = Cents.ofDollars(60.0))

        // act
        val ruling = RuleJudge.judge(card, RULES, GAZETTEER)

        // assert
        assertEquals(Ruling.Leave(Ruling.Reason.SuburbNotAllowed("Dandenong South")), ruling)
    }

    @Test
    fun `given a lane in a suburb he works, when judged, then the road refuses it before the suburb allows it`() {
        // arrange
        val card = card(pickup = "Anything", dropoff = "Chandler Road & Bakers Lane, Noble Park")

        // act
        val ruling = RuleJudge.judge(card, RULES, GAZETTEER)

        // affirm  the suburb itself is one he works, so only the road can refuse it
        assertEquals(true, RULES.allowedSuburbs.contains("Noble Park"))

        // assert
        assertEquals(Ruling.Leave(Ruling.Reason.RoadRefused("Lane")), ruling)
    }

    @Test
    fun `given the road rule switched off, when a lane is judged, then the suburb decides as before`() {
        // arrange
        val card = card(pickup = "Anything", dropoff = "Chandler Road & Bakers Lane, Noble Park")

        // act
        val ruling = RuleJudge.judge(card, RULES.copy(refuseLanes = false), GAZETTEER)

        // assert
        assertEquals(Ruling.Take("Noble Park"), ruling)
    }

    @Test
    fun `given a destination he typed into the not-going list, when judged, then it is refused by name`() {
        // arrange
        val card = card(pickup = "Anything", dropoff = "Cnr Springvale and Cheltenham Rds, Keysborough")
        val rules = RULES.copy(deniedAddresses = listOf("Cnr Springvale and Cheltenham Rds"))

        // act
        val ruling = RuleJudge.judge(card, rules, GAZETTEER)

        // affirm  the suburb would have let it through
        assertEquals(true, rules.allowedSuburbs.contains("Keysborough"))

        // assert
        assertEquals(
            Ruling.Leave(Ruling.Reason.AddressDenied("Cnr Springvale and Cheltenham Rds")),
            ruling,
        )
    }

    private companion object {
        val RULES = Rules(
            allowedSuburbs = setOf("Noble Park", "Keysborough", "Dandenong"),
            farSuburbs = emptySet(),
            farOverCents = Cents.ofDollars(30.0),
            deniedStores = emptyList(),
            alwaysOkStores = emptyList(),
            deniedAddresses = emptyList(),
            refuseLanes = true,
        )
        val GAZETTEER = listOf(
            Suburb("Noble Park", GeoPoint(-37.9695, 145.1767)),
            Suburb("Keysborough", GeoPoint(-38.0054, 145.1674)),
            Suburb("Dandenong", GeoPoint(-37.9875, 145.2148)),
            Suburb("Dandenong South", GeoPoint(-38.028, 145.2209)),
        )
    }
}
