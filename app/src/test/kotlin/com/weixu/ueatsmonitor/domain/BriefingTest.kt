package com.weixu.ueatsmonitor.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class BriefingTest {

    @Test
    fun `given one job in hand and one refused offer, when written out, then the assistant gets both in order`() {
        // arrange  now is 13:40 Melbourne; the job was taken 25 minutes earlier, the offer refused 3 minutes ago
        TimeZone.setDefault(TimeZone.getTimeZone("Australia/Melbourne"))
        val now = 1_789_962_000_000L // 2026-09-21 13:40 AEST
        val inHand = Job(
            atMillis = now - 25 * 60_000,
            offer = cabra,
            taken = true,
            address = "1 Main St, Mordialloc",
            note = null,
            dropAddress = "39 Westbrook Drive, Keysborough",
            dropUnit = null,
            dropNote = "Press doorbell",
            noteCn = null,
            dropNoteCn = "请按门铃",
            extraDrops = emptyList(),
        )
        val refused = Job(now - 3 * 60_000, woolies, taken = false, address = null, note = null, dropAddress = null, dropUnit = null, dropNote = null, noteCn = null, dropNoteCn = null, extraDrops = emptyList())
        val where = Briefing.Whereabouts(carAt = GeoPoint(-38.0054, 145.1674), centre = GeoPoint(-38.02506, 145.12873), nearestSuburb = "Keysborough")

        // act
        val text = Briefing.text(now, listOf(refused, inHand), where, "ParkMore")

        // assert  distance Keysborough -> centre is about 4.0 km (checked by hand with the haversine formula)
        assertEquals(
            """
            时间 2026年9月21日 星期一 13:40，墨尔本
            车的位置：在 Keysborough 附近，离选区中心 4.0 公里
            当前选区：ParkMore

            手上的单（最新的在前）：
            1. ${'$'}16.22，13:15 接的（25 分钟前）。取餐：La Cabra Mordialloc，地址 1 Main St, Mordialloc，已到店。送餐：39 Westbrook Drive, Keysborough，已在送餐页面。客户留言：请按门铃。

            最近没接的派单：
            - ${'$'}14.40，3 分钟前，Woolworths Keysborough 送到 David St, Dandenong，判断：不要接单，理由：要 32 分钟，超过 12 分钟
            """.trimIndent(),
            text,
        )
    }

    @Test
    fun `given an English driver, when the same board is written out, then it is written in English`() {
        // arrange  the same job as above, and the same moment
        TimeZone.setDefault(TimeZone.getTimeZone("Australia/Melbourne"))
        val now = 1_789_962_000_000L // 2026-09-21 13:40 AEST
        val inHand = Job(
            atMillis = now - 25 * 60_000,
            offer = cabra,
            taken = true,
            address = "1 Main St, Mordialloc",
            note = null,
            dropAddress = "39 Westbrook Drive, Keysborough",
            dropUnit = null,
            dropNote = "Press doorbell",
            noteCn = null,
            dropNoteCn = "请按门铃",
            extraDrops = emptyList(),
        )
        val where = Briefing.Whereabouts(carAt = GeoPoint(-38.0054, 145.1674), centre = GeoPoint(-38.02506, 145.12873), nearestSuburb = "Keysborough")

        // act
        val text = Briefing.text(now, listOf(inHand), where, "ParkMore", lang = Lang.ENGLISH)

        // assert  the note goes up in the customer's own words, not the translation
        assertEquals(
            """
            Time Monday 21 September 2026 13:40, Melbourne
            Where the car is: near Keysborough, 4.0 km from the set's centre
            Live set: ParkMore

            In the car (newest first):
            1. ${'$'}16.22, taken at 13:15 (25 min ago). Pick up: La Cabra Mordialloc, at 1 Main St, Mordialloc, already at the shop. Drop off: 39 Westbrook Drive, Keysborough, delivery screen reached. Customer note: Press doorbell.
            """.trimIndent(),
            text,
        )
    }

    @Test
    fun `given no position and no jobs, when written out, then it says so rather than leaving blanks`() {
        // arrange
        val where = Briefing.Whereabouts(carAt = null, centre = null, nearestSuburb = null)

        // act
        val text = Briefing.text(0L, emptyList(), where, null)

        // assert
        assertTrue(text.contains("车的位置：不知道") && text.contains("手上没有正在送的单"))
    }

    private companion object {
        val cabra = OfferRecord(
            isMatch = false,
            payout = "${'$'}16.22",
            pickup = "La Cabra Mordialloc",
            dropoff = "Kawarra Drive & Westbrook Drive, Keysborough",
            ruling = "不要接单",
            why = "离中心更远：现在 1.9 公里，送完 3.6 公里",
            fromTree = true,
        )
        val woolies = OfferRecord(
            isMatch = false,
            payout = "${'$'}14.40",
            pickup = "Woolworths Keysborough",
            dropoff = "David St, Dandenong",
            ruling = "不要接单",
            why = "要 32 分钟，超过 12 分钟",
            fromTree = true,
        )
    }
}
