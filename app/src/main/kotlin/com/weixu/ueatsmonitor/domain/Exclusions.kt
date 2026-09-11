package com.weixu.ueatsmonitor.domain

/**
 * Data. Suburbs the driver ticked off while out, and what they were ticked off
 * against.
 *
 * [set] and [stamp] are the set that was live and the rules file they came from.
 * A tick box on the phone is a change of mind for this shift, not a rule: when
 * either of those changes the exclusions are gone, so one can never quietly
 * outlive the day it was made.
 */
data class Excluded(
    val set: String,
    val stamp: Long,
    val suburbs: Set<String>,
)

/** Calculation. What the exclusions mean for a set of suburbs. */
object Exclusions {

    /** The exclusions that still apply, or none when the ground has moved. */
    fun inForce(excluded: Excluded?, set: String?, stamp: Long): Set<String> =
        if (excluded != null && excluded.set == set && excluded.stamp == stamp) {
            excluded.suburbs
        } else {
            emptySet()
        }

    fun apply(suburbs: Set<String>, excluded: Set<String>): Set<String> =
        if (excluded.isEmpty()) suburbs
        else suburbs.filterNot { name -> excluded.any { it.equals(name, ignoreCase = true) } }.toSet()

    fun toggle(excluded: Set<String>, suburb: String): Set<String> =
        if (excluded.any { it.equals(suburb, ignoreCase = true) }) {
            excluded.filterNot { it.equals(suburb, ignoreCase = true) }.toSet()
        } else {
            excluded + suburb
        }
}
