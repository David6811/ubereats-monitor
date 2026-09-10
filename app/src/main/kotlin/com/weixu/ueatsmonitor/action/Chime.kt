package com.weixu.ueatsmonitor.action

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import com.weixu.ueatsmonitor.domain.AreaCall

/**
 * Action. Two sounds a driver can tell apart without looking: a short rising
 * pair for "inside my area", a low repeated buzz for "outside".
 * Plays on the alarm stream so Uber's own audio does not bury it.
 */
class Chime {

    private val main = Handler(Looper.getMainLooper())

    fun play(call: AreaCall) {
        when (call) {
            is AreaCall.AllInside -> friendly()
            is AreaCall.SomeOutside -> warning()
            AreaCall.NoSuburb -> neutral()
        }
    }

    /** Two quick high beeps. */
    private fun friendly() {
        sequence(
            Beat(ToneGenerator.TONE_PROP_BEEP, durationMillis = 120, afterMillis = 0),
            Beat(ToneGenerator.TONE_PROP_BEEP, durationMillis = 120, afterMillis = 160),
            Beat(ToneGenerator.TONE_PROP_BEEP2, durationMillis = 220, afterMillis = 320),
        )
    }

    /** Three low buzzes, deliberately unpleasant. */
    private fun warning() {
        sequence(
            Beat(ToneGenerator.TONE_SUP_ERROR, durationMillis = 400, afterMillis = 0),
            Beat(ToneGenerator.TONE_SUP_ERROR, durationMillis = 400, afterMillis = 500),
            Beat(ToneGenerator.TONE_SUP_ERROR, durationMillis = 400, afterMillis = 1000),
        )
    }

    /** One flat tick: an offer was seen, the area could not judge it. */
    private fun neutral() {
        sequence(Beat(ToneGenerator.TONE_PROP_ACK, durationMillis = 200, afterMillis = 0))
    }

    private data class Beat(val tone: Int, val durationMillis: Int, val afterMillis: Long)

    private fun sequence(vararg beats: Beat) {
        val generator = runCatching {
            ToneGenerator(AudioManager.STREAM_ALARM, VOLUME_PERCENT)
        }.getOrNull() ?: return

        beats.forEach { beat ->
            main.postDelayed({ generator.startTone(beat.tone, beat.durationMillis) }, beat.afterMillis)
        }
        val last = beats.maxOf { it.afterMillis + it.durationMillis }
        main.postDelayed({ runCatching { generator.release() } }, last + RELEASE_GRACE_MILLIS)
    }

    private companion object {
        const val VOLUME_PERCENT = 100
        const val RELEASE_GRACE_MILLIS = 500L
    }
}
