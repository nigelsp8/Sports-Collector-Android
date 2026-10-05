package com.nigelspeight.sportscollector.engine

import com.nigelspeight.sportscollector.level.Level

/// Headless rules engine for one level. Owns all pacing internally and advances
/// purely as a function of accumulated `deltaTime` - the caller (the game view,
/// or a test) never blocks on it and never feeds timing back in. `advance` drains
/// and returns every event produced since the last call, including ones queued by
/// `attemptSwap` earlier in the same tick.
class GameEngine(
    private val level: Level,
    private val randomSource: TileRandomSource = SystemTileRandomSource(),
) {
    companion object {
        const val INTRO_FADE_DURATION = 0.6
        const val HINT_IDLE_THRESHOLD = 8.0
        const val SHUFFLE_BANNER_DURATION = 1.0
        const val MAX_INITIAL_FILL_ATTEMPTS = 500

        /// Base cadence for gravity's board-wide fall steps in normal play - each
        /// step moves every currently-fallable tile down/diagonal by one cell.
        /// Only applies when `debugFallStepInterval` is 0.
        const val BASE_FALL_STEP_INTERVAL = 0.07

        /// Picks the candidate after `last` in scan order (wrapping around), so
        /// repeated idle hints on an unchanged board cycle through the available
        /// moves instead of always re-highlighting the same pair.
        private fun nextHint(
            candidates: List<Pair<GridPoint, GridPoint>>,
            last: Pair<GridPoint, GridPoint>?,
        ): Pair<GridPoint, GridPoint>? {
            if (candidates.isEmpty()) return null
            if (last == null) return candidates[0]
            val lastIndex = candidates.indexOf(last)
            return if (lastIndex >= 0) candidates[(lastIndex + 1) % candidates.size] else candidates[0]
        }

        /// Mirrors the original's `setupGrid:` random-fill-with-retry: every active cell
        /// without level-authored fixed content gets a random tile from the level's
        /// weighted distribution, retried until the result is both match-free and has at
        /// least one legal move (or the attempt cap is hit, in which case the last
        /// attempt is used anyway).
        private fun makeInitialBoard(level: Level, randomSource: TileRandomSource): Board {
            var board = Board(level)
            val distribution = spawnDistribution(level)
            if (distribution.isEmpty()) return board

            val fillablePoints = (0 until board.height).flatMap { row ->
                (0 until board.width).mapNotNull { col ->
                    val point = GridPoint(row, col)
                    val cell = board[point]
                    if (cell != null && cell.isActive && cell.tile == null) point else null
                }
            }
            if (fillablePoints.isEmpty()) return board

            repeat(MAX_INITIAL_FILL_ATTEMPTS) {
                val candidate = board.copy()
                for (point in fillablePoints) {
                    val type = randomSource.nextSpawnTile(distribution)
                    candidate.mutate(point) { it.copy(tile = type) }
                }
                board = candidate
                if (candidate.findMatches().isEmpty() && candidate.legalSwapExists()) {
                    return board
                }
            }
            return board
        }

        private fun spawnDistribution(level: Level): List<Pair<TileType, Int>> =
            level.activeBlocks.map { (type, spec) -> type to spec.spawnPercentage }.filter { it.second > 0 }

        /// Was level 1 only: restricted input to a rect built from the bounding box
        /// of the level's spawn cells (expanded by one) until the first valid match
        /// was made (see `settle()`). Disabled for now - always returns `null`, so no
        /// level shows the tutorial overlay.
        @Suppress("UNUSED_PARAMETER")
        private fun tutorialRect(level: Level): GridRect? = null
    }

    /// How long a committed swap takes to visually resolve on the rendering
    /// side (`BoardNode.SWAP_DURATION`). The `SWAPPING` phase holds match
    /// resolution/gravity until this elapses, so the two swapped tiles are
    /// never popped or replaced mid-flight.
    var swapAnimationDuration: Double = 0.15

    var board: Board = makeInitialBoard(level, randomSource)
        private set
    var movesRemaining: Int = level.movesAllowed
        private set
    var score: Int = 0
        private set
    val objectives: List<Objective> = Objective.objectivesFor(level)
    var phase: EnginePhase = EnginePhase.INTRO_FADE_IN
        private set
    var tutorialRect: GridRect? = tutorialRect(level)
        private set

    /// Debug aid: when > 0, throttles how often the falling phase advances one
    /// logical step, independent of frame rate.
    var debugFallStepInterval: Double = 0.0
    private var fallStepAccumulator = 0.0

    /// While true, no idle hints are suggested and the idle timer is held at
    /// zero, so the first hint after it's cleared comes a full
    /// `HINT_IDLE_THRESHOLD` later. The scene sets this while a full-screen
    /// popup (objectives/win/lose) covers the board.
    var hintsSuspended = false

    private var phaseTimer = INTRO_FADE_DURATION
    private var hintIdleTimer = 0.0
    private var lastHintSuggestion: Pair<GridPoint, GridPoint>? = null
    private val pendingEvents = mutableListOf<GameEvent>()
    private var pendingSwapMatches: List<MatchGroup> = emptyList()

    init {
        pendingEvents += GameEvent.IntroFadeInStarted(INTRO_FADE_DURATION)
    }

    // region Public API

    /// Only honored while idle (or tutorial-restricted, and then only within the
    /// restricted rect). Adjacency/wall failures and swaps that produce no match
    /// are both free: no move is spent, no phase change happens.
    fun attemptSwap(a: GridPoint, b: GridPoint): Boolean {
        if (phase != EnginePhase.IDLE && phase != EnginePhase.TUTORIAL_RESTRICTED) return false
        val rect = tutorialRect
        if (phase == EnginePhase.TUTORIAL_RESTRICTED && rect != null && !(rect.contains(a) && rect.contains(b))) {
            return false
        }
        if (!board.canSwap(a, b)) return false

        board.swapTiles(a, b)
        val matches = board.findMatches()
        val touchesSwap = matches.any { a in it.cells || b in it.cells }

        if (!touchesSwap) {
            board.swapTiles(a, b)
            pendingEvents += GameEvent.SwapRejected(a, b)
            return false
        }

        movesRemaining -= 1
        pendingEvents += GameEvent.MovesChanged(movesRemaining)
        pendingEvents += GameEvent.SwapAnimated(a, b, committed = true)
        pendingSwapMatches = matches
        phase = EnginePhase.SWAPPING
        phaseTimer = swapAnimationDuration
        return true
    }

    /// Resets the hint idle timer - call on any touch, even ones that don't
    /// result in a swap, matching the original's "any interaction defers the hint".
    fun notifyUserInteraction() {
        hintIdleTimer = 0.0
    }

    fun advance(deltaTime: Double): List<GameEvent> {
        when (phase) {
            EnginePhase.WON, EnginePhase.LOST -> Unit

            EnginePhase.INTRO_FADE_IN -> {
                phaseTimer -= deltaTime
                if (phaseTimer <= 0) {
                    if (board.legalSwapExists()) {
                        phase = if (tutorialRect != null) EnginePhase.TUTORIAL_RESTRICTED else EnginePhase.IDLE
                    } else {
                        phase = EnginePhase.SHUFFLING
                        phaseTimer = SHUFFLE_BANNER_DURATION
                        pendingEvents += GameEvent.NoMoreMovesShuffleStarted
                    }
                }
            }

            EnginePhase.IDLE, EnginePhase.TUTORIAL_RESTRICTED -> {
                hintIdleTimer = if (hintsSuspended) 0.0 else hintIdleTimer + deltaTime
                if (hintIdleTimer >= HINT_IDLE_THRESHOLD) {
                    hintIdleTimer = 0.0
                    val candidates = board.legalSwaps(tutorialRect)
                    nextHint(candidates, lastHintSuggestion)?.let { next ->
                        lastHintSuggestion = next
                        pendingEvents += GameEvent.HintSuggested(next.first, next.second)
                    }
                }
            }

            EnginePhase.SWAPPING -> {
                phaseTimer -= deltaTime
                if (phaseTimer <= 0) {
                    val matches = pendingSwapMatches
                    pendingSwapMatches = emptyList()
                    resolveMatches(matches)
                    phase = EnginePhase.FALLING
                }
            }

            EnginePhase.FALLING -> {
                val interval = if (debugFallStepInterval > 0) debugFallStepInterval else BASE_FALL_STEP_INTERVAL
                fallStepAccumulator += deltaTime
                if (fallStepAccumulator >= interval) {
                    fallStepAccumulator = 0.0
                    stepFalling()
                }
            }

            EnginePhase.SHUFFLING -> {
                phaseTimer -= deltaTime
                if (phaseTimer <= 0) {
                    performShuffle()
                }
            }
        }

        val drained = pendingEvents.toList()
        pendingEvents.clear()
        return drained
    }

    fun legalSwapExists(): Boolean = board.legalSwapExists()

    fun firstLegalSwap(): Pair<GridPoint, GridPoint>? = board.firstLegalSwap()

    // endregion

    // region Falling phase

    private fun stepFalling() {
        val moves = board.computeFallMoves()
        if (moves.isNotEmpty()) {
            for (move in moves) {
                val tile = board[move.from]?.tile
                board.mutate(move.to) { it.copy(tile = tile) }
                board.mutate(move.from) { it.copy(tile = null) }
                pendingEvents += GameEvent.TileMoved(move.from, move.to, move.kind)
            }
            return
        }

        if (fillSpawnCells()) return

        val matches = board.findMatches()
        if (matches.isNotEmpty()) {
            resolveMatches(matches)
            return
        }

        settle()
    }

    private fun fillSpawnCells(): Boolean {
        val distribution = spawnDistribution(level)
        if (distribution.isEmpty()) return false

        var spawnedAny = false
        level.cells.forEachIndexed { index, spec ->
            if (!spec.isSpawnPoint) return@forEachIndexed
            val point = GridPoint(index / level.width, index % level.width)
            val cell = board[point]
            if (cell == null || !cell.isActive || cell.tile != null) return@forEachIndexed
            val type = randomSource.nextSpawnTile(distribution)
            board.mutate(point) { it.copy(tile = type) }
            pendingEvents += GameEvent.TilePlaced(point, type)
            spawnedAny = true
        }
        return spawnedAny
    }

    private fun settle() {
        if (objectives.isNotEmpty() && objectives.all { it.isComplete }) {
            phase = EnginePhase.WON
            pendingEvents += GameEvent.Won(score, starsEarned())
            return
        }
        if (movesRemaining <= 0 && !objectives.all { it.isComplete }) {
            phase = EnginePhase.LOST
            pendingEvents += GameEvent.Lost
            return
        }

        if (tutorialRect != null) {
            tutorialRect = null
            pendingEvents += GameEvent.TutorialUnlocked
        }

        if (board.legalSwapExists()) {
            phase = EnginePhase.IDLE
        } else {
            phase = EnginePhase.SHUFFLING
            phaseTimer = SHUFFLE_BANNER_DURATION
            pendingEvents += GameEvent.NoMoreMovesShuffleStarted
        }
    }

    private fun performShuffle() {
        board = ShuffleSolver.shuffledBoard(board, randomSource)
        pendingEvents += GameEvent.NoMoreMovesShuffleFinished
        phase = EnginePhase.IDLE
    }

    private fun starsEarned(): Int = StarThreshold.stars(score, level.starThresholds)

    // endregion

    // region Match resolution

    /// Applies every effect of a resolved set of matches in one shot: combo-pill
    /// downgrades, wall destruction between two co-removed cells, tile removal with
    /// scoring/objective updates, and solid damage to orthogonal neighbors. All of
    /// this is a discrete state change - the *animation* of it is the rendering
    /// layer's job, driven by the emitted events.
    private fun resolveMatches(matches: List<MatchGroup>) {
        val uniquePoints = LinkedHashSet<GridPoint>().apply { matches.forEach { addAll(it.cells) } }

        val pointsToRemove = mutableListOf<GridPoint>()
        for (point in uniquePoints) {
            val tile = board[point]?.tile ?: continue
            val downgraded = tile.downgradedForm
            if (downgraded != null) {
                board.mutate(point) { it.copy(tile = downgraded) }
                pendingEvents += GameEvent.ComboPillDowngraded(point, tile, downgraded)
            } else {
                pointsToRemove += point
            }
        }

        val removalSet = pointsToRemove.toSet()
        for (point in pointsToRemove) {
            val cell = board[point] ?: continue
            if (cell.hasWallRight) {
                val right = GridPoint(point.row, point.col + 1)
                if (right in removalSet) {
                    board.mutate(point) { it.copy(hasWallRight = false) }
                    pendingEvents += GameEvent.WallDestroyed(point, right)
                }
            }
            if (cell.hasWallBelow) {
                val below = GridPoint(point.row + 1, point.col)
                if (below in removalSet) {
                    board.mutate(point) { it.copy(hasWallBelow = false) }
                    pendingEvents += GameEvent.WallDestroyed(point, below)
                }
            }
        }

        for (point in pointsToRemove) {
            val tile = board[point]?.tile ?: continue

            if (board[point]?.hasJelly == true) {
                board.mutate(point) { it.copy(hasJelly = false) }
                pendingEvents += GameEvent.JellyCleared(point)
                decrementObjective(ObjectiveKind.ClearJelly, 1)
            }

            val scoreAwarded = if (tile.isBacteria) 750 else 375
            score += scoreAwarded
            pendingEvents += GameEvent.TileRemoved(point, tile, scoreAwarded)
            pendingEvents += GameEvent.ScoreChanged(score)

            val collectKind = ObjectiveKind.Collect(tile)
            if (tile.isBacteria) {
                pendingEvents += GameEvent.BacteriaFellOff(point)
                decrementObjective(collectKind, 1)
            } else if (hasObjective(collectKind)) {
                score += 35
                pendingEvents += GameEvent.ObjectiveFlyCompleted(collectKind, bonusScore = 35)
                pendingEvents += GameEvent.ScoreChanged(score)
                decrementObjective(collectKind, 1)
            }

            board.mutate(point) { it.copy(tile = null) }
            damageAdjacentSolids(point)
        }
    }

    private fun damageAdjacentSolids(point: GridPoint) {
        val neighbors = listOf(
            GridPoint(point.row - 1, point.col),
            GridPoint(point.row + 1, point.col),
            GridPoint(point.row, point.col - 1),
            GridPoint(point.row, point.col + 1),
        )
        for (neighbor in neighbors) {
            val neighborTile = board[neighbor]?.tile ?: continue
            if (!neighborTile.isSolid) continue
            val next = neighborTile.nextDamageStage
            board.mutate(neighbor) { it.copy(tile = next) }
            pendingEvents += GameEvent.SolidDamaged(neighbor, neighborTile, next)
        }
    }

    private fun decrementObjective(kind: ObjectiveKind, amount: Int) {
        val objective = objectives.firstOrNull { it.kind == kind } ?: return
        objective.decrement(amount)
        pendingEvents += GameEvent.ObjectiveProgressed(kind, objective.remaining)
    }

    private fun hasObjective(kind: ObjectiveKind): Boolean = objectives.any { it.kind == kind }

    // endregion
}
