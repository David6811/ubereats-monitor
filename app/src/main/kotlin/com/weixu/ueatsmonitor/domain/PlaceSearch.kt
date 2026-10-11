package com.weixu.ueatsmonitor.domain

/**
 * Data. Somewhere a centre can be put, found by typing its name.
 *
 * [kind] is only for the line under the name: a driver who types "mcdonalds"
 * wants to know which of the two answers is a shop and which is a suburb.
 */
data class Place(val name: String, val at: GeoPoint, val kind: Kind) {
    enum class Kind { SAVED, SUBURB, SHOP, ADDRESS }
}

/**
 * Calculation. The places worth offering for what has been typed so far.
 *
 * Typing is for the driver who knows where he wants to be and does not want
 * to hunt for it on a map - so a name that starts with what was typed comes
 * first, and "gard" has to reach Aspendale Gardens, which means the start of
 * any word in the name counts. Anything else containing it follows.
 */
object PlaceSearch {

    /**
     * What a place box offers. Nothing typed yet: every saved place, so the
     * usual ones are a tap away. Something typed: the saved places that match,
     * then everything else that does, without a name twice.
     */
    fun offered(query: String, saved: List<Place>, known: List<Place>, limit: Int = 8): List<Place> {
        if (query.isBlank()) return saved
        val mine = matching(query, saved, limit)
        return (mine + matching(query, known, limit).filterNot { other -> mine.any { it.name == other.name } })
            .take(limit)
    }

    fun matching(query: String, places: List<Place>, limit: Int = 8): List<Place> {
        val wanted = query.trim().lowercase()
        if (wanted.isEmpty()) return emptyList()
        return places
            .mapNotNull { place -> rank(wanted, place)?.let { it to place } }
            .sortedWith(compareBy({ it.first }, { it.second.name.length }, { it.second.name }))
            .map { it.second }
            .take(limit)
    }

    /** Lower is better; null is no match at all. */
    private fun rank(wanted: String, place: Place): Int? {
        val name = place.name.lowercase()
        return when {
            name.startsWith(wanted) -> 0
            startsAWord(name, wanted) -> 1
            name.contains(wanted) -> 2
            else -> null
        }
    }

    /** "gard" starts a word in "Aspendale Gardens"; "arden" does not. */
    private fun startsAWord(name: String, wanted: String): Boolean =
        name.split(' ', '-', ',', '\'').any { it.startsWith(wanted) }
}
