package com.nigelspeight.sportscollector

import com.nigelspeight.sportscollector.engine.GameEngine
import com.nigelspeight.sportscollector.engine.GridPoint
import com.nigelspeight.sportscollector.engine.ObjectiveKind
import com.nigelspeight.sportscollector.engine.StarThreshold
import com.nigelspeight.sportscollector.engine.TileColor
import com.nigelspeight.sportscollector.engine.TileType
import com.nigelspeight.sportscollector.level.EditableLevel
import com.nigelspeight.sportscollector.level.LevelEditStore
import com.nigelspeight.sportscollector.level.LevelEncoder
import com.nigelspeight.sportscollector.level.LevelLoader
import com.nigelspeight.sportscollector.level.LevelMeta
import com.nigelspeight.sportscollector.level.StarThresholds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private fun gp(row: Int, col: Int) = GridPoint(row, col)

/// Sweeps every one of the 100 bundled level files to catch data-shape edge cases.
class AllLevelsTests {
    private val allLevelIDs = 1..100

    @Test fun everyLevelDecodesWithSaneShape() {
        for (id in allLevelIDs) {
            val level = loadBundledLevel(id)
            assertEquals(id, level.id)
            assertTrue(level.width > 0 && level.height > 0)
            assertEquals(level.width * level.height, level.cells.size)
            assertTrue("level $id has no moves allowed", level.movesAllowed > 0)
            assertTrue("level $id has no spawnable tile types", level.activeBlocks.values.sumOf { it.spawnPercentage } > 0)
            assertTrue(StarThreshold.isValidTriple(level.starThresholds))
        }
    }

    @Test fun everyLevelInitializesWithEveryActiveCellPopulatedAndALegalMove() {
        for (id in allLevelIDs) {
            val level = loadBundledLevel(id)
            val engine = GameEngine(level, seededRandomSource(id))
            level.cells.forEachIndexed { index, spec ->
                if (spec.isActive) {
                    assertNotNull("level $id cell $index should have a tile", engine.board[gp(index / level.width, index % level.width)]?.tile)
                }
            }
            assertTrue("level $id has no legal move after initial fill", engine.legalSwapExists())
        }
    }

    /// Regression: some levels flag jelly on *inactive* cells, which can never be
    /// cleared. The jelly objective must only count active cells.
    @Test fun clearJellyObjectiveOnlyCountsActiveJellyCells() {
        for (id in allLevelIDs) {
            val level = loadBundledLevel(id)
            val engine = GameEngine(level, seededRandomSource(id))
            val activeJellyCount = level.cells.count { it.hasJelly && it.isActive }
            val jellyObjective = engine.objectives.firstOrNull { it.kind == ObjectiveKind.ClearJelly }
            if (jellyObjective == null) {
                assertEquals("level $id", 0, activeJellyCount)
            } else {
                assertEquals("level $id", activeJellyCount, jellyObjective.total)
            }
        }
    }
}

class LevelLoaderTests {
    @Test fun bundledLevelOneDecodesAndLoads() {
        val level = loadBundledLevel(1)
        assertEquals(1, level.id)
        assertEquals(9, level.width)
        assertEquals(9, level.height)
        assertEquals(24, level.movesAllowed)
        assertEquals(81, level.cells.size)
        // Level 1's JSON thresholds are already valid, so they pass through unchanged.
        assertEquals(StarThresholds(40000, 52000, 67000), level.starThresholds)
    }

    @Test fun activeBlockSpawnPercentagesSumToOneHundred() {
        assertEquals(100, loadBundledLevel(1).activeBlocks.values.sumOf { it.spawnPercentage })
    }

    @Test fun overrideStoreTakesPrecedenceOverTheBundledResource() {
        val root = kotlin.io.path.createTempDirectory().toFile()
        val editStore = LevelEditStore(root)
        val bundled = loadBundledLevel(1)
        val edited = EditableLevel(bundled).copy(movesAllowed = bundled.movesAllowed + 7)
        editStore.save(edited.makeLevel(), "1")

        val loaded = LevelLoader.loadLevel("1", 1, testBundle, editStore)
        assertEquals(bundled.movesAllowed + 7, loaded.movesAllowed)
        root.deleteRecursively()
    }
}

/// Pins specific JSON keys from Levels/1.txt to their cross-referenced (row, col),
/// since population-count checks can't catch a row/col transposition.
class LevelGeometryTests {
    @Test fun specificRawCellsLandAtTheirExpectedGridPoint() {
        val level = loadBundledLevel(1)

        // Key "10": active, spawn. width=9 -> row = 10 % 9 = 1, col = 10 / 9 = 1.
        val cellAt10 = level.cell(gp(1, 1))
        assertTrue(cellAt10.isActive)
        assertTrue(cellAt10.isSpawnPoint)
        assertFalse(cellAt10.hasJelly)

        // Key "61": active, exit. row=61%9=7, col=61/9=6.
        val cellAt61 = level.cell(gp(7, 6))
        assertTrue(cellAt61.isActive)
        assertTrue(cellAt61.isExit)

        // Key "0": inactive, jelly.
        val cellAt0 = level.cell(gp(0, 0))
        assertFalse(cellAt0.isActive)
        assertTrue(cellAt0.hasJelly)

        // Key "54": inactive, jelly. row=54%9=0, col=54/9=6.
        val cellAt54 = level.cell(gp(0, 6))
        assertFalse(cellAt54.isActive)
        assertTrue(cellAt54.hasJelly)
    }

    @Test fun spawnRowFormsTheExpectedHorizontalShape() {
        val level = loadBundledLevel(1)
        val spawnPoints = (0 until level.height).flatMap { row ->
            (0 until level.width).map { col -> gp(row, col) }
        }.filter { level.cell(it).isSpawnPoint }
        assertEquals(setOf(1), spawnPoints.map { it.row }.toSet())
        assertEquals((1..7).toSet(), spawnPoints.map { it.col }.toSet())
    }
}

/// Round-trips every bundled level through `LevelEncoder` and back.
class LevelEncoderTests {
    @Test fun roundTripPreservesEveryCellAndObjective() {
        for (id in 1..100) {
            val original = loadBundledLevel(id)
            val roundTripped = LevelLoader.decodeLevel(LevelEncoder.encode(original), id)

            assertEquals(original.width, roundTripped.width)
            assertEquals(original.height, roundTripped.height)
            assertEquals(original.movesAllowed, roundTripped.movesAllowed)
            assertEquals(original.starThresholds, roundTripped.starThresholds)
            assertEquals(original.backgroundImageFilename, roundTripped.backgroundImageFilename)
            assertEquals("level $id", original.activeBlocks, roundTripped.activeBlocks)
            assertEquals("level $id", original.cells, roundTripped.cells)
        }
    }

    @Test fun specificRawCellsStillLandAtTheirExpectedGridPointAfterRoundTrip() {
        val level = loadBundledLevel(1)
        val roundTripped = LevelLoader.decodeLevel(LevelEncoder.encode(level), 1)
        assertTrue(roundTripped.cell(gp(1, 1)).isActive)
        assertTrue(roundTripped.cell(gp(1, 1)).isSpawnPoint)
        assertTrue(roundTripped.cell(gp(7, 6)).isActive)
        assertTrue(roundTripped.cell(gp(7, 6)).isExit)
    }
}

class LevelEditStoreTests {
    @get:Rule val temp = TemporaryFolder()

    @Test fun overrideDataIsNullForANeverSavedResource() {
        val store = LevelEditStore(temp.root)
        assertFalse(store.hasOverride("1"))
        assertNull(store.overrideData("1"))
    }

    @Test fun saveThenLoadRoundTrips() {
        val store = LevelEditStore(temp.root)
        val level = loadBundledLevel(1)
        store.save(level, "1")

        assertTrue(store.hasOverride("1"))
        val reloaded = LevelLoader.decodeLevel(store.overrideData("1")!!, 1)
        assertEquals(level.id, reloaded.id)
        assertEquals(level.cells.size, reloaded.cells.size)
    }

    @Test fun resetToOriginalRemovesTheOverride() {
        val store = LevelEditStore(temp.root)
        store.save(loadBundledLevel(1), "1")
        assertTrue(store.hasOverride("1"))
        store.resetToOriginal("1")
        assertFalse(store.hasOverride("1"))
    }

    @Test fun resetToOriginalIsANoOpWhenNoOverrideExists() {
        val store = LevelEditStore(temp.root)
        store.resetToOriginal("1")
        assertFalse(store.hasOverride("1"))
    }
}

class EditableLevelTests {
    @Test fun makeLevelIsIdentityForAnUnmodifiedLevel() {
        val original = loadBundledLevel(1)
        assertEquals(original, EditableLevel(original).makeLevel())
    }

    @Test fun withCellOnlyChangesTheTargetedCell() {
        val level = loadBundledLevel(1)
        val target = gp(0, 0)
        val editable = EditableLevel(level).let {
            it.withCell(target, it[target].copy(isActive = true, fixedTile = TileType.SOLID_STAGE_1))
        }
        assertTrue(editable[target].isActive)
        assertEquals(TileType.SOLID_STAGE_1, editable[target].fixedTile)
        val neighbor = gp(0, 1)
        assertEquals(level.cell(neighbor), editable[neighbor])
    }
}

class StarThresholdTests {
    private fun meta(moves: Int, one: Int, two: Int, three: Int) = LevelMeta(moves, one, two, three, 9, "grad_1.jpg")

    @Test fun validJSONTripleIsUsedUntouched() {
        assertEquals(StarThresholds(10000, 15000, 20000), StarThreshold.resolve(meta(24, 10000, 15000, 20000)))
    }

    @Test fun zeroedTripleFallsBackAndStaysIncreasing() {
        assertTrue(StarThreshold.isValidTriple(StarThreshold.resolve(meta(24, 0, 0, 0))))
    }

    @Test fun equalTripleFallsBack() {
        assertTrue(StarThreshold.isValidTriple(StarThreshold.resolve(meta(15, 15, 15, 15))))
    }

    @Test fun fallbackStaysIncreasingAcrossSmallMoveBudgets() {
        for (moves in 1..5) assertTrue(StarThreshold.isValidTriple(StarThreshold.fallback(moves)))
    }

    @Test fun fallbackScalesWithMoveBudget() {
        val small = StarThreshold.fallback(10)
        val large = StarThreshold.fallback(60)
        assertTrue(large.one > small.one)
        assertTrue(large.three > small.three)
    }
}

class TileTypeTests {
    @Test fun blockContainedDecodesToExpectedType() {
        assertNull(TileType.fromBlockContained(-1))
        assertEquals(TileType.PINK_TABLET, TileType.fromBlockContained(0))
        assertEquals(TileType.BLUE_TABLET, TileType.fromBlockContained(5))
        assertEquals(TileType.ORANGE_BLUE_PILL, TileType.fromBlockContained(6))
        assertEquals(TileType.PURPLE_YELLOW_PILL, TileType.fromBlockContained(8))
        assertEquals(TileType.PINK_BACTERIA, TileType.fromBlockContained(9))
        assertEquals(TileType.BLUE_BACTERIA, TileType.fromBlockContained(14))
        assertEquals(TileType.SOLID_STAGE_1, TileType.fromBlockContained(15))
    }

    @Test fun comboPillsCarryOneColorAndDowngradeCorrectly() {
        assertEquals(TileColor.ORANGE, TileType.ORANGE_BLUE_PILL.colorBit)
        assertEquals(TileType.ORANGE_TABLET, TileType.ORANGE_BLUE_PILL.downgradedForm)
        assertEquals(TileColor.PINK, TileType.PINK_GREEN_PILL.colorBit)
        assertEquals(TileType.PINK_TABLET, TileType.PINK_GREEN_PILL.downgradedForm)
        assertEquals(TileColor.PURPLE, TileType.PURPLE_YELLOW_PILL.colorBit)
        assertEquals(TileType.PURPLE_TABLET, TileType.PURPLE_YELLOW_PILL.downgradedForm)
        assertNull(TileType.PINK_TABLET.downgradedForm)
    }

    @Test fun bacteriaAndSolidsNeverColorMatch() {
        for (type in TileType.entries) if (type.isBacteria || type.isSolid) assertNull(type.colorBit)
    }

    @Test fun solidDamageStagesProgressThenDestroy() {
        assertEquals(TileType.SOLID_STAGE_2, TileType.SOLID_STAGE_1.nextDamageStage)
        assertEquals(TileType.SOLID_STAGE_3, TileType.SOLID_STAGE_2.nextDamageStage)
        assertNull(TileType.SOLID_STAGE_3.nextDamageStage)
    }
}
