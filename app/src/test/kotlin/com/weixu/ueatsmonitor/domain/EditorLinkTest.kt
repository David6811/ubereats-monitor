package com.weixu.ueatsmonitor.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class EditorLinkTest {

    @Test
    fun `given a session, when the link is made, then the page carries it as base64 JSON in the fragment`() {
        // arrange
        val session = HandedSession(access = "a1", refresh = "r1")

        // act
        val url = EditorLink.url(session, page = "https://example.test/editor/")

        // assert
        // base64 of {"access":"a1","refresh":"r1"}
        assertEquals("https://example.test/editor/#app-session=eyJhY2Nlc3MiOiJhMSIsInJlZnJlc2giOiJyMSJ9", url)
    }

    @Test
    fun `given a session whose base64 holds a slash, when the link is made, then the slash is URL-encoded`() {
        // arrange
        val session = HandedSession(access = "x.y_z-0", refresh = "r?r")

        // act
        val url = EditorLink.url(session, page = "https://example.test/editor/")

        // assert
        // base64 of {"access":"x.y_z-0","refresh":"r?r"} is ...InI/ciJ9; the '/' becomes %2F
        assertEquals("https://example.test/editor/#app-session=eyJhY2Nlc3MiOiJ4Lnlfei0wIiwicmVmcmVzaCI6InI%2FciJ9", url)
    }
}
