import SpriteKit

/// Slices `grid1tiles.png` (an 8x8 atlas of 128pt frames) and maps a cell's
/// neighbor-presence bitmask to the matching frame, porting `drawGridFrame`'s
/// autotile lookup table from the original (`Reference/itag/DrPopperViewController.m`).
enum GridFrameAtlas {
    private static let columns = 8
    private static let rows = 8
    private static let cellFraction: CGFloat = 1.0 / CGFloat(columns)
    private static let baseTexture = SKTexture(imageNamed: "grid1tiles")

    /// Bits: 1/2/4/8 = left/right/up/down neighbor missing, 16/32/64/128 =
    /// diagonal-only touch at top-left/top-right/bottom-left/bottom-right,
    /// 256/257 = fully-interior checkerboard (alternates by cell parity). Any
    /// bitmask not covered here never occurs for a valid level shape, exactly
    /// as in the original (which logs a warning for the same case).
    private static let frameForBitmask: [Int: Int] = [
        1: 8, 2: 10, 4: 1, 8: 17,
        5: 11, 6: 12, 7: 28, 11: 27, 12: 22, 13: 30, 14: 29, 15: 19,
        16: 0, 32: 2, 48: 20,
        64: 16, 128: 18,
        240: 31,
        256: 32, 257: 33,
        3: 23,
        9: 3, 10: 4,
        192: 26,
        80: 34, 160: 24,
        70: 35, 41: 36,
        26: 37, 133: 38,
        18: 40, 33: 41,
        208: 15, 224: 21,
        68: 42, 132: 43,
    ]

    static func texture(forBitmask bitmask: Int) -> SKTexture? {
        guard let frame = frameForBitmask[bitmask] else { return nil }
        let col = frame % columns
        // The original's UV math counts rows top-down; SpriteKit's texture
        // rect space has its origin at the bottom-left, so flip here.
        let topRow = frame / columns
        let bottomRow = rows - 1 - topRow
        let rect = CGRect(
            x: CGFloat(col) * cellFraction,
            y: CGFloat(bottomRow) * cellFraction,
            width: cellFraction,
            height: cellFraction
        )
        return SKTexture(rect: rect, in: baseTexture)
    }
}
