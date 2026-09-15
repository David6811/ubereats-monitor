package com.weixu.ueatsmonitor.domain

/** Data. The apps a spoken command can name. */
enum class VoiceTarget(val packageName: String, val spoken: String) {
    SELF("com.weixu.ueatsmonitor", "接单助手"),
    MAPS("com.google.android.apps.maps", "谷歌地图"),
    UBER("com.ubercab.driver", "Uber 司机端"),
}

/** Data. What a sentence asked for. */
sealed interface VoiceCommand {
    /** Bring the app to the front. */
    data class SwitchTo(val target: VoiceTarget) : VoiceCommand

    /** Send the app to the background if it is in front. Nothing can kill another app. */
    data class Close(val target: VoiceTarget) : VoiceCommand
}

/**
 * Calculation. Reads one heard sentence as a command, or as nothing.
 *
 * The microphone is always open, so a passenger or the radio is heard as often
 * as the driver. A sentence counts only when it has both a verb and an app in
 * it: "地图" alone, said in passing, does nothing.
 */
object VoiceCommands {

    private val CLOSE_VERBS = listOf("关闭", "关掉", "关了", "退出", "收起")
    private val SWITCH_VERBS = listOf("切换到", "切换", "切到", "切回", "打开", "回到", "去")

    private val NAMES: List<Pair<VoiceTarget, List<String>>> = listOf(
        VoiceTarget.MAPS to listOf("谷歌地图", "google地图", "googlemap", "googlemaps", "地图", "导航"),
        VoiceTarget.UBER to listOf("ubereats", "uber", "优步", "司机端", "派单"),
        VoiceTarget.SELF to listOf("接单助手", "我们的app", "我们的应用", "我们的", "助手"),
    )

    /** The recognizer offers several guesses, best first; the first that reads as a command wins. */
    fun parse(guesses: List<String>): VoiceCommand? = guesses.firstNotNullOfOrNull(::parseOne)

    private fun parseOne(sentence: String): VoiceCommand? {
        val text = fold(sentence)
        val target = NAMES.firstOrNull { (_, names) -> names.any { text.contains(it) } }?.first
            ?: return null
        return when {
            CLOSE_VERBS.any { text.contains(it) } -> VoiceCommand.Close(target)
            SWITCH_VERBS.any { text.contains(it) } -> VoiceCommand.SwitchTo(target)
            else -> null
        }
    }

    /** Lower case, no spaces: the recognizer writes "Google Map" and "google map" alike. */
    private fun fold(sentence: String): String =
        sentence.lowercase().filterNot { it.isWhitespace() }
}
