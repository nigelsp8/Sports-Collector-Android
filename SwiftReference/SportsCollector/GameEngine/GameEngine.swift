import Foundation

/// Headless rules engine for one level. Owns all pacing internally and advances
/// purely as a function of accumulated `deltaTime` - the caller (a SpriteKit scene,
/// or a test) never blocks on it and never feeds timing back in. `advance` drains
/// and returns every event produced since the last call, including ones queued by
/// `attemptSwap` earlier in the same tick.
final class GameEngine {
    static let introFadeDuration: TimeInterval = 0.6
    static let hintIdleThreshold: TimeInterval = 8.0
    static let shuffleBannerDuration: TimeInterval = 1.0
    static let maxInitialFillAttempts = 500

    /// Base cadence for gravity's board-wide fall steps in normal play - each
    /// step moves every currently-fallable tile down/diagonal by one cell.
    /// Previously this ran unthrottled (one full step every rendered frame,
    /// ~0.0167s at 60fps), which advanced the model many rows before any
    /// single row's fall animation (see `BoardNode.moveTile`) had time to
    /// visually play out, making falls look instant rather than gravity-driven.
    /// Only applies when `debugFallStepInterval` is 0 (its own slower values
    /// still take priority for the manual speed-cycle debug aid).
    static let baseFallStepInterval: TimeInterval = 0.07

    /// How long a committed swap takes to visually resolve on the rendering
    /// side (`BoardNode.swapDuration`). The `.swapping` phase holds match
    /// resolution/gravity until this elapses, so the two swapped tiles are
    /// never popped or replaced mid-flight while they're still animating
    /// into each other's slot. Kept in sync with the debug slow-motion
    /// speed via `GameScene.cycleDebugSpeed`.
    var swapAnimationDuration: TimeInterval = 0.15

    private(set) var board: Board
    private(set) var movesRemaining: Int
    private(set) var score: Int
    private(set) var objectives: [Objective]
    private(set) var phase: EnginePhase
    private(set) var tutorialRect: GridRect?

    /// Debug aid: when > 0, throttles how often the falling phase advances one
    /// logical step (fall/spawn/match-resolve), independent of frame rate, so a
    /// human observer can watch each step happen instead of it completing in a
    /// handful of frames. 0 (default) means no throttle - a step runs every call.
    var debugFallStepInterval: TimeInterval = 0
    private var fallStepAccumulator: TimeInterval = 0

    private let level: Level
    private var randomSource: TileRandomSource
    private var phaseTimer: TimeInterval = 0
    private var hintIdleTimer: TimeInterval = 0
    private var lastHintSuggestion: (GridPoint, GridPoint)?
    private var pendingEvents: [GameEvent] = []
    private var pendingSwapMatches: [MatchGroup] = []

    init(level: Level, randomSource: TileRandomSource = SystemTileRandomSource()) {
        self.level = level
        self.movesRemaining = level.movesAllowed
        self.score = 0
        var random = randomSource
        self.board = Self.makeInitialBoard(level: level, randomSource: &random)
        self.randomSource = random
        self.phase = .introFadeIn
        self.phaseTimer = Self.introFadeDuration
        self.tutorialRect = Self.tutorialRect(for: level)

        var objs: [Objective] = level.activeBlocks.compactMap { type, spec in
            spec.blocksToWin > 0 ? Objective(kind: .collect(type), total: spec.blocksToWin) : nil
        }
        // Jelly on an inactive cell can never be cleared (inactive cells never
        // hold a tile, so `resolveMatches` can never reach it) and is never
        // rendered (`BoardNode.buildStaticLayer` only draws jelly for active
        // cells) - counting it here would create an un-completable objective.
        let jellyCount = level.cells.filter { $0.hasJelly && $0.isActive }.count
        if jellyCount > 0 {
            objs.append(Objective(kind: .clearJelly, total: jellyCount))
        }
        self.objectives = objs
        self.pendingEvents = [.introFadeInStarted(duration: Self.introFadeDuration)]
    }

    // MARK: - Public API

    /// Only honored while idle (or tutorial-restricted, and then only within the
    /// restricted rect). Adjacency/wall failures and swaps that produce no match
    /// are both free: no move is spent, no phase change happens.
    @discardableResult
    func attemptSwap(_ a: GridPoint, _ b: GridPoint) -> Bool {
        guard phase == .idle || phase == .tutorialRestricted else { return false }
        if phase == .tutorialRestricted, let rect = tutorialRect,
           !(rect.contains(a) && rect.contains(b)) {
            return false
        }
        guard board.canSwap(a, b) else { return false }

        board.swapTiles(a, b)
        let matches = board.findMatches()
        let touchesSwap = matches.contains { $0.cells.contains(a) || $0.cells.contains(b) }

        guard touchesSwap else {
            board.swapTiles(a, b)
            pendingEvents.append(.swapRejected(a, b))
            return false
        }

        movesRemaining -= 1
        pendingEvents.append(.movesChanged(movesRemaining))
        pendingEvents.append(.swapAnimated(a, b, committed: true))
        pendingSwapMatches = matches
        phase = .swapping
        phaseTimer = swapAnimationDuration
        return true
    }

    /// Resets the hint idle timer - call on any touch, even ones that don't
    /// result in a swap, matching the original's "any interaction defers the hint".
    func notifyUserInteraction() {
        hintIdleTimer = 0
    }

    /// Picks the candidate after `last` in scan order (wrapping around), so
    /// repeated idle hints on an unchanged board cycle through the available
    /// moves instead of always re-highlighting the same pair. Falls back to
    /// the first candidate if `last` is nil or no longer among them (e.g. the
    /// board changed since the previous hint).
    private static func nextHint(
        from candidates: [(GridPoint, GridPoint)],
        excluding last: (GridPoint, GridPoint)?
    ) -> (GridPoint, GridPoint)? {
        guard !candidates.isEmpty else { return nil }
        guard let last else { return candidates[0] }
        if let lastIndex = candidates.firstIndex(where: { $0 == last }) {
            return candidates[(lastIndex + 1) % candidates.count]
        }
        return candidates[0]
    }

    func advance(deltaTime: TimeInterval) -> [GameEvent] {
        defer { pendingEvents.removeAll() }

        switch phase {
        case .won, .lost:
            break

        case .introFadeIn:
            phaseTimer -= deltaTime
            if phaseTimer <= 0 {
                if board.legalSwapExists() {
                    phase = tutorialRect != nil ? .tutorialRestricted : .idle
                } else {
                    phase = .shuffling
                    phaseTimer = Self.shuffleBannerDuration
                    pendingEvents.append(.noMoreMovesShuffleStarted)
                }
            }

        case .idle, .tutorialRestricted:
            hintIdleTimer += deltaTime
            if hintIdleTimer >= Self.hintIdleThreshold {
                hintIdleTimer = 0
                let candidates = board.legalSwaps(restrictedTo: tutorialRect)
                if let next = Self.nextHint(from: candidates, excluding: lastHintSuggestion) {
                    lastHintSuggestion = next
                    pendingEvents.append(.hintSuggested(next.0, next.1))
                }
            }

        case .swapping:
            phaseTimer -= deltaTime
            if phaseTimer <= 0 {
                let matches = pendingSwapMatches
                pendingSwapMatches = []
                resolveMatches(matches)
                phase = .falling
            }

        case .falling:
            let interval = debugFallStepInterval > 0 ? debugFallStepInterval : Self.baseFallStepInterval
            fallStepAccumulator += deltaTime
            guard fallStepAccumulator >= interval else { break }
            fallStepAccumulator = 0
            stepFalling()

        case .shuffling:
            phaseTimer -= deltaTime
            if phaseTimer <= 0 {
                performShuffle()
            }
        }

        return pendingEvents
    }

    func legalSwapExists() -> Bool {
        board.legalSwapExists()
    }

    func firstLegalSwap() -> (GridPoint, GridPoint)? {
        board.firstLegalSwap()
    }

    // MARK: - Initial fill

    /// Mirrors the original's `setupGrid:` random-fill-with-retry: every active cell
    /// without level-authored fixed content gets a random tile from the level's
    /// weighted distribution, retried until the result is both match-free and has at
    /// least one legal move (or the attempt cap is hit, in which case the last
    /// attempt is used anyway). Ongoing *spawn*-cell refills after removals are a
    /// separate, ongoing concern handled by `fillSpawnCells()`.
    private static func makeInitialBoard(level: Level, randomSource: inout some TileRandomSource) -> Board {
        var board = Board(level: level)
        let distribution = level.activeBlocks
            .map { ($0.key, $0.value.spawnPercentage) }
            .filter { $0.1 > 0 }
        guard !distribution.isEmpty else { return board }

        let fillablePoints: [GridPoint] = (0..<board.height).flatMap { row in
            (0..<board.width).compactMap { col -> GridPoint? in
                let point = GridPoint(row: row, col: col)
                guard let cell = board[point], cell.isActive, cell.tile == nil else { return nil }
                return point
            }
        }
        guard !fillablePoints.isEmpty else { return board }

        for _ in 0..<maxInitialFillAttempts {
            var candidate = board
            for point in fillablePoints {
                let type = randomSource.nextSpawnTile(from: distribution)
                candidate.mutate(at: point) { $0.tile = type }
            }
            board = candidate
            if candidate.findMatches().isEmpty && candidate.legalSwapExists() {
                break
            }
        }
        return board
    }

    // MARK: - Falling phase

    private func stepFalling() {
        let moves = board.computeFallMoves()
        if !moves.isEmpty {
            for move in moves {
                let tile = board[move.from]?.tile
                board.mutate(at: move.to) { $0.tile = tile }
                board.mutate(at: move.from) { $0.tile = nil }
                pendingEvents.append(.tileMoved(from: move.from, to: move.to, kind: move.kind))
            }
            return
        }

        if fillSpawnCells() {
            return
        }

        let matches = board.findMatches()
        if !matches.isEmpty {
            resolveMatches(matches)
            return
        }

        settle()
    }

    private func fillSpawnCells() -> Bool {
        let distribution = level.activeBlocks
            .map { ($0.key, $0.value.spawnPercentage) }
            .filter { $0.1 > 0 }
        guard !distribution.isEmpty else { return false }

        var spawnedAny = false
        for (index, spec) in level.cells.enumerated() where spec.isSpawnPoint {
            let point = GridPoint(row: index / level.width, col: index % level.width)
            guard board[point]?.isActive == true, board[point]?.tile == nil else { continue }
            let type = randomSource.nextSpawnTile(from: distribution)
            board.mutate(at: point) { $0.tile = type }
            pendingEvents.append(.tilePlaced(point, type))
            spawnedAny = true
        }
        return spawnedAny
    }

    private func settle() {
        if !objectives.isEmpty, objectives.allSatisfy(\.isComplete) {
            phase = .won
            pendingEvents.append(.won(score: score, stars: starsEarned()))
            return
        }
        if movesRemaining <= 0, !objectives.allSatisfy(\.isComplete) {
            phase = .lost
            pendingEvents.append(.lost)
            return
        }

        if tutorialRect != nil {
            tutorialRect = nil
            pendingEvents.append(.tutorialUnlocked)
        }

        if board.legalSwapExists() {
            phase = .idle
        } else {
            phase = .shuffling
            phaseTimer = Self.shuffleBannerDuration
            pendingEvents.append(.noMoreMovesShuffleStarted)
        }
    }

    private func performShuffle() {
        board = ShuffleSolver.shuffledBoard(board, randomSource: &randomSource)
        pendingEvents.append(.noMoreMovesShuffleFinished)
        phase = .idle
    }

    private func starsEarned() -> Int {
        StarThreshold.stars(forScore: score, thresholds: level.starThresholds)
    }

    // MARK: - Match resolution

    /// Applies every effect of a resolved set of matches in one shot: combo-pill
    /// downgrades, wall destruction between two co-removed cells, tile removal with
    /// scoring/objective updates, and solid damage to orthogonal neighbors. All of
    /// this is a discrete state change - the *animation* of it is the SpriteKit
    /// layer's job, driven by the emitted events.
    private func resolveMatches(_ matches: [MatchGroup]) {
        let uniquePoints = Set(matches.flatMap(\.cells))

        var pointsToRemove: [GridPoint] = []
        for point in uniquePoints {
            guard let tile = board[point]?.tile else { continue }
            if let downgraded = tile.downgradedForm {
                board.mutate(at: point) { $0.tile = downgraded }
                pendingEvents.append(.comboPillDowngraded(point, from: tile, to: downgraded))
            } else {
                pointsToRemove.append(point)
            }
        }

        let removalSet = Set(pointsToRemove)
        for point in pointsToRemove {
            guard let cell = board[point] else { continue }
            if cell.hasWallRight {
                let right = GridPoint(row: point.row, col: point.col + 1)
                if removalSet.contains(right) {
                    board.mutate(at: point) { $0.hasWallRight = false }
                    pendingEvents.append(.wallDestroyed(point, right))
                }
            }
            if cell.hasWallBelow {
                let below = GridPoint(row: point.row + 1, col: point.col)
                if removalSet.contains(below) {
                    board.mutate(at: point) { $0.hasWallBelow = false }
                    pendingEvents.append(.wallDestroyed(point, below))
                }
            }
        }

        for point in pointsToRemove {
            guard let tile = board[point]?.tile else { continue }

            if board[point]?.hasJelly == true {
                board.mutate(at: point) { $0.hasJelly = false }
                pendingEvents.append(.jellyCleared(point))
                decrementObjective(.clearJelly, by: 1)
            }

            let scoreAwarded = tile.isBacteria ? 750 : 375
            score += scoreAwarded
            pendingEvents.append(.tileRemoved(point, tile, scoreAwarded: scoreAwarded))
            pendingEvents.append(.scoreChanged(score))

            if tile.isBacteria {
                pendingEvents.append(.bacteriaFellOff(point))
                decrementObjective(.collect(tile), by: 1)
            } else if hasObjective(.collect(tile)) {
                score += 35
                pendingEvents.append(.objectiveFlyCompleted(.collect(tile), bonusScore: 35))
                pendingEvents.append(.scoreChanged(score))
                decrementObjective(.collect(tile), by: 1)
            }

            board.mutate(at: point) { $0.tile = nil }
            damageAdjacentSolids(around: point)
        }
    }

    private func damageAdjacentSolids(around point: GridPoint) {
        let neighbors = [
            GridPoint(row: point.row - 1, col: point.col),
            GridPoint(row: point.row + 1, col: point.col),
            GridPoint(row: point.row, col: point.col - 1),
            GridPoint(row: point.row, col: point.col + 1),
        ]
        for neighbor in neighbors {
            guard let neighborTile = board[neighbor]?.tile, neighborTile.isSolid else { continue }
            if let next = neighborTile.nextDamageStage {
                board.mutate(at: neighbor) { $0.tile = next }
                pendingEvents.append(.solidDamaged(neighbor, from: neighborTile, to: next))
            } else {
                board.mutate(at: neighbor) { $0.tile = nil }
                pendingEvents.append(.solidDamaged(neighbor, from: neighborTile, to: nil))
            }
        }
    }

    private func decrementObjective(_ kind: ObjectiveKind, by amount: Int) {
        guard let idx = objectives.firstIndex(where: { $0.kind == kind }) else { return }
        objectives[idx].decrement(by: amount)
        pendingEvents.append(.objectiveProgressed(kind, remaining: objectives[idx].remaining))
    }

    private func hasObjective(_ kind: ObjectiveKind) -> Bool {
        objectives.contains { $0.kind == kind }
    }

    // MARK: - Tutorial

    /// Was level 1 only, for this slice: restricted input to a rect built from
    /// the bounding box of the level's spawn cells (expanded by one) until the
    /// first valid match was made (see `settle()`). Disabled for now - always
    /// returns `nil`, so no level shows the tutorial overlay.
    private static func tutorialRect(for level: Level) -> GridRect? {
        nil
    }
}
