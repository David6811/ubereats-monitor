package com.weixu.ueatsmonitor.domain

/**
 * Data. The driver's rules, exactly as set on the laptop. Nothing here is
 * decided by the app: no payout floor, no dollars per mile, no distance cap.
 * Those were invented once and are gone.
 */
data class Rules(
    val allowedSuburbs: Set<String>,
    /**
     * Where a big payout will take him. An offer over [farOverCents] is judged
     * against these suburbs instead of [allowedSuburbs]: the money is worth the
     * drive back. Empty means the rule is off.
     */
    val farSuburbs: Set<String>,
    val farOverCents: Cents,
    val deniedStores: List<String>,
    /**
     * Chains that always have their own car park. A pickup matching one of these
     * is never refused, whatever else says so - a McDonald's inside a shopping
     * strip is still a McDonald's with a car park.
     */
    val alwaysOkStores: List<String>,
    /** Rectangles drawn on the laptop: no pickup and no dropoff inside any of them. */
    val noGoBoxes: List<NoGoBox>,
    /** The driver's petrol and return-time reckoning, set on the phone. */
    val tripCost: TripCost,
    /**
     * An offer let through by the far set must also clear this, in dollars an
     * hour after petrol: the far set is there for money, and a long drive can
     * pay less than it looks.
     */
    val farMinPerHour: Double,
)

/** Data. Sum type: why an offer is or is not worth taking. */
sealed interface Ruling {

    /** [far] when the payout was what let it through, not the ordinary set. */
    data class Take(val suburb: String, val far: Boolean = false) : Ruling

    data class Leave(val reason: Reason) : Ruling

    /** Data. Nothing was set, so nothing can be said. */
    data object NoRules : Ruling

    /** Data. The card gave no destination suburb we recognise. */
    data object Unknown : Ruling

    sealed interface Reason {
        data class SuburbNotAllowed(val suburb: String) : Reason
        data class StoreDenied(val store: String) : Reason
        data class InNoGoBox(val hit: NoGoHit) : Reason
        data class FarTooCheap(val perHour: Double, val floor: Double) : Reason
    }
}

/**
 * Calculation. Card plus rules in, [Ruling] out.
 *
 * Three rules, in order: the pickup must not be on the deny list, neither stop
 * may be inside a no-go box, and the destination's suburb must be on the allow
 * list. The first one that says no is the one reported, because that is the
 * one the driver needs to hear.
 */
object RuleJudge {

    fun judge(card: OfferCard, rules: Rules, gazetteer: List<Suburb>, stops: Stops): Ruling {
        if (rules.allowedSuburbs.isEmpty() && rules.deniedStores.isEmpty() && rules.noGoBoxes.isEmpty()) {
            return Ruling.NoRules
        }

        val alwaysOk = rules.alwaysOkStores.any { name ->
            name.isNotBlank() && card.pickup.contains(name, ignoreCase = true)
        }
        val denied = if (alwaysOk) null else rules.deniedStores.firstOrNull { name ->
            name.isNotBlank() && card.pickup.contains(name, ignoreCase = true)
        }
        if (denied != null) return Ruling.Leave(Ruling.Reason.StoreDenied(denied))

        // Before the suburb list, and whatever the payout: a box is a place he
        // will not go, not a place that is merely out of the way.
        NoGo.hit(rules.noGoBoxes, stops)?.let { return Ruling.Leave(Ruling.Reason.InNoGoBox(it)) }

        // Over the threshold the far set applies instead. Not as well as: the
        // whole point is that a big payout reaches somewhere the ordinary set
        // will not.
        val far = rules.farSuburbs.isNotEmpty() &&
            card.payout.amount > rules.farOverCents.amount
        val allowed = if (far) rules.farSuburbs else rules.allowedSuburbs
        if (allowed.isEmpty()) return Ruling.Unknown

        val found = SuburbIndex.findAll(card.dropoff, gazetteer)
        if (found.isEmpty()) return Ruling.Unknown

        val outside = found.firstOrNull { suburb ->
            allowed.none { it.equals(suburb.name, ignoreCase = true) }
        }
        if (outside != null) return Ruling.Leave(Ruling.Reason.SuburbNotAllowed(outside.name))

        // Only on the far set. An unreadable distance or time says nothing about
        // the hour, so it does not refuse.
        if (far) {
            val perHour = TripEarnings.perHour(card, rules.tripCost)
            if (perHour != null && perHour < rules.farMinPerHour) {
                return Ruling.Leave(Ruling.Reason.FarTooCheap(perHour, rules.farMinPerHour))
            }
        }
        return Ruling.Take(found.first().name, far = far)
    }
}

/** Calculation. The ruling in the few words the chip has room for. */
object RulingText {

    fun headline(ruling: Ruling, isMatch: Boolean = false): String = when (ruling) {
        // A Match is worth entering, not "taken": several drivers are shown the
        // same trip and only one gets it.
        is Ruling.Take -> if (isMatch) "可以抢（Match）" else "可以接单"
        is Ruling.Leave -> if (isMatch) "不要抢（Match）" else "不要接单"
        Ruling.NoRules -> "没设规则"
        Ruling.Unknown -> "认不出地点"
    }

    fun reason(ruling: Ruling): String = when (ruling) {
        is Ruling.Take -> ruling.suburb + if (ruling.far) " 在远区名单里" else " 在名单里"
        is Ruling.Leave -> when (val why = ruling.reason) {
            is Ruling.Reason.SuburbNotAllowed -> why.suburb + " 不在名单里"
            is Ruling.Reason.StoreDenied -> why.store + " 在黑名单里"
            is Ruling.Reason.InNoGoBox -> when (val hit = why.hit) {
                is NoGoHit.Pickup -> "取餐 " + hit.store + " 在「" + hit.box.label + "」里"
                is NoGoHit.Dropoff -> "送餐点在「" + hit.box.label + "」里"
            }
            is Ruling.Reason.FarTooCheap ->
                "远区单每小时 $" + String.format("%.2f", why.perHour) +
                    "，低于 $" + String.format("%.0f", why.floor)
        }
        Ruling.NoRules -> "在电脑上设好规则再推过来"
        Ruling.Unknown -> "送达地址里没有认得出的郊区"
    }
}
