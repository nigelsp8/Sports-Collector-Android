import Testing
@testable import Sports_Collector

/// Regression coverage for a transposition bug: population-count-style checks
/// (every active cell has a tile, spawn percentages sum to 100, etc.) are
/// invariant under any bijective relabeling of (row, col), so they can't catch
/// a swapped row/col convention. These tests instead pin specific JSON keys from
/// Reference/Levels/1.txt to their manually cross-referenced (row, col) and flag
/// values, and check the level's spawn row forms the expected *shape*.
@Suite(.serialized)
struct LevelGeometryTests {

    @Test func specificRawCellsLandAtTheirExpectedGridPoint() throws {
        let level = try LevelLoader.loadLevel(resourceName: "1", level: 1)

        // Key "10": active:true, spawn:true, exit:false, background:-1.
        // width=9 -> row = 10 % 9 = 1, col = 10 / 9 = 1.
        let cellAt10 = level.cell(at: GridPoint(row: 1, col: 1))
        #expect(cellAt10.isActive)
        #expect(cellAt10.isSpawnPoint)
        #expect(!cellAt10.hasJelly)

        // Key "61": active:true, spawn:false, exit:true. row=61%9=7, col=61/9=6.
        let cellAt61 = level.cell(at: GridPoint(row: 7, col: 6))
        #expect(cellAt61.isActive)
        #expect(cellAt61.isExit)

        // Key "0": active:false, background:0 (jelly). row=0%9=0, col=0/9=0.
        let cellAt0 = level.cell(at: GridPoint(row: 0, col: 0))
        #expect(!cellAt0.isActive)
        #expect(cellAt0.hasJelly) // background 0 != -1

        // Key "54": active:false, background:0 (jelly). row=54%9=0, col=54/9=6.
        let cellAt54 = level.cell(at: GridPoint(row: 0, col: 6))
        #expect(!cellAt54.isActive)
        #expect(cellAt54.hasJelly)
    }

    @Test func spawnRowFormsTheExpectedHorizontalShape() throws {
        let level = try LevelLoader.loadLevel(resourceName: "1", level: 1)
        // Confirmed via direct analysis of Reference/Levels/1.txt: every spawn
        // point sits in row 1, at columns 1 through 7 - one per column, at the
        // top of each usable column (matching the original design). A row/col
        // transposition would turn this into column 1, rows 1-7 instead, which
        // this test would catch.
        var spawnPoints: [GridPoint] = []
        for row in 0..<level.height {
            for col in 0..<level.width {
                if level.cell(at: GridPoint(row: row, col: col)).isSpawnPoint {
                    spawnPoints.append(GridPoint(row: row, col: col))
                }
            }
        }
        let rows = Set(spawnPoints.map(\.row))
        let columns = Set(spawnPoints.map(\.col))
        #expect(rows == [1], "expected all spawn points confined to row 1, got rows \(rows)")
        #expect(columns == Set(1...7), "expected spawn points at columns 1-7, got \(columns)")
    }
}
