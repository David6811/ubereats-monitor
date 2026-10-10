package com.weixu.ueatsmonitor.action

import android.content.Context
import android.location.Geocoder
import com.weixu.ueatsmonitor.domain.GeoPoint
import com.weixu.ueatsmonitor.domain.Place
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Action. Where the places offered while typing a centre come from.
 *
 * Two sources, and the order matters. The bundled tables - every suburb and
 * every shop in the service area - answer instantly and work in a dead spot,
 * which is where a driver is when he wants to move his centre. The phone's own
 * geocoder answers street addresses, needs the network, and takes most of a
 * second, so it only runs once typing stops and its answers arrive underneath.
 */
object CentreSearch {

    @Volatile
    private var known: List<Place>? = null

    /** Suburbs and shops, off disk, once. */
    fun known(context: Context): List<Place> = known ?: synchronized(this) {
        known ?: build(context).also { known = it }
    }

    /**
     * Street addresses, from the phone. Bounded to Greater Melbourne: "Wells
     * Rd" is a street in a dozen countries, and a centre dropped in the wrong
     * one is a drive the driver never notices he is not making.
     */
    suspend fun addresses(context: Context, query: String): List<Place> = withContext(Dispatchers.IO) {
        if (query.trim().length < 3) return@withContext emptyList()
        runCatching {
            @Suppress("DEPRECATION")
            Geocoder(context, Locale.ENGLISH)
                .getFromLocationName(query, 5, SOUTH, WEST, NORTH, EAST)
                .orEmpty()
                .map { found ->
                    Place(
                        name = listOfNotNull(
                            listOfNotNull(found.subThoroughfare, found.thoroughfare).joinToString(" ").ifBlank { null },
                            found.locality,
                        ).joinToString(", ").ifBlank { found.getAddressLine(0) ?: "" },
                        at = GeoPoint(found.latitude, found.longitude),
                        kind = Place.Kind.ADDRESS,
                    )
                }
                .filter { it.name.isNotBlank() }
        }.getOrDefault(emptyList())
    }

    private fun build(context: Context): List<Place> =
        Gazetteer.suburbs(context).map { Place(it.name, it.at, Place.Kind.SUBURB) } +
            StoreTable.all(context).map { Place(it.name, it.at, Place.Kind.SHOP) }

    // The corners of the bundled tables, which is the area this app is for.
    private const val SOUTH = -38.56
    private const val NORTH = -37.39
    private const val WEST = 144.29
    private const val EAST = 145.96
}
