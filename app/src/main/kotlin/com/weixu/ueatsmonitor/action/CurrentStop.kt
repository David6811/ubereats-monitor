package com.weixu.ueatsmonitor.action

import com.weixu.ueatsmonitor.domain.TripState

/**
 * Action. What Uber last said about the job in hand: where the food is, where
 * it is going, and which of the two the driver was last shown.
 *
 * Uber's map cannot be asked where it is going. Its pickup screen and its
 * delivery screen both carry a full street address, and both are read - but
 * only while Uber's window is on screen, which on this board failed for 16 of
 * 71 pickups and 7 of 71 deliveries.
 *
 * So the last screen read is a guess, not an answer, and it is held as one.
 * The one that matters is kept with the time it was read, and both addresses
 * are kept whether or not they are the guess, because a driver sent to the
 * shop he has just left loses more than a tap.
 *
 * Read from any thread, written from the reader's.
 */
object CurrentStop {

    /** Data. The two ends of the job in hand, and which was last on screen. */
    data class Trip(
        val shop: String?,
        val customer: String?,
        /** True when the pickup screen was the last of the two seen. */
        val lastSeenWasShop: Boolean,
        val seenAtMillis: Long,
        /** True when Uber's notification said it, which is the better word. */
        val fromNotice: Boolean,
    ) {
        /** How sure the guess is. A reading this old has probably been overtaken. */
        fun stale(nowMillis: Long): Boolean = nowMillis - seenAtMillis > STALE_AFTER_MILLIS
    }

    @Volatile
    var trip: Trip = Trip(shop = null, customer = null, lastSeenWasShop = true, seenAtMillis = 0L, fromNotice = false)
        private set

    /**
     * The pickup screen. It carries the shop's street address, which the notice
     * does not, so the address is always worth keeping - but which half of the
     * job is running is left to the notice when the notice is still fresh.
     */
    fun headingToShop(address: String) {
        if (address.isBlank()) return
        val now = System.currentTimeMillis()
        val noticeWins = trip.fromNotice && !trip.stale(now)
        trip = trip.copy(
            shop = address,
            lastSeenWasShop = if (noticeWins) trip.lastSeenWasShop else true,
            seenAtMillis = if (noticeWins) trip.seenAtMillis else now,
            fromNotice = noticeWins,
        )
    }

    fun headingToCustomer(address: String) {
        if (address.isBlank()) return
        val now = System.currentTimeMillis()
        val noticeWins = trip.fromNotice && !trip.stale(now)
        trip = trip.copy(
            customer = address,
            lastSeenWasShop = if (noticeWins) trip.lastSeenWasShop else false,
            seenAtMillis = if (noticeWins) trip.seenAtMillis else now,
            fromNotice = noticeWins,
        )
    }

    /**
     * What Uber's own notification said. It beats both screens: it arrives with
     * the phone in a pocket, and it names which half of the job is running
     * rather than leaving that to be inferred from whichever screen was read
     * last. A screen reading never overwrites a notification newer than itself.
     */
    fun saw(state: TripState) {
        val now = System.currentTimeMillis()
        trip = when (state) {
            is TripState.Idle -> Trip(null, null, lastSeenWasShop = true, seenAtMillis = 0L, fromNotice = true)
            is TripState.ToShop -> trip.copy(shop = state.shop, lastSeenWasShop = true, seenAtMillis = now, fromNotice = true)
            is TripState.AtShop -> trip.copy(shop = state.shop, lastSeenWasShop = true, seenAtMillis = now, fromNotice = true)
            is TripState.ToCustomer -> trip.copy(customer = state.address, lastSeenWasShop = false, seenAtMillis = now, fromNotice = true)
            is TripState.AtCustomer -> trip.copy(lastSeenWasShop = false, seenAtMillis = now, fromNotice = true)
        }
    }

    /** A new offer's pickup screen starts a new trip; the last job's addresses are not this one's. */
    fun startOver() {
        trip = Trip(shop = null, customer = null, lastSeenWasShop = true, seenAtMillis = 0L, fromNotice = false)
    }

    /** Longer than a pickup wait, shorter than a delivery. */
    private const val STALE_AFTER_MILLIS = 25L * 60 * 1000
}
