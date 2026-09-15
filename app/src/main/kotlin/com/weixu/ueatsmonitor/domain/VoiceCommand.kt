package com.weixu.ueatsmonitor.domain

/**
 * Data. The apps a spoken command can name. [spoken] is written on screen;
 * [confirm] is said aloud the moment the command is understood.
 */
enum class VoiceTarget(val packageName: String, val spoken: String, val confirm: String) {
    SELF("com.weixu.ueatsmonitor", "接单助手", "好，应用"),
    MAPS("com.google.android.apps.maps", "谷歌地图", "好，地图"),
    UBER("com.ubercab.driver", "Uber 司机端", "好，送餐"),
}

/** Data. What a sentence asked for. */
sealed interface VoiceCommand {
    /** Bring the app to the front. */
    data class SwitchTo(val target: VoiceTarget) : VoiceCommand
}

/**
 * Calculation. Reads one heard sentence as a command, or as nothing.
 *
 * The microphone is always open, so a passenger or the radio is heard as often
 * as the driver. A sentence counts when it has both a verb and an app in it, or
 * when it is nothing but an app's name: "地图" on its own switches, while
 * "这个地图不太准", which only mentions one, does nothing.
 */
object VoiceCommands {

    // Single characters are enough: an app name has to be in the sentence too.
    private val SWITCH_VERBS = listOf("切换", "切回", "打开", "回到", "切", "去")

    /**
     * Chinese first. A sentence half in English - "切到 Google Map" - is the one a
     * Chinese recognizer gets wrong, so every app has a plain Chinese name to say.
     * The English ones stay for when the recognizer does write them.
     */
    private val NAMES: List<Pair<VoiceTarget, List<String>>> = listOf(
        VoiceTarget.MAPS to listOf("地图", "谷歌", "导航", "google", "map", "maps"),
        VoiceTarget.UBER to listOf("优步", "司机", "送餐", "外卖", "派单", "接单", "uber", "ubereats"),
        VoiceTarget.SELF to listOf("接单助手", "助手", "应用", "我们的", "application", "app"),
    )

    /** The short sentences to say, and to steer the recognizer towards. */
    val PHRASES: List<String> = listOf(
        "切地图", "切优步", "切送餐", "切应用", "切助手",
        "地图", "送餐", "助手", "应用",
        "map", "uber eats", "application",
    )

    /** The recognizer offers several guesses, best first; the first that reads as a command wins. */
    fun parse(guesses: List<String>): VoiceCommand? = guesses.firstNotNullOfOrNull(::parseOne)

    private fun parseOne(sentence: String): VoiceCommand? {
        val text = fold(sentence)
        // The longest name heard wins: "接单助手" is this app, though "接单" is Uber.
        val target = NAMES
            .flatMap { (target, names) -> names.map { target to it } }
            .filter { (_, name) -> text.contains(name) }
            .maxByOrNull { (_, name) -> name.length }
            ?.first
            ?: return null
        return when {
            SWITCH_VERBS.any { text.contains(it) } -> VoiceCommand.SwitchTo(target)
            NAMES.any { (_, names) -> text in names } -> VoiceCommand.SwitchTo(target)
            else -> null
        }
    }

    /**
     * Lower case, no spaces, no punctuation: the recognizer writes "Google Map"
     * and "google map" alike, and may end a one-word sentence with "。".
     */
    private fun fold(sentence: String): String =
        sentence.lowercase().filter { it.isLetterOrDigit() }
}
