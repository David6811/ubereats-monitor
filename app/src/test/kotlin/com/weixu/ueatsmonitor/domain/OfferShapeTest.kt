package com.weixu.ueatsmonitor.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfferShapeTest {

    @Test
    fun `given a real offer card, when shaped, then it looks like an offer`() {
        // arrange
        val text = OFFER_CARD

        // act
        val looksLikeOffer = OfferShape.looksLikeOffer(text)

        // assert
        assertTrue(looksLikeOffer)
    }

    @Test
    fun `given the earnings page, when shaped, then money alone is not an offer`() {
        // arrange
        val text = EARNINGS_PAGE

        // act
        val looksLikeOffer = OfferShape.looksLikeOffer(text)

        // assert
        assertFalse(looksLikeOffer)
    }

    @Test
    fun `given a navigation screen with distance but no money, when shaped, then it is not an offer`() {
        // arrange
        val text = "Gleneagles Drive\n1 min\n0.6 km\nExpected by 1:01 PM"

        // act
        val looksLikeOffer = OfferShape.looksLikeOffer(text)

        // assert
        assertFalse(looksLikeOffer)
    }

    @Test
    fun `given the chinese wording of the same card, when shaped, then it still looks like an offer`() {
        // arrange
        val text = "外送 (2) 獨享\n\$86\n27 分鐘 (6.0 公里) 總計\n斗六當歸鴨 福壽店"

        // act
        val looksLikeOffer = OfferShape.looksLikeOffer(text)

        // assert
        assertTrue(looksLikeOffer)
    }

    private companion object {
        val OFFER_CARD = """
            Delivery (2) Exclusive
            ${'$'}18.03
            27 min (6.0 km) total
            Coles - Keysborough
            30 Keating Cres, Dandenong VIC 3175
            Accept
        """.trimIndent()

        val EARNINGS_PAGE = """
            Wallet
            Balance
            ${'$'}44.49
            Next payout 14 Sept at 4:00 am
        """.trimIndent()
    }
}
