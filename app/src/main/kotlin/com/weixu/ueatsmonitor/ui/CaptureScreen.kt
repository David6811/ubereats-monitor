package com.weixu.ueatsmonitor.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Switch
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
    var everything by remember { mutableStateOf(false) }
    var openName by remember { mutableStateOf<String?>(null) }

    val suburbs = remember { Gazetteer.suburbs(context) }
    val hereNow = remember(reloads) { CurrentPosition(context).lastKnown()?.at }

    val shown by produceState(initialValue = emptyList<CaptureStore.Capture>(), reloads, everything) {
        value = withContext(Dispatchers.IO) {
            if (everything) store.list() else store.listOffers()
        }
    }

    val recording = remember(reloads) { Permissions.screenReadingGranted(context) }
    val journal = remember(reloads) { ServiceJournal.read(context) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (!recording) {
            item {
                Card {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("⛔ 读屏没开，这一趟什么都不会记录", fontWeight = FontWeight.Bold)
                        Text(
                            text = "系统会在重装或某些更新后把它关掉。去「设置 → 无障碍 → 接单助手」打开。",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        TextButton(onClick = { Permissions.openAccessibilitySettings(context) }) {
                            Text("去开启")
                        }
                    }
                }
            }
        }

        if (journal.isNotEmpty()) {
            item {
                Text(
                    text = "读屏状态：" + journal.first(),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("连没识别出派单的画面一起看", modifier = Modifier.weight(1f))
                Switch(checked = everything, onCheckedChange = { everything = it })
            }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (everything) "" + shown.size + " 张画面" else "" + shown.size + " 单",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                )
                TextButton(onClick = { reloads++ }) { Text("刷新") }
                TextButton(onClick = { scope.launch(Dispatchers.IO) { store.deleteAll(); reloads++ } }) { Text("全部删除") }
            }
        }

        if (shown.isEmpty()) {
            item {
                Text(
                    text = if (everything) {
                        "还没有记录。打开 Uber Driver 跑一单，界面变化会自动存下来。"
                    } else {
                        "还没有识别到派单。打开上面的开关能看到全部画面。"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
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
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(CLOCK.format(Date(capture.atMillis)), style = MaterialTheme.typography.labelMedium)
            val offer = capture.offer
            if (offer == null) {
                Text(CaptureText.previewOf(capture.body), fontWeight = FontWeight.Bold)
            } else {
                Text(
                    text = (offer.ruling ?: "没判") + "  " + offer.payout,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(offer.pickup + "  →  " + offer.dropoff, style = MaterialTheme.typography.bodyMedium)
                offer.why?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
            }

            if (open) {
                Directions(capture, suburbs, hereNow)
                Text(capture.body, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                capture.imagePath?.let { Screenshot(it) }
            } else {
                Text("点开看全文和截图", style = MaterialTheme.typography.labelSmall)
            }
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
