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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Read-back of what the screen recorder saved during a shift. */
@Composable
fun CaptureScreen() {
    val context = LocalContext.current
    val store = remember { CaptureStore(context) }
    var reloads by remember { mutableIntStateOf(0) }
    var moneyOnly by remember { mutableStateOf(false) }
    var openName by remember { mutableStateOf<String?>(null) }

    val captures by produceState(initialValue = emptyList<CaptureStore.Capture>(), reloads) {
        value = withContext(Dispatchers.IO) { store.list() }
    }
    val shown = captures.filter { !moneyOnly || CaptureText.hasMoney(it.body) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("只看含金额的", modifier = Modifier.weight(1f))
                Switch(checked = moneyOnly, onCheckedChange = { moneyOnly = it })
            }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${shown.size} / ${captures.size} 张",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                )
                TextButton(onClick = { reloads++ }) { Text("刷新") }
                TextButton(onClick = { store.deleteAll(); reloads++ }) { Text("全部删除") }
            }
        }

        if (shown.isEmpty()) {
            item {
                Text(
                    text = if (captures.isEmpty()) {
                        "还没有记录。打开 Uber Driver 跑一单，界面变化会自动存下来。"
                    } else {
                        "这 ${captures.size} 张里没有带金额的，关掉上面的开关看全部。"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        items(shown, key = { it.name }) { capture ->
            CaptureCard(
                capture = capture,
                open = openName == capture.name,
                onToggle = { openName = if (openName == capture.name) null else capture.name },
            )
        }
    }
}

@Composable
private fun CaptureCard(capture: CaptureStore.Capture, open: Boolean, onToggle: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(CLOCK.format(Date(capture.atMillis)), style = MaterialTheme.typography.labelMedium)
            Text(CaptureText.previewOf(capture.body), fontWeight = FontWeight.Bold)

            if (open) {
                Text(capture.body, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                capture.imagePath?.let { Screenshot(it) }
            } else {
                Text("点开看全文和截图", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
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
