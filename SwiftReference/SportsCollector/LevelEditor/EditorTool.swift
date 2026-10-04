import Foundation

/// What a tap on an `EditorGridView` cell does. `.tile` additionally needs a
/// selected `TileType?` (nil = "clear fixed tile"), tracked separately by
/// `EditorPaletteView`.
enum EditorTool: Int, CaseIterable {
    case active
    case wallBelow
    case wallRight
    case spawn
    case exit
    case jelly
    case tile

    var title: String {
        switch self {
        case .active: return "Active"
        case .wallBelow: return "Wall Below"
        case .wallRight: return "Wall Right"
        case .spawn: return "Spawn"
        case .exit: return "Exit"
        case .jelly: return "Jelly"
        case .tile: return "Tile"
        }
    }
}
