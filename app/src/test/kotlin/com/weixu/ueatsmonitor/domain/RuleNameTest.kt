package com.weixu.ueatsmonitor.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RuleNameTest {

    @Test
    fun `given a suburb outside the set, when named, then it is the suburb rule`() {
        // arrange
        val why = RulingText.reason(Ruling.Leave(Ruling.Reason.SuburbNotAllowed("Glen Waverley")))

        // act
        val rule = RuleName.of(why)

        // assert
        assertEquals("选区", rule)
    }

    @Test
    fun `given a denied store, when named, then it is the store deny list`() {
        // arrange
        val why = RulingText.reason(Ruling.Leave(Ruling.Reason.StoreDenied("Walrus BBQ")))

        // act
        val rule = RuleName.of(why)

        // assert
        assertEquals("店铺黑名单", rule)
    }

    @Test
    fun `given a drop inside a no-go box, when named, then it is the no-go box rule`() {
        // arrange
        val box = NoGoBox("Dandemong不接区", south = -37.99501, west = 145.20226, north = -37.97905, east = 145.22432)
        val why = RulingText.reason(Ruling.Leave(Ruling.Reason.InNoGoBox(NoGoHit.Dropoff(box))))

        // act
        val rule = RuleName.of(why)

        // assert
        assertEquals("不接区", rule)
    }

    @Test
    fun `given a pickup inside a no-go box, when named, then it is the no-go box rule`() {
        // arrange
        val box = NoGoBox("Springvale 西", south = -38.00, west = 145.10, north = -37.93, east = 145.14)
        val why = RulingText.reason(Ruling.Leave(Ruling.Reason.InNoGoBox(NoGoHit.Pickup(box, "Some Shop"))))

        // act
        val rule = RuleName.of(why)

        // assert
        assertEquals("不接区", rule)
    }

    @Test
    fun `given a drop leading away from the centre, when named, then it is the homeward rule`() {
        // arrange
        val why = RulingText.reason(Ruling.Leave(Ruling.Reason.LeadingAway(Homeward.Further(Miles(1.18), Miles(2.24)))))

        // act
        val rule = RuleName.of(why)

        // assert
        assertEquals("回中心模式（送完离中心更远）", rule)
    }

    @Test
    fun `given a job over the time limit, when named, then it is the homeward time limit`() {
        // arrange
        val why = RulingText.reason(Ruling.Leave(Ruling.Reason.TooLong(minutes = 32, max = 12)))

        // act
        val rule = RuleName.of(why)

        // assert
        assertEquals("回中心 / 近中心模式（时间太长）", rule)
    }

    @Test
    fun `given a drop too far from the centre, when named, then it is the near-centre rule`() {
        // arrange
        val why = RulingText.reason(Ruling.Leave(Ruling.Reason.TooFarFromCentre(Miles(3.5), maxKm = 4.0)))

        // act
        val rule = RuleName.of(why)

        // assert
        assertEquals("近中心模式（送得太远）", rule)
    }

    @Test
    fun `given a far job paying too little an hour, when named, then it is the far floor`() {
        // arrange
        val why = RulingText.reason(Ruling.Leave(Ruling.Reason.FarTooCheap(perHour = 8.5, floor = 10.0)))

        // act
        val rule = RuleName.of(why)

        // assert
        assertEquals("远区每小时最低", rule)
    }

    @Test
    fun `given a take on the far set, when named, then it is the far set, not the ordinary one`() {
        // arrange
        val why = RulingText.reason(Ruling.Take("Braeside", far = true))

        // act
        val rule = RuleName.of(why)

        // assert
        assertEquals("远区", rule)
    }

    @Test
    fun `given a take on the ordinary set, when named, then it is the suburb rule`() {
        // arrange
        val why = RulingText.reason(Ruling.Take("Keysborough"))

        // act
        val rule = RuleName.of(why)

        // assert
        assertEquals("选区", rule)
    }

    @Test
    fun `given words no rule writes, when named, then there is no rule`() {
        // arrange
        val why = RulingText.reason(Ruling.Unknown)

        // act
        val rule = RuleName.of(why)

        // assert
        assertNull(rule)
    }
}
