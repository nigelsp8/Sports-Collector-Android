import SpriteKit

/// A tile sprite that remembers its own `TileType`, so `BoardNode` can tell
/// which nodes are bacteria (the only tile type that continuously wobbles -
/// see `BoardNode.updateBacteriaWobble(elapsedTime:)`) without a separate,
/// harder-to-keep-in-sync lookup table.
private final class TileSpriteNode: SKSpriteNode {
    var tileType: TileType?

    /// Per-instance wobble tuning so multiple bacteria on screen don't all
    /// animate in perfect lockstep. Randomized once when the sprite is
    /// created and stable for its lifetime - nodes persist across moves and
    /// swaps (see `BoardNode.swapTileNodes`/`moveTile`), so this won't reset
    /// every time a tile shifts to a new grid cell.
    let wobblePhase = Double.random(in: 0..<(2 * .pi))
    let wobbleSpeedScale = Double.random(in: 0.8...1.25)
    let wobbleAmplitudeScale = CGFloat.random(in: 0.75...1.3)
}

/// Renders the board's static layout (cell backgrounds, walls, jelly overlays)
/// and manages tile sprite nodes, keyed by grid position. Pure presentation - it
/// has no game-rule knowledge; `GameScene` tells it what happened via discrete
/// calls (`placeTile`, `moveTile`, `removeTile`, ...) driven by `GameEvent`s.
final class BoardNode: SKNode {
    /// Shared key for every positional animation on a tile node. A fast cascade
    /// can issue several `moveTile`/`swapTileNodes` calls on the same node before
    /// its previous `.move` finishes; `run(_:withKey:)` atomically replaces any
    /// action already running under this key instead of letting them stack up
    /// and fight over `position` each frame (the actual cause of tiles visually
    /// "sticking" near the spawn row at full speed - the model was always
    /// correct, only the on-screen animation was racing itself).
    private static let moveActionKey = "tileMove"
    /// Shared key for every scale/fade animation on a tile node (pulse, removal),
    /// for the same reason as `moveActionKey` above but for the `scale`/`alpha`
    /// properties instead of `position`.
    private static let scaleActionKey = "tileScale"

    /// Base duration of the swap-over animation in `swapTileNodes`, before
    /// `animationSpeedMultiplier` is applied. `GameScene` mirrors this into
    /// `GameEngine.swapAnimationDuration` so the engine's `.swapping` phase
    /// holds match resolution/gravity for exactly as long as the two tiles
    /// are visually still animating into each other's slot.
    static let swapDuration: TimeInterval = 0.15

    /// Every freshly-created tile sprite (initial board fill, spawn-row
    /// refills, post-shuffle resync) fades in from transparent instead of
    /// popping in instantly. Shares `scaleActionKey` with `removeTile`/`pulse`
    /// so an immediate removal (e.g. a match resolved the same step a spawn
    /// filled its cell) cleanly overrides the fade-in instead of fighting it
    /// for the node's `alpha`.
    private static let spawnFadeDuration: TimeInterval = 0.25
    /// Duration of a spawn-row tile's drop-in from above the board, eased in
    /// (starts slow, speeds up) to read as gravity taking hold rather than a
    /// constant-speed slide - slower and more weighted than the old flat
    /// 0.25s linear move.
    private static let fallInDuration: TimeInterval = 0.35
    /// Durations for a shuffled tile's scale-down-and-fade-out before its
    /// texture swaps to the post-shuffle tile, and its scale-up-and-fade-in
    /// after - see `reshuffleTile`. Quicker than `spawnFadeDuration` on the
    /// way out so a board-wide, staggered chain of these reads as a brisk
    /// ripple rather than a slow crossfade.
    private static let reshuffleOutDuration: TimeInterval = 0.12
    private static let reshuffleInDuration: TimeInterval = 0.22

    let level: Level
    private(set) var cellSize: CGFloat = 0

    /// Debug aid: scales every tile animation's duration so motion can be
    /// watched frame-by-frame. 1.0 (default) is normal speed.
    var animationSpeedMultiplier: TimeInterval = 1.0

    private let textures: TileTextureProvider
    private let boardLayer = SKNode()
    private let jellyLayer = SKNode()
    private let wallLayer = SKNode()
    private let tileLayer = SKNode()
    private let selectionLayer = SKNode()

    /// Persistent highlight ring shown around the currently-selected tile -
    /// distinct from the transient `pulse` bounce (still used for errant-swap/
    /// hint feedback) so a tap's selection reads as a held state rather than
    /// a one-shot animation. Repositioned/shown-hidden, never recreated,
    /// since only one tile can be selected at a time.
    private let selectionHighlight: SKShapeNode = {
        let node = SKShapeNode()
        node.strokeColor = .white
        node.lineWidth = 4
        node.fillColor = .clear
        node.isHidden = true
        node.zPosition = 10
        return node
    }()

    private var tileNodes: [GridPoint: TileSpriteNode] = [:]
    private var jellyNodes: [GridPoint: SKSpriteNode] = [:]
    private var wallNodes: [String: SKSpriteNode] = [:]
    /// Each active cell's own `grid1tiles.png` checkerboard background node
    /// (as opposed to the border/edge-frame tiles, which never sit under
    /// jelly). Hidden for the duration a cell holds jelly - see `addJelly`/
    /// `clearJelly` - so the jelly overlay reads as fully opaque ground
    /// rather than a tint over the checkerboard showing through it.
    private var gridTileNodes: [GridPoint: SKSpriteNode] = [:]

    init(level: Level, textures: TileTextureProvider) {
        self.level = level
        self.textures = textures
        super.init()
        addChild(boardLayer)
        addChild(jellyLayer)
        addChild(wallLayer)
        addChild(tileLayer)
        selectionLayer.addChild(selectionHighlight)
        addChild(selectionLayer)
    }

    required init?(coder: NSCoder) {
        fatalError("BoardNode does not support storyboard instantiation")
    }

    /// (Re)computes cell size to fit `size` and rebuilds the static layer.
    /// Existing tile nodes are repositioned, not recreated.
    func layout(in size: CGSize) {
        cellSize = min(size.width / CGFloat(level.width), size.height / CGFloat(level.height)) * 0.92
        let highlightSize = cellSize * 0.92
        selectionHighlight.path = CGPath(
            roundedRect: CGRect(x: -highlightSize / 2, y: -highlightSize / 2, width: highlightSize, height: highlightSize),
            cornerWidth: highlightSize * 0.18, cornerHeight: highlightSize * 0.18, transform: nil)

        boardLayer.removeAllChildren()
        gridTileNodes.removeAll()
        jellyLayer.removeAllChildren()
        wallLayer.removeAllChildren()
        wallNodes.removeAll()
        let previousJellyPoints = Set(jellyNodes.keys)
        jellyNodes.removeAll()

        buildStaticLayer(restoringJellyAt: previousJellyPoints)

        for (gp, node) in tileNodes {
            node.position = point(for: gp)
            node.size = CGSize(width: cellSize * 0.88, height: cellSize * 0.88)
        }
    }

    /// Grid -> scene point. Row increases downward (matches the level data's
    /// convention); SpriteKit's y axis points up, so this flips it.
    func point(for grid: GridPoint) -> CGPoint {
        let x = (CGFloat(grid.col) - CGFloat(level.width - 1) / 2) * cellSize
        let y = (CGFloat(level.height - 1) / 2 - CGFloat(grid.row)) * cellSize
        return CGPoint(x: x, y: y)
    }

    /// Scene point -> grid point, or nil if it doesn't land within a cell's footprint.
    func gridPoint(at scenePoint: CGPoint) -> GridPoint? {
        guard cellSize > 0 else { return nil }
        let colF = scenePoint.x / cellSize + CGFloat(level.width - 1) / 2
        let rowF = CGFloat(level.height - 1) / 2 - scenePoint.y / cellSize
        let candidate = GridPoint(row: Int(rowF.rounded()), col: Int(colF.rounded()))
        guard candidate.row >= 0, candidate.row < level.height, candidate.col >= 0, candidate.col < level.width else { return nil }
        let center = point(for: candidate)
        guard abs(scenePoint.x - center.x) <= cellSize / 2, abs(scenePoint.y - center.y) <= cellSize / 2 else { return nil }
        return candidate
    }

    var boardPixelSize: CGSize {
        CGSize(width: CGFloat(level.width) * cellSize, height: CGFloat(level.height) * cellSize)
    }

    private func buildStaticLayer(restoringJellyAt jellyPoints: Set<GridPoint>) {
        buildGridFrame()

        for (index, spec) in level.cells.enumerated() {
            guard spec.isActive else { continue }
            let gp = GridPoint(row: index / level.width, col: index % level.width)

            if spec.hasJelly, jellyPoints.contains(gp) || jellyPoints.isEmpty {
                addJelly(at: gp)
            }
            if spec.hasWallRight { addWall(at: gp, horizontal: false) }
            if spec.hasWallBelow { addWall(at: gp, horizontal: true) }
        }
    }

    private func isActive(_ row: Int, _ col: Int) -> Bool {
        guard row >= 0, row < level.height, col >= 0, col < level.width else { return false }
        return level.cell(at: GridPoint(row: row, col: col)).isActive
    }

    /// Ports `drawGridFrame`/`setupGrid`'s autotile technique: every active
    /// cell gets an alternating checkerboard tile, and every cell bordering
    /// the play area gets whichever edge/corner tile matches which sides
    /// face active cells - so the level's footprint reads as one framed
    /// panel instead of a plain rectangle. Bitmasks are accumulated on a grid
    /// padded by one cell on every side (a border cell can be touched by up
    /// to four different active neighbors).
    private func buildGridFrame() {
        let paddedRows = level.height + 2
        let paddedCols = level.width + 2
        var bits = Array(repeating: Array(repeating: 0, count: paddedCols), count: paddedRows)

        for row in 0..<level.height {
            for col in 0..<level.width {
                guard isActive(row, col) else { continue }
                let leftMissing = !isActive(row, col - 1)
                let rightMissing = !isActive(row, col + 1)
                let upMissing = !isActive(row - 1, col)
                let downMissing = !isActive(row + 1, col)
                let tl = !isActive(row - 1, col - 1)
                let tr = !isActive(row - 1, col + 1)
                let bl = !isActive(row + 1, col - 1)
                let br = !isActive(row + 1, col + 1)

                let fx = col + 1
                let fy = row + 1

                if leftMissing  { bits[fy][fx - 1] |= 1 }
                if rightMissing { bits[fy][fx + 1] |= 2 }
                if upMissing    { bits[fy - 1][fx] |= 4 }
                if downMissing  { bits[fy + 1][fx] |= 8 }

                if tl && leftMissing && upMissing    { bits[fy - 1][fx - 1] |= 16 }
                if tr && rightMissing && upMissing   { bits[fy - 1][fx + 1] |= 32 }
                if bl && leftMissing && downMissing  { bits[fy + 1][fx - 1] |= 64 }
                if br && rightMissing && downMissing { bits[fy + 1][fx + 1] |= 128 }
            }
        }

        for row in 0..<level.height {
            for col in 0..<level.width {
                guard isActive(row, col) else { continue }
                bits[row + 1][col + 1] = ((row + col) & 1 != 0) ? 256 : 257
            }
        }

        for fy in 0..<paddedRows {
            for fx in 0..<paddedCols {
                let bitmask = bits[fy][fx]
                guard bitmask != 0, let texture = GridFrameAtlas.texture(forBitmask: bitmask) else { continue }
                let node = SKSpriteNode(texture: texture)
                node.size = CGSize(width: cellSize, height: cellSize)
                let gp = GridPoint(row: fy - 1, col: fx - 1)
                node.position = point(for: gp)
                boardLayer.addChild(node)
                if bitmask == 256 || bitmask == 257 {
                    gridTileNodes[gp] = node
                }
            }
        }
    }

    private func addJelly(at gp: GridPoint) {
        let jelly = SKSpriteNode(texture: textures.jellyOverlayTexture)
        jelly.size = CGSize(width: cellSize, height: cellSize)
        jelly.position = point(for: gp)
        jelly.zPosition = 1
        jellyLayer.addChild(jelly)
        jellyNodes[gp] = jelly
        gridTileNodes[gp]?.isHidden = true
    }

    private func wallKey(_ point: GridPoint, horizontal: Bool) -> String {
        "\(point.row),\(point.col),\(horizontal ? "b" : "r")"
    }

    private func addWall(at gp: GridPoint, horizontal: Bool) {
        let node = SKSpriteNode(texture: horizontal ? textures.wallHorizontalTexture : textures.wallVerticalTexture)
        let center = point(for: gp)
        if horizontal {
            node.size = CGSize(width: cellSize * 0.9, height: cellSize * 0.3)
            node.position = CGPoint(x: center.x, y: center.y - cellSize / 2)
        } else {
            node.size = CGSize(width: cellSize * 0.3, height: cellSize * 0.9)
            node.position = CGPoint(x: center.x + cellSize / 2, y: center.y)
        }
        node.zPosition = 3
        wallLayer.addChild(node)
        wallNodes[wallKey(gp, horizontal: horizontal)] = node
    }

    func removeWall(between a: GridPoint, and b: GridPoint) {
        let fade = 0.2 * animationSpeedMultiplier
        if a.row == b.row {
            let left = a.col < b.col ? a : b
            wallNodes.removeValue(forKey: wallKey(left, horizontal: false))?.run(.sequence([.fadeOut(withDuration: fade), .removeFromParent()]))
        } else {
            let top = a.row < b.row ? a : b
            wallNodes.removeValue(forKey: wallKey(top, horizontal: true))?.run(.sequence([.fadeOut(withDuration: fade), .removeFromParent()]))
        }
    }

    func clearJelly(at gp: GridPoint) {
        guard let node = jellyNodes.removeValue(forKey: gp) else { return }
        node.run(.sequence([.fadeOut(withDuration: 0.3 * animationSpeedMultiplier), .removeFromParent()]))
        gridTileNodes[gp]?.isHidden = false
    }

    // MARK: - Tiles

    func tileNode(at gp: GridPoint) -> SKSpriteNode? { tileNodes[gp] }

    /// Exposes the same textures tiles/jelly are drawn with, so a caller (e.g.
    /// a removal particle burst) can visually match a game object it no longer
    /// has a live node for.
    func texture(for type: TileType) -> SKTexture { textures.texture(for: type) }
    var jellyTexture: SKTexture { textures.jellyOverlayTexture }

    /// Swaps the two nodes currently tracked at `a` and `b` (both the dictionary
    /// entries and their on-screen positions), so a committed swap in the engine
    /// stays in sync with which sprite the rendering layer considers to be at
    /// each grid point. Without this, later removal/move events look up the
    /// wrong node - the symptom being "the tile that completed a match doesn't
    /// disappear" and, subsequently, orphaned sprites nothing ever cleans up.
    func swapTileNodes(_ a: GridPoint, _ b: GridPoint) {
        guard let nodeA = tileNodes[a], let nodeB = tileNodes[b] else { return }
        tileNodes[a] = nodeB
        tileNodes[b] = nodeA
        nodeA.run(.move(to: point(for: b), duration: Self.swapDuration * animationSpeedMultiplier), withKey: Self.moveActionKey)
        nodeB.run(.move(to: point(for: a), duration: Self.swapDuration * animationSpeedMultiplier), withKey: Self.moveActionKey)
    }

    @discardableResult
    func placeTile(_ type: TileType, at gp: GridPoint, droppingInFromAbove: Bool = false) -> SKSpriteNode {
        // Defensive: never silently orphan a node that's already tracked here.
        removeTile(at: gp)

        let node = TileSpriteNode(texture: textures.texture(for: type))
        node.tileType = type
        node.size = CGSize(width: cellSize * 0.88, height: cellSize * 0.88)
        node.zPosition = 5
        node.alpha = 0
        let target = point(for: gp)
        node.position = droppingInFromAbove ? CGPoint(x: target.x, y: target.y + cellSize * 3) : target
        tileLayer.addChild(node)
        tileNodes[gp] = node
        node.run(.fadeIn(withDuration: Self.spawnFadeDuration * animationSpeedMultiplier), withKey: Self.scaleActionKey)
        if droppingInFromAbove {
            let drop = SKAction.move(to: target, duration: Self.fallInDuration * animationSpeedMultiplier)
            drop.timingMode = .easeIn
            node.run(drop, withKey: Self.moveActionKey)
        }
        return node
    }

    func moveTile(from: GridPoint, to: GridPoint, duration: TimeInterval) {
        guard let node = tileNodes.removeValue(forKey: from) else { return }
        // Defensive: never silently orphan a node already tracked at the destination.
        if to != from { removeTile(at: to) }
        tileNodes[to] = node
        let move = SKAction.move(to: point(for: to), duration: duration * animationSpeedMultiplier)
        // Eased in (starts slow, speeds up) rather than constant-speed, so a
        // multi-row fall reads as gravity accelerating rather than sliding.
        move.timingMode = .easeIn
        node.run(move, withKey: Self.moveActionKey)
    }

    func removeTile(at gp: GridPoint) {
        guard let node = tileNodes.removeValue(forKey: gp) else { return }
        let scaleFade = 0.2 * animationSpeedMultiplier
        node.run(.sequence([.group([.scale(to: 0.1, duration: scaleFade), .fadeOut(withDuration: scaleFade)]), .removeFromParent()]), withKey: Self.scaleActionKey)
    }

    /// Detaches (rather than fades) the tile node at `gp`, for callers that want
    /// to animate it independently - e.g. a "fly to the HUD" objective-collection
    /// flight instead of the usual fade-in-place. Stops tracking it here and
    /// unparents it, but leaves the node itself alive; `node.position` remains
    /// expressed in this node's own coordinate space (the tile layer has no
    /// transform of its own), so callers can still use it with `convert(_:to:)`.
    /// Returns `nil` if no tile is currently tracked at `gp`.
    func detachTile(at gp: GridPoint) -> SKSpriteNode? {
        guard let node = tileNodes.removeValue(forKey: gp) else { return nil }
        node.removeFromParent()
        return node
    }

    /// Same as `detachTile(at:)`, but for the jelly overlay layer.
    func detachJelly(at gp: GridPoint) -> SKSpriteNode? {
        guard let node = jellyNodes.removeValue(forKey: gp) else { return nil }
        node.removeFromParent()
        gridTileNodes[gp]?.isHidden = false
        return node
    }

    func updateTexture(at gp: GridPoint, to type: TileType) {
        guard let node = tileNodes[gp] else { return }
        node.texture = textures.texture(for: type)
        node.tileType = type
    }

    /// After a no-more-moves shuffle the engine reassigns tile types across
    /// the whole board without any sprite actually changing grid points
    /// (see `GameScene.resyncAllTilePositions`), so a plain `updateTexture`
    /// would just flip every texture in place with nothing to show the board
    /// was touched. Instead the tile scales down and fades out, swaps to its
    /// new type, then scales back up and fades in; `delay` lets the caller
    /// stagger these across the board (e.g. by row/col) so it reads as a
    /// ripple rather than every tile popping at once.
    func reshuffleTile(at gp: GridPoint, to type: TileType, delay: TimeInterval) {
        guard let node = tileNodes[gp] else {
            placeTile(type, at: gp)
            return
        }

        let scaleOut = SKAction.scale(to: 0.2, duration: Self.reshuffleOutDuration * animationSpeedMultiplier)
        scaleOut.timingMode = .easeIn
        let fadeOutGroup = SKAction.group([scaleOut, .fadeOut(withDuration: Self.reshuffleOutDuration * animationSpeedMultiplier)])

        let swapTexture = SKAction.run { [weak self] in
            node.texture = self?.textures.texture(for: type)
            node.tileType = type
        }

        let scaleIn = SKAction.scale(to: 1.0, duration: Self.reshuffleInDuration * animationSpeedMultiplier)
        scaleIn.timingMode = .easeOut
        let fadeInGroup = SKAction.group([scaleIn, .fadeIn(withDuration: Self.reshuffleInDuration * animationSpeedMultiplier)])

        node.run(.sequence([.wait(forDuration: delay), fadeOutGroup, swapTexture, fadeInGroup]), withKey: Self.scaleActionKey)
    }

    func pulse(at gp: GridPoint) {
        guard let node = tileNodes[gp] else { return }
        let half = 0.15 * animationSpeedMultiplier
        node.run(.sequence([.scale(to: 1.15, duration: half), .scale(to: 1.0, duration: half)]), withKey: Self.scaleActionKey)
    }

    /// Shows the "currently selected" ring at `gp`. Jumps straight there
    /// rather than animating in, since selection is a state change, not an
    /// event - `GameScene` calls this once per tap-to-select and leaves it
    /// up until the selection resolves (successful move, errant move, or a
    /// timeout).
    func showSelection(at gp: GridPoint) {
        selectionHighlight.position = point(for: gp)
        selectionHighlight.isHidden = false
    }

    func hideSelection() {
        selectionHighlight.isHidden = true
    }

    // MARK: - Bacteria wobble

    /// Amplitude of each corner's displacement, as a fraction of the tile's own size.
    private static let wobbleAmplitude: CGFloat = 0.07
    /// Each corner advances at its own rate (radians/sec) so the four corners never
    /// move in lockstep - ported from the original's four independent per-corner
    /// phase accumulators (`fruit[frt].tl/tr/bl/br`, `drawAtlasBendScale` in
    /// `Reference/itag/DrPopperViewController.m`), converted from its
    /// degrees-per-frame-at-60fps rates (2.3/2.2/2.4/2.6) to radians/sec.
    private static let wobbleRates = (tl: 2.408, tr: 2.304, bl: 2.513, br: 2.723)

    /// Continuously distorts every bacteria tile's four corners - the original
    /// draws bacteria (and only bacteria; see `drawAtlasBendScale`'s call site)
    /// with this always-on "jelly wobble" so they read as organic/alive versus
    /// the plain pills. Ported using `SKWarpGeometryGrid`, SpriteKit's native
    /// per-corner quad deformation, called once per frame from `GameScene.update`.
    func updateBacteriaWobble(elapsedTime: TimeInterval) {
        let r = Self.wobbleRates
        let sourcePositions: [SIMD2<Float>] = [
            SIMD2(0, 0), SIMD2(1, 0),
            SIMD2(0, 1), SIMD2(1, 1),
        ]

        for node in tileNodes.values where node.tileType?.isBacteria == true {
            // Each bacteria sprite runs the same four-corner motion on its own
            // clock (`wobbleSpeedScale`, `wobblePhase`) and at its own size
            // (`wobbleAmplitudeScale`), so a cluster of them on screen reads as
            // independently alive rather than one shape stamped out repeatedly.
            let t = elapsedTime * node.wobbleSpeedScale + node.wobblePhase
            let a = Self.wobbleAmplitude * node.wobbleAmplitudeScale

            func offset(_ rate: Double) -> SIMD2<Float> {
                SIMD2<Float>(Float(a * CGFloat(sin(rate * t))), Float(a * CGFloat(cos(rate * t))))
            }

            let bl = offset(r.bl), br = offset(r.br), tl = offset(r.tl), tr = offset(r.tr)
            let destinationPositions: [SIMD2<Float>] = [
                sourcePositions[0] + bl, sourcePositions[1] + br,
                sourcePositions[2] + tl, sourcePositions[3] + tr,
            ]
            node.warpGeometry = SKWarpGeometryGrid(columns: 1, rows: 1, sourcePositions: sourcePositions, destinationPositions: destinationPositions)
        }
    }
}
