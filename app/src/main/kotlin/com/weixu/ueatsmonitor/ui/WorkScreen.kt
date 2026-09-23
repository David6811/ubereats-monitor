package com.weixu.ueatsmonitor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.filled.LocationOn
import com.weixu.ueatsmonitor.action.JobStore
import com.weixu.ueatsmonitor.action.Navigation
import com.weixu.ueatsmonitor.domain.Job
import com.weixu.ueatsmonitor.domain.RulingText
import com.weixu.ueatsmonitor.domain.Words
import com.weixu.ueatsmonitor.domain.Zh
import com.weixu.ueatsmonitor.domain.RuleName
import com.weixu.ueatsmonitor.domain.JobDay
import com.weixu.ueatsmonitor.domain.JobDays
import java.time.LocalDate
import java.time.ZoneId
import com.weixu.ueatsmonitor.domain.Shelf
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
    val words = words()
    val context = LocalContext.current
    var cleared by remember { mutableStateOf(0) }
    // The date is read with the jobs, so the board turns over at midnight on its
    // own - the counts go back to nothing without the screen being touched.
    val zone = remember { ZoneId.systemDefault() }
    val board by rememberPolled(
        JobStore.list(context) to LocalDate.now(zone),
        2_000L,
        cleared,
    ) { JobStore.list(context) to LocalDate.now(zone) }
    val (jobs, today) = board

    // Taken first, because that is the work in hand; the other two are why it is
    // there. A job sits on every shelf that is true of it: what the rules advised
    // and what the driver did are separate facts, so one taken against the advice
    // shows up in both places. The counts add up to more than the board holds.
    val shelves = listOf(Shelf.TAKEN to words.shelfTaken, Shelf.WORTH_TAKING to words.shelfWorth, Shelf.NOT_WORTH_TAKING to words.shelfLeave)
    var shelf by remember { mutableStateOf(Shelf.TAKEN) }
    val todays = JobDays.today(jobs, today, zone)
    val here = todays.filter { shelf.holds(it) }
    val before = JobDays.earlier(jobs.filter { shelf.holds(it) }, today, zone)
    // Folded unless opened: yesterday is for looking something up, not for driving.
    val opened = remember { mutableStateMapOf<LocalDate, Boolean>() }

    Column(Modifier.fillMaxSize()) {
        Segments(
            choices = shelves.map { (which, label) ->
                val count = todays.count { which.holds(it) }
                which to (if (count > 0) "$label  $count" else label)
            },
            chosen = shelf,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        ) { shelf = it }

        // The clear is the last thing in the list, not a bar pinned over it: it is
        // done once the work is finished, and it should cost a scroll to reach.
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (here.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = if (before.isEmpty()) 64.dp else 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(words.empty, style = MaterialTheme.typography.headlineLarge, color = Dash.Line)
                        Text(
                            text = when (shelf) {
                                Shelf.TAKEN -> words.noneTakenToday
                                Shelf.WORTH_TAKING -> words.noneWorthToday
                                Shelf.NOT_WORTH_TAKING -> words.noneLeaveToday
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = Dash.Muted,
                        )
                    }
                }
            }
            items(here, key = { it.atMillis }) { job ->
                JobCard(job, shelf) { JobStore.remove(context, job.atMillis); cleared++ }
            }
            before.forEach { day ->
                val open = opened[day.date] == true
                item(key = "day-" + day.date) {
                    DayHeader(day, today, open) { opened[day.date] = !open }
                }
                if (open) {
                    items(day.jobs, key = { it.atMillis }) { job ->
                        JobCard(job, shelf) { JobStore.remove(context, job.atMillis); cleared++ }
                    }
                }
            }
            if (jobs.isNotEmpty()) {
                item {
                    GhostButton(words.clearBoard, Modifier.fillMaxWidth(), color = Dash.Muted) {
                        JobStore.clear(context); cleared++
                    }
                }
            }
        }
    }
}

/** An earlier day, folded to one line: which day, how many, and a chevron to open it. */
@Composable
private fun DayHeader(day: JobDay, today: LocalDate, open: Boolean, onToggle: () -> Unit) {
    val words = words()
    val name = when (day.date) {
        today.minusDays(1) -> words.yesterday
        today.minusDays(2) -> words.dayBefore
        else -> ""
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Dash.ControlShape)
            .background(Dash.Panel)
            .border(1.dp, Dash.Line, Dash.ControlShape)
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = dayOf(day.date, words),
            style = MaterialTheme.typography.titleMedium.merge(Dash.Numbers),
            color = Dash.Ink,
        )
        if (name.isNotEmpty()) Text(name, style = MaterialTheme.typography.bodyMedium, color = Dash.Muted)
        Spacer(Modifier.weight(1f))
        Text(
            text = day.jobs.size.toString() + "",
            style = MaterialTheme.typography.bodyMedium.merge(Dash.Numbers),
            color = Dash.Muted,
        )
        Text(if (open) "▴" else "▾", style = MaterialTheme.typography.titleMedium, color = Dash.Gold)
    }
}

@Composable
private fun JobCard(job: Job, shelf: Shelf, onClear: () -> Unit) {
    val words = words()
    val context = LocalContext.current
    val advice = job.offer.ruling
    val good = advice != null && RulingText.saysTake(advice)
    var explaining by remember { mutableStateOf(false) }
    // Tight on purpose: two jobs have to fit on one screen, because scrolling to
    // the second delivery is not something to do at a red light.
    if (explaining) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { explaining = false },
            title = { Text(advice.orEmpty()) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val why = job.offer.why.orEmpty()
                    RuleName.of(why)?.let { rule ->
                        Text(words.ruleLine(rule), style = MaterialTheme.typography.bodyMedium, color = Dash.Muted)
                    }
                    Text(why, style = MaterialTheme.typography.titleMedium)
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { explaining = false }) { Text(words.gotIt) }
            },
        )
    }
    Panel(padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), spacing = 8.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = job.offer.payout,
                style = MaterialTheme.typography.titleLarge.merge(Dash.Numbers),
                color = Dash.Gold,
            )
            Text(
                text = CLOCK.format(Date(job.atMillis)),
                style = MaterialTheme.typography.bodyMedium.merge(Dash.Numbers),
                color = Dash.Muted,
            )
            // On the two advice shelves the card cannot say whether he went, and
            // that is the one thing worth reading back off them. On the taken
            // shelf every card would say the same word, so it is left off.
            if (shelf != Shelf.TAKEN) {
                Tag(if (job.taken) words.shelfTaken else words.notTaken, ink = if (job.taken) Dash.Ink else Dash.Muted, ground = Dash.Raised)
            }
            advice?.let {
                // Tapped, it says why: the one question a refused job raises.
                Tag(
                    it,
                    ink = if (good) Dash.Blue else Dash.Orange,
                    ground = if (good) Dash.BlueDeep else Dash.OrangeDeep,
                    modifier = Modifier.clickable(enabled = job.offer.why != null) { explaining = true },
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = words.clearIt,
                modifier = Modifier.clickable(onClick = onClear).padding(vertical = 6.dp, horizontal = 4.dp),
                style = MaterialTheme.typography.labelLarge,
                color = Dash.Muted,
            )
        }

        // Navigate to what the card shows, not to what the card said. Once a
        // screen has given us the real street address it is the better
        // destination in both places, and sending the driver somewhere other
        // than the line he is reading is how he ends up at the wrong door.
        val pickup = job.address ?: job.offer.pickup
        val dropoff = job.dropAddress ?: job.offer.dropoff
        Stop(
            title = words.pickUp,
            // Both, once the pickup screen has given the street address. The
            // shop's name is how he knows which job this is; the address is
            // where he drives. Losing either leaves him guessing.
            place = job.offer.pickup,
            detail = job.address,
            confirmed = job.address != null,
            first = true,
            onDrive = { Navigation.driveTo(context, pickup) },
            onMap = { Navigation.showPlace(context, pickup) },
        )
        Stop(
            title = words.dropOff,
            place = dropoff,
            detail = job.dropUnit,
            confirmed = job.dropAddress != null,
            first = false,
            onDrive = { Navigation.driveTo(context, dropoff) },
            onMap = { Navigation.showPlace(context, dropoff) },
        )
        job.dropNote?.let { Note(words.customerNote, it, job.dropNoteCn) }
        // A batched offer's other deliveries. The card named one destination;
        // the rest turned up on their own delivery screens.
        job.extraDrops.forEach { drop ->
            Stop(
                title = words.alsoDrop,
                place = drop.address,
                detail = drop.unit,
                confirmed = true,
                first = false,
                onDrive = { Navigation.driveTo(context, drop.address) },
                onMap = { Navigation.showPlace(context, drop.address) },
            )
            drop.note?.let { Note(words.customerNote, it, null) }
        }
        // The shop's own words about where to park, which is the one thing no
        // map or table of ours can tell him.
        job.note?.let { Note(words.shopNote, it, job.noteCn) }
    }
}

/**
 * A note, in Chinese where the phone has managed it, with the original underneath.
 * The original stays because it is the authority: a door number or an intercom
 * code inside a translated sentence is not something to trust blind.
 */
@Composable
private fun Note(title: String, original: String, chinese: String?) {
    // The note arrives in English and is translated for a Chinese reader. An
    // English reader wants the note itself, not a translation of it back.
    val translation = chinese.takeIf { words() === Zh }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Dash.GoldDeep.copy(alpha = 0.55f))
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.width(3.dp).height(38.dp).clip(RoundedCornerShape(2.dp)).background(Dash.Gold))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SectionLabel(title, color = Dash.Gold)
            Text(translation ?: original, style = MaterialTheme.typography.bodyLarge, color = Dash.Ink)
            if (translation != null) {
                Text(original, style = MaterialTheme.typography.bodySmall, color = Dash.Muted)
            }
        }
    }
}

/**
 * One stop on the route, one row: a point on a line down the left (a hollow ring
 * for the pickup, a solid dot for the dropoff), the place in the middle, and the
 * two things to do with it on the right, big enough to hit without looking.
 */
@Composable
private fun Stop(
    title: String,
    place: String,
    detail: String?,
    confirmed: Boolean,
    first: Boolean,
    onDrive: () -> Unit,
    onMap: () -> Unit,
) {
    val words = words()
    Row(
        modifier = Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.fillMaxHeight().width(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.weight(1f).width(2.dp).background(if (first) Color.Transparent else Dash.Line))
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(if (first) Color.Transparent else Dash.Blue)
                    .border(2.5.dp, if (first) Dash.Gold else Dash.Blue, CircleShape),
            )
            Box(Modifier.weight(1f).width(2.dp).background(if (first) Dash.Line else Color.Transparent))
        }
        Column(Modifier.weight(1f).padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel(title)
                // Says in words as well as colour that this address came from
                // Uber's own screen rather than from OCR of the offer card.
                if (confirmed) SectionLabel(words.confirmed, color = Dash.Blue)
            }
            Text(
                text = place,
                style = MaterialTheme.typography.titleMedium,
                color = Dash.Ink,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            detail?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = Dash.Muted, maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
        }
        GoldButton(words.drive, Modifier.width(76.dp), onDrive)
        GlyphButton(androidx.compose.material.icons.Icons.Filled.LocationOn, words.seeOnMap, Modifier.width(52.dp), onMap)
    }
}

private val CLOCK = SimpleDateFormat("HH:mm", Locale.US)
/** The date in the driver's language: "9月21日 周一" or "Mon 21 Sep". */
private fun dayOf(date: LocalDate, words: Words): String = date.format(
    java.time.format.DateTimeFormatter.ofPattern(
        words.dayPattern,
        if (words === Zh) Locale.CHINA else Locale.ENGLISH,
    )
)
