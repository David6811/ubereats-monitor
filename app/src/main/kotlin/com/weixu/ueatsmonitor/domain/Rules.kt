package com.weixu.ueatsmonitor.domain

/**
 * Data. The driver's rules, exactly as set on the laptop. Nothing here is
 * decided by the app: no payout floor, no dollars per mile, no distance cap.
 * Those were invented once and are gone.
 */
data class Rules(
    val allowedSuburbs: Set<String>,
    val deniedStores: List<String>,
)

/** Data. Sum type: why an offer is or is not worth taking. */
sealed interface Ruling {

    data class Take(val suburb: String) : Ruling

    data class Leave(val reason: Reason) : Ruling

    /** Data. Nothing was set, so nothing can be said. */
    data object NoRules : Ruling

    /** Data. The card gave no destination suburb we recognise. */
    data object Unknown : Ruling

    sealed interface Reason {
        data class SuburbNotAllowed(val suburb: String) : Reason
        data class StoreDenied(val store: String) : Reason
    }
}

/**
 * Calculation. Card plus rules in, [Ruling] out.
 *
 * Two rules, in order: the pickup must not be on the deny list, and the
 * destination's suburb must be on the allow list. The first one that says no
 * is the one reported, because that is the one the driver needs to hear.
 */
object RuleJudge {

    fun judge(card: OfferCard, rules: Rules, gazetteer: List<Suburb>): Ruling {
        if (rules.allowedSuburbs.isEmpty() && rules.deniedStores.isEmpty()) return Ruling.NoRules

        val denied = rules.deniedStores.firstOrNull { name ->
            name.isNotBlank() && card.pickup.contains(name, ignoreCase = true)
        }
        if (denied != null) return Ruling.Leave(Ruling.Reason.StoreDenied(denied))

        if (rules.allowedSuburbs.isEmpty()) return Ruling.Unknown

        val found = SuburbIndex.findAll(card.dropoff, gazetteer)
        if (found.isEmpty()) return Ruling.Unknown

        val outside = found.firstOrNull { suburb ->
            rules.allowedSuburbs.none { it.equals(suburb.name, ignoreCase = true) }
        }
        return if (outside != null) {
            Ruling.Leave(Ruling.Reason.SuburbNotAllowed(outside.name))
        } else {
            Ruling.Take(found.first().name)
        }
    }
}

/** Calculation. The ruling in the few words the chip has room for. */
object RulingText {

    fun headline(ruling: Ruling): String = when (ruling) {
        is Ruling.Take -> "可以接单"
        is Ruling.Leave -> "不要接单"
        Ruling.NoRules -> "没设规则"
        Ruling.Unknown -> "认不出地点"
    }

    fun reason(ruling: Ruling): String = when (ruling) {
        is Ruling.Take -> ruling.suburb + " 在名单里"
        is Ruling.Leave -> when (val why = ruling.reason) {
            is Ruling.Reason.SuburbNotAllowed -> why.suburb + " 不在名单里"
            is Ruling.Reason.StoreDenied -> why.store + " 在黑名单里"
        }
        Ruling.NoRules -> "在电脑上设好规则再推过来"
        Ruling.Unknown -> "送达地址里没有认得出的郊区"
    }
}
