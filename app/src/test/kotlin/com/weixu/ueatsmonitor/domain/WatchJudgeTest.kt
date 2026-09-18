package com.weixu.ueatsmonitor.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class WatchJudgeTest {

    @Test
    fun `given the reader on and a frame two seconds ago, when judged, then it is watching`() {
        // arrange
        val sinceLastFrame = 2_000L

        // act
        val watch = WatchJudge.judge(readerGranted = true, sinceLastFrameMillis = sinceLastFrame, overlayGranted = true)

        // assert
        assertEquals(Watch.Watching, watch)
    }

    @Test
    fun `given the reader off, when judged, then the reader is what is missing`() {
        // arrange
        val sinceLastFrame = 2_000L

        // act
        val watch = WatchJudge.judge(readerGranted = false, sinceLastFrameMillis = sinceLastFrame, overlayGranted = true)

        // assert
        assertEquals(Watch.NotWatching(Missing.READER), watch)
    }

    @Test
    fun `given the reader on but no frame for seven seconds, when judged, then the reader has stalled`() {
        // arrange
        val sinceLastFrame = 7_000L

        // act
        val watch = WatchJudge.judge(readerGranted = true, sinceLastFrameMillis = sinceLastFrame, overlayGranted = true)

        // assert
        assertEquals(Watch.NotWatching(Missing.READER_STALLED), watch)
    }

    @Test
    fun `given a frame exactly six seconds ago, when judged, then it is still watching`() {
        // arrange
        val sinceLastFrame = 6_000L

        // act
        val watch = WatchJudge.judge(readerGranted = true, sinceLastFrameMillis = sinceLastFrame, overlayGranted = true)

        // assert
        assertEquals(Watch.Watching, watch)
    }

    @Test
    fun `given the reader running but no overlay permission, when judged, then the overlay is what is missing`() {
        // arrange
        val sinceLastFrame = 2_000L

        // act
        val watch = WatchJudge.judge(readerGranted = true, sinceLastFrameMillis = sinceLastFrame, overlayGranted = false)

        // assert
        assertEquals(Watch.NotWatching(Missing.OVERLAY), watch)
    }

    @Test
    fun `given the reader off and no overlay permission, when judged, then the reader is named first`() {
        // arrange
        val sinceLastFrame = 60_000L

        // act
        val watch = WatchJudge.judge(readerGranted = false, sinceLastFrameMillis = sinceLastFrame, overlayGranted = false)

        // assert
        assertEquals(Watch.NotWatching(Missing.READER), watch)
    }
}
