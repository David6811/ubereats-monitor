package com.weixu.ueatsmonitor.action

import android.animation.Animator
import android.animation.ObjectAnimator
import android.content.Context
import android.util.Log
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.weixu.ueatsmonitor.domain.ChipText
import com.weixu.ueatsmonitor.domain.OfferCard
import com.weixu.ueatsmonitor.domain.Ruling
import com.weixu.ueatsmonitor.domain.RulingText

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
        data class Decided(
            val ruling: Ruling,
            val card: OfferCard,
            /** How far the drop is from this set's centre, when one is marked. */
            val fromCentre: String?,
        ) : State
    }

    private val main = Handler(Looper.getMainLooper())
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private var shown: View? = null

    /**
     * Read from the reader's own thread by [showingVerdict] and written here on
     * main: without this the reader can see a stale empty string, decide no
     * verdict is up, and take a verdict away that had only just gone up.
     */
    @Volatile
    private var showing: String = ""

    /** Kept so the beat stops when its dots leave the screen. */
    private var beating: List<Animator> = emptyList()

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
        if (System.currentTimeMillis() < quietUntilMillis) return
        if (!canDraw()) {
            Log.i(TAG, "overlay: show refused, no draw-over permission")
            return
        }
        val key = keyOf(state)
        main.post {
            if (key != showing || shown == null) {
                removeNow("replaced")
                val chip = buildChip(state)
                runCatching { windowManager.addView(chip, layoutParams(state)) }
                    .onSuccess { shown = chip; showing = key; Log.i(TAG, "overlay: added $key") }
                    .onFailure { Log.w(TAG, "overlay: addView failed for $key", it) }
            }
            main.removeCallbacks(autoHide)
            main.postDelayed(autoHide, ttlMillis)
        }
    }

    fun hide() = main.post { removeNow("told to hide") }

    /**
     * A still button at the right edge that ends a Maps navigation. Static:
     * drawn once and left alone, so it costs nothing while it sits there -
     * unlike the beating dot that once warmed the phone. Its window takes
     * touches only on its own small area, well clear of Uber's card.
     */
    private var tools: View? = null

    fun showTools(on: Boolean) = main.post {
        if (on == (tools != null)) return@post
        if (!on) {
            tools?.let { runCatching { windowManager.removeView(it) } }
            tools = null
            return@post
        }
        if (!canDraw()) return@post
        val words = driverWords()
        val navigation = toolButton(words.stopNavigation).apply {
            setOnClickListener {
                text = words.stopping
                MapsNavigation.stop(context) { pressed ->
                    text = words.stopNavigation
                    val said = if (pressed) words.navigationClosed else words.mapsNotNavigating
                    android.widget.Toast.makeText(context, said, android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
        // Words and bright-against-dim, never hue: on is bold on the gold fill,
        // off is muted on the dark one.
        val voice = toolButton("").apply {
            fun paint() {
                val on = VoiceService.isRunning()
                text = if (on) words.voiceOn else words.voiceOff
                setTextColor(if (on) INK else Color.parseColor("#8F8C85"))
                (background as GradientDrawable).setColor(if (on) FILL else Color.parseColor("#F0141416"))
            }
            paint()
            setOnClickListener {
                VoiceService.toggle(context) { paint() }
                main.postDelayed({ paint() }, 1_500L)
            }
        }
        val button = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            // See-through, so what Uber draws under the corner still shows.
            alpha = TOOLS_ALPHA
            addView(navigation)
            addView(voice, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })
        }
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply {
            // Top right corner, just under the status bar and clear of the
            // verdict chip's own row; nothing Uber asks to be pressed lives there.
            gravity = Gravity.TOP or Gravity.END
            x = dp(8)
            y = dp(TOOLS_TOP_DP)
        }
        runCatching { windowManager.addView(button, params) }
            .onSuccess { tools = button }
            .onFailure { Log.w(TAG, "overlay: tools addView failed", it) }
    }

    /** Whether a verdict is up right now, as opposed to nothing or "thinking". */
    fun showingVerdict(): Boolean = showing.isNotEmpty() && showing != THINKING

    private val autoHide = Runnable { removeNow("deadline passed") }

    /**
     * Set when the driver closes the chip. It covers the top of the map, and
     * while driving that can be the part he needs; closing it only quiets it
     * for a while, so the next card still gets a verdict.
     */
    @Volatile
    private var quietUntilMillis: Long = 0L

    /** Fades the chip out rather than snapping it away, then keeps it off for [QUIET_MILLIS]. */
    private fun closeByDriver() {
        quietUntilMillis = System.currentTimeMillis() + QUIET_MILLIS
        main.removeCallbacks(autoHide)
        val chip = shown ?: return
        chip.animate()
            .alpha(0f)
            .setDuration(FADE_MILLIS)
            .withEndAction { if (shown === chip) removeNow("closed by driver") }
            .start()
    }

    private fun removeNow(why: String = "unsaid") {
        val chip = shown ?: return
        Log.i(TAG, "overlay: removed $showing - $why")
        shown = null
        showing = ""
        beating.forEach { it.cancel() }
        beating = emptyList()
        runCatching { windowManager.removeView(chip) }
    }

    private fun toolButton(label: String): TextView = TextView(context).apply {
        text = label
        textSize = 15f
        setTextColor(INK)
        setTypeface(typeface, Typeface.BOLD)
        gravity = Gravity.CENTER
        setPadding(dp(14), dp(10), dp(14), dp(10))
        background = GradientDrawable().apply {
            cornerRadius = dp(14).toFloat()
            setColor(FILL)
            setStroke(dp(2), EDGE)
        }
    }

    private fun keyOf(state: State): String = when (state) {
        State.Thinking -> THINKING
        is State.Decided -> RulingText.headline(state.ruling, state.card.isMatch, driverLang()) +
            RulingText.reason(state.ruling, driverLang()) + (state.fromCentre ?: "")
    }

    private fun layoutParams(state: State): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val verdict = state is State.Decided
        return WindowManager.LayoutParams(
            if (verdict) {
                WindowManager.LayoutParams.MATCH_PARENT
            } else {
                WindowManager.LayoutParams.WRAP_CONTENT
            },
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                // The verdict carries a close button, so it takes touches on
                // its own area; the dots have nothing to press.
                (if (verdict) 0 else WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE) or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply {
            // Over the map, above everything the card says. Anywhere on the card
            // and the chip's own words are read back as part of it: the card's
            // pickup once came out as the suburb this chip was naming.
            if (verdict) {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                x = 0
            } else {
                // Off in the corner beside the heartbeat, which owns the very edge.
                // Nothing here is worth a band across the screen: the state says
                // only that a card may be arriving, and the driver is looking at
                // the card, not at us.
                gravity = Gravity.TOP or Gravity.END
                x = dp(DOTS_FROM_EDGE_DP)
            }
            y = TOP_PIXELS
        }
    }

    private fun buildChip(state: State): View = when (state) {
        State.Thinking -> thinkingDots()
        is State.Decided -> verdictBar(faceOf(state))
    }

    /**
     * Three dots, and not one word.
     *
     * Every word this window draws lands on a screen the reader is about to read,
     * and comes back as part of what it thinks the card says - a shop name once
     * came out as the suburb this chip was naming. Dots cannot be read back. They
     * also say everything this state knows, which is only that something is being
     * looked at.
     */
    private fun thinkingDots(): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(10), dp(7), dp(10), dp(7))
        background = GradientDrawable().apply {
            cornerRadius = dp(11).toFloat()
            setColor(FILL)
            setStroke(dp(2), EDGE)
        }
        // Each dot swells and fades a third of a cycle behind the one before it,
        // which is the wave everything else on a phone uses to say "working". A
        // still row of dots reads as a decoration; this reads as a wait.
        beating = (0 until 3).map { at ->
            val dot = View(context).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(INK)
                }
                alpha = DOT_DIM
            }
            addView(
                dot,
                LinearLayout.LayoutParams(dp(6), dp(6)).apply {
                    if (at > 0) marginStart = dp(5)
                },
            )
            ObjectAnimator.ofFloat(dot, View.ALPHA, DOT_DIM, 1f, DOT_DIM).apply {
                duration = BEAT_MILLIS
                startDelay = at * (BEAT_MILLIS / 3)
                repeatCount = ObjectAnimator.INFINITE
                start()
            }
        }
    }

    private fun verdictBar(face: Face): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(8), dp(18), dp(10))
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(face.fill)
                setStroke(dp(2), face.edge)
            }
            addView(
                LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    addView(
                        shoulders(face.title, 26f, face.payout, 26f, face.ink),
                        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
                    )
                    addView(closeButton(face.ink))
                }
            )
            face.route?.let { route ->
                addView(
                    line(route.text, 17f, face.ink).apply {
                        if (route.hasWarning) {
                            text = SpannableString(route.text).apply {
                                setSpan(
                                    ForegroundColorSpan(WARN),
                                    route.warnFrom,
                                    route.warnTo,
                                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                                )
                            }
                        }
                    }
                )
            }
            if (face.distance != null || face.rate != null) {
                addView(shoulders(face.distance ?: "", 17f, face.rate, 17f, face.ink))
            }
            face.fromCentre?.let { addView(line(it, 16f, face.ink)) }
        }
    }

    /** A cross big enough to hit without looking long, beside the payout. */
    private fun closeButton(ink: Int): View = TextView(context).apply {
        text = "✕"
        textSize = 22f
        setTextColor(ink)
        gravity = Gravity.CENTER
        minWidth = dp(CLOSE_DP)
        minHeight = dp(CLOSE_DP)
        setPadding(dp(12), 0, 0, 0)
        setOnClickListener { closeByDriver() }
    }

    /** One row with something on each shoulder: the left grows, the right hugs. */
    private fun shoulders(left: String, leftSize: Float, right: String?, rightSize: Float, ink: Int): View =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(
                line(left, leftSize, ink),
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
            )
            right?.let { addView(line(it, rightSize, ink)) }
        }

    private fun line(text: String, size: Float, ink: Int): TextView = TextView(context).apply {
        this.text = text
        textSize = size
        setTextColor(ink)
        setTypeface(typeface, Typeface.BOLD)
        // A large line reserves a lot of room above its letters; without this the
        // rows drift apart and the chip grows for nothing.
        includeFontPadding = false
        setLineSpacing(0f, 1.05f)
    }

    private data class Face(
        val title: String,
        val payout: String?,
        val route: ChipText.Route?,
        val distance: String?,
        val rate: String?,
        val fromCentre: String?,
    ) {
        val fill: Int get() = FILL
        val edge: Int get() = EDGE
        val ink: Int get() = INK
    }

    private fun faceOf(state: State.Decided): Face = with(state) {
        val lang = driverLang()
        Face(
            title = RulingText.headline(ruling, card.isMatch, lang),
            payout = state.card.payout.toString(),
            // Where to where, with the shop's kind and where it stands. The street
            // names are unreadable in the second the card gives you.
            route = ChipText.route(state.card, Gazetteer.suburbs(context), StoreTable.all(context), lang),
            distance = ChipText.distance(state.card, lang),
            rate = ChipText.rate(state.card, RulesStore.current(context).tripCost, lang),
            fromCentre = fromCentre,
        )
    }

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    private companion object {
        /**
         * How far down the chip sits, in pixels from the very top of the screen.
         * It has to be clear of the card, which starts around halfway down and
         * whose text the reader parses - the chip's own words landing among that
         * text is what once put a suburb where a shop name belonged.
         */
        const val TOP_PIXELS = 20

        /** Enough to read at a glance, thin enough to see the map through. */
        const val TOOLS_ALPHA = 0.7f

        /** Below the status bar; the verdict chip starts at 20 px and is a row tall. */
        const val TOOLS_TOP_DP = 36

        /** Far enough in from the edge to clear the heartbeat dot's own window. */
        const val DOTS_FROM_EDGE_DP = 44

        /** One dot's swell and fade, and how dim it sits between them. */
        const val BEAT_MILLIS = 900L
        const val DOT_DIM = 0.25f

        private const val THINKING = "thinking"

        private const val TAG = "UEatsMonitor"

        // Yellow appears nowhere on the offer card - Uber's is white, its Accept
        // button green and its Match button black - so this is the one fill the
        // chip cannot hide against.
        val FILL: Int = Color.parseColor("#FAFFD400")
        val EDGE: Int = Color.parseColor("#FF8F00")
        val INK: Int = Color.parseColor("#14110A")

        /** For a shop with no car park of its own, on the yellow fill. */
        val WARN: Int = Color.parseColor("#B00020")

        /** Two frames' worth of grace, so a missed frame does not make it flicker. */
        const val TTL_MILLIS = 4_500L

        /** How long a closed chip stays away. */
        const val QUIET_MILLIS = 20_000L

        /** A gentle fade, not a snap. */
        const val FADE_MILLIS = 400L

        /** The close button's touch area, the size Android asks a tap target to be. */
        const val CLOSE_DP = 48
    }
}
