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
 * The sets of suburbs: which one is live, and everything that shapes one.
 *
 * All of this used to need the laptop. A driver who has to open a laptop to
 * use this does not use it, so the whole editor is here - the sets, their
 * suburbs, their centres, the shops refused by name, the far threshold and
 * the no-go boxes - done the way a phone is good at rather than the way a
 * mouse and a big map are.
 */
@Composable
fun ProfileScreen() {
    val words = words()
    val context = LocalContext.current
    var profiles by remember { mutableStateOf(Profiles.list(context)) }
    var fromPhone by remember { mutableStateOf(Profiles.chosenHere(context)) }

    val shapes = remember { SuburbShapes.all(context) }
    var excluded: Set<String> by remember { mutableStateOf(ExclusionStore.inForce(context)) }
    // Bumped by every save, which is what makes each panel read the rules again.
    var saves by remember { mutableStateOf(0) }
    val write = rememberRuleWriter {
        saves++
        profiles = Profiles.list(context)
        fromPhone = Profiles.chosenHere(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (profiles.isEmpty()) {
            Panel {
                SectionLabel(words.noAreasYet)
                Text(
                    text = words.pushFromTheWeb,
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
                    SectionLabel(if (fromPhone) words.liveFromPhone else words.liveFromLaptop)
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(live.name, style = MaterialTheme.typography.headlineLarge, color = Dash.Gold)
                        Text(
                            text = words.areasCount(Exclusions.apply(live.suburbs.toSet(), excluded).size),
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

        SetsPanel(write, saves) { name ->
            Profiles.choose(context, name)
            profiles = Profiles.list(context)
            fromPhone = Profiles.chosenHere(context)
            // Switching sets drops the trip's own exclusions, and this is what that looks like.
            excluded = ExclusionStore.inForce(context)
        }

        ShopsPanel(write, saves)
        BoxesPanel(write, saves)
    }
}
