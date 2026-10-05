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

/** The payout above which the far set is used instead. */
@Composable
fun FarThresholdPanel(write: RuleWriter, reload: Int) {
    val words = words()
    val now = remember(reload) { RuleEdits.farOverDollars(write.rules()) }
    var typed by remember(now) { mutableStateOf(now.toString()) }

    Panel {
        SectionLabel(words.farThreshold)
        NumberField(words.farThreshold, typed) { next ->
            typed = next
            next.toIntOrNull()?.takeIf { it > 0 }?.let { dollars ->
                write { RuleEdits.setFarOverDollars(it, dollars) }
            }
        }
    }
}

/**
 * The sets: which one is live, and everything the laptop could do to one.
 *
 * The centre is taken from the phone's own position rather than typed. A
 * driver setting a centre is standing in it, and a map's own fix beats an
 * address he half remembers.
 */
@Composable
fun SetsPanel(write: RuleWriter, reload: Int, onSwitch: (String) -> Unit) {
    val words = words()
    val context = androidx.compose.ui.platform.LocalContext.current
    val sets = remember(reload) { RuleEdits.sets(write.rules()) }
    var naming by remember { mutableStateOf<Naming?>(null) }
    var editing by remember { mutableStateOf<String?>(null) }

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
                FlowRowActions(
                    words.editSuburbs to { editing = set.name },
                    words.renameSet to { naming = Naming.Rename(set.name) },
                    words.copySet to { naming = Naming.Copy(set.name) },
                    words.setCentreHere to {
                        val fix = CurrentPosition(context).lastKnown()
                        if (fix == null) {
                            android.widget.Toast.makeText(context, words.noFixForCentre, android.widget.Toast.LENGTH_LONG).show()
                        } else {
                            write {
                                RuleEdits.setCentre(
                                    it,
                                    set.name,
                                    RuleEdits.Centre(words.setCentreHere, fix.at.latitude, fix.at.longitude),
                                )
                            }
                        }
                    },
                    words.deleteSet to { naming = Naming.Delete(set.name) },
                )
            }
        }
        Hairline()
        GhostButton(words.newSet, Modifier.fillMaxWidth(), color = Dash.Gold) { naming = Naming.New }
    }

    naming?.let { what ->
        NameDialog(what, words, onDismiss = { naming = null }) { typed ->
            naming = null
            when (what) {
                Naming.New -> write { RuleEdits.addSet(it, typed, null, emptyList()) }
                is Naming.Rename -> write { RuleEdits.renameSet(it, what.name, typed) }
                is Naming.Copy -> write { RuleEdits.copySet(it, what.name, typed) }
                is Naming.Delete -> write { RuleEdits.removeSet(it, what.name) }
            }
        }
    }

    editing?.let { name ->
        SuburbPicker(
            name = name,
            chosen = sets.firstOrNull { it.name == name }?.suburbs.orEmpty(),
            onDone = { editing = null },
        ) { suburbs -> write { RuleEdits.setSuburbs(it, name, suburbs) } }
    }
}

private sealed interface Naming {
    data object New : Naming
    data class Rename(val name: String) : Naming
    data class Copy(val name: String) : Naming
    data class Delete(val name: String) : Naming
}

@Composable
private fun NameDialog(what: Naming, words: Words, onDismiss: () -> Unit, onDone: (String) -> Unit) {
    var typed by remember {
        mutableStateOf(
            when (what) {
                Naming.New -> ""
                is Naming.Rename -> what.name
                is Naming.Copy -> what.name + " copy"
                is Naming.Delete -> what.name
            }
        )
    }
    val deleting = what is Naming.Delete
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when (what) {
                    Naming.New -> words.newSet
                    is Naming.Rename -> words.renameSet
                    is Naming.Copy -> words.copySet
                    is Naming.Delete -> words.deleteSet + "  " + what.name
                }
            )
        },
        text = {
            if (!deleting) {
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { if (deleting || typed.isNotBlank()) onDone(typed.trim()) }) {
                Text(if (deleting) words.deleteSet else words.save)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(words.cancel) } },
        containerColor = Dash.Panel,
    )
}

/**
 * Which suburbs a set holds, found by typing rather than hunted on a map: on a
 * screen this size a suburb is a few millimetres, and the name is known.
 */
@Composable
private fun SuburbPicker(
    name: String,
    chosen: List<String>,
    onDone: () -> Unit,
    onChange: (List<String>) -> Unit,
) {
    val words = words()
    val context = androidx.compose.ui.platform.LocalContext.current
    val all = remember { Gazetteer.suburbs(context).map { it.name }.distinct().sorted() }
    var picked by remember { mutableStateOf(chosen.toSet()) }
    var typed by remember { mutableStateOf("") }
    // Chosen first, so what the set holds is never buried under what it does not.
    val shown = remember(typed, picked, all) {
        val wanted = typed.trim().lowercase()
        all.filter { wanted.isEmpty() || it.lowercase().contains(wanted) }
            .sortedBy { if (it in picked) 0 else 1 }
    }

    AlertDialog(
        onDismissRequest = { onChange(picked.toList()); onDone() },
        title = { Text(name + "  ·  " + words.areasCount(picked.size)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    label = { Text(words.searchSuburb) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(shown, key = { it }) { suburb ->
                        val on = suburb in picked
                        Text(
                            text = suburb,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { picked = if (on) picked - suburb else picked + suburb }
                                .padding(vertical = 12.dp),
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (on) Dash.Gold else Dash.Muted,
                            textDecoration = if (on) null else TextDecoration.LineThrough,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onChange(picked.toList()); onDone() }) { Text(words.doneEditing) }
        },
        containerColor = Dash.Panel,
    )
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRowActions(vararg actions: Pair<String, () -> Unit>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        actions.forEach { (label, onClick) ->
            Text(
                text = label,
                modifier = Modifier.clickable(onClick = onClick).padding(vertical = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = Dash.Gold,
            )
        }
    }
}
