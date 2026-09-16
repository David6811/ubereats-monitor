package com.weixu.ueatsmonitor.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.weixu.ueatsmonitor.domain.GeoBox
import com.weixu.ueatsmonitor.domain.MapProjection
import com.weixu.ueatsmonitor.domain.SuburbShape

/**
 * The set drawn as shapes. Read only - the sets are made on the laptop, on a real
 * map; this is here to answer "which one is this" at a glance.
 *
 * No tiles and no streets: the outlines are bundled with the app, so this works
 * with no network and no map library.
 */
@Composable
fun SuburbMap(
    chosen: Set<String>,
    shapes: List<SuburbShape>,
    modifier: Modifier = Modifier,
    /**
     * Suburbs of the set that are not being gone to right now. Drawn in their
     * place rather than left out, so dropping one changes a colour instead of
     * making the map redraw itself around a new hole.
     */
    dropped: Set<String> = emptySet(),
) {
    val mine = remember(chosen, shapes) { shapes.filter { it.name in chosen } }
    val off = remember(dropped, shapes) { shapes.filter { it.name in dropped } }
    if (mine.isEmpty() && off.isEmpty()) return

    // The frame holds the whole set, so it does not jump as suburbs go on and off.
    val box = remember(mine, off) { MapProjection.boxOf(mine + off) } ?: return
    // The neighbours give the shapes somewhere to sit; without them a handful of
    // outlines floating in the dark says nothing about where they are.
    val around = remember(box, shapes, chosen, dropped) {
        shapes.filter { it.name !in chosen && it.name !in dropped && touches(it, box) }
    }

    val ink = Dash.Gold
    val faint = Dash.Line
    val offInk = Dash.Muted

    Canvas(
        modifier
            .fillMaxWidth()
            .height(300.dp)
            .clip(RoundedCornerShape(bottomStart = 18.dp, bottomEnd = 18.dp))
            .background(Dash.Panel)
    ) {
        val fit = MapProjection.fit(box, size.width, size.height, PADDING)
        around.forEach { shape ->
            drawPath(pathOf(shape, fit), color = faint, style = Stroke(width = 1f))
        }
        off.forEach { shape ->
            val path = pathOf(shape, fit)
            drawPath(path, color = offInk.copy(alpha = 0.10f))
            drawPath(path, color = offInk.copy(alpha = 0.55f), style = Stroke(width = 1.5f))
        }
        mine.forEach { shape ->
            val path = pathOf(shape, fit)
            drawPath(path, color = ink.copy(alpha = 0.18f))
            drawPath(path, color = ink, style = Stroke(width = 2.5f))
        }
    }
}

private fun pathOf(shape: SuburbShape, fit: MapProjection.Fit): Path {
    val path = Path()
    shape.rings.forEach { ring ->
        ring.forEachIndexed { at, point ->
            val pixel = fit.place(point)
            if (at == 0) path.moveTo(pixel.x, pixel.y) else path.lineTo(pixel.x, pixel.y)
        }
        path.close()
    }
    return path
}

/** Cheap overlap test on the boxes, so only nearby suburbs are drawn. */
private fun touches(shape: SuburbShape, box: GeoBox): Boolean {
    val its = MapProjection.boxOf(listOf(shape)) ?: return false
    return its.minLat <= box.maxLat && its.maxLat >= box.minLat &&
        its.minLon <= box.maxLon && its.maxLon >= box.minLon
}

private const val PADDING = 12f
