package com.weixu.ueatsmonitor.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.weixu.ueatsmonitor.action.CaptureStore
import com.weixu.ueatsmonitor.action.CaptureText
import com.weixu.ueatsmonitor.action.CurrentPosition
import com.weixu.ueatsmonitor.action.Gazetteer
import com.weixu.ueatsmonitor.action.Permissions
import com.weixu.ueatsmonitor.action.ServiceJournal
import com.weixu.ueatsmonitor.domain.AreaCall
import com.weixu.ueatsmonitor.domain.AreaJudge
import com.weixu.ueatsmonitor.domain.Geo
import com.weixu.ueatsmonitor.domain.GeoPoint
import com.weixu.ueatsmonitor.domain.ServiceArea
import com.weixu.ueatsmonitor.domain.Suburb
import com.weixu.ueatsmonitor.domain.SuburbIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Read-back of what the screen recorder saved during a shift. */
@Composable
fun CaptureScreen() {
    val context = LocalContext.current
    val store = remember { CaptureStore(context) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var reloads by remember { mutableIntStateOf(0) }
    var openName by remember { mutableStateOf<String?>(null) }

    val suburbs = remember { Gazetteer.suburbs(context) }
    val hereNow = remember(reloads) { CurrentPosition(context).lastKnown()?.at }

    val shown by produceState(initialValue = emptyList<CaptureStore.Capture>(), reloads) {
        value = withContext(Dispatchers.IO) { store.listOffers() }
    }

    val recording = remember(reloads) { Permissions.screenReadingGranted(context) }
    val journal = remember(reloads) { ServiceJournal.read(context) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!recording) {
            item {
                Panel(Modifier.border(1.dp, Dash.Orange, Dash.PanelShape)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Lamp(true, Dash.Orange)
                        Text("读屏没开，这一趟什么都不会记录", style = MaterialTheme.typography.titleMedium, color = Dash.Orange)
                    }
                    Text(
                        text = "系统会在重装或某些更新后把它关掉。去「设置 → 无障碍 → 接单助手」打开。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Dash.Muted,
                    )
                    GoldButton(words().goTurnOn, Modifier.fillMaxWidth()) { Permissions.openAccessibilitySettings(context) }
                }
            }
        }

        item {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    SectionLabel("识别到的派单")
                    Text(
                        text = shown.size.toString(),
                        style = MaterialTheme.typography.headlineLarge.merge(Dash.Numbers),
                        color = Dash.Gold,
                    )
                }
                Text(
                    text = "刷新",
                    modifier = Modifier.clickable { reloads++ }.padding(10.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = Dash.Gold,
                )
                Text(
                    text = "全部删除",
                    modifier = Modifier.clickable { scope.launch(Dispatchers.IO) { store.deleteAll(); reloads++ } }.padding(10.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = Dash.Muted,
                )
            }
        }

        if (journal.isNotEmpty()) {
            item {
                Text(
                    text = "读屏 · " + journal.first(),
                    style = MaterialTheme.typography.bodySmall.merge(Dash.Numbers),
                    color = Dash.Muted,
                )
            }
        }

        if (shown.isEmpty()) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 64.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("空", style = MaterialTheme.typography.headlineLarge, color = Dash.Line)
                    Text("还没有识别到派单", style = MaterialTheme.typography.bodyLarge, color = Dash.Muted)
                }
            }
        }

        items(shown, key = { it.name }) { capture ->
            CaptureCard(
                capture = capture,
                open = openName == capture.name,
                suburbs = suburbs,
                hereNow = hereNow,
                onToggle = { openName = if (openName == capture.name) null else capture.name },
            )
        }
    }
}

@Composable
private fun CaptureCard(
    capture: CaptureStore.Capture,
    open: Boolean,
    suburbs: List<Suburb>,
    hereNow: GeoPoint?,
    onToggle: () -> Unit,
) {
    Panel(Modifier.clickable(onClick = onToggle), padding = PaddingValues(16.dp)) {
        val offer = capture.offer
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = CLOCK.format(Date(capture.atMillis)),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium.merge(Dash.Numbers),
                color = Dash.Muted,
            )
            offer?.ruling?.let { ruling ->
                val good = ruling.startsWith("可以")
                Tag(ruling, ink = if (good) Dash.Blue else Dash.Orange, ground = if (good) Dash.BlueDeep else Dash.OrangeDeep)
            }
        }
        offer?.let {
            Text(it.payout, style = MaterialTheme.typography.titleLarge.merge(Dash.Numbers), color = Dash.Gold)
            Text(it.pickup + "  →  " + it.dropoff, style = MaterialTheme.typography.bodyLarge, color = Dash.Ink)
            it.why?.let { why -> Text(why, style = MaterialTheme.typography.bodySmall, color = Dash.Muted) }
        }

        if (open) {
            Hairline()
            Directions(capture, suburbs, hereNow)
            Text(capture.body, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, color = Dash.Muted)
            capture.imagePath?.let { Screenshot(it) }
        } else {
            SectionLabel("点开看全文和截图")
        }
    }
}

/**
 * Which way the places named on this screen lie, measured from where the car was
 * when the offer appeared. Only when that was never recorded does it fall back to
 * the phone's position right now, and then it says so.
 */
@Composable
private fun Directions(capture: CaptureStore.Capture, suburbs: List<Suburb>, hereNow: GeoPoint?) {
    val found = remember(capture.name, suburbs) { SuburbIndex.findAll(capture.body, suburbs) }
    if (found.isEmpty()) return

    val recorded = capture.recordedAt
    val origin = recorded ?: hereNow

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = when (val call = AreaJudge.call(found, ServiceArea.SOUTH_EAST)) {
                is AreaCall.AllInside -> "✅ 全在区内"
                is AreaCall.SomeOutside -> "⚠ 区外：" + call.outside.joinToString("、") { it.name }
                AreaCall.NoSuburb -> ""
            },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = when {
                recorded != null -> "从派单时车的位置起算" + staleness(capture.fixAgeMillis)
                origin != null -> "⚠ 这条没记下当时的位置，用的是你现在的位置"
                else -> "⚠ 没有位置，只能看两地之间的方向"
            },
            style = MaterialTheme.typography.labelSmall,
        )

        origin?.let { from ->
            found.forEach { suburb ->
                val heading = Geo.headingTo(from, suburb.at)
                Text(
                    text = "%s  %s %.0f°  直线 %.1f mi".format(
                        suburb.name,
                        heading.compass.label,
                        heading.bearingDegrees,
                        heading.straightLine.value,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        if (found.size >= 2) {
            val first = found[0]
            val second = found[1]
            val leg = Geo.headingTo(second.at, first.at)
            Text(
                text = "%s → %s  %s %.1f mi".format(
                    second.name, first.name, leg.compass.label, leg.straightLine.value,
                ),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/** A fix minutes old was taken somewhere else; say how old rather than hide it. */
private fun staleness(fixAgeMillis: Long?): String {
    val minutes = fixAgeMillis?.div(60_000L) ?: return ""
    return if (minutes < 2) "" else "（定位比派单早 $minutes 分钟）"
}

@Composable
private fun Screenshot(path: String) {
    // Half resolution is plenty for reading an offer card back and keeps memory small.
    val bitmap = remember(path) {
        val options = BitmapFactory.Options().apply { inSampleSize = 2 }
        runCatching { BitmapFactory.decodeFile(path, options) }.getOrNull()
    } ?: return

    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = null,
        modifier = Modifier.fillMaxWidth(),
    )
}

private val CLOCK = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US)
