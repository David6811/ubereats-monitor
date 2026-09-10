package com.weixu.ueatsmonitor.domain

/** Data. One line of the phone's suburb list. */
data class SuburbRow(val name: String, val chosen: Boolean)

/**
 * Calculation. The rows to draw for a suburb list on the phone.
 *
 * With nothing typed the list is the driver's own suburbs, so opening the page
 * answers "where am I going tonight" without scrolling seven hundred names.
 * Typing searches all of them, so a new one can be added.
 */
object SuburbPicker {

    fun rows(all: List<String>, chosen: Set<String>, query: String): List<SuburbRow> {
        val wanted = query.trim().lowercase()
        if (wanted.isEmpty()) return chosen.sorted().map { SuburbRow(it, true) }

        val starts = mutableListOf<String>()
        val contains = mutableListOf<String>()
        for (name in all) {
            val lower = name.lowercase()
            when {
                lower.startsWith(wanted) -> starts += name
                lower.contains(wanted) -> contains += name
            }
        }
        return (starts.sorted() + contains.sorted()).map { SuburbRow(it, it in chosen) }
    }

    /** Ticking a box on, or off. */
    fun toggle(chosen: Set<String>, name: String): Set<String> =
        if (name in chosen) chosen - name else chosen + name
}
