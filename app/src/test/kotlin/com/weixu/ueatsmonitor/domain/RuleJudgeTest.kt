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
        assertEquals("Dandenong South 不在名单里", RulingText.reason(ruling, Lang.CHINESE))
    }

    @Test
    fun `given no rules at all, when judged, then the app says so rather than deciding`() {
        // arrange
        val card = card(pickup = "Anything", dropoff = "Anywhere, Noble Park")

        // act
        val ruling = RuleJudge.judge(card, Rules(emptySet(), emptyList(), TripCost(0.2, 1.5), null), GAZETTEER, Stops.UNPLACED)

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
        assertEquals("送餐点在「Springvale 西」里", RulingText.reason(ruling, Lang.CHINESE))
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
        assertEquals("取餐 Pho Hung 在「Springvale 西」里", RulingText.reason(ruling, Lang.CHINESE))
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
        val rules = RULES.copy(allowedSuburbs = RULES.allowedSuburbs + "Rowville", homeward = HOMEWARD)
        val card = card(pickup = "Some Shop", dropoff = "Barbican Court, Rowville")
        val stops = Stops(pickup = null, dropoff = Spot.AtCrossing(ROWVILLE, "Barbican Court", "Bexsarm Crescent"), carAt = KEYSBOROUGH)

        // act
        val ruling = RuleJudge.judge(card, rules, GAZETTEER + Suburb("Rowville", ROWVILLE), stops)

        // assert
        assertEquals("离中心更远：现在 4.0 公里，送完 14.1 公里", RulingText.reason(ruling, Lang.CHINESE))
    }

    @Test
    fun `given the homeward switch on and a drop that leads in, when judged, then it is taken`() {
        // arrange  the car is out at Rowville, the drop is in Keysborough
        val rules = RULES.copy(homeward = HOMEWARD)
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
        val rules = RULES.copy(allowedSuburbs = RULES.allowedSuburbs + "Rowville", homeward = HOMEWARD)
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

    @Test
    fun `given the homeward switch on and a drop that leads away but lands near the centre, when judged, then it is taken`() {
        // arrange  18 Sept, 12:13: the car about 2 km from the centre, the drop at Westbrook Drive about 3.3 km out
        val card = card(pickup = "La Cabra Mordialloc", dropoff = "Kawarra Drive & Westbrook Drive, Keysborough")
        val stops = Stops(pickup = null, dropoff = Spot.AtCrossing(WESTBROOK_DRIVE, "Kawarra Drive", "Westbrook Drive"), carAt = NEAR_CENTRE)

        // act
        val ruling = RuleJudge.judge(card, RULES.copy(homeward = HOMEWARD), GAZETTEER, stops)

        // affirm  it really does lead away
        assertTrue(HomewardRule.judge(NEAR_CENTRE, WESTBROOK_DRIVE, CENTRE) is Homeward.Further)

        // assert
        assertEquals(Ruling.Take("Keysborough"), ruling)
    }

    @Test
    fun `given the homeward switch on and a job over the time limit, when judged, then it is left however near`() {
        // arrange  the same near drop, but the card says 37 minutes against a limit of 20
        val card = card(pickup = "La Cabra Mordialloc", dropoff = "Kawarra Drive & Westbrook Drive, Keysborough")
            .copy(duration = Minutes(37))
        val stops = Stops(pickup = null, dropoff = Spot.AtCrossing(WESTBROOK_DRIVE, "Kawarra Drive", "Westbrook Drive"), carAt = NEAR_CENTRE)

        // act
        val ruling = RuleJudge.judge(card, RULES.copy(homeward = HOMEWARD), GAZETTEER, stops)

        // assert
        assertEquals("要 37 分钟，超过 20 分钟", RulingText.reason(ruling, Lang.CHINESE))
    }

    @Test
    fun `given the homeward switch on and a job of exactly the time limit, when judged, then it is not refused for time`() {
        // arrange  20 minutes against a limit of 20, and a drop that leads in
        val card = card(pickup = "Some Shop", dropoff = "Ashleigh Street, Keysborough").copy(duration = Minutes(20))
        val stops = Stops(pickup = null, dropoff = Spot.AtCrossing(KEYSBOROUGH, "Ashleigh Street", "Jean Court"), carAt = ROWVILLE)

        // act
        val ruling = RuleJudge.judge(card, RULES.copy(homeward = HOMEWARD), GAZETTEER, stops)

        // assert
        assertEquals(Ruling.Take("Keysborough"), ruling)
    }

    @Test
    fun `given the homeward switch on and no time on the card, when judged, then it is not refused for time`() {
        // arrange
        val card = card(pickup = "Some Shop", dropoff = "Ashleigh Street, Keysborough").copy(duration = null)
        val stops = Stops(pickup = null, dropoff = Spot.AtCrossing(KEYSBOROUGH, "Ashleigh Street", "Jean Court"), carAt = ROWVILLE)

        // act
        val ruling = RuleJudge.judge(card, RULES.copy(homeward = HOMEWARD), GAZETTEER, stops)

        // assert
        assertEquals(Ruling.Take("Keysborough"), ruling)
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

    private companion object {
        /** Wells Rd, Aspendale Gardens - the centre drawn for the driver's own set. */
        val CENTRE = GeoPoint(-38.02506, 145.12873)
        val KEYSBOROUGH = GeoPoint(-38.0054, 145.1674)
        val ROWVILLE = GeoPoint(-37.9282, 145.2333)

        /** About 2 km west of the centre - near where the car was at 12:13 on 18 Sept. */
        val NEAR_CENTRE = GeoPoint(-38.0180, 145.1080)

        /** Kawarra Drive & Westbrook Drive, Keysborough - about 3.3 km east of the centre. */
        val WESTBROOK_DRIVE = GeoPoint(-38.0146, 145.1640)

        val HOMEWARD = HomewardLimits(CENTRE, nearKm = 4.0, maxMinutes = 20)

        val WEST_OF_SPRINGVALE = NoGoBox("Springvale 西", south = -38.00, west = 145.10, north = -37.93, east = 145.14)

        val RULES = Rules(
            allowedSuburbs = setOf("Noble Park", "Keysborough", "Dandenong"),
            noGoBoxes = emptyList(),
            tripCost = TripCost(fuelPerKm = 0.2, timeFactor = 1.5),
            homeward = null,
        )
        val GAZETTEER = listOf(
            Suburb("Noble Park", GeoPoint(-37.9695, 145.1767)),
            Suburb("Keysborough", GeoPoint(-38.0054, 145.1674)),
            Suburb("Dandenong", GeoPoint(-37.9875, 145.2148)),
            Suburb("Dandenong South", GeoPoint(-38.028, 145.2209)),
        )
    }
}
