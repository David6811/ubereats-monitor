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
        assertEquals(0.9942, card.distance!!.value, 0.001)
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

    /**
     * The card read off the tree at 09:15:54 on 18 Sept, transcribed exactly. The
     * payout carries the currency in front of the sign, and nothing else about the
     * card changed - which is the whole of why that offer went unseen.
     */
    @Test
    fun `given a card whose payout names the currency, when read, then the payout is read`() {
        // arrange
        val lines = listOf(
            "Delivery",
            "Exclusive",
            "A\$13.01",
            "Est. earnings for completed trip",
            "29 min (18.6 km) total",
            "Mad Shak's Cafe",
            "Coolibar Avenue & Railway Parade, Seaford",
            "Accept",
        )

        // act
        val card = OfferCardReader.read(lines)

        // affirm
        assertNotNull(card)

        // assert
        assertEquals(Cents(1301), card!!.payout)
    }

    /**
     * The second of the three cards of 18 Sept, read off the tree at 09:41:15 and
     * transcribed exactly - a shopping order, so the bag weight sits between the
     * dropoff and the button.
     */
    @Test
    fun `given a named currency and a bag weight, when read, then the payout is read`() {
        // arrange
        val lines = listOf(
            "Delivery",
            "Exclusive",
            "A\$11.09",
            "Est. earnings for completed trip",
            "25 min (10.0 km) total",
            "Woolworths Seaford (VIC)",
            "Carrington Crescent & Royston Court, Carrum Downs",
            "Heavy (5-10 bags)",
            "Accept",
        )

        // act
        val card = OfferCardReader.read(lines)

        // affirm
        assertNotNull(card)

        // assert
        assertEquals(Cents(1109), card!!.payout)
    }

    /**
     * The third card of 18 Sept, at 09:53:44: an add-on to the trip already in
     * hand, where every number on the card is written as an increment. The payout
     * is what this stop adds, and that is the number to judge it by.
     */
    @Test
    fun `given an add-on card whose payout is an increment, when read, then the increment is the payout`() {
        // arrange
        val lines = listOf(
            "Delivery",
            "Exclusive",
            "+ A\$4",
            "Est. earnings for completed trip",
            "+ 6 min (+ 0.1 km) total",
            "Woolworths Seaford (VIC)",
            "Jacana Drive & Lewin Street, Carrum Downs",
            "Customer verification",
            "Accept",
        )

        // act
        val card = OfferCardReader.read(lines)

        // affirm
        assertNotNull(card)

        // assert
        assertEquals(Cents(400), card!!.payout)
    }

    /**
     * The navigation screen at 11:57 on 18 Sept, transcribed from the tree: the
     * day's earnings at the top, and a merchant note with "accept" in a sentence.
     * Read as a card, it held a verdict over the map for the whole drive.
     */
    @Test
    fun `given a navigation screen whose note says accept, when read, then it is not a card`() {
        // arrange
        val lines = listOf(
            "Home",
            "A\$36.29",
            "Search for places",
            "Gladesville Boulevard",
            "Third exit: Thompson Road",
            "100 m",
            "8 min",
            "8.3 km",
            "Merchant logo",
            "Poveretti Pizzeria",
            "243 Governor Road, Braeside, VIC 3195",
            "- Please have your WARMER BAG ready to accept order, otherwise we will now allow you to pick up.",
        )

        // act
        val card = OfferCardReader.read(lines)

        // assert
        assertNull(card)
    }

    @Test
    fun `given the real offer card, when tested for presence, then Accept plus a payout is enough`() {
        // arrange
        val lines = REAL_CARD

        // act
        val looksLikeCard = OfferCardReader.looksLikeCard(lines)

        // assert
        assertTrue(looksLikeCard)
    }

    @Test
    fun `given a card whose totals line was garbled, when read, then the card is still found`() {
        // arrange
        val lines = listOf(
            "Delivery", "\$5", "Est. earnings for completed trip",
            "1O min (l.6 krn) totaI",
            "Mario's Pizza And Pasta",
            "Cole Street & Nockolds Crescent, Noble Park",
            "Accept",
        )

        // act
        val card = OfferCardReader.read(lines)

        // affirm
        assertNotNull(card)

        // assert
        assertEquals("Cole Street & Nockolds Crescent, Noble Park", card!!.dropoff)
    }

    @Test
    fun `given a card whose totals line was garbled, when read, then the distance is null not zero`() {
        // arrange
        val lines = listOf("\$5", "1O min (l.6 krn) totaI", "Mario's Pizza And Pasta", "Noble Park", "Accept")

        // act
        val card = OfferCardReader.read(lines)!!

        // assert
        assertNull(card.distance)
    }

    @Test
    fun `given a batched card with three stops, when read, then the last stop is the dropoff`() {
        // arrange
        val lines = listOf(
            "Delivery (2)", "Batched", "\$14.20", "32 min (7.4 km) total",
            "Mario's Pizza And Pasta",
            "12 Cole Street, Noble Park",
            "30 Keating Cres, Dandenong",
            "Accept",
        )

        // act
        val card = OfferCardReader.read(lines)!!

        // assert
        assertEquals("30 Keating Cres, Dandenong", card.stops.last())
    }

    @Test
    fun `given a batched card with three stops, when read, then every stop is kept`() {
        // arrange
        val lines = listOf(
            "Delivery (2)", "Batched", "\$14.20", "32 min (7.4 km) total",
            "Mario's Pizza And Pasta",
            "12 Cole Street, Noble Park",
            "30 Keating Cres, Dandenong",
            "Accept",
        )

        // act
        val card = OfferCardReader.read(lines)!!

        // assert
        assertEquals(3, card.stops.size)
    }

    @Test
    fun `given the earnings page, when tested for presence, then a price alone is not a card`() {
        // arrange
        val lines = listOf("Wallet", "Balance", "\$44.49", "Next payout 14 Sept")

        // act
        val looksLikeCard = OfferCardReader.looksLikeCard(lines)

        // assert
        assertTrue(!looksLikeCard)
    }

    @Test
    fun `given an Accept button with a countdown, when tested, then the card is still recognised`() {
        // arrange
        val lines = listOf("Delivery", "\$5", "10 min (1.6 km) total", "Mario's", "Noble Park", "Accept 12s")

        // act
        val looksLikeCard = OfferCardReader.looksLikeCard(lines)

        // assert
        assertTrue(looksLikeCard)
    }

    @Test
    fun `given an Accept button described with its role, when tested, then the card is still recognised`() {
        // arrange
        val lines = listOf("Delivery", "\$5", "10 min (1.6 km) total", "Mario's", "Noble Park", "Accept, button")

        // act
        val looksLikeCard = OfferCardReader.looksLikeCard(lines)

        // assert
        assertTrue(looksLikeCard)
    }

    @Test
    fun `given the word Accepted in past tense, when tested, then it is not taken for the button`() {
        // arrange
        val lines = listOf("Trip Accepted", "\$5", "Acceptance rate 92%")

        // act
        val looksLikeCard = OfferCardReader.looksLikeCard(lines)

        // assert
        assertTrue(!looksLikeCard)
    }

    @Test
    fun `given the OCR of a real long haul card, when read, then the payout is read`() {
        // arrange
        val lines = REAL_OCR

        // act
        val card = OfferCardReader.read(lines)

        // affirm
        assertNotNull(card)

        // assert
        assertEquals(Cents(3545), card!!.payout)
    }

    @Test
    fun `given an hour and minutes in the totals, when read, then they add up`() {
        // arrange
        val lines = REAL_OCR

        // act
        val card = OfferCardReader.read(lines)!!

        // assert
        assertEquals(Minutes(66), card.duration)
    }

    @Test
    fun `given fifty three kilometres, when read, then it becomes miles`() {
        // arrange
        val lines = REAL_OCR

        // act
        val card = OfferCardReader.read(lines)!!

        // assert
        assertEquals(32.93, card.distance!!.value, 0.01)
    }

    @Test
    fun `given an address broken over two lines, when read, then it is put back together`() {
        // arrange
        val lines = REAL_OCR

        // act
        val card = OfferCardReader.read(lines)!!

        // assert
        assertEquals("Culverlands Street & Northern Road, Heidelberg West", card.dropoff)
    }

    @Test
    fun `given the OCR of a real card, when read, then the status bar is not taken as the pickup`() {
        // arrange
        val lines = REAL_OCR

        // act
        val card = OfferCardReader.read(lines)!!

        // assert
        assertEquals("17/110 Indian Dr, Keysborough VIC 3173, Australia", card.pickup)
    }

    @Test
    fun `given an address split over two lines with no comma, when read, then it is whole`() {
        // arrange
        val lines = GUZMAN_OCR

        // act
        val card = OfferCardReader.read(lines)!!

        // assert
        assertEquals("Bangholme Road & Mark Anthony Drive, Dandenong South", card.dropoff)
    }

    @Test
    fun `given OCR debris beside the button, when read, then it is not taken for an address`() {
        // arrange
        val lines = GUZMAN_OCR

        // act
        val card = OfferCardReader.read(lines)!!

        // assert
        assertEquals("Guzman y Gomez - Aspendale Gardens", card.pickup)
    }

    @Test
    fun `given the guzman card, when evaluated, then the metrics come out`() {
        // arrange
        val card = OfferCardReader.read(GUZMAN_OCR)!!

        // act
        val metrics = OfferEvaluator.metricsOf(OfferCardReader.toOffer(card))

        // assert
        assertEquals(27.15, metrics.payPerHour!!, 0.02)
    }

    @Test
    fun `given a Match button instead of Accept, when read, then it is still an offer card`() {
        // arrange
        val lines = MATCH_CARD

        // act
        val card = OfferCardReader.read(lines)

        // affirm
        assertNotNull(card)

        // assert
        assertEquals(Cents(855), card!!.payout)
    }

    @Test
    fun `given the Match card, when read, then its destination is whole`() {
        // arrange
        val lines = MATCH_CARD

        // act
        val card = OfferCardReader.read(lines)!!

        // assert
        assertEquals("Nettelbeck Road & Watton Close, Clayton South", card.dropoff)
    }

    @Test
    fun `given the Match card, when judged, then the same rules apply as to any offer`() {
        // arrange
        val card = OfferCardReader.read(MATCH_CARD)!!
        val rules = Rules(
            allowedSuburbs = setOf("Noble Park"),
            farSuburbs = emptySet(),
            farOverCents = Cents.ofDollars(30.0),
            deniedStores = emptyList(),
            alwaysOkStores = emptyList(),
            noGoBoxes = emptyList(),
            tripCost = TripCost(fuelPerKm = 0.2, timeFactor = 1.5),
            farMinPerHour = 10.0,
            homewardCentre = null,
        )
        val gazetteer = listOf(
            Suburb("Clayton South", GeoPoint(-37.9415, 145.1245)),
            Suburb("Noble Park", GeoPoint(-37.9695, 145.1767)),
        )

        // act
        val ruling = RuleJudge.judge(card, rules, gazetteer, Stops.UNPLACED)

        // assert
        assertEquals("Clayton South 不在名单里", RulingText.reason(ruling))
    }

    @Test
    fun `given the word Matched in past tense, when tested, then it is not the button`() {
        // arrange
        val lines = listOf("Trip Matched", "\$5", "Rematch later")

        // act
        val looksLikeCard = OfferCardReader.looksLikeCard(lines)

        // assert
        assertTrue(!looksLikeCard)
    }

    private companion object {
        /** Exactly what ML Kit read off the screen at 13:13 on 10 Sept. */
        val REAL_OCR = listOf(
            "4G", "13:13\u2192", "VO", "Templ\u00e9stowe", "Balwyn", "North",
            "Blackburn South", "GlenIris", "Glen Waverley", "Rowville", "Mulgrave",
            "Bentleigh East", "Cheltenham", "hdenong", "10",
            "Package Exclusive",
            "\$35.45",
            "* 4.81 Est. earnings for completed trip",
            ") lhr6 min (53.0 km) total",
            "17/110 Indian Dr, Keysborough VIC 3173,",
            "Australia",
            "Culverlands Street & Northern Road,",
            "Heidelberg West",
            "Accept",
        )

        /** ML Kit's read of the 13:25 card, debris and all. */
        val GUZMAN_OCR = listOf(
            "13:25", "Keysborough", "1", "EASTLIlNK", "HUTTON ROAD", "40",
            "Y? Delivery Exclusive", "X",
            "\$9.05",
            "Est. earnings for completed trip",
            "9 20 min (6.4 km) total",
            "Guzman y Gomez - Aspendale Gardens",
            "Bangholme Road & Mark Anthony",
            "Drive, Dandenong South",
            "Se",
            "Accept",
        )

        /** The 17:12 card: a black Match button, a Trip Radar offer. */
        val MATCH_CARD = listOf(
            "Delivery",
            "\$8.55",
            "Est. earnings for completed trip",
            "19 min (8.1 km) total",
            "Guzman y Gomez (Springvale)",
            "Nettelbeck Road & Watton Close,",
            "Clayton South",
            "Match",
        )

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

    @Test
    fun `given a distance whose digit OCR read as a letter, when the card is read, then the distance survives`() {
        // arrange  the real reading of a Match card: 8.1 km came back as 8.l km
        val lines = listOf(
            "Delivery X",
            "${'$'}8.55",
            "Est. earnings for completed trip",
            "G) 19 min (8.l km) total",
            "9 Guzman y Gomez (Springvale)",
            "Nettelbeck Road & Watton Close,",
            "Clayton South",
            "Match",
        )

        // act
        val card = OfferCardReader.read(lines)

        // affirm
        assertEquals(19, card?.duration?.value)

        // assert  8.1 km in miles
        assertEquals(5.03, card?.distance?.value ?: 0.0, 0.01)
    }

    @Test
    fun `given a street whose name ends in a letter after a number, when the card is read, then the address is left alone`() {
        // arrange
        val lines = listOf(
            "${'$'}5.00",
            "10 min (1.6 km) total",
            "Mario's Pizza And Pasta",
            "12 Cole Street, Noble Park",
            "Accept",
        )

        // act
        val card = OfferCardReader.read(lines)

        // assert
        assertEquals("12 Cole Street, Noble Park", card?.dropoff)
    }
}
