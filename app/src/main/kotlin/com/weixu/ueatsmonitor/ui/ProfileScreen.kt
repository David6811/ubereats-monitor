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
import com.weixu.ueatsmonitor.R
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.Icon
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.isImeVisible
import com.weixu.ueatsmonitor.action.RulesStore
import com.weixu.ueatsmonitor.domain.NoGoBox

/**
 * Picking which set of suburbs is live - the one rule that changes mid-shift.
 *
 * The sets are drawn on the laptop, on the map. Here they are a list you tap.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(onBare: (Boolean) -> Unit = {}) {
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
    // The centre is set once in a while; suburbs are tapped all the time. So
    // the box that sets it waits behind a target rather than standing on the
    // map's share of the screen.
    var settingCentre by remember { mutableStateOf(false) }
    // Drawn on the editor's map, but changed in Settings: a box belongs to the
    // rules, not to any one set.
    val drawnBoxes = remember { RulesStore.drawn(context) }
    var liveCentre by remember { mutableStateOf(Profiles.centre(context)) }
    var saving by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    // Which set is being named, and why. Null while no dialog is up.
    var naming by remember { mutableStateOf<Naming?>(null) }
    // Which row has its three actions open. One at a time, by name.
    var menuFor by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun stopEditing() {
        draft = null
        draftCentre = null
        typed = ""
        found = emptyList()
        settingCentre = false
    }

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
    fun change(
        /** False for an edit that is not about the set being drafted, such as a box. */
        thenStop: Boolean = true,
        what: (kotlinx.serialization.json.JsonObject) -> kotlinx.serialization.json.JsonObject,
    ) {
        saving = true
        scope.launch {
            when (RulesEditor.edit(context, what)) {
                RulesEditor.Saved.Pushed -> note = null
                is RulesEditor.Saved.OnPhoneOnly -> note = words.savedOnPhoneOnly
                RulesEditor.Saved.CloudMovedOn -> { RulesSync.pull(context); note = words.cloudMovedOn }
            }
            if (thenStop) stopEditing()
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

    val live = profiles.firstOrNull { it.active }
    val editingNow = draft

    // Drawing wants the whole glass; leaving the tab gives it back.
    LaunchedEffect(editingNow != null) { onBare(editingNow != null) }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { onBare(false) } }

    // Drawing a set gets the whole screen. It used to be a 380dp window inside
    // a page that scrolls, which is less than half the glass and puts the drag
    // that pans the map in a fight with the drag that scrolls the page.
    if (editingNow != null && live != null) {
        // The keyboard gets the back press first. It did not, and a driver
        // half way through typing a centre lost the whole draft to one tap.
        val keyboardUp = androidx.compose.foundation.layout.WindowInsets.isImeVisible
        val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
        BackHandler {
            if (keyboardUp) keyboard?.hide() else { stopEditing(); note = null }
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Dash.Ground)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(live.name, style = MaterialTheme.typography.headlineSmall, color = Dash.Gold)
                Text(
                    text = words.areasCount(editingNow.size),
                    modifier = Modifier
                        .weight(1f)
                        .padding(bottom = 4.dp),
                    style = MaterialTheme.typography.bodyLarge.merge(Dash.Numbers),
                    color = Dash.Muted,
                )
                Icon(
                    painter = painterResource(R.drawable.ic_centre),
                    contentDescription = words.setCentreHere,
                    tint = if (settingCentre) Dash.Gold else Dash.Muted,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable { settingCentre = !settingCentre }
                        .padding(8.dp),
                )
            }
            if (settingCentre) PlaceSearchBox(
                typed = typed,
                found = found,
                lookingUp = lookingUp,
                onTyped = {
                    typed = it
                    found = PlaceSearch.matching(it, CentreSearch.known(context))
                },
            ) { place ->
                // The place names itself. No reverse lookup, which is what a
                // long press needs and this does not.
                draftCentre = RuleEdits.Centre(place.name, place.at.latitude, place.at.longitude)
                lookAt = place.at
                typed = ""
                found = emptyList()
                note = null
                settingCentre = false
            }
            AreaMap(
                setName = live.name,
                chosen = editingNow,
                boxes = drawnBoxes,
                shapes = shapes,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(Dash.PanelShape),
                centre = draftCentre?.let { GeoPoint(it.latitude, it.longitude) } ?: liveCentre,
                onTap = { suburb -> draft = RuleEdits.toggled(editingNow, suburb) },
                onLongPress = { at -> markCentre(at) },
                lookAt = lookAt,
            )
            note?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Dash.Orange) }
            draftCentre?.let { Text(words.centreNotSaved(it.label), style = MaterialTheme.typography.bodyMedium, color = Dash.Gold) }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Beside the buttons, not on a line of its own. Half of what it
                // used to say is now written on the box above the map.
                Text(
                    text = words.tapSuburbToToggle,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = Dash.Muted,
                )
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
        return
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
                // One line: what is live, how big it is, which of the two places
                // picked it, and the way in. It was four lines, three of which
                // were a heading the map underneath already made obvious.
                Row(
                    modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = live.name,
                        modifier = Modifier.weight(1f, fill = false),
                        style = MaterialTheme.typography.titleLarge,
                        color = Dash.Gold,
                    )
                    // Which of the two places picked it is not said here any
                    // more. It was a whole line, then three words, and it
                    // answered a question nobody was asking at a glance.
                    Text(
                        text = words.areasCount(Exclusions.apply(live.suburbs.toSet(), excluded).size),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium.merge(Dash.Numbers),
                        color = Dash.Muted,
                    )
                    // Both ways of getting a set, side by side: change this one
                    // or start another.
                    SmallButton(words.newSet, gold = false, enabled = !saving) { naming = Naming.New }
                    SmallButton(words.editOnMap, gold = true) { draft = live.suburbs.toSet(); note = null }
                }
                // Read only, and short. It answers "which patch am I running
                // tonight"; drawing one happens on its own page.
                AreaMap(
                    setName = live.name,
                    chosen = Exclusions.apply(live.suburbs.toSet(), excluded),
                    shapes = shapes,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .height(220.dp),
                    centre = liveCentre,
                )
                note?.let {
                    Text(
                        text = it,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Dash.Orange,
                    )
                }
            }
        }

        if (profiles.isNotEmpty()) {
            Panel(padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp, vertical = 14.dp)) {
                SectionLabel(words.switchSet)
                Column {
                    // The one being worked goes first. The order the sets
                    // were written in means nothing to the driver, and the
                    // live one is the row he looks for. Kotlin's sort is
                    // stable, so the rest keep the order they came in.
                    profiles.sortedByDescending { it.active }.forEachIndexed { index, profile ->
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
                            // Behind a dot each, not spelled out under every
                            // row. Spelled out they doubled the height of the
                            // list and said the same three words eight times;
                            // what the list is for is seeing the sets.
                            Box {
                                Text(
                                    text = "\u22ee",
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .clickable(enabled = !saving) { menuFor = profile.name }
                                        .padding(horizontal = 10.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Dash.Muted,
                                )
                                androidx.compose.material3.DropdownMenu(
                                    expanded = menuFor == profile.name,
                                    onDismissRequest = { menuFor = null },
                                    containerColor = Dash.Panel,
                                ) {
                                    androidx.compose.material3.DropdownMenuItem(
                                        text = { Text(words.renameSet, color = Dash.Ink) },
                                        onClick = { menuFor = null; naming = Naming.Rename(profile.name) },
                                    )
                                    androidx.compose.material3.DropdownMenuItem(
                                        text = { Text(words.copySet, color = Dash.Ink) },
                                        onClick = { menuFor = null; naming = Naming.Copy(profile.name) },
                                    )
                                    androidx.compose.material3.DropdownMenuItem(
                                        text = { Text(words.deleteSet, color = Dash.Orange) },
                                        onClick = {
                                            menuFor = null
                                            if (profiles.size <= 1) note = words.lastSetStays
                                            else naming = Naming.Delete(profile.name)
                                        },
                                    )
                                }
                            }
                        }
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
        // Gold, and with a magnifier in front of it. In the panel's own grey it
        // read as a divider: the driver it was built for did not see it.
        androidx.compose.material3.OutlinedTextField(
            value = typed,
            onValueChange = onTyped,
            // The label says what the box does; the placeholder says what may
            // be typed. With a magnifier and the suburb names alone, the box
            // read as a way to find a suburb, not as the way to set a centre.
            label = { Text(words.setCentreHere) },
            placeholder = { Text(words.searchPlace, color = Dash.Muted) },
            leadingIcon = {
                Icon(
                    painter = painterResource(R.drawable.ic_search),
                    contentDescription = null,
                    tint = Dash.Gold,
                )
            },
            singleLine = true,
            shape = Dash.ControlShape,
            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Dash.Gold,
                unfocusedBorderColor = Dash.Gold,
                focusedTextColor = Dash.Ink,
                unfocusedTextColor = Dash.Ink,
                cursorColor = Dash.Gold,
                focusedLabelColor = Dash.Gold,
                unfocusedLabelColor = Dash.Gold,
                focusedContainerColor = Dash.GoldDeep.copy(alpha = 0.35f),
                unfocusedContainerColor = Dash.GoldDeep.copy(alpha = 0.35f),
            ),
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

/** A button that fits beside a line of text: for a panel's own small actions. */
@Composable
internal fun SmallButton(text: String, gold: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
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
