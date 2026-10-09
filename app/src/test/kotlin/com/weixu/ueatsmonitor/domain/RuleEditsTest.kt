package com.weixu.ueatsmonitor.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The rules are the laptop editor's own file and the laptop goes on reading
 * them, so every change here has to leave everything it did not touch exactly
 * as it was - including keys this app has never heard of.
 */
class RuleEditsTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val rules: JsonObject = json.parseToJsonElement(
        """
        {
          "active": "ParkMore",
          "suburbs": { "allow": ["Noble Park"] },
          "stores": { "deny": ["Walrus BBQ"], "cbdDeny": [], "alwaysOk": [] },
          "far": { "overDollars": 30, "suburbs": ["Braeside"] },
          "noGo": [ { "label": "Tower block", "south": -38.0, "west": 145.1, "north": -37.9, "east": 145.2 } ],
          "profiles": [
            { "name": "ParkMore", "suburbs": ["Keysborough", "Noble Park"],
              "centre": { "label": "Parkmore", "lat": -37.99, "lon": 145.16 } },
            { "name": "Evenings", "suburbs": ["Dandenong"] }
          ],
          "places": [ { "label": "Wells Rd, Aspendale Gardens", "lat": -38.02506, "lon": 145.12873 } ],
          "version": 3,
          "somethingTheLaptopKnows": 7
        }
        """.trimIndent()
    ) as JsonObject

    @Test
    fun `given a shop to refuse, when it is added, then it joins the list`() {
        // arrange
        val name = "Hungry Jacks"

        // act
        val next = RuleEdits.denyStore(rules, name)

        // assert
        assertEquals(listOf("Walrus BBQ", "Hungry Jacks"), RuleEdits.deniedStores(next))
    }

    @Test
    fun `given a shop already refused, when it is added again in other case, then nothing changes`() {
        // arrange
        val name = "walrus bbq"

        // act
        val next = RuleEdits.denyStore(rules, name)

        // assert
        assertEquals(rules, next)
    }

    @Test
    fun `given a change to the shops, when it is made, then the laptop's own keys are kept`() {
        // arrange
        val name = "Hungry Jacks"

        // act
        val next = RuleEdits.denyStore(rules, name)

        // assert  a key this app knows nothing about is still there
        assertEquals(rules["somethingTheLaptopKnows"], next["somethingTheLaptopKnows"])
    }

    @Test
    fun `given a set is renamed, when it was the live one, then the live one follows it`() {
        // arrange
        val from = "ParkMore"

        // act
        val next = RuleEdits.renameSet(rules, from, "Parkmore evenings")

        // assert
        assertEquals("Parkmore evenings", RuleEdits.activeSet(next))
    }

    @Test
    fun `given a name already taken, when a set is renamed to it, then nothing changes`() {
        // arrange
        val taken = "Evenings"

        // act
        val next = RuleEdits.renameSet(rules, "ParkMore", taken)

        // assert
        assertEquals(rules, next)
    }

    @Test
    fun `given a set is copied, when the copy is made, then it has the same suburbs and centre`() {
        // arrange
        val from = "ParkMore"

        // act
        val next = RuleEdits.copySet(rules, from, "ParkMore copy")
        val copy = RuleEdits.sets(next).first { it.name == "ParkMore copy" }

        // affirm
        assertEquals(listOf("Keysborough", "Noble Park"), copy.suburbs)

        // assert
        assertEquals(RuleEdits.Centre("Parkmore", -37.99, 145.16), copy.centre)
    }

    @Test
    fun `given the live set is deleted, when it goes, then another becomes live`() {
        // arrange
        val live = "ParkMore"

        // act
        val next = RuleEdits.removeSet(rules, live)

        // assert  a live set naming nothing would leave the judge with no suburbs at all
        assertEquals("Evenings", RuleEdits.activeSet(next))
    }

    @Test
    fun `given a centre from the phone's own position, when it is set, then the set carries it`() {
        // arrange
        val here = RuleEdits.Centre("Where I am", -37.95, 145.15)

        // act
        val next = RuleEdits.setCentre(rules, "Evenings", here)

        // assert
        assertEquals(here, RuleEdits.sets(next).first { it.name == "Evenings" }.centre)
    }

    @Test
    fun `given suburbs tapped on the phone, when they are saved, then the set holds them sorted`() {
        // arrange
        val tapped = listOf("Noble Park", "Dandenong", "Noble Park")

        // act
        val next = RuleEdits.setSuburbs(rules, "Evenings", tapped)

        // assert
        assertEquals(listOf("Dandenong", "Noble Park"), RuleEdits.sets(next).first { it.name == "Evenings" }.suburbs)
    }

    @Test
    fun `given a box drawn on the phone, when it is added, then it joins the boxes`() {
        // arrange
        val box = NoGoBox("Car park", south = -38.1, west = 145.0, north = -38.0, east = 145.1)

        // act
        val next = RuleEdits.addBox(rules, box)

        // assert
        assertEquals(listOf("Tower block", "Car park"), RuleEdits.boxes(next).map { it.label })
    }

    @Test
    fun `given the far threshold, when it is changed, then the far suburbs are untouched`() {
        // arrange
        val dollars = 45

        // act
        val next = RuleEdits.setFarOverDollars(rules, dollars)

        // affirm
        assertEquals(45, RuleEdits.farOverDollars(next))

        // assert
        assertEquals(
            (rules["far"] as JsonObject)["suburbs"],
            (next["far"] as JsonObject)["suburbs"],
        )
    }

    @Test
    fun `given the laptop's saved addresses, when a set is added, then they are left alone`() {
        // arrange  "places" is the laptop's centre picker, not the sets; reading
        //          the sets from it found none at all on a real phone
        val before = rules["places"]

        // act
        val next = RuleEdits.addSet(rules, "Late nights", null, listOf("Clayton"))

        // affirm  the set did land under "profiles"
        assertEquals(listOf("ParkMore", "Evenings", "Late nights"), RuleEdits.sets(next).map { it.name })

        // assert
        assertEquals(before, next["places"])
    }

    @Test
    fun `given a suburb outside a set, when it is tapped, then it joins that set`() {
        // arrange
        val tapped = "Dandenong"

        // act
        val after = RuleEdits.toggleSuburb(rules, set = "ParkMore", suburb = tapped)

        // assert
        assertEquals(listOf("Dandenong", "Keysborough", "Noble Park"), RuleEdits.sets(after).first { it.name == "ParkMore" }.suburbs)
    }

    @Test
    fun `given a suburb inside a set, when it is tapped, then it leaves that set`() {
        // arrange
        val tapped = "Keysborough"

        // act
        val after = RuleEdits.toggleSuburb(rules, set = "ParkMore", suburb = tapped)

        // assert
        assertEquals(listOf("Noble Park"), RuleEdits.sets(after).first { it.name == "ParkMore" }.suburbs)
    }

    @Test
    fun `given the laptop's open set, when a suburb is tapped, then its working copy follows`() {
        // arrange
        val tapped = "Dandenong"

        // act
        val after = RuleEdits.toggleSuburb(rules, set = "ParkMore", suburb = tapped)

        // assert
        assertEquals(
            json.parseToJsonElement("""{ "allow": ["Dandenong", "Keysborough", "Noble Park"] }"""),
            after["suburbs"],
        )
    }

    @Test
    fun `given a set the laptop does not have open, when a suburb is tapped, then the working copy is left alone`() {
        // arrange
        val tapped = "Keysborough"

        // act
        val after = RuleEdits.toggleSuburb(rules, set = "Evenings", suburb = tapped)

        // assert
        assertEquals(rules["suburbs"], after["suburbs"])
    }
}
