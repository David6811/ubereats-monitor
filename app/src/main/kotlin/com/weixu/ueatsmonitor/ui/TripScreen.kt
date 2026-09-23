package com.weixu.ueatsmonitor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.weixu.ueatsmonitor.action.ExclusionStore
import com.weixu.ueatsmonitor.action.Profiles
import com.weixu.ueatsmonitor.action.SuburbShapes
import com.weixu.ueatsmonitor.domain.Exclusions
import com.weixu.ueatsmonitor.domain.SuburbAt

/**
 * This trip's suburbs: the live set with any of them dropped for now.
 *
 * Nothing here is written into the laptop's set. Dropping is for the last hour
 * of a shift, when the driver wants to end up near home - and it is forgotten
 * the moment the set is switched or new rules arrive.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun TripScreen() {
    val words = words()
    val context = LocalContext.current
    val profiles = remember { Profiles.list(context) }
    val live = profiles.firstOrNull { it.active }
    val shapes = remember { SuburbShapes.all(context) }
    var excluded: Set<String> by remember { mutableStateOf(ExclusionStore.inForce(context)) }
    // The last suburb a finger landed on, named above the map: on a map this small
    // a suburb is a few millimetres, and a miss has to be visible to be undone.
    var touched: String? by remember { mutableStateOf(null) }

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

    val all = live.suburbs.sorted()
    // The set's own centre, as drawn on the laptop, and the suburb it stands in -
    // where he wants to end up when the shift is nearly over.
    val middle = remember(live.name) {
        Profiles.centre(context)?.let { centre ->
            SuburbAt.find(centre, shapes)?.takeIf { it in live.suburbs }
        }
    }
    val going = Exclusions.apply(all.toSet(), excluded)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Panel(padding = PaddingValues(0.dp)) {
            Column(
                modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                SectionLabel(words.thisTrip(live.name))
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = going.size.toString(),
                        style = MaterialTheme.typography.headlineLarge.merge(Dash.Numbers),
                        color = Dash.Gold,
                    )
                    Text(
                        text = touched?.let { name ->
                            name + (if (name in going) words.going else words.notGoing)
                        } ?: if (excluded.isEmpty()) words.areasAllGoing else words.areasSomeOff(excluded.size),
                        modifier = Modifier.padding(bottom = 6.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = Dash.Muted,
                    )
                }
            }
            // The same outlines as the areas page, so what is left is a shape, not
            // a list of names to read one by one.
            SuburbMap(
                chosen = going,
                shapes = shapes,
                dropped = all.toSet() - going,
                onTap = { suburb ->
                    // Only this set's suburbs; a finger in the next one along is a miss.
                    if (suburb in all) {
                        ExclusionStore.toggle(context, suburb)
                        excluded = ExclusionStore.inForce(context)
                        touched = suburb
                    }
                },
                onLongPress = { suburb ->
                    if (suburb in all) {
                        ExclusionStore.keepOnly(context, suburb, all)
                        excluded = ExclusionStore.inForce(context)
                        touched = suburb
                    }
                },
            )
        }

        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    SectionLabel(words.tapToToggle)
                    Text(
                        text = words.thisTripOnly,
                        style = MaterialTheme.typography.bodySmall,
                        color = Dash.Muted,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    middle?.let { here ->
                        GhostButton(words.keepCentreOnly, color = Dash.Gold) {
                            ExclusionStore.keepOnly(context, here, all)
                            excluded = ExclusionStore.inForce(context)
                        }
                    }
                    if (going.isNotEmpty()) {
                        GhostButton(words.clearAll, color = Dash.Muted) {
                            ExclusionStore.excludeAll(context, all)
                            excluded = ExclusionStore.inForce(context)
                        }
                    }
                    if (excluded.isNotEmpty()) {
                        GhostButton(words.restoreAll, color = Dash.Gold) {
                            ExclusionStore.clear(context)
                            excluded = ExclusionStore.inForce(context)
                        }
                    }
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                all.forEach { suburb ->
                    val on = suburb in going
                    Text(
                        text = suburb,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (on) Dash.GoldDeep else Dash.Ground)
                            .border(1.dp, if (on) Dash.Gold.copy(alpha = 0.5f) else Dash.Line, RoundedCornerShape(10.dp))
                            .combinedClickable(
                                onClick = {
                                    ExclusionStore.toggle(context, suburb)
                                    excluded = ExclusionStore.inForce(context)
                                },
                                onLongClick = {
                                    // The last hour of a shift: one suburb, everything else off.
                                    ExclusionStore.keepOnly(context, suburb, all)
                                    excluded = ExclusionStore.inForce(context)
                                },
                            )
                            .padding(horizontal = 14.dp, vertical = 11.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (on) Dash.Gold else Dash.Muted,
                        textDecoration = if (on) null else TextDecoration.LineThrough,
                    )
                }
            }
        }
    }
}
