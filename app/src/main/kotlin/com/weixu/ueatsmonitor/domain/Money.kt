package com.weixu.ueatsmonitor.domain

import kotlinx.serialization.Serializable

/** Data. Payout in whole cents so no float error creeps into money. */
@Serializable
@JvmInline
value class Cents(val amount: Int) {
    val dollars: Double get() = amount / 100.0

    override fun toString(): String = "$" + String.format("%.2f", dollars)

    companion object {
        fun ofDollars(dollars: Double): Cents = Cents(Math.round(dollars * 100).toInt())
    }
}

/** Data. Distance always normalized to miles; the parser converts km on the way in. */
@Serializable
@JvmInline
value class Miles(val value: Double)

/** Data. Estimated trip duration. */
@Serializable
@JvmInline
value class Minutes(val value: Int)

fun Double.km2mi(): Miles = Miles(this * 0.621371)
