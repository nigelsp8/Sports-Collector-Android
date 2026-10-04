package com.nigelspeight.sportscollector.engine

import com.nigelspeight.sportscollector.level.Level

/// The live board: storage plus pure queries (adjacency/wall rules, match
/// detection, gravity). Mutation happens via `mutate(at, body)`; the higher-level
/// removal/fall/spawn *sequencing* lives in `GameEngine`, not here.
///
/// The Swift original is a value type; here `Cell` is immutable and `copy()`
/// gives an independent board, so callers that relied on Swift's copy-on-assign
/// (`var trial = board`) call `copy()` explicitly instead.
class Board private constructor(
    val width: Int,
    val height: Int,
    private val storage: Array<Cell>,
) {
    constructor(level: Level) : this(
        level.width,
        level.height,
        Array(level.cells.size) { index ->
            val spec = level.cells[index]
            Cell(
                point = GridPoint(row = index / level.width, col = index % level.width),
                isActive = spec.isActive,
                hasWallBelow = spec.hasWallBelow,
                hasWallRight = spec.hasWallRight,
                isSpawnPoint = spec.isSpawnPoint,
                hasJelly = spec.hasJelly,
                tile = spec.fixedTile,
                isFixedSolidAnchor = spec.fixedTile == TileType.SOLID_STAGE_1,
            )
        },
    )

    val cells: List<Cell> get() = storage.asList()

    fun copy(): Board = Board(width, height, storage.copyOf())

    private fun index(point: GridPoint): Int? {
        if (point.row < 0 || point.row >= height || point.col < 0 || point.col >= width) return null
        return point.row * width + point.col
    }

    operator fun get(point: GridPoint): Cell? = index(point)?.let { storage[it] }

    fun mutate(at: GridPoint, body: (Cell) -> Cell) {
        val idx = index(at) ?: return
        storage[idx] = body(storage[idx])
    }

    // region Swapping

    /// Orthogonal adjacency, both cells active and occupied, no wall between
    /// them, and neither tile is a solid blocker (solids are immovable - they
    /// only clear via adjacent matches/damage, never by swapping). Diagonals
    /// are never valid.
    fun canSwap(a: GridPoint, b: GridPoint): Boolean {
        val cellA = this[a] ?: return false
        val cellB = this[b] ?: return false
        val tileA = cellA.tile ?: return false
        val tileB = cellB.tile ?: return false
        if (!cellA.isActive || !cellB.isActive || tileA.isSolid || tileB.isSolid) return false

        val dr = b.row - a.row
        val dc = b.col - a.col
        return when {
            dr == 0 && dc == 1 -> !cellA.hasWallRight
            dr == 0 && dc == -1 -> !cellB.hasWallRight
            dr == 1 && dc == 0 -> !cellA.hasWallBelow
            dr == -1 && dc == 0 -> !cellB.hasWallBelow
            else -> false
        }
    }

    fun swapTiles(a: GridPoint, b: GridPoint) {
        val idxA = index(a) ?: return
        val idxB = index(b) ?: return
        val tileA = storage[idxA].tile
        storage[idxA] = storage[idxA].copy(tile = storage[idxB].tile)
        storage[idxB] = storage[idxB].copy(tile = tileA)
    }

    // endregion

    // region Matching

    /// Runs of 3+ sharing a color, horizontal or vertical, plus any bacteria
    /// sitting in the bottom-most active row of its column (which auto-match
    /// regardless of neighbors). Every real tile carries exactly one color bit,
    /// so "shares a color bit" and "has an equal color" are equivalent here -
    /// this is a plain equal-run scan, not a bitmask intersection.
    fun findMatches(): List<MatchGroup> {
        val groups = mutableListOf<MatchGroup>()
        groups += colorRuns(horizontal = true)
        groups += colorRuns(horizontal = false)
        groups += bottomRowBacteriaMatches()
        return groups
    }

    private fun colorRuns(horizontal: Boolean): List<MatchGroup> {
        val groups = mutableListOf<MatchGroup>()
        val outerCount = if (horizontal) height else width
        val innerCount = if (horizontal) width else height

        for (outer in 0 until outerCount) {
            var runStart = 0
            var runColor: TileColor? = null

            fun pointAt(inner: Int) =
                if (horizontal) GridPoint(row = outer, col = inner) else GridPoint(row = inner, col = outer)

            fun flushRun(endExclusive: Int) {
                val color = runColor
                if (color != null && endExclusive - runStart >= 3) {
                    groups += MatchGroup((runStart until endExclusive).map(::pointAt), color, MatchGroup.Cause.COLOR_RUN)
                }
                runStart = endExclusive
                runColor = null
            }

            for (inner in 0 until innerCount) {
                val cell = this[pointAt(inner)]
                val color = if (cell != null && cell.isActive) cell.tile?.colorBit else null
                if (color == null || color != runColor) {
                    flushRun(endExclusive = inner)
                    runStart = inner
                    runColor = color
                }
            }
            flushRun(endExclusive = innerCount)
        }
        return groups
    }

    private fun bottomRowBacteriaMatches(): List<MatchGroup> {
        val groups = mutableListOf<MatchGroup>()
        for (col in 0 until width) {
            for (row in height - 1 downTo 0) {
                val point = GridPoint(row, col)
                val cell = this[point] ?: continue
                if (!cell.isActive) continue
                if (cell.tile?.isBacteria == true) {
                    groups += MatchGroup(listOf(point), null, MatchGroup.Cause.BOTTOM_ROW_BACTERIA)
                }
                break // only the bottom-most *active* cell in the column counts
            }
        }
        return groups
    }

    // endregion

    // region Gravity

    /// A "floor" for chasm-scan purposes is about what's *currently* resting
    /// there, not the cell's permanent authoring: a former solid-anchor cell
    /// whose solid has been fully destroyed is passable again, exactly like any
    /// other emptied cell.
    private fun isFloorBoundary(cell: Cell): Boolean =
        !cell.isActive || (cell.tile?.isSolid == true) || cell.hasWallBelow

    /// Port of the original's `goodchasm:yp:`. `column` must have a genuine open
    /// chasm at and above `fromRow`: a defined floor boundary somewhere in the
    /// empty span above, with no resting non-solid tile in either neighboring
    /// column alongside that span (which would visually collide with the slide).
    fun isChasmClear(column: Int, fromRow: Int): Boolean {
        var boundaryRow: Int? = null
        var row = fromRow
        while (row >= 0) {
            val cell = this[GridPoint(row, column)] ?: break
            if (isFloorBoundary(cell)) {
                boundaryRow = row
                break
            }
            if (cell.tile != null) return false
            row -= 1
        }
        if (boundaryRow == null) return false
        if (boundaryRow == fromRow - 1) return true

        for (r in fromRow - 1 downTo boundaryRow) {
            for (neighborColumn in listOf(column - 1, column + 1)) {
                val tile = this[GridPoint(r, neighborColumn)]?.tile
                if (tile != null && !tile.isSolid) return false
            }
        }
        return true
    }

    /// One full-board pass: for every occupied cell (scanned bottom-up,
    /// left-to-right, mirroring the original's scan order so later checks in
    /// the same pass see earlier decisions), find its fall move in priority
    /// order (straight down > diagonal right > diagonal left). A cell whose
    /// tile is currently solid never falls (solids are stationary blockers).
    /// Pure - does not mutate the board; the caller applies the returned moves.
    fun computeFallMoves(): List<FallMove> {
        if (height < 2) return emptyList()
        val moves = mutableListOf<FallMove>()
        val occupied = BooleanArray(storage.size) { storage[it].tile != null }

        fun isOccupied(p: GridPoint): Boolean = index(p)?.let { occupied[it] } ?: true
        fun setOccupied(p: GridPoint, value: Boolean) {
            index(p)?.let { occupied[it] = value }
        }

        for (row in height - 2 downTo 0) {
            for (col in 0 until width) {
                val source = GridPoint(row, col)
                val cell = this[source] ?: continue
                val tile = cell.tile ?: continue
                if (!cell.isActive || tile.isSolid || !isOccupied(source)) continue

                val below = GridPoint(row + 1, col)
                if (!cell.hasWallBelow && this[below]?.isActive == true && !isOccupied(below)) {
                    moves += FallMove(source, below, MoveKind.STRAIGHT_DOWN)
                    setOccupied(source, false)
                    setOccupied(below, true)
                    continue
                }

                val rightTarget = GridPoint(row + 1, col + 1)
                if (col < width - 1 && !cell.hasWallBelow && !cell.hasWallRight &&
                    this[rightTarget]?.isActive == true && !isOccupied(rightTarget) &&
                    isChasmClear(column = col + 1, fromRow = row)
                ) {
                    moves += FallMove(source, rightTarget, MoveKind.DIAGONAL_RIGHT)
                    setOccupied(source, false)
                    setOccupied(rightTarget, true)
                    continue
                }

                val leftTarget = GridPoint(row + 1, col - 1)
                if (col > 0 && !cell.hasWallBelow &&
                    this[GridPoint(row, col - 1)]?.hasWallRight == false &&
                    this[leftTarget]?.isActive == true && !isOccupied(leftTarget) &&
                    isChasmClear(column = col - 1, fromRow = row)
                ) {
                    moves += FallMove(source, leftTarget, MoveKind.DIAGONAL_LEFT)
                    setOccupied(source, false)
                    setOccupied(leftTarget, true)
                }
            }
        }
        return moves
    }

    // endregion

    // region Legal move search

    fun legalSwapExists(): Boolean = firstLegalSwap() != null

    fun firstLegalSwap(restrictedTo: GridRect? = null): Pair<GridPoint, GridPoint>? =
        legalSwaps(restrictedTo).firstOrNull()

    /// Every swap (not just the first found) that produces a match, in
    /// board-scan order. Lets a caller offer a different legal move than one
    /// already shown for the same board state, instead of always returning
    /// the same pair.
    fun legalSwaps(restrictedTo: GridRect? = null): List<Pair<GridPoint, GridPoint>> {
        val found = mutableListOf<Pair<GridPoint, GridPoint>>()
        val trial = copy()
        for (row in 0 until height) {
            for (col in 0 until width) {
                val a = GridPoint(row, col)
                if (restrictedTo != null && !restrictedTo.contains(a)) continue
                for ((dr, dc) in NEIGHBOR_OFFSETS) {
                    val b = GridPoint(row + dr, col + dc)
                    if (restrictedTo != null && !restrictedTo.contains(b)) continue
                    if (!canSwap(a, b)) continue
                    trial.swapTiles(a, b)
                    val matches = trial.findMatches().any { a in it.cells || b in it.cells }
                    trial.swapTiles(a, b)
                    if (matches) found += a to b
                }
            }
        }
        return found
    }

    // endregion

    private companion object {
        val NEIGHBOR_OFFSETS = listOf(0 to 1, 1 to 0)
    }
}
