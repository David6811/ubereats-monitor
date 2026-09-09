package com.weixu.ueatsmonitor.action

import android.app.Notification
import android.util.Log
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.weixu.ueatsmonitor.domain.OfferEvaluator
import com.weixu.ueatsmonitor.domain.OfferParser
import com.weixu.ueatsmonitor.domain.ParseResult
import com.weixu.ueatsmonitor.domain.RawNotification
import com.weixu.ueatsmonitor.domain.Thresholds
import com.weixu.ueatsmonitor.domain.Verdict

/**
 * Action. The only entry point: Android hands us every notification, we keep the Uber ones.
 * Parsing and judging happen in the domain; this class does IO and nothing else.
 */
class OfferListenerService : NotificationListenerService() {

    private val overlay: OverlayController by lazy { OverlayController(this) }

    override fun onListenerConnected() {
        ListenerStatus.connected.value = true
        Log.i(TAG, "listener connected")
    }

    override fun onListenerDisconnected() {
        ListenerStatus.connected.value = false
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val raw = readNotification(sbn)
        Log.i(TAG, "posted " + sbn.packageName + " :: " + raw.body)

        val settings = LiveSettings.current
        val fromUber = OfferParser.isUberPackage(sbn.packageName)
        if (!fromUber && settings?.logEveryNotification != true) return

        // Settings load asynchronously; a real offer must never be dropped waiting for them.
        val thresholds = settings?.thresholds ?: Thresholds.STARTER
        val result = if (fromUber) OfferParser.parse(raw) else ParseResult.NotAnOffer
        val verdict = (result as? ParseResult.Parsed)
            ?.let { OfferEvaluator.evaluate(it.offer, thresholds) }

        OfferLog.add(LoggedEvent(raw = raw, result = result, verdict = verdict))

        if (verdict == null) return
        // Silent by default: during a shift the app only records, it never interrupts.
        if (settings?.overlayEnabled == true) overlay.show(verdict, subtitleOf(result))
        if (settings?.vibrateEnabled == true) vibrate(verdict)
    }

    private fun readNotification(sbn: StatusBarNotification): RawNotification {
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = listOfNotNull(
            extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString(),
            extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString(),
        ).distinct().joinToString(" | ").ifBlank { null }
        return RawNotification(
            packageName = sbn.packageName,
            title = title,
            text = text,
            postedAtMillis = sbn.postTime,
        )
    }

    private fun subtitleOf(result: ParseResult): String? {
        val parsed = result as? ParseResult.Parsed ?: return null
        return listOfNotNull(parsed.offer.pickup, parsed.offer.dropoff)
            .joinToString(" → ")
            .ifBlank { null }
    }

    private fun vibrate(verdict: Verdict) {
        val vibrator = vibrator() ?: return
        val pattern = when (verdict) {
            is Verdict.Accept -> longArrayOf(0, 250, 120, 250)
            is Verdict.Uncertain -> longArrayOf(0, 400)
            is Verdict.Decline -> longArrayOf(0, 80)
        }
        vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
    }

    private companion object {
        const val TAG = "UEatsMonitor"
    }

    private fun vibrator(): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        }
}
