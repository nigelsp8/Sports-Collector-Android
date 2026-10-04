import Foundation
@testable import Sports_Collector

/// Deterministic xorshift64 RNG so engine tests are reproducible.
struct SeededGenerator: RandomNumberGenerator {
    private var state: UInt64
    init(seed: UInt64) { state = seed == 0 ? 0xdead_beef : seed }
    mutating func next() -> UInt64 {
        state ^= state << 13
        state ^= state >> 7
        state ^= state << 17
        return state
    }
}

struct SeededTileRandomSource: TileRandomSource {
    private var generator: SeededGenerator
    init(seed: UInt64) { generator = SeededGenerator(seed: seed) }

    mutating func nextSpawnTile(from distribution: [(TileType, Int)]) -> TileType {
        SystemTileRandomSource.pick(from: distribution) { Int.random(in: 0..<$0, using: &generator) }
    }

    mutating func shuffled<T>(_ items: [T]) -> [T] {
        items.shuffled(using: &generator)
    }
}

/// Builds a minimal, fully-controlled `Level` for engine tests, bypassing the
/// JSON decode pipeline entirely.
enum TestLevel {
    struct Spec {
        var tile: TileType?
        var isActive = true
        var hasWallBelow = false
        var hasWallRight = false
        var isSpawnPoint = false
        var hasJelly = false
    }

    static func make(
        id: Int = 99,
        rows: [[Spec]],
        movesAllowed: Int = 10,
        activeBlocks: [TileType: ActiveBlockSpec] = [:],
        starThresholds: (one: Int, two: Int, three: Int) = (100, 200, 300)
    ) -> Level {
        let height = rows.count
        let width = rows.first?.count ?? 0

        // Row-major (index = row * width + col), matching `Board.init`'s own
        // decode convention (row = index / width, col = index % width) directly -
        // this builder constructs a `Level` in memory and never goes through the
        // real JSON loader, so it has no need to replicate that loader's
        // column-major on-disk quirk (see `LevelLoader.decodeLevel`).
        let cells = rows.flatMap { rowSpecs in
            rowSpecs.map { spec in
                LevelCellSpec(
                    isActive: spec.isActive,
                    hasWallBelow: spec.hasWallBelow,
                    hasWallRight: spec.hasWallRight,
                    isSpawnPoint: spec.isSpawnPoint,
                    isExit: false,
                    hasJelly: spec.hasJelly,
                    fixedTile: spec.tile
                )
            }
        }
        return Level(
            mapNumber: id, id: id, width: width, height: height, movesAllowed: movesAllowed,
            starThresholds: starThresholds, backgroundImageFilename: "",
            cells: cells, activeBlocks: activeBlocks
        )
    }
}
