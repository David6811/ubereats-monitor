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
import android.app.Activity
import android.content.Context
import android.media.projection.MediaProjectionManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.weixu.ueatsmonitor.action.CaptureKeeperService
import com.weixu.ueatsmonitor.action.Chime
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
                    "通知权限没开：常驻通知和上面的两个按钮不会出现",
                    android.widget.Toast.LENGTH_LONG,
                ).show()
            }
        }

    override fun onResume() {
        super.onResume()
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
        // live copy is still empty when the first screen resumes.
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
                Surface(modifier = Modifier.fillMaxSize(), color = Dash.Ground) {
                    HomeTabs()
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
private enum class Place(val label: String, val glyph: androidx.compose.ui.graphics.vector.ImageVector) {
    WORK("工作", Icons.Filled.Home),
    TRIP("这趟", Icons.Filled.Check),
    AREAS("选区", Icons.Filled.LocationOn),
    SETTINGS("设置", Icons.Filled.Settings),
}

/**
 * The frame: what is running along the top, the page in the middle, and the four
 * places along the bottom where a thumb reaches them while the phone is in its
 * cradle.
 */
@Composable
private fun HomeTabs() {
    var place by remember { mutableStateOf(Place.WORK) }
    Column(Modifier.fillMaxSize().background(Dash.Ground)) {
        StatusStrip()
        Box(Modifier.weight(1f)) {
            when (place) {
                Place.WORK -> WorkScreen()
                Place.TRIP -> TripScreen()
                Place.AREAS -> ProfileScreen()
                Place.SETTINGS -> MonitorScreen(App.instance.settingsStore)
            }
        }
        BottomBar(place) { place = it }
    }
}

/** Is it watching, is it listening, which set is live - the three things worth a glance. */
@Composable
private fun StatusStrip() {
    val context = LocalContext.current
    val settings by App.instance.settingsStore.settings.collectAsStateWithLifecycle(initialValue = null)
    val live by rememberPolled(Pair(false, ""), 2_000L) {
        Pair(
            Permissions.screenReadingGranted(context),
            Profiles.list(context).firstOrNull { it.active }?.name.orEmpty(),
        )
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 56.dp, top = 14.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("接单助手", style = MaterialTheme.typography.titleMedium, color = Dash.Ink)
        Spacer(Modifier.weight(1f))
        LampLabel("读屏", live.first, Dash.Blue)
        LampLabel("语音", settings?.voiceEnabled == true, Dash.Gold)
        if (live.second.isNotEmpty()) {
            Tag(live.second, ink = Dash.Gold, ground = Dash.GoldDeep)
        }
    }
}

@Composable
private fun LampLabel(label: String, on: Boolean, color: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Lamp(on, color)
        Text(label, style = MaterialTheme.typography.bodySmall, color = if (on) Dash.Ink else Dash.Muted)
    }
}

@Composable
private fun BottomBar(current: Place, onChoose: (Place) -> Unit) {
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
                            contentDescription = place.label,
                            tint = if (on) Dash.Gold else Dash.Muted,
                        )
                    }
                    Text(
                        text = place.label,
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
                    title = "读屏没开",
                    hint = "唯一能看到派单卡片的通道。关掉就什么都记录不到",
                    onFix = { Permissions.openAccessibilitySettings(context) },
                )
            }
        }

        if (!Permissions.overlayGranted(context)) {
            item {
                PermissionCard(
                    title = "悬浮窗没开",
                    hint = "打开后判断结果会盖在派单卡片上",
                    onFix = { Permissions.openOverlaySettings(context) },
                )
            }
        }

        item {
            Panel(padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp, vertical = 14.dp)) {
                SectionLabel("监控")
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
                    SwitchRow("每 2 秒截屏", "关掉就读不到派单卡片", current.timedCaptureEnabled) {
                        scope.launch { store.setTimedCaptureEnabled(it) }
                    }
                    Hairline()
                    SwitchRow("区域提示音", null, current.areaSoundEnabled) {
                        scope.launch { store.setAreaSoundEnabled(it) }
                    }
                    Hairline()
                    VoiceToggle(current.voiceEnabled) { scope.launch { store.setVoiceEnabled(it) } }
                    Hairline()
                    SwitchRow("测试模式", "任何 App 的画面都识别", current.testModeEnabled) {
                        scope.launch { store.setTestModeEnabled(it) }
                    }
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
 * The voice switch. Turning it on asks for the microphone first; the service is
 * started and stopped here, while the app is on screen, because Android refuses
 * to open the microphone for a service started from the background.
 */
@Composable
private fun VoiceToggle(enabled: Boolean, save: (Boolean) -> Unit) {
    val context = LocalContext.current
    val askMicrophone = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            save(true)
            VoiceService.start(context)
        } else {
            android.widget.Toast.makeText(context, "没有麦克风权限，语音命令开不了", android.widget.Toast.LENGTH_LONG).show()
        }
    }
    SwitchRow("语音命令", "一直在听：说「地图」「送餐」「应用」", enabled) { on ->
        if (!on) {
            save(false)
            VoiceService.stop(context)
        } else if (Permissions.microphoneGranted(context)) {
            save(true)
            VoiceService.start(context)
        } else {
            askMicrophone.launch(android.Manifest.permission.RECORD_AUDIO)
        }
    }
}

/**
 * Everything off at the end of a shift. Kept to the bottom of the last page and
 * behind a question, because getting back from it means a trip through the
 * system's accessibility settings - no app can grant itself that again.
 */
@Composable
private fun QuitCard() {
    val context = LocalContext.current
    val activity = context as? Activity
    var asking by remember { mutableStateOf(false) }

    Panel {
        SectionLabel("收工")
        Text(
            text = "停掉读屏和后台守护。下次要用，得去「系统设置 → 无障碍 → 接单助手」重新打开。",
            style = MaterialTheme.typography.bodyMedium,
            color = Dash.Muted,
        )
        GhostButton("退出并停止监控", Modifier.fillMaxWidth(), color = Dash.Orange) { asking = true }
    }

    if (asking) {
        AlertDialog(
            onDismissRequest = { asking = false },
            title = { Text("停掉监控？") },
            text = { Text("派单来了就不会再有判断和记录，直到你在系统的无障碍设置里重新打开。") },
            confirmButton = {
                TextButton(onClick = {
                    asking = false
                    UberScreenService.stopEverything(context)
                    activity?.finish()
                }) { Text("停掉") }
            },
            dismissButton = {
                TextButton(onClick = { asking = false }) { Text("取消") }
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
            ) { Text("保存") }
        }
    }
}

/**
 * The driver's petrol and time reckoning. Every card shows the hour it works out
 * to; only an offer let through by the far set is refused for falling short.
 */
@Composable
private fun TripCostCard(settings: SettingsStore.Settings, onSave: (Double, Double, Double) -> Unit) {
    var fuel by remember { mutableStateOf("") }
    var factor by remember { mutableStateOf("") }
    var floor by remember { mutableStateOf("") }

    LaunchedEffect(settings.fuelPerKm, settings.timeFactor, settings.farMinPerHour) {
        fuel = settings.fuelPerKm.toString()
        factor = settings.timeFactor.toString()
        floor = settings.farMinPerHour.toString()
    }

    Panel {
        SectionLabel("每小时收入")
        Text(
            text = "(钱 − 公里 × 2 × 油钱) ÷ (分钟 × 倍数 ÷ 60)",
            style = MaterialTheme.typography.bodyMedium.merge(Dash.Numbers),
            color = Dash.Muted,
        )
        DashField("每公里油钱 $", fuel) { fuel = it }
        ButtonRow {
            DashField("时间倍数", factor, Modifier.weight(1f)) { factor = it }
            DashField("远区最低 $/时", floor, Modifier.weight(1f)) { floor = it }
        }
        GoldButton("保存", Modifier.fillMaxWidth()) {
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
