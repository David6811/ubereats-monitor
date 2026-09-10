package com.weixu.ueatsmonitor.domain

/**
 * Calculation. Is the Accept button on screen?
 *
 * The button is also the countdown: it fills dark green RGB(16,130,70) and
 * empties to a lighter RGB(63,155,106), so the mix shifts second by second and
 * neither shade can be the test on its own. Both are matched, with room around
 * them for the shades in between.
 *
 * Measured off a real offer card, where about 85% of the pixels sampled across
 * the band are one of those two. Testing a few hundred pixels costs
 * microseconds, against a couple of hundred milliseconds to read the whole
 * screen - so this decides whether reading is worth doing at all.
 */
object AcceptBand {

    /** Where the button sits, as fractions of screen height. */
    /** Spans the button and its edges, so a shifted layout still lands on it. */
    val ROWS: List<Double> = listOf(0.87, 0.89, 0.91, 0.93, 0.95, 0.97)

    /**
     * Deliberately low. A frame wrongly read costs a fifth of a second; a frame
     * wrongly skipped costs the offer. The two failures are not worth the same,
     * so the threshold leans towards reading.
     */
    private const val MIN_GREEN = 0.12

    fun isUberGreen(argb: Int): Boolean {
        val red = (argb shr 16) and 0xFF
        val green = (argb shr 8) and 0xFF
        val blue = argb and 0xFF
        return green in 80..205 &&
            red < 130 &&
            blue < 145 &&
            green - red > 35 &&
            green - blue > 35
    }

    /** How much of [samples] is button green, 0 to 1. */
    fun greenFraction(samples: IntArray): Double {
        if (samples.isEmpty()) return 0.0
        return samples.count(::isUberGreen).toDouble() / samples.size
    }

    /** True when enough of [samples] is that green for a button to be there. */
    fun holdsButton(samples: IntArray): Boolean = greenFraction(samples) >= MIN_GREEN
}
