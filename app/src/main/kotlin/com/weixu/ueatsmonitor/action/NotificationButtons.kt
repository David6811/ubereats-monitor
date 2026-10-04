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
                AppSwitch.bringForward(context, VoiceTarget.UBER)
            }

            SEND_TO_MAPS -> sendToMaps(context)

            TOGGLE_VOICE -> VoiceService.toggle(context) {}

            else -> Log.w(TAG, "notification button: unknown action " + intent.action)
        }
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

        private const val TAG = "UEatsMonitor"
    }
}
