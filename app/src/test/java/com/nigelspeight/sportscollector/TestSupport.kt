package com.nigelspeight.sportscollector

import com.nigelspeight.sportscollector.engine.EnginePhase
import com.nigelspeight.sportscollector.engine.GameEngine
import com.nigelspeight.sportscollector.engine.GameEvent
import com.nigelspeight.sportscollector.engine.SystemTileRandomSource
import com.nigelspeight.sportscollector.engine.TileType
import com.nigelspeight.sportscollector.level.ActiveBlockSpec
import com.nigelspeight.sportscollector.level.Level
import com.nigelspeight.sportscollector.level.LevelBundle
import com.nigelspeight.sportscollector.level.LevelCellSpec
import com.nigelspeight.sportscollector.level.LevelLoader
import com.nigelspeight.sportscollector.level.StarThresholds
import java.io.File
import kotlin.random.Random

/// Deterministic random source so engine tests are reproducible.
fun seededRandomSource(seed: Int) = SystemTileRandomSource(Random(seed))

/// Reads the bundled levels straight from the source tree (unit tests run with
/// the module directory as their working directory).
val testBundle = LevelBundle { name -> File("src/main/assets/Levels/$name.txt").takeIf { it.exists() }?.readText() }

fun loadBundledLevel(id: Int): Level = LevelLoader.loadLevel(id.toString(), id, testBundle)

/// Builds a minimal, fully-controlled `Level` for engine tests, bypassing the
/// JSON decode pipeline entirely.
object TestLevel {
    data class Spec(
        val tile: TileType? = null,
        val isActive: Boolean = true,
        val hasWallBelow: Boolean = false,
        val hasWallRight: Boolean = false,
        val isSpawnPoint: Boolean = false,
        val hasJelly: Boolean = false,
    )

    fun make(
        id: Int = 99,
        rows: List<List<Spec>>,
        movesAllowed: Int = 10,
        activeBlocks: Map<TileType, ActiveBlockSpec> = emptyMap(),
        starThresholds: StarThresholds = StarThresholds(100, 200, 300),
    ): Level {
        val height = rows.size
        val width = rows.firstOrNull()?.size ?: 0
        // Row-major, matching `Board`'s own convention directly - this builder
        // never goes through the real JSON loader's column-major on-disk quirk.
        val cells = rows.flatten().map { spec ->
            LevelCellSpec(
                isActive = spec.isActive,
                hasWallBelow = spec.hasWallBelow,
                hasWallRight = spec.hasWallRight,
                isSpawnPoint = spec.isSpawnPoint,
                isExit = false,
                hasJelly = spec.hasJelly,
                fixedTile = spec.tile,
            )
        }
        return Level(
            mapNumber = id, id = id, width = width, height = height, movesAllowed = movesAllowed,
            starThresholds = starThresholds, backgroundImageFilename = "",
            cells = cells, activeBlocks = activeBlocks.toSortedMap(compareBy { it.rawValue }),
        )
    }
}

fun s(tile: TileType?, isSpawnPoint: Boolean = false, hasJelly: Boolean = false, hasWallBelow: Boolean = false, hasWallRight: Boolean = false) =
    TestLevel.Spec(tile = tile, isSpawnPoint = isSpawnPoint, hasJelly = hasJelly, hasWallBelow = hasWallBelow, hasWallRight = hasWallRight)

fun runUntilSettled(engine: GameEngine, dt: Double = 0.05, maxSteps: Int = 1000): List<GameEvent> {
    val all = mutableListOf<GameEvent>()
    repeat(maxSteps) {
        all += engine.advance(dt)
        when (engine.phase) {
            EnginePhase.IDLE, EnginePhase.TUTORIAL_RESTRICTED, EnginePhase.WON, EnginePhase.LOST -> return all
            else -> Unit
        }
    }
    return all
}
