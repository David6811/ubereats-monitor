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
        val ruling = RuleJudge.judge(card, RULES, GAZETTEER, Stops.UNPLACED)

        // assert
        assertTrue(ruling is Ruling.Take)
    }

    @Test
    fun `given a destination outside the list, when judged, then leave it and name the suburb`() {
        // arrange
        val card = card(pickup = "Mad Shak's Cafe", dropoff = "Bangholme Road, Dandenong South")

        // act
        val ruling = RuleJudge.judge(card, RULES, GAZETTEER, Stops.UNPLACED)

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
        val ruling = RuleJudge.judge(card, rules, GAZETTEER, Stops.UNPLACED)

        // assert
        assertEquals("Walrus BBQ 在黑名单里", RulingText.reason(ruling))
    }

    @Test
    fun `given a denied pickup and a bad suburb, when judged, then the store is reported first`() {
        // arrange
        val card = card(pickup = "Walrus BBQ", dropoff = "Bangholme Road, Dandenong South")
        val rules = RULES.copy(deniedStores = listOf("Walrus BBQ"))

        // act
        val ruling = RuleJudge.judge(card, rules, GAZETTEER, Stops.UNPLACED)

        // assert
        assertEquals("Walrus BBQ 在黑名单里", RulingText.reason(ruling))
    }

    @Test
    fun `given no rules at all, when judged, then the app says so rather than deciding`() {
        // arrange
        val card = card(pickup = "Anything", dropoff = "Anywhere, Noble Park")

        // act
        val ruling = RuleJudge.judge(card, Rules(emptySet(), emptySet(), Cents.ofDollars(30.0), emptyList(), emptyList(), emptyList(), TripCost(0.2, 1.5), 10.0, null), GAZETTEER, Stops.UNPLACED)

        // assert
        assertEquals(Ruling.NoRules, ruling)
    }

    @Test
    fun `given a destination with no recognisable suburb, when judged, then it says so`() {
        // arrange
        val card = card(pickup = "Anything", dropoff = "12 Nowhere Street")

        // act
        val ruling = RuleJudge.judge(card, RULES, GAZETTEER, Stops.UNPLACED)

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
        val ruling = RuleJudge.judge(card, RULES, GAZETTEER, Stops.UNPLACED)

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
        val ruling = RuleJudge.judge(card, rules, GAZETTEER, Stops.UNPLACED)

        // assert
        assertTrue(ruling is Ruling.Take)
    }

    @Test
    fun `given a whitelisted chain with a destination outside the area, when judged, then the suburb still rules`() {
        // arrange
        val card = card(pickup = "KFC (Dandenong)", dropoff = "Bangholme Road, Dandenong South")
        val rules = RULES.copy(deniedStores = listOf("KFC"), alwaysOkStores = listOf("KFC"))

        // act
        val ruling = RuleJudge.judge(card, rules, GAZETTEER, Stops.UNPLACED)

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
        val ruling = RuleJudge.judge(card, rules, GAZETTEER, Stops.UNPLACED)

        // assert
        assertEquals(Ruling.Take("Dandenong South", far = true), ruling)
    }

    @Test
    fun `given a payout at the threshold, when judged, then the ordinary set still decides`() {
        // arrange  over, not at: thirty dollars exactly is an ordinary offer
        val rules = RULES.copy(farSuburbs = setOf("Dandenong South"))
        val card = card(pickup = "Some Shop", dropoff = "Bennet Street, Dandenong South", payout = Cents.ofDollars(30.0))

        // act
        val ruling = RuleJudge.judge(card, rules, GAZETTEER, Stops.UNPLACED)

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
        val ruling = RuleJudge.judge(card, rules, GAZETTEER, Stops.UNPLACED)

        // assert
        assertEquals(Ruling.Leave(Ruling.Reason.SuburbNotAllowed("Keysborough")), ruling)
    }

    @Test
    fun `given no far set drawn, when a big payout is judged, then the ordinary set decides`() {
        // arrange
        val card = card(pickup = "Some Shop", dropoff = "Bennet Street, Dandenong South", payout = Cents.ofDollars(60.0))

        // act
        val ruling = RuleJudge.judge(card, RULES, GAZETTEER, Stops.UNPLACED)

        // assert
        assertEquals(Ruling.Leave(Ruling.Reason.SuburbNotAllowed("Dandenong South")), ruling)
    }

    @Test
    fun `given a Lane on the card in a suburb he works, when judged, then the suburb alone decides`() {
        // arrange  the card of 13 Sep 18:50 - Bowman Lane there is an ordinary rural road
        val card = card(pickup = "Woolworths Keysborough", dropoff = "Bowman Lane & Keys Road, Keysborough")

        // act
        val ruling = RuleJudge.judge(card, RULES, GAZETTEER, Stops.UNPLACED)

        // assert
        assertEquals(Ruling.Take("Keysborough"), ruling)
    }

    @Test
    fun `given a dropoff at a junction inside a no-go box, when judged, then the box is the reason`() {
        // arrange  -37.97, 145.12 lies inside -38.00..-37.93 by 145.10..145.14
        val card = card(pickup = "Some Shop", dropoff = "Dandenong Road & Dunblane Road, Noble Park")
        val rules = RULES.copy(noGoBoxes = listOf(WEST_OF_SPRINGVALE))
        val stops = Stops(pickup = null, dropoff = Spot.AtCrossing(GeoPoint(-37.97, 145.12), "Dandenong Road", "Dunblane Road"), carAt = null)

        // act
        val ruling = RuleJudge.judge(card, rules, GAZETTEER, stops)

        // assert
        assertEquals("送餐点在「Springvale 西」里", RulingText.reason(ruling))
    }

    @Test
    fun `given a pickup shop inside a no-go box, when judged, then the shop and the box are the reason`() {
        // arrange
        val card = card(pickup = "Pho Hung", dropoff = "Some Street, Noble Park")
        val rules = RULES.copy(noGoBoxes = listOf(WEST_OF_SPRINGVALE))
        val shop = Store("Pho Hung", "restaurant", "STRIP", GeoPoint(-37.95, 145.13))
        val stops = Stops(pickup = shop, dropoff = null, carAt = null)

        // act
        val ruling = RuleJudge.judge(card, rules, GAZETTEER, stops)

        // assert
        assertEquals("取餐 Pho Hung 在「Springvale 西」里", RulingText.reason(ruling))
    }

    @Test
    fun `given a dropoff placed only by its suburb inside a no-go box, when judged, then the suburb list decides`() {
        // arrange  a suburb's middle is kilometres from the door, too rough for a box
        val card = card(pickup = "Some Shop", dropoff = "Somewhere, Noble Park")
        val rules = RULES.copy(noGoBoxes = listOf(WEST_OF_SPRINGVALE))
        val stops = Stops(pickup = null, dropoff = Spot.InSuburb(GeoPoint(-37.97, 145.12), "Noble Park"), carAt = null)

        // act
        val ruling = RuleJudge.judge(card, rules, GAZETTEER, stops)

        // assert
        assertEquals(Ruling.Take("Noble Park"), ruling)
    }

    @Test
    fun `given a dropoff at a junction just east of a no-go box, when judged, then the box does not refuse it`() {
        // arrange  145.141 is east of the box's 145.14 edge
        val card = card(pickup = "Some Shop", dropoff = "Dandenong Road & Dunblane Road, Noble Park")
        val rules = RULES.copy(noGoBoxes = listOf(WEST_OF_SPRINGVALE))
        val stops = Stops(pickup = null, dropoff = Spot.AtCrossing(GeoPoint(-37.97, 145.141), "Dandenong Road", "Dunblane Road"), carAt = null)

        // act
        val ruling = RuleJudge.judge(card, rules, GAZETTEER, stops)

        // assert
        assertEquals(Ruling.Take("Noble Park"), ruling)
    }

    @Test
    fun `given a far-set offer under ten dollars an hour after petrol, when judged, then it is left`() {
        // arrange  the 15 Sep 09:51 card: $40.14, 75 min, 58.9 km (36.6 mi)
        //          petrol 58.9 x 2 x 0.2 = 23.56; 40.14 - 23.56 = 16.58; 75 x 1.5 = 112.5 min = 1.875 h; 16.58 / 1.875 = 8.84
        val rules = RULES.copy(farSuburbs = setOf("Dandenong South"))
        val card = card(pickup = "Some Shop", dropoff = "Bennet Street, Dandenong South", payout = Cents.ofDollars(40.14))
            .copy(duration = Minutes(75), distance = Miles(36.6))

        // act
        val ruling = RuleJudge.judge(card, rules, GAZETTEER, Stops.UNPLACED)

        // assert
        assertEquals("远区单每小时 ${'$'}8.84，低于 ${'$'}10", RulingText.reason(ruling))
    }

    @Test
    fun `given a far-set offer over ten dollars an hour after petrol, when judged, then it is taken`() {
        // arrange  the 15 Sep 11:24 card: $35.46, 70 min, 35.9 km (22.3 mi) works out to $12.06
        val rules = RULES.copy(farSuburbs = setOf("Dandenong South"))
        val card = card(pickup = "Some Shop", dropoff = "Bennet Street, Dandenong South", payout = Cents.ofDollars(35.46))
            .copy(duration = Minutes(70), distance = Miles(22.3))

        // act
        val ruling = RuleJudge.judge(card, rules, GAZETTEER, Stops.UNPLACED)

        // assert
        assertEquals(Ruling.Take("Dandenong South", far = true), ruling)
    }

    @Test
    fun `given a far-set offer whose distance was unreadable, when judged, then the hour does not refuse it`() {
        // arrange
        val rules = RULES.copy(farSuburbs = setOf("Dandenong South"))
        val card = card(pickup = "Some Shop", dropoff = "Bennet Street, Dandenong South", payout = Cents.ofDollars(40.14))
            .copy(duration = Minutes(75), distance = null)

        // act
        val ruling = RuleJudge.judge(card, rules, GAZETTEER, Stops.UNPLACED)

        // assert
        assertEquals(Ruling.Take("Dandenong South", far = true), ruling)
    }

    @Test
    fun `given an ordinary-set offer under ten dollars an hour, when judged, then the hour does not refuse it`() {
        // arrange  $7.45, 17 min, 9.3 km (5.78 mi) works out to $8.78, but the floor is for the far set only
        val card = card(pickup = "Some Shop", dropoff = "Somewhere, Noble Park", payout = Cents.ofDollars(7.45))
            .copy(duration = Minutes(17), distance = Miles(5.78))

        // act
        val ruling = RuleJudge.judge(card, RULES, GAZETTEER, Stops.UNPLACED)

        // assert
        assertEquals(Ruling.Take("Noble Park"), ruling)
    }

    @Test
    fun `given the homeward switch on and a drop that leads away, when judged, then it is left`() {
        // arrange  the car is in Keysborough, the drop is out at Rowville, the centre is Aspendale Gardens
        val rules = RULES.copy(allowedSuburbs = RULES.allowedSuburbs + "Rowville", homewardCentre = CENTRE)
        val card = card(pickup = "Some Shop", dropoff = "Barbican Court, Rowville")
        val stops = Stops(pickup = null, dropoff = Spot.AtCrossing(ROWVILLE, "Barbican Court", "Bexsarm Crescent"), carAt = KEYSBOROUGH)

        // act
        val ruling = RuleJudge.judge(card, rules, GAZETTEER + Suburb("Rowville", ROWVILLE), stops)

        // assert
        assertEquals("离中心更远：现在 4.0 公里，送完 14.1 公里", RulingText.reason(ruling))
    }

    @Test
    fun `given the homeward switch on and a drop that leads in, when judged, then it is taken`() {
        // arrange  the car is out at Rowville, the drop is in Keysborough
        val rules = RULES.copy(homewardCentre = CENTRE)
        val card = card(pickup = "Some Shop", dropoff = "Ashleigh Street, Keysborough")
        val stops = Stops(pickup = null, dropoff = Spot.AtCrossing(KEYSBOROUGH, "Ashleigh Street", "Jean Court"), carAt = ROWVILLE)

        // act
        val ruling = RuleJudge.judge(card, rules, GAZETTEER, stops)

        // assert
        assertEquals(Ruling.Take("Keysborough"), ruling)
    }

    @Test
    fun `given the homeward switch on but no fix for the car, when judged, then it does not refuse`() {
        // arrange
        val rules = RULES.copy(allowedSuburbs = RULES.allowedSuburbs + "Rowville", homewardCentre = CENTRE)
        val card = card(pickup = "Some Shop", dropoff = "Barbican Court, Rowville")
        val stops = Stops(pickup = null, dropoff = Spot.AtCrossing(ROWVILLE, "Barbican Court", "Bexsarm Crescent"), carAt = null)

        // act
        val ruling = RuleJudge.judge(card, rules, GAZETTEER + Suburb("Rowville", ROWVILLE), stops)

        // assert
        assertEquals(Ruling.Take("Rowville"), ruling)
    }

    @Test
    fun `given the homeward switch off, when a drop that leads away is judged, then the suburb alone decides`() {
        // arrange  the same card as the first homeward test, with the switch off
        val rules = RULES.copy(allowedSuburbs = RULES.allowedSuburbs + "Rowville")
        val card = card(pickup = "Some Shop", dropoff = "Barbican Court, Rowville")
        val stops = Stops(pickup = null, dropoff = Spot.AtCrossing(ROWVILLE, "Barbican Court", "Bexsarm Crescent"), carAt = KEYSBOROUGH)

        // act
        val ruling = RuleJudge.judge(card, rules, GAZETTEER + Suburb("Rowville", ROWVILLE), stops)

        // assert
        assertEquals(Ruling.Take("Rowville"), ruling)
    }

    private companion object {
        /** Wells Rd, Aspendale Gardens - the centre drawn for the driver's own set. */
        val CENTRE = GeoPoint(-38.02506, 145.12873)
        val KEYSBOROUGH = GeoPoint(-38.0054, 145.1674)
        val ROWVILLE = GeoPoint(-37.9282, 145.2333)

        val WEST_OF_SPRINGVALE = NoGoBox("Springvale 西", south = -38.00, west = 145.10, north = -37.93, east = 145.14)

        val RULES = Rules(
            allowedSuburbs = setOf("Noble Park", "Keysborough", "Dandenong"),
            farSuburbs = emptySet(),
            farOverCents = Cents.ofDollars(30.0),
            deniedStores = emptyList(),
            alwaysOkStores = emptyList(),
            noGoBoxes = emptyList(),
            tripCost = TripCost(fuelPerKm = 0.2, timeFactor = 1.5),
            farMinPerHour = 10.0,
            homewardCentre = null,
        )
        val GAZETTEER = listOf(
            Suburb("Noble Park", GeoPoint(-37.9695, 145.1767)),
            Suburb("Keysborough", GeoPoint(-38.0054, 145.1674)),
            Suburb("Dandenong", GeoPoint(-37.9875, 145.2148)),
            Suburb("Dandenong South", GeoPoint(-38.028, 145.2209)),
        )
    }
}
