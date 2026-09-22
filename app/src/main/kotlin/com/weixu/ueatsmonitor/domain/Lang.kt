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

    // The job board.
    val shelfTaken: String
    val shelfWorth: String
    val shelfLeave: String
    val noneTakenToday: String
    val noneWorthToday: String
    val noneLeaveToday: String
    val empty: String
    val confirmed: String
    fun ruleLine(rule: String): String
    fun jobCount(n: Int): String
    val yesterday: String
    val dayBefore: String
    val today: String
    /** How a date is written on the board: "9月21日 周一" or "Mon 21 Sep". */
    val dayPattern: String
    val pickUp: String
    val dropOff: String
    val alsoDrop: String
    val customerNote: String
    val shopNote: String
    val drive: String
    val clearIt: String

    // This trip, and the sets.
    fun thisTrip(name: String): String
    val tapToToggle: String
    val going: String
    val notGoing: String
    fun areasAllGoing(n: Int): String
    fun areasSomeOff(n: Int, off: Int): String
    val clearAll: String
    val keepCentreOnly: String
    val noAreasYet: String
    val pushFromTheWeb: String
    val switchSet: String
    val liveFromPhone: String
    val liveFromLaptop: String
    val fewerAreasThisTrip: String
    val turnThemOffOnTrip: String
    fun areaCount(n: Int): String

    // Signing in.
    val signIn: String
    val signUp: String
    val email: String
    val password: String
    val show: String
    val hide: String
    val signingIn: String
    val noAccountYet: String
    val fillBothFirst: String
    val rulesShared: String
    val badEmail: String
    val shortPassword: String
    val wrongEmailOrPassword: String
    val alreadyRegistered: String
    val emailNotConfirmed: String
    val noNetwork: String
    fun failed(what: String, why: String): String

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

    override val shelfTaken = "已接"
    override val shelfWorth = "建议接"
    override val shelfLeave = "建议不接"
    override val noneTakenToday = "今天还没有已接的单"
    override val noneWorthToday = "今天还没有建议接的单"
    override val noneLeaveToday = "今天还没有建议不接的单"
    override val empty = "空"
    override val confirmed = "✓ 已确认"
    override fun ruleLine(rule: String) = "规则：$rule"
    override fun jobCount(n: Int) = "$n 单"
    override val yesterday = "昨天"
    override val dayBefore = "前天"
    override val today = "今天"
    override val dayPattern = "M月d日 EEE"
    override val pickUp = "取餐"
    override val dropOff = "送达"
    override val alsoDrop = "同单再送"
    override val customerNote = "客户留言"
    override val shopNote = "店家留言"
    override val drive = "导航"
    override val clearIt = "清掉"

    override fun thisTrip(name: String) = "这一趟 · $name"
    override val tapToToggle = "点一下切换 · 长按只留它"
    override val going = " 去"
    override val notGoing = " 不去"
    override fun areasAllGoing(n: Int) = "$n 个区，全都去"
    override fun areasSomeOff(n: Int, off: Int) = "$n 个区，点掉了 $off"
    override val clearAll = "全不选"
    override val keepCentreOnly = "只留中心"
    override val noAreasYet = "还没有选区"
    override val pushFromTheWeb = "在电脑的编辑器里点一次「保存」。"
    override val switchSet = "换一套"
    override val liveFromPhone = "现在用 · 手机上选的"
    override val liveFromLaptop = "现在用 · 电脑推过来的"
    override val fewerAreasThisTrip = "这一趟想少去几个区"
    override val turnThemOffOnTrip = "去「这趟」那一页点掉，只管这一趟，不会改这里的选区。"
    override fun areaCount(n: Int) = "$n 个区"

    override val signIn = "登录"
    override val signUp = "注册"
    override val email = "邮箱"
    override val password = "密码"
    override val show = "显示"
    override val hide = "隐藏"
    override val signingIn = "正在登录…"
    override val noAccountYet = "还没有账号？"
    override val fillBothFirst = "填好邮箱和密码再点注册"
    override val rulesShared = "登录后，规则在手机和电脑上是同一份"
    override val badEmail = "邮箱不对"
    override val shortPassword = "密码至少 6 位"
    override val wrongEmailOrPassword = "邮箱或密码不对"
    override val alreadyRegistered = "这个邮箱已经注册过，直接登录"
    override val emailNotConfirmed = "邮箱还没确认，去邮箱点一下链接"
    override val noNetwork = "没网，连不上"
    override fun failed(what: String, why: String) = "$what 失败：$why"

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

    override val shelfTaken = "Taken"
    override val shelfWorth = "Worth it"
    override val shelfLeave = "Not worth it"
    override val noneTakenToday = "Nothing taken today yet"
    override val noneWorthToday = "Nothing worth taking today yet"
    override val noneLeaveToday = "Nothing refused today yet"
    override val empty = "Empty"
    override val confirmed = "✓ Confirmed"
    override fun ruleLine(rule: String) = "Rule: $rule"
    override fun jobCount(n: Int) = if (n == 1) "1 job" else "$n jobs"
    override val yesterday = "Yesterday"
    override val dayBefore = "The day before"
    override val today = "Today"
    override val dayPattern = "EEE d MMM"
    override val pickUp = "Pick up"
    override val dropOff = "Drop off"
    override val alsoDrop = "Also drop"
    override val customerNote = "Customer note"
    override val shopNote = "Shop note"
    override val drive = "Drive"
    override val clearIt = "Clear"

    override fun thisTrip(name: String) = "This trip · $name"
    override val tapToToggle = "Tap to toggle · hold to keep only that one"
    override val going = " going"
    override val notGoing = " not going"
    override fun areasAllGoing(n: Int) = "$n areas, all going"
    override fun areasSomeOff(n: Int, off: Int) = "$n areas, $off turned off"
    override val clearAll = "None"
    override val keepCentreOnly = "Centre only"
    override val noAreasYet = "No areas yet"
    override val pushFromTheWeb = "Press Save once in the web editor."
    override val switchSet = "Switch set"
    override val liveFromPhone = "In use · picked on the phone"
    override val liveFromLaptop = "In use · sent from the web"
    override val fewerAreasThisTrip = "Fewer areas just for this trip"
    override val turnThemOffOnTrip = "Turn them off on the Trip page; it lasts this trip only and leaves the set alone."
    override fun areaCount(n: Int) = if (n == 1) "1 area" else "$n areas"

    override val signIn = "Sign in"
    override val signUp = "Sign up"
    override val email = "Email"
    override val password = "Password"
    override val show = "Show"
    override val hide = "Hide"
    override val signingIn = "Signing in…"
    override val noAccountYet = "No account yet?"
    override val fillBothFirst = "Fill in both before signing up"
    override val rulesShared = "Sign in and your rules are the same on the phone and the web"
    override val badEmail = "That email does not look right"
    override val shortPassword = "Password needs 6 characters or more"
    override val wrongEmailOrPassword = "Wrong email or password"
    override val alreadyRegistered = "That email is already registered, just sign in"
    override val emailNotConfirmed = "Email not confirmed yet, open the link in it"
    override val noNetwork = "No network"
    override fun failed(what: String, why: String) = "$what failed: $why"

    override fun km(value: String) = "$value km"
    override fun minutes(value: Int) = "$value min"
    override fun perHour(amount: String) = "\$$amount/h"
}

fun wordsIn(lang: Lang): Words = if (lang == Lang.ENGLISH) En else Zh
