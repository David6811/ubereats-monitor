package com.weixu.ueatsmonitor.domain

/**
 * Data. The apps a spoken command can name. [spoken] is written on screen;
 * the confirmations are said aloud the moment the command is understood, in
 * the language it was given in.
 */
enum class VoiceTarget(
    val packageName: String,
    val spoken: String,
    val confirmChinese: String,
    val confirmEnglish: String,
) {
    SELF("com.weixu.ueatsmonitor", "接单助手", "好，应用", "OK, application"),
    MAPS("com.google.android.apps.maps", "谷歌地图", "好，地图", "OK, map"),
    UBER("com.ubercab.driver", "Uber 司机端", "好，送餐", "OK, Uber Eats"),
}

/** Data. The language a command was said in, which is the language it is answered in. */
enum class SpokenLanguage { CHINESE, ENGLISH }

/** Data. What a sentence asked for. */
sealed interface VoiceCommand {
    val language: SpokenLanguage

    /** Bring the app to the front. */
    data class SwitchTo(val target: VoiceTarget, override val language: SpokenLanguage) : VoiceCommand

    /** Drive back to the middle of the set being worked, as drawn on the laptop. */
    data class DriveToCentre(override val language: SpokenLanguage) : VoiceCommand

    /** Press the cross on Google Maps' running navigation. */
    data class StopNavigation(override val language: SpokenLanguage) : VoiceCommand

    /**
     * A question for the assistant, after the wake word: "你好，现在送哪一单".
     * The recognizer usually ends the sentence at the pause after "你好", so the
     * question is often empty here and arrives as the next sentence: the
     * service then treats whatever it hears next as the question.
     */
    data class Ask(val question: String, override val language: SpokenLanguage) : VoiceCommand
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
    private val SWITCH_VERBS = listOf("切换", "切回", "打开", "回到", "切", "去", "switch", "open", "goto")

    /** Said on its own or with a verb: the middle of the set, not an app. */
    private val CENTRE_NAMES = listOf("回中心", "中心", "回工作点", "centre", "center")

    /**
     * Before the apps, and before the centre: "关导航" names Maps by its
     * Chinese name, and must not be taken for switching to it.
     */
    private val STOP_NAVIGATION = listOf("关导航", "关闭导航", "停止导航", "结束导航", "stopnavigation", "endnavigation", "closenavigation")

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

    /**
     * Everything the recogniser listens for between questions, in Mandarin
     * only: with so few phrases a small model is sure of them. "你好" is here
     * because it opens the door to the whole language for the question after it.
     */
    val GRAMMAR: List<String> = listOf(
        "你好",
        "切地图", "切优步", "切送餐", "切应用", "切助手",
        "打开地图", "打开送餐", "打开应用", "回到应用", "回到地图",
        "地图", "送餐", "助手", "应用", "优步",
        "回中心", "回工作点",
        "关导航", "关闭导航", "停止导航", "结束导航",
    )

    /** The short sentences to say, and to steer the recognizer towards. */
    val PHRASES: List<String> = listOf(
        "切地图", "切优步", "切送餐", "切应用", "切助手",
        "地图", "送餐", "助手", "应用",
        "map", "uber eats", "application",
        "switch to map", "switch to uber eats", "switch to application",
        "回中心", "centre",
        "关导航", "stop navigation",
        "你好，现在送哪一单",
    )

    /**
     * The wake word for a question. Anything after it is the question, so it
     * is matched on the raw sentence before folding, and only at the start:
     * "你好" in the middle of a sentence is just a greeting.
     */
    private val WAKE = Regex("""^\s*(你好|哈喽|hello|hi)[\s,，、。!！]*(.*)$""", RegexOption.IGNORE_CASE)

    /** The recognizer offers several guesses, best first; the first that reads as a command wins. */
    fun parse(guesses: List<String>): VoiceCommand? = guesses.firstNotNullOfOrNull(::parseOne)

    /**
     * Only the commands that can act on a half-heard sentence. A question is
     * not one of them: acting on "你好，现在送" would answer the wrong question.
     */
    fun parsePartial(guesses: List<String>): VoiceCommand? = parse(guesses)?.takeUnless { it is VoiceCommand.Ask }

    private fun parseOne(sentence: String): VoiceCommand? {
        // A short sentence that ends in the wake word is the wake word, whatever
        // stray character the recogniser put in front of it.
        if (sentence.length <= 4 && sentence.endsWith("你好")) return VoiceCommand.Ask("", languageOf(sentence))
        WAKE.matchEntire(sentence)?.let { match ->
            return VoiceCommand.Ask(match.groupValues[2].trim(), languageOf(sentence))
        }
        val text = fold(sentence)
        if (STOP_NAVIGATION.any { text.contains(it) }) return VoiceCommand.StopNavigation(languageOf(sentence))
        // Before the apps: "回中心" names no app, and "中心" must not be taken
        // for one either.
        if (CENTRE_NAMES.any { text.contains(it) }) return VoiceCommand.DriveToCentre(languageOf(sentence))
        // The longest name heard wins: "接单助手" is this app, though "接单" is Uber.
        val target = NAMES
            .flatMap { (target, names) -> names.map { target to it } }
            .filter { (_, name) -> text.contains(name) }
            .maxByOrNull { (_, name) -> name.length }
            ?.first
            ?: return null
        val language = languageOf(sentence)
        return when {
            SWITCH_VERBS.any { text.contains(it) } -> VoiceCommand.SwitchTo(target, language)
            NAMES.any { (_, names) -> text in names } -> VoiceCommand.SwitchTo(target, language)
            else -> null
        }
    }

    /** Any Chinese character makes it Chinese: "切到 Google Map" is answered in Chinese. */
    private fun languageOf(sentence: String): SpokenLanguage =
        if (sentence.any { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.HAN }) SpokenLanguage.CHINESE
        else SpokenLanguage.ENGLISH

    /**
     * Lower case, no spaces, no punctuation: the recognizer writes "Google Map"
     * and "google map" alike, and may end a one-word sentence with "。".
     */
    private fun fold(sentence: String): String =
        sentence.lowercase().filter { it.isLetterOrDigit() }
}
