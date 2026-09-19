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

    /** Most specific first: "在远区名单里" also contains "在名单里". */
    private val BY_WORDS: List<Pair<String, String>> = listOf(
        "」里" to "不接区",
        "在黑名单里" to "店铺黑名单",
        "离中心更远" to "回中心模式（送完离中心更远）",
        "送完离中心" to "近中心模式（送得太远）",
        "分钟，超过" to "回中心 / 近中心模式（时间太长）",
        "远区单每小时" to "远区每小时最低",
        "不在名单里" to "选区",
        "在远区名单里" to "远区",
        "在名单里" to "选区",
    )

    fun of(why: String): String? = BY_WORDS.firstOrNull { (words, _) -> why.contains(words) }?.second
}
