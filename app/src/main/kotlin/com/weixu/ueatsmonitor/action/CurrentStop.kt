package com.weixu.ueatsmonitor.action

/**
 * Action. Where Uber is sending the driver right now, as its own screens said it.
 *
 * Uber's map cannot be asked where it is going, but it cannot be started either
 * without first opening the stop - the pickup screen or the delivery screen -
 * and both of those carry the full street address. Whichever was read last is
 * the stop in hand, and it stays here until the next one replaces it.
 *
 * Read from any thread, written from the reader's.
 */
object CurrentStop {

    /** Data. A stop, and which end of the job it is. */
    data class Stop(val address: String, val toShop: Boolean)

    @Volatile
    var stop: Stop? = null
        private set

    fun headingToShop(address: String) {
        if (address.isNotBlank()) stop = Stop(address, toShop = true)
    }

    fun headingToCustomer(address: String) {
        if (address.isNotBlank()) stop = Stop(address, toShop = false)
    }
}
