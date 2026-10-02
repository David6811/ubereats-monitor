package com.weixu.ueatsmonitor.domain

/**
 * Calculation. Which of the driver's rules a recorded reason came from, named the
 * way the settings and the laptop name it, so he knows where to change it.
 *
 * Read from the reason's words rather than stored beside them: jobs already on
 * the board carry only the words. [RulingText.reason] writes those words, and
 * the tests build every [Ruling] through it, so the two cannot drift apart.
 */
object RuleName {

    /**
     * Most specific first: "在远区名单里" also contains "在名单里". Both languages
     * are matched, since a reason recorded before the driver switched language
     * still has to be named.
     */
    private val BY_WORDS: List<Pair<String, (Words) -> String>> = listOf(
        "」里" to { w: Words -> w.ruleNoGoBox },
        "inside \"" to { w: Words -> w.ruleNoGoBox },
        "在黑名单里" to { w: Words -> w.ruleStoreDenyList },
        "deny list" to { w: Words -> w.ruleStoreDenyList },
        "离中心更远" to { w: Words -> w.ruleHomewardAway },
        "Leads away" to { w: Words -> w.ruleHomewardAway },
        "送完离中心" to { w: Words -> w.ruleNearCentreTooFar },
        "out, over your" to { w: Words -> w.ruleNearCentreTooFar },
        "分钟，超过" to { w: Words -> w.ruleTimeLimit },
        "min, over your" to { w: Words -> w.ruleTimeLimit },
        "远区单每小时" to { w: Words -> w.ruleFarPerHour },
        "an hour, under" to { w: Words -> w.ruleFarPerHour },
        "不在名单里" to { w: Words -> w.ruleAreas },
        "is not on your list" to { w: Words -> w.ruleAreas },
        "在远区名单里" to { w: Words -> w.ruleFarAreas },
        "on the far list" to { w: Words -> w.ruleFarAreas },
        "在名单里" to { w: Words -> w.ruleAreas },
        "is on your list" to { w: Words -> w.ruleAreas },
    )

    fun of(why: String, lang: Lang = Lang.CHINESE): String? =
        BY_WORDS.firstOrNull { (words, _) -> why.contains(words) }?.second?.invoke(wordsIn(lang))
}
