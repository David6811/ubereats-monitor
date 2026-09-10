package com.weixu.ueatsmonitor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.weixu.ueatsmonitor.action.BriefStore
import com.weixu.ueatsmonitor.domain.Brief
import com.weixu.ueatsmonitor.domain.StopBrief
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The laptop's notes on the offer on screen right now.
 *
 * Polled rather than pushed: the file arrives over adb, which the app cannot be
 * told about. Two seconds is well inside how long it takes to read the page.
 */
@Composable
fun BriefScreen() {
    val context = LocalContext.current
    val brief by produceState<Brief?>(initialValue = BriefStore.current(context)) {
        while (true) {
            delay(2_000)
            value = BriefStore.current(context)
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val here = brief
        if (here == null) {
            Text("还没有这一单的建议", fontWeight = FontWeight.Bold)
            Text(
                text = "这一页要电脑在旁边听着才有东西：电脑连上手机热点，跑 tools/copilot/watch.py。" +
                    "没连也不影响接单判断。",
                style = MaterialTheme.typography.bodySmall,
            )
            return@Column
        }

        Text(
            text = "电脑 " + SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(here.atMillis)) + " 给的",
            style = MaterialTheme.typography.labelMedium,
        )
        StopCard("取货", here.pickup)
        StopCard("送到", here.dropoff)
    }
}

@Composable
private fun StopCard(title: String, stop: StopBrief?) {
    if (stop == null) return
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title + " · " + stop.place, fontWeight = FontWeight.Bold)
            Text(stop.advice, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
