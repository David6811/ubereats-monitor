package com.weixu.ueatsmonitor.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class JobDaysTest {

    @Test
    fun `given jobs either side of midnight, when today's are asked for, then only those after midnight come back`() {
        // arrange  23:59 on the 14th and 00:01 on the 15th, Melbourne time
        val jobs = listOf(job(AT_2359_ON_14TH), job(AT_0001_ON_15TH))

        // act
        val today = JobDays.today(jobs, FIFTEENTH, MELBOURNE)

        // assert
        assertEquals(listOf(AT_0001_ON_15TH), today.map { it.atMillis })
    }

    @Test
    fun `given jobs on two earlier days, when they are grouped, then each day is its own group, most recent first`() {
        // arrange
        val jobs = listOf(job(AT_0900_ON_13TH), job(AT_2359_ON_14TH), job(AT_0001_ON_15TH), job(AT_1000_ON_14TH))

        // act
        val earlier = JobDays.earlier(jobs, FIFTEENTH, MELBOURNE)

        // assert
        assertEquals(
            listOf(
                JobDay(LocalDate.of(2026, 9, 14), listOf(job(AT_2359_ON_14TH), job(AT_1000_ON_14TH))),
                JobDay(LocalDate.of(2026, 9, 13), listOf(job(AT_0900_ON_13TH))),
            ),
            earlier,
        )
    }

    private fun job(atMillis: Long) = Job(
        atMillis = atMillis,
        offer = OfferRecord(isMatch = false, payout = "${'$'}9.07", pickup = "Some Shop", dropoff = "Somewhere", ruling = null, why = null, fromTree = false),
        taken = true,
        address = null, note = null, dropAddress = null, dropUnit = null, dropNote = null, noteCn = null, dropNoteCn = null,
        extraDrops = emptyList(), ordersAtPickup = 1,
    )

    private companion object {
        val MELBOURNE: ZoneId = ZoneId.of("Australia/Melbourne")
        val FIFTEENTH: LocalDate = LocalDate.of(2026, 9, 15)

        // Melbourne is UTC+10 in September.
        const val AT_0900_ON_13TH = 1_789_254_000_000L // 2026-09-12T23:00Z
        const val AT_1000_ON_14TH = 1_789_344_000_000L // 2026-09-14T00:00Z
        const val AT_2359_ON_14TH = 1_789_394_340_000L // 2026-09-14T13:59Z
        const val AT_0001_ON_15TH = 1_789_394_460_000L // 2026-09-14T14:01Z
    }
}
