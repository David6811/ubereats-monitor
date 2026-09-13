package com.weixu.ueatsmonitor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import com.weixu.ueatsmonitor.domain.Job
import com.weixu.ueatsmonitor.domain.Shelf
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

    // Taken first, because that is the work in hand; the other two are why it is
    // there. A job sits on every shelf that is true of it: what the rules advised
    // and what the driver did are separate facts, so one taken against the advice
    // shows up in both places. The counts add up to more than the board holds.
    val shelves = listOf(Shelf.TAKEN to "已接", Shelf.WORTH_TAKING to "建议接", Shelf.NOT_WORTH_TAKING to "建议不接")
    var shelf by remember { mutableStateOf(Shelf.TAKEN) }
    val here = jobs.filter { shelf.holds(it) }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TabRow(selectedTabIndex = shelves.indexOfFirst { it.first == shelf }) {
            shelves.forEach { (which, label) ->
                val count = jobs.count { which.holds(it) }
                Tab(
                    selected = which == shelf,
                    onClick = { shelf = which },
                    text = { Text(if (count > 0) label + " " + count else label) },
                )
            }
        }

        // The clear is the last thing in the list, not a bar pinned over it: it is
        // done once the work is finished, and it should cost a scroll to reach.
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (here.isEmpty()) {
                item {
                    Text(
                        text = when (shelf) {
                            Shelf.TAKEN -> "现在没有已接的单"
                            Shelf.WORTH_TAKING -> "现在没有建议接的单"
                            Shelf.NOT_WORTH_TAKING -> "现在没有建议不接的单"
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
            items(here, key = { it.atMillis }) { job ->
                JobCard(job, shelf) { JobStore.remove(context, job.atMillis); cleared++ }
            }
            item {
                Button(
                    onClick = { JobStore.clear(context); cleared++ },
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                ) {
                    Text("全部清空", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun JobCard(job: Job, shelf: Shelf, onClear: () -> Unit) {
    val context = LocalContext.current
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = CLOCK.format(Date(job.atMillis)) + "  " + job.offer.payout,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                )
                // On the two advice shelves the card cannot say whether he went, and
                // that is the one thing worth reading back off them. On the taken
                // shelf every card would say the same word, so it is left off.
                if (shelf != Shelf.TAKEN) {
                    Text(
                        text = if (job.taken) "已接" else "没接",
                        modifier = Modifier
                            .padding(start = 10.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (job.taken) TAKEN_MARK else UNTAKEN_MARK)
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onClear) { Text("清掉", fontSize = 16.sp) }
            }
            // Navigate to what the card shows, not to what the card said. Once a
            // screen has given us the real street address it is the better
            // destination in both places, and sending the driver somewhere other
            // than the line he is reading is how he ends up at the wrong door.
            val pickup = job.address ?: job.offer.pickup
            val dropoff = job.dropAddress ?: job.offer.dropoff
            Stop(
                title = "取货",
                // Both, once the pickup screen has given the street address. The
                // shop's name is how he knows which job this is; the address is
                // where he drives. Losing either leaves him guessing.
                place = listOfNotNull(job.offer.pickup, job.address).joinToString("\n"),
                mark = if (job.address != null) "已确认" else null,
                tint = PICKUP,
                actions = listOf(
                    StopAction("导航") { Navigation.driveTo(context, pickup) },
                    StopAction("看地图") { Navigation.showPlace(context, pickup) },
                ),
            )
            Stop(
                title = "送到",
                place = listOfNotNull(dropoff, job.dropUnit).joinToString("  "),
                tint = DROPOFF,
                mark = if (job.dropAddress != null) "已确认" else null,
                actions = listOf(
                    StopAction("导航") { Navigation.driveTo(context, dropoff) },
                    StopAction("看地图") { Navigation.showPlace(context, dropoff) },
                ),
            )
            job.dropNote?.let { Note("客户留言", it, job.dropNoteCn) }
            // The shop's own words about where to park, which is the one thing no
            // map or table of ours can tell him.
            job.note?.let { Note("店家留言", it, job.noteCn) }
            job.offer.ruling?.let {
                Text(it, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/**
 * A note, in Chinese where the phone has managed it, with the original underneath.
 * The original stays because it is the authority: a door number or an intercom
 * code inside a translated sentence is not something to trust blind.
 */
@Composable
private fun Note(title: String, original: String, chinese: String?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(NOTE_TINT)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = title + "：" + (chinese ?: original),
            style = MaterialTheme.typography.bodyMedium,
        )
        if (chinese != null) {
            Text(
                text = original,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
private fun Stop(
    title: String,
    place: String,
    tint: Color,
    actions: List<StopAction>,
    mark: String? = null,
) {
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
            // Says in words as well as colour that this address came from Uber's
            // own pickup screen rather than from OCR of the offer card.
            mark?.let {
                Text(
                    text = it,
                    modifier = Modifier
                        .padding(start = 6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(CONFIRMED)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
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
private val NOTE_TINT = Color(0x33B38600)

/** Blue, never green: the driver cannot tell green from red. */
private val CONFIRMED = Color(0xFF0A6ECF)

/** Went / did not go. Green and grey rather than green and red: not going is not a fault. */
private val TAKEN_MARK = Color(0xFF2E7D32)
private val UNTAKEN_MARK = Color(0xFF757575)
private val DROPOFF = Color(0xFF5E35B1)

private val CLOCK = SimpleDateFormat("HH:mm", Locale.US)
