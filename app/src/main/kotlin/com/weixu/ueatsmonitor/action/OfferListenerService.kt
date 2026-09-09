package com.weixu.ueatsmonitor.action

import android.app.Notification
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
import com.weixu.ueatsmonitor.domain.Verdict

/**
 * Action. The only entry point: Android hands us every notification, we keep the Uber ones.
 * Parsing and judging happen in the domain; this class does IO and nothing else.
 */
class OfferListenerService : NotificationListenerService() {

    private val overlay: OverlayController by lazy { OverlayController(this) }

    override fun onListenerConnected() {
        ListenerStatus.connected.value = true
    }

    override fun onListenerDisconnected() {
        ListenerStatus.connected.value = false
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val settings = LiveSettings.current ?: return
        val fromUber = sbn.packageName == OfferParser.UBER_DRIVER_PACKAGE
        if (!fromUber && !settings.logEveryNotification) return

        val raw = readNotification(sbn)
        val result = if (fromUber) OfferParser.parse(raw) else ParseResult.NotAnOffer
        val verdict = (result as? ParseResult.Parsed)
            ?.let { OfferEvaluator.evaluate(it.offer, settings.thresholds) }

        OfferLog.add(LoggedEvent(raw = raw, result = result, verdict = verdict))

        if (verdict == null) return
        if (settings.overlayEnabled) overlay.show(verdict, subtitleOf(result))
        if (settings.vibrateEnabled) vibrate(verdict)
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

    private fun vibrator(): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        }
}
