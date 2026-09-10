package com.weixu.ueatsmonitor.action

import com.weixu.ueatsmonitor.domain.GeoPoint

/** Calculation. Splits a capture file into its header facts and the screen text. */
object CaptureText {

    private const val SEPARATOR = "\n---\n"
    private val MILLIS = Regex("""^millis=(\d+)$""", RegexOption.MULTILINE)
    private val PACKAGE = Regex("""^package=(.*)$""", RegexOption.MULTILINE)
    private val FIX_MILLIS = Regex("""^fix_millis=(\d+)$""", RegexOption.MULTILINE)
    private val LATITUDE = Regex("""^lat=(-?\d+(?:\.\d+)?)$""", RegexOption.MULTILINE)
    private val LONGITUDE = Regex("""^lon=(-?\d+(?:\.\d+)?)$""", RegexOption.MULTILINE)

    fun millisOf(raw: String): Long? = MILLIS.find(raw)?.groupValues?.get(1)?.toLongOrNull()

    /** Which app was on screen. Test mode records other apps, and those are not offers. */
    fun packageOf(raw: String): String? = PACKAGE.find(raw)?.groupValues?.get(1)?.trim()

    fun fixMillisOf(raw: String): Long? =
        FIX_MILLIS.find(raw)?.groupValues?.get(1)?.toLongOrNull()

    /** Where the car was when this screen was recorded, when the phone knew. */
    fun positionOf(raw: String): GeoPoint? {
        val latitude = LATITUDE.find(raw)?.groupValues?.get(1)?.toDoubleOrNull() ?: return null
        val longitude = LONGITUDE.find(raw)?.groupValues?.get(1)?.toDoubleOrNull() ?: return null
        return GeoPoint(latitude, longitude)
    }

    fun bodyOf(raw: String): String = raw.substringAfter(SEPARATOR, raw).trim()

    fun hasMoney(body: String): Boolean = body.contains('$')
}
