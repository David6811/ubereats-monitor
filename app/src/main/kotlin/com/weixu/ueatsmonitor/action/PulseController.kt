package com.weixu.ueatsmonitor.action

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout

/**
 * Action. A small dot in the top right that beats once for every frame read.
 *
 * A still dot proves nothing - it would sit there just as calmly with the
 * service dead. The beat is the evidence: it moves only because a frame was
 * read a moment ago, and it stops moving the moment the reader does.
 *
 * No text, deliberately. Anything written here would be photographed along with
 * the offer card and read back as part of it, which is a bug this project has
 * already had twice.
 */
class PulseController(private val context: Context) {

    /** Data. What the dot is saying. */
    enum class Mood { WATCHING, IDLE, BROKEN }

    private val main = Handler(Looper.getMainLooper())
    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var frame: View? = null
    private var dot: View? = null
    private var mood: Mood? = null

    fun beat(next: Mood) {
        if (!Settings.canDrawOverlays(context)) return
        main.post {
            val view = dot ?: add() ?: return@post
            if (next != mood) {
                mood = next
                (view.background as? GradientDrawable)?.setColor(colourOf(next))
            }
            // A loop that stops while the process lives would leave a dot sitting
            // there looking calm. Say so instead.
            main.removeCallbacks(goneQuiet)
            main.postDelayed(goneQuiet, QUIET_MILLIS)
            // Out and back, a little over a third of a second. Slower than the
            // two-second cadence, so beats never run into each other.
            view.animate().cancel()
            view.scaleX = 1f
            view.scaleY = 1f
            view.alpha = RESTING
            view.animate()
                .scaleX(SWELL).scaleY(SWELL).alpha(1f)
                .setDuration(140)
                .withEndAction {
                    view.animate()
                        .scaleX(1f).scaleY(1f).alpha(RESTING)
                        .setDuration(260)
                        .start()
                }
                .start()
        }
    }

    /** While a verdict is on screen the verdict is the news, not the heartbeat. */
    fun hide() = main.post {
        main.removeCallbacks(goneQuiet)
        val holder = frame ?: return@post
        frame = null
        dot = null
        mood = null
        runCatching { windowManager.removeView(holder) }
    }

    /** Ten seconds without a frame is five missed passes of a two-second loop. */
    private val goneQuiet = Runnable {
        val view = dot ?: return@Runnable
        mood = null
        view.animate().cancel()
        view.scaleX = 1f
        view.scaleY = 1f
        view.alpha = STALLED
        (view.background as? GradientDrawable)?.setColor(QUIET)
    }

    private fun add(): View? {
        // The window is wider than the dot on purpose: a window sized to the dot
        // clips the beat, and a clipped circle swelling to fill its own window is
        // what turned this into a grey square.
        val view = View(context).apply {
            alpha = RESTING
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(colourOf(Mood.IDLE))
                setStroke(dp(3), RING)
            }
        }
        val holder = FrameLayout(context).apply {
            clipChildren = false
            clipToPadding = false
            addView(
                view,
                FrameLayout.LayoutParams(dp(DOT_DP), dp(DOT_DP), Gravity.CENTER),
            )
        }
        val size = dp(DOT_DP * 2)
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val params = WindowManager.LayoutParams(
            size,
            size,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(8)
            y = TOP_PIXELS
        }
        return runCatching { windowManager.addView(holder, params); holder }
            .getOrNull()
            ?.let { frame = it; dot = view; view }
    }

    private fun colourOf(mood: Mood): Int = when (mood) {
        Mood.WATCHING -> WATCHING
        Mood.IDLE -> IDLE
        Mood.BROKEN -> WRONG
    }

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    private companion object {
        const val DOT_DP = 18

        /** Level with the verdict chip, at the very top of the screen. */
        const val TOP_PIXELS = 20

        /** Dim between beats, so a dead dot is visibly dead rather than merely still. */
        const val RESTING = 0.45f

        const val SWELL = 1.5f

        /** Longer than this without a beat and the loop is not running. */
        const val QUIET_MILLIS = 10_000L

        // Blue against orange, never green against red: the driver cannot tell
        // those two apart. Colour is the second signal anyway - whether the dot
        // beats at all is the first.
        val WATCHING: Int = Color.parseColor("#0A84FF")
        val IDLE: Int = Color.parseColor("#9AA0A6")
        val WRONG: Int = Color.parseColor("#E67E22")

        /** Dimmer than a resting beat: still there, no longer reading. */
        val QUIET: Int = WRONG
        const val STALLED = 0.30f

        /** Gold and solid, so the blue sits inside a clear edge. */
        val RING: Int = Color.parseColor("#FFC400")
    }
}
