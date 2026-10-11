package com.weixu.ueatsmonitor.domain

/**
 * Data. A point the driver works around for today instead of the set's centre:
 * typed in, picked from his saved places or another set's centre, or where he
 * is standing. [day] is the local date it was set on, "2026-10-11".
 */
data class TempCentre(val label: String, val at: GeoPoint, val day: String)

/**
 * Calculation. Which centre the "work around the centre" rule, the voice's
 * "back to centre" and the briefing use.
 *
 * A temporary point lasts the day it was set. One left from yesterday would
 * have the first offers of the morning judged against wherever the driver
 * happened to end up last night, and nothing on the screen he is driving with
 * would say so.
 */
object CentreChoice {

    /** Today's temporary point, or null when there is none or it is from another day. */
    fun stillToday(temp: TempCentre?, today: String): TempCentre? = temp?.takeIf { it.day == today }

    fun inForce(temp: TempCentre?, today: String, setCentre: GeoPoint?): GeoPoint? =
        stillToday(temp, today)?.at ?: setCentre
}
