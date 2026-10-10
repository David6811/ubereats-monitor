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
import kotlinx.coroutines.delay
import com.weixu.ueatsmonitor.domain.PlaceSearch
import com.weixu.ueatsmonitor.domain.Place
import com.weixu.ueatsmonitor.action.CentreSearch
import androidx.compose.runtime.LaunchedEffect

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
    // What is being typed into the search box, what it found, and whether the
    // network half is still out. Cleared the moment editing stops.
    var typed by remember { mutableStateOf("") }
    var found by remember { mutableStateOf<List<Place>>(emptyList()) }
    var lookingUp by remember { mutableStateOf(false) }
    var lookAt by remember { mutableStateOf<GeoPoint?>(null) }
    var liveCentre by remember { mutableStateOf(Profiles.centre(context)) }
    var saving by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    // Which set is being named, and why. Null while no dialog is up.
    var naming by remember { mutableStateOf<Naming?>(null) }
    val scope = rememberCoroutineScope()

    fun stopEditing() { draft = null; draftCentre = null; typed = ""; found = emptyList() }

    // The bundled suburbs and shops answer on every keystroke; the phone's
    // geocoder is a network call, so it waits for the typing to stop, and its
    // answers join the ones already on screen rather than replacing them.
    LaunchedEffect(typed) {
        if (typed.trim().length < 3) {
            lookingUp = false
            return@LaunchedEffect
        }
        delay(400)
        lookingUp = true
        val offline = PlaceSearch.matching(typed, CentreSearch.known(context))
        val net = CentreSearch.addresses(context, typed)
        found = offline + net.filterNot { address -> offline.any { it.name == address.name } }
        lookingUp = false
    }

    fun markCentre(at: GeoPoint) {
        scope.launch {
            draftCentre = RuleEdits.Centre(CentreLabel.of(context, at), at.latitude, at.longitude)
        }
    }

    /**
     * Making and unmaking sets: the same write path as the map, with the same
     * three answers. A set that disappears under the driver takes the live one
     * with it, so what is live is read again after every one of these.
     */
    fun change(what: (kotlinx.serialization.json.JsonObject) -> kotlinx.serialization.json.JsonObject) {
        saving = true
        scope.launch {
            when (RulesEditor.edit(context, what)) {
                RulesEditor.Saved.Pushed -> note = null
                is RulesEditor.Saved.OnPhoneOnly -> note = words.savedOnPhoneOnly
                RulesEditor.Saved.CloudMovedOn -> { RulesSync.pull(context); note = words.cloudMovedOn }
            }
            stopEditing()
            profiles = Profiles.list(context)
            fromPhone = Profiles.chosenHere(context)
            liveCentre = Profiles.centre(context)
            excluded = ExclusionStore.inForce(context)
            saving = false
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
                // Above the map, not under it. The panel sits near the foot of
                // a scrolling page, and a list of suggestions below the box is
                // a list behind the keyboard.
                if (draft != null) {
                    Box(Modifier.padding(horizontal = 18.dp, vertical = 4.dp)) {
                        PlaceSearchBox(
                            typed = typed,
                            found = found,
                            lookingUp = lookingUp,
                            onTyped = {
                                typed = it
                                found = PlaceSearch.matching(it, CentreSearch.known(context))
                            },
                        ) { place ->
                            // The place names itself. No reverse lookup, which
                            // is what a long press needs and this does not.
                            draftCentre = RuleEdits.Centre(place.name, place.at.latitude, place.at.longitude)
                            lookAt = place.at
                            typed = ""
                            found = emptyList()
                            note = null
                        }
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
                    lookAt = lookAt,
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
                                color = if (profile.active) Dash.Gold else Dash.Ink,
                            )
                            Text(
                                text = words.areasCount(profile.suburbs.size),
                                style = MaterialTheme.typography.bodyMedium.merge(Dash.Numbers),
                                color = Dash.Muted,
                            )
                        }
                        // Under the row, not in it: the row is a big target for
                        // switching sets mid-shift, and these three are not
                        // things to hit by accident at the wheel.
                        Row(
                            modifier = Modifier.padding(start = 36.dp, bottom = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(22.dp),
                        ) {
                            QuietAction(words.renameSet, enabled = !saving) {
                                naming = Naming.Rename(profile.name)
                            }
                            QuietAction(words.copySet, enabled = !saving) {
                                naming = Naming.Copy(profile.name)
                            }
                            QuietAction(words.deleteSet, enabled = !saving) {
                                if (profiles.size <= 1) note = words.lastSetStays
                                else naming = Naming.Delete(profile.name)
                            }
                        }
                    }
                    Hairline()
                    Row(Modifier.padding(top = 10.dp)) {
                        SmallButton(words.newSet, gold = true, enabled = !saving) { naming = Naming.New }
                    }
                }
            }
        }

        naming?.let { what ->
            NameDialog(
                what = what,
                taken = profiles.map { it.name },
                onDismiss = { naming = null },
            ) { typed ->
                naming = null
                when (what) {
                    Naming.New -> change { RuleEdits.addSet(it, typed, null, emptyList()) }
                    is Naming.Rename -> change { RuleEdits.renameSet(it, what.name, typed) }
                    is Naming.Copy -> change { RuleEdits.copySet(it, what.name, typed) }
                    is Naming.Delete -> change { RuleEdits.removeSet(it, what.name) }
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

/**
 * Where to put the centre, by typing its name.
 *
 * The centre used to be a long press on the map and nothing else, which means
 * finding the spot by eye first. A driver who already knows he wants to work
 * out of Aspendale should be able to say so.
 */
@Composable
private fun PlaceSearchBox(
    typed: String,
    found: List<Place>,
    lookingUp: Boolean,
    onTyped: (String) -> Unit,
    onPicked: (Place) -> Unit,
) {
    val words = words()
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        androidx.compose.material3.OutlinedTextField(
            value = typed,
            onValueChange = onTyped,
            placeholder = { Text(words.searchPlace, color = Dash.Muted) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        found.forEach { place ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(Dash.ControlShape)
                    .clickable { onPicked(place) }
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(place.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = when (place.kind) {
                        Place.Kind.SUBURB -> words.kindSuburb
                        Place.Kind.SHOP -> words.kindShop
                        Place.Kind.ADDRESS -> words.kindAddress
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Dash.Muted,
                )
            }
        }
        if (typed.trim().length >= 3) {
            Text(
                text = if (lookingUp) words.searchingNet else if (found.isEmpty()) words.nothingFound else "",
                style = MaterialTheme.typography.bodySmall,
                color = Dash.Muted,
            )
        }
    }
}

/** Data. Which set a dialog is about, and what it will do to it. */
private sealed interface Naming {
    data object New : Naming
    data class Rename(val name: String) : Naming
    data class Copy(val name: String) : Naming
    data class Delete(val name: String) : Naming
}

/**
 * One dialog for all four. Deleting asks for nothing but a yes; the other
 * three want a name, and refuse one that is already taken rather than
 * silently doing nothing, which is what the rules themselves do.
 */
@Composable
private fun NameDialog(
    what: Naming,
    taken: List<String>,
    onDismiss: () -> Unit,
    onDone: (String) -> Unit,
) {
    val words = words()
    var typed by remember {
        mutableStateOf(
            when (what) {
                Naming.New -> ""
                is Naming.Rename -> what.name
                is Naming.Copy -> what.name + " copy"
                is Naming.Delete -> ""
            }
        )
    }
    val deleting = what is Naming.Delete
    val wanted = typed.trim()
    val clash = !deleting && wanted.isNotEmpty() &&
        taken.any { it.equals(wanted, ignoreCase = true) } &&
        !(what is Naming.Rename && wanted.equals(what.name, ignoreCase = true))

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when (what) {
                    Naming.New -> words.newSet
                    is Naming.Rename -> words.renameSet
                    is Naming.Copy -> words.copySet
                    is Naming.Delete -> words.reallyDelete(what.name)
                }
            )
        },
        text = {
            if (!deleting) {
                Column {
                    androidx.compose.material3.OutlinedTextField(
                        value = typed,
                        onValueChange = { typed = it },
                        singleLine = true,
                        isError = clash,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (clash) {
                        Text(words.nameTaken, color = Dash.Orange, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                enabled = deleting || (wanted.isNotEmpty() && !clash),
                onClick = { onDone(wanted) },
            ) { Text(if (deleting) words.deleteSet else words.save) }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text(words.cancel) }
        },
        containerColor = Dash.Panel,
    )
}

/**
 * An action with no box around it, for the three that sit under every set.
 *
 * Boxed, they shouted over the names they belong to - a list of eighteen
 * buttons with a set name somewhere behind them. Small grey words read as what
 * they are: something to do to the line above, once the line has been found.
 */
@Composable
private fun QuietAction(text: String, enabled: Boolean, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (enabled) Dash.Muted else Dash.Line,
        modifier = Modifier
            .clip(Dash.ControlShape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 6.dp),
    )
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
