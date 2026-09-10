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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import com.weixu.ueatsmonitor.action.Navigation
import com.weixu.ueatsmonitor.action.StoreTable
import com.weixu.ueatsmonitor.domain.Store
import com.weixu.ueatsmonitor.domain.StoreKinds
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
    val stores = remember { StoreTable.all(context) }
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
                JobCard(job, stores) { JobStore.remove(context, job.atMillis); cleared++ }
            }
        }
    }
}

@Composable
private fun JobCard(job: Job, stores: List<Store>, onClear: () -> Unit) {
    val context = LocalContext.current
    val shop = remember(job.offer.pickup, stores) { StoreKinds.find(job.offer.pickup, stores) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = CLOCK.format(Date(job.atMillis)) + "  " + job.offer.payout,
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                )
                TextButton(onClick = onClear) { Text("清掉", fontSize = 16.sp) }
            }
            Stop(
                title = "取货",
                place = job.offer.pickup,
                tint = PICKUP,
                actions = listOfNotNull(
                    StopAction("导航") { Navigation.driveTo(context, job.offer.pickup) },
                    // Street View wants a point, and only a shop we know has one.
                    shop?.let { found ->
                        StopAction("街景") {
                            Navigation.streetView(context, found.at.latitude, found.at.longitude)
                        }
                    },
                ),
            )
            Stop(
                title = "送到",
                place = job.offer.dropoff,
                tint = DROPOFF,
                actions = listOf(
                    StopAction("导航") { Navigation.driveTo(context, job.offer.dropoff) },
                    StopAction("看地图") { Navigation.showOnMap(context, job.offer.dropoff) },
                ),
            )
            job.offer.ruling?.let {
                Text(it, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** One thing that can be done with a stop, as its own tap target. */
private data class StopAction(val label: String, val run: () -> Unit)

/**
 * One stop, as a block rather than a caption: which of the two it is has to be
 * readable in the half second the driver can spare for it, and what can be done
 * with it has to be hittable without looking.
 */
@Composable
private fun Stop(title: String, place: String, tint: Color, actions: List<StopAction>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(tint.copy(alpha = 0.18f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
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
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            actions.forEach { action ->
                Button(
                    onClick = action.run,
                    modifier = Modifier.weight(1f).height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = tint,
                        // Without this the label takes the theme's colour, which on
                        // these two fills is nearly the fill itself.
                        contentColor = Color.White,
                    ),
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Text(action.label, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private val PICKUP = Color(0xFF00796B)
private val DROPOFF = Color(0xFF5E35B1)

private val CLOCK = SimpleDateFormat("HH:mm", Locale.US)
