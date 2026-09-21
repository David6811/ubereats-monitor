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
        offersToMention: Int = 4,
    ): String = buildString {
        append("时间 ").append(CLOCK.format(Date(nowMillis))).append('\n')
        append(whereLine(where)).append('\n')
        if (!profileName.isNullOrBlank()) append("当前选区：").append(profileName).append('\n')

        // The board keeps every job it ever saw; only the recent ones can still
        // be in the car. The rest are a count, so "how many today" still works.
        val taken = jobs.filter { it.taken }.sortedByDescending { it.atMillis }
        val inHand = taken.filter { nowMillis - it.atMillis < IN_HAND_MILLIS }
        append('\n')
        if (inHand.isEmpty()) {
            append("手上没有正在送的单。\n")
        } else {
            append("手上的单（最新的在前）：\n")
            inHand.forEachIndexed { index, job -> append(index + 1).append(". ").append(jobLine(job, nowMillis)).append('\n') }
        }
        if (taken.size > inHand.size) append("更早接过的单：").append(taken.size - inHand.size).append(" 单，已经送完。\n")

        val recent = jobs.filter { !it.taken }.sortedByDescending { it.atMillis }.take(offersToMention)
        if (recent.isNotEmpty()) {
            append("\n最近没接的派单：\n")
            recent.forEach { job -> append("- ").append(offerLine(job, nowMillis)).append('\n') }
        }
    }.trimEnd()

    private fun whereLine(where: Whereabouts): String {
        val car = where.carAt ?: return "车的位置：不知道"
        val parts = mutableListOf<String>()
        where.nearestSuburb?.let { parts += "在 $it 附近" }
        where.centre?.let { parts += "离选区中心 " + km(Geo.straightLine(car, it)) }
        return "车的位置：" + (parts.joinToString("，").ifEmpty { "有定位，但认不出在哪个区" })
    }

    private fun jobLine(job: Job, nowMillis: Long): String = buildString {
        append(job.offer.payout).append("，").append(CLOCK.format(Date(job.atMillis))).append(" 接的")
        append("（").append(ageOf(nowMillis - job.atMillis)).append("）。")
        append("取餐：").append(job.offer.pickup)
        job.address?.let { append("，地址 ").append(it) }
        append("，").append(if (job.address != null) "已到店" else "还没到店").append("。")
        append("送餐：").append(job.dropAddress ?: job.offer.dropoff)
        job.dropUnit?.let { append("，").append(it) }
        append("，").append(if (job.dropAddress != null) "已在送餐页面" else "还没到送餐页面").append("。")
        (job.noteCn ?: job.note)?.let { append("商家留言：").append(it).append("。") }
        (job.dropNoteCn ?: job.dropNote)?.let { append("客户留言：").append(it).append("。") }
    }

    private fun offerLine(job: Job, nowMillis: Long): String = buildString {
        append(job.offer.payout).append("，").append(ageOf(nowMillis - job.atMillis)).append("，")
        append(job.offer.pickup).append(" 送到 ").append(job.offer.dropoff)
        job.offer.ruling?.let { append("，判断：").append(it) }
        job.offer.why?.let { append("，理由：").append(it) }
    }

    private fun ageOf(millis: Long): String {
        val minutes = millis / 60_000
        return when {
            minutes < 1 -> "刚刚"
            minutes < 60 -> "$minutes 分钟前"
            else -> (minutes / 60).toString() + " 小时 " + (minutes % 60) + " 分钟前"
        }
    }

    private fun km(miles: Miles): String = String.format(Locale.ROOT, "%.1f 公里", miles.value * 1.609344)

    private val CLOCK = SimpleDateFormat("HH:mm", Locale.ROOT)

    /** A job taken longer ago than this is done, whatever the board still says. */
    private const val IN_HAND_MILLIS = 2L * 60 * 60 * 1000
}
