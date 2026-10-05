package com.nigelspeight.sportscollector.engine

import com.nigelspeight.sportscollector.level.Level

data class GridPoint(val row: Int, val col: Int)

data class GridRect(val minRow: Int, val maxRow: Int, val minCol: Int, val maxCol: Int) {
    fun contains(point: GridPoint): Boolean =
        point.row in minRow..maxRow && point.col in minCol..maxCol
}

data class Cell(
    val point: GridPoint,
    val isActive: Boolean,
    val hasWallBelow: Boolean,
    val hasWallRight: Boolean,
    val isSpawnPoint: Boolean,
    val hasJelly: Boolean,
    val tile: TileType?,
    /// True if this cell is a level-authored, permanent solid spawn point (the
    /// original's `settype == SOLID1`). Unlike the original, gravity does NOT
    /// consult this flag - only whether the cell's *current* tile is solid
    /// (see `Board.computeFallMoves`/`isFloorBoundary`), so once the solid
    /// occupying this cell is fully destroyed, the cell falls/refills like any
    /// other open cell. This flag still exists, and is still permanent, for the
    /// shuffle system: the shuffle-on-stuck relocation pool always excludes cells
    /// that were ever a solid spawn point.
    val isFixedSolidAnchor: Boolean,
)

enum class EnginePhase {
    INTRO_FADE_IN,
    IDLE,
    TUTORIAL_RESTRICTED,
    SWAPPING,
    FALLING,
    SHUFFLING,
    WON,
    LOST,
}

sealed interface ObjectiveKind {
    data class Collect(val type: TileType) : ObjectiveKind
    data object ClearJelly : ObjectiveKind
}

class Objective(val kind: ObjectiveKind, val total: Int) {
    var remaining: Int = total
        private set

    val isComplete: Boolean get() = remaining <= 0

    fun decrement(amount: Int = 1) {
        remaining = maxOf(0, remaining - amount)
    }

    companion object {
        /// Derives a level's required objectives from its block/jelly layout -
        /// shared by `GameEngine` (to track progress during play) and the section
        /// screen (to preview a level's objectives before playing).
        fun objectivesFor(level: Level): List<Objective> {
            val objs = level.activeBlocks.mapNotNull { (type, spec) ->
                if (spec.blocksToWin > 0) Objective(ObjectiveKind.Collect(type), spec.blocksToWin) else null
            }.toMutableList()
            // Jelly on an inactive cell can never be cleared (inactive cells never
            // hold a tile) and is never rendered - counting it here would create an
            // un-completable objective.
            val jellyCount = level.cells.count { it.hasJelly && it.isActive }
            if (jellyCount > 0) {
                objs += Objective(ObjectiveKind.ClearJelly, jellyCount)
            }
            return objs
        }
    }
}

enum class MoveKind { STRAIGHT_DOWN, DIAGONAL_RIGHT, DIAGONAL_LEFT }

data class FallMove(val from: GridPoint, val to: GridPoint, val kind: MoveKind)

data class MatchGroup(val cells: List<GridPoint>, val color: TileColor?, val cause: Cause) {
    enum class Cause { COLOR_RUN, BOTTOM_ROW_BACTERIA }
}

sealed interface GameEvent {
    data class IntroFadeInStarted(val duration: Double) : GameEvent
    data class TilePlaced(val point: GridPoint, val type: TileType) : GameEvent
    data class SwapRejected(val a: GridPoint, val b: GridPoint) : GameEvent
    data class SwapAnimated(val a: GridPoint, val b: GridPoint, val committed: Boolean) : GameEvent
    data class TileMoved(val from: GridPoint, val to: GridPoint, val kind: MoveKind) : GameEvent
    data class TileRemoved(val point: GridPoint, val tile: TileType, val scoreAwarded: Int) : GameEvent
    data class ComboPillDowngraded(val point: GridPoint, val from: TileType, val to: TileType) : GameEvent
    data class SolidDamaged(val point: GridPoint, val from: TileType, val to: TileType?) : GameEvent
    data class WallDestroyed(val a: GridPoint, val b: GridPoint) : GameEvent
    data class JellyCleared(val point: GridPoint) : GameEvent
    data class ObjectiveProgressed(val kind: ObjectiveKind, val remaining: Int) : GameEvent
    data class ObjectiveFlyCompleted(val kind: ObjectiveKind, val bonusScore: Int) : GameEvent
    data class BacteriaFellOff(val point: GridPoint) : GameEvent
    data class ScoreChanged(val score: Int) : GameEvent
    data class MovesChanged(val moves: Int) : GameEvent
    data class HintSuggested(val a: GridPoint, val b: GridPoint) : GameEvent
    data object NoMoreMovesShuffleStarted : GameEvent
    data object NoMoreMovesShuffleFinished : GameEvent
    data object TutorialUnlocked : GameEvent
    data class Won(val score: Int, val stars: Int) : GameEvent
    data object Lost : GameEvent
}
