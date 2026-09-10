package com.weixu.ueatsmonitor.action

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.weixu.ueatsmonitor.domain.OfferCard
import com.weixu.ueatsmonitor.domain.Ruling
import com.weixu.ueatsmonitor.domain.RulingText
import com.weixu.ueatsmonitor.domain.VerdictText

/**
 * Action. A small chip beside the payout on the offer card.
 *
 * The sound is not enough: Uber's own alert is loud and lands at the same moment.
 * This puts the answer where the driver is already looking - next to the money -
 * and says "thinking" the instant a card is detected, so a blank space is never
 * mistaken for a verdict that has not arrived.
 */
class OverlayController(private val context: Context) {

    /** Data. What the chip is saying right now. */
    sealed interface State {
        data object Thinking : State
        data class Decided(val ruling: Ruling, val card: OfferCard) : State
    }

    private val main = Handler(Looper.getMainLooper())
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private var shown: View? = null
    private var showing: String = ""

    fun canDraw(): Boolean = Settings.canDrawOverlays(context)

    /**
     * Shows [state] and gives it [ttlMillis] to live. Every frame that still sees
     * the card calls this again and pushes the deadline out; when the card goes,
     * nothing calls it and the chip takes itself away.
     *
     * The deadline is the whole safety mechanism. Hiding on "no card in this
     * frame" is not enough on its own: when the screen goes dark or Uber leaves
     * the foreground no frame is judged at all, and a chip that waits to be told
     * to go would sit on the screen indefinitely.
     */
    fun show(state: State, ttlMillis: Long = TTL_MILLIS) {
        if (!canDraw()) return
        val key = keyOf(state)
        main.post {
            if (key != showing || shown == null) {
                removeNow()
                val chip = buildChip(state)
                runCatching { windowManager.addView(chip, layoutParams()) }
                    .onSuccess { shown = chip; showing = key }
            }
            main.removeCallbacks(autoHide)
            main.postDelayed(autoHide, ttlMillis)
        }
    }

    fun hide() = main.post(::removeNow)

    private val autoHide = Runnable { removeNow() }

    private fun removeNow() {
        val chip = shown ?: return
        shown = null
        showing = ""
        runCatching { windowManager.removeView(chip) }
    }

    private fun keyOf(state: State): String = when (state) {
        State.Thinking -> "thinking"
        is State.Decided -> RulingText.headline(state.ruling) + RulingText.reason(state.ruling)
    }

    private fun layoutParams(): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply {
            // Beside the payout: the card's amount sits about two thirds down.
            gravity = Gravity.TOP or Gravity.END
            x = dp(20)
            y = (context.resources.displayMetrics.heightPixels * PAYOUT_ROW).toInt()
        }
    }

    private fun buildChip(state: State): View {
        val face = faceOf(state)
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(10), dp(16), dp(10))
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(face.fill)
                setStroke(dp(2), face.edge)
            }
            addView(
                TextView(context).apply {
                    text = face.title
                    textSize = 22f
                    setTextColor(face.ink)
                    setTypeface(typeface, Typeface.BOLD)
                }
            )
            face.detail?.let { detail ->
                addView(
                    TextView(context).apply {
                        text = detail
                        textSize = 13f
                        setTextColor(face.ink)
                    }
                )
            }
        }
    }

    private data class Face(
        val title: String,
        val detail: String?,
        val fill: Int,
        val edge: Int,
        val ink: Int,
    )

    private fun faceOf(state: State): Face = when (state) {
        State.Thinking -> Face(
            title = "思考中…",
            detail = null,
            fill = Color.parseColor("#F2263238"),
            edge = Color.parseColor("#607D8B"),
            ink = Color.WHITE,
        )
        is State.Decided -> {
            val take = state.ruling is Ruling.Take
            val unsure = state.ruling is Ruling.NoRules || state.ruling is Ruling.Unknown
            Face(
                title = RulingText.headline(state.ruling),
                detail = RulingText.reason(state.ruling) + "\n" +
                    VerdictText.metricsLine(metricsOf(state.card)),
                fill = when {
                    unsure -> Color.parseColor("#F2263238")
                    take -> Color.parseColor("#F21B5E20")
                    else -> Color.parseColor("#F27F1D17")
                },
                edge = when {
                    unsure -> Color.parseColor("#FFB300")
                    take -> Color.parseColor("#66BB6A")
                    else -> Color.parseColor("#EF5350")
                },
                ink = Color.WHITE,
            )
        }
    }

    /** The numbers are shown, never judged: the driver reads the money himself. */
    private fun metricsOf(card: OfferCard) =
        com.weixu.ueatsmonitor.domain.OfferEvaluator.metricsOf(
            com.weixu.ueatsmonitor.domain.OfferCardReader.toOffer(card)
        )

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    private companion object {
        /** Where the payout line sits, as a fraction of screen height. */
        const val PAYOUT_ROW = 0.63

        /** Two frames' worth of grace, so a missed frame does not make it flicker. */
        const val TTL_MILLIS = 4_500L
    }
}
