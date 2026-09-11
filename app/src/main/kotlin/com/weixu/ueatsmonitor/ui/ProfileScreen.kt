package com.weixu.ueatsmonitor.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("用哪套选区", fontWeight = FontWeight.Bold)
        profiles.firstOrNull { it.active }?.let { live ->
            // Two places can pick, so say plainly which one did.
            Text(
                text = "现在用「" + live.name + "」，" +
                    (if (fromPhone) "在这台手机上选的" else "从电脑推过来的"),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (profiles.isEmpty()) {
            Text(
                text = "手机上还没有规则。在电脑的编辑器里点一次「保存并推送到手机」。",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        // Whichever set is live, drawn as shapes so it is obvious which one it is.
        profiles.firstOrNull { it.active }?.let { live ->
            SuburbMap(
                chosen = Exclusions.apply(live.suburbs.toSet(), excluded),
                shapes = shapes,
            )
        }

        profiles.forEach { profile ->
            // The Card overload that takes onClick, not a clickable modifier: the
            // Surface inside a plain Card swallows the touch before it reaches one.
            Card(
                onClick = {
                    Profiles.choose(context, profile.name)
                    profiles = Profiles.list(context)
                    fromPhone = Profiles.chosenHere(context)
                    // Switching sets drops them, and this is what that looks like.
                    excluded = ExclusionStore.inForce(context)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = if (profile.active) {
                    CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                } else {
                    CardDefaults.cardColors()
                },
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = profile.active, onClick = null)
                    Column(Modifier.padding(start = 10.dp)) {
                        Text(profile.name, fontWeight = FontWeight.Bold)
                        Text(
                            text = "去 " + profile.suburbs.size + " 个区",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }

        profiles.firstOrNull { it.active }?.let { live ->
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
    if (excluded.isNotEmpty()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "去掉了 " + excluded.sorted().joinToString("、"),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            TextButton(onClick = onRestore) { Text("恢复") }
        }
    }
    // Across as well as down: a set of thirty suburbs is a long scroll in one
    // column, and each name is short enough to sit beside its neighbour.
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        suburbs.forEach { suburb ->
            val on = excluded.none { it.equals(suburb, ignoreCase = true) }
            Row(
                modifier = Modifier.clickable { onToggle(suburb) }.padding(end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = on, onCheckedChange = null)
                Text(
                    text = suburb,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (on) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
