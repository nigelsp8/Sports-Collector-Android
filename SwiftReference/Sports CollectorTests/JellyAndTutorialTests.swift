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

struct JellyObjectiveTests {

    @Test func firstMatchOnJellyClearsItAndDecrementsTheAutoAddedObjective() {
        let level = TestLevel.make(rows: [
            [TestLevel.Spec(tile: .pinkTablet, hasJelly: true), TestLevel.Spec(tile: .orangeTablet, hasJelly: true), TestLevel.Spec(tile: .pinkTablet)],
            [TestLevel.Spec(tile: .pinkTablet), TestLevel.Spec(tile: .pinkTablet), TestLevel.Spec(tile: .orangeTablet)],
        ])
        let engine = GameEngine(level: level)
        _ = runUntilSettled(engine)

        #expect(engine.objectives.count == 1)
        #expect(engine.objectives[0].kind == .clearJelly)
        #expect(engine.objectives[0].remaining == 2)

        // Swap (0,1)<->(1,1): row 0 becomes [pink, pink, pink] - a real 3-run, and jelly
        // stays with the cell (not the tile), so (0,0) and (0,1) both still carry it.
        #expect(engine.attemptSwap(GridPoint(row: 0, col: 1), GridPoint(row: 1, col: 1)))
        let events = runUntilSettled(engine)

        #expect(events.filter { if case .jellyCleared = $0 { return true } else { return false } }.count == 2)
        // Both jelly cells cleared and it was the only objective: the level should win.
        #expect(engine.phase == .won)
    }
}

struct TutorialTests {

    // Spawn point sits at (1,1) in a 3x6 grid, so the +/-1 expanded rect covers
    // cols 0-2 only, leaving cols 3-5 genuinely outside it.
    private func tutorialLevel() -> Level {
        TestLevel.make(
            id: 1,
            rows: [
                [TestLevel.Spec(tile: .pinkTablet), TestLevel.Spec(tile: .orangeTablet), TestLevel.Spec(tile: .pinkTablet),
                 TestLevel.Spec(tile: .pinkTablet), TestLevel.Spec(tile: .orangeTablet), TestLevel.Spec(tile: .pinkTablet)],
                [TestLevel.Spec(tile: .pinkTablet), TestLevel.Spec(tile: .pinkTablet, isSpawnPoint: true), TestLevel.Spec(tile: .orangeTablet),
                 TestLevel.Spec(tile: .pinkTablet), TestLevel.Spec(tile: .pinkTablet), TestLevel.Spec(tile: .orangeTablet)],
                [TestLevel.Spec(tile: .yellowTablet), TestLevel.Spec(tile: .orangeTablet), TestLevel.Spec(tile: .yellowTablet),
                 TestLevel.Spec(tile: .yellowTablet), TestLevel.Spec(tile: .orangeTablet), TestLevel.Spec(tile: .yellowTablet)],
            ]
        )
    }

    @Test func startsRestrictedToARectAroundTheSpawnPoints() {
        let engine = GameEngine(level: tutorialLevel())
        _ = runUntilSettled(engine)
        #expect(engine.phase == .tutorialRestricted)
    }

    @Test func swapOutsideTheRestrictedRectIsIgnoredEvenWhenItWouldMatch() {
        let engine = GameEngine(level: tutorialLevel())
        _ = runUntilSettled(engine)

        let before = engine.movesRemaining
        // (0,4)<->(1,4) [orange<->pink] would turn row 0's cols 3-5 into [pink,pink,pink],
        // but cols 3-5 are outside the tutorial rect, so this must be a no-op.
        let committed = engine.attemptSwap(GridPoint(row: 0, col: 4), GridPoint(row: 1, col: 4))
        #expect(!committed)
        #expect(engine.movesRemaining == before)
        #expect(engine.board[GridPoint(row: 0, col: 4)]?.tile == .orangeTablet)
    }

    @Test func firstValidMatchInsideTheRectUnlocksTheWholeBoard() {
        let engine = GameEngine(level: tutorialLevel())
        _ = runUntilSettled(engine)

        // (0,1)<->(1,1) [orange<->pink] turns row 0's cols 0-2 into [pink,pink,pink],
        // and col 1 is inside the tutorial rect.
        let committed = engine.attemptSwap(GridPoint(row: 0, col: 1), GridPoint(row: 1, col: 1))
        #expect(committed)

        let events = runUntilSettled(engine)
        #expect(events.contains(.tutorialUnlocked))
        #expect(engine.phase == .idle)
    }
}
