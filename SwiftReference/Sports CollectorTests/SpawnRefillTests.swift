import Testing
import Foundation
@testable import Sports_Collector

/// Regression coverage for a reported bug: when several cells in a spawn column
/// become empty at once, new tiles should appear in ALL of them, not just the
/// first (topmost) one.
struct SpawnRefillTests {

    @Test func allSimultaneouslyEmptySpawnCellsInASingleColumnRefillTogether() {
        // col0 (spawn) is a pre-existing vertical pink match; cols 2-4 hold a
        // separate swap-triggered match so both resolve in the same attemptSwap call.
        let level = TestLevel.make(
            rows: [
                [TestLevel.Spec(tile: .pinkTablet, isSpawnPoint: true), TestLevel.Spec(tile: .orangeTablet),
                 TestLevel.Spec(tile: .pinkTablet), TestLevel.Spec(tile: .orangeTablet), TestLevel.Spec(tile: .pinkTablet)],
                [TestLevel.Spec(tile: .pinkTablet, isSpawnPoint: true), TestLevel.Spec(tile: .yellowTablet),
                 TestLevel.Spec(tile: .pinkTablet), TestLevel.Spec(tile: .pinkTablet), TestLevel.Spec(tile: .orangeTablet)],
                [TestLevel.Spec(tile: .pinkTablet, isSpawnPoint: true), TestLevel.Spec(tile: .orangeTablet),
                 TestLevel.Spec(tile: .yellowTablet), TestLevel.Spec(tile: .purpleTablet), TestLevel.Spec(tile: .yellowTablet)],
            ],
            activeBlocks: [.pinkTablet: ActiveBlockSpec(blocksToWin: 0, spawnPercentage: 100)]
        )
        let engine = GameEngine(level: level)
        _ = runUntilSettled(engine) // clear intro fade

        // Swap (0,3)<->(1,3): orange<->pink makes row0 cols2-4 = [pink,pink,pink],
        // which also causes the engine to notice col0's pre-existing pink match.
        #expect(engine.attemptSwap(GridPoint(row: 0, col: 3), GridPoint(row: 1, col: 3)))

        // Drain events one tick at a time and find the tick where fillSpawnCells
        // fires - it should place tiles in all 3 of col0's rows in that SAME tick.
        var placedInColumnZeroThisTick: [GridPoint] = []
        for _ in 0..<50 {
            let events = engine.advance(deltaTime: 0.05)
            let placedHere = events.compactMap { event -> GridPoint? in
                if case .tilePlaced(let point, _) = event, point.col == 0 { return point }
                return nil
            }
            if !placedHere.isEmpty {
                placedInColumnZeroThisTick = placedHere
                break
            }
        }

        #expect(
            Set(placedInColumnZeroThisTick) == Set([
                GridPoint(row: 0, col: 0), GridPoint(row: 1, col: 0), GridPoint(row: 2, col: 0),
            ]),
            "expected all 3 emptied spawn cells in column 0 to refill in the same tick, got \(placedInColumnZeroThisTick)"
        )
    }
    @Test func spawnCellsInDifferentColumnsBothRefillInTheSameTick() {
        // col0 (spawn) starts as pink,pink,orange - one step from a vertical
        // match. col3 (spawn) is already a complete blue,blue,blue match. Swapping
        // (2,0)<->(2,1) completes col0's match and touches it, so the engine accepts
        // the swap; col3's pre-existing match is picked up for free in the same
        // findMatches() call, so both spawn columns empty out together.
        let level = TestLevel.make(
            rows: [
                [TestLevel.Spec(tile: .pinkTablet, isSpawnPoint: true), TestLevel.Spec(tile: .orangeTablet),
                 TestLevel.Spec(tile: .yellowTablet), TestLevel.Spec(tile: .blueTablet, isSpawnPoint: true)],
                [TestLevel.Spec(tile: .pinkTablet, isSpawnPoint: true), TestLevel.Spec(tile: .yellowTablet),
                 TestLevel.Spec(tile: .purpleTablet), TestLevel.Spec(tile: .blueTablet, isSpawnPoint: true)],
                [TestLevel.Spec(tile: .orangeTablet), TestLevel.Spec(tile: .pinkTablet),
                 TestLevel.Spec(tile: .yellowTablet), TestLevel.Spec(tile: .blueTablet, isSpawnPoint: true)],
            ],
            activeBlocks: [
                .pinkTablet: ActiveBlockSpec(blocksToWin: 0, spawnPercentage: 50),
                .blueTablet: ActiveBlockSpec(blocksToWin: 0, spawnPercentage: 50),
            ]
        )
        let engine = GameEngine(level: level)
        _ = runUntilSettled(engine)

        #expect(engine.attemptSwap(GridPoint(row: 2, col: 0), GridPoint(row: 2, col: 1)))

        var placedThisTick: Set<GridPoint> = []
        for _ in 0..<50 {
            let events = engine.advance(deltaTime: 0.05)
            let placed = events.compactMap { event -> GridPoint? in
                if case .tilePlaced(let point, _) = event, point.col == 0 || point.col == 3 { return point }
                return nil
            }
            if !placed.isEmpty {
                placedThisTick = Set(placed)
                break
            }
        }

        // (2,0) isn't a spawn point in this fixture (it's the plain tile the
        // triggering swap needed), so it's correctly excluded here - only the
        // *actual* spawn cells across both columns should refill together.
        let expected = Set([
            GridPoint(row: 0, col: 0), GridPoint(row: 1, col: 0),
            GridPoint(row: 0, col: 3), GridPoint(row: 1, col: 3), GridPoint(row: 2, col: 3),
        ])
        #expect(placedThisTick == expected, "expected both spawn columns to fully refill in the same tick, got \(placedThisTick)")
    }

}

private func runUntilSettled(_ engine: GameEngine, dt: TimeInterval = 0.05, maxSteps: Int = 1000) -> [GameEvent] {
    var all: [GameEvent] = []
    for _ in 0..<maxSteps {
        all.append(contentsOf: engine.advance(deltaTime: dt))
        switch engine.phase {
        case .idle, .tutorialRestricted, .won, .lost:
            return all
        default:
            continue
        }
    }
    return all
}
