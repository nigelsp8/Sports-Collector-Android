import Foundation

/// Bitmask of the six tablet colors. A tile "matches" another tile when their
/// color bits intersect, which is what lets a combo pill (carrying one color bit)
/// match plain tablets of that color.
struct TileColor: OptionSet, Codable, Hashable {
    let rawValue: Int

    static let pink   = TileColor(rawValue: 1 << 0)
    static let orange = TileColor(rawValue: 1 << 1)
    static let yellow = TileColor(rawValue: 1 << 2)
    static let green  = TileColor(rawValue: 1 << 3)
    static let purple = TileColor(rawValue: 1 << 4)
    static let blue   = TileColor(rawValue: 1 << 5)
}
