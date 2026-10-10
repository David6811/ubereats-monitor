package com.weixu.ueatsmonitor.ui

import android.annotation.SuppressLint
import android.view.MotionEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.weixu.ueatsmonitor.domain.GeoPoint
import com.weixu.ueatsmonitor.domain.MapProjection
import com.weixu.ueatsmonitor.domain.BoxCorners
import com.weixu.ueatsmonitor.domain.NoGoBox
import com.weixu.ueatsmonitor.domain.NoGoGeoJson
import com.weixu.ueatsmonitor.domain.SuburbGeoJson
import com.weixu.ueatsmonitor.domain.SuburbShape
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.Canvas
import android.graphics.PointF

/**
 * The live set on a real map: streets under it, the chosen suburbs in gold,
 * the rest as faint outlines so the set reads against its neighbours.
 *
 * MapLibre draws it natively, from OpenFreeMap's vector tiles - sharp at any
 * zoom, and neither needs an API key. The outlines are the bundled ones, so the
 * set itself still shows with no network; only the streets under it need one.
 */
@SuppressLint("ClickableViewAccessibility")
@Composable
fun AreaMap(
    /** Which set this is. The camera reframes when it changes, and only then. */
    setName: String,
    chosen: Set<String>,
    shapes: List<SuburbShape>,
    modifier: Modifier = Modifier,
    /**
     * In the set but not being worked right now. Drawn dim rather than left to
     * look like the suburbs that were never in it: "this trip" turns them off
     * for an hour, and the driver has to see which ones he turned off.
     */
    dropped: Set<String> = emptySet(),
    /** The boxes drawn on the laptop. Empty draws none. */
    boxes: List<NoGoBox> = emptyList(),
    /** Which of them are switched off for this trip, by label. */
    boxesOff: Set<String> = emptySet(),
    /** The set's centre, drawn as a gold ring. Null draws none. */
    centre: GeoPoint? = null,
    /** The suburb under a tap, by name. Null leaves the map read only. */
    onTap: ((String) -> Unit)? = null,
    /** Where a long press landed. Null ignores long presses. */
    onLongPress: ((GeoPoint) -> Unit)? = null,
    /** The suburb under a long press. Takes precedence over [onLongPress]. */
    onLongPressSuburb: ((String) -> Unit)? = null,
    /**
     * Press, drag, release draws a rectangle instead of moving the map. The
     * map's own gestures are off while this is true, the way the web page
     * turns Leaflet's dragging off: one finger cannot both pan and draw.
     */
    drawingBox: Boolean = false,
    /** The two corners of the rectangle just drawn, in no particular order. */
    onBoxDrawn: ((GeoPoint, GeoPoint) -> Unit)? = null,
    /**
     * What to fit the camera to on the way in, when the chosen suburbs are not
     * the answer. Empty falls back to them.
     */
    frameTo: List<GeoPoint> = emptyList(),
    /**
     * A box whose corners can be dragged. The map holds still while one is:
     * a corner is placed in screen points and read back off the projection,
     * which only works while the projection is not moving.
     */
    adjusting: NoGoBox? = null,
    onAdjusted: ((NoGoBox) -> Unit)? = null,
    /**
     * Somewhere to show. Each new value moves the camera once; a long press
     * passes nothing, because moving the map out from under the finger that
     * just pressed it loses the driver his place.
     */
    lookAt: GeoPoint? = null,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val holder = remember { MapHolder() }
    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context).apply {
            onCreate(null)
            // Inside a scrolling page: a drag on the map moves the map, not the page.
            setOnTouchListener { view, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) view.parent?.requestDisallowInterceptTouchEvent(true)
                false
            }
            getMapAsync { map ->
                map.uiSettings.isRotateGesturesEnabled = false
                map.uiSettings.isTiltGesturesEnabled = false
                map.addOnMapClickListener { at ->
                    val tap = holder.onTap ?: return@addOnMapClickListener false
                    val point = map.projection.toScreenLocation(at)
                    val name = map.queryRenderedFeatures(point, HIT).firstOrNull()
                        ?.getStringProperty(SuburbGeoJson.NAME)
                        ?: return@addOnMapClickListener false
                    tap(name)
                    true
                }
                map.addOnMapLongClickListener { at ->
                    holder.onLongPressSuburb?.let { press ->
                        val point = map.projection.toScreenLocation(at)
                        val name = map.queryRenderedFeatures(point, HIT).firstOrNull()
                            ?.getStringProperty(SuburbGeoJson.NAME)
                            ?: return@addOnMapLongClickListener false
                        press(name)
                        return@addOnMapLongClickListener true
                    }
                    val press = holder.onLongPress ?: return@addOnMapLongClickListener false
                    press(GeoPoint(at.latitude, at.longitude))
                    true
                }
                map.setStyle(Style.Builder().fromUri(STYLE)) { style ->
                    // Handed over once: the outlines are the same whichever set
                    // is being worked, and re-sending them is what made a tap lag.
                    style.addSource(GeoJsonSource(SOURCE, SuburbGeoJson.featureCollection(shapes)))
                    // Every suburb, all but invisible: a tap inside an unchosen
                    // suburb has to land on something, and its outline alone is
                    // a hairline.
                    style.addLayer(FillLayer(HIT, SOURCE).withProperties(PropertyFactory.fillOpacity(0.01f)))
                    // Between the two: part of the set, off for now.
                    style.addLayer(
                        FillLayer(DROPPED_FILL, SOURCE)
                            .withProperties(PropertyFactory.fillColor(LINE), PropertyFactory.fillOpacity(0.35f))
                            .withFilter(among(holder.dropped)),
                    )
                    style.addLayer(
                        LineLayer(DROPPED_EDGE, SOURCE)
                            .withProperties(PropertyFactory.lineColor(LINE), PropertyFactory.lineWidth(1.2f))
                            .withFilter(among(holder.dropped)),
                    )
                    style.addLayer(
                        LineLayer(OTHERS, SOURCE)
                            .withProperties(PropertyFactory.lineColor(LINE), PropertyFactory.lineWidth(0.6f))
                            .withFilter(neither(holder.chosen, holder.dropped)),
                    )
                    style.addLayer(
                        FillLayer(FILL, SOURCE)
                            .withProperties(PropertyFactory.fillColor(GOLD), PropertyFactory.fillOpacity(0.28f))
                            .withFilter(among(holder.chosen)),
                    )
                    style.addLayer(
                        LineLayer(EDGE, SOURCE)
                            .withProperties(PropertyFactory.lineColor(GOLD), PropertyFactory.lineWidth(1.6f))
                            .withFilter(among(holder.chosen)),
                    )
                    // Over the suburbs, under the centre: a box is a hole in
                    // the set, so it has to read on top of what it cuts out of.
                    style.addSource(GeoJsonSource(BOXES, NoGoGeoJson.featureCollection(holder.boxes, holder.boxesOff)))
                    style.addLayer(
                        FillLayer(BOX_FILL, BOXES).withProperties(
                            PropertyFactory.fillColor(
                                Expression.switchCase(
                                    Expression.get(NoGoGeoJson.REFUSING), Expression.color(android.graphics.Color.parseColor(ORANGE)),
                                    Expression.color(android.graphics.Color.parseColor(LINE)),
                                ),
                            ),
                            PropertyFactory.fillOpacity(0.18f),
                        ),
                    )
                    style.addLayer(
                        LineLayer(BOX_EDGE, BOXES).withProperties(
                            PropertyFactory.lineColor(
                                Expression.switchCase(
                                    Expression.get(NoGoGeoJson.REFUSING), Expression.color(android.graphics.Color.parseColor(ORANGE)),
                                    Expression.color(android.graphics.Color.parseColor(LINE)),
                                ),
                            ),
                            PropertyFactory.lineWidth(1.6f),
                            PropertyFactory.lineDasharray(arrayOf(3f, 2f)),
                        ),
                    )
                    style.addSource(GeoJsonSource(CENTRE, centreJson(holder.centre)))
                    style.addLayer(
                        CircleLayer(CENTRE_RING, CENTRE).withProperties(
                            PropertyFactory.circleRadius(8f),
                            PropertyFactory.circleColor(GROUND),
                            PropertyFactory.circleStrokeColor(GOLD),
                            PropertyFactory.circleStrokeWidth(3f),
                        ),
                    )
                    holder.map = map
                    holder.style = style
                    if (holder.frameTo.isNotEmpty()) frameOn(map, holder.frameTo)
                    else frame(map, shapes, holder.chosen + holder.dropped)
                }
            }
        }
    }

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) mapView.onStart()
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onResume()
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }

    var from by remember { mutableStateOf<Offset?>(null) }
    var to by remember { mutableStateOf<Offset?>(null) }
    Box(modifier) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { mapView },
        update = {
            holder.onTap = onTap
            holder.onLongPress = onLongPress
            holder.onLongPressSuburb = onLongPressSuburb
            holder.map?.uiSettings?.setAllGesturesEnabled(!drawingBox && adjusting == null)
            holder.frameTo = frameTo
            if (holder.centre != centre) {
                holder.centre = centre
                holder.style?.getSourceAs<GeoJsonSource>(CENTRE)?.setGeoJson(centreJson(centre))
            }
            if (holder.boxes != boxes || holder.boxesOff != boxesOff) {
                holder.boxes = boxes
                holder.boxesOff = boxesOff
                holder.style?.getSourceAs<GeoJsonSource>(BOXES)?.setGeoJson(NoGoGeoJson.featureCollection(boxes, boxesOff))
            }
            if (holder.chosen != chosen || holder.dropped != dropped) {
                holder.chosen = chosen
                holder.dropped = dropped
                holder.style?.let { style ->
                    style.getLayerAs<FillLayer>(FILL)?.setFilter(among(chosen))
                    style.getLayerAs<LineLayer>(EDGE)?.setFilter(among(chosen))
                    style.getLayerAs<FillLayer>(DROPPED_FILL)?.setFilter(among(dropped))
                    style.getLayerAs<LineLayer>(DROPPED_EDGE)?.setFilter(among(dropped))
                    style.getLayerAs<LineLayer>(OTHERS)?.setFilter(neither(chosen, dropped))
                }
            }
            if (lookAt != null && holder.lookAt != lookAt) {
                holder.lookAt = lookAt
                holder.map?.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(LatLng(lookAt.latitude, lookAt.longitude), LOOK_ZOOM),
                )
            }
            // A suburb tapped in or out is the same set: jumping the camera
            // under the finger that just tapped would lose the driver's place.
            if (holder.setName != setName) {
                holder.setName = setName
                holder.map?.let { frame(it, shapes, chosen + dropped) }
            }
        },
    )
        if (adjusting != null && onAdjusted != null) {
            // Four handles, one per corner, and a drag that starts near one
            // moves it. Anywhere else does nothing: a stray finger on a map
            // that is holding still should not resize what is under it.
            var held by remember { mutableStateOf(-1) }
            // Keyed on nothing, and the box read fresh on every event: keyed on
            // the box, the detector restarted the moment a corner moved and the
            // finger lost its grip after a few pixels.
            val live = androidx.compose.runtime.rememberUpdatedState(adjusting)
            val report = androidx.compose.runtime.rememberUpdatedState(onAdjusted)
            Canvas(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { at ->
                                val map = holder.map
                                val box = live.value
                                held = if (map == null || box == null) -1 else cornersOf(box, map)
                                    .withIndex()
                                    .filter { (_, point) -> (point - at).getDistance() <= HANDLE_GRAB }
                                    .minByOrNull { (_, point) -> (point - at).getDistance() }
                                    ?.index ?: -1
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                val map = holder.map ?: return@detectDragGestures
                                val box = live.value ?: return@detectDragGestures
                                if (held < 0) return@detectDragGestures
                                val at = map.projection.fromScreenLocation(PointF(change.position.x, change.position.y))
                                report.value?.invoke(BoxCorners.moved(box, held, at.latitude, at.longitude))
                            },
                            onDragEnd = { held = -1 },
                            onDragCancel = { held = -1 },
                        )
                    },
            ) {
                val map = holder.map ?: return@Canvas
                cornersOf(adjusting, map).forEach { point ->
                    drawCircle(PREVIEW, radius = 22f, center = point)
                    drawCircle(GROUND_INK, radius = 22f, center = point, style = Stroke(width = 5f))
                }
            }
        }

        if (drawingBox) {
            // Over the map, not in it: the rectangle is drawn in screen points
            // and turned into two corners of the earth only on release, which
            // is the one moment the projection has to be right.
            Canvas(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { at -> from = at; to = at },
                            onDrag = { change, _ -> change.consume(); to = change.position },
                            onDragEnd = {
                                val a = from
                                val b = to
                                from = null
                                to = null
                                val map = holder.map
                                if (a != null && b != null && map != null) {
                                    val one = map.projection.fromScreenLocation(PointF(a.x, a.y))
                                    val two = map.projection.fromScreenLocation(PointF(b.x, b.y))
                                    onBoxDrawn?.invoke(
                                        GeoPoint(one.latitude, one.longitude),
                                        GeoPoint(two.latitude, two.longitude),
                                    )
                                }
                            },
                            onDragCancel = { from = null; to = null },
                        )
                    },
            ) {
                val a = from ?: return@Canvas
                val b = to ?: return@Canvas
                val corner = Offset(minOf(a.x, b.x), minOf(a.y, b.y))
                val span = androidx.compose.ui.geometry.Size(kotlin.math.abs(b.x - a.x), kotlin.math.abs(b.y - a.y))
                drawRect(PREVIEW, topLeft = corner, size = span, alpha = 0.18f)
                drawRect(PREVIEW, topLeft = corner, size = span, style = Stroke(width = 4f))
            }
        }
    }
}

/** The rectangle being dragged out, before it is a box. */
private val PREVIEW = Color(0xFFF0883E)
private val GROUND_INK = Color(0xFF0A0A0B)

/** How close a finger has to land to take hold of a corner, in screen points. */
private const val HANDLE_GRAB = 90f

/** Calculation. The box's four corners where they fall on the screen, SW first. */
private fun cornersOf(box: NoGoBox, map: MapLibreMap): List<Offset> =
    listOf(
        box.south to box.west,
        box.south to box.east,
        box.north to box.east,
        box.north to box.west,
    ).map { (lat, lon) ->
        val point = map.projection.toScreenLocation(LatLng(lat, lon))
        Offset(point.x, point.y)
    }

/** Draws only the named suburbs. */
private fun among(names: Set<String>): Expression =
    Expression.`in`(Expression.get(SuburbGeoJson.NAME), Expression.literal(names.toTypedArray()))

/** Draws the ones in neither list: the rest of the city, as hairlines. */
private fun neither(chosen: Set<String>, dropped: Set<String>): Expression =
    Expression.not(among(chosen + dropped))

/** What the map callbacks and the next recomposition share. */
private class MapHolder {
    var chosen: Set<String> = emptySet()
    var setName: String? = null
    var map: MapLibreMap? = null
    var style: Style? = null
    var onTap: ((String) -> Unit)? = null
    var onLongPress: ((GeoPoint) -> Unit)? = null
    var onLongPressSuburb: ((String) -> Unit)? = null

    var dropped: Set<String> = emptySet()
    var boxes: List<NoGoBox> = emptyList()
    var boxesOff: Set<String> = emptySet()
    var centre: GeoPoint? = null
    var lookAt: GeoPoint? = null
    var frameTo: List<GeoPoint> = emptyList()
}

/** The centre as GeoJSON: one point, or nothing at all. */
private fun centreJson(centre: GeoPoint?): String =
    if (centre == null) """{"type":"FeatureCollection","features":[]}"""
    else """{"type":"Feature","properties":{},"geometry":{"type":"Point","coordinates":[${centre.longitude},${centre.latitude}]}}"""

/** Fits the camera to a handful of points, such as the corners of every box. */
private fun frameOn(map: MapLibreMap, points: List<GeoPoint>) {
    if (points.isEmpty()) return
    val bounds = LatLngBounds.Builder()
        .includes(points.map { LatLng(it.latitude, it.longitude) })
        .build()
    map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 140))
}

/** Fits the camera to the chosen suburbs, or to all of them when none is chosen. */
private fun frame(map: MapLibreMap, shapes: List<SuburbShape>, chosen: Set<String>) {
    val inSet = shapes.filter { it.name in chosen }.ifEmpty { shapes }
    val box = MapProjection.boxOf(inSet) ?: return
    val bounds = LatLngBounds.Builder()
        .include(LatLng(box.minLat, box.minLon))
        .include(LatLng(box.maxLat, box.maxLon))
        .build()
    map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 48))
}

/** Close enough that a suburb fills the map, wide enough to see its neighbours. */
private const val LOOK_ZOOM = 12.0

private const val STYLE = "https://tiles.openfreemap.org/styles/dark"
private const val SOURCE = "suburbs"
private const val FILL = "suburbs-chosen-fill"
private const val EDGE = "suburbs-chosen-edge"
private const val OTHERS = "suburbs-others"
private const val DROPPED_FILL = "suburbs-dropped-fill"
private const val DROPPED_EDGE = "suburbs-dropped-edge"
private const val HIT = "suburbs-hit"
private const val BOXES = "nogo"
private const val BOX_FILL = "nogo-fill"
private const val BOX_EDGE = "nogo-edge"
private const val CENTRE = "centre"
private const val CENTRE_RING = "centre-ring"
private const val GROUND = "#0A0A0B"
private const val GOLD = "#E8B64C"
private const val LINE = "#5A5850"
private const val ORANGE = "#F0883E"
