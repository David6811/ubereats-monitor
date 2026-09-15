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
        assertEquals(VoiceCommand.SwitchTo(VoiceTarget.MAPS, SpokenLanguage.CHINESE), command)
    }

    @Test
    fun `given switch to uber eats driver, when it is parsed, then uber comes to the front`() {
        // arrange
        val heard = listOf("切到Uber Eats司机端")

        // act
        val command = VoiceCommands.parse(heard)

        // assert
        assertEquals(VoiceCommand.SwitchTo(VoiceTarget.UBER, SpokenLanguage.CHINESE), command)
    }

    @Test
    fun `given switch to our app, when it is parsed, then this app comes to the front`() {
        // arrange
        val heard = listOf("切换到我们的app")

        // act
        val command = VoiceCommands.parse(heard)

        // assert
        assertEquals(VoiceCommand.SwitchTo(VoiceTarget.SELF, SpokenLanguage.CHINESE), command)
    }

    @Test
    fun `given the short chinese for uber, when it is parsed, then uber comes to the front`() {
        // arrange
        val heard = listOf("切优步")

        // act
        val command = VoiceCommands.parse(heard)

        // assert
        assertEquals(VoiceCommand.SwitchTo(VoiceTarget.UBER, SpokenLanguage.CHINESE), command)
    }

    @Test
    fun `given switch to the app, when it is parsed, then this app comes to the front`() {
        // arrange
        val heard = listOf("切应用")

        // act
        val command = VoiceCommands.parse(heard)

        // assert
        assertEquals(VoiceCommand.SwitchTo(VoiceTarget.SELF, SpokenLanguage.CHINESE), command)
    }

    @Test
    fun `given switch to delivering, when it is parsed, then uber comes to the front`() {
        // arrange
        val heard = listOf("切送餐")

        // act
        val command = VoiceCommands.parse(heard)

        // assert
        assertEquals(VoiceCommand.SwitchTo(VoiceTarget.UBER, SpokenLanguage.CHINESE), command)
    }

    @Test
    fun `given this app's full name, which contains uber's word for taking a job, when it is parsed, then this app wins`() {
        // arrange
        val heard = listOf("切到接单助手")

        // act
        val command = VoiceCommands.parse(heard)

        // assert
        assertEquals(VoiceCommand.SwitchTo(VoiceTarget.SELF, SpokenLanguage.CHINESE), command)
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
                VoiceCommand.SwitchTo(VoiceTarget.MAPS, SpokenLanguage.CHINESE),
                VoiceCommand.SwitchTo(VoiceTarget.UBER, SpokenLanguage.CHINESE),
                VoiceCommand.SwitchTo(VoiceTarget.UBER, SpokenLanguage.CHINESE),
                VoiceCommand.SwitchTo(VoiceTarget.SELF, SpokenLanguage.CHINESE),
                VoiceCommand.SwitchTo(VoiceTarget.SELF, SpokenLanguage.CHINESE),
                VoiceCommand.SwitchTo(VoiceTarget.MAPS, SpokenLanguage.CHINESE),
                VoiceCommand.SwitchTo(VoiceTarget.UBER, SpokenLanguage.CHINESE),
                VoiceCommand.SwitchTo(VoiceTarget.SELF, SpokenLanguage.CHINESE),
                VoiceCommand.SwitchTo(VoiceTarget.SELF, SpokenLanguage.CHINESE),
                VoiceCommand.SwitchTo(VoiceTarget.MAPS, SpokenLanguage.ENGLISH),
                VoiceCommand.SwitchTo(VoiceTarget.UBER, SpokenLanguage.ENGLISH),
                VoiceCommand.SwitchTo(VoiceTarget.SELF, SpokenLanguage.ENGLISH),
                VoiceCommand.SwitchTo(VoiceTarget.MAPS, SpokenLanguage.ENGLISH),
                VoiceCommand.SwitchTo(VoiceTarget.UBER, SpokenLanguage.ENGLISH),
                VoiceCommand.SwitchTo(VoiceTarget.SELF, SpokenLanguage.ENGLISH),
            ),
            commands,
        )
    }

    @Test
    fun `given only the map's name ending in a full stop, when it is parsed, then maps comes to the front`() {
        // arrange
        val heard = listOf("地图。")

        // act
        val command = VoiceCommands.parse(heard)

        // assert
        assertEquals(VoiceCommand.SwitchTo(VoiceTarget.MAPS, SpokenLanguage.CHINESE), command)
    }

    @Test
    fun `given uber eats said in english with a capital and a full stop, when it is parsed, then uber comes to the front`() {
        // arrange
        val heard = listOf("Uber Eats.")

        // act
        val command = VoiceCommands.parse(heard)

        // assert
        assertEquals(VoiceCommand.SwitchTo(VoiceTarget.UBER, SpokenLanguage.ENGLISH), command)
    }

    @Test
    fun `given application said in english, when it is parsed, then this app wins over the shorter app`() {
        // arrange
        val heard = listOf("Application")

        // act
        val command = VoiceCommands.parse(heard)

        // assert
        assertEquals(VoiceCommand.SwitchTo(VoiceTarget.SELF, SpokenLanguage.ENGLISH), command)
    }

    @Test
    fun `given switch to uber eats in english, when it is parsed, then uber comes to the front and is answered in english`() {
        // arrange
        val heard = listOf("Switch to Uber Eats")

        // act
        val command = VoiceCommands.parse(heard)

        // assert
        assertEquals(VoiceCommand.SwitchTo(VoiceTarget.UBER, SpokenLanguage.ENGLISH), command)
    }

    @Test
    fun `given an english sentence that mentions the map in passing, when it is parsed, then nothing happens`() {
        // arrange
        val heard = listOf("the map is wrong")

        // act
        val command = VoiceCommands.parse(heard)

        // assert
        assertNull(command)
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
        assertEquals(VoiceCommand.SwitchTo(VoiceTarget.MAPS, SpokenLanguage.CHINESE), command)
    }
}
