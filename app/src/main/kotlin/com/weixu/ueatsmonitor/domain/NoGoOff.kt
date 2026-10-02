package com.weixu.ueatsmonitor.domain

/**
 * Data. No-go boxes the driver switched off while out, and the rules file they
 * were switched off against.
 *
 * Switching one off is a change of mind for this shift, not a change to the
 * rules: a box drawn on the laptop sits there for weeks, and the box over the
 * tower block with no lift is worth refusing most days but not the day the
 * driver is willing. When the laptop pushes new rules the switches are gone,
 * so one can never quietly outlive the day it was made.
 *
 * Boxes are named by their label, which is what the editor makes the driver
 * type and what the verdict says on screen.
 */
data class BoxesOff(
    val stamp: Long,
    val labels: Set<String>,
)

/** Calculation. What the switches mean for the boxes the rules carry. */
object NoGoOff {

    /** The switches that still apply, or none when the rules have moved under them. */
    fun inForce(off: BoxesOff?, stamp: Long): Set<String> =
        if (off != null && off.stamp == stamp) off.labels else emptySet()

    /** The boxes still being judged against. */
    fun apply(boxes: List<NoGoBox>, off: Set<String>): List<NoGoBox> =
        if (off.isEmpty()) boxes
        else boxes.filterNot { box -> off.any { it.equals(box.label, ignoreCase = true) } }

    fun toggle(off: Set<String>, label: String): Set<String> =
        if (off.any { it.equals(label, ignoreCase = true) }) {
            off.filterNot { it.equals(label, ignoreCase = true) }.toSet()
        } else {
            off + label
        }
}
