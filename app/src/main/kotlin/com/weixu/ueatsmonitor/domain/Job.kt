package com.weixu.ueatsmonitor.domain

/** Data. One job on the board: an offer that appeared, with when it appeared. */
data class Job(
    val atMillis: Long,
    val offer: OfferRecord,
)

/**
 * Calculation. What the board holds after an offer appears.
 *
 * An offer is added, never made to replace what is already there: two deliveries
 * at once is ordinary, and replacing would lose the first one. What is not added
 * is the same offer again - the card sits on screen for a minute and is read
 * every couple of seconds.
 */
object JobBoard {

    /** How many to keep. More than a handful in hand at once does not happen. */
    const val CAPACITY = 6

    fun add(jobs: List<Job>, job: Job): List<Job> {
        if (jobs.any { sameOffer(it, job) }) return jobs
        return (listOf(job) + jobs).take(CAPACITY)
    }

    fun remove(jobs: List<Job>, atMillis: Long): List<Job> = jobs.filterNot { it.atMillis == atMillis }

    /** Same money to the same address is the same job, whatever OCR made of the shop name. */
    private fun sameOffer(job: Job, other: Job): Boolean =
        job.offer.payout == other.offer.payout &&
            job.offer.dropoff == other.offer.dropoff &&
            job.offer.isMatch == other.offer.isMatch
}
