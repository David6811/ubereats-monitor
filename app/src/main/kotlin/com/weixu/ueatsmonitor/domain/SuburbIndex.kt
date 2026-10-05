package com.weixu.ueatsmonitor.domain

/**
 * Calculation. Turns the bundled gazetteer into places, and finds the places
 * a screen of text mentions. Longer names win, so "Noble Park North" is never
 * reported as "Noble Park".
 */
object SuburbIndex {

    fun parse(csv: String): List<Suburb> = csv.lineSequence()
        .filter { it.isNotBlank() && !it.startsWith("#") }
        .mapNotNull { line ->
            val parts = line.split(',')
            if (parts.size != 3) return@mapNotNull null
            val latitude = parts[1].trim().toDoubleOrNull() ?: return@mapNotNull null
            val longitude = parts[2].trim().toDoubleOrNull() ?: return@mapNotNull null
            Suburb(parts[0].trim(), GeoPoint(latitude, longitude))
        }
        .toList()

    /** Every place named in [text], longest name first, each reported once. */
    /**
     * The suburb a dropoff is in, which is the one after its last comma.
     *
     * Uber writes a destination as "Cross Street & Cross Street, Suburb", and
     * half the streets in Melbourne are named after a suburb: Dandenong Road
     * runs through Noble Park, Blackburn Rd through Clayton, Newport Drive
     * through Mulgrave. Reading every suburb named anywhere in the line and
     * refusing the offer if any of them was off the list threw away 23 jobs on
     * this board alone - every one of them going somewhere the driver goes.
     *
     * Falls back to the whole line when the tail names none, since a line with
     * no comma in it is still a destination.
     */
    fun ofDropoff(dropoff: String, suburbs: List<Suburb>): Suburb? {
        val tail = dropoff.substringAfterLast(',')
        return findAll(tail, suburbs).firstOrNull() ?: findAll(dropoff, suburbs).firstOrNull()
    }

    fun findAll(text: String, suburbs: List<Suburb>): List<Suburb> {
        val haystack = text.lowercase()
        val hits = suburbs
            .filter { suburb -> haystack.contains(suburb.name.lowercase()) }
            .sortedByDescending { it.name.length }
        val kept = mutableListOf<Suburb>()
        for (hit in hits) {
            val swallowed = kept.any { it.name.lowercase().contains(hit.name.lowercase()) }
            if (!swallowed) kept += hit
        }
        return kept
    }
}
