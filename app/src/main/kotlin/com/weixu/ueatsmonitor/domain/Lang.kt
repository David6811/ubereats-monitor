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

    // The settings page.
    val settingsWatching: String
    val settingsFinishing: String
    val settingsPerHour: String
    val save: String
    val cancel: String
    val gotIt: String
    val areaSound: String
    val floatingButtons: String
    val floatingButtonsHint: String
    val voiceCommands: String
    val voiceCommandsHint: String
    val whatCanISay: String
    val voiceHelpTail: String
    val homewardMode: String
    val homewardHint: String
    val nearCentreMode: String
    val nearCentreHint: String
    val noCentreSet: String
    val noCentreSetLong: String
    val needAlwaysLocation: String
    val noFixYet: String
    val setLocationAlways: String
    val quitAndStop: String
    val quitHint: String
    val quitAsk: String
    val quitAskBody: String
    val quitIt: String
    val signOut: String
    val overlayOff: String
    val overlayOffHint: String
    val readerOff: String
    val readerOffHint: String
    val notificationsOff: String
    fun farOver(dollars: String, areas: Int): String
    val nearKmLabel: String
    val maxMinutesLabel: String
    val withinKmLabel: String
    val withinMinutesLabel: String
    val fuelPerKm: String
    val timeFactor: String
    val farFloorPerHour: String
    val perHourFormula: String
    val rulesUpdated: String
    val rulesAlreadyCurrent: String
    fun couldNotFetch(why: String): String
    val refreshed: String
    val onPhoneOnly: String

    // Spoken commands, as the help lists them.
    val sayMap: String
    val sayUber: String
    val sayApp: String
    val sayCentre: String
    val sayStopNavigation: String
    val sayAsk: String
    val sayVoiceOff: String
    val switchedTo: String

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

    override val settingsWatching = "监控"
    override val settingsFinishing = "收工"
    override val settingsPerHour = "每小时收入"
    override val save = "保存"
    override val cancel = "取消"
    override val gotIt = "知道了"
    override val areaSound = "区域提示音"
    override val floatingButtons = "悬浮按钮"
    override val floatingButtonsHint = "跑单时右上角的「关导航」「语音」"
    override val voiceCommands = "语音命令"
    override val voiceCommandsHint = "一直在听：说「地图」「送餐」「应用」「回中心」"
    override val whatCanISay = "能说什么？"
    override val voiceHelpTail = "前面加「切」「打开」也行，比如「切地图」。"
    override val homewardMode = "回中心模式"
    override val homewardHint = "只接离中心更近的单，远区也一样"
    override val nearCentreMode = "近中心模式"
    override val nearCentreHint = "只接离中心几公里内、时间短的单"
    override val noCentreSet = "这套选区没设中心，先在电脑上设一个"
    override val noCentreSetLong = "这套选区没设中心，在电脑上点「改中心」再保存"
    override val needAlwaysLocation = "定位要设成「始终允许」才有用，点这里去改"
    override val noFixYet = "手机还没有定位，开不了"
    override val setLocationAlways = "把定位改成「始终允许」：权限 → 位置信息 → 始终允许"
    override val quitAndStop = "退出并停止监控"
    override val quitHint = "停掉读屏和后台守护。下次要用，得去「系统设置 → 无障碍 → 接单助手」重新打开。"
    override val quitAsk = "停掉监控？"
    override val quitAskBody = "派单来了就不会再有判断和记录，直到你在系统的无障碍设置里重新打开。"
    override val quitIt = "停掉"
    override val signOut = "退出登录"
    override val overlayOff = "悬浮窗没开"
    override val overlayOffHint = "打开后判断结果会盖在派单卡片上"
    override val readerOff = "读屏没开"
    override val readerOffHint = "唯一能看到派单卡片的通道。关掉就什么都记录不到"
    override val notificationsOff = "通知权限没开：常驻通知和上面的两个按钮不会出现"
    override fun farOver(dollars: String, areas: Int) = "超过 \$$dollars 用远区"
    override val nearKmLabel = "离中心小于 公里 照接"
    override val maxMinutesLabel = "超过 分钟 不接"
    override val withinKmLabel = "离中心 公里内 才接"
    override val withinMinutesLabel = "分钟内 才接"
    override val fuelPerKm = "每公里油钱 \$"
    override val timeFactor = "时间倍数"
    override val farFloorPerHour = "远区最低 \$/时"
    override val perHourFormula = "(钱 − 公里 × 2 × 油钱) ÷ (分钟 × 倍数 ÷ 60)"
    override val rulesUpdated = "拿到了新规则"
    override val rulesAlreadyCurrent = "已经是最新的"
    override fun couldNotFetch(why: String) = "拿不到：$why"
    override val refreshed = "刷新了"
    override val onPhoneOnly = "这些在手机上改"

    override val sayMap = "切到谷歌地图"
    override val sayUber = "切到 Uber"
    override val sayApp = "切回接单助手"
    override val sayCentre = "导航回选区中心"
    override val sayStopNavigation = "关掉谷歌地图的导航"
    override val sayAsk = "问助手：你好，现在送哪一单"
    override val sayVoiceOff = "关掉语音，再开要点一下按钮"
    override val switchedTo = "切到"

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

    override val settingsWatching = "Watching"
    override val settingsFinishing = "Finishing up"
    override val settingsPerHour = "Dollars an hour"
    override val save = "Save"
    override val cancel = "Cancel"
    override val gotIt = "Got it"
    override val areaSound = "Area chime"
    override val floatingButtons = "Floating buttons"
    override val floatingButtonsHint = "Stop navigation and voice, top right, while on shift"
    override val voiceCommands = "Voice commands"
    override val voiceCommandsHint = "Always listening: say 地图, 送餐, 应用, 回中心"
    override val whatCanISay = "What can I say?"
    override val voiceHelpTail = "A verb in front is fine too, like 切地图. Commands are Mandarin for now."
    override val homewardMode = "Homeward mode"
    override val homewardHint = "Only jobs that leave you nearer the centre, far areas too"
    override val nearCentreMode = "Near-centre mode"
    override val nearCentreHint = "Only drops a few km from the centre, on short jobs"
    override val noCentreSet = "This set has no centre; set one on the web first"
    override val noCentreSetLong = "This set has no centre; press 改中心 on the web and save"
    override val needAlwaysLocation = "Location must be \"Allow all the time\"; tap here to change it"
    override val noFixYet = "No position yet, cannot switch it on"
    override val setLocationAlways = "Set location to \"Allow all the time\": Permissions → Location → Allow all the time"
    override val quitAndStop = "Quit and stop watching"
    override val quitHint = "Stops the reader and the keeper. To use it again, switch it on under Settings → Accessibility."
    override val quitAsk = "Stop watching?"
    override val quitAskBody = "Offers will get no verdict and no record until you switch it on again under Accessibility."
    override val quitIt = "Stop it"
    override val signOut = "Sign out"
    override val overlayOff = "Draw-over is off"
    override val overlayOffHint = "Switch it on and the verdict lands on the offer card"
    override val readerOff = "The reader is off"
    override val readerOffHint = "The only way it sees an offer card. Off, nothing is read at all"
    override val notificationsOff = "Notifications are off: the ongoing notice and the two buttons will not appear"
    override fun farOver(dollars: String, areas: Int) = "Over \$$dollars use the far set"
    override val nearKmLabel = "Take if within km"
    override val maxMinutesLabel = "Leave if over min"
    override val withinKmLabel = "Only within km"
    override val withinMinutesLabel = "Only under min"
    override val fuelPerKm = "Fuel per km \$"
    override val timeFactor = "Time factor"
    override val farFloorPerHour = "Far floor \$/h"
    override val perHourFormula = "(pay − km × 2 × fuel) ÷ (min × factor ÷ 60)"
    override val rulesUpdated = "Got the new rules"
    override val rulesAlreadyCurrent = "Already up to date"
    override fun couldNotFetch(why: String) = "Could not fetch: $why"
    override val refreshed = "Refreshed"
    override val onPhoneOnly = "Set these on the phone"

    override val sayMap = "Switch to Google Maps"
    override val sayUber = "Switch to Uber"
    override val sayApp = "Back to Offer Mate"
    override val sayCentre = "Drive back to the set's centre"
    override val sayStopNavigation = "Close the Maps navigation"
    override val sayAsk = "Ask the assistant: 你好, then your question"
    override val sayVoiceOff = "Switch voice off; tap the button to bring it back"
    override val switchedTo = "Switched to"

    override fun km(value: String) = "$value km"
    override fun minutes(value: Int) = "$value min"
    override fun perHour(amount: String) = "\$$amount/h"
}

fun wordsIn(lang: Lang): Words = if (lang == Lang.ENGLISH) En else Zh
