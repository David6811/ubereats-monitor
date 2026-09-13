package com.weixu.ueatsmonitor.domain

/**
 * Data. The screen Uber shows once an offer has been accepted.
 *
 * It is the only place that says a job was taken - the offer card cannot, since
 * it looks the same whether the driver presses Accept or lets it run out. It
 * also carries the shop's full street address and, when the shop wrote one, a
 * note about where to park.
 */
data class Pickup(
    val store: String,
    val address: String,
    val note: String?,
)

/**
 * Calculation. Reads that screen, which - unlike the offer card - the
 * accessibility tree returns in full, unwrapped and unmangled.
 */
object PickupScreen {

    /** Both say the same thing; either alone is enough, and OCR drops lines. */
    private val ANCHORS = listOf(
        Regex("""^pick up \d+ order""", RegexOption.IGNORE_CASE),
        Regex("""^complete pickup$""", RegexOption.IGNORE_CASE),
    )

    /**
     * An Australian address ends in its suburb and postcode, with the state
     * between them or missing. Uber renders the same shop both ways on the same
     * screen - "Keysborough VIC 3173, Australia" beside "Keysborough 3173" - and
     * requiring the state read the second as no address at all, which is how
     * nearly half of the pickups went unclaimed.
     *
     * A third form puts a comma after the suburb and "APAC" where the state goes:
     * "Keysborough South, APAC 3173", read off a pharmacy on 13 Sept.
     */
    private val ADDRESS = Regex(
        """\b[A-Z][a-z]+(?:\s+[A-Z][a-z]+)*,?\s+(?:(?:VIC|NSW|QLD|SA|WA|TAS|NT|ACT|APAC)\s+)?\d{4}\b"""
    )

    /**
     * Uber's own labels between the shop's name and its address. Only "Merchant
     * logo" used to be skipped; with the name further up, "Agenda" was taken for it.
     */
    private val NOT_A_NAME = setOf("merchant logo", "agenda", "trip planner")

    private val NOTE = Regex("""^merchant note:\s*(.+)$""", RegexOption.IGNORE_CASE)

    fun looksLikePickup(lines: List<String>): Boolean {
        val clean = clean(lines)
        return clean.any { line -> ANCHORS.any { it.containsMatchIn(line) } }
    }

    fun read(lines: List<String>): Pickup? {
        val clean = clean(lines)
        if (!looksLikePickup(clean)) return null

        val addressAt = clean.indexOfFirst { ADDRESS.containsMatchIn(it) }
        if (addressAt < 0) return null

        // The shop's name is the nearest line above its address that is not one
        // of Uber's labels. "Merchant logo" is the picture beside it, never the name.
        val store = clean.take(addressAt)
            .lastOrNull { it.length >= MIN_NAME && it.lowercase() !in NOT_A_NAME }
            ?: return null

        return Pickup(
            store = store,
            address = clean[addressAt],
            note = clean.firstNotNullOfOrNull { NOTE.find(it)?.groupValues?.get(1)?.trim() },
        )
    }

    private const val MIN_NAME = 2

    private fun clean(lines: List<String>): List<String> =
        lines.map { it.trim() }.filter { it.isNotEmpty() }
}
