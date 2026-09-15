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

    /**
     * Opens Maps searching for an address, with its pin on the building and its
     * street view photos underneath. The address alone is enough - Maps resolves
     * it, which is why no table of house numbers is shipped here.
     *
     * The search form of the Maps URL, not the /maps/place/ one: the app took a
     * place URL as text for its search box and left it there unsearched.
     */
    fun showPlace(context: Context, place: String) {
        start(
            context,
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://www.google.com/maps/search/?api=1&query=" + Uri.encode(qualify(place))),
            ).setPackage(MAPS),
        )
    }

    /** Opens the map at a place, without starting a route to it. */
    fun showOnMap(context: Context, place: String) {
        start(
            context,
            Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(qualify(place)))),
        )
    }

    /** Drops the viewer into Street View. Needs a point; an address will not do. */
    fun streetView(context: Context, latitude: Double, longitude: Double) {
        start(
            context,
            Intent(Intent.ACTION_VIEW, Uri.parse("google.streetview:cbll=$latitude,$longitude"))
                .setPackage(MAPS),
            Intent(Intent.ACTION_VIEW, Uri.parse("geo:$latitude,$longitude?z=19")),
        )
    }

    private fun start(context: Context, vararg tries: Intent) {
        for (intent in tries) {
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (missing: ActivityNotFoundException) {
                Log.i("UEatsMonitor", "nav: nothing handles " + intent.data)
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
