package com.weixu.ueatsmonitor.action

/** Calculation. Splits a capture file into its header facts and the screen text. */
object CaptureText {

    private const val SEPARATOR = "\n---\n"
    private val MILLIS = Regex("""^millis=(\d+)$""", RegexOption.MULTILINE)

    fun millisOf(raw: String): Long? = MILLIS.find(raw)?.groupValues?.get(1)?.toLongOrNull()

    fun bodyOf(raw: String): String = raw.substringAfter(SEPARATOR, raw).trim()

    /** The one line worth showing in a collapsed row: the money, when there is money. */
    fun previewOf(body: String): String {
        val lines = body.lines().filter { it.isNotBlank() }
        return lines.firstOrNull { it.contains('$') }
            ?: lines.firstOrNull()
            ?: "(空)"
    }

    fun hasMoney(body: String): Boolean = body.contains('$')
}
