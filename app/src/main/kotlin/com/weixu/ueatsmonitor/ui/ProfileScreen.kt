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
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.weixu.ueatsmonitor.action.CentreLabel
import com.weixu.ueatsmonitor.action.CurrentPosition
import com.weixu.ueatsmonitor.action.ExclusionStore
import com.weixu.ueatsmonitor.action.Profiles
import com.weixu.ueatsmonitor.action.RulesEditor
import com.weixu.ueatsmonitor.action.RulesSync
import com.weixu.ueatsmonitor.action.SuburbShapes
import com.weixu.ueatsmonitor.domain.Exclusions
import com.weixu.ueatsmonitor.domain.GeoPoint
import com.weixu.ueatsmonitor.domain.RuleEdits
import kotlinx.coroutines.launch

/**
 * Picking which set of suburbs is live - the one rule that changes mid-shift.
 *
 * The sets are drawn on the laptop, on the map. Here they are a list you tap.
 */
@Composable
fun ProfileScreen() {
    val words = words()
    val context = LocalContext.current
    var profiles by remember { mutableStateOf(Profiles.list(context)) }
    var fromPhone by remember { mutableStateOf(Profiles.chosenHere(context)) }

    val shapes = remember { SuburbShapes.all(context) }
    var excluded: Set<String> by remember { mutableStateOf(ExclusionStore.inForce(context)) }
    // Null until "edit" is pressed: a map that changes the rules at a brush of
    // the thumb is not one to leave under a driver's hand. While editing, taps
    // change only this draft; nothing is written until Save.
    var draft by remember { mutableStateOf<Set<String>?>(null) }
    // A centre marked while editing - by a long press or "my position" - and
    // not yet saved. Null keeps the set's own.
    var draftCentre by remember { mutableStateOf<RuleEdits.Centre?>(null) }
    var liveCentre by remember { mutableStateOf(Profiles.centre(context)) }
    var saving by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun stopEditing() { draft = null; draftCentre = null }

    fun markCentre(at: GeoPoint) {
        scope.launch {
            draftCentre = RuleEdits.Centre(CentreLabel.of(context, at), at.latitude, at.longitude)
        }
    }

    fun save(set: String, suburbs: Set<String>) {
        saving = true
        scope.launch {
            when (RulesEditor.edit(context) { RuleEdits.saveSet(it, set, suburbs.toList(), draftCentre) }) {
                RulesEditor.Saved.Pushed -> { stopEditing(); note = null }
                is RulesEditor.Saved.OnPhoneOnly -> { stopEditing(); note = words.savedOnPhoneOnly }
                // The draft is kept: pressing Save again writes it over what
                // the laptop just saved, now that this phone has seen it.
                RulesEditor.Saved.CloudMovedOn -> { RulesSync.pull(context); note = words.cloudMovedOn }
            }
            profiles = Profiles.list(context)
            liveCentre = Profiles.centre(context)
            saving = false
        }
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
                            text = words.areasCount(draft?.size ?: Exclusions.apply(live.suburbs.toSet(), excluded).size),
                            modifier = Modifier.padding(bottom = 6.dp),
                            style = MaterialTheme.typography.bodyLarge.merge(Dash.Numbers),
                            color = Dash.Muted,
                        )
                    }
                }
                AreaMap(
                    setName = live.name,
                    chosen = draft ?: Exclusions.apply(live.suburbs.toSet(), excluded),
                    shapes = shapes,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .height(380.dp),
                    centre = draftCentre?.let { GeoPoint(it.latitude, it.longitude) } ?: liveCentre,
                    onTap = draft?.let { now -> { suburb: String -> draft = RuleEdits.toggled(now, suburb) } },
                    onLongPress = if (draft != null) { at -> markCentre(at) } else null,
                )
                Column(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    note?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Dash.Orange) }
                    draftCentre?.let { Text(words.centreNotSaved(it.label), style = MaterialTheme.typography.bodyMedium, color = Dash.Gold) }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = if (draft != null) words.tapSuburbToToggle else "",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            color = Dash.Muted,
                        )
                        val editingNow = draft
                        if (editingNow == null) {
                            SmallButton(words.editOnMap, gold = false) { draft = live.suburbs.toSet(); note = null }
                        } else {
                            SmallButton(words.myPosition, gold = false, enabled = !saving) {
                                val fix = CurrentPosition(context).lastKnown()
                                if (fix == null) note = words.noPosition else markCentre(fix.at)
                            }
                            SmallButton(words.cancel, gold = false, enabled = !saving) { stopEditing(); note = null }
                            SmallButton(
                                words.save,
                                gold = true,
                                enabled = !saving && (editingNow != live.suburbs.toSet() || draftCentre != null),
                            ) { save(live.name, editingNow) }
                        }
                    }
                }
            }
        }

        if (profiles.isNotEmpty()) {
            Panel(padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp, vertical = 14.dp)) {
                SectionLabel(words.switchSet)
                Column {
                    profiles.forEachIndexed { index, profile ->
                        if (index > 0) Hairline()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    Profiles.choose(context, profile.name)
                                    stopEditing()
                                    note = null
                                    liveCentre = Profiles.centre(context)
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
                                text = words.areasCount(profile.suburbs.size),
                                style = MaterialTheme.typography.bodyMedium.merge(Dash.Numbers),
                                color = Dash.Muted,
                            )
                        }
                    }
                }
            }
        }

        Panel {
            SectionLabel(words.fewerAreasThisTrip)
            Text(
                text = words.turnThemOffOnTrip,
                style = MaterialTheme.typography.bodyMedium,
                color = Dash.Muted,
            )
        }
    }
}

/** A button that fits beside a line of text: for a panel's own small actions. */
@Composable
private fun SmallButton(text: String, gold: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    val shape = Dash.ControlShape
    val padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp)
    if (gold) {
        androidx.compose.material3.Button(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.height(40.dp),
            shape = shape,
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Dash.Gold, contentColor = Dash.Ground),
            contentPadding = padding,
        ) { Text(text, style = MaterialTheme.typography.labelLarge) }
    } else {
        androidx.compose.material3.OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.height(40.dp),
            shape = shape,
            border = androidx.compose.foundation.BorderStroke(1.dp, Dash.Line),
            colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = Dash.Ink),
            contentPadding = padding,
        ) { Text(text, style = MaterialTheme.typography.labelLarge) }
    }
}
