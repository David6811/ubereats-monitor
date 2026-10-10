package com.weixu.ueatsmonitor.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.weixu.ueatsmonitor.action.NoGoOffStore
import com.weixu.ueatsmonitor.action.RulesEditor
import com.weixu.ueatsmonitor.action.RulesStore
import com.weixu.ueatsmonitor.action.RulesSync
import com.weixu.ueatsmonitor.domain.GeoPoint
import com.weixu.ueatsmonitor.domain.NoGoBox
import com.weixu.ueatsmonitor.domain.RuleEdits
import kotlinx.coroutines.launch

/**
 * The boxes nothing is picked up or delivered inside.
 *
 * Its own screen, beside the one that changes a set rather than inside it: a
 * box belongs to the rules and holds whichever set is being worked.
 *
 * Drawing is press, drag, release, the way it is on the laptop. Two taps for
 * two corners was tried first and is not accurate enough: the boxes that exist
 * are a few hundred metres across.
 */
@Composable
fun BoxScreen(
    shapes: List<com.weixu.ueatsmonitor.domain.SuburbShape>,
    onLeave: () -> Unit,
    onBare: (Boolean) -> Unit = {},
) {
    val words = words()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var boxes by remember { mutableStateOf(RulesStore.drawn(context)) }
    val working = remember { com.weixu.ueatsmonitor.action.Profiles.suburbsInForce(context).orEmpty() }
    var off by remember { mutableStateOf(NoGoOffStore.inForce(context)) }
    var drawing by remember { mutableStateOf(false) }
    var naming by remember { mutableStateOf<Pair<GeoPoint, GeoPoint>?>(null) }
    var chosen by remember { mutableStateOf<String?>(null) }
    var renaming by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { onBare(true) }
    DisposableEffect(Unit) { onDispose { onBare(false) } }
    BackHandler { if (drawing) drawing = false else onLeave() }

    fun change(what: (kotlinx.serialization.json.JsonObject) -> kotlinx.serialization.json.JsonObject) {
        saving = true
        scope.launch {
            when (RulesEditor.edit(context, what)) {
                RulesEditor.Saved.Pushed -> note = null
                is RulesEditor.Saved.OnPhoneOnly -> note = words.savedOnPhoneOnly
                RulesEditor.Saved.CloudMovedOn -> { RulesSync.pull(context); note = words.cloudMovedOn }
            }
            boxes = RulesStore.drawn(context)
            saving = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Dash.Ground)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(words.noGoBoxes, style = MaterialTheme.typography.headlineSmall, color = Dash.Orange)
            Text(
                text = words.boxCount(boxes.size),
                modifier = Modifier
                    .weight(1f)
                    .padding(bottom = 4.dp),
                style = MaterialTheme.typography.bodyLarge.merge(Dash.Numbers),
                color = Dash.Muted,
            )
        }

        AreaMap(
            // The boxes are not a set, so nothing reframes the camera: it opens
            // on the whole city and the driver zooms to where he is working.
            setName = "no-go",
            chosen = emptySet(),
            boxes = boxes,
            boxesOff = off,
            shapes = shapes,
            // Opens on the boxes, or on the set being worked when there are
            // none: the whole of Victoria is nobody's starting point.
            frameTo = boxes.flatMap {
                listOf(GeoPoint(it.south, it.west), GeoPoint(it.north, it.east))
            }.ifEmpty {
                shapes.filter { it.name in working }.flatMap { shape -> shape.rings.flatten() }
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(Dash.PanelShape),
            drawingBox = drawing,
            onBoxDrawn = { a, b ->
                drawing = false
                // A press with no drag is a slip, not a box.
                if (kotlin.math.abs(a.latitude - b.latitude) >= 0.0005 &&
                    kotlin.math.abs(a.longitude - b.longitude) >= 0.0005
                ) {
                    naming = a to b
                } else {
                    note = words.boxTooSmall
                }
            },
        )

        note?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Dash.Orange) }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = if (drawing) words.dragToDraw else words.boxesHint,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = if (drawing) Dash.Orange else Dash.Muted,
            )
            SmallButton(words.cancel, gold = false, enabled = !saving) {
                if (drawing) drawing = false else onLeave()
            }
            SmallButton(if (drawing) words.stopDrawing else words.drawBox, gold = true, enabled = !saving) {
                drawing = !drawing
                note = null
            }
        }

        if (boxes.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // One row of names, so a box can be renamed or deleted without
                // having to find it on the map first.
                boxes.take(4).forEach { box ->
                    Text(
                        text = box.label,
                        modifier = Modifier
                            .weight(1f)
                            .clip(Dash.ControlShape)
                            .clickable { chosen = if (chosen == box.label) null else box.label }
                            .padding(vertical = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (chosen == box.label) Dash.Orange else Dash.Muted,
                    )
                }
            }
        }
        chosen?.let { label ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SmallButton(words.renameSet, gold = false, enabled = !saving) { renaming = label }
                SmallButton(words.deleteSet, gold = false, enabled = !saving) { deleting = label }
            }
        }
    }

    naming?.let { (a, b) ->
        TypedName(
            title = words.drawBox,
            start = words.boxDefaultName(boxes.size + 1),
            taken = boxes.map { it.label },
            onDismiss = { naming = null },
        ) { typed ->
            naming = null
            change {
                RuleEdits.addBox(
                    it,
                    NoGoBox(
                        label = typed,
                        south = minOf(a.latitude, b.latitude),
                        west = minOf(a.longitude, b.longitude),
                        north = maxOf(a.latitude, b.latitude),
                        east = maxOf(a.longitude, b.longitude),
                    ),
                )
            }
        }
    }

    renaming?.let { label ->
        val old = boxes.first { it.label == label }
        TypedName(
            title = words.renameSet,
            start = label,
            taken = boxes.map { it.label } - label,
            onDismiss = { renaming = null },
        ) { typed ->
            renaming = null
            chosen = typed
            change { RuleEdits.addBox(RuleEdits.removeBox(it, label), old.copy(label = typed)) }
        }
    }

    deleting?.let { label ->
        Confirm(
            question = words.reallyDelete(label),
            yes = words.deleteSet,
            onDismiss = { deleting = null },
        ) {
            deleting = null
            chosen = null
            change { RuleEdits.removeBox(it, label) }
        }
    }
}
