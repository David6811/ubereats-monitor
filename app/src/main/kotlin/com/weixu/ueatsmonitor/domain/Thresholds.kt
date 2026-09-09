package com.weixu.ueatsmonitor.domain

import kotlinx.serialization.Serializable

/** Data. The driver's own accept/decline bar. No defaults here - see [Thresholds.STARTER]. */
@Serializable
data class Thresholds(
    val minPayout: Cents,
    val minPayPerMile: Double,
    val minPayPerHour: Double,
    val maxDistance: Miles,
) {
    companion object {
        /** A starting point, applied once on first launch and then owned by the user. */
        val STARTER: Thresholds = Thresholds(
            minPayout = Cents(500),
            minPayPerMile = 1.50,
            minPayPerHour = 20.0,
            maxDistance = Miles(8.0),
        )
    }
}
