import Testing
@testable import Sports_Collector

struct InitialBoardFillTests {

    @Test func everyActiveNonFixedCellGetsATileAtLevelStart() {
        let level = TestLevel.make(
            rows: [
                [TestLevel.Spec(tile: nil), TestLevel.Spec(tile: nil), TestLevel.Spec(tile: nil)],
                [TestLevel.Spec(tile: nil), TestLevel.Spec(tile: .solidStage1), TestLevel.Spec(tile: nil)],
                [TestLevel.Spec(tile: nil), TestLevel.Spec(tile: nil), TestLevel.Spec(tile: nil)],
            ],
            activeBlocks: [
                .pinkTablet: ActiveBlockSpec(blocksToWin: 0, spawnPercentage: 50),
                .orangeTablet: ActiveBlockSpec(blocksToWin: 0, spawnPercentage: 50),
            ]
        )
        let engine = GameEngine(level: level)

        for row in 0..<3 {
            for col in 0..<3 {
                let point = GridPoint(row: row, col: col)
                #expect(engine.board[point]?.tile != nil, "cell \(point) should have a tile at level start")
            }
        }
        // The level-authored fixed solid must still be exactly where it was placed.
        #expect(engine.board[GridPoint(row: 1, col: 1)]?.tile == .solidStage1)
    }

    @Test func realLevelOneBoardStartsFullyPopulated() throws {
        let level = try LevelLoader.loadLevel(resourceName: "1", level: 1)
        let engine = GameEngine(level: level)

        for (index, spec) in level.cells.enumerated() where spec.isActive {
            let point = GridPoint(row: index / level.width, col: index % level.width)
            #expect(engine.board[point]?.tile != nil, "active cell \(point) should have a tile at level start")
        }
    }
}
