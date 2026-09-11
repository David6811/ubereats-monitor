package com.weixu.ueatsmonitor.domain

/** Data. One job on the board: an offer that appeared, with when it appeared. */
data class Job(
    val atMillis: Long,
    val offer: OfferRecord,
    /**
     * True once the offer is known to have been accepted. Nothing on screen says
     * so yet, so this is false on everything the reader writes today - the shelf
     * it belongs on exists, and stays empty until the screen can tell us.
     */
    val taken: Boolean,
)

/** Data. Which shelf of the work area a job sits on. */
enum class Shelf {
    TAKEN,
    WORTH_TAKING,
    NOT_WORTH_TAKING,
    ;

    companion object {
        /**
         * Calculation. A job goes where its verdict puts it, unless it is known to
         * have been taken - then it belongs with the work in hand whatever the
         * rules said about it.
         */
        fun of(job: Job): Shelf = when {
            job.taken -> TAKEN
            job.offer.ruling?.startsWith("可以") == true -> WORTH_TAKING
            else -> NOT_WORTH_TAKING
        }
    }
}

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
