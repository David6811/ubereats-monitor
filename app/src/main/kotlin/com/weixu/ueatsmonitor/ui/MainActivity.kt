package com.weixu.ueatsmonitor.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.weixu.ueatsmonitor.App
import com.weixu.ueatsmonitor.domain.Missing
import com.weixu.ueatsmonitor.domain.Watch
import com.weixu.ueatsmonitor.domain.WatchJudge
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.animateFloat
import android.app.Activity
import android.content.Context
import android.media.projection.MediaProjectionManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.weixu.ueatsmonitor.action.CaptureKeeperService
import com.weixu.ueatsmonitor.action.Cloud
import com.weixu.ueatsmonitor.action.LiveSettings
import com.weixu.ueatsmonitor.domain.Lang
import com.weixu.ueatsmonitor.domain.Words
import com.weixu.ueatsmonitor.domain.Zh
import com.weixu.ueatsmonitor.domain.wordsIn
import com.weixu.ueatsmonitor.action.RulesSync
import io.github.jan.supabase.auth.status.SessionStatus
import com.weixu.ueatsmonitor.action.CaptureStatus
import com.weixu.ueatsmonitor.action.Chime
import com.weixu.ueatsmonitor.action.CurrentPosition
import com.weixu.ueatsmonitor.action.RecordingStore
import com.weixu.ueatsmonitor.action.ScreenRecorderService
import com.weixu.ueatsmonitor.action.LoggedEvent
import com.weixu.ueatsmonitor.action.OfferLog
import com.weixu.ueatsmonitor.action.OverlayController
import com.weixu.ueatsmonitor.action.Permissions
import com.weixu.ueatsmonitor.action.Profiles
import com.weixu.ueatsmonitor.action.SettingsStore
import com.weixu.ueatsmonitor.action.UberScreenService
import com.weixu.ueatsmonitor.action.VoiceService
import com.weixu.ueatsmonitor.domain.AreaCall
import com.weixu.ueatsmonitor.domain.Cents
import com.weixu.ueatsmonitor.domain.GeoPoint
import com.weixu.ueatsmonitor.domain.Suburb
import com.weixu.ueatsmonitor.domain.Miles
import com.weixu.ueatsmonitor.domain.OfferEvaluator
import com.weixu.ueatsmonitor.domain.OfferParser
import com.weixu.ueatsmonitor.domain.ParseResult
import com.weixu.ueatsmonitor.domain.RawNotification
import com.weixu.ueatsmonitor.domain.Thresholds
import com.weixu.ueatsmonitor.domain.Verdict
import com.weixu.ueatsmonitor.domain.VerdictText
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private var askedNotifications = false

    private val askNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                android.widget.Toast.makeText(
                    this,
                    wordsIn(LiveSettings.current?.lang ?: Lang.CHINESE).notificationsOff,
                    android.widget.Toast.LENGTH_LONG,
                ).show()
            }
        }

    override fun onResume() {
        super.onResume()
        // Opening the app is the moment the driver expects it to be current.
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) { RulesSync.pull(this@MainActivity) }
        // Asked whenever the app is opened afresh, so a permission lost in the
        // background is noticed. Once per activity: the permission dialog itself
        // pauses and resumes this screen, and a refusal would otherwise loop.
        if (!askedNotifications && !Permissions.postNotificationsGranted(this)) {
            askedNotifications = true
            askNotifications.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        // Brings back the keeper notification if it was swiped away - but never
        // after a deliberate quit, which leaves screen reading off. The keeper
        // exists to serve the reader; without one there is nothing to keep alive.
        if (Permissions.screenReadingGranted(this)) {
            runCatching { CaptureKeeperService.start(this) }
        }
        // The microphone service dies with the process (a reinstall, a reboot) and
        // may only be started while the app is in front, so opening the app is
        // what brings it back.
        // Read from the store, not the live copy: straight after a reinstall the
        // live copy is still empty when the first screen resumes. Voice follows
        // its own switch and nothing else.
        if (Permissions.microphoneGranted(this)) {
            lifecycleScope.launch {
                if (App.instance.settingsStore.settings.first().voiceEnabled) {
                    runCatching { VoiceService.start(this@MainActivity) }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DashTheme {
                val settings by App.instance.settingsStore.settings.collectAsStateWithLifecycle(initialValue = null)
                androidx.compose.runtime.CompositionLocalProvider(
                    LocalWords provides wordsIn(settings?.lang ?: Lang.CHINESE)
                ) {
                    Surface(modifier = Modifier.fillMaxSize(), color = Dash.Ground) {
                        Gate()
                    }
                }
            }
        }
    }
}

/**
 * Data. The three places in the app, in the order of the bar along the bottom.
 *
 * The recorded frames are still written and still pruned; they are read on the
 * laptop, where a shift is actually looked into, so the phone no longer carries
 * a page for them.
 */
private enum class Place(val glyph: androidx.compose.ui.graphics.vector.ImageVector) {
    WORK(Icons.Filled.Home),
    TRIP(Icons.Filled.Check),
    AREAS(Icons.Filled.LocationOn),
    SETTINGS(Icons.Filled.Settings),
    ;

    fun label(words: Words): String = when (this) {
        WORK -> words.tabWork
        TRIP -> words.tabTrip
        AREAS -> words.tabAreas
        SETTINGS -> words.tabSettings
    }
}

/**
 * The words every screen reads, set once from the settings and taken from the
 * air by whatever needs them. A screen that wants a phrase asks [words]; it
 * never holds a Chinese sentence of its own.
 */
val LocalWords = androidx.compose.runtime.staticCompositionLocalOf<Words> { Zh }

@Composable
fun words(): Words = LocalWords.current

/**
 * The login screen until the driver is signed in, then the app. While the
 * saved session is still being read nothing is drawn, so the login screen does
 * not flash past on every open.
 */
@Composable
private fun Gate() {
    val session by Cloud.session.collectAsStateWithLifecycle()
    when (session) {
        is SessionStatus.Authenticated -> HomeTabs()
        is SessionStatus.NotAuthenticated -> LoginScreen()
        is SessionStatus.Initializing, is SessionStatus.RefreshFailure -> Unit
    }
}

/**
 * The frame: what is running along the top, the page in the middle, and the four
 * places along the bottom where a thumb reaches them while the phone is in its
 * cradle.
 */
@Composable
private fun HomeTabs() {
    var place by remember { mutableStateOf(Place.WORK) }
    // Bumped by 刷新: the page is built again from scratch, reading the rules
    // file as it is now rather than as it was when the page was opened.
    var reload by remember { mutableStateOf(0) }
    val words = words()
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val refresh: () -> Unit = {
        scope.launch {
            val outcome = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { RulesSync.pull(context) }
            say(context, when (outcome) {
                RulesSync.Outcome.Updated -> words.rulesUpdated
                RulesSync.Outcome.Unchanged -> words.rulesAlreadyCurrent
                is RulesSync.Outcome.Failed -> words.couldNotFetch(outcome.why)
                else -> words.refreshed
            })
            reload++
        }
    }
    Column(Modifier.fillMaxSize().background(Dash.Ground)) {
        StatusStrip(onRefresh = refresh)
        Box(Modifier.weight(1f)) {
            androidx.compose.runtime.key(reload) {
                when (place) {
                    Place.WORK -> WorkScreen()
                    Place.TRIP -> TripScreen()
                    Place.AREAS -> ProfileScreen()
                    Place.SETTINGS -> MonitorScreen(App.instance.settingsStore)
                }
            }
        }
        BottomBar(place) { place = it }
    }
}

/**
 * The name of the app, the live set, and underneath one bar that says whether an
 * offer arriving now would get a verdict. Told by words, a tick or a cross, and
 * dark against bright - never by hue alone, because the driver cannot tell red
 * from green.
 */
@Composable
private fun StatusStrip(onRefresh: () -> Unit) {
    val context = LocalContext.current
    val settings by App.instance.settingsStore.settings.collectAsStateWithLifecycle(initialValue = null)
    val live by rememberPolled(Pair<Watch, String>(Watch.Watching, ""), 2_000L) {
        Pair(
            WatchJudge.judge(
                readerGranted = Permissions.screenReadingGranted(context),
                sinceLastFrameMillis = System.currentTimeMillis() - CaptureStatus.lastFrameAtMillis,
                overlayGranted = Permissions.overlayGranted(context),
            ),
            Profiles.list(context).firstOrNull { it.active }?.name.orEmpty(),
        )
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 16.dp, top = 14.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(words().appName, style = MaterialTheme.typography.titleMedium, color = Dash.Ink)
        Spacer(Modifier.weight(1f))
        settings?.let { VoiceChip(it.voiceEnabled) }
        WatchLamp(live.first)
        // The live set's name doubles as the refresh: tap it to fetch the rules
        // again and rebuild the page, in no room of its own.
        if (live.second.isNotEmpty()) {
            Tag("↻ " + live.second, ink = Dash.Gold, ground = Dash.GoldDeep, modifier = Modifier.clickable(onClick = onRefresh))
        }
    }
}

/**
 * Quiet words while watching; a bright, pulsing, tappable block while not. The
 * difference is carried by the tick or cross, the words and dark against bright.
 */
@Composable
private fun WatchLamp(watch: Watch) {
    val context = LocalContext.current
    when (watch) {
        Watch.Watching -> Text(
            text = words().watching,
            style = MaterialTheme.typography.bodyMedium,
            color = Dash.Muted,
        )
        is Watch.NotWatching -> {
            val hint = hintFor(watch.what)
            val pulse = androidx.compose.animation.core.rememberInfiniteTransition(label = "pulse")
            val alpha by pulse.animateFloat(
                initialValue = 1f,
                targetValue = 0.55f,
                animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                    animation = androidx.compose.animation.core.tween<Float>(700),
                    repeatMode = androidx.compose.animation.core.RepeatMode.Reverse,
                ),
                label = "alpha",
            )
            Text(
                text = words().notWatching,
                style = MaterialTheme.typography.bodyMedium,
                color = Dash.Ground,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .graphicsLayer { this.alpha = alpha }
                    .clip(Dash.ControlShape)
                    .background(Dash.Gold)
                    .clickable {
                        say(context, hint)
                        fix(context, watch.what)
                    }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

/** What to switch on once the system page opens, in the words that page shows. */
@Composable
private fun hintFor(missing: Missing): String = when (missing) {
    Missing.READER -> words().turnOnReader
    Missing.READER_STALLED -> words().readerStalled
    Missing.OVERLAY -> words().allowOverlay
}

private fun fix(context: Context, missing: Missing) = when (missing) {
    Missing.READER, Missing.READER_STALLED -> Permissions.openAccessibilitySettings(context)
    Missing.OVERLAY -> Permissions.openOverlaySettings(context)
}

@Composable
private fun BottomBar(current: Place, onChoose: (Place) -> Unit) {
    val words = words()
    Column {
        Hairline()
        Row(
            modifier = Modifier.fillMaxWidth().background(Dash.Ground).padding(vertical = 8.dp),
        ) {
            Place.entries.forEach { place ->
                val on = place == current
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onChoose(place) }
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (on) Dash.GoldDeep else androidx.compose.ui.graphics.Color.Transparent)
                            .padding(horizontal = 18.dp, vertical = 4.dp),
                    ) {
                        androidx.compose.material3.Icon(
                            imageVector = place.glyph,
                            contentDescription = place.label(words),
                            tint = if (on) Dash.Gold else Dash.Muted,
                        )
                    }
                    Text(
                        text = place.label(words),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (on) Dash.Gold else Dash.Muted,
                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

@Composable
private fun MonitorScreen(store: SettingsStore) {
    val words = words()
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val settings by store.settings.collectAsStateWithLifecycle(initialValue = null)

    val current = settings ?: return

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Only what is broken, and only what the driver actually changes. The
        // notification path was proven dead, and recording, vibration and the
        // debug toggles were things nobody asked for.
        if (!Permissions.screenReadingGranted(context)) {
            item {
                PermissionCard(
                    title = words.readerOff,
                    hint = words.readerOffHint,
                    onFix = { Permissions.openAccessibilitySettings(context) },
                )
            }
        }

        if (!Permissions.overlayGranted(context)) {
            item {
                PermissionCard(
                    title = words.overlayOff,
                    hint = words.overlayOffHint,
                    onFix = { Permissions.openOverlaySettings(context) },
                )
            }
        }

        item {
            Panel(padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp, vertical = 14.dp)) {
                SectionLabel(words.settingsWatching)
                Column {
                    // Named with its own numbers, because the threshold and the
                    // size of the far set are the laptop's, not this app's.
                    Profiles.far(context)?.let { far ->
                        SwitchRow(
                            label = "超过 $" + far.overDollars + " 用远区",
                            hint = far.suburbs.toString() + " 个区，每小时不够也不接",
                            checked = current.farEnabled,
                        ) { scope.launch { store.setFarEnabled(it) } }
                        Hairline()
                    }
                    HomewardToggle(current.homewardEnabled) { scope.launch { store.setHomewardEnabled(it) } }
                    if (current.homewardEnabled) {
                        HomewardLimitsRow(current) { near, max -> scope.launch { store.saveHomewardLimits(near, max) } }
                    }
                    Hairline()
                    NearCentreToggle(current.nearCentreEnabled) { scope.launch { store.setNearCentreEnabled(it) } }
                    if (current.nearCentreEnabled) {
                        LimitsRow(
                            kmLabel = words.withinKmLabel,
                            minutesLabel = words.withinMinutesLabel,
                            km = current.nearCentreMaxKm,
                            minutes = current.nearCentreMaxMinutes,
                        ) { km, max -> scope.launch { store.saveNearCentreLimits(km, max) } }
                    }
                    Hairline()
                    SwitchRow(words.areaSound, null, current.areaSoundEnabled) {
                        scope.launch { store.setAreaSoundEnabled(it) }
                    }
                    Hairline()
                    VoiceToggle(current.voiceEnabled)
                    Hairline()
                    SwitchRow(words.floatingButtons, words.floatingButtonsHint, current.toolsEnabled) {
                        scope.launch { store.setToolsEnabled(it) }
                    }
                    Hairline()
                    LanguageRow(current.lang) { scope.launch { store.setLang(it) } }
                }
            }
        }

        item {
            TripCostCard(current) { fuel, factor, floor ->
                scope.launch { store.saveTripCost(fuel, factor, floor) }
            }
        }

        item { QuitCard() }
    }
}

/**
 * The two numbers the homeward rule bends on: a drop this near the centre is
 * taken even if it leads away, and a job longer than this is left however near.
 */
@Composable
private fun HomewardLimitsRow(settings: SettingsStore.Settings, onSave: (Double, Int) -> Unit) {
    val words = words()
    LimitsRow(
        kmLabel = words.nearKmLabel,
        minutesLabel = words.maxMinutesLabel,
        km = settings.homewardNearKm,
        minutes = settings.homewardMaxMinutes,
        onSave = onSave,
    )
}

/** A kilometres field and a minutes field with one save button, for the rules that bend on two numbers. */
@Composable
private fun LimitsRow(kmLabel: String, minutesLabel: String, km: Double, minutes: Int, onSave: (Double, Int) -> Unit) {
    val words = words()
    var kmText by remember { mutableStateOf("") }
    var minutesText by remember { mutableStateOf("") }

    LaunchedEffect(km, minutes) {
        kmText = km.toString()
        minutesText = minutes.toString()
    }

    Column(
        modifier = Modifier.padding(bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ButtonRow {
            DashField(kmLabel, kmText, Modifier.weight(1f)) { kmText = it }
            DashField(minutesLabel, minutesText, Modifier.weight(1f)) { minutesText = it }
        }
        GoldButton(words.save, Modifier.fillMaxWidth()) {
            onSave(
                kmText.toDoubleOrNull()?.takeIf { it >= 0 } ?: km,
                minutesText.toIntOrNull()?.takeIf { it > 0 } ?: minutes,
            )
        }
    }
}

/**
 * The near-centre switch. It needs only a centre drawn for the live set - not
 * the car's position, since it judges the drop alone - so that is the one
 * thing it can be missing.
 */
@Composable
private fun NearCentreToggle(enabled: Boolean, save: (Boolean) -> Unit) {
    val words = words()
    val context = LocalContext.current
    val centre = remember { Profiles.centre(context) }
    SwitchRow(
        label = words.nearCentreMode,
        hint = if (centre == null) words.noCentreSet else words.nearCentreHint,
        checked = enabled,
    ) { on ->
        when {
            !on -> save(false)
            centre == null -> say(context, words.noCentreSetLong)
            else -> save(true)
        }
    }
}

/** Chinese or English, for every word the app says. Both names are written in their own language. */
@Composable
private fun LanguageRow(lang: Lang, onChoose: (Lang) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("语言 · Language", style = MaterialTheme.typography.titleMedium, color = Dash.Ink)
        }
        listOf(Lang.CHINESE to "中文", Lang.ENGLISH to "English").forEach { (which, name) ->
            val on = which == lang
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium,
                color = if (on) Dash.Ground else Dash.Ink,
                fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .clip(Dash.ControlShape)
                    .background(if (on) Dash.Gold else Dash.Raised)
                    .clickable { onChoose(which) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

/**
 * The homeward switch, which will not go on while it could do nothing.
 *
 * It needs three things: the phone's own position, a centre drawn for the live
 * set, and the permission to read that position. Missing any of them, the rule
 * would judge nothing at all - so the switch says what is missing instead of
 * turning on and staying silent.
 */
@Composable
private fun HomewardToggle(enabled: Boolean, save: (Boolean) -> Unit) {
    val words = words()
    val context = LocalContext.current
    val centre = remember { Profiles.centre(context) }
    val fix = remember { CurrentPosition(context).lastKnown() }
    val askLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val now = if (granted) CurrentPosition(context).lastKnown() else null
        when {
            !granted -> say(context, "没有定位权限，回中心模式开不了")
            now == null -> say(context, "还没有定位，到车里开着定位再试")
            !Permissions.backgroundLocationGranted(context) -> askForAllTheTime(context)
            centre != null -> save(true)
        }
    }

    SwitchRow(
        label = words.homewardMode,
        hint = when {
            centre == null -> words.noCentreSet
            !Permissions.backgroundLocationGranted(context) -> words.needAlwaysLocation
            fix == null -> words.noFixYet
            else -> words.homewardHint
        },
        checked = enabled,
    ) { on ->
        when {
            !on -> save(false)
            centre == null ->
                android.widget.Toast.makeText(context, words.noCentreSetLong, android.widget.Toast.LENGTH_LONG).show()
            !Permissions.locationGranted(context) ->
                askLocation.launch(android.Manifest.permission.ACCESS_FINE_LOCATION)
            // Uber is in front while a card is judged, so "while using the app"
            // hands back nothing. Only the system settings page can change that.
            !Permissions.backgroundLocationGranted(context) -> askForAllTheTime(context)
            CurrentPosition(context).lastKnown() == null ->
                say(context, "手机还没有定位，到车里开着定位再试")
            else -> save(true)
        }
    }
}

private fun say(context: Context, words: String) {
    android.widget.Toast.makeText(context, words, android.widget.Toast.LENGTH_LONG).show()
}

private fun askForAllTheTime(context: Context) {
    say(context, Zh.setLocationAlways)
    Permissions.openAppSettings(context)
}

/**
 * The voice switch. Turning it on asks for the microphone first; the service is
 * started and stopped here, while the app is on screen, because Android refuses
 * to open the microphone for a service started from the background.
 */
@Composable
private fun VoiceToggle(enabled: Boolean) {
    val words = words()
    val switchVoice = rememberVoiceSwitch()
    var explaining by remember { mutableStateOf(false) }
    SwitchRow(words.voiceCommands, words.voiceCommandsHint, enabled, switchVoice)
    TextButton(onClick = { explaining = true }) { Text(words.whatCanISay, color = Dash.Gold) }

    if (explaining) {
        AlertDialog(
            onDismissRequest = { explaining = false },
            title = { Text(words.voiceCommands) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    voiceHelp().forEach { (say, does) ->
                        Row {
                            Text(say, Modifier.weight(1f), fontWeight = FontWeight.Bold)
                            Text(does, Modifier.weight(1.4f))
                        }
                    }
                    Text(
                        words.voiceHelpTail,
                        color = Dash.Muted,
                    )
                }
            },
            confirmButton = { TextButton(onClick = { explaining = false }) { Text(words.gotIt) } },
        )
    }
}

/**
 * Turns voice on or off, the same way from the settings page and the top of the
 * app: on asks for the microphone first; the service is started and stopped
 * here, while the app is on screen, because Android refuses to open the
 * microphone for a service started from the background.
 */
@Composable
private fun rememberVoiceSwitch(): (Boolean) -> Unit {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val store = App.instance.settingsStore
    val askMicrophone = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            scope.launch { store.setVoiceEnabled(true) }
            VoiceService.start(context)
        } else {
            say(context, "没有麦克风权限，语音命令开不了")
        }
    }
    return { on ->
        if (!on) {
            scope.launch { store.setVoiceEnabled(false) }
            VoiceService.stop(context)
        } else if (Permissions.microphoneGranted(context)) {
            scope.launch { store.setVoiceEnabled(true) }
            VoiceService.start(context)
        } else {
            askMicrophone.launch(android.Manifest.permission.RECORD_AUDIO)
        }
    }
}

/**
 * Voice on or off, at the top where a thumb finds it while driving. Said in
 * words and by bright against dim, never by hue alone.
 */
@Composable
private fun VoiceChip(enabled: Boolean) {
    val switchVoice = rememberVoiceSwitch()
    Text(
        text = if (enabled) words().voiceOn else words().voiceOff,
        style = MaterialTheme.typography.bodyMedium,
        color = if (enabled) Dash.Ink else Dash.Muted,
        fontWeight = if (enabled) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier
            .clip(Dash.ControlShape)
            .border(1.dp, if (enabled) Dash.Gold else Dash.Line, Dash.ControlShape)
            .clickable { switchVoice(!enabled) }
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

/**
 * What each spoken command does. The words to say stay Mandarin whatever the
 * language is set to: the recogniser on the phone knows no English.
 */
@Composable
private fun voiceHelp(): List<Pair<String, String>> {
    val words = words()
    return listOf(
        "「地图」" to words.sayMap,
        "「送餐」" to words.sayUber,
        "「应用」" to words.sayApp,
        "「回中心」" to words.sayCentre,
        "「关导航」" to words.sayStopNavigation,
        "「你好，…」" to words.sayAsk,
        "「关语音」" to words.sayVoiceOff,
    )
}

/**
 * Everything off at the end of a shift. Kept to the bottom of the last page and
 * behind a question, because getting back from it means a trip through the
 * system's accessibility settings - no app can grant itself that again.
 */
@Composable
private fun QuitCard() {
    val words = words()
    val context = LocalContext.current
    val activity = context as? Activity
    var asking by remember { mutableStateOf(false) }

    Panel {
        SectionLabel(words.settingsFinishing)
        Text(
            text = words.quitHint,
            style = MaterialTheme.typography.bodyMedium,
            color = Dash.Muted,
        )
        GhostButton(words.quitAndStop, Modifier.fillMaxWidth(), color = Dash.Orange) { asking = true }
        val scope = androidx.compose.runtime.rememberCoroutineScope()
        GhostButton(words.signOut, Modifier.fillMaxWidth()) {
            scope.launch {
                App.instance.settingsStore.forgetLogin()
                runCatching { Cloud.signOut() }.onFailure { say(context, "退不出去：" + it.message) }
            }
        }
    }

    if (asking) {
        AlertDialog(
            onDismissRequest = { asking = false },
            title = { Text(words.quitAsk) },
            text = { Text(words.quitAskBody) },
            confirmButton = {
                TextButton(onClick = {
                    asking = false
                    UberScreenService.stopEverything(context)
                    activity?.finish()
                }) { Text(words.quitIt) }
            },
            dismissButton = {
                TextButton(onClick = { asking = false }) { Text(words.cancel) }
            },
        )
    }
}

/** Screen recording: a switch, what it is costing, and a way to throw it away. */
@Composable
private fun RecordingCard(recording: Boolean, store: SettingsStore) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val recordings = remember { RecordingStore(context) }
    var reloads by remember { mutableStateOf(0) }
    val usage = remember(reloads, recording) {
        val files = recordings.segments()
        files.size to files.sumOf { it.length() }
    }

    val consent = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            ScreenRecorderService.start(context, result.resultCode, data)
            scope.launch { store.setRecordScreenEnabled(true) }
        } else {
            scope.launch { store.setRecordScreenEnabled(false) }
        }
        reloads++
    }

    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ToggleRow("跑单时录屏", recording) { wanted ->
                if (wanted) {
                    val manager = context.getSystemService(MediaProjectionManager::class.java)
                    consent.launch(manager.createScreenCaptureIntent())
                } else {
                    ScreenRecorderService.stop(context)
                    scope.launch { store.setRecordScreenEnabled(false) }
                    reloads++
                }
            }
            Text(
                text = "720×1600 · 实测每小时约 1.1 GB。超过 16 GB 才自动删最老的一段 —— 跑 3 小时约 3.4 GB，删不到。",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = "现在占用 " + megabytes(usage.second) + "，共 " + usage.first + " 段",
                fontWeight = FontWeight.Bold,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { reloads++ }) { Text("刷新") }
                TextButton(onClick = { recordings.deleteAll(); reloads++ }) { Text("删除全部录屏") }
            }
            Text(
                text = recordings.folder,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

private fun megabytes(bytes: Long): String =
    if (bytes >= 1024L * 1024 * 1024) {
        String.format("%.2f GB", bytes / 1024.0 / 1024.0 / 1024.0)
    } else {
        String.format("%.0f MB", bytes / 1024.0 / 1024.0)
    }

@Composable
private fun PermissionCard(title: String, hint: String, onFix: () -> Unit) {
    Panel(Modifier.border(1.dp, Dash.Orange, Dash.PanelShape)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Lamp(true, Dash.Orange)
            Text(title, style = MaterialTheme.typography.titleMedium, color = Dash.Orange)
        }
        Text(hint, style = MaterialTheme.typography.bodyMedium, color = Dash.Muted)
        GoldButton("去开启", Modifier.fillMaxWidth(), onFix)
    }
}

@Composable
private fun ThresholdCard(thresholds: Thresholds, onSave: (Thresholds) -> Unit) {
    val words = words()
    var payout by remember { mutableStateOf("") }
    var perMile by remember { mutableStateOf("") }
    var perHour by remember { mutableStateOf("") }
    var maxMiles by remember { mutableStateOf("") }

    LaunchedEffect(thresholds) {
        payout = thresholds.minPayout.dollars.toString()
        perMile = thresholds.minPayPerMile.toString()
        perHour = thresholds.minPayPerHour.toString()
        maxMiles = thresholds.maxDistance.value.toString()
    }

    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("我的底线", fontWeight = FontWeight.Bold)
            NumberField("最低总价 $", payout) { payout = it }
            NumberField("最低 $/mi", perMile) { perMile = it }
            NumberField("最低 $/h", perHour) { perHour = it }
            NumberField("最远 mi", maxMiles) { maxMiles = it }
            Button(
                onClick = {
                    onSave(
                        Thresholds(
                            minPayout = Cents.ofDollars(payout.toDoubleOrNull() ?: thresholds.minPayout.dollars),
                            minPayPerMile = perMile.toDoubleOrNull() ?: thresholds.minPayPerMile,
                            minPayPerHour = perHour.toDoubleOrNull() ?: thresholds.minPayPerHour,
                            maxDistance = Miles(maxMiles.toDoubleOrNull() ?: thresholds.maxDistance.value),
                        )
                    )
                },
            ) { Text(words.save) }
        }
    }
}

/**
 * The driver's petrol and time reckoning. Every card shows the hour it works out
 * to; only an offer let through by the far set is refused for falling short.
 */
@Composable
private fun TripCostCard(settings: SettingsStore.Settings, onSave: (Double, Double, Double) -> Unit) {
    val words = words()
    var fuel by remember { mutableStateOf("") }
    var factor by remember { mutableStateOf("") }
    var floor by remember { mutableStateOf("") }

    LaunchedEffect(settings.fuelPerKm, settings.timeFactor, settings.farMinPerHour) {
        fuel = settings.fuelPerKm.toString()
        factor = settings.timeFactor.toString()
        floor = settings.farMinPerHour.toString()
    }

    Panel {
        SectionLabel(words.settingsPerHour)
        Text(
            text = words.perHourFormula,
            style = MaterialTheme.typography.bodyMedium.merge(Dash.Numbers),
            color = Dash.Muted,
        )
        DashField(words.fuelPerKm, fuel) { fuel = it }
        ButtonRow {
            DashField(words.timeFactor, factor, Modifier.weight(1f)) { factor = it }
            DashField(words.farFloorPerHour, floor, Modifier.weight(1f)) { floor = it }
        }
        GoldButton(words.save, Modifier.fillMaxWidth()) {
            onSave(
                fuel.toDoubleOrNull() ?: settings.fuelPerKm,
                factor.toDoubleOrNull()?.takeIf { it > 0 } ?: settings.timeFactor,
                floor.toDoubleOrNull() ?: settings.farMinPerHour,
            )
        }
    }
}

@Composable
private fun NumberField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun EventRow(event: LoggedEvent) {
    Card {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = TIME_FORMAT.format(Date(event.raw.postedAtMillis)) + "  " + event.raw.packageName,
                style = MaterialTheme.typography.labelSmall,
            )
            val verdict = event.verdict
            if (verdict != null) {
                Text(VerdictText.headline(verdict), fontWeight = FontWeight.Bold)
                Text(VerdictText.metricsLine(verdict.metrics))
                Text(VerdictText.reason(verdict), style = MaterialTheme.typography.bodySmall)
            } else {
                Text(describe(event.result), style = MaterialTheme.typography.bodySmall)
            }
            Text(event.raw.body, style = MaterialTheme.typography.labelSmall)
        }
    }
}

private fun describe(result: ParseResult): String = when (result) {
    is ParseResult.Parsed -> "已解析"
    is ParseResult.Unreadable -> "读不出：" + result.reason
    ParseResult.NotAnOffer -> "不是派单"
}

/** A fixed sample so the overlay can be checked without waiting for a real order. */
private fun sampleVerdict(thresholds: Thresholds): Verdict {
    val sample = RawNotification(
        packageName = OfferParser.UBER_DRIVER_PACKAGE,
        title = "New delivery request",
        text = "\$8.25 · 3.4 mi · 18 min · from McDonald's",
        postedAtMillis = 0L,
    )
    val parsed = OfferParser.parse(sample) as ParseResult.Parsed
    return OfferEvaluator.evaluate(parsed.offer, thresholds)
}

private val SAMPLE_INSIDE = AreaCall.AllInside(
    listOf(Suburb("Keysborough", GeoPoint(-38.0054, 145.1674))),
)
private val SAMPLE_OUTSIDE = AreaCall.SomeOutside(
    outside = listOf(Suburb("Springvale", GeoPoint(-37.9456, 145.158))),
    inside = emptyList(),
)

private val TIME_FORMAT = SimpleDateFormat("HH:mm:ss", Locale.US)
