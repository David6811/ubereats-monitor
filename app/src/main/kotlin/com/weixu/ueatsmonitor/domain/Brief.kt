package com.weixu.ueatsmonitor.domain

/** Data. What the laptop worked out about one stop of the current offer. */
data class StopBrief(
    val place: String,
    val advice: String,
)

/**
 * Data. The laptop's notes on the offer showing right now.
 *
 * Written on the laptop and pushed over adb, so it exists only while a laptop is
 * listening. Absent is the normal case, not an error.
 */
data class Brief(
    val atMillis: Long,
    val pickup: StopBrief?,
    val dropoff: StopBrief?,
)
