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
import android.widget.LinearLayout
import android.widget.TextView
import com.weixu.ueatsmonitor.domain.Verdict
import com.weixu.ueatsmonitor.domain.VerdictText

/**
 * Action. Draws one card on top of the Uber Driver app and takes it away again.
 * Everything it shows was already decided by [com.weixu.ueatsmonitor.domain.OfferEvaluator].
 */
class OverlayController(private val context: Context) {

    private val main = Handler(Looper.getMainLooper())
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private var shown: View? = null

    fun canDraw(): Boolean = Settings.canDrawOverlays(context)

    fun show(verdict: Verdict, subtitle: String?) {
        if (!canDraw()) return
        main.post {
            dismissNow()
            val card = buildCard(verdict, subtitle)
            runCatching { windowManager.addView(card, layoutParams()) }
                .onSuccess { shown = card }
            main.postDelayed(::dismissNow, VISIBLE_MILLIS)
        }
    }

    fun dismiss() = main.post(::dismissNow)

    private fun dismissNow() {
        val card = shown ?: return
        shown = null
        runCatching { windowManager.removeView(card) }
    }

    private fun layoutParams(): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP
            y = dp(24)
        }
    }

    private fun buildCard(verdict: Verdict, subtitle: String?): View {
        val accent = accentOf(verdict)
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(16))
            background = GradientDrawable().apply {
                cornerRadius = dp(20).toFloat()
                setColor(Color.parseColor("#F2101418"))
                setStroke(dp(2), accent)
            }
            addView(line(VerdictText.headline(verdict), sizeSp = 34f, color = accent, bold = true))
            addView(line(VerdictText.metricsLine(verdict.metrics), sizeSp = 18f, color = Color.WHITE, bold = false))
            addView(line(VerdictText.reason(verdict), sizeSp = 14f, color = Color.parseColor("#B0BEC5"), bold = false))
            subtitle?.let {
                addView(line(it, sizeSp = 13f, color = Color.parseColor("#78909C"), bold = false))
            }
            setOnClickListener { dismissNow() }
        }
    }

    private fun line(text: String, sizeSp: Float, color: Int, bold: Boolean): TextView =
        TextView(context).apply {
            this.text = text
            textSize = sizeSp
            setTextColor(color)
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        }

    private fun accentOf(verdict: Verdict): Int = when (verdict) {
        is Verdict.Accept -> Color.parseColor("#4CAF50")
        is Verdict.Decline -> Color.parseColor("#EF5350")
        is Verdict.Uncertain -> Color.parseColor("#FFB300")
    }

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    private companion object {
        const val VISIBLE_MILLIS = 40_000L
    }
}
