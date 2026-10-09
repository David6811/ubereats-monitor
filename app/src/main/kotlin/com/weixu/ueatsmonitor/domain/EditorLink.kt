package com.weixu.ueatsmonitor.domain

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.net.URLEncoder
import java.util.Base64

/** Data. The app's sign-in, as handed to the web editor. */
data class HandedSession(val access: String, val refresh: String)

/**
 * Calculation. The address that opens the web rules editor already signed in.
 *
 * The editor is the laptop's, map and all - the phone opens the same page
 * rather than a second editor of its own. The sign-in rides in the fragment,
 * which a browser never sends to a server, as base64 JSON so the page reads it
 * back with atob; base64 can hold a '/' or '+', so it is URL-encoded on top.
 */
object EditorLink {

    const val PAGE = "https://david6811.github.io/ubereats-monitor/"

    fun url(session: HandedSession, page: String = PAGE): String {
        val json = buildJsonObject {
            put("access", session.access)
            put("refresh", session.refresh)
        }.toString()
        val packed = Base64.getEncoder().encodeToString(json.toByteArray(Charsets.UTF_8))
        return page + "#app-session=" + URLEncoder.encode(packed, "UTF-8")
    }
}
