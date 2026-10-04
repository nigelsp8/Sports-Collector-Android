import Foundation

/// A single grid cell's level-authored, unchanging configuration (as opposed to
/// `Cell`, which is the live in-engine board state derived from this spec).
struct LevelCellSpec {
    let isActive: Bool
    let hasWallBelow: Bool
    let hasWallRight: Bool
    let isSpawnPoint: Bool
    /// Decoded for fidelity with the original level data. Confirmed to have no
    /// gameplay effect anywhere in the original game - kept as inert data.
    let isExit: Bool
    let hasJelly: Bool
    let fixedTile: TileType?

    static let inactive = LevelCellSpec(
        isActive: false, hasWallBelow: false, hasWallRight: false,
        isSpawnPoint: false, isExit: false, hasJelly: false, fixedTile: nil
    )
}

struct ActiveBlockSpec {
    let blocksToWin: Int
    let spawnPercentage: Int
}

/// Normalized, engine-facing level definition produced by `LevelLoader`.
struct Level {
    let mapNumber: Int
    let id: Int
    let width: Int
    let height: Int
    let movesAllowed: Int
    let starThresholds: (one: Int, two: Int, three: Int)
    let backgroundImageFilename: String
    /// Flat, row-major: index = row * width + col. `LevelLoader` remaps the JSON's
    /// own column-major cell keys into this standard layout once, at decode time,
    /// so everything downstream can use plain row-major indexing.
    let cells: [LevelCellSpec]
    let activeBlocks: [TileType: ActiveBlockSpec]

    func cell(at point: GridPoint) -> LevelCellSpec {
        cells[point.row * width + point.col]
    }
}
