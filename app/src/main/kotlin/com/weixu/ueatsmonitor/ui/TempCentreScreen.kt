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
import com.weixu.ueatsmonitor.action.TempCentreStore
import com.weixu.ueatsmonitor.domain.GeoPoint
import com.weixu.ueatsmonitor.domain.Place
import com.weixu.ueatsmonitor.domain.PlaceSearch
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The line under "work around the centre": which point today is worked
 * around, and the way to change it or go back to the set's own.
 */
@Composable
internal fun TempCentreRow(onChange: () -> Unit) {
    val words = words()
    val context = LocalContext.current
    var today by remember { mutableStateOf(TempCentreStore.today(context)) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(words.tempCentre, style = MaterialTheme.typography.bodyLarge, color = Dash.Ink)
            Text(
                text = today?.let { words.tempCentreToday(it.label) } ?: words.tempCentreNone,
                style = MaterialTheme.typography.bodySmall,
                color = if (today != null) Dash.Gold else Dash.Muted,
            )
        }
        if (today != null) {
            SmallButton(words.useSetCentre, gold = false) {
                TempCentreStore.clear(context)
                today = null
            }
        }
        SmallButton(words.changeTempCentre, gold = false, onClick = onChange)
    }
}

/**
 * Picking today's temporary centre: typed, chosen from the saved places and
 * the sets' own centres, or where the phone is. Picking one is the save - it
 * is one point, with nothing to draft.
 */
@Composable
fun TempCentreScreen(onLeave: () -> Unit) {
    val words = words()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Saved places first, then each set's centre: the list "pick one" means.
    val mine = remember { CentreSearch.saved(context) + CentreSearch.setCentres(context) }
    var typed by remember { mutableStateOf("") }
    var found by remember { mutableStateOf(mine) }
    var lookingUp by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }

    BackHandler(onBack = onLeave)

    LaunchedEffect(typed) {
        if (typed.trim().length < 3) { lookingUp = false; return@LaunchedEffect }
        delay(400)
        lookingUp = true
        val offline = PlaceSearch.offered(typed, mine, CentreSearch.known(context))
        val net = CentreSearch.addresses(context, typed)
        found = offline + net.filterNot { address -> offline.any { it.name == address.name } }
        lookingUp = false
    }

    fun use(label: String, at: GeoPoint) {
        TempCentreStore.set(context, label, at)
        onLeave()
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
            Text(words.tempCentre, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, color = Dash.Ink)
            SmallButton(words.myPosition, gold = false) {
                val fix = CurrentPosition(context).lastKnown()
                if (fix == null) note = words.noPosition
                else scope.launch { use(CentreLabel.of(context, fix.at), fix.at) }
            }
            SmallButton(words.back, gold = false) { onLeave() }
        }
        note?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Dash.Orange) }
        PlaceSearchBox(
            typed = typed,
            found = found,
            lookingUp = lookingUp,
            onTyped = {
                typed = it
                found = PlaceSearch.offered(it, mine, CentreSearch.known(context))
            },
            label = words.pickTempCentre,
        ) { place: Place -> use(place.name, place.at) }
    }
}
