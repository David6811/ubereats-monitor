package com.weixu.ueatsmonitor.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceCommandsTest {

    @Test
    fun `given switch to google map, when it is parsed, then maps comes to the front`() {
        // arrange
        val heard = listOf("切换到 Google Map")

        // act
        val command = VoiceCommands.parse(heard)

        // assert
        assertEquals(VoiceCommand.SwitchTo(VoiceTarget.MAPS), command)
    }

    @Test
    fun `given switch to uber eats driver, when it is parsed, then uber comes to the front`() {
        // arrange
        val heard = listOf("切到Uber Eats司机端")

        // act
        val command = VoiceCommands.parse(heard)

        // assert
        assertEquals(VoiceCommand.SwitchTo(VoiceTarget.UBER), command)
    }

    @Test
    fun `given switch to our app, when it is parsed, then this app comes to the front`() {
        // arrange
        val heard = listOf("切换到我们的app")

        // act
        val command = VoiceCommands.parse(heard)

        // assert
        assertEquals(VoiceCommand.SwitchTo(VoiceTarget.SELF), command)
    }

    @Test
    fun `given close google map, when it is parsed, then maps is closed rather than opened`() {
        // arrange
        val heard = listOf("关闭谷歌地图")

        // act
        val command = VoiceCommands.parse(heard)

        // assert
        assertEquals(VoiceCommand.Close(VoiceTarget.MAPS), command)
    }

    @Test
    fun `given the short chinese for uber, when it is parsed, then uber comes to the front`() {
        // arrange
        val heard = listOf("切优步")

        // act
        val command = VoiceCommands.parse(heard)

        // assert
        assertEquals(VoiceCommand.SwitchTo(VoiceTarget.UBER), command)
    }

    @Test
    fun `given the short chinese for closing the map, when it is parsed, then maps is closed`() {
        // arrange
        val heard = listOf("关地图")

        // act
        val command = VoiceCommands.parse(heard)

        // assert
        assertEquals(VoiceCommand.Close(VoiceTarget.MAPS), command)
    }

    @Test
    fun `given this app's full name, which contains uber's word for taking a job, when it is parsed, then this app wins`() {
        // arrange
        val heard = listOf("切到接单助手")

        // act
        val command = VoiceCommands.parse(heard)

        // assert
        assertEquals(VoiceCommand.SwitchTo(VoiceTarget.SELF), command)
    }

    @Test
    fun `given each phrase offered to the driver, when they are parsed, then every one is a command`() {
        // arrange
        val phrases = VoiceCommands.PHRASES

        // act
        val commands = phrases.map { VoiceCommands.parse(listOf(it)) }

        // assert
        assertEquals(
            listOf(
                VoiceCommand.SwitchTo(VoiceTarget.MAPS),
                VoiceCommand.SwitchTo(VoiceTarget.UBER),
                VoiceCommand.SwitchTo(VoiceTarget.SELF),
                VoiceCommand.Close(VoiceTarget.MAPS),
            ),
            commands,
        )
    }

    @Test
    fun `given an app named in passing with no verb, when it is parsed, then nothing happens`() {
        // arrange
        val heard = listOf("这个地图不太准")

        // act
        val command = VoiceCommands.parse(heard)

        // assert
        assertNull(command)
    }

    @Test
    fun `given the best guess is not a command, when a later guess is, then the later guess is used`() {
        // arrange
        val heard = listOf("切换到地", "切换到地图")

        // act
        val command = VoiceCommands.parse(heard)

        // assert
        assertEquals(VoiceCommand.SwitchTo(VoiceTarget.MAPS), command)
    }
}
