package com.weixu.ueatsmonitor.action

import com.weixu.ueatsmonitor.domain.TripState

/**
 * Action. The job Uber is running right now: which shop, which door, and which
 * of the two the driver is on the way to.
 *
 * Uber's ongoing notification is the authority on *which job*, because it is
 * the only thing that says when one ends and the next begins. The screens fill
 * in what the notification leaves out - the shop's street address - and are
 * believed only when they name the shop the notification named.
 *
 * It used to be one record of two fields, overwritten and never cleared, and on
 * 4 Oct it showed the shop of one job beside the door of the one before it,
 * while Uber's own screen named a third. Nothing here survives the end of the
 * job it belongs to.
 *
 * Read from any thread, written from the reader's and the listener's.
 */
object CurrentStop {

    /** Data. One job in progress, as far as anything has said. */
    data class Trip(
        /** What Uber calls the shop. Null until a notice names one. */
        val shopName: String?,
        /** Its street address, which only the pickup screen carries. */
        val shopAddress: String?,
        /** The customer's address, in full, from the notice or the delivery screen. */
        val customer: String?,
        val headingToShop: Boolean,
        val seenAtMillis: Long,
        /** True when Uber's notification said it, which is the better word. */
        val fromNotice: Boolean,
    ) {
        /** What to drive to for the pickup: the street address when known, else the name. */
        val shop: String? get() = shopAddress ?: shopName

        fun stale(nowMillis: Long): Boolean = nowMillis - seenAtMillis > STALE_AFTER_MILLIS

        val empty: Boolean get() = shop == null && customer == null
    }

    private val NOTHING = Trip(null, null, null, headingToShop = true, seenAtMillis = 0L, fromNotice = false)

    @Volatile
    var trip: Trip = NOTHING
        private set

    /**
     * Uber's own words. A shop it has not named before is the next job, and the
     * job before it is gone - its door is not this job's door.
     */
    fun saw(state: TripState) {
        val now = System.currentTimeMillis()
        trip = when (state) {
            is TripState.Idle -> NOTHING
            is TripState.ToShop -> atShop(state.shop, now)
            is TripState.AtShop -> atShop(state.shop, now)
            is TripState.ToCustomer ->
                trip.copy(customer = state.address, headingToShop = false, seenAtMillis = now, fromNotice = true)
            is TripState.AtCustomer ->
                trip.copy(headingToShop = false, seenAtMillis = now, fromNotice = true)
        }
    }

    private fun atShop(name: String, now: Long): Trip =
        if (sameShop(trip.shopName, name)) {
            trip.copy(headingToShop = true, seenAtMillis = now, fromNotice = true)
        } else {
            Trip(name, shopAddress = null, customer = null, headingToShop = true, seenAtMillis = now, fromNotice = true)
        }

    /**
     * The pickup screen, which is the only place the shop's street address is
     * written. Believed when it names the shop the notice named, or when no
     * notice has named one; a screen left over from an earlier job is what put
     * another suburb's address on this job's panel.
     */
    fun pickupScreen(store: String, address: String) {
        if (address.isBlank()) return
        val now = System.currentTimeMillis()
        val named = trip.shopName
        when {
            named != null && sameShop(named, store) -> trip = trip.copy(shopAddress = address)
            named != null -> Unit
            else -> trip = Trip(store, address, customer = null, headingToShop = true, seenAtMillis = now, fromNotice = false)
        }
    }

    /**
     * The delivery screen. The notice carries the same address and carries it
     * sooner, so this only fills a gap it left - never argues with it.
     */
    fun dropoffScreen(address: String) {
        if (address.isBlank()) return
        val now = System.currentTimeMillis()
        if (trip.fromNotice && !trip.stale(now)) {
            if (trip.customer == null) trip = trip.copy(customer = address)
            return
        }
        trip = trip.copy(customer = address, headingToShop = false, seenAtMillis = now, fromNotice = false)
    }

    /** Either name inside the other: "KFC" against "KFC (Noble Park)". */
    private fun sameShop(one: String?, other: String?): Boolean {
        val a = fold(one ?: return false)
        val b = fold(other ?: return false)
        if (a.length < MIN_NAME || b.length < MIN_NAME) return false
        return a.contains(b) || b.contains(a)
    }

    private fun fold(text: String): String = text.lowercase().filter { it.isLetterOrDigit() }

    /** "KFC" is a shop. Shorter than this and a name would sit inside anything. */
    private const val MIN_NAME = 3

    /** Longer than a pickup wait, shorter than a delivery. */
    private const val STALE_AFTER_MILLIS = 25L * 60 * 1000
}
