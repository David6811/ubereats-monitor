package com.weixu.ueatsmonitor.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OfferRecordReaderTest {

    private val withCard = """
        package=com.ubercab.driver
        millis=1789034250001
        source=ocr
        card=true
        card_payout=${'$'}5.00
        card_minutes=10
        card_miles=0.99
        card_pickup=Mario's Pizza And Pasta
        card_dropoff=Cole Street & Nockolds Crescent, Noble Park
        card_kind=accept
        ruling=可以接单
        ruling_why=Noble Park 在去的区里
        ---
        Delivery
        ${'$'}5
    """.trimIndent()

    private val withoutCard = """
        package=com.tencent.mm
        millis=1789034250001
        card=false
        money=false
        ---
        微信
    """.trimIndent()

    @Test
    fun `given a capture with a card, when it is read, then the two stops come back`() {
        // arrange
        val raw = withCard

        // act
        val record = OfferRecordReader.read(raw)

        // affirm
        assertEquals("可以接单", record?.ruling)

        // assert
        assertEquals(
            "Mario's Pizza And Pasta" to "Cole Street & Nockolds Crescent, Noble Park",
            record?.pickup to record?.dropoff,
        )
    }

    @Test
    fun `given a capture with no card, when it is read, then nothing comes back`() {
        // arrange
        val raw = withoutCard

        // act
        val record = OfferRecordReader.read(raw)

        // assert
        assertNull(record)
    }

    @Test
    fun `given a match card, when it is read, then it is marked as a match`() {
        // arrange
        val raw = withCard.replace("card_kind=accept", "card_kind=match")

        // act
        val record = OfferRecordReader.read(raw)

        // assert
        assertEquals(true, record?.isMatch)
    }

    @Test
    fun `given a card read before the rules ruled, when it is read, then the ruling is absent`() {
        // arrange
        val raw = withCard
            .replace("ruling=可以接单\n", "")
            .replace("ruling_why=Noble Park 在去的区里\n", "")

        // act
        val record = OfferRecordReader.read(raw)

        // affirm
        assertEquals("${'$'}5.00", record?.payout)

        // assert
        assertNull(record?.ruling)
    }
}
