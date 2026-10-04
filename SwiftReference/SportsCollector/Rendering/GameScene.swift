import SpriteKit

final class GameScene: SKScene {
    private let engine: GameEngine
    private let level: Level
    private let background:BackgroundNode? // = BackgroundNode(level: level)
    private let boardNode: BoardNode
    private let hud = HUDNode()
    private let banner = BannerNode()
    private let tutorialOverlay = TutorialOverlayNode()
    private let winOverlay = WinOverlayNode()
    /// The win screen's rotating backdrop - kept separate from `winOverlay`
    /// (and given the same zPosition slot as `background`) so it renders
    /// behind `boardNode` instead of on top of everything else.
    private let winBackground = WinBackgroundNode()
    private let loseOverlay = LoseOverlayNode()
    private let objectivesOverlay = ObjectivesOverlayNode()

    /// Set by `ViewController` after presenting this scene; invoked when the
    /// win overlay's Continue button is tapped so the game screen can pop
    /// itself back to level select without this scene needing a reference
    /// to any `UINavigationController`.
    var onContinue: (() -> Void)?

    /// Set by `ViewController`; invoked when the win overlay's Restart button
    /// is tapped so the game screen can rebuild itself from a fresh board
    /// without needing any navigation-stack awareness of its own.
    var onRestart: (() -> Void)?

    private var lastUpdateTime: TimeInterval?
    /// Accumulated from clamped per-frame `deltaTime`, rather than the raw
    /// `currentTime` SpriteKit hands to `update(_:)`, which is wall-clock time
    /// and keeps advancing while the app is backgrounded or the scene is
    /// paused - driving the background/wobble sinusoids off it made them jump
    /// to a new position on resume instead of continuing smoothly.
    private var animationTime: TimeInterval = 0
    private var selectedPoint: GridPoint?
    /// `animationTime` when `selectedPoint` was last set, so a selection
    /// that's never followed up on auto-clears instead of staying visibly
    /// "held" forever - see the timeout check in `update(_:)`.
    private var selectedAt: TimeInterval?
    /// How long a tile stays visibly selected with no follow-up tap before
    /// the selection quietly clears itself.
    private static let selectionTimeout: TimeInterval = 3.0
    private var announcedTutorial = false
    /// The touch currently pressing the pause button, if any - tracked so
    /// `touchesEnded`/`touchesCancelled` know which touch's release should
    /// depress it (see `HUDNode.setPauseButtonPressed`).
    private var pauseButtonTouch: UITouch?

    /// Debug aid (see HUD's speed button): cycles through slower fall-step
    /// pacing + slower tile animations so a human can watch a cascade happen
    /// step by step instead of it resolving in a handful of frames.
    private struct DebugSpeed {
        let label: String
        let fallStepInterval: TimeInterval
        let animationMultiplier: TimeInterval
    }
    private let debugSpeeds: [DebugSpeed] = [
        DebugSpeed(label: "Speed: 1x", fallStepInterval: 0, animationMultiplier: 1.0),
        DebugSpeed(label: "Speed: 0.75x", fallStepInterval: 0, animationMultiplier: 1.5),
        DebugSpeed(label: "Speed: 0.5x", fallStepInterval: 0, animationMultiplier: 2.0),
        DebugSpeed(label: "Speed: 0.25x", fallStepInterval: 0.35, animationMultiplier: 4.0),
        DebugSpeed(label: "Speed: 0.1x", fallStepInterval: 0.9, animationMultiplier: 8.0),
    ]
    private var debugSpeedIndex = 0

    init(level: Level, size: CGSize) {
        self.level = level
        self.background = BackgroundNode(level: level)
        self.engine = GameEngine(level: level)
        self.boardNode = BoardNode(level: level, textures: TileTextureProvider())
        super.init(size: size)

        engine.swapAnimationDuration = BoardNode.swapDuration

        anchorPoint = CGPoint(x: 0.5, y: 0.5)
        backgroundColor = SKColor(white: 0.08, alpha: 1)
        addChild(background!)
        winBackground.zPosition = -100
        winBackground.isHidden = true
        addChild(winBackground)
        addChild(boardNode)
        addChild(hud)
        addChild(banner)
        addChild(tutorialOverlay)
        addChild(winOverlay)
        addChild(loseOverlay)
        addChild(objectivesOverlay)
    }

    required init?(coder: NSCoder) {
        fatalError("GameScene does not support storyboard instantiation")
    }

    override func didMove(to view: SKView) {
        layoutForCurrentSize()
        seedInitialTiles()
        AudioManager.shared.playMusic("DrPopperTune1", loop: true)
    }

    override func didChangeSize(_ oldSize: CGSize) {
        layoutForCurrentSize()
    }

    private func layoutForCurrentSize() {
        background?.layout(in: size)
        boardNode.layout(in: CGSize(width: size.width * 0.92, height: size.height * 0.72))
        // HUD now has two zones (see HUDNode.layout): level/score/moves badges
        // at the top, gamebar + pause button at the bottom - so the board is
        // centered to leave even clearance on both sides.
        boardNode.position = CGPoint(x: 0, y: 0)
        hud.layout(in: size)
        banner.position = .zero
        winOverlay.position = .zero
        winOverlay.layout(in: size)
        winBackground.position = .zero
        winBackground.layout(in: size)
        loseOverlay.position = .zero
        loseOverlay.layout(in: size)
        objectivesOverlay.position = .zero
        objectivesOverlay.layout(in: size)
    }

    private func seedInitialTiles() {
        for row in 0..<level.height {
            for col in 0..<level.width {
                let gp = GridPoint(row: row, col: col)
                if let tile = engine.board[gp]?.tile {
                    boardNode.placeTile(tile, at: gp)
                }
            }
        }
        refreshHUD()

        objectivesOverlay.configure(objectives: engine.objectives, textures: TileTextureProvider())
        objectivesOverlay.show()
    }

    private func refreshHUD() {
        hud.update(level: level.mapNumber, score: engine.score, moves: engine.movesRemaining, objectives: engine.objectives)
        //hud.update(level: level.id, score: engine.score, moves: engine.movesRemaining, objectives: engine.objectives)
    }

    // MARK: - Frame loop

    override func update(_ currentTime: TimeInterval) {
        let deltaTime = lastUpdateTime.map { min(currentTime - $0, 0.1) } ?? 0
        lastUpdateTime = currentTime
        animationTime += deltaTime

        if let selectedAt, animationTime - selectedAt > Self.selectionTimeout {
            selectedPoint = nil
            self.selectedAt = nil
            boardNode.hideSelection()
        }

        background?.update(elapsedTime: animationTime)
        boardNode.updateBacteriaWobble(elapsedTime: animationTime)

        // Captured before `advance()` mutates `engine.objectives`, so the batch
        // handler below can tell whether a given objective still had a
        // nonzero count *before* the removal it's currently looking at -
        // see `handle(_:objectivesBefore:)`.
        let objectivesBefore = Dictionary(uniqueKeysWithValues: engine.objectives.map { ($0.kind, $0.remaining) })
        let events = engine.advance(deltaTime: deltaTime)
        if !events.isEmpty {
            handle(events, objectivesBefore: objectivesBefore)
            refreshHUD()
        }

        if !announcedTutorial, engine.phase == .tutorialRestricted {
            announcedTutorial = true
            if let rect = engine.tutorialRect {
                let center = GridPoint(row: (rect.minRow + rect.maxRow) / 2, col: (rect.minCol + rect.maxCol) / 2)
                tutorialOverlay.show(at: boardNode.convert(boardNode.point(for: center), to: self))
            }
        }
    }

    /// Processes one frame's full event batch, rather than a plain `forEach`,
    /// so `.tileRemoved`/`.jellyCleared` can be paired with a same-point
    /// `.objectiveProgressed` that follows shortly after (the engine always
    /// emits them together, per point, with only a few passthrough events -
    /// `scoreChanged`, `bacteriaFellOff`, `objectiveFlyCompleted` - in
    /// between). When a removal is linked to an objective that still had a
    /// nonzero count, it flies to that objective's HUD icon instead of fading
    /// in place, and the HUD's count label only decrements once it lands -
    /// see `animateObjectiveCollection`.
    private func handle(_ events: [GameEvent], objectivesBefore: [ObjectiveKind: Int]) {
        playBatchSounds(for: events)

        var remainingByKind = objectivesBefore

        func pairedObjectiveProgressed(after index: Int, kind: ObjectiveKind) -> Int? {
            var lookahead = index + 1
            while lookahead < events.count {
                switch events[lookahead] {
                case .tileRemoved, .jellyCleared:
                    return nil
                case .objectiveProgressed(let k, let remaining) where k == kind:
                    return remaining
                default:
                    lookahead += 1
                }
            }
            return nil
        }

        for (index, event) in events.enumerated() {
            switch event {
            case .tileRemoved(let point, let tile, _):
                let kind = ObjectiveKind.collect(tile)
                if let remaining = pairedObjectiveProgressed(after: index, kind: kind) {
                    let before = remainingByKind[kind] ?? 0
                    remainingByKind[kind] = remaining
                    if before > 0, let node = boardNode.detachTile(at: point) {
                        animateObjectiveCollection(node, kind: kind, remaining: remaining)
                        continue
                    }
                }
                spawnCollectionBurst(texture: boardNode.texture(for: tile), at: point)
                boardNode.removeTile(at: point)

            case .jellyCleared(let point):
                let kind = ObjectiveKind.clearJelly
                if let remaining = pairedObjectiveProgressed(after: index, kind: kind) {
                    let before = remainingByKind[kind] ?? 0
                    remainingByKind[kind] = remaining
                    if before > 0, let node = boardNode.detachJelly(at: point) {
                        animateObjectiveCollection(node, kind: kind, remaining: remaining)
                        continue
                    }
                }
                spawnCollectionBurst(texture: boardNode.jellyTexture, at: point)
                boardNode.clearJelly(at: point)

            default:
                handle(event)
            }
        }
    }

    /// Plays each sound category at most once per event batch, regardless of
    /// how many matching events occurred in that batch - mirrors the
    /// original game's own per-update-pass boolean-gated sound playback
    /// (`DrPopperViewController.m`'s `playRemoveGood`/`playRemoveSolid` etc.),
    /// so a big cascade doesn't fire the same short sound a dozen times at once.
    private func playBatchSounds(for events: [GameEvent]) {
        if events.contains(where: { if case .tileMoved = $0 { return true } else { return false } }) {
            playSound("PillsFalling1Sec")
        }
        if events.contains(where: { if case .tileRemoved(_, let tile, _) = $0 { return tile.isBacteria } else { return false } }) {
            playSound("BacteriaFall_1sec")
        }
        if events.contains(where: { if case .tileRemoved(_, let tile, _) = $0 { return !tile.isBacteria } else { return false } }) {
            playSound("StarryEffect1A_UpBeat")
        }
        if events.contains(where: { if case .jellyCleared = $0 { return true } else { return false } }) {
            playSound("Squelch_Tile1")
        }
        if events.contains(where: { if case .solidDamaged(_, _, let to) = $0 { return to != nil } else { return false } }) {
            playSound("Explosion2Loud")
        }
        if events.contains(where: { if case .solidDamaged(_, _, let to) = $0 { return to == nil } else { return false } }) {
            playSound("Explosion1")
        }
        if events.contains(where: { if case .wallDestroyed = $0 { return true } else { return false } }) {
            playSound(Bool.random() ? "Siren1_2secs" : "Siren2_2secs")
        }
        if events.contains(where: { if case .comboPillDowngraded = $0 { return true } else { return false } }) {
            playSound("PillX2")
        }
    }

    private func playSound(_ name: String) {
        run(SKAction.playSoundFileNamed("\(name).caf", waitForCompletion: false))
    }

    /// Flies a detached tile/jelly sprite from its current board position to
    /// its objective's HUD icon, decrementing that icon's count label only
    /// once it lands (rather than immediately, in step with the board removal).
    private func animateObjectiveCollection(_ node: SKSpriteNode, kind: ObjectiveKind, remaining: Int) {
        node.removeAllActions()
        let startPosition = boardNode.convert(node.position, to: self)
        addChild(node)
        node.position = startPosition
        node.zPosition = 50

        guard let target = hud.scenePosition(for: kind, in: self) else {
            // Defensive: HUD isn't tracking this objective for some reason -
            // fall back to a plain fade instead of flying nowhere.
            node.run(.sequence([.group([.scale(to: 0.1, duration: 0.2), .fadeOut(withDuration: 0.2)]), .removeFromParent()]))
            hud.collect(kind: kind, remaining: remaining)
            return
        }

        let duration = 0.45 * boardNode.animationSpeedMultiplier
        let move = SKAction.move(to: target, duration: duration)
        move.timingMode = .easeIn
        let flight = SKAction.group([move, .scale(to: 0.3, duration: duration)])
        let land = SKAction.run { [weak self] in self?.hud.collect(kind: kind, remaining: remaining) }
        node.run(.sequence([flight, land, .fadeOut(withDuration: 0.08), .removeFromParent()]))
    }

    /// Small particle burst, using the removed object's own texture (its tile
    /// icon, or the jelly overlay swatch), for removals that have no HUD
    /// objective to fly toward - so they still get a bit of "pop" instead of
    /// a plain fade. Purely cosmetic; self-removing, no board/engine state.
    private func spawnCollectionBurst(texture: SKTexture, at gp: GridPoint) {
        let speedMultiplier = CGFloat(Float.random(in: 2.5..<3.5)) //boardNode.animationSpeedMultiplier
        let emitter = SKEmitterNode()
        emitter.particleTexture = texture
        emitter.position = boardNode.convert(boardNode.point(for: gp), to: self)
        emitter.zPosition = 40
        emitter.particleBirthRate = 500
        emitter.numParticlesToEmit = 10
        emitter.particleLifetime = 0.35 * speedMultiplier
        emitter.particleLifetimeRange = 0.1 * speedMultiplier
        emitter.particleSpeed = 70
        emitter.particleSpeedRange = 30
        emitter.emissionAngleRange = .pi * 2
        emitter.particleScale = 0.132
        emitter.particleScaleRange = 0.12
        emitter.particleScaleSpeed = -0.5 / speedMultiplier
        emitter.particleAlpha = 1
        emitter.particleAlphaSpeed = -2.6 / speedMultiplier
        emitter.particleRotationRange = .pi * 2
        emitter.particleRotationSpeed = 2
        addChild(emitter)
        emitter.run(.sequence([.wait(forDuration: 0.5 * speedMultiplier), .removeFromParent()]))
    }

    private func handle(_ event: GameEvent) {
        switch event {
        case .introFadeInStarted, .objectiveProgressed, .objectiveFlyCompleted,
             .bacteriaFellOff, .scoreChanged, .movesChanged, .tileRemoved, .jellyCleared:
            break

        case .swapAnimated(let a, let b, _):
            boardNode.swapTileNodes(a, b)
            playSound("Swap_Pill1A")

        case .tilePlaced(let point, let type):
            boardNode.placeTile(type, at: point, droppingInFromAbove: true)

        case .swapRejected(let a, let b):
            boardNode.pulse(at: a)
            boardNode.pulse(at: b)
            playSound("SwapPillError1")

        case .tileMoved(let from, let to, _):
            boardNode.moveTile(from: from, to: to, duration: 0.16)

        case .comboPillDowngraded(let point, _, let to):
            boardNode.updateTexture(at: point, to: to)

        case .solidDamaged(let point, _, let to):
            if let to {
                boardNode.updateTexture(at: point, to: to)
            } else {
                boardNode.removeTile(at: point)
            }

        case .wallDestroyed(let a, let b):
            boardNode.removeWall(between: a, and: b)

        case .hintSuggested(let a, let b):
            boardNode.pulse(at: a)
            boardNode.pulse(at: b)
            playSound("ShowHint")

        case .noMoreMovesShuffleStarted:
            banner.show("No More Moves!")

        case .noMoreMovesShuffleFinished:
            resyncAllTilePositions()

        case .tutorialUnlocked:
            tutorialOverlay.hide()

        case .won(let score, _):
            AudioManager.shared.playMusic("PILLPOPTUNE", loop: false)
            // The engine's own `score`/`stars` are computed the instant the
            // board is solved, before the moves-remaining bonus `WinOverlayNode`
            // reveals - recompute both here against the post-bonus total so
            // the persisted result and the star rating shown both reflect it.
            let movesLeft = engine.movesRemaining
            let finalScore = score + movesLeft * WinOverlayNode.moveBonusPerMove
            let finalStars = StarThreshold.stars(forScore: finalScore, thresholds: level.starThresholds)
            LevelProgressStore.recordWin(mapNumber: level.mapNumber, score: finalScore, stars: finalStars)
            background?.isHidden = true
            winBackground.isHidden = false
            winOverlay.show(baseScore: score, movesRemaining: movesLeft, finalStars: finalStars) { [weak self] currentScore, movesLeft in
                guard let self else { return }
                self.hud.update(level: self.level.mapNumber, score: currentScore, moves: movesLeft, objectives: self.engine.objectives)
            }

        case .lost:
            AudioManager.shared.playMusic("DoctorPopperLoseJingle_4sec", loop: false)
            loseOverlay.show()
        }
    }

    /// After a shuffle the engine's board has an entirely fresh arrangement;
    /// rather than trying to track which sprite went where, just resync every
    /// tile's texture against the current board state. Sprites never actually
    /// move between grid points (there's no stable per-tile identity to
    /// track), so each one is staggered by its distance from the top-left
    /// corner and popped out/in (`reshuffleTile`) to read as a ripple sweeping
    /// across the board - the visual cue that it was rearranged.
    private func resyncAllTilePositions() {
        for row in 0..<level.height {
            for col in 0..<level.width {
                let gp = GridPoint(row: row, col: col)
                guard let tile = engine.board[gp]?.tile else { continue }
                let delay = Double(row + col) * 0.025
                boardNode.reshuffleTile(at: gp, to: tile, delay: delay)
            }
        }
    }

    // MARK: - Touch input

    private func togglePause() {
        // Direct alpha set, not an animated show/hide: once `isPaused` is
        // true the scene never evaluates queued actions, so a fade-in here
        // would never actually reach visible.
        isPaused.toggle()
        if isPaused {
            banner.setVisibleInstantly(true, message: "Paused")
        } else {
            banner.setVisibleInstantly(false)
        }
    }

    private func cycleDebugSpeed() {
        debugSpeedIndex = (debugSpeedIndex + 1) % debugSpeeds.count
        let speed = debugSpeeds[debugSpeedIndex]
        engine.debugFallStepInterval = speed.fallStepInterval
        engine.swapAnimationDuration = BoardNode.swapDuration * speed.animationMultiplier
        boardNode.animationSpeedMultiplier = speed.animationMultiplier
        hud.setSpeedText(speed.label)
    }

    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard let touch = touches.first else { return }
        if !objectivesOverlay.isHidden {
            if objectivesOverlay.containsBackButton(touch.location(in: objectivesOverlay)) {
                onContinue?()
            } else if objectivesOverlay.containsContinueButton(touch.location(in: objectivesOverlay)) {
                objectivesOverlay.hide()
            }
            return
        }
        if !winOverlay.isHidden {
            if winOverlay.containsRestartButton(touch.location(in: winOverlay)) {
                onRestart?()
            } else if winOverlay.containsContinueButton(touch.location(in: winOverlay)) {
                onContinue?()
            }
            return
        }
        if !loseOverlay.isHidden {
            if loseOverlay.containsContinueButton(touch.location(in: loseOverlay)) {
                onContinue?()
            }
            return
        }
        if hud.containsPauseToggle(touch.location(in: hud)) {
            pauseButtonTouch = touch
            hud.setPauseButtonPressed(true)
            togglePause()
            return
        }
        if hud.containsSpeedToggle(touch.location(in: hud)) {
            cycleDebugSpeed()
            return
        }
        engine.notifyUserInteraction()
        guard let gp = boardNode.gridPoint(at: touch.location(in: boardNode)) else { return }

        if let selected = selectedPoint {
            selectedPoint = nil
            selectedAt = nil
            boardNode.hideSelection()
            if selected != gp {
                engine.attemptSwap(selected, gp)
            }
        } else {
            // Only show the tile as selected if a move is actually possible
            // from here - a cell with no tile, or a tap while the board is
            // mid-animation/won/lost, can never lead anywhere.
            guard boardNode.tileNode(at: gp) != nil,
                  engine.phase == .idle || engine.phase == .tutorialRestricted else { return }
            selectedPoint = gp
            selectedAt = animationTime
            boardNode.showSelection(at: gp)
        }
    }

    override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard let touch = touches.first, let selected = selectedPoint else { return }
        guard let gp = boardNode.gridPoint(at: touch.location(in: boardNode)), gp != selected else { return }
        selectedPoint = nil
        selectedAt = nil
        boardNode.hideSelection()
        engine.attemptSwap(selected, gp)
    }

    override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) {
        depressPauseButton(if: touches)
    }

    override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent?) {
        depressPauseButton(if: touches)
    }

    private func depressPauseButton(if touches: Set<UITouch>) {
        guard let pressedTouch = pauseButtonTouch, touches.contains(pressedTouch) else { return }
        pauseButtonTouch = nil
        hud.setPauseButtonPressed(false)
    }
}
