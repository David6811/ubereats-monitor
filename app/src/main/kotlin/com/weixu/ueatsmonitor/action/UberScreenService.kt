package com.weixu.ueatsmonitor.action

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.hardware.HardwareBuffer
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.weixu.ueatsmonitor.domain.AreaCall
import com.weixu.ueatsmonitor.domain.AreaJudge
import com.weixu.ueatsmonitor.domain.OfferParser
import com.weixu.ueatsmonitor.domain.ServiceArea
import com.weixu.ueatsmonitor.domain.Suburb
import com.weixu.ueatsmonitor.domain.SuburbIndex
import java.util.concurrent.Executors

/**
 * Action. Watches the Uber apps' screens, because a foreground offer card never
 * reaches the notification listener. Every distinct screen is written to disk as
 * a text dump plus a screenshot, for offline analysis.
 *
 * It only reads. It never taps, never accepts, never declines.
 */
class UberScreenService : AccessibilityService() {

    private val store: CaptureStore by lazy { CaptureStore(this) }
    private val position: CurrentPosition by lazy { CurrentPosition(this) }
    private val chime: Chime by lazy { Chime() }
    private val gazetteer: List<Suburb> by lazy { Gazetteer.suburbs(this) }
    private val executor = Executors.newSingleThreadExecutor()

    private var lastText: String = ""
    private var lastCaptureAtMillis: Long = 0L
    private var lastRungSignature: String = ""
    private var lastRungAtMillis: Long = 0L

    override fun onServiceConnected() {
        Log.i(TAG, "accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val packageName = event?.packageName?.toString() ?: return
        if (!OfferParser.isUberPackage(packageName)) return

        val now = System.currentTimeMillis()
        if (now - lastCaptureAtMillis < MIN_GAP_MILLIS) return

        // The event can come from an Uber bubble while another app owns the screen.
        // Only record when the window we are about to read is Uber's own.
        val root = rootInActiveWindow ?: return
        val onScreen = root.packageName?.toString() ?: return
        if (!OfferParser.isUberPackage(onScreen)) return

        val lines = ScreenReader.readAll(root)
        if (lines.isEmpty()) return

        val text = lines.joinToString("\n")
        if (text == lastText) return

        lastText = text
        lastCaptureAtMillis = now
        ring(text, now)
        Log.i(TAG, "screen changed in $packageName, ${lines.size} lines")

        // Where the car was when the offer appeared. Reading it later would answer
        // a different question: where the phone is now, sitting at home.
        val fix = position.lastKnown()
        val header = buildString {
            append("package=").append(onScreen).append('\n')
            append("event_from=").append(packageName).append('\n')
            append("millis=").append(now).append('\n')
            append("event=").append(event.eventType).append('\n')
            if (fix != null) {
                append("lat=").append(fix.at.latitude).append('\n')
                append("lon=").append(fix.at.longitude).append('\n')
                append("fix_millis=").append(fix.measuredAtMillis).append('\n')
            }
            append("---\n")
        }
        capture(now) { screen -> store.write(now, screen, header + text) }
    }

    /**
     * Rings only for a screen that shows money and names a place, and never twice
     * for the same set of places inside [SAME_CALL_MILLIS] - Uber redraws constantly.
     */
    private fun ring(text: String, now: Long) {
        if (LiveSettings.current?.areaSoundEnabled == false) return
        if (!CaptureText.hasMoney(text)) return

        val found = SuburbIndex.findAll(text, gazetteer)
        val call = AreaJudge.call(found, ServiceArea.SOUTH_EAST)
        if (call is AreaCall.NoSuburb) return

        val signature = found.map { it.name }.sorted().joinToString(",")
        if (signature == lastRungSignature && now - lastRungAtMillis < SAME_CALL_MILLIS) return
        lastRungSignature = signature
        lastRungAtMillis = now

        Log.i(TAG, "area call for [$signature] -> " + call::class.simpleName)
        chime.play(call)
    }

    override fun onInterrupt() = Unit

    /** Hands a screenshot to [onReady], or null when the platform refuses one. */
    private fun capture(atMillis: Long, onReady: (Bitmap?) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            onReady(null)
            return
        }
        runCatching {
            takeScreenshot(
                android.view.Display.DEFAULT_DISPLAY,
                executor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(result: ScreenshotResult) {
                        val bitmap = toBitmap(result.hardwareBuffer, result.colorSpace)
                        result.hardwareBuffer.close()
                        onReady(bitmap)
                    }

                    override fun onFailure(errorCode: Int) {
                        Log.w(TAG, "screenshot failed, code $errorCode at $atMillis")
                        onReady(null)
                    }
                },
            )
        }.onFailure {
            Log.w(TAG, "screenshot threw: " + it.message)
            onReady(null)
        }
    }

    private fun toBitmap(buffer: HardwareBuffer, colorSpace: ColorSpace): Bitmap? {
        val wrapped = Bitmap.wrapHardwareBuffer(buffer, colorSpace) ?: return null
        // Copy off the hardware buffer so the bitmap survives close() and can be compressed.
        return wrapped.copy(Bitmap.Config.ARGB_8888, false)
    }

    private companion object {
        const val TAG = "UEatsMonitor"
        const val MIN_GAP_MILLIS = 2_500L
        const val SAME_CALL_MILLIS = 90_000L
    }
}
