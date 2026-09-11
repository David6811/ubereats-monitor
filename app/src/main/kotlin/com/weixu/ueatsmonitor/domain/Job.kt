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
    /** The shop's full street address, which only the pickup screen carries. */
    val address: String?,
    /** What the shop wrote about finding it - often where to park. */
    val note: String?,
    /** The customer's full street address, which only the delivery screen carries. */
    val dropAddress: String?,
    /** Their unit or floor, when the address has one. */
    val dropUnit: String?,
    /** What the customer wrote about reaching their door. */
    val dropNote: String?,
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

    /**
     * The pickup screen says a job was taken and carries the shop's full address.
     * It names the shop, so the newest job whose pickup names the same shop is the
     * one it belongs to. Nothing matches when the offer was never read - the
     * screen alone is not enough to build a job from, since it says nothing about
     * where the food is going or what it pays.
     */
    fun taken(jobs: List<Job>, pickup: Pickup): List<Job> {
        val wanted = fold(pickup.store)
        if (wanted.length < MIN_STORE) return jobs
        val address = fold(pickup.address)

        val at = jobs.indexOfFirst { job ->
            val card = fold(job.offer.pickup)
            if (!card.contains(wanted)) return@indexOfFirst false
            // Two offers from one chain can sit on the board at once, and taking
            // the wrong one would put another branch's address on the card the
            // driver then navigates to. The card usually names the branch's
            // suburb in brackets and the screen's address always names one, so
            // when both say a suburb they have to say the same suburb.
            val branch = branchOf(job.offer.pickup)
            branch == null || address.contains(fold(branch))
        }
        if (at < 0) return jobs
        return jobs.mapIndexed { index, job ->
            if (index != at) job
            else job.copy(taken = true, address = pickup.address, note = pickup.note)
        }
    }

    /** What the offer card put in brackets after the shop name, which is its suburb. */
    private fun branchOf(pickup: String): String? =
        Regex("""\(([^)]{3,40})\)""").find(pickup)?.groupValues?.get(1)?.trim()

    /**
     * The delivery screen carries the customer's address but never names the shop
     * or the money, so the suburb is what ties it to a job. The job it belongs to
     * is the newest whose destination names that suburb - and one already known
     * to be taken wins, since that is the one being delivered.
     */
    fun delivered(jobs: List<Job>, dropoff: Dropoff, suburb: String?): List<Job> {
        if (suburb.isNullOrBlank()) return jobs
        val wanted = fold(suburb)
        if (wanted.length < MIN_SUBURB) return jobs

        val matches = jobs.indices.filter { at -> fold(jobs[at].offer.dropoff).contains(wanted) }
        val at = matches.firstOrNull { jobs[it].taken } ?: matches.firstOrNull() ?: return jobs

        return jobs.mapIndexed { index, job ->
            if (index != at) job
            else job.copy(
                taken = true,
                dropAddress = dropoff.address,
                dropUnit = dropoff.unit,
                dropNote = dropoff.note,
            )
        }
    }

    private const val MIN_STORE = 4

    /** A suburb name shorter than this would match half of Melbourne by accident. */
    private const val MIN_SUBURB = 4

    private fun fold(text: String): String = text.lowercase().filter { it.isLetterOrDigit() }

    /** Same money to the same address is the same job, whatever OCR made of the shop name. */
    private fun sameOffer(job: Job, other: Job): Boolean =
        job.offer.payout == other.offer.payout &&
            job.offer.dropoff == other.offer.dropoff &&
            job.offer.isMatch == other.offer.isMatch
}
