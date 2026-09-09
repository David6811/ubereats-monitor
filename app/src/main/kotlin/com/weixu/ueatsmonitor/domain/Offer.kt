package com.weixu.ueatsmonitor.domain

import kotlinx.serialization.Serializable

/**
 * Data. What the Uber Driver notification told us, already normalized.
 * Everything the parser could not find is null - never invented.
 */
@Serializable
data class Offer(
    val payout: Cents,
    val distance: Miles?,
    val duration: Minutes?,
    val pickup: String?,
    val dropoff: String?,
)

/** Data. The untouched notification, kept so the parser can be tuned against reality. */
@Serializable
data class RawNotification(
    val packageName: String,
    val title: String?,
    val text: String?,
    val postedAtMillis: Long,
) {
    val body: String get() = listOfNotNull(title, text).joinToString(" | ")
}

/** Data. Sum type: a notification is an offer, or noise, or an offer we failed to read. */
@Serializable
sealed interface ParseResult {
    @Serializable
    data class Parsed(val offer: Offer) : ParseResult

    @Serializable
    data class Unreadable(val reason: String) : ParseResult

    @Serializable
    data object NotAnOffer : ParseResult
}
