import Foundation

/// Encodes a `Level` back into the same envelope/payload JSON shape
/// `LevelLoader` decodes. No such encoder existed before the level editor -
/// this is the inverse of `LevelLoader.decodeLevel`.
enum LevelEncoder {
    enum EncodingError: Error {
        case nonUTF8Payload
    }

    /// Precondition: `level.width == level.height` (true of every shipped
    /// level; this encoder doesn't support non-square grids, matching the
    /// loader's own `levelgridsize` assumption).
    static func encode(_ level: Level) throws -> Data {
        var levelgrid: [String: LevelCellRaw] = [:]
        for row in 0..<level.height {
            for col in 0..<level.width {
                let spec = level.cell(at: GridPoint(row: row, col: col))
                // Inverse of LevelLoader's `row = flatIndex % width, col = flatIndex / width`.
                let flatIndex = col * level.width + row
                levelgrid[String(flatIndex)] = LevelCellRaw(
                    background: spec.hasJelly ? 0 : -1,
                    wallbelow: spec.hasWallBelow,
                    wallright: spec.hasWallRight,
                    blockcontained: spec.fixedTile.map { $0.rawValue - 1 } ?? -1,
                    spawn: spec.isSpawnPoint,
                    active: spec.isActive,
                    exit: spec.isExit
                )
            }
        }

        var activeblocks: [String: ActiveBlockRaw] = [:]
        for (type, spec) in level.activeBlocks {
            activeblocks[String(type.rawValue - 1)] = ActiveBlockRaw(
                blockstowin: spec.blocksToWin,
                blockspawnpercentage: spec.spawnPercentage
            )
        }

        let meta = LevelMeta(
            numberofmoves: level.movesAllowed,
            onestarscore: level.starThresholds.one,
            twostarscore: level.starThresholds.two,
            threestarscore: level.starThresholds.three,
            levelgridsize: level.width,
            backgroundimagefilename: level.backgroundImageFilename
        )
        let payload = LevelPayload(meta: meta, levelgrid: levelgrid, activeblocks: activeblocks)
        let payloadData = try JSONEncoder().encode(payload)
        guard let payloadString = String(data: payloadData, encoding: .utf8) else {
            throw EncodingError.nonUTF8Payload
        }

        let envelope = LevelFileEnvelope(levelID: String(level.id), levelXML: payloadString)
        return try JSONEncoder().encode([envelope])
    }
}
