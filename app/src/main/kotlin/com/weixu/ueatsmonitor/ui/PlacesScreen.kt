package com.weixu.ueatsmonitor.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.weixu.ueatsmonitor.action.CentreLabel
import com.weixu.ueatsmonitor.action.CentreSearch
import com.weixu.ueatsmonitor.action.CurrentPosition
import com.weixu.ueatsmonitor.action.RulesEditor
import com.weixu.ueatsmonitor.action.RulesSync
import com.weixu.ueatsmonitor.domain.Place
import com.weixu.ueatsmonitor.domain.PlaceSearch
import com.weixu.ueatsmonitor.domain.RuleEdits
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

/**
 * The driver's saved places: the addresses every place box offers first.
 *
 * They are the rules' own "places" - the list the laptop's centre picker keeps
 * - so a place saved here shows on the laptop and one saved there shows here.
 * Each change is written and pushed at once: a list of names has no draft
 * worth keeping, unlike a set drawn on a map.
 */
@Composable
fun PlacesScreen(onLeave: () -> Unit) {
    val words = words()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var places by remember { mutableStateOf(RuleEdits.places(RulesEditor.read(context))) }
    var typed by remember { mutableStateOf("") }
    var found by remember { mutableStateOf(emptyList<Place>()) }
    var lookingUp by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }

    BackHandler(onBack = onLeave)

    // Addresses from the phone's geocoder once typing stops, under the
    // suburbs and shops that answer on every keystroke. Saved places are left
    // out: offering what is already in the list would only make a duplicate.
    LaunchedEffect(typed) {
        if (typed.trim().length < 3) { lookingUp = false; return@LaunchedEffect }
        delay(400)
        lookingUp = true
        val offline = PlaceSearch.matching(typed, CentreSearch.known(context))
        val net = CentreSearch.addresses(context, typed)
        found = offline + net.filterNot { address -> offline.any { it.name == address.name } }
        lookingUp = false
    }

    fun change(what: (JsonObject) -> JsonObject) {
        saving = true
        scope.launch {
            note = when (RulesEditor.edit(context, what)) {
                RulesEditor.Saved.Pushed -> null
                is RulesEditor.Saved.OnPhoneOnly -> words.savedOnPhoneOnly
                RulesEditor.Saved.CloudMovedOn -> { RulesSync.pull(context); words.cloudMovedOn }
            }
            places = RuleEdits.places(RulesEditor.read(context))
            saving = false
        }
    }

    fun add(place: RuleEdits.Centre) {
        typed = ""
        found = emptyList()
        change { RuleEdits.addPlace(it, place) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Dash.Ground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(words.savedPlaces, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, color = Dash.Ink)
            SmallButton(words.myPosition, gold = false, enabled = !saving) {
                val fix = CurrentPosition(context).lastKnown()
                if (fix == null) note = words.noPosition
                else scope.launch {
                    add(RuleEdits.Centre(CentreLabel.of(context, fix.at), fix.at.latitude, fix.at.longitude))
                }
            }
            SmallButton(words.back, gold = false) { onLeave() }
        }

        PlaceSearchBox(
            typed = typed,
            found = found,
            lookingUp = lookingUp,
            onTyped = {
                typed = it
                found = PlaceSearch.matching(it, CentreSearch.known(context))
            },
            label = words.addSavedPlace,
        ) { place -> add(RuleEdits.Centre(place.name, place.at.latitude, place.at.longitude)) }

        note?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Dash.Orange) }

        Panel(padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp, vertical = 8.dp)) {
            if (places.isEmpty()) {
                Text(words.noSavedPlacesYet, style = MaterialTheme.typography.bodyMedium, color = Dash.Muted)
            }
            places.forEachIndexed { index, place ->
                if (index > 0) Hairline()
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(place.label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = Dash.Ink)
                    SmallButton(words.renameSet, gold = false, enabled = !saving) { renaming = place.label }
                    SmallButton(words.deleteSet, gold = false, enabled = !saving) { deleting = place.label }
                }
            }
        }
    }

    renaming?.let { label ->
        TypedName(
            title = words.renameSet,
            start = label,
            taken = places.map { it.label } - label,
            onDismiss = { renaming = null },
        ) { typedName ->
            renaming = null
            change { RuleEdits.renamePlace(it, label, typedName) }
        }
    }

    deleting?.let { label ->
        Confirm(
            question = words.reallyDelete(label),
            yes = words.deleteSet,
            onDismiss = { deleting = null },
        ) {
            deleting = null
            change { RuleEdits.removePlace(it, label) }
        }
    }
}
