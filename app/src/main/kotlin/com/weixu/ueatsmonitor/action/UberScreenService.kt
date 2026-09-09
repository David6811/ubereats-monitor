package com.weixu.ueatsmonitor.action

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.hardware.HardwareBuffer
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.weixu.ueatsmonitor.domain.AreaCall
import com.weixu.ueatsmonitor.domain.AreaJudge
import com.weixu.ueatsmonitor.domain.OfferParser
import com.weixu.ueatsmonitor.domain.OfferShape
import com.weixu.ueatsmonitor.domain.ServiceArea
import com.weixu.ueatsmonitor.domain.Suburb
import com.weixu.ueatsmonitor.domain.SuburbIndex
import java.util.concurrent.Executors

/**
 * Action. Records what the Uber apps show, because a foreground offer card never
 * reaches the notification listener.
 *
 * It does not wait to be told. A shift was lost to that: for 28 minutes Android
 * delivered no accessibility event at all, so nothing was recorded even though
 * offers were ringing. This polls the window list on its own clock instead, and
 * treats incoming events only as a reason to look sooner.
 *
 * It only reads. It never taps, never accepts, never declines.
 */
class UberScreenService : AccessibilityService() {

    private val store: CaptureStore by lazy { CaptureStore(this) }
    private val position: CurrentPosition by lazy { CurrentPosition(this) }
    private val chime: Chime by lazy { Chime() }
    private val gazetteer: List<Suburb> by lazy { Gazetteer.suburbs(this) }

    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val poll = object : Runnable {
        override fun run() {
            look()
            main.postDelayed(this, POLL_MILLIS)
        }
    }

    private var lastText: String = ""
    private var lastCaptureAtMillis: Long = 0L
    private var lastRungSignature: String = ""
    private var lastRungAtMillis: Long = 0L
    private var lastHeartbeatAtMillis: Long = 0L

    override fun onServiceConnected() {
        Log.i(TAG, "accessibility service connected")
        ServiceJournal.note(this, "读屏已连接")
        main.removeCallbacks(poll)
        main.post(poll)
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        Log.w(TAG, "accessibility service unbound")
        ServiceJournal.note(this, "读屏被断开")
        main.removeCallbacks(poll)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        ServiceJournal.note(this, "读屏被销毁")
        main.removeCallbacks(poll)
        super.onDestroy()
    }

    /** An event is a hint that something moved, nothing more. */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        look()
    }

    override fun onInterrupt() = Unit

    private fun look() {
        val all = runCatching { windows.orEmpty().mapNotNull { it.root } }.getOrDefault(emptyList())
        val roots = uberRoots()
        heartbeat(all.map { it.packageName?.toString() ?: "null" }, roots.size)
        if (roots.isEmpty()) return

        val now = System.currentTimeMillis()
        if (now - lastCaptureAtMillis < MIN_GAP_MILLIS) return

        val lines = roots.flatMap { ScreenReader.readAll(it) }
        if (lines.isEmpty()) return

        val text = lines.joinToString("\n")
        if (text == lastText) return

        lastText = text
        lastCaptureAtMillis = now
        Log.i(TAG, "uber screen changed, ${lines.size} lines")

        val onScreen = roots.first().packageName?.toString() ?: "unknown"
        val fix = position.lastKnown()
        val decision = decide(text, now)

        val header = buildString {
            append("package=").append(onScreen).append('\n')
            append("windows=").append(roots.size).append('\n')
            append("lines=").append(lines.size).append('\n')
            append("millis=").append(now).append('\n')
            if (fix != null) {
                append("lat=").append(fix.at.latitude).append('\n')
                append("lon=").append(fix.at.longitude).append('\n')
                append("fix_millis=").append(fix.measuredAtMillis).append('\n')
            } else {
                append("fix=none\n")
            }
            append(decision)
        }
        DecisionLog.note(this, now, onScreen, decision)
        capture { screen ->
            store.write(now, screen, header + "screenshot=" + (screen != null) + "\n---\n" + text)
        }
    }

    /**
     * Every step of the judgement, written down. A shift is expensive to repeat,
     * so a capture must explain by itself why it did or did not make a sound.
     */
    private fun decide(text: String, now: Long): String {
        val money = CaptureText.hasMoney(text)
        val offerShape = OfferShape.looksLikeOffer(text)
        val found = SuburbIndex.findAll(text, gazetteer)
        val call = AreaJudge.call(found, ServiceArea.SOUTH_EAST)

        val chime = when {
            !offerShape -> "none_not_offer_shape"
            LiveSettings.current?.areaSoundEnabled == false -> "suppressed_setting_off"
            else -> {
                val signature = found.map { it.name }.sorted().joinToString(",").ifEmpty { "?" }
                if (signature == lastRungSignature && now - lastRungAtMillis < SAME_CALL_MILLIS) {
                    "suppressed_same_within_90s"
                } else {
                    lastRungSignature = signature
                    lastRungAtMillis = now
                    this@UberScreenService.chime.play(call)
                    "played_" + call::class.simpleName
                }
            }
        }

        Log.i(TAG, "decide money=$money offer=$offerShape suburbs=${found.map { it.name }} chime=$chime")

        return buildString {
            append("money=").append(money).append('\n')
            append("offer_shape=").append(offerShape).append('\n')
            append("suburbs=").append(found.joinToString(",") { it.name }).append('\n')
            append("area=").append(
                when (call) {
                    is AreaCall.AllInside -> "AllInside"
                    is AreaCall.SomeOutside -> "SomeOutside:" + call.outside.joinToString("/") { it.name }
                    AreaCall.NoSuburb -> "NoSuburb"
                }
            ).append('\n')
            append("chime=").append(chime).append('\n')
        }
    }

    /** Says once every few seconds what the service can actually see. */
    private fun heartbeat(packages: List<String>, uberCount: Int) {
        val now = System.currentTimeMillis()
        if (now - lastHeartbeatAtMillis < HEARTBEAT_MILLIS) return
        lastHeartbeatAtMillis = now
        val active = rootInActiveWindow?.packageName?.toString() ?: "null"
        Log.i(TAG, "poll: windows=$packages active=$active uber=$uberCount")
    }

    /** Every Uber window currently up - an offer card can sit in its own. */
    private fun uberRoots(): List<AccessibilityNodeInfo> {
        val fromWindows = runCatching {
            windows.orEmpty().mapNotNull { it.root }
        }.getOrDefault(emptyList())
        val candidates = fromWindows.ifEmpty { listOfNotNull(rootInActiveWindow) }
        return candidates.filter { node ->
            OfferParser.isUberPackage(node.packageName?.toString().orEmpty())
        }
    }

    /** Hands a screenshot to [onReady], or null when the platform refuses one. */
    private fun capture(onReady: (Bitmap?) -> Unit) {
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
                        Log.w(TAG, "screenshot failed, code $errorCode")
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
        const val POLL_MILLIS = 1_000L
        const val MIN_GAP_MILLIS = 1_500L
        const val SAME_CALL_MILLIS = 90_000L
        const val HEARTBEAT_MILLIS = 5_000L
    }
}
