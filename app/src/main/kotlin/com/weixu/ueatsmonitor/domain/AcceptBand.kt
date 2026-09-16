package com.weixu.ueatsmonitor.domain

/**
 * Calculation. Is the offer card's action button on screen?
 *
 * Not by its colour. Uber ships at least two cards: a green Accept, which is
 * also the countdown - filling RGB(16,130,70) and emptying to RGB(63,155,106) -
 * and a black Match, RGB(0,0,0), for a Trip Radar offer. Both measured off real
 * screenshots. Pinning the test to one hue lost every card of the other kind,
 * and would lose the next kind too.
 *
 * What both have in common is the shape: a wide band of one flat colour across
 * the bottom of the card, against the card's white body. So that is the test.
 * A few hundred pixels cost microseconds, against a couple of hundred
 * milliseconds to read the whole screen.
 */
object AcceptBand {

    /** Where the button sits, as fractions of screen height. */
    val ROWS: List<Double> = listOf(0.89, 0.91, 0.93, 0.95, 0.97)

    /**
     * Deliberately low. A frame wrongly read costs a fifth of a second; a frame
     * wrongly skipped costs the offer.
     */
    private const val MIN_SHARE = 0.22

    /** Colours are quantised before counting, so anti-aliasing does not split a band. */
    private const val STEP = 24

    /** The card's own body, which a button has to stand out from. */
    fun isCardWhite(argb: Int): Boolean {
        val red = (argb shr 16) and 0xFF
        val green = (argb shr 8) and 0xFF
        val blue = argb and 0xFF
        return red > 225 && green > 225 && blue > 225
    }

    /** How much of [samples] one flat, non-white colour takes, 0 to 1. */
    fun bandShare(samples: IntArray): Double {
        if (samples.isEmpty()) return 0.0
        val counts = HashMap<Int, Int>()
        for (pixel in samples) {
            if (isCardWhite(pixel)) continue
            val key = quantise(pixel)
            counts[key] = (counts[key] ?: 0) + 1
        }
        val biggest = counts.values.maxOrNull() ?: return 0.0
        return biggest.toDouble() / samples.size
    }

    fun holdsButton(samples: IntArray): Boolean = bandShare(samples) >= MIN_SHARE

    /**
     * Calculation. A short name for what the band looks like, quantised the same
     * way the share is. Two frames of the same still screen give the same name;
     * a card appearing changes it.
     */
    fun signature(samples: IntArray): Int {
        var hash = 17
        for (pixel in samples) hash = hash * 31 + quantise(pixel)
        return hash
    }

    private fun quantise(argb: Int): Int {
        val red = ((argb shr 16) and 0xFF) / STEP
        val green = ((argb shr 8) and 0xFF) / STEP
        val blue = (argb and 0xFF) / STEP
        return (red shl 16) or (green shl 8) or blue
    }
}
