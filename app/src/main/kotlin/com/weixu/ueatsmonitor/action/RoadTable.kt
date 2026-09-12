package com.weixu.ueatsmonitor.action

import android.content.Context
import android.util.Log
import com.weixu.ueatsmonitor.domain.Crossing
import com.weixu.ueatsmonitor.domain.GeoPoint
import com.weixu.ueatsmonitor.domain.Road

/**
 * Action. Reads the bundled road tables off disk once, then never again.
 *
 * Fifteen thousand junctions and ten thousand roads, about 1.2 MB of text. Read
 * on the screen-reading thread the first time a card is judged, not at startup:
 * a shift where no card ever appears should not pay for it.
 */
object RoadTable {

    private const val CROSSINGS = "service-area-crossings.csv"
    private const val ROADS = "service-area-roads.csv"

    @Volatile
    private var loadedCrossings: List<Crossing>? = null

    @Volatile
    private var loadedRoads: List<Road>? = null

    /**
     * Reads both tables now, so the first card of a shift does not wait for them.
     * Safe to call more than once: each is read once and held.
     */
    fun warm(context: Context) {
        crossings(context)
        roads(context)
    }

    fun crossings(context: Context): List<Crossing> = loadedCrossings ?: synchronized(this) {
        loadedCrossings ?: readCrossings(context).also { loadedCrossings = it }
    }

    fun roads(context: Context): List<Road> = loadedRoads ?: synchronized(this) {
        loadedRoads ?: readRoads(context).also { loadedRoads = it }
    }

    /** Lines of "RoadA|RoadB|lat,lon". */
    private fun readCrossings(context: Context): List<Crossing> = timed(CROSSINGS) {
        context.assets.open(CROSSINGS).bufferedReader().useLines { lines ->
            lines.mapNotNull { line ->
                val parts = line.split("|")
                if (parts.size != 3) return@mapNotNull null
                pointOf(parts[2])?.let { Crossing(parts[0], parts[1], it) }
            }.toList()
        }
    }

    /** Lines of "Road|lat,lon lat,lon ...". */
    private fun readRoads(context: Context): List<Road> = timed(ROADS) {
        context.assets.open(ROADS).bufferedReader().useLines { lines ->
            lines.mapNotNull { line ->
                val at = line.indexOf('|')
                if (at <= 0) return@mapNotNull null
                val points = line.substring(at + 1).split(" ").mapNotNull(::pointOf)
                if (points.isEmpty()) null else Road(line.substring(0, at), points)
            }.toList()
        }
    }

    private fun pointOf(text: String): GeoPoint? {
        val comma = text.indexOf(',')
        if (comma <= 0) return null
        val lat = text.substring(0, comma).toDoubleOrNull() ?: return null
        val lon = text.substring(comma + 1).toDoubleOrNull() ?: return null
        return GeoPoint(lat, lon)
    }

    /** The one number that says whether reading these on the fly was a mistake. */
    private fun <T> timed(what: String, read: () -> List<T>): List<T> {
        val began = System.currentTimeMillis()
        val rows = runCatching(read).getOrDefault(emptyList())
        Log.i("UEatsMonitor", "roads: $what ${rows.size} rows in ${System.currentTimeMillis() - began} ms")
        return rows
    }
}
