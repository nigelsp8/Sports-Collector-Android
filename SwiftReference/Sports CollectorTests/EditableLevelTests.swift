import Testing
@testable import Sports_Collector

struct EditableLevelTests {

    @Test func makeLevelIsIdentityForAnUnmodifiedLevel() throws {
        let original = try LevelLoader.loadLevel(resourceName: "1", level: 1)
        let roundTripped = EditableLevel(level: original).makeLevel()

        #expect(roundTripped.mapNumber == original.mapNumber)
        #expect(roundTripped.id == original.id)
        #expect(roundTripped.width == original.width)
        #expect(roundTripped.height == original.height)
        #expect(roundTripped.movesAllowed == original.movesAllowed)
        #expect(roundTripped.starThresholds == original.starThresholds)
        #expect(roundTripped.backgroundImageFilename == original.backgroundImageFilename)
        #expect(roundTripped.activeBlocks.count == original.activeBlocks.count)

        for row in 0..<original.height {
            for col in 0..<original.width {
                let point = GridPoint(row: row, col: col)
                let originalCell = original.cell(at: point)
                let roundTrippedCell = roundTripped.cell(at: point)
                #expect(roundTrippedCell.isActive == originalCell.isActive)
                #expect(roundTrippedCell.fixedTile == originalCell.fixedTile)
                #expect(roundTrippedCell.isSpawnPoint == originalCell.isSpawnPoint)
            }
        }
    }

    @Test func subscriptSetterOnlyChangesTheTargetedCell() throws {
        let level = try LevelLoader.loadLevel(resourceName: "1", level: 1)
        var editable = EditableLevel(level: level)

        let target = GridPoint(row: 0, col: 0)
        var spec = editable[target]
        spec.isActive = true
        spec.fixedTile = .solidStage1
        editable[target] = spec

        #expect(editable[target].isActive)
        #expect(editable[target].fixedTile == .solidStage1)

        // A neighboring cell must be untouched.
        let neighbor = GridPoint(row: 0, col: 1)
        #expect(editable[neighbor].isActive == level.cell(at: neighbor).isActive)
        #expect(editable[neighbor].fixedTile == level.cell(at: neighbor).fixedTile)
    }
}
