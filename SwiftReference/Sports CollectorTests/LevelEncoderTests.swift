import Testing
@testable import Sports_Collector

/// Round-trips every bundled level through `LevelEncoder` and back. Asserts
/// pinned-cell/shape facts rather than bulk equality - the established pattern
/// in `LevelGeometryTests`, specifically because aggregate checks (cell count,
/// active-cell count, etc.) are blind to the row/col transposition bug class
/// this encoder's column-major key inversion risks reintroducing.
@Suite(.serialized)
struct LevelEncoderTests {

    private static let allLevelIDs = Array(1...100)

    @Test(arguments: allLevelIDs)
    func roundTripPreservesEveryCellAndObjective(id: Int) throws {
        let original = try LevelLoader.loadLevel(resourceName: "\(id)", level: id)

        let data = try LevelEncoder.encode(original)
        let roundTripped = try LevelLoader.decodeLevel(from: data, level: id)

        #expect(roundTripped.width == original.width)
        #expect(roundTripped.height == original.height)
        #expect(roundTripped.movesAllowed == original.movesAllowed)
        #expect(roundTripped.starThresholds == original.starThresholds)
        #expect(roundTripped.backgroundImageFilename == original.backgroundImageFilename)
        #expect(roundTripped.activeBlocks.count == original.activeBlocks.count)
        for (type, spec) in original.activeBlocks {
            let roundTrippedSpec = roundTripped.activeBlocks[type]
            #expect(roundTrippedSpec?.blocksToWin == spec.blocksToWin)
            #expect(roundTrippedSpec?.spawnPercentage == spec.spawnPercentage)
        }

        for row in 0..<original.height {
            for col in 0..<original.width {
                let point = GridPoint(row: row, col: col)
                let originalCell = original.cell(at: point)
                let roundTrippedCell = roundTripped.cell(at: point)
                #expect(roundTrippedCell.isActive == originalCell.isActive, "level \(id) \(point)")
                #expect(roundTrippedCell.hasWallBelow == originalCell.hasWallBelow, "level \(id) \(point)")
                #expect(roundTrippedCell.hasWallRight == originalCell.hasWallRight, "level \(id) \(point)")
                #expect(roundTrippedCell.isSpawnPoint == originalCell.isSpawnPoint, "level \(id) \(point)")
                #expect(roundTrippedCell.isExit == originalCell.isExit, "level \(id) \(point)")
                #expect(roundTrippedCell.hasJelly == originalCell.hasJelly, "level \(id) \(point)")
                #expect(roundTrippedCell.fixedTile == originalCell.fixedTile, "level \(id) \(point)")
            }
        }
    }

    @Test func specificRawCellsStillLandAtTheirExpectedGridPointAfterRoundTrip() throws {
        // Same pinned cells as LevelGeometryTests.specificRawCellsLandAtTheirExpectedGridPoint,
        // re-checked after an encode/decode round trip to catch the encoder
        // reintroducing a row/col transposition.
        let level = try LevelLoader.loadLevel(resourceName: "1", level: 1)
        let data = try LevelEncoder.encode(level)
        let roundTripped = try LevelLoader.decodeLevel(from: data, level: 1)

        let cellAt10 = roundTripped.cell(at: GridPoint(row: 1, col: 1))
        #expect(cellAt10.isActive)
        #expect(cellAt10.isSpawnPoint)

        let cellAt61 = roundTripped.cell(at: GridPoint(row: 7, col: 6))
        #expect(cellAt61.isActive)
        #expect(cellAt61.isExit)
    }
}
