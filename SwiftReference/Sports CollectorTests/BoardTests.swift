import Testing
@testable import Sports_Collector

private struct TestCell {
    var tile: TileType? = nil
    var isActive: Bool = true
    var hasWallBelow: Bool = false
    var hasWallRight: Bool = false
    var isSpawnPoint: Bool = false
}

private func makeBoard(_ rows: [[TestCell]]) -> Board {
    let height = rows.count
    let width = rows.first?.count ?? 0
    var specs: [LevelCellSpec] = []
    for row in rows {
        precondition(row.count == width, "all rows must have equal width")
        for cell in row {
            specs.append(LevelCellSpec(
                isActive: cell.isActive,
                hasWallBelow: cell.hasWallBelow,
                hasWallRight: cell.hasWallRight,
                isSpawnPoint: cell.isSpawnPoint,
                isExit: false,
                hasJelly: false,
                fixedTile: cell.tile
            ))
        }
    }
    let level = Level(
        mapNumber: 0, id: 0, width: width, height: height, movesAllowed: 10,
        starThresholds: (1, 2, 3), backgroundImageFilename: "",
        cells: specs, activeBlocks: [:]
    )
    return Board(level: level)
}

private func inactive() -> TestCell { TestCell(isActive: false) }

struct BoardTests {

    // MARK: - Swapping

    @Test func orthogonalAdjacentTilesCanSwap() {
        let board = makeBoard([
            [TestCell(tile: .pinkTablet), TestCell(tile: .orangeTablet)],
            [TestCell(tile: .yellowTablet), TestCell(tile: .greenTablet)],
        ])
        #expect(board.canSwap(GridPoint(row: 0, col: 0), GridPoint(row: 0, col: 1)))
        #expect(board.canSwap(GridPoint(row: 0, col: 0), GridPoint(row: 1, col: 0)))
    }

    @Test func diagonalTilesCannotSwap() {
        let board = makeBoard([
            [TestCell(tile: .pinkTablet), TestCell(tile: .orangeTablet)],
            [TestCell(tile: .yellowTablet), TestCell(tile: .greenTablet)],
        ])
        #expect(!board.canSwap(GridPoint(row: 0, col: 0), GridPoint(row: 1, col: 1)))
    }

    @Test func inactiveCellsCannotSwap() {
        let board = makeBoard([[TestCell(tile: .pinkTablet), inactive()]])
        #expect(!board.canSwap(GridPoint(row: 0, col: 0), GridPoint(row: 0, col: 1)))
    }

    @Test func wallBetweenCellsBlocksSwap() {
        var board = makeBoard([[TestCell(tile: .pinkTablet), TestCell(tile: .orangeTablet)]])
        board.mutate(at: GridPoint(row: 0, col: 0)) { $0.hasWallRight = true }
        #expect(!board.canSwap(GridPoint(row: 0, col: 0), GridPoint(row: 0, col: 1)))
    }

    // MARK: - Matching

    @Test func threeInARowHorizontalMatches() {
        let board = makeBoard([[
            TestCell(tile: .pinkTablet), TestCell(tile: .pinkTablet),
            TestCell(tile: .pinkTablet), TestCell(tile: .orangeTablet),
        ]])
        let matches = board.findMatches()
        #expect(matches.contains { $0.cells.count == 3 && $0.color == .pink && $0.cause == .colorRun })
    }

    @Test func twoInARowDoesNotMatch() {
        let board = makeBoard([[
            TestCell(tile: .pinkTablet), TestCell(tile: .pinkTablet), TestCell(tile: .orangeTablet),
        ]])
        #expect(board.findMatches().isEmpty)
    }

    @Test func threeInAColumnVerticallyMatches() {
        let board = makeBoard([
            [TestCell(tile: .blueTablet)],
            [TestCell(tile: .blueTablet)],
            [TestCell(tile: .blueTablet)],
        ])
        let matches = board.findMatches()
        #expect(matches.contains { $0.cells.count == 3 && $0.color == .blue })
    }

    @Test func comboPillMatchesPlainTabletsOfItsCarriedColor() {
        let board = makeBoard([[
            TestCell(tile: .orangeTablet), TestCell(tile: .orangeBluePill), TestCell(tile: .orangeTablet),
        ]])
        let matches = board.findMatches()
        #expect(matches.contains { $0.cells.count == 3 && $0.color == .orange })
    }

    @Test func bacteriaAtBottomOfColumnAutoMatches() {
        let board = makeBoard([
            [TestCell(tile: .pinkTablet)],
            [TestCell(tile: .pinkBacteria)],
        ])
        let matches = board.findMatches()
        #expect(matches.contains { $0.cause == .bottomRowBacteria && $0.cells == [GridPoint(row: 1, col: 0)] })
    }

    @Test func bacteriaAboveInactiveCellCountsAsColumnBottom() {
        let board = makeBoard([
            [TestCell(tile: .pinkBacteria)],
            [inactive()],
        ])
        let matches = board.findMatches()
        #expect(matches.contains { $0.cause == .bottomRowBacteria && $0.cells == [GridPoint(row: 0, col: 0)] })
    }

    @Test func bacteriaNotAtColumnBottomDoesNotAutoMatch() {
        let board = makeBoard([
            [TestCell(tile: .pinkBacteria)],
            [TestCell(tile: .orangeTablet)],
        ])
        #expect(!board.findMatches().contains { $0.cause == .bottomRowBacteria })
    }

    @Test func solidsNeverColorMatch() {
        let board = makeBoard([[
            TestCell(tile: .solidStage1), TestCell(tile: .solidStage1), TestCell(tile: .solidStage1),
        ]])
        #expect(board.findMatches().isEmpty)
    }

    // MARK: - Gravity: straight down

    @Test func tileFallsStraightDownIntoEmptyCellBelow() {
        let board = makeBoard([
            [TestCell(tile: .pinkTablet)],
            [TestCell()],
        ])
        let moves = board.computeFallMoves()
        #expect(moves == [FallMove(from: GridPoint(row: 0, col: 0), to: GridPoint(row: 1, col: 0), kind: .straightDown)])
    }

    @Test func tileDoesNotFallThroughItsOwnWallBelow() {
        var board = makeBoard([
            [TestCell(tile: .pinkTablet)],
            [TestCell()],
        ])
        board.mutate(at: GridPoint(row: 0, col: 0)) { $0.hasWallBelow = true }
        #expect(board.computeFallMoves().isEmpty)
    }

    @Test func fixedSolidAnchorNeverFalls() {
        let board = makeBoard([
            [TestCell(tile: .solidStage1)],
            [TestCell()],
        ])
        #expect(board.computeFallMoves().isEmpty)
    }

    // MARK: - Gravity: diagonal chasm

    @Test func tileSlidesDiagonallyWhenStraightDownBlockedButChasmIsOpen() {
        let board = makeBoard([
            [TestCell(tile: .pinkTablet), inactive(), inactive()],
            [inactive(), TestCell(), inactive()],
        ])
        let moves = board.computeFallMoves()
        #expect(moves == [FallMove(from: GridPoint(row: 0, col: 0), to: GridPoint(row: 1, col: 1), kind: .diagonalRight)])
    }

    @Test func deepChasmPocketAllowsDiagonalSlideWhenUnobstructed() {
        let board = makeBoard([
            [inactive(), inactive(), inactive()],
            [inactive(), TestCell(), inactive()],
            [TestCell(tile: .pinkTablet), TestCell(), inactive()],
            [inactive(), TestCell(), inactive()],
        ])
        let moves = board.computeFallMoves()
        #expect(moves.contains(FallMove(from: GridPoint(row: 2, col: 0), to: GridPoint(row: 3, col: 1), kind: .diagonalRight)))
    }

    @Test func restingTileInChasmPocketBlocksDiagonalSlide() {
        // The orangeTablet at (0,1) is itself free to fall straight down - that's a
        // separate, legitimate move. What this test actually pins down is that it
        // blocks the *pinkTablet's* diagonal slide into column 1 while it's resting
        // there, exactly as the original's `goodchasm` pocket-obstruction check does.
        let board = makeBoard([
            [inactive(), TestCell(tile: .orangeTablet), inactive()],
            [TestCell(tile: .pinkTablet), TestCell(), inactive()],
            [inactive(), TestCell(), inactive()],
        ])
        let moves = board.computeFallMoves()
        #expect(!moves.contains { $0.from == GridPoint(row: 1, col: 0) })
    }

    @Test func wallRightBlocksDiagonalSlideAcrossIt() {
        var board = makeBoard([
            [TestCell(tile: .pinkTablet), inactive()],
            [inactive(), TestCell()],
        ])
        board.mutate(at: GridPoint(row: 0, col: 0)) { $0.hasWallRight = true }
        #expect(board.computeFallMoves().isEmpty)
    }

    // MARK: - Legal move search

    @Test func firstLegalSwapFindsAMoveThatProducesAMatch() {
        // Swapping (0,1)<->(1,1) turns row 0 into [pink, pink, pink].
        let board = makeBoard([
            [TestCell(tile: .pinkTablet), TestCell(tile: .orangeTablet), TestCell(tile: .pinkTablet)],
            [TestCell(tile: .pinkTablet), TestCell(tile: .pinkTablet), TestCell(tile: .orangeTablet)],
        ])
        let found = board.firstLegalSwap()
        #expect(found != nil)
        #expect(board.legalSwapExists())
    }

    @Test func noLegalSwapWhenNoSwapCanProduceAMatch() {
        let board = makeBoard([[
            TestCell(tile: .pinkTablet), TestCell(tile: .orangeTablet), TestCell(tile: .yellowTablet),
        ]])
        #expect(board.firstLegalSwap() == nil)
        #expect(!board.legalSwapExists())
    }
}
