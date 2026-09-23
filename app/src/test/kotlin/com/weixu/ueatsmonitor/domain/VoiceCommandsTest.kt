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
        val heard = listOf("切送餐")

        // act
        val command = VoiceCommands.parse(heard)

        // assert
        assertEquals(VoiceCommand.SwitchTo(VoiceTarget.UBER, SpokenLanguage.CHINESE), command)
    }

    @Test
    fun `given the word dropped from the list, when it is parsed, then nothing happens`() {
        // arrange  "优步" was taken off both the grammar and the names
        val heard = listOf("切优步")

        // act
        val command = VoiceCommands.parse(heard)

        // assert
        assertEquals(null, command)
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
                VoiceCommand.DriveToCentre(SpokenLanguage.CHINESE),
                VoiceCommand.DriveToCentre(SpokenLanguage.ENGLISH),
                VoiceCommand.StopNavigation(SpokenLanguage.CHINESE),
                VoiceCommand.StopNavigation(SpokenLanguage.ENGLISH),
                VoiceCommand.Ask("现在送哪一单", SpokenLanguage.CHINESE),
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
    fun `given 回中心, when it is parsed, then it is the drive back to the centre`() {
        // arrange
        val heard = listOf("回中心")

        // act
        val command = VoiceCommands.parse(heard)

        // assert
        assertEquals(VoiceCommand.DriveToCentre(SpokenLanguage.CHINESE), command)
    }

    @Test
    fun `given centre said in english, when it is parsed, then it is answered in english`() {
        // arrange
        val heard = listOf("Centre")

        // act
        val command = VoiceCommands.parse(heard)

        // assert
        assertEquals(VoiceCommand.DriveToCentre(SpokenLanguage.ENGLISH), command)
    }

    @Test
    fun `given 关导航, when it is parsed, then it stops the navigation rather than switching to maps`() {
        // arrange  "导航" alone is a name for Maps
        val heard = listOf("关导航")

        // act
        val command = VoiceCommands.parse(heard)

        // assert
        assertEquals(VoiceCommand.StopNavigation(SpokenLanguage.CHINESE), command)
    }

    @Test
    fun `given stop navigation in english, when it is parsed, then it is answered in english`() {
        // arrange
        val heard = listOf("Stop navigation")

        // act
        val command = VoiceCommands.parse(heard)

        // assert
        assertEquals(VoiceCommand.StopNavigation(SpokenLanguage.ENGLISH), command)
    }

    @Test
    fun `given 打开导航, when it is parsed, then it still switches to maps`() {
        // arrange
        val heard = listOf("打开导航")

        // act
        val command = VoiceCommands.parse(heard)

        // assert
        assertEquals(VoiceCommand.SwitchTo(VoiceTarget.MAPS, SpokenLanguage.CHINESE), command)
    }

    @Test
    fun `given 你好 and a question, when it is parsed, then the question goes to the assistant without the wake word`() {
        // arrange
        val heard = listOf("你好，现在送哪一单")

        // act
        val command = VoiceCommands.parse(heard)

        // assert
        assertEquals(VoiceCommand.Ask("现在送哪一单", SpokenLanguage.CHINESE), command)
    }

    @Test
    fun `given 你好 on its own, when it is parsed, then it is a question still to be asked`() {
        // arrange  the recognizer ends the sentence at the pause after the wake word
        val heard = listOf("你好")

        // act
        val command = VoiceCommands.parse(heard)

        // assert
        assertEquals(VoiceCommand.Ask("", SpokenLanguage.CHINESE), command)
    }

    @Test
    fun `given 你好 in the middle of a sentence, when it is parsed, then it is not a question`() {
        // arrange  a greeting to a passenger that happens to name an app afterwards
        val heard = listOf("跟你说你好切地图")

        // act
        val command = VoiceCommands.parse(heard)

        // assert
        assertEquals(VoiceCommand.SwitchTo(VoiceTarget.MAPS, SpokenLanguage.CHINESE), command)
    }

    @Test
    fun `given a half-heard question, when it is parsed as partial, then it waits for the whole sentence`() {
        // arrange
        val heard = listOf("你好，现在送")

        // act
        val command = VoiceCommands.parsePartial(heard)

        // assert
        assertEquals(null, command)
    }

    @Test
    fun `given 关语音, when it is parsed, then it stops listening`() {
        // arrange
        val heard = listOf("关语音")

        // act
        val command = VoiceCommands.parse(heard)

        // assert
        assertEquals(VoiceCommand.StopListening(SpokenLanguage.CHINESE), command)
    }

    @Test
    fun `given 不聊了, when checked, then it ends the conversation`() {
        // arrange
        val heard = "不聊了"

        // act
        val goodbye = VoiceCommands.isGoodbye(heard)

        // assert
        assertEquals(true, goodbye)
    }

    @Test
    fun `given a long sentence that happens to contain 好了, when checked, then it is a question, not a goodbye`() {
        // arrange
        val heard = "餐准备好了没有"

        // act
        val goodbye = VoiceCommands.isGoodbye(heard)

        // assert
        assertEquals(false, goodbye)
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
