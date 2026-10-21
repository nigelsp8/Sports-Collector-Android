package com.nigelspeight.sportscollector

import com.nigelspeight.sportscollector.engine.Objective
import com.nigelspeight.sportscollector.engine.ObjectiveKind
import com.nigelspeight.sportscollector.level.GameSection
import com.nigelspeight.sportscollector.level.LevelListRow
import com.nigelspeight.sportscollector.level.PunditCopy
import com.nigelspeight.sportscollector.level.SectionRows
import org.junit.Assert.assertEquals
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
        assertTrue(GameSection.RUGBY.hasPlayableLevels)
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

    @Test fun rugbyOpensOnceBasketballIsDone() {
        val rows = SectionRows.build(GameSection.RUGBY, progress(*(1..100).toList().toIntArray()), noObjectives)
        assertEquals(listOf(LevelListRow.NextLevel(101, emptyList())), rows)
    }

    @Test fun finishingRugbyCompletesTheGame() {
        val rows = SectionRows.build(GameSection.RUGBY, progress(*(1..120).toList().toIntArray()), noObjectives)
        assertEquals(LevelListRow.GameCompleted, rows.first())
    }

    @Test fun resumesOnLastPlayedLevelsSection() {
        assertEquals(GameSection.forMapID(1), GameSection.SOCCER)
        assertEquals(GameSection.forMapID(20), GameSection.SOCCER)
        assertEquals(GameSection.forMapID(21), GameSection.ICE_HOCKEY)
        assertEquals(GameSection.TENNIS, SectionRows.resumeSection(lastPlayedMapID = 65, bestResult = progress()))
    }

    @Test fun withNothingPlayedResumesAtTheProgressFrontier() {
        assertEquals(GameSection.SOCCER, SectionRows.resumeSection(null, progress()))
        val soccerDone = (1..20).toList().toIntArray()
        assertEquals(GameSection.ICE_HOCKEY, SectionRows.resumeSection(null, progress(*soccerDone)))
        val everythingDone = (1..120).toList().toIntArray()
        assertEquals(GameSection.RUGBY, SectionRows.resumeSection(null, progress(*everythingDone)))
    }

    @Test fun nextLevelCarriesThatLevelsObjectives() {
        val rows = SectionRows.build(GameSection.SOCCER, progress()) { mapID ->
            Objective.objectivesFor(loadBundledLevel(mapID)).map { it.kind }
        }
        val expected = Objective.objectivesFor(loadBundledLevel(1)).map { it.kind }
        assertTrue(expected.isNotEmpty())
        assertEquals(LevelListRow.NextLevel(1, expected), rows[0])
    }

    @Test fun punditUsesGenericCopyBeforeAnyWin() {
        assertEquals(
            PunditCopy("Olivia says...", "Collect the sports cards one at a time"),
            SectionRows.punditCopy(lastPlayedMapID = null, bestResult = progress()),
        )
        // Having merely played (not won) a level still counts as no progress.
        assertEquals("Olivia says...", SectionRows.punditCopy(lastPlayedMapID = 1, bestResult = progress()).speaker)
    }

    @Test fun punditTalksAboutTheResumeSection() {
        val soccerDone = (1..20).toList().toIntArray()
        assertEquals(
            PunditCopy("Ice Hockey card", "Let's make a start on collecting this card"),
            SectionRows.punditCopy(lastPlayedMapID = null, bestResult = progress(*soccerDone)),
        )
        assertEquals(
            PunditCopy("Soccer card", "Nearly there, WOW!"),
            SectionRows.punditCopy(lastPlayedMapID = 20, bestResult = progress(*soccerDone)),
        )
    }

    @Test fun punditMessageTracksWinsInTheSection() {
        fun messageAfterWins(count: Int) =
            SectionRows.punditCopy(lastPlayedMapID = 1, bestResult = progress(*(1..count).toList().toIntArray())).message
        assertEquals("Great start to collecting this card", messageAfterWins(1))
        assertEquals("Great start to collecting this card", messageAfterWins(6))
        assertEquals("Keep up the great work", messageAfterWins(7))
        assertEquals("Keep up the great work", messageAfterWins(14))
        assertEquals("Nearly there, WOW!", messageAfterWins(15))
    }
}
