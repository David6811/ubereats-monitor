package com.weixu.ueatsmonitor.domain

/** Data. The suburbs the driver is willing to deliver to. */
data class ServiceArea(val suburbNames: Set<String>) {

    fun holds(suburb: Suburb): Boolean =
        suburbNames.any { it.equals(suburb.name, ignoreCase = true) }

    companion object {
        /**
         * The convex region spanned by Hampton, Clayton South, Doveton and Seaford,
         * as measured against the bundled gazetteer.
         */
        val SOUTH_EAST: ServiceArea = ServiceArea(
            setOf(
                "Aspendale", "Aspendale Gardens", "Bangholme", "Bonbeach", "Braeside",
                "Carrum", "Chelsea", "Chelsea Heights", "Cheltenham", "Clarinda",
                "Clayton South", "Dandenong", "Dingley Village", "Doveton", "Edithvale",
                "Hampton", "Hampton East", "Heatherton", "Highett", "Keysborough",
                "Mentone", "Moorabbin", "Moorabbin Airport", "Moorabbin East",
                "Mordialloc", "Noble Park", "Parkdale", "Patterson Lakes", "Pennydale",
                "Seaford", "Springvale South", "Waterways",
            )
        )
    }
}

/** Data. Sum type: what the places on one screen mean for the service area. */
sealed interface AreaCall {

    /** Every place named sits inside the area. Safe to take. */
    data class AllInside(val suburbs: List<Suburb>) : AreaCall

    /** At least one place sits outside. Worth a hard look. */
    data class SomeOutside(val outside: List<Suburb>, val inside: List<Suburb>) : AreaCall

    /** No place was recognised, so the area says nothing. Stay quiet. */
    data object NoSuburb : AreaCall
}

/** Calculation. Places found on a screen in, an [AreaCall] out. */
object AreaJudge {

    fun call(found: List<Suburb>, area: ServiceArea): AreaCall {
        if (found.isEmpty()) return AreaCall.NoSuburb
        val (inside, outside) = found.partition { area.holds(it) }
        return if (outside.isEmpty()) {
            AreaCall.AllInside(inside)
        } else {
            AreaCall.SomeOutside(outside = outside, inside = inside)
        }
    }
}
