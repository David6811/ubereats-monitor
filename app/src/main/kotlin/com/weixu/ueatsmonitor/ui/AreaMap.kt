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
import com.weixu.ueatsmonitor.domain.MapProjection
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
    chosen: Set<String>,
    shapes: List<SuburbShape>,
    modifier: Modifier = Modifier,
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
                map.setStyle(Style.Builder().fromUri(STYLE)) { style ->
                    style.addSource(GeoJsonSource(SOURCE, SuburbGeoJson.featureCollection(shapes, holder.chosen)))
                    style.addLayer(
                        LineLayer(OTHERS, SOURCE)
                            .withProperties(PropertyFactory.lineColor(LINE), PropertyFactory.lineWidth(0.6f))
                            .withFilter(Expression.eq(Expression.get(SuburbGeoJson.CHOSEN), Expression.literal(false))),
                    )
                    style.addLayer(
                        FillLayer(FILL, SOURCE)
                            .withProperties(PropertyFactory.fillColor(GOLD), PropertyFactory.fillOpacity(0.28f))
                            .withFilter(Expression.eq(Expression.get(SuburbGeoJson.CHOSEN), Expression.literal(true))),
                    )
                    style.addLayer(
                        LineLayer(EDGE, SOURCE)
                            .withProperties(PropertyFactory.lineColor(GOLD), PropertyFactory.lineWidth(1.6f))
                            .withFilter(Expression.eq(Expression.get(SuburbGeoJson.CHOSEN), Expression.literal(true))),
                    )
                    holder.map = map
                    holder.style = style
                    frame(map, shapes, holder.chosen)
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
            // A different set picked below: recolour and reframe, keep the map.
            if (holder.chosen != chosen) {
                holder.chosen = chosen
                holder.style?.getSourceAs<GeoJsonSource>(SOURCE)?.setGeoJson(SuburbGeoJson.featureCollection(shapes, chosen))
                holder.map?.let { frame(it, shapes, chosen) }
            }
        },
    )
}

/** What the map callbacks and the next recomposition share. */
private class MapHolder {
    var chosen: Set<String> = emptySet()
    var map: MapLibreMap? = null
    var style: Style? = null
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

private const val STYLE = "https://tiles.openfreemap.org/styles/dark"
private const val SOURCE = "suburbs"
private const val FILL = "suburbs-chosen-fill"
private const val EDGE = "suburbs-chosen-edge"
private const val OTHERS = "suburbs-others"
private const val GOLD = "#E8B64C"
private const val LINE = "#5A5850"
