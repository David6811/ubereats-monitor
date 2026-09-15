package com.weixu.ueatsmonitor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.dp
import com.weixu.ueatsmonitor.action.ExclusionStore
import com.weixu.ueatsmonitor.action.Profiles
import com.weixu.ueatsmonitor.action.SuburbShapes
import com.weixu.ueatsmonitor.domain.Exclusions

/**
 * Picking which set of suburbs is live - the one rule that changes mid-shift.
 *
 * The sets are drawn on the laptop, on the map. Here they are a list you tap.
 */
@Composable
fun ProfileScreen() {
    val context = LocalContext.current
    var profiles by remember { mutableStateOf(Profiles.list(context)) }
    var fromPhone by remember { mutableStateOf(Profiles.chosenHere(context)) }

    val shapes = remember { SuburbShapes.all(context) }
    var excluded: Set<String> by remember { mutableStateOf(ExclusionStore.inForce(context)) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (profiles.isEmpty()) {
            Panel {
                SectionLabel("还没有选区")
                Text(
                    text = "在电脑的编辑器里点一次「保存并推送到手机」。",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Dash.Muted,
                )
            }
        }

        // Whichever set is live, drawn as shapes so it is obvious which one it is.
        profiles.firstOrNull { it.active }?.let { live ->
            Panel(padding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Two places can pick, so say plainly which one did.
                    SectionLabel(if (fromPhone) "现在用 · 手机上选的" else "现在用 · 电脑推过来的")
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(live.name, style = MaterialTheme.typography.headlineLarge, color = Dash.Gold)
                        Text(
                            text = Exclusions.apply(live.suburbs.toSet(), excluded).size.toString() + " 个区",
                            modifier = Modifier.padding(bottom = 6.dp),
                            style = MaterialTheme.typography.bodyLarge.merge(Dash.Numbers),
                            color = Dash.Muted,
                        )
                    }
                }
                SuburbMap(
                    chosen = Exclusions.apply(live.suburbs.toSet(), excluded),
                    shapes = shapes,
                )
            }
        }

        if (profiles.isNotEmpty()) {
            Panel(padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp, vertical = 14.dp)) {
                SectionLabel("换一套")
                Column {
                    profiles.forEachIndexed { index, profile ->
                        if (index > 0) Hairline()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    Profiles.choose(context, profile.name)
                                    profiles = Profiles.list(context)
                                    fromPhone = Profiles.chosenHere(context)
                                    // Switching sets drops them, and this is what that looks like.
                                    excluded = ExclusionStore.inForce(context)
                                }
                                .padding(vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .border(2.dp, if (profile.active) Dash.Gold else Dash.Line, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (profile.active) {
                                    Box(Modifier.size(11.dp).clip(CircleShape).background(Dash.Gold))
                                }
                            }
                            Text(
                                text = profile.name,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.titleMedium,
                                color = if (profile.active) Dash.Ink else Dash.Muted,
                            )
                            Text(
                                text = profile.suburbs.size.toString() + " 区",
                                style = MaterialTheme.typography.bodyMedium.merge(Dash.Numbers),
                                color = Dash.Muted,
                            )
                        }
                    }
                }
            }
        }

        profiles.firstOrNull { it.active }?.let { live ->
            Panel {
                SuburbTicks(
                    suburbs = live.suburbs.sorted(),
                    excluded = excluded,
                    onToggle = { suburb ->
                        ExclusionStore.toggle(context, suburb)
                        excluded = ExclusionStore.inForce(context)
                    },
                    onRestore = {
                        ExclusionStore.clear(context)
                        excluded = ExclusionStore.inForce(context)
                    },
                )
            }
        }
    }
}

/**
 * The live set's suburbs, each with a tick. Unticking one drops it for this
 * shift only - it is not written into the laptop's set, and it is forgotten the
 * moment the set is switched or new rules arrive.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SuburbTicks(
    suburbs: List<String>,
    excluded: Set<String>,
    onToggle: (String) -> Unit,
    onRestore: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            SectionLabel("这一趟去哪些")
            Text(
                text = if (excluded.isEmpty()) "点掉就是这一趟不去，换套或推新规则会恢复"
                else "去掉了 " + excluded.sorted().joinToString("、"),
                style = MaterialTheme.typography.bodySmall,
                color = if (excluded.isEmpty()) Dash.Muted else Dash.Orange,
            )
        }
        if (excluded.isNotEmpty()) {
            Text(
                text = "恢复",
                modifier = Modifier.clickable(onClick = onRestore).padding(8.dp),
                style = MaterialTheme.typography.labelLarge,
                color = Dash.Gold,
            )
        }
    }
    // Across as well as down: a set of thirty suburbs is a long scroll in one
    // column, and each name is short enough to sit beside its neighbour.
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        suburbs.forEach { suburb ->
            val on = excluded.none { it.equals(suburb, ignoreCase = true) }
            Text(
                text = suburb,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (on) Dash.GoldDeep else Dash.Ground)
                    .border(1.dp, if (on) Dash.Gold.copy(alpha = 0.5f) else Dash.Line, RoundedCornerShape(10.dp))
                    .clickable { onToggle(suburb) }
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = if (on) Dash.Gold else Dash.Muted,
                textDecoration = if (on) null else androidx.compose.ui.text.style.TextDecoration.LineThrough,
            )
        }
    }
}
