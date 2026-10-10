package com.weixu.ueatsmonitor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.weixu.ueatsmonitor.R
import com.weixu.ueatsmonitor.action.ExclusionStore
import com.weixu.ueatsmonitor.action.RulesStore
import com.weixu.ueatsmonitor.action.NoGoOffStore
import com.weixu.ueatsmonitor.action.Profiles
import com.weixu.ueatsmonitor.action.SuburbShapes
import com.weixu.ueatsmonitor.domain.Exclusions
import com.weixu.ueatsmonitor.domain.GeoPoint
import com.weixu.ueatsmonitor.domain.SuburbAt

/**
 * This trip's suburbs: the live set with any of them dropped for now.
 *
 * Nothing here is written into the laptop's set. Dropping is for the last hour
 * of a shift, when the driver wants to end up near home - and it is forgotten
 * the moment the set is switched or new rules arrive.
 *
 * The map is the page. It used to be 300dp above a list of every suburb as a
 * named block, which did the same job twice and left each suburb a few
 * millimetres wide; the names are gone and the map has the screen.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TripScreen() {
    val words = words()
    val context = LocalContext.current
    val profiles = remember { Profiles.list(context) }
    val live = profiles.firstOrNull { it.active }
    val shapes = remember { SuburbShapes.all(context) }
    var excluded: Set<String> by remember { mutableStateOf(ExclusionStore.inForce(context)) }
    val boxes = remember { RulesStore.drawn(context) }
    var boxesOff: Set<String> by remember { mutableStateOf(NoGoOffStore.inForce(context)) }
    // The boxes wait behind a mark, the way the centre does on the edit screen:
    // they are changed once in a blue moon and there can be a dozen of them.
    var showingBoxes by remember { mutableStateOf(false) }

    if (live == null) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Panel {
                SectionLabel(words.noAreasYet)
                Text(
                    text = words.pushFromTheWeb,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Dash.Muted,
                )
            }
        }
        return
    }

    val all = live.suburbs.toSet()
    val centre = remember(live.name) { Profiles.centre(context) }
    // The suburb the set's centre stands in - where he wants to end up when the
    // shift is nearly over.
    val middle = remember(live.name) {
        centre?.let { SuburbAt.find(it, shapes)?.takeIf { name -> name in all } }
    }
    val going = Exclusions.apply(all, excluded)

    fun reread() {
        excluded = ExclusionStore.inForce(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = going.size.toString(),
                style = MaterialTheme.typography.headlineSmall.merge(Dash.Numbers),
                color = Dash.Gold,
            )
            // No name for the last suburb touched. The map says which one it
            // was, in colour, and said it for every other one at the same time;
            // the line could only ever hold one, and not even that once four or
            // five were off.
            Text(
                text = if (excluded.isEmpty()) words.areasAllGoing else words.areasSomeOff(excluded.size),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = Dash.Muted,
            )
            if (boxes.isNotEmpty()) {
                Icon(
                    painter = painterResource(R.drawable.ic_nogo),
                    contentDescription = words.noGoBoxes,
                    tint = if (showingBoxes) Dash.Orange else Dash.Muted,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable { showingBoxes = !showingBoxes }
                        .padding(8.dp),
                )
            }
        }

        if (showingBoxes) {
            // On means the box is refusing, which is the opposite way round from
            // a suburb: there, on means going.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                boxes.forEach { box ->
                    val refusing = boxesOff.none { it.equals(box.label, ignoreCase = true) }
                    Text(
                        text = box.label,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (refusing) Dash.OrangeDeep else Dash.Ground)
                            .border(
                                1.dp,
                                if (refusing) Dash.Orange.copy(alpha = 0.5f) else Dash.Line,
                                RoundedCornerShape(10.dp),
                            )
                            .clickable {
                                NoGoOffStore.toggle(context, box.label)
                                boxesOff = NoGoOffStore.inForce(context)
                            }
                            .padding(horizontal = 14.dp, vertical = 11.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (refusing) Dash.Orange else Dash.Muted,
                        textDecoration = if (refusing) null else TextDecoration.LineThrough,
                    )
                }
            }
        }

        AreaMap(
            setName = live.name,
            chosen = going,
            dropped = all - going,
            // The boxes the laptop drew, where until now the phone showed them
            // only as a row of names.
            boxes = boxes,
            boxesOff = boxesOff,
            shapes = shapes,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(Dash.PanelShape),
            centre = centre?.let { GeoPoint(it.latitude, it.longitude) },
            // Only this set's suburbs; a finger in the next one along is a miss.
            onTap = { suburb ->
                if (suburb in all) {
                    ExclusionStore.toggle(context, suburb)
                    reread()
                }
            },
            // The last hour of a shift: one suburb, everything else off.
            onLongPressSuburb = { suburb ->
                if (suburb in all) {
                    ExclusionStore.keepOnly(context, suburb, all.toList())
                    reread()
                }
            },
        )

        Text(words.tapToToggle, style = MaterialTheme.typography.bodySmall, color = Dash.Muted)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            middle?.let { here ->
                GhostButton(words.keepCentreOnly, color = Dash.Gold) {
                    ExclusionStore.keepOnly(context, here, all.toList())
                    reread()
                }
            }
            if (going.isNotEmpty()) {
                GhostButton(words.clearAll, color = Dash.Muted) {
                    ExclusionStore.excludeAll(context, all.toList())
                    reread()
                }
            }
            if (excluded.isNotEmpty()) {
                GhostButton(words.restoreAll, color = Dash.Gold) {
                    ExclusionStore.clear(context)
                    reread()
                }
            }
        }
    }
}
