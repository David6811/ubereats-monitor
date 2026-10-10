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
     * Where a tap landed, instead of which suburb. Set while a box is being
     * drawn: a corner is a point on the earth, not a suburb.
     */
    onTapPoint: ((GeoPoint) -> Unit)? = null,
    /** The first corner of a box being drawn, drawn as a dot. */
    corner: GeoPoint? = null,
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
                    holder.onTapPoint?.let { put ->
                        put(GeoPoint(at.latitude, at.longitude))
                        return@addOnMapClickListener true
                    }
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
                    style.addSource(GeoJsonSource(CORNER, centreJson(holder.corner)))
                    style.addLayer(
                        CircleLayer(CORNER_DOT, CORNER).withProperties(
                            PropertyFactory.circleRadius(7f),
                            PropertyFactory.circleColor(android.graphics.Color.parseColor(ORANGE)),
                            PropertyFactory.circleStrokeColor(android.graphics.Color.parseColor(GROUND)),
                            PropertyFactory.circleStrokeWidth(2f),
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
                    frame(map, shapes, holder.chosen + holder.dropped)
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

    AndroidView(
        modifier = modifier,
        factory = { mapView },
        update = {
            holder.onTap = onTap
            holder.onLongPress = onLongPress
            holder.onLongPressSuburb = onLongPressSuburb
            holder.onTapPoint = onTapPoint
            if (holder.corner != corner) {
                holder.corner = corner
                holder.style?.getSourceAs<GeoJsonSource>(CORNER)?.setGeoJson(centreJson(corner))
            }
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
    var onTapPoint: ((GeoPoint) -> Unit)? = null
    var corner: GeoPoint? = null
    var dropped: Set<String> = emptySet()
    var boxes: List<NoGoBox> = emptyList()
    var boxesOff: Set<String> = emptySet()
    var centre: GeoPoint? = null
    var lookAt: GeoPoint? = null
}

/** The centre as GeoJSON: one point, or nothing at all. */
private fun centreJson(centre: GeoPoint?): String =
    if (centre == null) """{"type":"FeatureCollection","features":[]}"""
    else """{"type":"Feature","properties":{},"geometry":{"type":"Point","coordinates":[${centre.longitude},${centre.latitude}]}}"""

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
private const val CORNER = "box-corner"
private const val CORNER_DOT = "box-corner-dot"
private const val CENTRE = "centre"
private const val CENTRE_RING = "centre-ring"
private const val GROUND = "#0A0A0B"
private const val GOLD = "#E8B64C"
private const val LINE = "#5A5850"
private const val ORANGE = "#F0883E"
