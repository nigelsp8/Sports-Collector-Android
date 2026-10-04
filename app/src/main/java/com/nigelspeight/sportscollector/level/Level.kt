package com.nigelspeight.sportscollector.level

import com.nigelspeight.sportscollector.engine.GridPoint
import com.nigelspeight.sportscollector.engine.TileType

/// A single grid cell's level-authored, unchanging configuration (as opposed to
/// `Cell`, which is the live in-engine board state derived from this spec).
data class LevelCellSpec(
    val isActive: Boolean,
    val hasWallBelow: Boolean,
    val hasWallRight: Boolean,
    val isSpawnPoint: Boolean,
    /// Decoded for fidelity with the original level data. Confirmed to have no
    /// gameplay effect anywhere in the original game - kept as inert data.
    val isExit: Boolean,
    val hasJelly: Boolean,
    val fixedTile: TileType?,
) {
    companion object {
        val INACTIVE = LevelCellSpec(
            isActive = false, hasWallBelow = false, hasWallRight = false,
            isSpawnPoint = false, isExit = false, hasJelly = false, fixedTile = null,
        )
    }
}

data class ActiveBlockSpec(val blocksToWin: Int, val spawnPercentage: Int)

data class StarThresholds(val one: Int, val two: Int, val three: Int)

/// Normalized, engine-facing level definition produced by `LevelLoader`.
data class Level(
    val mapNumber: Int,
    val id: Int,
    val width: Int,
    val height: Int,
    val movesAllowed: Int,
    val starThresholds: StarThresholds,
    val backgroundImageFilename: String,
    /// Flat, row-major: index = row * width + col. `LevelLoader` remaps the JSON's
    /// own column-major cell keys into this standard layout once, at decode time,
    /// so everything downstream can use plain row-major indexing.
    val cells: List<LevelCellSpec>,
    /// Ordered by `TileType.rawValue` so objective/HUD ordering is stable.
    val activeBlocks: Map<TileType, ActiveBlockSpec>,
) {
    fun cell(at: GridPoint): LevelCellSpec = cells[at.row * width + at.col]
}

/// Raw meta block from the level JSON payload.
data class LevelMeta(
    val numberofmoves: Int,
    val onestarscore: Int,
    val twostarscore: Int,
    val threestarscore: Int,
    val levelgridsize: Int,
    val backgroundimagefilename: String,
)

/// Mutable-by-copy mirror of `Level`/`LevelCellSpec` for the level editor.
data class EditableLevel(
    val mapNumber: Int,
    val id: Int,
    /// Fixed for the lifetime of an editor session - resizing a level's grid
    /// is out of scope.
    val width: Int,
    val height: Int,
    val movesAllowed: Int,
    val starThresholds: StarThresholds,
    val backgroundImageFilename: String,
    val cells: List<LevelCellSpec>,
    val activeBlocks: Map<TileType, ActiveBlockSpec>,
) {
    constructor(level: Level) : this(
        mapNumber = level.mapNumber,
        id = level.id,
        width = level.width,
        height = level.height,
        movesAllowed = level.movesAllowed,
        starThresholds = level.starThresholds,
        backgroundImageFilename = level.backgroundImageFilename,
        cells = level.cells,
        activeBlocks = level.activeBlocks,
    )

    fun makeLevel(): Level = Level(
        mapNumber = mapNumber, id = id, width = width, height = height,
        movesAllowed = movesAllowed, starThresholds = starThresholds,
        backgroundImageFilename = backgroundImageFilename,
        cells = cells, activeBlocks = activeBlocks.toSortedMap(compareBy { it.rawValue }),
    )

    operator fun get(point: GridPoint): LevelCellSpec = cells[point.row * width + point.col]

    fun withCell(point: GridPoint, spec: LevelCellSpec): EditableLevel =
        copy(cells = cells.toMutableList().also { it[point.row * width + point.col] = spec })
}

/// Maps a 1-based map slot (what the player sees as "Level N") to the bundled
/// level file number that slot plays.
object LevelOrder {
    const val LEVEL_COUNT = 100

    val order: IntArray = intArrayOf(
        0, // not actually used
        1, 36, 46, 52, 17, 14, 51, 23, 73, 22,
        30, 48, 86, 8, 11, 33, 35, 38, 47, 27,
        44, 78, 32, 43, 49, 76, 85, 34, 72, 88,
        65, 15, 66, 68, 19, 28, 50, 90, 7, 21,
        24, 77, 79, 16, 41, 57, 74, 63, 45, 6,
        2, 40, 4, 69, 61, 71, 75, 12, 55, 56,
        20, 60, 89, 10, 25, 26, 80, 84, 82, 62,
        53, 37, 13, 87, 18, 9, 31, 3, 42, 54,
        83, 67, 39, 81, 64, 29, 59, 70, 58, 5,
        91, 92, 93, 94, 95, 96, 97, 98, 99, 100,
        -1,
    )

    fun resourceName(mapID: Int): String = order[mapID].toString()
}
