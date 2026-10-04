package com.nigelspeight.sportscollector.engine

import com.nigelspeight.sportscollector.level.LevelMeta
import com.nigelspeight.sportscollector.level.StarThresholds
import kotlin.math.roundToInt
import kotlin.random.Random

interface TileRandomSource {
    fun nextSpawnTile(distribution: List<Pair<TileType, Int>>): TileType
    fun <T> shuffled(items: List<T>): List<T>
}

/// Backed by any `kotlin.random.Random` - the default system one in production,
/// or a seeded one in tests for reproducibility.
class SystemTileRandomSource(private val random: Random = Random.Default) : TileRandomSource {
    override fun nextSpawnTile(distribution: List<Pair<TileType, Int>>): TileType =
        pick(distribution) { random.nextInt(it) }

    override fun <T> shuffled(items: List<T>): List<T> = items.shuffled(random)

    companion object {
        fun pick(distribution: List<Pair<TileType, Int>>, roll: (Int) -> Int): TileType {
            val total = distribution.sumOf { it.second }
            val first = distribution.firstOrNull()?.first
            if (total <= 0 || first == null) return TileType.PINK_TABLET
            var remaining = roll(total)
            for ((type, weight) in distribution) {
                if (remaining < weight) return type
                remaining -= weight
            }
            return distribution.last().first
        }
    }
}

/// Redistributes the existing multiset of tiles across the board when no legal
/// move remains. Solid blockers are excluded entirely from relocation - both as
/// something that moves and as an available destination - which is a deliberate
/// fix versus the original, whose attempt at "keep solids fixed" was dead code.
object ShuffleSolver {
    const val MAX_ATTEMPTS = 2000

    fun shuffledBoard(board: Board, randomSource: TileRandomSource): Board {
        val eligiblePoints = (0 until board.height).flatMap { row ->
            (0 until board.width).mapNotNull { col ->
                val point = GridPoint(row, col)
                val cell = board[point] ?: return@mapNotNull null
                val tile = cell.tile ?: return@mapNotNull null
                if (cell.isActive && !cell.isFixedSolidAnchor && !tile.isSolid) point else null
            }
        }
        val tiles = eligiblePoints.mapNotNull { board[it]?.tile }
        if (tiles.isEmpty()) return board

        var best = board
        repeat(MAX_ATTEMPTS) {
            val candidate = board.copy()
            val shuffledTiles = randomSource.shuffled(tiles)
            for ((point, tile) in eligiblePoints.zip(shuffledTiles)) {
                candidate.mutate(point) { it.copy(tile = tile) }
            }
            best = candidate
            if (candidate.findMatches().isEmpty() && candidate.legalSwapExists()) {
                return candidate
            }
        }
        return best
    }
}

/// Star-rating cutoffs. The original game parsed `onestarscore`/`twostarscore`/
/// `threestarscore` from level JSON but never actually used them. Fresh data
/// analysis of the shipped levels found the JSON fields are only sane (strictly
/// increasing, positive) on 3 of 100 levels; the rest are placeholder/zeroed data.
/// This uses the JSON values when they're sane, and falls back to a formula
/// derived from the level's move budget otherwise.
object StarThreshold {
    const val BASELINE_TILE_SCORE = 375
    const val TILES_PER_AVERAGE_MATCH = 3
    const val ONE_STAR_MULTIPLIER = 0.5
    const val TWO_STAR_MULTIPLIER = 0.8
    const val THREE_STAR_MULTIPLIER = 1.1
    const val ROUNDING_UNIT = 500

    fun resolve(meta: LevelMeta): StarThresholds {
        val json = StarThresholds(meta.onestarscore, meta.twostarscore, meta.threestarscore)
        return if (isValidTriple(json)) json else fallback(meta.numberofmoves)
    }

    fun isValidTriple(triple: StarThresholds): Boolean =
        triple.one > 0 && triple.one < triple.two && triple.two < triple.three

    /// `par` assumes every move nets one basic 3-tile match. Each threshold is
    /// rounded to the nearest `ROUNDING_UNIT` for a clean HUD number, then nudged up
    /// if rounding collapsed it into its neighbor - guaranteeing strict monotonic
    /// increase for any `movesAllowed >= 1`.
    fun fallback(movesAllowed: Int): StarThresholds {
        val par = (maxOf(movesAllowed, 1) * TILES_PER_AVERAGE_MATCH * BASELINE_TILE_SCORE).toDouble()

        fun rounded(multiplier: Double): Int {
            val value = par * multiplier
            // Swift's `.rounded()` rounds half away from zero; `roundToInt` rounds
            // half up, which is identical for the positive values here.
            return maxOf(ROUNDING_UNIT, (value / ROUNDING_UNIT).roundToInt() * ROUNDING_UNIT)
        }

        val one = rounded(ONE_STAR_MULTIPLIER)
        val two = maxOf(rounded(TWO_STAR_MULTIPLIER), one + ROUNDING_UNIT)
        val three = maxOf(rounded(THREE_STAR_MULTIPLIER), two + ROUNDING_UNIT)
        return StarThresholds(one, two, three)
    }

    fun stars(score: Int, thresholds: StarThresholds): Int = when {
        score >= thresholds.three -> 3
        score >= thresholds.two -> 2
        score >= thresholds.one -> 1
        else -> 0
    }
}
