package com.weixu.ueatsmonitor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weixu.ueatsmonitor.action.JobStore
import com.weixu.ueatsmonitor.domain.Job
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Where to go right now: the jobs in hand, newest first.
 *
 * A new offer is added rather than made to replace what is there - two
 * deliveries at once is ordinary, and replacing would lose the first one. What
 * clears a job is the driver, because nothing on screen says he accepted it.
 */
@Composable
fun WorkScreen() {
    val context = LocalContext.current
    var cleared by remember { mutableStateOf(0) }
    val jobs by produceState(initialValue = JobStore.list(context), cleared) {
        while (true) {
            value = JobStore.list(context)
            delay(2_000)
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(
            onClick = { JobStore.clear(context); cleared++ },
            modifier = Modifier.fillMaxWidth().height(64.dp),
        ) {
            Text("全部清空", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        if (jobs.isEmpty()) {
            Text("现在没有单", style = MaterialTheme.typography.titleMedium)
            return@Column
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(jobs, key = { it.atMillis }) { job ->
                JobCard(job) { JobStore.remove(context, job.atMillis); cleared++ }
            }
        }
    }
}

@Composable
private fun JobCard(job: Job, onClear: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = CLOCK.format(Date(job.atMillis)) + "  " + job.offer.payout,
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                )
                TextButton(onClick = onClear) { Text("清掉", fontSize = 16.sp) }
            }
            Stop("取货", job.offer.pickup, PICKUP)
            Stop("送到", job.offer.dropoff, DROPOFF)
            job.offer.ruling?.let {
                Text(it, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/**
 * One stop, as a block rather than a caption: which of the two it is has to be
 * readable in the half second the driver can spare for it.
 */
@Composable
private fun Stop(title: String, place: String, tint: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(tint.copy(alpha = 0.18f))
            .padding(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = title,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(tint)
                .padding(horizontal = 10.dp, vertical = 4.dp),
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = place,
            modifier = Modifier.padding(start = 10.dp),
            fontSize = 21.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

private val PICKUP = Color(0xFF00796B)
private val DROPOFF = Color(0xFF5E35B1)

private val CLOCK = SimpleDateFormat("HH:mm", Locale.US)
