package com.weixu.ueatsmonitor.action

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
    ) {
        /** How sure the guess is. A reading this old has probably been overtaken. */
        fun stale(nowMillis: Long): Boolean = nowMillis - seenAtMillis > STALE_AFTER_MILLIS
    }

    @Volatile
    var trip: Trip = Trip(shop = null, customer = null, lastSeenWasShop = true, seenAtMillis = 0L)
        private set

    fun headingToShop(address: String) {
        if (address.isBlank()) return
        trip = trip.copy(shop = address, lastSeenWasShop = true, seenAtMillis = System.currentTimeMillis())
    }

    fun headingToCustomer(address: String) {
        if (address.isBlank()) return
        trip = trip.copy(customer = address, lastSeenWasShop = false, seenAtMillis = System.currentTimeMillis())
    }

    /** A new offer's pickup screen starts a new trip; the last job's addresses are not this one's. */
    fun startOver() {
        trip = Trip(shop = null, customer = null, lastSeenWasShop = true, seenAtMillis = 0L)
    }

    /** Longer than a pickup wait, shorter than a delivery. */
    private const val STALE_AFTER_MILLIS = 25L * 60 * 1000
}
