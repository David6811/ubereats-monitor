package com.weixu.ueatsmonitor.domain

import kotlinx.serialization.Serializable

/** Data. Which bar an offer was measured against. */
@Serializable
enum class Rule { MIN_PAYOUT, MIN_PAY_PER_MILE, MIN_PAY_PER_HOUR, MAX_DISTANCE }

/** Data. One bar that the offer missed, with the numbers that made it miss. */
@Serializable
data class RuleFailure(val rule: Rule, val actual: Double, val required: Double)

/** Data. The derived numbers a driver actually decides on. Null when the input was missing. */
@Serializable
data class OfferMetrics(
    val payout: Cents,
    val distance: Miles?,
    val duration: Minutes?,
    val payPerMile: Double?,
    val payPerHour: Double?,
)

/**
 * Data. Sum type: three outcomes, and each one carries exactly what it needs.
 * [Uncertain] exists because a missing distance is not the same as a bad distance.
 */
@Serializable
sealed interface Verdict {
    val metrics: OfferMetrics

    @Serializable
    data class Accept(override val metrics: OfferMetrics) : Verdict

    @Serializable
    data class Decline(
        override val metrics: OfferMetrics,
        val failures: List<RuleFailure>,
    ) : Verdict

    @Serializable
    data class Uncertain(
        override val metrics: OfferMetrics,
        val uncheckable: List<Rule>,
    ) : Verdict
}
