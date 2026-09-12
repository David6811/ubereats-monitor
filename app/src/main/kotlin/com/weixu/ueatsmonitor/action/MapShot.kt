package com.weixu.ueatsmonitor.action

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.util.Log
import java.io.File

/**
 * Action. Two pictures of where a delivery is going, taken from Google Maps.
 *
 * The card cannot say what the door looks like and no table we could ship would
 * either. Maps can, and it is already on the phone - so the app goes there,
 * takes the two pictures worth having, and comes back. The driver never leaves
 * the card for more than the trip itself.
 *
 * Two, because either alone misleads. The aerial says which roof and which
 * driveway; the street view says what he will actually see from the kerb, and
 * is sometimes years out of date. Together they settle it.
 */
object MapShot {

    private const val TAG = "UEatsMonitor"
    private const val DIR = "mapshots"

/** Data. Which end of the job a picture belongs to. */
    enum class Stop { PICKUP, DROPOFF }

    /** Where the two pictures for one end of one job live. */
    fun aerialFile(service: AccessibilityService, atMillis: Long, stop: Stop): File =
        file(service, atMillis, stop, "aerial")

    fun streetFile(service: AccessibilityService, atMillis: Long, stop: Stop): File =
        file(service, atMillis, stop, "street")

    private fun file(
        service: AccessibilityService,
        atMillis: Long,
        stop: Stop,
        which: String,
    ): File = File(
        File(service.filesDir, DIR).apply { mkdirs() },
        atMillis.toString() + "-" + stop.name.lowercase() + "-" + which + ".jpg",
    )

    /**
     * The pictures already taken for one end of a job, wide one first.
     *
     * The second picture is the one taken after the place was opened, which is
     * the wider view - the street he arrives on, or the whole shopping centre
     * with its shops named. That is what to look at first: it says where, and
     * the close one then says which.
     */
    fun taken(context: android.content.Context, atMillis: Long, stop: Stop): List<File> {
        val dir = File(context.filesDir, DIR)
        val head = atMillis.toString() + "-" + stop.name.lowercase() + "-"
        return listOf(head + "street.jpg", head + "aerial.jpg")
            .map { File(dir, it) }
            .filter { it.exists() && it.length() > 0 }
    }

    /** Writes a screenshot beside the job it belongs to, or nothing at all. */
    fun save(shot: android.graphics.Bitmap?, into: File) {
        if (shot == null) {
            Log.w(TAG, "mapshot: no screenshot to save into " + into.name)
            return
        }
        runCatching {
            into.outputStream().use { out ->
                shot.compress(android.graphics.Bitmap.CompressFormat.JPEG, 88, out)
            }
        }.onFailure { Log.w(TAG, "mapshot: could not write " + into.name, it) }
        runCatching { shot.recycle() }
    }

    /** One tap, sent the way a gesture is, since nothing else can reach Maps. */
    fun tap(service: AccessibilityService, x: Float, y: Float, onDone: () -> Unit) {
        val path = Path().apply { moveTo(x, y); lineTo(x + 1f, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 60))
            .build()
        val sent = service.dispatchGesture(
            gesture,
            object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(description: GestureDescription?) = onDone()
                override fun onCancelled(description: GestureDescription?) = onDone()
            },
            null,
        )
        if (!sent) onDone()
    }

    /**
     * The street view thumbnail, which sits in the bottom left corner of the map
     * once the place card has been pushed down - and pushed down is where the
     * card ends up after the map has been handled.
     */
    fun streetViewSpot(service: AccessibilityService): Pair<Float, Float> {
        val metrics = service.resources.displayMetrics
        return Pair(metrics.widthPixels * 0.14f, metrics.heightPixels * 0.855f)
    }

    /**
     * Spreads two fingers from the middle of the map, which is how a map is
     * zoomed and the only way: the place URL's own zoom is ignored, and one
     * finger dragged either way zooms out.
     */
    /**
     * Spread once and the map moves one step; the driver wants several. Each
     * spread multiplies what the last one did, so they are done in a row with a
     * moment between for the map to settle.
     */
    fun pinchOut(service: AccessibilityService, times: Int, onDone: () -> Unit) {
        if (times <= 0) {
            onDone()
            return
        }
        // The last spread is a short one. A full one from here puts a single roof
        // across the screen, and the neighbours' numbers are half of how a house
        // is found from the kerb.
        pinchOnce(service, if (times == 1) 0.20f else 0.40f) {
            service.main?.postDelayed({ pinchOut(service, times - 1, onDone) }, 600)
                ?: pinchOut(service, times - 1, onDone)
        }
    }

    private val AccessibilityService.main: android.os.Handler?
        get() = android.os.Handler(android.os.Looper.getMainLooper())

    private fun pinchOnce(service: AccessibilityService, reach: Float, onDone: () -> Unit) {
        val metrics = service.resources.displayMetrics
        val midX = metrics.widthPixels / 2f
        // The upper half is map; the lower half is the place card.
        val midY = metrics.heightPixels * 0.32f
        // Starting close together and ending far apart is what a zoom in looks
        // like to a map. Slowly: a fast spread reads as a fling.
        val from = metrics.widthPixels * 0.04f
        val to = metrics.widthPixels * reach

        val left = Path().apply {
            moveTo(midX - from, midY)
            lineTo(midX - to, midY)
        }
        val right = Path().apply {
            moveTo(midX + from, midY)
            lineTo(midX + to, midY)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(left, 0, 900))
            .addStroke(GestureDescription.StrokeDescription(right, 0, 900))
            .build()

        val sent = service.dispatchGesture(
            gesture,
            object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(description: GestureDescription?) = onDone()
                override fun onCancelled(description: GestureDescription?) {
                    Log.w(TAG, "mapshot: pinch cancelled")
                    onDone()
                }
            },
            null,
        )
        if (!sent) {
            Log.w(TAG, "mapshot: pinch refused")
            onDone()
        }
    }
}
