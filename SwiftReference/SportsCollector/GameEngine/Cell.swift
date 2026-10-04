import Foundation

struct Cell {
    let point: GridPoint
    let isActive: Bool
    var hasWallBelow: Bool
    var hasWallRight: Bool
    let isSpawnPoint: Bool
    var hasJelly: Bool
    var tile: TileType?

    /// True if this cell is a level-authored, permanent solid spawn point (the
    /// original's `settype == SOLID1`). Unlike the original, gravity does NOT
    /// consult this flag - only whether the cell's *current* tile is solid
    /// (see `Board.computeFallMoves`/`isFloorBoundary`), so once the solid
    /// occupying this cell is fully destroyed, the cell falls/refills like any
    /// other open cell (an intentional deviation from the original, where
    /// `settype == SOLID1` permanently jams gravity in that cell forever - see
    /// project memory for why). This flag still exists, and is still permanent,
    /// for the shuffle system: the shuffle-on-stuck relocation pool always
    /// excludes cells that were ever a solid spawn point.
    let isFixedSolidAnchor: Bool
}
