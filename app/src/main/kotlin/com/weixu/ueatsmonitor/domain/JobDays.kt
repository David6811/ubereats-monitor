package com.weixu.ueatsmonitor.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Data. The jobs of one earlier day, newest first. */
data class JobDay(val date: LocalDate, val jobs: List<Job>)

/**
 * Calculation. Splits the board at local midnight: today's jobs, and every
 * earlier day on its own. The counts the driver reads are today's, so they go
 * back to nothing when the day turns over.
 */
object JobDays {

    fun dateOf(job: Job, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(job.atMillis).atZone(zone).toLocalDate()

    fun today(jobs: List<Job>, today: LocalDate, zone: ZoneId): List<Job> =
        jobs.filter { dateOf(it, zone) == today }

    /** Days before today, the most recent first; a job dated after today stays with today's. */
    fun earlier(jobs: List<Job>, today: LocalDate, zone: ZoneId): List<JobDay> =
        jobs.filter { dateOf(it, zone) < today }
            .groupBy { dateOf(it, zone) }
            .map { (date, those) -> JobDay(date, those.sortedByDescending { it.atMillis }) }
            .sortedByDescending { it.date }
}
