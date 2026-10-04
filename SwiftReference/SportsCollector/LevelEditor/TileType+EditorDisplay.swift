import Foundation

/// UI-facing display names for the level editor's tile palette/objectives
/// list. Kept out of `GameEngine/TileType.swift` to avoid mixing UI concerns
/// into the engine model.
extension TileType {
    var editorDisplayName: String {
        switch self {
        case .pinkTablet: return "Pink Tablet"
        case .orangeTablet: return "Orange Tablet"
        case .yellowTablet: return "Yellow Tablet"
        case .greenTablet: return "Green Tablet"
        case .purpleTablet: return "Purple Tablet"
        case .blueTablet: return "Blue Tablet"
        case .orangeBluePill: return "Orange/Blue Pill"
        case .pinkGreenPill: return "Pink/Green Pill"
        case .purpleYellowPill: return "Purple/Yellow Pill"
        case .pinkBacteria: return "Pink Bacteria"
        case .orangeBacteria: return "Orange Bacteria"
        case .yellowBacteria: return "Yellow Bacteria"
        case .greenBacteria: return "Green Bacteria"
        case .purpleBacteria: return "Purple Bacteria"
        case .blueBacteria: return "Blue Bacteria"
        case .solidStage1: return "Solid (Stage 1)"
        case .solidStage2: return "Solid (Stage 2)"
        case .solidStage3: return "Solid (Stage 3)"
        }
    }
}
