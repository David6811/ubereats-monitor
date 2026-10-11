package com.weixu.ueatsmonitor.action

import android.content.Context
import android.location.Geocoder
import com.weixu.ueatsmonitor.domain.GeoPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Action. A name for a centre marked on the phone, the way the laptop names
 * one it found by address: "Wells Rd, Aspendale Gardens".
 *
 * The phone's own geocoder, which needs no key. It needs the network and can
 * find nothing, so the coordinates stand in rather than the centre going
 * unsaved - the name is for reading, the coordinates are what the rules use.
 */
object CentreLabel {

    suspend fun of(context: Context, at: GeoPoint): String = withContext(Dispatchers.IO) {
        val found = runCatching {
            @Suppress("DEPRECATION")
            Geocoder(context, Locale.ENGLISH).getFromLocation(at.latitude, at.longitude, 1)?.firstOrNull()
        }.getOrNull()
        listOfNotNull(found?.thoroughfare, found?.locality)
            .joinToString(", ")
            .ifEmpty { String.format(Locale.ROOT, "%.5f, %.5f", at.latitude, at.longitude) }
    }
}
