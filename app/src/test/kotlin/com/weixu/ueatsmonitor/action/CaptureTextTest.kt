package com.weixu.ueatsmonitor.action

import com.weixu.ueatsmonitor.domain.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CaptureTextTest {

    @Test
    fun `given a capture with a fix, when read, then the recorded position comes back`() {
        // arrange
        val raw = CAPTURE_WITH_FIX

        // act
        val position = CaptureText.positionOf(raw)

        // assert
        assertEquals(GeoPoint(-37.9695, 145.1767), position)
    }

    @Test
    fun `given a capture with no fix, when read, then the position is null not zero`() {
        // arrange
        val raw = CAPTURE_WITHOUT_FIX

        // act
        val position = CaptureText.positionOf(raw)

        // assert
        assertNull(position)
    }

    @Test
    fun `given a capture with a fix, when read, then the fix timestamp comes back`() {
        // arrange
        val raw = CAPTURE_WITH_FIX

        // act
        val fixMillis = CaptureText.fixMillisOf(raw)

        // assert
        assertEquals(1_788_914_400_000L, fixMillis)
    }

    @Test
    fun `given a capture header, when the body is read, then the header is stripped`() {
        // arrange
        val raw = CAPTURE_WITH_FIX

        // act
        val body = CaptureText.bodyOf(raw)

        // assert
        assertEquals("Delivery request\n\$8.25\nKeysborough", body)
    }

    private companion object {
        val CAPTURE_WITH_FIX = """
            package=com.ubercab.driver
            millis=1788914523307
            lat=-37.9695
            lon=145.1767
            fix_millis=1788914400000
            ---
            Delivery request
            ${'$'}8.25
            Keysborough
        """.trimIndent()

        val CAPTURE_WITHOUT_FIX = """
            package=com.ubercab.driver
            millis=1788914523307
            ---
            Home
        """.trimIndent()
    }
}
