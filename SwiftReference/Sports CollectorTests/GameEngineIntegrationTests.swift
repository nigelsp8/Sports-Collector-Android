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

struct GameEngineIntegrationTests {

    @Test func acceptedSwapSpendsAMoveScoresAndCanWin() {
        let level = TestLevel.make(
            rows: [
                [TestLevel.Spec(tile: .pinkTablet), TestLevel.Spec(tile: .orangeTablet), TestLevel.Spec(tile: .pinkTablet)],
                [TestLevel.Spec(tile: .pinkTablet), TestLevel.Spec(tile: .pinkTablet), TestLevel.Spec(tile: .orangeTablet)],
            ],
            movesAllowed: 5,
            activeBlocks: [.pinkTablet: ActiveBlockSpec(blocksToWin: 2, spawnPercentage: 0)]
        )
        let engine = GameEngine(level: level)
        _ = runUntilSettled(engine) // clear intro fade

        let committed = engine.attemptSwap(GridPoint(row: 0, col: 1), GridPoint(row: 1, col: 1))
        #expect(committed)
        #expect(engine.movesRemaining == 4)

        let events = runUntilSettled(engine)
        // 3 pink tiles removed, all counting toward the pink objective (+35 bonus each): 3*(375+35)
        #expect(engine.score == 1230)
        #expect(engine.phase == .won)
        #expect(events.contains { if case .won(let score, let stars) = $0 { return score == 1230 && stars == 3 } else { return false } })
    }

    @Test func rejectedSwapSpendsNoMoveAndMakesNoBoardChange() {
        // Cols 1-3 hold a legal move (swap (0,2)<->(1,2) makes row 0 all pink), so the
        // board isn't "stuck" and won't auto-shuffle out from under this test; col 0 is
        // colorwise isolated from that pattern so its swap is unambiguously non-matching.
        let level = TestLevel.make(rows: [
            [TestLevel.Spec(tile: .yellowTablet), TestLevel.Spec(tile: .pinkTablet), TestLevel.Spec(tile: .orangeTablet), TestLevel.Spec(tile: .pinkTablet)],
            [TestLevel.Spec(tile: .blueTablet), TestLevel.Spec(tile: .pinkTablet), TestLevel.Spec(tile: .pinkTablet), TestLevel.Spec(tile: .orangeTablet)],
        ], movesAllowed: 5)
        let engine = GameEngine(level: level)
        _ = runUntilSettled(engine)

        let before = engine.movesRemaining
        let committed = engine.attemptSwap(GridPoint(row: 0, col: 0), GridPoint(row: 1, col: 0))
        #expect(!committed)
        #expect(engine.movesRemaining == before)
        #expect(engine.board[GridPoint(row: 0, col: 0)]?.tile == .yellowTablet)
        #expect(engine.board[GridPoint(row: 1, col: 0)]?.tile == .blueTablet)
    }

    @Test func runningOutOfMovesWithUnmetObjectivesLoses() {
        let level = TestLevel.make(
            rows: [
                [TestLevel.Spec(tile: .pinkTablet), TestLevel.Spec(tile: .orangeTablet), TestLevel.Spec(tile: .pinkTablet)],
                [TestLevel.Spec(tile: .pinkTablet), TestLevel.Spec(tile: .pinkTablet), TestLevel.Spec(tile: .orangeTablet)],
            ],
            movesAllowed: 1,
            activeBlocks: [.orangeTablet: ActiveBlockSpec(blocksToWin: 5, spawnPercentage: 0)]
        )
        let engine = GameEngine(level: level)
        _ = runUntilSettled(engine)

        _ = engine.attemptSwap(GridPoint(row: 0, col: 1), GridPoint(row: 1, col: 1))
        #expect(engine.movesRemaining == 0)

        let events = runUntilSettled(engine)
        #expect(engine.phase == .lost)
        #expect(events.contains(.lost))
    }
}
