import Foundation

/// Redistributes the existing multiset of tiles across the board when no legal
/// move remains. Solid blockers are excluded entirely from relocation - both as
/// something that moves and as an available destination - which is a deliberate
/// fix versus the original, whose attempt at "keep solids fixed" was dead code
/// (an unreachable condition meant solids got shuffled like everything else).
enum ShuffleSolver {
    static let maxAttempts = 2000

    static func shuffledBoard(_ board: Board, randomSource: inout some TileRandomSource) -> Board {
        let eligiblePoints: [GridPoint] = (0..<board.height).flatMap { row in
            (0..<board.width).compactMap { col -> GridPoint? in
                let point = GridPoint(row: row, col: col)
                guard let cell = board[point], cell.isActive, !cell.isFixedSolidAnchor,
                      let tile = cell.tile, !tile.isSolid else { return nil }
                return point
            }
        }
        let tiles = eligiblePoints.compactMap { board[$0]?.tile }
        guard !tiles.isEmpty else { return board }

        var best = board
        for _ in 0..<maxAttempts {
            var candidate = board
            let shuffledTiles = randomSource.shuffled(tiles)
            for (point, tile) in zip(eligiblePoints, shuffledTiles) {
                candidate.mutate(at: point) { $0.tile = tile }
            }
            best = candidate
            if candidate.findMatches().isEmpty && candidate.legalSwapExists() {
                return candidate
            }
        }
        return best
    }
}
