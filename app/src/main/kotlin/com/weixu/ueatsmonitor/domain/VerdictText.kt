package com.weixu.ueatsmonitor.domain

/** Calculation. Verdict in, the few short strings the overlay shows out. */
object VerdictText {

    fun headline(verdict: Verdict): String = when (verdict) {
        is Verdict.Accept -> "接单"
        is Verdict.Decline -> "不接"
        is Verdict.Uncertain -> "自己看"
    }

    /** One line per number the driver decides on, e.g. "$8.25 · 3.4 mi · $2.43/mi · $27.5/h". */
    fun metricsLine(metrics: OfferMetrics): String = listOfNotNull(
        metrics.payout.toString(),
        metrics.distance?.let { format(it.value) + " mi" },
        metrics.duration?.let { "${it.value} min" },
        metrics.payPerMile?.let { "$" + format(it) + "/mi" },
        metrics.payPerHour?.let { "$" + format(it) + "/h" },
    ).joinToString(" · ")

    fun reason(verdict: Verdict): String = when (verdict) {
        is Verdict.Accept -> "全部达标"
        is Verdict.Decline -> verdict.failures.joinToString("；") { describe(it) }
        is Verdict.Uncertain -> "缺少数据：" + verdict.uncheckable.joinToString("、") { label(it) }
    }

    private fun describe(failure: RuleFailure): String = when (failure.rule) {
        Rule.MIN_PAYOUT -> "总价 $" + format(failure.actual) + " < $" + format(failure.required)
        Rule.MIN_PAY_PER_MILE -> "$" + format(failure.actual) + "/mi < $" + format(failure.required) + "/mi"
        Rule.MIN_PAY_PER_HOUR -> "$" + format(failure.actual) + "/h < $" + format(failure.required) + "/h"
        Rule.MAX_DISTANCE -> format(failure.actual) + " mi > " + format(failure.required) + " mi"
    }

    private fun label(rule: Rule): String = when (rule) {
        Rule.MIN_PAYOUT -> "总价"
        Rule.MIN_PAY_PER_MILE -> "每英里"
        Rule.MIN_PAY_PER_HOUR -> "每小时"
        Rule.MAX_DISTANCE -> "里程"
    }

    private fun format(value: Double): String = String.format("%.2f", value).removeSuffix("0").removeSuffix("0").removeSuffix(".")
}
