import Foundation

enum MoveKind: Equatable {
    case straightDown, diagonalRight, diagonalLeft
}

struct FallMove: Equatable {
    let from: GridPoint
    let to: GridPoint
    let kind: MoveKind
}

struct MatchGroup: Equatable {
    enum Cause: Equatable {
        case colorRun
        case bottomRowBacteria
    }
    let cells: [GridPoint]
    let color: TileColor?
    let cause: Cause
}

/// The live board: storage plus pure queries (adjacency/wall rules, match
/// detection, gravity). Mutation happens via `mutate(at:_:)`; the higher-level
/// removal/fall/spawn *sequencing* lives in `GameEngine`, not here.
struct Board {
    let width: Int
    let height: Int
    private(set) var cells: [Cell]

    init(level: Level) {
        width = level.width
        height = level.height
        cells = level.cells.enumerated().map { index, spec in
            Cell(
                point: GridPoint(row: index / level.width, col: index % level.width),
                isActive: spec.isActive,
                hasWallBelow: spec.hasWallBelow,
                hasWallRight: spec.hasWallRight,
                isSpawnPoint: spec.isSpawnPoint,
                hasJelly: spec.hasJelly,
                tile: spec.fixedTile,
                isFixedSolidAnchor: spec.fixedTile == .solidStage1
            )
        }
    }

    private func index(of point: GridPoint) -> Int? {
        guard point.row >= 0, point.row < height, point.col >= 0, point.col < width else { return nil }
        return point.row * width + point.col
    }

    subscript(_ point: GridPoint) -> Cell? {
        guard let idx = index(of: point) else { return nil }
        return cells[idx]
    }

    mutating func mutate(at point: GridPoint, _ body: (inout Cell) -> Void) {
        guard let idx = index(of: point) else { return }
        body(&cells[idx])
    }

    // MARK: - Swapping

    /// Orthogonal adjacency, both cells active and occupied, no wall between
    /// them, and neither tile is a solid blocker (solids are immovable - they
    /// only clear via adjacent matches/damage, never by swapping). Diagonals
    /// are never valid.
    func canSwap(_ a: GridPoint, _ b: GridPoint) -> Bool {
        guard let cellA = self[a], let cellB = self[b],
              cellA.isActive, cellB.isActive,
              cellA.tile != nil, cellB.tile != nil,
              !(cellA.tile?.isSolid ?? false), !(cellB.tile?.isSolid ?? false) else { return false }

        switch (b.row - a.row, b.col - a.col) {
        case (0, 1): return !cellA.hasWallRight
        case (0, -1): return !cellB.hasWallRight
        case (1, 0): return !cellA.hasWallBelow
        case (-1, 0): return !cellB.hasWallBelow
        default: return false
        }
    }

    mutating func swapTiles(_ a: GridPoint, _ b: GridPoint) {
        guard let idxA = index(of: a), let idxB = index(of: b) else { return }
        let tileA = cells[idxA].tile
        cells[idxA].tile = cells[idxB].tile
        cells[idxB].tile = tileA
    }

    // MARK: - Matching

    /// Runs of 3+ sharing a color, horizontal or vertical, plus any bacteria
    /// sitting in the bottom-most active row of its column (which auto-match
    /// regardless of neighbors). Every real tile carries exactly one color bit,
    /// so "shares a color bit" and "has an equal color" are equivalent here -
    /// this is a plain equal-run scan, not a bitmask intersection.
    func findMatches() -> [MatchGroup] {
        var groups: [MatchGroup] = []
        groups.append(contentsOf: colorRuns(horizontal: true))
        groups.append(contentsOf: colorRuns(horizontal: false))
        groups.append(contentsOf: bottomRowBacteriaMatches())
        return groups
    }

    private func colorRuns(horizontal: Bool) -> [MatchGroup] {
        var groups: [MatchGroup] = []
        let outerCount = horizontal ? height : width
        let innerCount = horizontal ? width : height

        for outer in 0..<outerCount {
            var runStart = 0
            var runColor: TileColor?

            func flushRun(endExclusive: Int) {
                defer { runStart = endExclusive; runColor = nil }
                guard let color = runColor, endExclusive - runStart >= 3 else { return }
                let points = (runStart..<endExclusive).map { inner in
                    horizontal ? GridPoint(row: outer, col: inner) : GridPoint(row: inner, col: outer)
                }
                groups.append(MatchGroup(cells: points, color: color, cause: .colorRun))
            }

            for inner in 0..<innerCount {
                let point = horizontal ? GridPoint(row: outer, col: inner) : GridPoint(row: inner, col: outer)
                let color = self[point].flatMap { $0.isActive ? $0.tile?.colorBit : nil }
                if color == nil || color != runColor {
                    flushRun(endExclusive: inner)
                    runStart = inner
                    runColor = color
                }
            }
            flushRun(endExclusive: innerCount)
        }
        return groups
    }

    private func bottomRowBacteriaMatches() -> [MatchGroup] {
        var groups: [MatchGroup] = []
        for col in 0..<width {
            for row in stride(from: height - 1, through: 0, by: -1) {
                let point = GridPoint(row: row, col: col)
                guard let cell = self[point], cell.isActive else { continue }
                if let tile = cell.tile, tile.isBacteria {
                    groups.append(MatchGroup(cells: [point], color: nil, cause: .bottomRowBacteria))
                }
                break // only the bottom-most *active* cell in the column counts
            }
        }
        return groups
    }

    // MARK: - Gravity

    /// A "floor" for chasm-scan purposes is about what's *currently* resting
    /// there, not the cell's permanent authoring: a former solid-anchor cell
    /// whose solid has been fully destroyed is passable again, exactly like any
    /// other emptied cell (see `isFixedSolidAnchor`'s doc comment for why that
    /// flag itself never changes, and `computeFallMoves` for the matching
    /// straight-down/diagonal-source check).
    private func isFloorBoundary(_ cell: Cell) -> Bool {
        !cell.isActive || (cell.tile?.isSolid ?? false) || cell.hasWallBelow
    }

    /// Port of the original's `goodchasm:yp:`. `column` must have a genuine open
    /// chasm at and above `fromRow`: a defined floor boundary somewhere in the
    /// empty span above, with no resting non-solid tile in either neighboring
    /// column alongside that span (which would visually collide with the slide).
    func isChasmClear(column: Int, fromRow: Int) -> Bool {
        var boundaryRow: Int?
        var row = fromRow
        while row >= 0 {
            guard let cell = self[GridPoint(row: row, col: column)] else { break }
            if isFloorBoundary(cell) {
                boundaryRow = row
                break
            }
            if cell.tile != nil { return false }
            row -= 1
        }
        guard let boundaryRow else { return false }
        if boundaryRow == fromRow - 1 { return true }

        for r in stride(from: fromRow - 1, through: boundaryRow, by: -1) {
            for neighborColumn in [column - 1, column + 1] {
                if let neighbor = self[GridPoint(row: r, col: neighborColumn)],
                   let tile = neighbor.tile, !tile.isSolid {
                    return false
                }
            }
        }
        return true
    }

    /// One full-board pass: for every occupied cell (scanned bottom-up,
    /// left-to-right, mirroring the original's scan order so later checks in
    /// the same pass see earlier decisions), find its fall move in priority
    /// order (straight down > diagonal right > diagonal left). A cell whose
    /// tile is currently solid never falls (solids are stationary blockers) -
    /// but once a solid is destroyed, whatever later occupies that cell falls
    /// like any other tile; only the currently-solid *tile* is checked, not
    /// the cell's permanent `isFixedSolidAnchor` authoring flag (see its doc
    /// comment - that flag exists for the shuffle system, not gravity). Pure -
    /// does not mutate `self`; the caller applies the returned moves.
    func computeFallMoves() -> [FallMove] {
        guard height >= 2 else { return [] }
        var moves: [FallMove] = []
        var occupied = cells.map { $0.tile != nil }

        func isOccupied(_ p: GridPoint) -> Bool {
            guard let idx = index(of: p) else { return true }
            return occupied[idx]
        }
        func setOccupied(_ p: GridPoint, _ value: Bool) {
            guard let idx = index(of: p) else { return }
            occupied[idx] = value
        }

        for row in stride(from: height - 2, through: 0, by: -1) {
            for col in 0..<width {
                let source = GridPoint(row: row, col: col)
                guard let cell = self[source], cell.isActive, !(cell.tile?.isSolid ?? false),
                      cell.tile != nil, isOccupied(source) else { continue }

                let below = GridPoint(row: row + 1, col: col)
                if !cell.hasWallBelow, self[below]?.isActive == true, !isOccupied(below) {
                    moves.append(FallMove(from: source, to: below, kind: .straightDown))
                    setOccupied(source, false)
                    setOccupied(below, true)
                    continue
                }

                let rightTarget = GridPoint(row: row + 1, col: col + 1)
                if col < width - 1, !cell.hasWallBelow, !cell.hasWallRight,
                   self[rightTarget]?.isActive == true, !isOccupied(rightTarget),
                   isChasmClear(column: col + 1, fromRow: row) {
                    moves.append(FallMove(from: source, to: rightTarget, kind: .diagonalRight))
                    setOccupied(source, false)
                    setOccupied(rightTarget, true)
                    continue
                }

                let leftTarget = GridPoint(row: row + 1, col: col - 1)
                if col > 0, !cell.hasWallBelow,
                   self[GridPoint(row: row, col: col - 1)]?.hasWallRight == false,
                   self[leftTarget]?.isActive == true, !isOccupied(leftTarget),
                   isChasmClear(column: col - 1, fromRow: row) {
                    moves.append(FallMove(from: source, to: leftTarget, kind: .diagonalLeft))
                    setOccupied(source, false)
                    setOccupied(leftTarget, true)
                }
            }
        }
        return moves
    }

    // MARK: - Legal move search

    func legalSwapExists() -> Bool {
        firstLegalSwap() != nil
    }

    func firstLegalSwap(restrictedTo rect: GridRect? = nil) -> (GridPoint, GridPoint)? {
        legalSwaps(restrictedTo: rect).first
    }

    /// Every swap (not just the first found) that produces a match, in
    /// board-scan order. Lets a caller offer a different legal move than one
    /// already shown for the same board state, instead of always returning
    /// the same pair.
    func legalSwaps(restrictedTo rect: GridRect? = nil) -> [(GridPoint, GridPoint)] {
        var found: [(GridPoint, GridPoint)] = []
        for row in 0..<height {
            for col in 0..<width {
                let a = GridPoint(row: row, col: col)
                if let rect, !rect.contains(a) { continue }
                for (dr, dc) in [(0, 1), (1, 0)] {
                    let b = GridPoint(row: row + dr, col: col + dc)
                    if let rect, !rect.contains(b) { continue }
                    guard canSwap(a, b) else { continue }
                    var trial = self
                    trial.swapTiles(a, b)
                    if trial.findMatches().contains(where: { $0.cells.contains(a) || $0.cells.contains(b) }) {
                        found.append((a, b))
                    }
                }
            }
        }
        return found
    }
}
