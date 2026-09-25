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
    /**
     * The other deliveries of a batched offer. Uber's card for "Delivery (2)"
     * names one destination only; the second appears on its own delivery screen
     * half an hour later, naming a suburb no job on the board mentions.
     */
    val extraDrops: List<Drop>,
    /**
     * How many orders the pickup screen said were being collected. One until
     * that screen has been read. More than one and a second delivery is coming,
     * whether or not its screen has been seen yet.
     */
    val ordersAtPickup: Int,
)

/** Data. One more delivery of the same offer: where it goes and what the customer wrote. */
data class Drop(val address: String, val unit: String?, val note: String?)

/** Data. What the rules said about an offer. The machine's opinion, and only that. */
enum class Advice {
    WORTH_TAKING,
    NOT_WORTH_TAKING,
    ;

    companion object {
        /** Calculation. The headline the judge wrote, read back as the two cases it has. */
        fun of(offer: OfferRecord): Advice =
            if (offer.ruling != null && RulingText.saysTake(offer.ruling)) WORTH_TAKING else NOT_WORTH_TAKING
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

    /**
     * No cap. A cap of six pushed offers off the board during a busy half hour;
     * the driver clears jobs by hand, so the board only ever holds what he kept.
     */
    fun add(jobs: List<Job>, job: Job): List<Job> {
        val at = jobs.indexOfFirst { sameOffer(it, job) }
        if (at < 0) return listOf(job) + jobs

        // The same offer again. Its first reading was off the picture, and this
        // one is off the tree: keep the job, its stops and its addresses, and
        // take the words from the tree, which cannot mistake a letter.
        val held = jobs[at]
        if (!job.offer.fromTree || held.offer.fromTree) return jobs
        return jobs.mapIndexed { index, one -> if (index == at) one.copy(offer = job.offer) else one }
    }

    fun remove(jobs: List<Job>, atMillis: Long): List<Job> = jobs.filterNot { it.atMillis == atMillis }

    /**
     * The pickup screen says a job was taken and carries the shop's full address.
     * It names the shop, so the newest job whose pickup names the same shop is the
     * one it belongs to. Nothing matches when the offer was never read - the
     * screen alone is not enough to build a job from, since it says nothing about
     * where the food is going or what it pays.
     */
    fun taken(jobs: List<Job>, pickup: Pickup, now: Long): List<Job> {
        val wanted = fold(pickup.store)
        if (wanted.length < MIN_STORE) return jobs
        val address = fold(pickup.address)
        val recent = jobs.indices.filter { now - jobs[it].atMillis <= IN_HAND_MILLIS }

        // Either name can be the longer one. OCR cuts the card's short: the card
        // said "Chemist2u) Pharmacy 4 Less" for "(Chemist2U) Pharmacy 4 Less Parkmore".
        val named = recent.filter { index ->
            val onCard = fold(jobs[index].offer.pickup)
            onCard.contains(wanted) || (onCard.length >= MIN_CARD_STORE && wanted.contains(onCard))
        }
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
        // Two orders from the same shop, one after the other - a second Coles
        // order added while the first was being collected - both name it, and
        // the one already collected is not the one this screen is about.
        val open = named.filterNot { jobs[it].taken }
        val at = when {
            named.size == 1 -> named.first()
            open.size == 1 -> open.first()
            else -> named.firstOrNull { index ->
                val branch = branchOf(jobs[index].offer.pickup)
                branch != null && address.contains(fold(branch))
            } ?: return jobs
        }
        return jobs.mapIndexed { index, job ->
            if (index != at) job
            else job.copy(taken = true, address = pickup.address, note = pickup.note, ordersAtPickup = pickup.orders)
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
    fun delivered(jobs: List<Job>, dropoff: Dropoff, suburb: String?, now: Long): List<Job> {
        if (suburb.isNullOrBlank()) return jobs

        // Only what could still be in the car. The board keeps every offer of the
        // last few days, and a job taken two days ago to the same suburb used to
        // claim today's delivery, because a taken job wins over an untaken one.
        val recent = jobs.indices.filter { now - jobs[it].atMillis <= IN_HAND_MILLIS }

        // The delivery screen sometimes writes the city after the suburb -
        // "Clarinda Melbourne VIC" - while the card only ever said "Clarinda".
        // So the whole name is tried first, then shorter ones from its front.
        val matches = suburbNames(suburb).firstNotNullOfOrNull { wanted ->
            recent.filter { at -> fold(jobs[at].offer.dropoff).contains(wanted) }
                .takeIf { it.isNotEmpty() }
        } ?: return jobs
        val at = matches.firstOrNull { jobs[it].taken } ?: matches.first()

        return jobs.mapIndexed { index, job ->
            if (index != at) job else withDrop(job, dropoff)
        }
    }

    /**
     * This job, with what the delivery screen just said.
     *
     * The screen is read every couple of seconds and the driver goes back and
     * forth between two doors, so nearly every read names an address the job
     * already has and must change nothing.
     */
    private fun withDrop(job: Job, dropoff: Dropoff): Job = when {
        // The shop, not a customer. Uber shows the pickup and the next delivery
        // on one screen, and the address read off it was the shop's: $5.00 on
        // 25 Sept was recorded as collected from Busy Burgers and delivered to
        // "Busy Burgers, 63 Florence St" - the same door, which no job has.
        namesTheShop(job, dropoff.address) -> job

        // Nothing here yet, or this is the same door read again: fill it in.
        job.dropAddress == null || sameAddress(job.dropAddress, dropoff.address) ->
            job.copy(taken = true, dropAddress = dropoff.address, dropUnit = dropoff.unit, dropNote = dropoff.note)

        // A door already kept as a second delivery. Leaving it alone is the whole
        // point: writing it into dropAddress instead is what put one address on a
        // job twice on 25 Sept, on an offer that only ever had one order.
        job.extraDrops.any { sameAddress(it.address, dropoff.address) } -> job

        // A door this job has never seen. Only a batch can have one, and the
        // pickup screen is what says whether this is a batch. Without that, the
        // screen belongs to some other job and is left where it was found.
        job.ordersAtPickup > job.extraDrops.size + 1 ->
            job.copy(extraDrops = job.extraDrops + Drop(dropoff.address, dropoff.unit, dropoff.note))

        else -> job
    }

    /**
     * Whether an address read as a delivery is really the shop this job was
     * collected from - by its name, which the screen prints in front, or by the
     * street address the pickup screen already gave us.
     */
    private fun namesTheShop(job: Job, address: String): Boolean {
        val here = fold(address)
        val shop = fold(job.offer.pickup)
        if (shop.length >= MIN_STORE && here.contains(shop)) return true
        val collectedAt = job.address?.let(::fold) ?: return false
        return collectedAt.length >= MIN_STORE && here.contains(collectedAt)
    }

    /**
     * Whether two delivery screens name one door. The tree writes an address
     * differently from read to read - "Blamey Street, Noble Park" against
     * "14 Blamey Street, Noble Park Melbourne VIC" - so the house number in
     * front and the city behind are both dropped before comparing.
     */
    private fun sameAddress(one: String, other: String): Boolean {
        val a = street(one)
        val b = street(other)
        return a.isNotEmpty() && (a.startsWith(b) || b.startsWith(a))
    }

    /** An address without its leading unit or house number: "14/2-4 Blamey St" -> "blameyst". */
    private fun street(address: String): String =
        fold(address.trim().replace(Regex("""^[\d/\-]+\s*"""), ""))

    /**
     * A delivery screen whose suburb no job names. On a batched offer -
     * "Delivery (2)" - the card names one destination and the second turns up
     * only here, so it is kept against the job in hand rather than thrown away.
     * Nothing is claimed when there is more than one job it could belong to.
     */
    fun alsoDelivered(jobs: List<Job>, dropoff: Dropoff, now: Long): List<Job> {
        val recent = jobs.indices.filter { now - jobs[it].atMillis <= IN_HAND_MILLIS }
        val inHand = recent.filter { jobs[it].taken }
        val at = inHand.singleOrNull() ?: return jobs
        if (jobs[at].dropAddress == null) return jobs
        return jobs.mapIndexed { index, job -> if (index != at) job else withDrop(job, dropoff) }
    }

    /** "Clarinda Melbourne" -> ["clarindamelbourne", "clarinda"], longest first. */
    private fun suburbNames(suburb: String): List<String> {
        val words = suburb.trim().split(Regex("""\s+""")).filter { it.isNotBlank() }
        return (words.size downTo 1)
            .map { take -> fold(words.take(take).joinToString(" ")) }
            .filter { it.length >= MIN_SUBURB }
            .distinct()
    }

    private const val MIN_STORE = 4

    /** A card's shop name this short, "KFC", would sit inside too many other names. */
    private const val MIN_CARD_STORE = 8

    /** A suburb name shorter than this would match half of Melbourne by accident. */
    private const val MIN_SUBURB = 4

    /**
     * How long a job could still be the one being driven. Longer than any single
     * delivery and shorter than a shift, so yesterday's job to the same suburb
     * cannot claim today's screen.
     */
    private const val IN_HAND_MILLIS = 4L * 60 * 60 * 1000

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
    /**
     * The same card read twice. The money usually settles it, but the picture
     * turns "$8.06" into "$8.O6" often enough that the destination has to be
     * able to say so too - it carries "o Latrobe Street" for "Latrobe Street",
     * so one being inside the other is as close as it gets.
     */
    private fun sameOffer(job: Job, other: Job): Boolean {
        if (job.offer.isMatch != other.offer.isMatch) return false
        if (abs(job.atMillis - other.atMillis) > SAME_OFFER_MILLIS) return false
        if (job.offer.payout == other.offer.payout) return true
        val here = fold(job.offer.dropoff)
        val there = fold(other.offer.dropoff)
        if (here.length < MIN_DROPOFF || there.length < MIN_DROPOFF) return false
        return here.contains(there) || there.contains(here)
    }

    /** Short enough to sit inside another destination by accident. */
    private const val MIN_DROPOFF = 12
}
