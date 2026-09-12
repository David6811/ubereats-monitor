package com.weixu.ueatsmonitor.domain

import kotlin.math.abs

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
    /** The two notes in Chinese, once the phone has translated them. */
    val noteCn: String?,
    val dropNoteCn: String?,
)

/** Data. What the rules said about an offer. The machine's opinion, and only that. */
enum class Advice {
    WORTH_TAKING,
    NOT_WORTH_TAKING,
    ;

    companion object {
        /** Calculation. The headline the judge wrote, read back as the two cases it has. */
        fun of(offer: OfferRecord): Advice =
            if (offer.ruling?.startsWith("可以") == true) WORTH_TAKING else NOT_WORTH_TAKING
    }
}

/**
 * Data. One of the three lists in the work area.
 *
 * Not a classification: a job belongs to every shelf that is true of it, so one
 * taken job appears twice. What the rules advised and what the driver did are
 * separate facts about it, and collapsing them hid the single most useful row
 * on the board - the job taken against the advice, which vanished from the
 * shelf that advised against it the moment it was accepted.
 */
enum class Shelf {
    TAKEN,
    WORTH_TAKING,
    NOT_WORTH_TAKING,
    ;

    /** Calculation. Whether this shelf shows that job. */
    fun holds(job: Job): Boolean = when (this) {
        TAKEN -> job.taken
        WORTH_TAKING -> Advice.of(job.offer) == Advice.WORTH_TAKING
        NOT_WORTH_TAKING -> Advice.of(job.offer) == Advice.NOT_WORTH_TAKING
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
        val next = listOf(job) + jobs
        // The cap is about how many offers are worth still looking at. A job in
        // hand is not an offer any more, so it does not compete for the room -
        // a busy half hour used to push the delivery being driven off the board.
        val kept = next.filterNot { it.taken }.take(CAPACITY).map { it.atMillis }.toSet()
        return next.filter { it.taken || it.atMillis in kept }
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

        val named = jobs.indices.filter { fold(jobs[it].offer.pickup).contains(wanted) }
        if (named.isEmpty()) return jobs

        // The bracket after a chain's name tells two of its branches apart, and
        // that is the only thing it is good for. It is not always a suburb: as
        // often it is the shopping centre, and a centre's name never appears in
        // the street address it stands on - "Pizza Hut (Parkmore)" is picked up
        // at 317 Cheltenham Rd, Keysborough. Asking the address to agree with it
        // therefore refused every pickup from a centre, and the job kept the
        // card's address instead of the real one.
        //
        // So it is asked only when there is something to decide. One job naming
        // the chain is that job. Two, with neither branch in the address, is a
        // question this screen cannot answer - and answering it wrong sends the
        // driver to another branch, so nothing is claimed at all.
        val at = if (named.size == 1) {
            named.first()
        } else {
            named.firstOrNull { index ->
                val branch = branchOf(jobs[index].offer.pickup)
                branch != null && address.contains(fold(branch))
            } ?: return jobs
        }
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

    /** How long one card can go on being read. It sits on screen for about a minute. */
    private const val SAME_OFFER_MILLIS = 90_000L

    /**
     * The same card, read again. The text cannot be what decides: OCR reads the
     * card every couple of seconds and rarely twice the same way - "ar Ashleigh
     * Street & Jean Court, Keysborough" and "a Ashleigh Street & Jean Court, sb
     * Keysborough" were one offer, and counting them apart filled four of the six
     * places with it. The money is read from large digits and comes out stable,
     * and a card is only on screen for about a minute, so money within a window
     * is what says the same offer.
     */
    private fun sameOffer(job: Job, other: Job): Boolean =
        job.offer.payout == other.offer.payout &&
            job.offer.isMatch == other.offer.isMatch &&
            abs(job.atMillis - other.atMillis) <= SAME_OFFER_MILLIS
}
