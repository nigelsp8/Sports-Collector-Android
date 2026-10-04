import Testing
import Foundation
@testable import Sports_Collector

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

struct SolidBlockerTests {

    @Test func removalAdjacentToSolidAdvancesItsDamageStageWithoutScoring() {
        let level = TestLevel.make(rows: [[
            TestLevel.Spec(tile: .pinkTablet), TestLevel.Spec(tile: .orangeTablet),
            TestLevel.Spec(tile: .pinkTablet), TestLevel.Spec(tile: .pinkTablet),
            TestLevel.Spec(tile: .solidStage1),
        ]])
        let engine = GameEngine(level: level)
        _ = runUntilSettled(engine)

        // Swap col0<->col1: [orange, pink, pink, pink, solid1] -> cols 1-3 match, solid at col4 takes damage.
        #expect(engine.attemptSwap(GridPoint(row: 0, col: 0), GridPoint(row: 0, col: 1)))
        let events = runUntilSettled(engine)

        #expect(engine.board[GridPoint(row: 0, col: 4)]?.tile == .solidStage2)
        #expect(engine.score == 1125) // 3 plain tiles removed, no objectives configured: 3 * 375
        #expect(events.contains { if case .solidDamaged(_, let from, let to) = $0 { return from == .solidStage1 && to == .solidStage2 } else { return false } })
        #expect(!events.contains { if case .tileRemoved(_, .solidStage1, _) = $0 { return true } else { return false } })
    }

    @Test func solidNeverRelocatesDuringShuffle() {
        let level = TestLevel.make(rows: [[
            TestLevel.Spec(tile: .solidStage1), TestLevel.Spec(tile: .pinkTablet), TestLevel.Spec(tile: .orangeTablet),
        ]])
        var board = Board(level: level)
        var random = SeededTileRandomSource(seed: 42)
        for _ in 0..<20 {
            board = ShuffleSolver.shuffledBoard(board, randomSource: &random)
            #expect(board[GridPoint(row: 0, col: 0)]?.tile == .solidStage1)
        }
    }
}

struct ComboPillTests {

    @Test func matchedComboPillDowngradesInPlaceInsteadOfBeingRemoved() {
        let level = TestLevel.make(rows: [[
            TestLevel.Spec(tile: .orangeTablet), TestLevel.Spec(tile: .orangeTablet),
            TestLevel.Spec(tile: .yellowTablet), TestLevel.Spec(tile: .orangeBluePill),
        ]])
        let engine = GameEngine(level: level)
        _ = runUntilSettled(engine)

        // Swap col2<->col3: [orange, orange, orangeBluePill, yellow] -> cols 0-2 match on the orange bit.
        #expect(engine.attemptSwap(GridPoint(row: 0, col: 2), GridPoint(row: 0, col: 3)))
        let events = runUntilSettled(engine)

        let downgradedPoint = GridPoint(row: 0, col: 2)
        // Check the transition via its event, not the tile's final resting position: this
        // tiny 4-cell board is stuck (no legal move) once 2 of its 4 tiles are removed, which
        // triggers a shuffle that's free to relocate the downgraded tile before we can inspect it.
        #expect(events.contains(.comboPillDowngraded(downgradedPoint, from: .orangeBluePill, to: .orangeTablet)))
        #expect(engine.score == 750) // only the 2 plain tablets were actually removed: 2 * 375
        #expect(!events.contains { if case .tileRemoved(downgradedPoint, _, _) = $0 { return true } else { return false } })
    }
}

struct WallTests {

    @Test func swapAcrossAWallIsRefusedWithNoMoveSpent() {
        let level = TestLevel.make(rows: [[
            TestLevel.Spec(tile: .pinkTablet, hasWallRight: true), TestLevel.Spec(tile: .orangeTablet),
        ]], movesAllowed: 3)
        let engine = GameEngine(level: level)
        _ = runUntilSettled(engine)

        let before = engine.movesRemaining
        #expect(!engine.attemptSwap(GridPoint(row: 0, col: 0), GridPoint(row: 0, col: 1)))
        #expect(engine.movesRemaining == before)
    }

    @Test func matchStraddlingAWallDestroysIt() {
        let level = TestLevel.make(rows: [
            [TestLevel.Spec(tile: .pinkTablet), TestLevel.Spec(tile: .orangeTablet)],
            [TestLevel.Spec(tile: .pinkTablet, hasWallBelow: true), TestLevel.Spec(tile: .yellowTablet)],
            [TestLevel.Spec(tile: .orangeTablet), TestLevel.Spec(tile: .pinkTablet)],
        ])
        let engine = GameEngine(level: level)
        _ = runUntilSettled(engine)

        // Swap (2,0)<->(2,1): col 0 becomes pink/pink/pink, straddling the wall below (1,0).
        #expect(engine.attemptSwap(GridPoint(row: 2, col: 0), GridPoint(row: 2, col: 1)))
        let events = runUntilSettled(engine)

        #expect(events.contains(.wallDestroyed(GridPoint(row: 1, col: 0), GridPoint(row: 2, col: 0))))
    }
}
