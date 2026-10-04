import Foundation

enum LevelLoaderError: Error {
    case resourceNotFound
    case emptyEnvelope
}

enum LevelLoader {
    /// Production entry point: locates `<resourceName>.txt` in the app bundle.
    /// Falls back to a flat bundle-root lookup if the `Levels` subdirectory
    /// structure wasn't preserved by the resource-copy build phase.
    static func loadLevel(
        resourceName: String, level: Int, bundle: Bundle = .main, editStore: LevelEditStore = .shared
    ) throws -> Level {
        if let overrideData = editStore.overrideData(forResourceName: resourceName) {
            return try decodeLevel(from: overrideData, level: level)
        }
        let url = bundle.url(forResource: resourceName, withExtension: "txt", subdirectory: "Levels")
            ?? bundle.url(forResource: resourceName, withExtension: "txt")
        guard let url else { throw LevelLoaderError.resourceNotFound }
        let data = try Data(contentsOf: url)
        return try decodeLevel(from: data, level: level)
    }

    /// Pure decode entry point (no Bundle dependency) - used directly by tests.
    static func decodeLevel(from data: Data, level:Int) throws -> Level {
        let decoder = JSONDecoder()
        let envelopes = try decoder.decode([LevelFileEnvelope].self, from: data)
        guard let envelope = envelopes.first, let id = Int(envelope.levelID) else {
            throw LevelLoaderError.emptyEnvelope
        }
        let payload = try decoder.decode(LevelPayload.self, from: Data(envelope.levelXML.utf8))

        let width = payload.meta.levelgridsize
        let height = payload.meta.levelgridsize
        var cells = [LevelCellSpec](repeating: .inactive, count: width * height)
        for (key, raw) in payload.levelgrid {
            guard let flatIndex = Int(key), cells.indices.contains(flatIndex) else { continue }
            // The JSON key is column-major (row = flatIndex % width, col = flatIndex
            // / width) - confirmed empirically against all 100 shipped levels: under
            // that convention, spawn points land one-per-column at the topmost active
            // row of that column in 678 of 688 cases (the reverse convention doesn't
            // reproduce that pattern at all). Remap into standard row-major storage
            // here, once, so every downstream consumer (Board, GameEngine, BoardNode)
            // can use plain row-major indexing without re-deriving this.
            let row = flatIndex % width
            let col = flatIndex / width
            let storageIndex = row * width + col
            cells[storageIndex] = LevelCellSpec(
                isActive: raw.active,
                hasWallBelow: raw.wallbelow,
                hasWallRight: raw.wallright,
                isSpawnPoint: raw.spawn,
                isExit: raw.exit,
                hasJelly: raw.background != -1,
                fixedTile: TileType(blockContained: raw.blockcontained)
            )
        }

        var activeBlocks: [TileType: ActiveBlockSpec] = [:]
        for (key, raw) in payload.activeblocks {
            guard let index = Int(key), let type = TileType(blockContained: index) else { continue }
            activeBlocks[type] = ActiveBlockSpec(blocksToWin: raw.blockstowin, spawnPercentage: raw.blockspawnpercentage)
        }

        return Level(
            mapNumber: level,
            id: id,
            width: width,
            height: height,
            movesAllowed: payload.meta.numberofmoves,
            starThresholds: StarThreshold.resolve(meta: payload.meta),
            backgroundImageFilename: payload.meta.backgroundimagefilename,
            cells: cells,
            activeBlocks: activeBlocks
        )
    }
}
