package com.weixu.ueatsmonitor.domain

/** Data. Where two named roads meet, as OpenStreetMap has it. */
data class Crossing(val one: String, val other: String, val at: GeoPoint)

/** Data. One named road, thinned to a point every few hundred metres. */
data class Road(val name: String, val points: List<GeoPoint>)

/**
 * Data. Where the card says the food is going, and how well it is known.
 *
 * Four cases rather than a point and a margin, because the difference matters to
 * the driver: a junction is a corner he can picture, two roads that never quite
 * meet put him within a block, one road is a street he may drive the length of,
 * and a suburb is barely an answer. Anything reading this has to say which it
 * got.
 */
sealed interface Spot {

    val at: GeoPoint

    /** Both streets found, sharing a junction. Good to the metre. */
    data class AtCrossing(override val at: GeoPoint, val one: String, val other: String) : Spot

    /**
     * Both streets found, but no junction between them in the map - a freeway
     * carries one over the other, or a third street separates them. Halfway
     * between their nearest points, which is a block or so out.
     */
    data class NearCrossing(override val at: GeoPoint, val one: String, val other: String) : Spot

    /** One street found. The error is the length of that street. */
    data class OnRoad(override val at: GeoPoint, val road: String) : Spot

    /** No street found, so the middle of the suburb. Kilometres out. */
    data class InSuburb(override val at: GeoPoint, val suburb: String) : Spot
}

/**
 * Calculation. Turns the card's destination into a place.
 *
 * The card writes "Ashleigh Street & Jean Court, Keysborough". The suburb alone
 * cannot answer how far that is - a job from Noble Park to Noble Park is not
 * zero kilometres - so the streets have to be looked up, and the suburb is what
 * tells two Station Streets apart.
 */
object RoadIndex {

    /**
     * How far from the suburb's middle a match may be. Big enough for a long
     * suburb and the card's own vagueness, small enough that the Station Street
     * in Edithvale is not mistaken for the one in Box Hill.
     */
    private const val NEAR_SUBURB_MILES = 4.0

    /**
     * A name shorter than this matches half the city once the punctuation is
     * folded away - "Mews", "Rise", "The Grove".
     */
    private const val MIN_NAME = 6

    /** A stem shorter than this is not worth matching without its road type. */
    private const val MIN_STEM = 4

    fun find(
        dropoff: String,
        suburb: Suburb,
        crossings: List<Crossing>,
        roads: List<Road>,
    ): Spot {
        val parts = streetsIn(dropoff)
        val near = { at: GeoPoint -> Geo.straightLine(suburb.at, at).value <= NEAR_SUBURB_MILES }

        // Both streets named and they share a junction: the one answer worth
        // having, and the only one good to the metre.
        crossings.firstOrNull { c ->
            near(c.at) && matched(parts, c.one) && matched(parts, c.other)
        }?.let { return Spot.AtCrossing(it.at, it.one, it.other) }

        // One road per street the card named, rather than every road either
        // could be. Without that, "Racecourse Drive & Sandown Road" matched
        // Racecourse Road twice and called it a corner.
        val found = parts.mapNotNull { part ->
            roadFor(part, roads)?.let { road ->
                road.points.filter(near).ifEmpty { null }?.let { road.name to it }
            }
        }.distinctBy { it.first }

        // Both named, no junction between them. Two roads a driver reads as a
        // corner are a corner whatever the map says about their nodes.
        if (found.size >= 2) {
            val (oneName, onePoints) = found[0]
            val (otherName, otherPoints) = found[1]
            var best: Pair<GeoPoint, GeoPoint>? = null
            var least = Double.MAX_VALUE
            for (a in onePoints) {
                for (b in otherPoints) {
                    val apart = Geo.straightLine(a, b).value
                    if (apart < least) { least = apart; best = a to b }
                }
            }
            best?.let { (a, b) ->
                return Spot.NearCrossing(
                    GeoPoint((a.latitude + b.latitude) / 2, (a.longitude + b.longitude) / 2),
                    oneName,
                    otherName,
                )
            }
        }

        // One street: the point of it nearest the suburb, which is at worst that
        // road's own length out.
        found.firstOrNull()?.let { (name, points) ->
            val at = points.minByOrNull { Geo.straightLine(suburb.at, it).value }
            if (at != null) return Spot.OnRoad(at, name)
        }

        return Spot.InSuburb(suburb.at, suburb.name)
    }

    /**
     * The one road this street on the card means.
     *
     * The full name first. Only when nothing carries it does the stem count -
     * the card abbreviates the road type and does not always get it right, and
     * Wells Road came through as "Wells Ln". Taking the stem first instead put
     * Racecourse Road where the card said Racecourse Drive.
     */
    private fun roadFor(part: String, roads: List<Road>): Road? =
        roads.firstOrNull { saysFully(part, it.name) }
            ?: roads.firstOrNull { saysStem(part, it.name) }

    private fun matched(parts: List<String>, road: String): Boolean =
        parts.any { saysFully(it, road) || saysStem(it, road) }

    /**
     * The road names on the card, before the suburb.
     *
     * Everything after the last comma is the suburb and whatever OCR made of the
     * line's end - "Keysborough J./KTT|T3.T0" - and is no use here.
     */
    private fun streetsIn(dropoff: String): List<String> =
        dropoff.substringBeforeLast(",")
            .split("&")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    /**
     * The road's whole name, inside what the card wrote. OCR puts a letter or
     * two in front of the line often enough that "ar Ashleigh Street" has to
     * still be Ashleigh Street.
     */
    private fun saysFully(part: String, road: String): Boolean {
        val folded = fold(road)
        return folded.length >= MIN_NAME && fold(part).contains(folded)
    }

    /**
     * The road's name without its type, ending what the card wrote without its
     * type. Whole stems only: "Watt" sitting inside "Watton" is not a match, and
     * allowing it put Watt Street where the card said Watton Close.
     */
    private fun saysStem(part: String, road: String): Boolean {
        val stem = fold(stemOf(road))
        return stem.length >= MIN_STEM && fold(stemOf(part)).endsWith(stem)
    }

    /** The name without its road type: "Wells Road" and "Wells Ln" are both Wells. */
    private fun stemOf(name: String): String {
        val words = name.trim().split(" ").filter { it.isNotEmpty() }
        if (words.size < 2) return name
        val last = words.last().lowercase().trim('.')
        return if (TYPES.contains(last)) words.dropLast(1).joinToString(" ") else name
    }

    /** Every road type the card and the map between them use, long form and short. */
    private val TYPES = setOf(
        "road", "rd", "street", "st", "avenue", "ave", "av", "drive", "dr",
        "court", "ct", "crescent", "cres", "cr", "close", "cl", "place", "pl",
        "lane", "ln", "parade", "pde", "boulevard", "blvd", "bvd", "highway",
        "hwy", "terrace", "tce", "circuit", "cct", "grove", "gr", "square", "sq",
        "way", "walk", "rise", "mews", "track", "esplanade", "esp",
    )

    private fun fold(text: String): String = text.lowercase().filter { it.isLetterOrDigit() }
}
