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
import com.weixu.ueatsmonitor.action.Chime
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
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("跑单记录") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("设置") })
        }
        when (tab) {
            0 -> CaptureScreen()
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
        item { Text("Uber Eats 接单助手", style = MaterialTheme.typography.headlineSmall) }

        item {
            PermissionCard(
                title = "通知访问",
                granted = Permissions.notificationAccessGranted(context),
                hint = "打开后才能读到 Uber Driver 的派单通知",
                onFix = { Permissions.openNotificationAccessSettings(context) },
            )
        }

        item {
            PermissionCard(
                title = "读屏（无障碍）",
                granted = Permissions.screenReadingGranted(context),
                hint = "唯一能看到派单卡片的通道。关掉就什么都记录不到",
                onFix = { Permissions.openAccessibilitySettings(context) },
            )
        }

        item {
            PermissionCard(
                title = "悬浮窗",
                granted = Permissions.overlayGranted(context),
                hint = "打开后判断结果会盖在 Uber 界面上",
                onFix = { Permissions.openOverlaySettings(context) },
            )
        }

        item {
            ThresholdCard(
                thresholds = current.thresholds,
                onSave = { scope.launch { store.saveThresholds(it) } },
            )
        }

        item {
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ToggleRow("区域提示音", current.areaSoundEnabled) {
                        scope.launch { store.setAreaSoundEnabled(it) }
                    }
                    ToggleRow("弹悬浮窗", current.overlayEnabled) {
                        scope.launch { store.setOverlayEnabled(it) }
                    }
                    ToggleRow("震动提示", current.vibrateEnabled) {
                        scope.launch { store.setVibrateEnabled(it) }
                    }
                    ToggleRow("记录所有 App 的通知（调参用）", current.logEveryNotification) {
                        scope.launch { store.setLogEveryNotification(it) }
                    }
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { chime.play(SAMPLE_INSIDE) }) { Text("试听：区内") }
                    Button(onClick = { chime.play(SAMPLE_OUTSIDE) }) { Text("试听：区外") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { overlay.show(sampleVerdict(current.thresholds), "McDonald's → Downtown") }) {
                        Text("试一下悬浮窗")
                    }
                    TextButton(onClick = { OfferLog.clear() }) { Text("清空记录") }
                }
            }
        }

        item { Text("最近 ${events.size} 条", style = MaterialTheme.typography.titleMedium) }

        items(events) { event -> EventRow(event) }
    }
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
