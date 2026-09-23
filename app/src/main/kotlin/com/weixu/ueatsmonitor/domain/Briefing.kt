package com.weixu.ueatsmonitor.domain

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Calculation. What the phone knows right now, written out for the assistant
 * to read before it answers a question: the time, where the car is, the jobs
 * in hand and the last few offers. Plain lines, no JSON - a model reads prose
 * better and the driver may one day read this back too.
 *
 * Nothing here decides anything; it only describes.
 */
object Briefing {

    data class Whereabouts(
        val carAt: GeoPoint?,
        val centre: GeoPoint?,
        val nearestSuburb: String?,
    )

    fun text(
        nowMillis: Long,
        jobs: List<Job>,
        where: Whereabouts,
        profileName: String?,
        lang: Lang = Lang.CHINESE,
        offersToMention: Int = 4,
    ): String {
        val zh = lang == Lang.CHINESE
        return buildString {
            append(if (zh) "时间 " else "Time ")
            append(day(nowMillis, zh)).append(" ").append(CLOCK.format(Date(nowMillis)))
            append(if (zh) "，墨尔本\n" else ", Melbourne\n")
            append(whereLine(where, zh)).append('\n')
            if (!profileName.isNullOrBlank()) {
                append(if (zh) "当前选区：" else "Live set: ").append(profileName).append('\n')
            }

            // The board keeps every job it ever saw; only the recent ones can still
            // be in the car. The rest are a count, so "how many today" still works.
            val taken = jobs.filter { it.taken }.sortedByDescending { it.atMillis }
            val inHand = taken.filter { nowMillis - it.atMillis < IN_HAND_MILLIS }
            append('\n')
            if (inHand.isEmpty()) {
                append(if (zh) "手上没有正在送的单。\n" else "Nothing in the car right now.\n")
            } else {
                append(if (zh) "手上的单（最新的在前）：\n" else "In the car (newest first):\n")
                inHand.forEachIndexed { index, job ->
                    append(index + 1).append(". ").append(jobLine(job, nowMillis, zh)).append('\n')
                }
            }
            if (taken.size > inHand.size) {
                val done = taken.size - inHand.size
                append(if (zh) "更早接过的单：$done 单，已经送完。\n" else "Taken earlier and delivered: $done.\n")
            }

            val recent = jobs.filter { !it.taken }.sortedByDescending { it.atMillis }.take(offersToMention)
            if (recent.isNotEmpty()) {
                append(if (zh) "\n最近没接的派单：\n" else "\nRecent offers not taken:\n")
                recent.forEach { job -> append("- ").append(offerLine(job, nowMillis, zh)).append('\n') }
            }
        }.trimEnd()
    }

    private fun whereLine(where: Whereabouts, zh: Boolean): String {
        val car = where.carAt ?: return if (zh) "车的位置：不知道" else "Where the car is: not known"
        val parts = mutableListOf<String>()
        where.nearestSuburb?.let { parts += if (zh) "在 $it 附近" else "near $it" }
        where.centre?.let {
            val far = km(Geo.straightLine(car, it), zh)
            parts += if (zh) "离选区中心 $far" else "$far from the set's centre"
        }
        val head = if (zh) "车的位置：" else "Where the car is: "
        val none = if (zh) "有定位，但认不出在哪个区" else "a fix, but no suburb I recognise"
        return head + (parts.joinToString(if (zh) "，" else ", ").ifEmpty { none })
    }

    private fun jobLine(job: Job, nowMillis: Long, zh: Boolean): String = buildString {
        val at = CLOCK.format(Date(job.atMillis))
        val age = ageOf(nowMillis - job.atMillis, zh)
        if (zh) {
            append(job.offer.payout).append("，").append(at).append(" 接的（").append(age).append("）。")
            append("取餐：").append(job.offer.pickup)
            job.address?.let { append("，地址 ").append(it) }
            append("，").append(if (job.address != null) "已到店" else "还没到店").append("。")
            append("送餐：").append(job.dropAddress ?: job.offer.dropoff)
            job.dropUnit?.let { append("，").append(it) }
            append("，").append(if (job.dropAddress != null) "已在送餐页面" else "还没到送餐页面").append("。")
            job.extraDrops.forEach { drop ->
                append("同一单还要送：").append(drop.address)
                drop.unit?.let { append("，").append(it) }
                drop.note?.let { append("，客户留言：").append(it) }
                append("。")
            }
            (job.noteCn ?: job.note)?.let { append("商家留言：").append(it).append("。") }
            (job.dropNoteCn ?: job.dropNote)?.let { append("客户留言：").append(it).append("。") }
        } else {
            append(job.offer.payout).append(", taken at ").append(at).append(" (").append(age).append("). ")
            append("Pick up: ").append(job.offer.pickup)
            job.address?.let { append(", at ").append(it) }
            append(", ").append(if (job.address != null) "already at the shop" else "not at the shop yet").append(". ")
            append("Drop off: ").append(job.dropAddress ?: job.offer.dropoff)
            job.dropUnit?.let { append(", ").append(it) }
            append(", ").append(if (job.dropAddress != null) "delivery screen reached" else "delivery screen not reached").append(". ")
            job.extraDrops.forEach { drop ->
                append("Same offer also goes to: ").append(drop.address)
                drop.unit?.let { append(", ").append(it) }
                drop.note?.let { append(", customer note: ").append(it) }
                append(". ")
            }
            job.note?.let { append("Shop note: ").append(it).append(". ") }
            job.dropNote?.let { append("Customer note: ").append(it).append(". ") }
        }
    }

    private fun offerLine(job: Job, nowMillis: Long, zh: Boolean): String = buildString {
        append(job.offer.payout).append(if (zh) "，" else ", ").append(ageOf(nowMillis - job.atMillis, zh))
        if (zh) {
            append("，").append(job.offer.pickup).append(" 送到 ").append(job.offer.dropoff)
            job.offer.ruling?.let { append("，判断：").append(it) }
            job.offer.why?.let { append("，理由：").append(it) }
        } else {
            append(", ").append(job.offer.pickup).append(" to ").append(job.offer.dropoff)
            job.offer.ruling?.let { append(", verdict: ").append(it) }
            job.offer.why?.let { append(", because: ").append(it) }
        }
    }

    private fun ageOf(millis: Long, zh: Boolean): String {
        val minutes = millis / 60_000
        return when {
            minutes < 1 -> if (zh) "刚刚" else "just now"
            minutes < 60 -> if (zh) "$minutes 分钟前" else "$minutes min ago"
            else -> {
                val hours = minutes / 60
                val rest = minutes % 60
                if (zh) "$hours 小时 $rest 分钟前" else "$hours h $rest min ago"
            }
        }
    }

    private fun km(miles: Miles, zh: Boolean): String =
        String.format(Locale.ROOT, if (zh) "%.1f 公里" else "%.1f km", miles.value * 1.609344)

    private fun day(millis: Long, zh: Boolean): String =
        (if (zh) DAY_ZH else DAY_EN).format(Date(millis))

    private val CLOCK = SimpleDateFormat("HH:mm", Locale.ROOT)
    private val DAY_ZH = SimpleDateFormat("yyyy年M月d日 EEEE", Locale.SIMPLIFIED_CHINESE)
    private val DAY_EN = SimpleDateFormat("EEEE d MMMM yyyy", Locale.ENGLISH)

    /** A job taken longer ago than this is done, whatever the board still says. */
    private const val IN_HAND_MILLIS = 2L * 60 * 60 * 1000
}
