package com.weixu.ueatsmonitor.domain

/**
 * Data. Where the driver is in a job, as Uber's own ongoing notification says it.
 *
 * Read off a real shift on 4 Oct, which ran:
 *
 *     You are currently online.
 *     Going to Captain Makos Fish and Chips
 *     Arrived at Captain Makos Fish and Chips
 *     Going to 27 Buldah Street, Dandenong North VIC 3175, Australia
 *     Leave the order at Callum's door
 *     You are currently online.
 *
 * This beats the screens at the one thing the screens are bad at: it arrives
 * with the phone in a pocket and Uber in the background, which is where a
 * quarter of the pickup screens were lost. It also says which half of the job
 * is running, which no single screen does.
 */
sealed interface TripState {

    /** Between jobs. */
    data object Idle : TripState

    /** Driving to the shop. Uber names the shop, not its address. */
    data class ToShop(val shop: String) : TripState

    data class AtShop(val shop: String) : TripState

    /** Driving to the customer. Uber gives the full street address here. */
    data class ToCustomer(val address: String) : TripState

    /** At the door: "Leave the order at Callum's door", "Meet at door for Michael's order". */
    data object AtCustomer : TripState
}

/** Calculation. Reads one line of Uber's ongoing notification. */
object TripNotice {

    private val GOING = Regex("""^going to\s+(.+)$""", RegexOption.IGNORE_CASE)
    private val ARRIVED = Regex("""^arrived at\s+(.+)$""", RegexOption.IGNORE_CASE)
    private val IDLE = Regex("""you are currently (online|offline)""", RegexOption.IGNORE_CASE)
    private val AT_DOOR = Regex("""(leave the order|meet at door|hand it to|deliver to)""", RegexOption.IGNORE_CASE)

    /**
     * Whether what follows "Going to" is a street address rather than a shop.
     * Uber writes the customer's address in full - number, street, suburb,
     * state, postcode - and writes a shop by name alone.
     */
    private val ADDRESS = Regex("""\b\d{4}\b|\bVIC\b|\bAustralia\b""", RegexOption.IGNORE_CASE)
    private val HOUSE_NUMBER = Regex("""^\d+[A-Za-z]?(?:[/\-]\d+[A-Za-z]?)?\s+\S""")

    fun read(line: String): TripState? {
        val text = line.trim()
        if (text.isEmpty()) return null
        if (IDLE.containsMatchIn(text)) return TripState.Idle
        if (AT_DOOR.containsMatchIn(text)) return TripState.AtCustomer
        ARRIVED.find(text)?.let { return TripState.AtShop(it.groupValues[1].trim()) }
        GOING.find(text)?.let { match ->
            val where = match.groupValues[1].trim()
            return if (looksLikeAddress(where)) TripState.ToCustomer(where) else TripState.ToShop(where)
        }
        return null
    }

    /**
     * "27 Buldah Street, Dandenong North VIC 3175, Australia" is an address;
     * "Captain Makos Fish and Chips" is a shop. A shop with a number in its
     * name - "7-Eleven" - is not one: the number has to start the line and be
     * followed by a street.
     */
    fun looksLikeAddress(where: String): Boolean =
        HOUSE_NUMBER.containsMatchIn(where) && ADDRESS.containsMatchIn(where)
}
