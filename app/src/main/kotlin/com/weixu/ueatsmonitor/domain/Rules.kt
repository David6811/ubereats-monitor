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
    /**
     * The homeward rule, when the driver has asked to be taken back to the
     * middle of the set: an offer is then refused if it would take too long, or
     * if its drop leaves him further out and is not near the middle anyway.
     * Null when the switch is off or no centre was drawn.
     */
    val homeward: HomewardLimits?,
    /**
     * The near-centre rule, when the driver has asked to stay around the middle
     * of the set: an offer is refused if its drop lands further out than this
     * or the job takes too long. Null when the switch is off or no centre was drawn.
     */
    val nearCentre: NearCentreLimits?,
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
        data class LeadingAway(val away: Homeward.Further) : Reason
        data class TooLong(val minutes: Int, val max: Int) : Reason
        data class TooFarFromCentre(val fromDrop: Miles, val maxKm: Double) : Reason
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

        // On the way back in, whichever set let it through.
        rules.homeward?.let { limits -> homewardReason(card, stops, limits) }
            ?.let { return Ruling.Leave(it) }

        // Staying around the middle, whichever set let it through.
        rules.nearCentre?.let { limits -> nearCentreReason(card, stops, limits) }
            ?.let { return Ruling.Leave(it) }

        return Ruling.Take(found.first().name, far = far)
    }

    /**
     * Too long first: however near it lands, a job that eats the time left is no
     * good. Then leading away - unless the drop is near the middle anyway, where
     * a short job is worth more than the few kilometres it costs. An unreadable
     * time, or an unknown position, says nothing and refuses nothing.
     */
    private fun homewardReason(card: OfferCard, stops: Stops, limits: HomewardLimits): Ruling.Reason? {
        val minutes = card.duration?.value
        if (minutes != null && minutes > limits.maxMinutes) return Ruling.Reason.TooLong(minutes, limits.maxMinutes)
        val homeward = HomewardRule.judge(stops.carAt, stops.dropoff?.at, limits.centre)
        if (homeward is Homeward.Further && homeward.fromDrop.value * KM_PER_MILE >= limits.nearKm) {
            return Ruling.Reason.LeadingAway(homeward)
        }
        return null
    }

    /**
     * Too long first, then too far out. An unreadable time, or a drop the tables
     * could not place, says nothing and refuses nothing.
     */
    private fun nearCentreReason(card: OfferCard, stops: Stops, limits: NearCentreLimits): Ruling.Reason? {
        val minutes = card.duration?.value
        if (minutes != null && minutes > limits.maxMinutes) return Ruling.Reason.TooLong(minutes, limits.maxMinutes)
        val dropAt = stops.dropoff?.at ?: return null
        val fromDrop = Geo.straightLine(dropAt, limits.centre)
        if (fromDrop.value * KM_PER_MILE > limits.maxKm) return Ruling.Reason.TooFarFromCentre(fromDrop, limits.maxKm)
        return null
    }

    private const val KM_PER_MILE = 1.609344
}

/** Calculation. The ruling in the few words the chip has room for, in the driver's language. */
object RulingText {

    private fun km(miles: Miles, words: Words): String =
        words.km(String.format("%.1f", miles.value / 0.621371))

    fun headline(ruling: Ruling, isMatch: Boolean, lang: Lang): String {
        val words = wordsIn(lang)
        return when (ruling) {
            // A Match is worth entering, not "taken": several drivers are shown
            // the same trip and only one gets it.
            is Ruling.Take -> if (isMatch) words.enterIt else words.takeIt
            is Ruling.Leave -> if (isMatch) words.leaveMatch else words.leaveIt
            Ruling.NoRules -> words.noRules
            Ruling.Unknown -> words.unknownPlace
        }
    }

    /**
     * Whether a headline off the board is a yes. The board keeps the words that
     * were shown at the time, which may be either language, so both are asked.
     */
    fun saysTake(headline: String): Boolean =
        headline == Zh.takeIt || headline == Zh.enterIt ||
            headline == En.takeIt || headline == En.enterIt

    fun reason(ruling: Ruling, lang: Lang): String {
        val words = wordsIn(lang)
        return when (ruling) {
            is Ruling.Take -> if (ruling.far) words.onTheFarList(ruling.suburb) else words.onTheList(ruling.suburb)
            is Ruling.Leave -> when (val why = ruling.reason) {
                is Ruling.Reason.SuburbNotAllowed -> words.notOnTheList(why.suburb)
                is Ruling.Reason.StoreDenied -> words.storeDenied(why.store)
                is Ruling.Reason.InNoGoBox -> when (val hit = why.hit) {
                    is NoGoHit.Pickup -> words.pickupInBox(hit.store, hit.box.label)
                    is NoGoHit.Dropoff -> words.dropInBox(hit.box.label)
                }
                is Ruling.Reason.LeadingAway ->
                    words.leadingAway(km(why.away.fromCar, words), km(why.away.fromDrop, words))
                is Ruling.Reason.TooLong -> words.tooLong(why.minutes, why.max)
                is Ruling.Reason.TooFarFromCentre ->
                    words.tooFarFromCentre(km(why.fromDrop, words), String.format("%.0f", why.maxKm))
                is Ruling.Reason.FarTooCheap ->
                    words.farTooCheap(String.format("%.2f", why.perHour), String.format("%.0f", why.floor))
            }
            Ruling.NoRules -> words.setRulesOnTheLaptop
            Ruling.Unknown -> words.noSuburbInAddress
        }
    }
}

