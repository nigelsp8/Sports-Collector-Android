import Testing
@testable import Sports_Collector

struct HintSystemTests {

    private func levelWithOneLegalMove() -> Level {
        TestLevel.make(rows: [
            [TestLevel.Spec(tile: .pinkTablet), TestLevel.Spec(tile: .orangeTablet), TestLevel.Spec(tile: .pinkTablet)],
            [TestLevel.Spec(tile: .pinkTablet), TestLevel.Spec(tile: .pinkTablet), TestLevel.Spec(tile: .orangeTablet)],
        ])
    }

    @Test func hintFiresOnlyAfterTheIdleThreshold() {
        let engine = GameEngine(level: levelWithOneLegalMove())
        _ = engine.advance(deltaTime: GameEngine.introFadeDuration + 0.01) // clear intro fade

        let early = engine.advance(deltaTime: GameEngine.hintIdleThreshold - 0.5)
        #expect(!early.contains { if case .hintSuggested = $0 { return true } else { return false } })

        let late = engine.advance(deltaTime: 1.0)
        #expect(late.contains { if case .hintSuggested = $0 { return true } else { return false } })
    }

    @Test func userInteractionResetsTheIdleTimer() {
        let engine = GameEngine(level: levelWithOneLegalMove())
        _ = engine.advance(deltaTime: GameEngine.introFadeDuration + 0.01)

        _ = engine.advance(deltaTime: GameEngine.hintIdleThreshold - 0.5)
        engine.notifyUserInteraction()
        let afterReset = engine.advance(deltaTime: 0.6)
        #expect(!afterReset.contains { if case .hintSuggested = $0 { return true } else { return false } })
    }

    @Test func repeatedIdleHintsCycleThroughDifferentLegalMovesWhenMoreThanOneExists() {
        // Two independent near-match blocks (cols 0-2 and cols 3-5), each with
        // its own legal swap, so the board has more than one candidate hint.
        let level = TestLevel.make(rows: [
            [TestLevel.Spec(tile: .pinkTablet), TestLevel.Spec(tile: .orangeTablet), TestLevel.Spec(tile: .pinkTablet),
             TestLevel.Spec(tile: .yellowTablet), TestLevel.Spec(tile: .blueTablet), TestLevel.Spec(tile: .yellowTablet)],
            [TestLevel.Spec(tile: .pinkTablet), TestLevel.Spec(tile: .pinkTablet), TestLevel.Spec(tile: .orangeTablet),
             TestLevel.Spec(tile: .yellowTablet), TestLevel.Spec(tile: .yellowTablet), TestLevel.Spec(tile: .blueTablet)],
        ])
        let engine = GameEngine(level: level)
        _ = engine.advance(deltaTime: GameEngine.introFadeDuration + 0.01)

        func nextHintPair() -> (GridPoint, GridPoint)? {
            for event in engine.advance(deltaTime: GameEngine.hintIdleThreshold + 0.01) {
                if case .hintSuggested(let a, let b) = event { return (a, b) }
            }
            return nil
        }

        guard let first = nextHintPair(), let second = nextHintPair() else {
            Issue.record("expected a hint on both idle cycles")
            return
        }
        #expect(first != second)
    }
}

struct ShuffleTests {

    @Test func noLegalMoveTriggersShuffleAndReturnsToIdle() {
        // No swap on this board can ever produce a match.
        let level = TestLevel.make(rows: [[
            TestLevel.Spec(tile: .pinkTablet), TestLevel.Spec(tile: .orangeTablet), TestLevel.Spec(tile: .yellowTablet),
        ]])
        let engine = GameEngine(level: level)

        var allEvents: [GameEvent] = []
        for _ in 0..<200 {
            allEvents.append(contentsOf: engine.advance(deltaTime: 0.1))
            if engine.phase == .idle { break }
        }

        #expect(allEvents.contains(.noMoreMovesShuffleStarted))
        #expect(allEvents.contains(.noMoreMovesShuffleFinished))
        #expect(engine.phase == .idle)
    }

    @Test func shuffleResultIsAPermutationOfTheOriginalMultiset() {
        let level = TestLevel.make(rows: [[
            TestLevel.Spec(tile: .pinkTablet), TestLevel.Spec(tile: .orangeTablet), TestLevel.Spec(tile: .yellowTablet),
        ]])
        let board = Board(level: level)
        var random = SeededTileRandomSource(seed: 7)
        let shuffled = ShuffleSolver.shuffledBoard(board, randomSource: &random)

        let originalTiles = board.cells.compactMap(\.tile).sorted { $0.rawValue < $1.rawValue }
        let shuffledTiles = shuffled.cells.compactMap(\.tile).sorted { $0.rawValue < $1.rawValue }
        #expect(originalTiles == shuffledTiles)
    }
}
