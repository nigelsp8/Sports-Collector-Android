import Foundation

/// Raw decode of the JSON string stored in `LevelFileEnvelope.levelXML`.
struct LevelPayload: Codable {
    let meta: LevelMeta
    let levelgrid: [String: LevelCellRaw]
    let activeblocks: [String: ActiveBlockRaw]
}

struct LevelMeta: Codable {
    let numberofmoves: Int
    let onestarscore: Int
    let twostarscore: Int
    let threestarscore: Int
    let levelgridsize: Int
    let backgroundimagefilename: String
}

/// Confirmed against all 8,100 cells across all 100 shipped levels: `background`,
/// `wallbelow`, `wallright`, and `exit` are omitted entirely whenever they'd be at
/// their default value (an encoder-side space-saving convention, not malformed
/// data) - `blockcontained`/`spawn`/`active` are always present. Decode leniently
/// rather than requiring every key.
struct LevelCellRaw: Codable {
    let background: Int
    let wallbelow: Bool
    let wallright: Bool
    let blockcontained: Int
    let spawn: Bool
    let active: Bool
    let exit: Bool

    private enum CodingKeys: String, CodingKey {
        case background, wallbelow, wallright, blockcontained, spawn, active, exit
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        background = try container.decodeIfPresent(Int.self, forKey: .background) ?? -1
        wallbelow = try container.decodeIfPresent(Bool.self, forKey: .wallbelow) ?? false
        wallright = try container.decodeIfPresent(Bool.self, forKey: .wallright) ?? false
        blockcontained = try container.decodeIfPresent(Int.self, forKey: .blockcontained) ?? -1
        spawn = try container.decodeIfPresent(Bool.self, forKey: .spawn) ?? false
        active = try container.decodeIfPresent(Bool.self, forKey: .active) ?? false
        exit = try container.decodeIfPresent(Bool.self, forKey: .exit) ?? false
    }

    /// Swift only synthesizes the memberwise initializer when no custom
    /// initializer is declared - `init(from:)` above suppresses it, so the
    /// level encoder (which has no `Decoder` to decode from) needs this.
    init(background: Int, wallbelow: Bool, wallright: Bool, blockcontained: Int, spawn: Bool, active: Bool, exit: Bool) {
        self.background = background
        self.wallbelow = wallbelow
        self.wallright = wallright
        self.blockcontained = blockcontained
        self.spawn = spawn
        self.active = active
        self.exit = exit
    }
}

struct ActiveBlockRaw: Codable {
    let blockstowin: Int
    let blockspawnpercentage: Int
}
