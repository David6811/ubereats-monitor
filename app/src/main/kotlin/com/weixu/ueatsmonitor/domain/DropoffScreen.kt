package com.weixu.ueatsmonitor.domain

/**
 * Data. The screen Uber shows on the way to the customer.
 *
 * It carries what the offer card never does: the street number, the unit, and
 * whatever the customer wrote about finding their door. The "Meet at door" and
 * "Apartment" chips beside the address are drawn but not described, so they do
 * not reach the accessibility tree and are not read.
 */
data class Dropoff(
    val customer: String?,
    val address: String,
    val unit: String?,
    val note: String?,
)

/**
 * Calculation. Reads that screen out of the accessibility tree, which returns it
 * whole - the customer's note arrives as one line, however long it is.
 */
object DropoffScreen {

    private val ANCHORS = listOf(
        Regex("""^drop off \d+ order""", RegexOption.IGNORE_CASE),
        Regex("""^complete delivery$""", RegexOption.IGNORE_CASE),
    )

    /**
     * The suburb line ends in the state, written two ways by the same trip:
     * "Chelsea, VIC, 3196" when the screen first opens, "Chelsea VIC" minutes later.
     */
    private val STATE = Regex("""^(.+?),?\s+(VIC|NSW|QLD|SA|WA|TAS|NT|ACT)(?:,?\s+(\d{4}))?$""")

    private const val UNIT_LABEL = "Apt / Unit / Floor:"
    private val NOTE = Regex("""^note from customer\s+(.+)$""", RegexOption.IGNORE_CASE)

    fun looksLikeDropoff(lines: List<String>): Boolean {
        val clean = clean(lines)
        return clean.any { line -> ANCHORS.any { it.containsMatchIn(line) } }
    }

    fun read(lines: List<String>): Dropoff? {
        val clean = clean(lines)
        if (!looksLikeDropoff(clean)) return null

        // The map header can repeat the address above the card, with no name over
        // it; the card's own copy is the last one before the buttons.
        val anchorAt = clean.indexOfFirst { line -> ANCHORS.any { it.containsMatchIn(line) } }
        val stateAt = clean.subList(0, anchorAt).indexOfLast { STATE.containsMatchIn(it) }
        if (stateAt < 1) return null

        // Street, then suburb and state, with the customer's name above both.
        val street = clean[stateAt - 1]
        val unitAt = clean.indexOfFirst { it.equals(UNIT_LABEL, ignoreCase = true) }

        return Dropoff(
            customer = clean.getOrNull(stateAt - 2)?.takeIf { it.length in 2..40 },
            address = street + ", " + suburbLine(clean[stateAt]),
            unit = if (unitAt >= 0) clean.getOrNull(unitAt + 1) else null,
            note = clean.firstNotNullOfOrNull { NOTE.find(it)?.groupValues?.get(1)?.trim() },
        )
    }

    /** The suburb this delivery is in, used to find which job it belongs to. */
    fun suburbOf(dropoff: Dropoff): String? =
        STATE.find(dropoff.address.substringAfterLast(", "))?.groupValues?.get(1)?.trim()

    /** "Chelsea, VIC, 3196" -> "Chelsea VIC 3196"; "Chelsea VIC" stays as it is. */
    private fun suburbLine(line: String): String {
        val (suburb, state, postcode) = STATE.find(line)!!.destructured
        return listOf(suburb.trim(), state, postcode).filter { it.isNotEmpty() }.joinToString(" ")
    }

    private fun clean(lines: List<String>): List<String> =
        lines.map { it.trim() }.filter { it.isNotEmpty() }
}
