package com.weixu.ueatsmonitor.action

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

/**
 * Action. Hands a stop to Google Maps as a driving destination.
 *
 * The addresses come off the offer card, which never names the state - "Douglas
 * Street & Shepreth Avenue, Noble Park" - and there is a Noble Park in more than
 * one country. Victoria is added when it is not already said.
 */
object Navigation {

    private const val MAPS = "com.google.android.apps.maps"
    private const val REGION = "Victoria, Australia"

    fun driveTo(context: Context, place: String) {
        val where = Uri.encode(qualify(place))
        val navigate = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$where&mode=d"))
            .setPackage(MAPS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        // Without Google Maps, geo: still reaches whatever map app is installed.
        val anyMap = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$where"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        for (intent in listOf(navigate, anyMap)) {
            try {
                context.startActivity(intent)
                return
            } catch (missing: ActivityNotFoundException) {
                Log.i("UEatsMonitor", "nav: nothing handles " + intent.`package`)
            }
        }
    }

    /** Calculation. The place as a map should be asked for it. */
    fun qualify(place: String): String {
        val clean = place.replace('(', ' ').replace(')', ' ').trim().replace(Regex("""\s+"""), " ")
        val said = clean.contains("victoria", ignoreCase = true) ||
            clean.contains(Regex("""\bvic\b""", RegexOption.IGNORE_CASE))
        return if (said) clean else "$clean, $REGION"
    }
}
