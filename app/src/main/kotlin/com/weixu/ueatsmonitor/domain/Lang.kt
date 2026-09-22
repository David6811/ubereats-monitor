package com.weixu.ueatsmonitor.domain

/**
 * Data. The language every word the driver reads is written in.
 *
 * Not the phone's locale: a driver may keep an English phone and want Chinese
 * here, or the other way round. [Words] turns it into the words themselves.
 */
enum class Lang { CHINESE, ENGLISH }

/**
 * Calculation. Every phrase the app says, in both languages.
 *
 * One object per language, both filling the same interface, so a phrase that
 * exists in one and not the other will not compile. Sentences that carry a
 * number or a name are functions of it rather than strings to be glued
 * together: word order differs between the two.
 */
interface Words {

    // The verdict, as the chip and the board show it.
    val takeIt: String
    val enterIt: String
    val leaveIt: String
    val leaveMatch: String
    val noRules: String
    val unknownPlace: String
    fun onTheList(suburb: String): String
    fun onTheFarList(suburb: String): String
    fun notOnTheList(suburb: String): String
    fun storeDenied(store: String): String
    fun pickupInBox(store: String, box: String): String
    fun dropInBox(box: String): String
    fun leadingAway(fromCarKm: String, fromDropKm: String): String
    fun tooLong(minutes: Int, max: Int): String
    fun tooFarFromCentre(km: String, maxKm: String): String
    fun farTooCheap(perHour: String, floor: String): String
    val setRulesOnTheLaptop: String
    val noSuburbInAddress: String

    // The rule a reason came from, named as the settings name it.
    val ruleAreas: String
    val ruleFarAreas: String
    val ruleStoreDenyList: String
    val ruleNoGoBox: String
    val ruleHomewardAway: String
    val ruleTimeLimit: String
    val ruleNearCentreTooFar: String
    val ruleFarPerHour: String

    // The four places along the bottom of the app.
    val tabWork: String
    val tabTrip: String
    val tabAreas: String
    val tabSettings: String

    // The strip along the top.
    val appName: String
    val watching: String
    val notWatching: String
    val turnOnReader: String
    val readerStalled: String
    val allowOverlay: String
    val voiceOn: String
    val voiceOff: String

    // Units and small words used in more than one place.
    fun km(value: String): String
    fun minutes(value: Int): String
    fun perHour(amount: String): String
}

object Zh : Words {
    override val takeIt = "可以接单"
    override val enterIt = "可以抢（Match）"
    override val leaveIt = "不要接单"
    override val leaveMatch = "不要抢（Match）"
    override val noRules = "没设规则"
    override val unknownPlace = "认不出地点"
    override fun onTheList(suburb: String) = "$suburb 在名单里"
    override fun onTheFarList(suburb: String) = "$suburb 在远区名单里"
    override fun notOnTheList(suburb: String) = "$suburb 不在名单里"
    override fun storeDenied(store: String) = "$store 在黑名单里"
    override fun pickupInBox(store: String, box: String) = "取餐 $store 在「$box」里"
    override fun dropInBox(box: String) = "送餐点在「$box」里"
    override fun leadingAway(fromCarKm: String, fromDropKm: String) =
        "离中心更远：现在 $fromCarKm，送完 $fromDropKm"
    override fun tooLong(minutes: Int, max: Int) = "要 $minutes 分钟，超过 $max 分钟"
    override fun tooFarFromCentre(km: String, maxKm: String) = "送完离中心 $km，超过 $maxKm 公里"
    override fun farTooCheap(perHour: String, floor: String) = "远区单每小时 \$$perHour，低于 \$$floor"
    override val setRulesOnTheLaptop = "在电脑上设好规则再推过来"
    override val noSuburbInAddress = "送达地址里没有认得出的郊区"

    override val ruleAreas = "选区"
    override val ruleFarAreas = "远区"
    override val ruleStoreDenyList = "店铺黑名单"
    override val ruleNoGoBox = "不接区"
    override val ruleHomewardAway = "回中心模式（送完离中心更远）"
    override val ruleTimeLimit = "回中心 / 近中心模式（时间太长）"
    override val ruleNearCentreTooFar = "近中心模式（送得太远）"
    override val ruleFarPerHour = "远区每小时最低"

    override val tabWork = "工作"
    override val tabTrip = "这趟"
    override val tabAreas = "选区"
    override val tabSettings = "设置"

    override val appName = "接单助手"
    override val watching = "✓ 在监控"
    override val notWatching = "✕ 没在监控"
    override val turnOnReader = "在「无障碍」里打开「接单助手」"
    override val readerStalled = "读屏卡住了：在「无障碍」里把「接单助手」关掉再打开"
    override val allowOverlay = "允许「接单助手」显示在其他应用上层"
    override val voiceOn = "语音开"
    override val voiceOff = "语音关"

    override fun km(value: String) = "$value 公里"
    override fun minutes(value: Int) = "$value 分钟"
    override fun perHour(amount: String) = "\$$amount/小时"
}

object En : Words {
    override val takeIt = "Take it"
    override val enterIt = "Enter it (Match)"
    override val leaveIt = "Leave it"
    override val leaveMatch = "Leave it (Match)"
    override val noRules = "No rules set"
    override val unknownPlace = "Place not recognised"
    override fun onTheList(suburb: String) = "$suburb is on your list"
    override fun onTheFarList(suburb: String) = "$suburb is on the far list"
    override fun notOnTheList(suburb: String) = "$suburb is not on your list"
    override fun storeDenied(store: String) = "$store is on your deny list"
    override fun pickupInBox(store: String, box: String) = "Pickup $store is inside \"$box\""
    override fun dropInBox(box: String) = "The drop is inside \"$box\""
    override fun leadingAway(fromCarKm: String, fromDropKm: String) =
        "Leads away: $fromCarKm now, $fromDropKm after"
    override fun tooLong(minutes: Int, max: Int) = "$minutes min, over your $max"
    override fun tooFarFromCentre(km: String, maxKm: String) = "Drop is $km out, over your $maxKm km"
    override fun farTooCheap(perHour: String, floor: String) = "Far job pays \$$perHour an hour, under \$$floor"
    override val setRulesOnTheLaptop = "Set your rules on the web first"
    override val noSuburbInAddress = "No suburb I know in that address"

    override val ruleAreas = "Areas"
    override val ruleFarAreas = "Far areas"
    override val ruleStoreDenyList = "Shop deny list"
    override val ruleNoGoBox = "No-go box"
    override val ruleHomewardAway = "Homeward (drop leads away)"
    override val ruleTimeLimit = "Homeward / near-centre (too long)"
    override val ruleNearCentreTooFar = "Near-centre (drop too far)"
    override val ruleFarPerHour = "Far minimum per hour"

    override val tabWork = "Work"
    override val tabTrip = "Trip"
    override val tabAreas = "Areas"
    override val tabSettings = "Settings"

    override val appName = "Offer Mate"
    override val watching = "✓ Watching"
    override val notWatching = "✕ Not watching"
    override val turnOnReader = "Turn on \"Offer Mate\" under Accessibility"
    override val readerStalled = "The reader stalled: switch \"Offer Mate\" off and on under Accessibility"
    override val allowOverlay = "Let \"Offer Mate\" draw over other apps"
    override val voiceOn = "Voice on"
    override val voiceOff = "Voice off"

    override fun km(value: String) = "$value km"
    override fun minutes(value: Int) = "$value min"
    override fun perHour(amount: String) = "\$$amount/h"
}

fun wordsIn(lang: Lang): Words = if (lang == Lang.ENGLISH) En else Zh
