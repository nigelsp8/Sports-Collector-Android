import Foundation

/// Mutable mirror of `Level`/`LevelCellSpec` for the level editor. `Level` and
/// `LevelCellSpec` are immutable and shared with production game code, so the
/// editor gets its own copy rather than loosening those types.
struct EditableLevel {
    var mapNumber: Int
    var id: Int
    /// Fixed for the lifetime of an editor session - resizing a level's grid
    /// is out of scope (see project plan).
    let width: Int
    let height: Int
    var movesAllowed: Int
    var starThresholds: (one: Int, two: Int, three: Int)
    var backgroundImageFilename: String
    var cells: [EditableCellSpec]
    var activeBlocks: [TileType: ActiveBlockSpec]

    struct EditableCellSpec {
        var isActive: Bool
        var hasWallBelow: Bool
        var hasWallRight: Bool
        var isSpawnPoint: Bool
        var isExit: Bool
        var hasJelly: Bool
        var fixedTile: TileType?

        init(_ spec: LevelCellSpec) {
            isActive = spec.isActive
            hasWallBelow = spec.hasWallBelow
            hasWallRight = spec.hasWallRight
            isSpawnPoint = spec.isSpawnPoint
            isExit = spec.isExit
            hasJelly = spec.hasJelly
            fixedTile = spec.fixedTile
        }

        func makeSpec() -> LevelCellSpec {
            LevelCellSpec(
                isActive: isActive, hasWallBelow: hasWallBelow, hasWallRight: hasWallRight,
                isSpawnPoint: isSpawnPoint, isExit: isExit, hasJelly: hasJelly, fixedTile: fixedTile
            )
        }
    }

    init(level: Level) {
        mapNumber = level.mapNumber
        id = level.id
        width = level.width
        height = level.height
        movesAllowed = level.movesAllowed
        starThresholds = level.starThresholds
        backgroundImageFilename = level.backgroundImageFilename
        cells = level.cells.map(EditableCellSpec.init)
        activeBlocks = level.activeBlocks
    }

    func makeLevel() -> Level {
        Level(
            mapNumber: mapNumber, id: id, width: width, height: height,
            movesAllowed: movesAllowed, starThresholds: starThresholds,
            backgroundImageFilename: backgroundImageFilename,
            cells: cells.map { $0.makeSpec() }, activeBlocks: activeBlocks
        )
    }

    subscript(point: GridPoint) -> EditableCellSpec {
        get { cells[point.row * width + point.col] }
        set { cells[point.row * width + point.col] = newValue }
    }
}
