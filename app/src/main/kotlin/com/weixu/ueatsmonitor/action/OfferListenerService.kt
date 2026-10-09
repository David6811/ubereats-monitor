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
import com.weixu.ueatsmonitor.domain.TripNotice
import com.weixu.ueatsmonitor.domain.TripState
import com.weixu.ueatsmonitor.domain.Verdict

/**
 * Action. The only entry point: Android hands us every notification, we keep the Uber ones.
 * Parsing and judging happen in the domain; this class does IO and nothing else.
 */
class OfferListenerService : NotificationListenerService() {


    override fun onListenerConnected() {
        ListenerStatus.connected.value = true
        Log.i(TAG, "listener connected")
        // An ongoing notification is posted once and then sits there. Connecting
        // after it was posted - every restart, every reboot - means onPosted
        // never fires for it, so what is already on the shade is read here.
        runCatching {
            activeNotifications.orEmpty()
                .filter { it.packageName == MAPS }
                .forEach { MapsNotice.saw(it.notification) }
            activeNotifications.orEmpty()
                .filter { OfferParser.isUberPackage(it.packageName) }
                .forEach { sbn ->
                    val raw = readNotification(sbn)
                    val line = listOfNotNull(raw.title, raw.text).joinToString(" | ")
                    TripProbe.note(this, "onshade", line)
                    TripNotice.read(line)?.let { CurrentStop.saw(it) }
                }
        }.onFailure { Log.w(TAG, "listener: could not read what is already posted", it) }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.packageName == MAPS) MapsNotice.gone()
    }

    override fun onListenerDisconnected() {
        ListenerStatus.connected.value = false
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        // Google Maps' own "exit navigation", kept for the button that ends a
        // drive. It works with the map shrunk into the corner, where pressing
        // its cross cannot.
        if (sbn.packageName == MAPS) MapsNotice.saw(sbn.notification)
        val raw = readNotification(sbn)
        Log.i(TAG, "posted " + sbn.packageName + " :: " + raw.body)

        val settings = LiveSettings.current
        val fromUber = OfferParser.isUberPackage(sbn.packageName)
        // Uber's own words about the trip, written down to be read after a
        // shift. Nothing acts on them yet; see TripProbe.
        if (fromUber) {
            val line = listOfNotNull(raw.title, raw.text).joinToString(" | ")
            TripProbe.note(this, "notify", line)
            // Uber's own words about the trip. Better than either screen: this
            // arrives with the phone in a pocket, which is where a quarter of
            // the pickup screens were lost.
            TripNotice.read(line)?.let { state ->
                CurrentStop.saw(state)
                // "Going to <shop>" is Uber saying the offer was accepted, and
                // it says so whether or not the pickup screen is ever read.
                val shop = (state as? TripState.ToShop)?.shop ?: (state as? TripState.AtShop)?.shop
                if (shop != null) JobStore.markTakenAtShop(this, shop)
            }
        }
        if (!fromUber && settings?.logEveryNotification != true) return

        // Settings load asynchronously; a real offer must never be dropped waiting for them.
        val thresholds = settings?.thresholds ?: Thresholds.STARTER
        val result = if (fromUber) OfferParser.parse(raw) else ParseResult.NotAnOffer
        val verdict = (result as? ParseResult.Parsed)
            ?.let { OfferEvaluator.evaluate(it.offer, thresholds) }

        OfferLog.add(LoggedEvent(raw = raw, result = result, verdict = verdict))

        if (verdict == null) return
        // No chip from here. A whole shift of notifications proved they never carry
        // an offer - only trip progress after one is accepted - so the card on
        // screen, not this, is what the driver is shown.
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
        const val MAPS = "com.google.android.apps.maps"
    }

    private fun vibrator(): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        }

}
