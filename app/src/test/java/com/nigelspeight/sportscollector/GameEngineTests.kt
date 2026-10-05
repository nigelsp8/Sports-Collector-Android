package com.nigelspeight.sportscollector

import com.nigelspeight.sportscollector.engine.Board
import com.nigelspeight.sportscollector.engine.EnginePhase
import com.nigelspeight.sportscollector.engine.GameEngine
import com.nigelspeight.sportscollector.engine.GameEvent
import com.nigelspeight.sportscollector.engine.GridPoint
import com.nigelspeight.sportscollector.engine.ObjectiveKind
import com.nigelspeight.sportscollector.engine.ShuffleSolver
import com.nigelspeight.sportscollector.engine.TileType.BLUE_TABLET
import com.nigelspeight.sportscollector.engine.TileType.ORANGE_BLUE_PILL
import com.nigelspeight.sportscollector.engine.TileType.ORANGE_TABLET
import com.nigelspeight.sportscollector.engine.TileType.PINK_TABLET
import com.nigelspeight.sportscollector.engine.TileType.PURPLE_TABLET
import com.nigelspeight.sportscollector.engine.TileType.SOLID_STAGE_1
import com.nigelspeight.sportscollector.engine.TileType.SOLID_STAGE_2
import com.nigelspeight.sportscollector.engine.TileType.YELLOW_TABLET
import com.nigelspeight.sportscollector.level.ActiveBlockSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun gp(row: Int, col: Int) = GridPoint(row, col)

class GameEngineIntegrationTests {
    @Test fun acceptedSwapSpendsAMoveScoresAndCanWin() {
        val level = TestLevel.make(
            rows = listOf(
                listOf(s(PINK_TABLET), s(ORANGE_TABLET), s(PINK_TABLET)),
                listOf(s(PINK_TABLET), s(PINK_TABLET), s(ORANGE_TABLET)),
            ),
            movesAllowed = 5,
            activeBlocks = mapOf(PINK_TABLET to ActiveBlockSpec(blocksToWin = 2, spawnPercentage = 0)),
        )
        val engine = GameEngine(level)
        runUntilSettled(engine) // clear intro fade

        assertTrue(engine.attemptSwap(gp(0, 1), gp(1, 1)))
        assertEquals(4, engine.movesRemaining)

        val events = runUntilSettled(engine)
        // 3 pink tiles removed, all counting toward the pink objective (+35 bonus each): 3*(375+35)
        assertEquals(1230, engine.score)
        assertEquals(EnginePhase.WON, engine.phase)
        assertTrue(GameEvent.Won(score = 1230, stars = 3) in events)
    }

    @Test fun rejectedSwapSpendsNoMoveAndMakesNoBoardChange() {
        // Cols 1-3 hold a legal move, so the board isn't "stuck" and won't
        // auto-shuffle; col 0's swap is unambiguously non-matching.
        val level = TestLevel.make(rows = listOf(
            listOf(s(YELLOW_TABLET), s(PINK_TABLET), s(ORANGE_TABLET), s(PINK_TABLET)),
            listOf(s(BLUE_TABLET), s(PINK_TABLET), s(PINK_TABLET), s(ORANGE_TABLET)),
        ), movesAllowed = 5)
        val engine = GameEngine(level)
        runUntilSettled(engine)

        val before = engine.movesRemaining
        assertFalse(engine.attemptSwap(gp(0, 0), gp(1, 0)))
        assertEquals(before, engine.movesRemaining)
        assertEquals(YELLOW_TABLET, engine.board[gp(0, 0)]?.tile)
        assertEquals(BLUE_TABLET, engine.board[gp(1, 0)]?.tile)
    }

    @Test fun runningOutOfMovesWithUnmetObjectivesLoses() {
        val level = TestLevel.make(
            rows = listOf(
                listOf(s(PINK_TABLET), s(ORANGE_TABLET), s(PINK_TABLET)),
                listOf(s(PINK_TABLET), s(PINK_TABLET), s(ORANGE_TABLET)),
            ),
            movesAllowed = 1,
            activeBlocks = mapOf(ORANGE_TABLET to ActiveBlockSpec(blocksToWin = 5, spawnPercentage = 0)),
        )
        val engine = GameEngine(level)
        runUntilSettled(engine)

        engine.attemptSwap(gp(0, 1), gp(1, 1))
        assertEquals(0, engine.movesRemaining)

        val events = runUntilSettled(engine)
        assertEquals(EnginePhase.LOST, engine.phase)
        assertTrue(GameEvent.Lost in events)
    }
}

class HintSystemTests {
    private fun levelWithOneLegalMove() = TestLevel.make(rows = listOf(
        listOf(s(PINK_TABLET), s(ORANGE_TABLET), s(PINK_TABLET)),
        listOf(s(PINK_TABLET), s(PINK_TABLET), s(ORANGE_TABLET)),
    ))

    @Test fun hintFiresOnlyAfterTheIdleThreshold() {
        val engine = GameEngine(levelWithOneLegalMove())
        engine.advance(GameEngine.INTRO_FADE_DURATION + 0.01)

        val early = engine.advance(GameEngine.HINT_IDLE_THRESHOLD - 0.5)
        assertFalse(early.any { it is GameEvent.HintSuggested })

        val late = engine.advance(1.0)
        assertTrue(late.any { it is GameEvent.HintSuggested })
    }

    @Test fun userInteractionResetsTheIdleTimer() {
        val engine = GameEngine(levelWithOneLegalMove())
        engine.advance(GameEngine.INTRO_FADE_DURATION + 0.01)

        engine.advance(GameEngine.HINT_IDLE_THRESHOLD - 0.5)
        engine.notifyUserInteraction()
        val afterReset = engine.advance(0.6)
        assertFalse(afterReset.any { it is GameEvent.HintSuggested })
    }

    @Test fun noHintsWhileSuspendedAndTimerRestartsWhenResumed() {
        val engine = GameEngine(levelWithOneLegalMove())
        engine.hintsSuspended = true
        engine.advance(GameEngine.INTRO_FADE_DURATION + 0.01)

        // Well past the idle threshold, in frame-sized steps, with a popup up.
        repeat(600) { assertFalse(engine.advance(0.05).any { it is GameEvent.HintSuggested }) }

        // Once the popup closes, the player gets the full idle period again.
        engine.hintsSuspended = false
        assertFalse(engine.advance(GameEngine.HINT_IDLE_THRESHOLD - 0.5).any { it is GameEvent.HintSuggested })
        assertTrue(engine.advance(1.0).any { it is GameEvent.HintSuggested })
    }

    @Test fun repeatedIdleHintsCycleThroughDifferentLegalMovesWhenMoreThanOneExists() {
        val level = TestLevel.make(rows = listOf(
            listOf(s(PINK_TABLET), s(ORANGE_TABLET), s(PINK_TABLET), s(YELLOW_TABLET), s(BLUE_TABLET), s(YELLOW_TABLET)),
            listOf(s(PINK_TABLET), s(PINK_TABLET), s(ORANGE_TABLET), s(YELLOW_TABLET), s(YELLOW_TABLET), s(BLUE_TABLET)),
        ))
        val engine = GameEngine(level)
        engine.advance(GameEngine.INTRO_FADE_DURATION + 0.01)

        fun nextHintPair() = engine.advance(GameEngine.HINT_IDLE_THRESHOLD + 0.01)
            .filterIsInstance<GameEvent.HintSuggested>().firstOrNull()

        val first = nextHintPair()
        val second = nextHintPair()
        assertNotNull(first)
        assertNotNull(second)
        assertNotEquals(first, second)
    }
}

class ShuffleTests {
    @Test fun noLegalMoveTriggersShuffleAndReturnsToIdle() {
        // No swap on this board can ever produce a match.
        val level = TestLevel.make(rows = listOf(listOf(s(PINK_TABLET), s(ORANGE_TABLET), s(YELLOW_TABLET))))
        val engine = GameEngine(level)

        val allEvents = mutableListOf<GameEvent>()
        for (i in 0 until 200) {
            allEvents += engine.advance(0.1)
            if (engine.phase == EnginePhase.IDLE) break
        }

        assertTrue(GameEvent.NoMoreMovesShuffleStarted in allEvents)
        assertTrue(GameEvent.NoMoreMovesShuffleFinished in allEvents)
        assertEquals(EnginePhase.IDLE, engine.phase)
    }

    @Test fun shuffleResultIsAPermutationOfTheOriginalMultiset() {
        val level = TestLevel.make(rows = listOf(listOf(s(PINK_TABLET), s(ORANGE_TABLET), s(YELLOW_TABLET))))
        val board = Board(level)
        val shuffled = ShuffleSolver.shuffledBoard(board, seededRandomSource(7))

        val original = board.cells.mapNotNull { it.tile }.sortedBy { it.rawValue }
        val result = shuffled.cells.mapNotNull { it.tile }.sortedBy { it.rawValue }
        assertEquals(original, result)
    }
}

/// Regression coverage: when several cells in a spawn column become empty at
/// once, new tiles should appear in ALL of them, not just the first one.
class SpawnRefillTests {
    private fun placedPointsInFirstSpawnTick(engine: GameEngine, columns: Set<Int>): Set<GridPoint> {
        repeat(50) {
            val placed = engine.advance(0.05).filterIsInstance<GameEvent.TilePlaced>()
                .map { it.point }.filter { it.col in columns }
            if (placed.isNotEmpty()) return placed.toSet()
        }
        return emptySet()
    }

    @Test fun allSimultaneouslyEmptySpawnCellsInASingleColumnRefillTogether() {
        val level = TestLevel.make(
            rows = listOf(
                listOf(s(PINK_TABLET, isSpawnPoint = true), s(ORANGE_TABLET), s(PINK_TABLET), s(ORANGE_TABLET), s(PINK_TABLET)),
                listOf(s(PINK_TABLET, isSpawnPoint = true), s(YELLOW_TABLET), s(PINK_TABLET), s(PINK_TABLET), s(ORANGE_TABLET)),
                listOf(s(PINK_TABLET, isSpawnPoint = true), s(ORANGE_TABLET), s(YELLOW_TABLET), s(PURPLE_TABLET), s(YELLOW_TABLET)),
            ),
            activeBlocks = mapOf(PINK_TABLET to ActiveBlockSpec(0, 100)),
        )
        val engine = GameEngine(level)
        runUntilSettled(engine)

        // Swap (0,3)<->(1,3): row0 cols2-4 = [pink,pink,pink], which also causes
        // the engine to notice col0's pre-existing pink match.
        assertTrue(engine.attemptSwap(gp(0, 3), gp(1, 3)))
        assertEquals(setOf(gp(0, 0), gp(1, 0), gp(2, 0)), placedPointsInFirstSpawnTick(engine, setOf(0)))
    }

    @Test fun spawnCellsInDifferentColumnsBothRefillInTheSameTick() {
        val level = TestLevel.make(
            rows = listOf(
                listOf(s(PINK_TABLET, isSpawnPoint = true), s(ORANGE_TABLET), s(YELLOW_TABLET), s(BLUE_TABLET, isSpawnPoint = true)),
                listOf(s(PINK_TABLET, isSpawnPoint = true), s(YELLOW_TABLET), s(PURPLE_TABLET), s(BLUE_TABLET, isSpawnPoint = true)),
                listOf(s(ORANGE_TABLET), s(PINK_TABLET), s(YELLOW_TABLET), s(BLUE_TABLET, isSpawnPoint = true)),
            ),
            activeBlocks = mapOf(
                PINK_TABLET to ActiveBlockSpec(0, 50),
                BLUE_TABLET to ActiveBlockSpec(0, 50),
            ),
        )
        val engine = GameEngine(level)
        runUntilSettled(engine)

        assertTrue(engine.attemptSwap(gp(2, 0), gp(2, 1)))
        // (2,0) isn't a spawn point, so only the actual spawn cells refill together.
        assertEquals(
            setOf(gp(0, 0), gp(1, 0), gp(0, 3), gp(1, 3), gp(2, 3)),
            placedPointsInFirstSpawnTick(engine, setOf(0, 3)),
        )
    }
}

class SolidBlockerTests {
    @Test fun removalAdjacentToSolidAdvancesItsDamageStageWithoutScoring() {
        val level = TestLevel.make(rows = listOf(listOf(
            s(PINK_TABLET), s(ORANGE_TABLET), s(PINK_TABLET), s(PINK_TABLET), s(SOLID_STAGE_1),
        )))
        val engine = GameEngine(level)
        runUntilSettled(engine)

        // Swap col0<->col1: [orange, pink, pink, pink, solid1] -> cols 1-3 match, solid takes damage.
        assertTrue(engine.attemptSwap(gp(0, 0), gp(0, 1)))
        val events = runUntilSettled(engine)

        assertEquals(SOLID_STAGE_2, engine.board[gp(0, 4)]?.tile)
        assertEquals(1125, engine.score) // 3 plain tiles, no objectives: 3 * 375
        assertTrue(events.any { it is GameEvent.SolidDamaged && it.from == SOLID_STAGE_1 && it.to == SOLID_STAGE_2 })
        assertFalse(events.any { it is GameEvent.TileRemoved && it.tile == SOLID_STAGE_1 })
    }

    @Test fun solidNeverRelocatesDuringShuffle() {
        val level = TestLevel.make(rows = listOf(listOf(s(SOLID_STAGE_1), s(PINK_TABLET), s(ORANGE_TABLET))))
        var board = Board(level)
        val random = seededRandomSource(42)
        repeat(20) {
            board = ShuffleSolver.shuffledBoard(board, random)
            assertEquals(SOLID_STAGE_1, board[gp(0, 0)]?.tile)
        }
    }
}

class ComboPillTests {
    @Test fun matchedComboPillDowngradesInPlaceInsteadOfBeingRemoved() {
        val level = TestLevel.make(rows = listOf(listOf(
            s(ORANGE_TABLET), s(ORANGE_TABLET), s(YELLOW_TABLET), s(ORANGE_BLUE_PILL),
        )))
        val engine = GameEngine(level)
        runUntilSettled(engine)

        // Swap col2<->col3: [orange, orange, orangeBluePill, yellow] -> cols 0-2 match on orange.
        assertTrue(engine.attemptSwap(gp(0, 2), gp(0, 3)))
        val events = runUntilSettled(engine)

        val downgradedPoint = gp(0, 2)
        assertTrue(GameEvent.ComboPillDowngraded(downgradedPoint, ORANGE_BLUE_PILL, ORANGE_TABLET) in events)
        assertEquals(750, engine.score) // only the 2 plain tablets were removed
        assertFalse(events.any { it is GameEvent.TileRemoved && it.point == downgradedPoint })
    }
}

class WallTests {
    @Test fun swapAcrossAWallIsRefusedWithNoMoveSpent() {
        val level = TestLevel.make(rows = listOf(listOf(s(PINK_TABLET, hasWallRight = true), s(ORANGE_TABLET))), movesAllowed = 3)
        val engine = GameEngine(level)
        runUntilSettled(engine)

        val before = engine.movesRemaining
        assertFalse(engine.attemptSwap(gp(0, 0), gp(0, 1)))
        assertEquals(before, engine.movesRemaining)
    }

    @Test fun matchStraddlingAWallDestroysIt() {
        val level = TestLevel.make(rows = listOf(
            listOf(s(PINK_TABLET), s(ORANGE_TABLET)),
            listOf(s(PINK_TABLET, hasWallBelow = true), s(YELLOW_TABLET)),
            listOf(s(ORANGE_TABLET), s(PINK_TABLET)),
        ))
        val engine = GameEngine(level)
        runUntilSettled(engine)

        // Swap (2,0)<->(2,1): col 0 becomes pink/pink/pink, straddling the wall below (1,0).
        assertTrue(engine.attemptSwap(gp(2, 0), gp(2, 1)))
        val events = runUntilSettled(engine)
        assertTrue(GameEvent.WallDestroyed(gp(1, 0), gp(2, 0)) in events)
    }
}

class JellyObjectiveTests {
    @Test fun firstMatchOnJellyClearsItAndDecrementsTheAutoAddedObjective() {
        val level = TestLevel.make(rows = listOf(
            listOf(s(PINK_TABLET, hasJelly = true), s(ORANGE_TABLET, hasJelly = true), s(PINK_TABLET)),
            listOf(s(PINK_TABLET), s(PINK_TABLET), s(ORANGE_TABLET)),
        ))
        val engine = GameEngine(level)
        runUntilSettled(engine)

        assertEquals(1, engine.objectives.size)
        assertEquals(ObjectiveKind.ClearJelly, engine.objectives[0].kind)
        assertEquals(2, engine.objectives[0].remaining)

        // Jelly stays with the cell (not the tile), so (0,0) and (0,1) both still carry it.
        assertTrue(engine.attemptSwap(gp(0, 1), gp(1, 1)))
        val events = runUntilSettled(engine)

        assertEquals(2, events.count { it is GameEvent.JellyCleared })
        assertEquals(EnginePhase.WON, engine.phase)
    }
}

class InitialBoardFillTests {
    @Test fun everyActiveNonFixedCellGetsATileAtLevelStart() {
        val level = TestLevel.make(
            rows = listOf(
                listOf(s(null), s(null), s(null)),
                listOf(s(null), s(SOLID_STAGE_1), s(null)),
                listOf(s(null), s(null), s(null)),
            ),
            activeBlocks = mapOf(PINK_TABLET to ActiveBlockSpec(0, 50), ORANGE_TABLET to ActiveBlockSpec(0, 50)),
        )
        val engine = GameEngine(level)
        for (row in 0 until 3) for (col in 0 until 3) {
            assertNotNull("cell ($row,$col) should have a tile", engine.board[gp(row, col)]?.tile)
        }
        // The level-authored fixed solid must still be exactly where it was placed.
        assertEquals(SOLID_STAGE_1, engine.board[gp(1, 1)]?.tile)
    }

    @Test fun realLevelOneBoardStartsFullyPopulated() {
        val level = loadBundledLevel(1)
        val engine = GameEngine(level)
        level.cells.forEachIndexed { index, spec ->
            if (spec.isActive) assertNotNull(engine.board[gp(index / level.width, index % level.width)]?.tile)
        }
    }
}
