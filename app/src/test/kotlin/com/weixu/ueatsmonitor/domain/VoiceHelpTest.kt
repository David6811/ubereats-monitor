package com.weixu.ueatsmonitor.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceHelpTest {

    @Test
    fun `given the phrases the help offers, when checked, then the recognizer is listening for all of them`() {
        // arrange
        val offered = VoiceCommands.HELP

        // act
        val unheard = offered.filterNot { it in VoiceCommands.GRAMMAR }

        // assert
        assertEquals(emptyList<String>(), unheard)
    }

    @Test
    fun `given the prefixed forms the help promises, when checked, then they are listened for too`() {
        // arrange
        val promised = VoiceCommands.HELP_ALSO

        // act
        val unheard = promised.filterNot { it in VoiceCommands.GRAMMAR }

        // assert
        assertEquals(emptyList<String>(), unheard)
    }

    @Test
    fun `given the prefixed forms the help promises, when said, then each switches to its app`() {
        // arrange
        val promised = VoiceCommands.HELP_ALSO

        // act
        val targets = promised.map { (VoiceCommands.parse(listOf(it)) as? VoiceCommand.SwitchTo)?.target }

        // assert
        assertEquals(
            listOf(
                VoiceTarget.MAPS, VoiceTarget.UBER, VoiceTarget.SELF,
                VoiceTarget.MAPS, VoiceTarget.UBER, VoiceTarget.SELF,
            ),
            targets,
        )
    }

    @Test
    fun `given the phrases the help offers, when said, then every one of them is a command`() {
        // arrange
        val offered = VoiceCommands.HELP

        // act
        val understood = offered.filterNot { VoiceCommands.parse(listOf(it)) == null }

        // assert
        assertEquals(offered, understood)
    }

    @Test
    fun `given each phrase the help offers, when said, then it means what the help says it means`() {
        // arrange
        val offered = VoiceCommands.HELP

        // act
        val meanings = offered.map { VoiceCommands.parse(listOf(it))!!::class.simpleName }

        // assert
        assertEquals(
            listOf("SwitchTo", "SwitchTo", "SwitchTo", "DriveToCentre", "StopNavigation", "Ask", "StopListening"),
            meanings,
        )
    }
}
