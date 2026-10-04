import Foundation

/// A tile's content type. Raw values match the original game's `blockcontained + 1`
/// encoding from level JSON, so `TileType(blockContained:)` is a direct decode.
enum TileType: Int, Codable, CaseIterable {
    case pinkTablet = 1
    case orangeTablet
    case yellowTablet
    case greenTablet
    case purpleTablet
    case blueTablet

    case orangeBluePill = 7
    case pinkGreenPill
    case purpleYellowPill

    case pinkBacteria = 10
    case orangeBacteria
    case yellowBacteria
    case greenBacteria
    case purpleBacteria
    case blueBacteria

    case solidStage1 = 16
    case solidStage2
    case solidStage3

    /// `blockContained` is the level JSON's 0-based type index, or -1 for "no fixed content".
    init?(blockContained: Int) {
        guard blockContained >= 0 else { return nil }
        self.init(rawValue: blockContained + 1)
    }

    /// The color bit this tile matches against. Combo pills carry exactly one of
    /// their two colors; bacteria and solids never color-match.
    var colorBit: TileColor? {
        switch self {
        case .pinkTablet, .pinkGreenPill: return .pink
        case .orangeTablet, .orangeBluePill: return .orange
        case .yellowTablet: return .yellow
        case .greenTablet: return .green
        case .purpleTablet, .purpleYellowPill: return .purple
        case .blueTablet: return .blue
        case .pinkBacteria, .orangeBacteria, .yellowBacteria, .greenBacteria, .purpleBacteria, .blueBacteria,
             .solidStage1, .solidStage2, .solidStage3:
            return nil
        }
    }

    var isBacteria: Bool {
        switch self {
        case .pinkBacteria, .orangeBacteria, .yellowBacteria, .greenBacteria, .purpleBacteria, .blueBacteria:
            return true
        default:
            return false
        }
    }

    var isSolid: Bool {
        switch self {
        case .solidStage1, .solidStage2, .solidStage3: return true
        default: return false
        }
    }

    var isComboPill: Bool {
        switch self {
        case .orangeBluePill, .pinkGreenPill, .purpleYellowPill: return true
        default: return false
        }
    }

    /// The plain tablet a combo pill downgrades to when matched. Downgrading
    /// replaces the tile in place rather than removing it.
    var downgradedForm: TileType? {
        switch self {
        case .orangeBluePill: return .orangeTablet
        case .pinkGreenPill: return .pinkTablet
        case .purpleYellowPill: return .purpleTablet
        default: return nil
        }
    }

    /// The next damage stage for a solid blocker. `nil` for `.solidStage3` means
    /// the solid is fully destroyed (cell clears) rather than advancing further.
    var nextDamageStage: TileType? {
        switch self {
        case .solidStage1: return .solidStage2
        case .solidStage2: return .solidStage3
        default: return nil
        }
    }
}
