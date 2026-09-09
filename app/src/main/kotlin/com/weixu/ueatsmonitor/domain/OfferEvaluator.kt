package com.weixu.ueatsmonitor.domain

/**
 * Calculation. Offer + the driver's bar in, [Verdict] out.
 * A rule whose input is missing is never counted as a pass - it lands in [Verdict.Uncertain].
 */
object OfferEvaluator {

    fun metricsOf(offer: Offer): OfferMetrics {
        val miles = offer.distance?.value?.takeIf { it > 0.0 }
        val hours = offer.duration?.value?.takeIf { it > 0 }?.let { it / 60.0 }
        return OfferMetrics(
            payout = offer.payout,
            distance = offer.distance,
            duration = offer.duration,
            payPerMile = miles?.let { offer.payout.dollars / it },
            payPerHour = hours?.let { offer.payout.dollars / it },
        )
    }

    fun evaluate(offer: Offer, thresholds: Thresholds): Verdict {
        val metrics = metricsOf(offer)
        val failures = mutableListOf<RuleFailure>()
        val uncheckable = mutableListOf<Rule>()

        if (offer.payout.amount < thresholds.minPayout.amount) {
            failures += RuleFailure(Rule.MIN_PAYOUT, offer.payout.dollars, thresholds.minPayout.dollars)
        }

        val distance = offer.distance
        if (distance == null) {
            uncheckable += Rule.MAX_DISTANCE
            uncheckable += Rule.MIN_PAY_PER_MILE
        } else {
            if (distance.value > thresholds.maxDistance.value) {
                failures += RuleFailure(Rule.MAX_DISTANCE, distance.value, thresholds.maxDistance.value)
            }
            val payPerMile = metrics.payPerMile
            if (payPerMile == null) {
                uncheckable += Rule.MIN_PAY_PER_MILE
            } else if (payPerMile < thresholds.minPayPerMile) {
                failures += RuleFailure(Rule.MIN_PAY_PER_MILE, payPerMile, thresholds.minPayPerMile)
            }
        }

        val payPerHour = metrics.payPerHour
        if (payPerHour == null) {
            uncheckable += Rule.MIN_PAY_PER_HOUR
        } else if (payPerHour < thresholds.minPayPerHour) {
            failures += RuleFailure(Rule.MIN_PAY_PER_HOUR, payPerHour, thresholds.minPayPerHour)
        }

        return when {
            failures.isNotEmpty() -> Verdict.Decline(metrics, failures)
            uncheckable.isNotEmpty() -> Verdict.Uncertain(metrics, uncheckable)
            else -> Verdict.Accept(metrics)
        }
    }
}
