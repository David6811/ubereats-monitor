package com.weixu.ueatsmonitor.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ActiveSetTest {

    private val known = setOf("默认", "晚上", "工作点近安全区")

    @Test
    fun `given the phone chose after the last push, when asked, then the phone's set is in force`() {
        // arrange
        val pickedAt = 2_000L
        val pushedAt = 1_000L

        // act
        val live = ActiveSet.inForce("晚上", pickedAt, "工作点近安全区", pushedAt, known)

        // assert
        assertEquals("晚上", live)
    }

    @Test
    fun `given a push after the phone chose, when asked, then the laptop's set is in force`() {
        // arrange
        val pickedAt = 1_000L
        val pushedAt = 2_000L

        // act
        val live = ActiveSet.inForce("晚上", pickedAt, "工作点近安全区", pushedAt, known)

        // assert
        assertEquals("工作点近安全区", live)
    }

    @Test
    fun `given the phone never chose, when asked, then the laptop's set is in force`() {
        // arrange
        val nothingPicked: String? = null

        // act
        val live = ActiveSet.inForce(nothingPicked, 0, "默认", 5_000, known)

        // assert
        assertEquals("默认", live)
    }

    @Test
    fun `given the phone chose a set since deleted, when asked, then the laptop's set is in force`() {
        // arrange
        val gone = "回归到工作点"

        // affirm
        assertEquals(false, known.contains(gone))

        // act
        val live = ActiveSet.inForce(gone, 9_000, "默认", 1_000, known)

        // assert
        assertEquals("默认", live)
    }
}
