package com.nigelspeight.sportscollector

import com.nigelspeight.sportscollector.engine.Objective
import com.nigelspeight.sportscollector.engine.ObjectiveKind
import com.nigelspeight.sportscollector.level.GameSection
import com.nigelspeight.sportscollector.level.LevelListRow
import com.nigelspeight.sportscollector.level.SectionRows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SectionRowsTests {
    private fun progress(vararg wonMapIDs: Int): (Int) -> LevelProgressStore.Result? =
        { mapID -> if (mapID in wonMapIDs) LevelProgressStore.Result(score = mapID * 1000, stars = 2) else null }

    private val noObjectives: (Int) -> List<ObjectiveKind> = { emptyList() }

    @Test fun sectionsCoverConsecutiveTwentyLevelRanges() {
        assertEquals(1..20, GameSection.SOCCER.levelRange)
        assertEquals(21..40, GameSection.ICE_HOCKEY.levelRange)
        assertEquals(101..120, GameSection.RUGBY.levelRange)
        assertTrue(GameSection.BASKETBALL.hasPlayableLevels)
        assertFalse(GameSection.RUGBY.hasPlayableLevels)
    }

    @Test fun freshGameOffersLevelOneOnly() {
        val rows = SectionRows.build(GameSection.SOCCER, progress(), noObjectives)
        assertEquals(listOf(LevelListRow.NextLevel(1, emptyList())), rows)
    }

    @Test fun wonLevelsAppearInReverseOrderBelowTheNextLevel() {
        val rows = SectionRows.build(GameSection.SOCCER, progress(1, 2, 3), noObjectives)
        assertEquals(listOf(4, 3, 2, 1), rows.map {
            when (it) {
                is LevelListRow.NextLevel -> it.mapID
                is LevelListRow.Won -> it.mapID
                else -> -1
            }
        })
        assertTrue(rows[0] is LevelListRow.NextLevel)
    }

    @Test fun progressStopsAtTheFirstGap() {
        // Level 3 won out of order doesn't count - the frontier is level 2.
        val rows = SectionRows.build(GameSection.SOCCER, progress(1, 3), noObjectives)
        assertEquals(LevelListRow.NextLevel(2, emptyList()), rows[0])
        assertEquals(2, rows.size)
    }

    @Test fun nextSectionStaysLockedUntilPreviousIsFullyWon() {
        val rows = SectionRows.build(GameSection.ICE_HOCKEY, progress(*(1..19).toList().toIntArray()), noObjectives)
        assertEquals(listOf(LevelListRow.Locked(GameSection.SOCCER)), rows)
    }

    @Test fun completedSectionPointsAtTheNextOne() {
        val all = (1..20).toList().toIntArray()
        val soccer = SectionRows.build(GameSection.SOCCER, progress(*all), noObjectives)
        assertEquals(LevelListRow.AdvanceToNextSection(GameSection.ICE_HOCKEY), soccer[0])
        assertEquals(21, soccer.size)

        val hockey = SectionRows.build(GameSection.ICE_HOCKEY, progress(*all), noObjectives)
        assertEquals(listOf(LevelListRow.NextLevel(21, emptyList())), hockey)
    }

    @Test fun rugbyIsComingSoonOnceBasketballIsDone() {
        val rows = SectionRows.build(GameSection.RUGBY, progress(*(1..100).toList().toIntArray()), noObjectives)
        assertEquals(listOf(LevelListRow.ComingSoon), rows)
    }

    @Test fun nextLevelCarriesThatLevelsObjectives() {
        val rows = SectionRows.build(GameSection.SOCCER, progress()) { mapID ->
            Objective.objectivesFor(loadBundledLevel(mapID)).map { it.kind }
        }
        val expected = Objective.objectivesFor(loadBundledLevel(1)).map { it.kind }
        assertTrue(expected.isNotEmpty())
        assertEquals(LevelListRow.NextLevel(1, expected), rows[0])
    }
}
