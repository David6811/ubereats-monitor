package com.weixu.ueatsmonitor.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
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
import com.weixu.ueatsmonitor.action.Chime
import com.weixu.ueatsmonitor.action.RecordingStore
import com.weixu.ueatsmonitor.action.ScreenRecorderService
import com.weixu.ueatsmonitor.action.LoggedEvent
import com.weixu.ueatsmonitor.action.OfferLog
import com.weixu.ueatsmonitor.action.OverlayController
import com.weixu.ueatsmonitor.action.Permissions
import com.weixu.ueatsmonitor.action.SettingsStore
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
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    HomeTabs()
                }
            }
        }
    }
}

@Composable
private fun HomeTabs() {
    var tab by remember { mutableStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("主工作区") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("用哪套") })
            Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("跑单记录") })
            Tab(selected = tab == 3, onClick = { tab = 3 }, text = { Text("设置") })
        }
        when (tab) {
            0 -> WorkScreen()
            1 -> ProfileScreen()
            2 -> CaptureScreen()
            else -> MonitorScreen(App.instance.settingsStore)
        }
    }
}

@Composable
private fun MonitorScreen(store: SettingsStore) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val settings by store.settings.collectAsStateWithLifecycle(initialValue = null)
    val events by OfferLog.events.collectAsStateWithLifecycle()
    val overlay = remember { OverlayController(context) }
    val chime = remember { Chime() }

    val current = settings ?: return

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Only what is broken, and only what the driver actually changes. The
        // notification path was proven dead, and recording, vibration and the
        // debug toggles were things nobody asked for.
        if (!Permissions.screenReadingGranted(context)) {
            item {
                PermissionCard(
                    title = "读屏（无障碍）",
                    granted = false,
                    hint = "唯一能看到派单卡片的通道。关掉就什么都记录不到",
                    onFix = { Permissions.openAccessibilitySettings(context) },
                )
            }
        }

        if (!Permissions.overlayGranted(context)) {
            item {
                PermissionCard(
                    title = "悬浮窗",
                    granted = false,
                    hint = "打开后判断结果会盖在派单卡片上",
                    onFix = { Permissions.openOverlaySettings(context) },
                )
            }
        }

        item {
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ToggleRow("卡片上显示接不接", current.overlayEnabled) {
                        scope.launch { store.setOverlayEnabled(it) }
                    }
                    ToggleRow("区域提示音", current.areaSoundEnabled) {
                        scope.launch { store.setAreaSoundEnabled(it) }
                    }
                    ToggleRow("测试模式（任何 App 的画面都识别）", current.testModeEnabled) {
                        scope.launch { store.setTestModeEnabled(it) }
                    }
                }
            }
        }

        item {
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("规则在电脑上设", fontWeight = FontWeight.Bold)
                    Text(
                        text = "不接哪些店在电脑的规则编辑器里改，保存时推到这台手机。" +
                            "上面那一页可以换用哪一套选区。",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(rulesSummary(context), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
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

/** What the phone is actually running, straight from the pushed file. */
private fun rulesSummary(context: android.content.Context): String {
    val rules = com.weixu.ueatsmonitor.action.RulesStore.current(context)
    if (rules.allowedSuburbs.isEmpty() && rules.deniedStores.isEmpty()) {
        return "还没收到规则文件"
    }
    return "去 " + rules.allowedSuburbs.size + " 个区 · 拉黑 " + rules.deniedStores.size + " 家店"
}

@Composable
private fun PermissionCard(title: String, granted: Boolean, hint: String, onFix: () -> Unit) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = (if (granted) "✅ " else "⛔ ") + title,
                fontWeight = FontWeight.Bold,
            )
            Text(hint, style = MaterialTheme.typography.bodySmall)
            if (!granted) TextButton(onClick = onFix) { Text("去开启") }
        }
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
