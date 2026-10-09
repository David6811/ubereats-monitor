package com.weixu.ueatsmonitor.action

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.weixu.ueatsmonitor.domain.DropoffScreen
import com.weixu.ueatsmonitor.domain.PickupScreen
import com.weixu.ueatsmonitor.domain.StoreKinds
import com.weixu.ueatsmonitor.domain.TripNotice
import com.weixu.ueatsmonitor.domain.VoiceTarget

/**
 * Action. What the buttons in the ongoing notification do.
 *
 * The shade is one swipe away with the phone in a cradle, and these sit in the
 * collapsed view, so nothing has to be expanded to reach them - a second swipe
 * at the wheel is a second with eyes off the road.
 *
 * Started by [ButtonActivity] rather than by a broadcast, because starting an
 * activity is what collapses the shade. While the shade is open it is the only
 * window the reader can see, and the button that ends a navigation went
 * looking for Google Maps behind it and found nothing at all.
 */
object NotificationButtons {

    const val STOP_NAVIGATION = "com.weixu.ueatsmonitor.STOP_NAVIGATION"
    const val SEND_TO_MAPS = "com.weixu.ueatsmonitor.SEND_TO_MAPS"
    const val TOGGLE_VOICE = "com.weixu.ueatsmonitor.TOGGLE_VOICE"

    fun run(context: Context, action: String) {
        val words = driverWords()
        when (action) {
            STOP_NAVIGATION -> after(SHADE_MILLIS) {
                MapsNavigation.stop(context) { pressed ->
                    val said = if (pressed) words.navigationClosed else words.mapsNotNavigating
                    Toast.makeText(context, said, Toast.LENGTH_SHORT).show()
                    sendMapsAway(context)
                }
            }

            SEND_TO_MAPS -> after(SHADE_MILLIS) { sendToMaps(context) }

            TOGGLE_VOICE -> VoiceService.toggle(context) {}

            else -> Log.w(TAG, "notification button: unknown action $action")
        }
    }

    /**
     * Get Google Maps off the screen.
     *
     * Stopping a navigation needs Maps in front, because its cross is pressed
     * by finding it there - so the button that ends the drive was leaving the
     * map filling the phone. Uber is what the driver wants in front of him
     * next; the home screen will do when Uber cannot be reached, which still
     * beats the map he has just closed.
     */
    private fun sendMapsAway(context: Context) {
        after(SETTLE_MILLIS) {
            if (!AppSwitch.bringForward(context, VoiceTarget.UBER)) UberScreenService.goHome()
        }
    }

    /**
     * The same question in the same order: what Uber has on screen beats
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

    /**
     * Uber's notification names the shop but never gives its address, and the
     * bundled table holds a thousand of them with their coordinates, so a name
     * is driven to as a point rather than handed to a search.
     */
    private fun go(context: Context, label: String, where: String) {
        Toast.makeText(context, "$label  $where", Toast.LENGTH_SHORT).show()
        if (TripNotice.looksLikeAddress(where)) {
            Navigation.driveTo(context, where)
            return
        }
        val found = StoreKinds.find(where, StoreTable.all(context))
        if (found != null) Navigation.driveTo(context, found.at) else Navigation.driveTo(context, where)
    }

    private fun after(millis: Long, work: () -> Unit) {
        Handler(Looper.getMainLooper()).postDelayed(work, millis)
    }

    /** Long enough for the shade to be gone before the screen behind it is read. */
    private const val SHADE_MILLIS = 350L

    /** Long enough for Maps to finish closing its route before it is sent away. */
    private const val SETTLE_MILLIS = 700L

    private const val TAG = "UEatsMonitor"
}
