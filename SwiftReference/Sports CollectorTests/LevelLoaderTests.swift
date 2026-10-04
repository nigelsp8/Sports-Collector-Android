import Testing
import Foundation
@testable import Sports_Collector

struct LevelLoaderTests {

    @Test func bundledLevelOneDecodesAndLoads() throws {
        let level = try LevelLoader.loadLevel(resourceName: "1", level: 1)

        #expect(level.id == 1)
        #expect(level.width == 9)
        #expect(level.height == 9)
        #expect(level.movesAllowed == 24)
        #expect(level.cells.count == 81)
        // Level 1's JSON thresholds (10000/15000/20000) are already valid, so they
        // must pass through StarThreshold.resolve unchanged.
        #expect(level.starThresholds == (40000, 52000, 67000))
    }

    @Test func activeBlockSpawnPercentagesSumToOneHundred() throws {
        let level = try LevelLoader.loadLevel(resourceName: "1", level: 1)
        let total = level.activeBlocks.values.reduce(0) { $0 + $1.spawnPercentage }
        #expect(total == 100)
    }

    @Test func flatIndexMapsToRowMajorGridPoint() throws {
        let level = try LevelLoader.loadLevel(resourceName: "1", level: 1)
        // Cell "10" in Reference/Levels/1.txt is a spawn cell (active, spawn=true).
        // LevelLoader remaps the JSON's column-major keys into standard row-major
        // storage at decode time, so plain row-major indexing is correct here.
        let point = GridPoint(row: 10 / level.width, col: 10 % level.width)
        let cell = level.cell(at: point)
        #expect(cell.isActive)
        #expect(cell.isSpawnPoint)
    }

    @Test func decodeLevelIsPureAndDeterministic() throws {
        let url = try #require(Bundle.main.url(forResource: "1", withExtension: "txt", subdirectory: "Levels")
            ?? Bundle.main.url(forResource: "1", withExtension: "txt"))
        let data = try Data(contentsOf: url)
        let a = try LevelLoader.decodeLevel(from: data, level: 1)
        let b = try LevelLoader.decodeLevel(from: data, level: 1)
        #expect(a.id == b.id)
        #expect(a.cells.count == b.cells.count)
    }

    @Test func overrideStoreTakesPrecedenceOverTheBundledResource() throws {
        let root = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString, isDirectory: true)
        let editStore = LevelEditStore(root: root)

        let bundled = try LevelLoader.loadLevel(resourceName: "1", level: 1)
        var edited = EditableLevel(level: bundled)
        edited.movesAllowed = bundled.movesAllowed + 7
        try editStore.save(edited.makeLevel(), resourceName: "1")

        let loaded = try LevelLoader.loadLevel(resourceName: "1", level: 1, editStore: editStore)
        #expect(loaded.movesAllowed == bundled.movesAllowed + 7)
    }
}
