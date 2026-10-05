package com.weixu.ueatsmonitor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.weixu.ueatsmonitor.action.CurrentPosition
import com.weixu.ueatsmonitor.action.Gazetteer
import com.weixu.ueatsmonitor.domain.NoGoBox
import com.weixu.ueatsmonitor.domain.RuleEdits
import com.weixu.ueatsmonitor.domain.Words

/**
 * The pieces of the laptop editor that belong on the phone.
 *
 * The laptop has a big map and a mouse, and draws with both. A phone has
 * neither, so the same jobs are done the way a phone is good at: a suburb is
 * found by typing its name rather than hunted on a map, and a no-go box is
 * drawn around where the driver is standing rather than dragged out with a
 * finger on a map the size of a postcard.
 */

/** The shops refused by name, with the field that adds one. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShopsPanel(write: RuleWriter, reload: Int) {
    val words = words()
    val denied = remember(reload) { RuleEdits.deniedStores(write.rules()) }
    var typed by remember { mutableStateOf("") }

    Panel {
        SectionLabel(words.shopsRefused + "  ·  " + denied.size)
        Text(words.shopsRefusedHint, style = MaterialTheme.typography.bodySmall, color = Dash.Muted)
        OutlinedTextField(
            value = typed,
            onValueChange = { typed = it },
            label = { Text(words.addShop) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                val name = typed.trim()
                if (name.isNotEmpty()) {
                    write { RuleEdits.denyStore(it, name) }
                    typed = ""
                }
            }),
            modifier = Modifier.fillMaxWidth(),
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            denied.forEach { name ->
                Text(
                    text = name + "   ✕",
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Dash.Raised)
                        .clickable { write { RuleEdits.allowStore(it, name) } }
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Dash.Ink,
                )
            }
        }
    }
}

/**
 * The sets: which one is live, and the one thing about a set a phone does
 * better than a laptop - its centre.
 *
 * Making a set, naming it, copying it, choosing its suburbs: all of that is
 * planning, done at a table with a map in front of you, and all of it was
 * unwieldy enough here that it was not used. It stays on the laptop. The
 * centre does not: a driver setting one is standing in it, and a fix beats an
 * address half remembered.
 */
@Composable
fun SetsPanel(write: RuleWriter, reload: Int, onSwitch: (String) -> Unit) {
    val words = words()
    val context = androidx.compose.ui.platform.LocalContext.current
    val sets = remember(reload) { RuleEdits.sets(write.rules()) }

    Panel(padding = PaddingValues(horizontal = 18.dp, vertical = 14.dp)) {
        SectionLabel(words.switchSet)
        sets.forEachIndexed { index, set ->
            if (index > 0) Hairline()
            Column(Modifier.padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = set.name,
                        modifier = Modifier.weight(1f).clickable { onSwitch(set.name) },
                        style = MaterialTheme.typography.titleMedium,
                        color = Dash.Ink,
                    )
                    Text(
                        text = words.areasCount(set.suburbs.size),
                        style = MaterialTheme.typography.bodyMedium.merge(Dash.Numbers),
                        color = Dash.Muted,
                    )
                }
                set.centre?.let {
                    Text(
                        words.centreOfSet(it.label.ifBlank { "%.4f, %.4f".format(it.latitude, it.longitude) }),
                        style = MaterialTheme.typography.bodySmall,
                        color = Dash.Muted,
                    )
                }
                Text(
                    text = words.setCentreHere,
                    modifier = Modifier
                        .clickable {
                            val fix = CurrentPosition(context).lastKnown()
                            if (fix == null) {
                                android.widget.Toast.makeText(context, words.noFixForCentre, android.widget.Toast.LENGTH_LONG).show()
                            } else {
                                // No label. The row falls back to the coordinates,
                                // which tell two phone-set centres apart; the
                                // button's own words told them apart from nothing.
                                write { RuleEdits.setCentre(it, set.name, RuleEdits.Centre("", fix.at.latitude, fix.at.longitude)) }
                            }
                        }
                        .padding(vertical = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = Dash.Gold,
                )
            }
        }
    }
}

/**
 * The no-go boxes, and a way to draw one without a map: a square around where
 * the driver is standing. Dragging a rectangle on a map this size is a worse
 * way to say "not this block" than standing in it and saying so.
 */
@Composable
fun BoxesPanel(write: RuleWriter, reload: Int) {
    val words = words()
    val context = androidx.compose.ui.platform.LocalContext.current
    val boxes = remember(reload) { RuleEdits.boxes(write.rules()) }
    var naming by remember { mutableStateOf<Int?>(null) }

    Panel {
        SectionLabel(words.noGoBoxes + "  ·  " + boxes.size)
        Text(
            if (boxes.isEmpty()) words.noGoBoxesNone else words.drawBoxHereHint,
            style = MaterialTheme.typography.bodySmall,
            color = Dash.Muted,
        )
        FlowRowBoxes(boxes) { label -> write { RuleEdits.removeBox(it, label) } }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(200, 400, 800).forEach { metres ->
                GhostButton("$metres m", color = Dash.Orange) { naming = metres }
            }
        }
        Text(words.drawBoxHere, style = MaterialTheme.typography.bodySmall, color = Dash.Muted)
    }

    naming?.let { metres ->
        var typed by remember(metres) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { naming = null },
            title = { Text(words.drawBoxHere + "  " + metres + " m") },
            text = {
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val label = typed.trim()
                    val fix = CurrentPosition(context).lastKnown()
                    naming = null
                    when {
                        label.isEmpty() ->
                            android.widget.Toast.makeText(context, words.needAName, android.widget.Toast.LENGTH_SHORT).show()
                        fix == null ->
                            android.widget.Toast.makeText(context, words.noFixForCentre, android.widget.Toast.LENGTH_LONG).show()
                        else -> write { RuleEdits.addBox(it, squareAround(label, fix.at.latitude, fix.at.longitude, metres)) }
                    }
                }) { Text(words.save) }
            },
            dismissButton = { TextButton(onClick = { naming = null }) { Text(words.cancel) } },
            containerColor = Dash.Panel,
        )
    }
}

/**
 * Calculation. A square of [metres] a side around a point. A degree of
 * latitude is 111 km anywhere; a degree of longitude narrows towards the pole,
 * and at Melbourne's latitude that is enough to make a square drawn without it
 * half as wide as it is tall.
 */
internal fun squareAround(label: String, latitude: Double, longitude: Double, metres: Int): NoGoBox {
    val half = metres / 2.0
    val lat = half / 111_320.0
    val lon = half / (111_320.0 * kotlin.math.cos(Math.toRadians(latitude)))
    return NoGoBox(label, latitude - lat, longitude - lon, latitude + lat, longitude + lon)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRowBoxes(boxes: List<NoGoBox>, onRemove: (String) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        boxes.forEach { box ->
            Text(
                text = box.label + "   ✕",
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Dash.OrangeDeep)
                    .clickable { onRemove(box.label) }
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = Dash.Orange,
            )
        }
    }
}

