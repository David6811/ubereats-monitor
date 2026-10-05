package com.weixu.ueatsmonitor.action

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import com.weixu.ueatsmonitor.domain.DropoffScreen
import com.weixu.ueatsmonitor.domain.PickupScreen
import com.weixu.ueatsmonitor.domain.StoreKinds
import com.weixu.ueatsmonitor.domain.TripNotice
import com.weixu.ueatsmonitor.domain.VoiceTarget

/**
 * Action. The buttons in the ongoing notification, which do what the floating
 * ones over the map do.
 *
 * The shade is one swipe away with the phone in a cradle, and these sit in the
 * collapsed view, so nothing has to be expanded to reach them - a second swipe
 * at the wheel is a second with eyes off the road.
 */
class NotificationButtons : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val words = driverWords()
        when (intent.action) {
            STOP_NAVIGATION -> MapsNavigation.stop(context) { pressed ->
                val said = if (pressed) words.navigationClosed else words.mapsNotNavigating
                Toast.makeText(context, said, Toast.LENGTH_SHORT).show()
                sendMapsAway(context)
            }

            SEND_TO_MAPS -> sendToMaps(context)

            TOGGLE_VOICE -> VoiceService.toggle(context) {}

            else -> Log.w(TAG, "notification button: unknown action " + intent.action)
        }
    }

    /**
     * Get Google Maps off the screen.
     *
     * Stopping a navigation needs Maps in front, because its cross is pressed
     * by finding it on screen - so the button that ends the drive was leaving
     * the map filling the phone. Uber is what the driver wants in front of him
     * next; the home screen will do when Uber cannot be reached, which is
     * still better than the map he just closed. A moment first, or the switch
     * lands while Maps is still settling and Maps wins.
     */
    private fun sendMapsAway(context: Context) {
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (!AppSwitch.bringForward(context, VoiceTarget.UBER)) UberScreenService.goHome()
        }, SETTLE_MILLIS)
    }

    /**
     * The same order as the floating button: what Uber has on screen beats
     * anything remembered, and the job in hand is the fallback.
     */
    private fun sendToMaps(context: Context) {
        val words = driverWords()
        val onScreen = UberScreenService.uberScreenNow()
        DropoffScreen.read(onScreen)?.let { return go(context, words.toCustomerLabel, it.address) }
        PickupScreen.read(onScreen)?.let { return go(context, words.toShopLabel, it.address) }

        val trip = CurrentStop.trip
        val where = if (trip.headingToShop) trip.shop ?: trip.customer else trip.customer ?: trip.shop
        if (where == null) {
            Toast.makeText(context, words.noStopYet, Toast.LENGTH_LONG).show()
            return
        }
        go(context, if (trip.headingToShop) words.toShopLabel else words.toCustomerLabel, where)
    }

    private fun go(context: Context, label: String, where: String) {
        Toast.makeText(context, label + "  " + where, Toast.LENGTH_SHORT).show()
        if (TripNotice.looksLikeAddress(where)) {
            Navigation.driveTo(context, where)
            return
        }
        val found = StoreKinds.find(where, StoreTable.all(context))
        if (found != null) Navigation.driveTo(context, found.at) else Navigation.driveTo(context, where)
    }

    companion object {
        const val STOP_NAVIGATION = "com.weixu.ueatsmonitor.STOP_NAVIGATION"
        const val SEND_TO_MAPS = "com.weixu.ueatsmonitor.SEND_TO_MAPS"
        const val TOGGLE_VOICE = "com.weixu.ueatsmonitor.TOGGLE_VOICE"

        /** Long enough for Maps to finish closing its route before it is sent away. */
        private const val SETTLE_MILLIS = 700L

        private const val TAG = "UEatsMonitor"
    }
}
