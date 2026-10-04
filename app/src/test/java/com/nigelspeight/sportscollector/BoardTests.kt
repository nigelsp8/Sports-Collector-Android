package com.nigelspeight.sportscollector

import com.nigelspeight.sportscollector.engine.Board
import com.nigelspeight.sportscollector.engine.FallMove
import com.nigelspeight.sportscollector.engine.GridPoint
import com.nigelspeight.sportscollector.engine.MatchGroup
import com.nigelspeight.sportscollector.engine.MoveKind
import com.nigelspeight.sportscollector.engine.TileColor
import com.nigelspeight.sportscollector.engine.TileType
import com.nigelspeight.sportscollector.engine.TileType.BLUE_TABLET
import com.nigelspeight.sportscollector.engine.TileType.GREEN_TABLET
import com.nigelspeight.sportscollector.engine.TileType.ORANGE_BLUE_PILL
import com.nigelspeight.sportscollector.engine.TileType.ORANGE_TABLET
import com.nigelspeight.sportscollector.engine.TileType.PINK_BACTERIA
import com.nigelspeight.sportscollector.engine.TileType.PINK_TABLET
import com.nigelspeight.sportscollector.engine.TileType.SOLID_STAGE_1
import com.nigelspeight.sportscollector.engine.TileType.YELLOW_TABLET
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BoardTests {
    private fun t(tile: TileType?) = TestLevel.Spec(tile = tile)
    private fun inactive() = TestLevel.Spec(isActive = false)
    private fun makeBoard(rows: List<List<TestLevel.Spec>>) = Board(TestLevel.make(id = 0, rows = rows))
    private fun gp(row: Int, col: Int) = GridPoint(row, col)

    // Swapping

    @Test fun orthogonalAdjacentTilesCanSwap() {
        val board = makeBoard(listOf(listOf(t(PINK_TABLET), t(ORANGE_TABLET)), listOf(t(YELLOW_TABLET), t(GREEN_TABLET))))
        assertTrue(board.canSwap(gp(0, 0), gp(0, 1)))
        assertTrue(board.canSwap(gp(0, 0), gp(1, 0)))
    }

    @Test fun diagonalTilesCannotSwap() {
        val board = makeBoard(listOf(listOf(t(PINK_TABLET), t(ORANGE_TABLET)), listOf(t(YELLOW_TABLET), t(GREEN_TABLET))))
        assertFalse(board.canSwap(gp(0, 0), gp(1, 1)))
    }

    @Test fun inactiveCellsCannotSwap() {
        val board = makeBoard(listOf(listOf(t(PINK_TABLET), inactive())))
        assertFalse(board.canSwap(gp(0, 0), gp(0, 1)))
    }

    @Test fun wallBetweenCellsBlocksSwap() {
        val board = makeBoard(listOf(listOf(t(PINK_TABLET), t(ORANGE_TABLET))))
        board.mutate(gp(0, 0)) { it.copy(hasWallRight = true) }
        assertFalse(board.canSwap(gp(0, 0), gp(0, 1)))
    }

    // Matching

    @Test fun threeInARowHorizontalMatches() {
        val board = makeBoard(listOf(listOf(t(PINK_TABLET), t(PINK_TABLET), t(PINK_TABLET), t(ORANGE_TABLET))))
        assertTrue(board.findMatches().any { it.cells.size == 3 && it.color == TileColor.PINK && it.cause == MatchGroup.Cause.COLOR_RUN })
    }

    @Test fun twoInARowDoesNotMatch() {
        val board = makeBoard(listOf(listOf(t(PINK_TABLET), t(PINK_TABLET), t(ORANGE_TABLET))))
        assertTrue(board.findMatches().isEmpty())
    }

    @Test fun threeInAColumnVerticallyMatches() {
        val board = makeBoard(listOf(listOf(t(BLUE_TABLET)), listOf(t(BLUE_TABLET)), listOf(t(BLUE_TABLET))))
        assertTrue(board.findMatches().any { it.cells.size == 3 && it.color == TileColor.BLUE })
    }

    @Test fun comboPillMatchesPlainTabletsOfItsCarriedColor() {
        val board = makeBoard(listOf(listOf(t(ORANGE_TABLET), t(ORANGE_BLUE_PILL), t(ORANGE_TABLET))))
        assertTrue(board.findMatches().any { it.cells.size == 3 && it.color == TileColor.ORANGE })
    }

    @Test fun bacteriaAtBottomOfColumnAutoMatches() {
        val board = makeBoard(listOf(listOf(t(PINK_TABLET)), listOf(t(PINK_BACTERIA))))
        assertTrue(board.findMatches().any { it.cause == MatchGroup.Cause.BOTTOM_ROW_BACTERIA && it.cells == listOf(gp(1, 0)) })
    }

    @Test fun bacteriaAboveInactiveCellCountsAsColumnBottom() {
        val board = makeBoard(listOf(listOf(t(PINK_BACTERIA)), listOf(inactive())))
        assertTrue(board.findMatches().any { it.cause == MatchGroup.Cause.BOTTOM_ROW_BACTERIA && it.cells == listOf(gp(0, 0)) })
    }

    @Test fun bacteriaNotAtColumnBottomDoesNotAutoMatch() {
        val board = makeBoard(listOf(listOf(t(PINK_BACTERIA)), listOf(t(ORANGE_TABLET))))
        assertFalse(board.findMatches().any { it.cause == MatchGroup.Cause.BOTTOM_ROW_BACTERIA })
    }

    @Test fun solidsNeverColorMatch() {
        val board = makeBoard(listOf(listOf(t(SOLID_STAGE_1), t(SOLID_STAGE_1), t(SOLID_STAGE_1))))
        assertTrue(board.findMatches().isEmpty())
    }

    // Gravity: straight down

    @Test fun tileFallsStraightDownIntoEmptyCellBelow() {
        val board = makeBoard(listOf(listOf(t(PINK_TABLET)), listOf(t(null))))
        assertEquals(listOf(FallMove(gp(0, 0), gp(1, 0), MoveKind.STRAIGHT_DOWN)), board.computeFallMoves())
    }

    @Test fun tileDoesNotFallThroughItsOwnWallBelow() {
        val board = makeBoard(listOf(listOf(t(PINK_TABLET)), listOf(t(null))))
        board.mutate(gp(0, 0)) { it.copy(hasWallBelow = true) }
        assertTrue(board.computeFallMoves().isEmpty())
    }

    @Test fun fixedSolidAnchorNeverFalls() {
        val board = makeBoard(listOf(listOf(t(SOLID_STAGE_1)), listOf(t(null))))
        assertTrue(board.computeFallMoves().isEmpty())
    }

    // Gravity: diagonal chasm

    @Test fun tileSlidesDiagonallyWhenStraightDownBlockedButChasmIsOpen() {
        val board = makeBoard(listOf(
            listOf(t(PINK_TABLET), inactive(), inactive()),
            listOf(inactive(), t(null), inactive()),
        ))
        assertEquals(listOf(FallMove(gp(0, 0), gp(1, 1), MoveKind.DIAGONAL_RIGHT)), board.computeFallMoves())
    }

    @Test fun deepChasmPocketAllowsDiagonalSlideWhenUnobstructed() {
        val board = makeBoard(listOf(
            listOf(inactive(), inactive(), inactive()),
            listOf(inactive(), t(null), inactive()),
            listOf(t(PINK_TABLET), t(null), inactive()),
            listOf(inactive(), t(null), inactive()),
        ))
        assertTrue(FallMove(gp(2, 0), gp(3, 1), MoveKind.DIAGONAL_RIGHT) in board.computeFallMoves())
    }

    @Test fun restingTileInChasmPocketBlocksDiagonalSlide() {
        // The orange tile at (0,1) is itself free to fall straight down - that's a
        // separate move. What this pins down is that it blocks the *pink* tile's
        // diagonal slide into column 1 while it's resting there.
        val board = makeBoard(listOf(
            listOf(inactive(), t(ORANGE_TABLET), inactive()),
            listOf(t(PINK_TABLET), t(null), inactive()),
            listOf(inactive(), t(null), inactive()),
        ))
        assertFalse(board.computeFallMoves().any { it.from == gp(1, 0) })
    }

    @Test fun wallRightBlocksDiagonalSlideAcrossIt() {
        val board = makeBoard(listOf(listOf(t(PINK_TABLET), inactive()), listOf(inactive(), t(null))))
        board.mutate(gp(0, 0)) { it.copy(hasWallRight = true) }
        assertTrue(board.computeFallMoves().isEmpty())
    }

    // Legal move search

    @Test fun firstLegalSwapFindsAMoveThatProducesAMatch() {
        // Swapping (0,1)<->(1,1) turns row 0 into [pink, pink, pink].
        val board = makeBoard(listOf(
            listOf(t(PINK_TABLET), t(ORANGE_TABLET), t(PINK_TABLET)),
            listOf(t(PINK_TABLET), t(PINK_TABLET), t(ORANGE_TABLET)),
        ))
        assertNotNull(board.firstLegalSwap())
        assertTrue(board.legalSwapExists())
    }

    @Test fun noLegalSwapWhenNoSwapCanProduceAMatch() {
        val board = makeBoard(listOf(listOf(t(PINK_TABLET), t(ORANGE_TABLET), t(YELLOW_TABLET))))
        assertNull(board.firstLegalSwap())
        assertFalse(board.legalSwapExists())
    }

    @Test fun legalSwapSearchDoesNotMutateTheBoard() {
        val board = makeBoard(listOf(
            listOf(t(PINK_TABLET), t(ORANGE_TABLET), t(PINK_TABLET)),
            listOf(t(PINK_TABLET), t(PINK_TABLET), t(ORANGE_TABLET)),
        ))
        val before = board.cells.map { it.tile }
        board.legalSwaps()
        assertEquals(before, board.cells.map { it.tile })
    }
}
