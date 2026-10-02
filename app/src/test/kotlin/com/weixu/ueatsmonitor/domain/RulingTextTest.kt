package com.weixu.ueatsmonitor.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The board keeps the headline that was on screen at the time, in whichever
 * language was chosen then. Reading it back has to work either way: it decides
 * which shelf a job lands on, and whether its card reads as a yes.
 */
class RulingTextTest {

    @Test
    fun `given a take written in Chinese, when it is read back, then it is a yes`() {
        // arrange
        val headline = RulingText.headline(Ruling.Take("Keysborough"), isMatch = false, lang = Lang.CHINESE)

        // affirm
        assertEquals("可以接单", headline)

        // act
        val yes = RulingText.saysTake(headline)

        // assert
        assertEquals(true, yes)
    }

    @Test
    fun `given a take written in English, when it is read back, then it is a yes`() {
        // arrange
        val headline = RulingText.headline(Ruling.Take("Keysborough"), isMatch = false, lang = Lang.ENGLISH)

        // affirm
        assertEquals("Take it", headline)

        // act
        val yes = RulingText.saysTake(headline)

        // assert
        assertEquals(true, yes)
    }

    @Test
    fun `given a Match worth entering in English, when it is read back, then it is a yes`() {
        // arrange
        val headline = RulingText.headline(Ruling.Take("Keysborough"), isMatch = true, lang = Lang.ENGLISH)

        // act
        val yes = RulingText.saysTake(headline)

        // assert
        assertEquals(true, yes)
    }

    @Test
    fun `given a leave written in English, when it is read back, then it is not a yes`() {
        // arrange
        val ruling = Ruling.Leave(Ruling.Reason.SuburbNotAllowed("Dandenong South"))
        val headline = RulingText.headline(ruling, isMatch = false, lang = Lang.ENGLISH)

        // affirm
        assertEquals("Leave it", headline)

        // act
        val yes = RulingText.saysTake(headline)

        // assert
        assertEquals(false, yes)
    }

    @Test
    fun `given a place that was not recognised, when it is read back, then it is not a yes`() {
        // arrange
        val headline = RulingText.headline(Ruling.Unknown, isMatch = false, lang = Lang.CHINESE)

        // act
        val yes = RulingText.saysTake(headline)

        // assert
        assertEquals(false, yes)
    }
}
