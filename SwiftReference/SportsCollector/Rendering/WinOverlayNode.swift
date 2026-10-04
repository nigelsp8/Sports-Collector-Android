import SpriteKit
import CoreImage

/// The spinning sunburst backdrop shown behind the win card, replacing the
/// normal panning `BackgroundNode` for the win screen. Wrapped in an
/// `SKEffectNode` so it can be blurred in real time while it keeps rotating -
/// a single full-screen sprite is cheap enough to re-filter every frame.
/// Owned directly by `GameScene` (not `WinOverlayNode`) so it can sit behind
/// `BoardNode` in the scene's z-order, in the same layer `BackgroundNode`
/// normally occupies, while the rest of the win overlay stays on top.
final class WinBackgroundNode: SKEffectNode {
    private let sprite = SKSpriteNode(imageNamed: "bkgwin")

    override init() {
        super.init()
        shouldEnableEffects = true
        shouldRasterize = false
        filter = CIFilter(name: "CIGaussianBlur", parameters: ["inputRadius": 8])
        addChild(sprite)
        sprite.run(.repeatForever(.rotate(byAngle: .pi * 2, duration: 14)))
    }

    required init?(coder: NSCoder) {
        fatalError("WinBackgroundNode does not support storyboard instantiation")
    }

    func layout(in size: CGSize) {
        // A square image rotating about its own centre only guarantees full
        // screen coverage at every angle once its side is at least the
        // screen's diagonal - the worst-case orientation (a corner facing a
        // screen corner) only reaches half that side length.
        let diagonal = (size.width * size.width + size.height * size.height).squareRoot()
        sprite.size = CGSize(width: diagonal, height: diagonal)
    }
}

/// Full-screen "You Win" overlay: darkens/blurs the screen behind a win card
/// with a star rating and a Continue button that pops back to level select.
/// `GameScene` hides the live board/HUD and the normal background when this
/// is shown, since the level is over and nothing else needs to keep updating.
/// Owns everything except the rotating `WinBackgroundNode` backdrop, which
/// `GameScene` positions separately, behind `BoardNode`, rather than inside
/// this node's own (frontmost) z-order.
final class WinOverlayNode: SKNode {
    /// Points awarded for each move the player had left when the board was
    /// solved, revealed one at a time by `show` before the stars/Continue
    /// button appear - see `runMoveBonusSequence`.
    static let moveBonusPerMove = 3000
    /// Delay between each individual move-bonus tick in `runMoveBonusSequence`.
    private static let moveBonusTickInterval: TimeInterval = 0.25

    private let dimOverlay = SKSpriteNode(color: SKColor(white: 0, alpha: 0.75), size: .zero)
    private let titleLabel = SKLabelNode(fontNamed: "AvenirNext-Bold")
    private let winImage = SKSpriteNode(imageNamed: "win")
    private let scoreLabel = SKLabelNode(fontNamed: "AvenirNext-Bold")
    private let moveBonusLabel = SKLabelNode(fontNamed: "AvenirNext-Bold")
    private let starsImage = SKSpriteNode(imageNamed: "starsx3")
    private let continueButton = SKSpriteNode(imageNamed: "nextbutton")
    private let restartButton = SKSpriteNode(imageNamed: "rewindbutton")

    private var cardFontSize: CGFloat = 24

    override init() {
        super.init()
        isHidden = true
        zPosition = 150

        titleLabel.verticalAlignmentMode = .baseline
        titleLabel.horizontalAlignmentMode = .center

        for label in [scoreLabel, moveBonusLabel] {
            label.verticalAlignmentMode = .center
            label.horizontalAlignmentMode = .center
        }

        addChild(dimOverlay)
        addChild(titleLabel)
        addChild(winImage)
        addChild(scoreLabel)
        addChild(moveBonusLabel)
        addChild(starsImage)
        addChild(continueButton)
        addChild(restartButton)
    }

    required init?(coder: NSCoder) {
        fatalError("WinOverlayNode does not support storyboard instantiation")
    }

    func layout(in size: CGSize) {
        dimOverlay.size = size

        let winSize = size.width * 0.75
        winImage.size = CGSize(width: winSize, height: winSize)
        winImage.position = CGPoint(x: 0, y: size.height * 0.04)

        let titleFontSize = min(size.width * 0.16, 80)
        titleLabel.attributedText = NSAttributedString(
            string: "You Win!",
            attributes: [
                .font: UIFont(name: "AvenirNext-Bold", size: titleFontSize) as Any,
                .foregroundColor: UIColor.white,
                .strokeColor: UIColor.black,
                .strokeWidth: -4,
            ]
        )
        titleLabel.position = CGPoint(x: 0, y: winImage.position.y + winSize / 2 + titleFontSize * 0.3)

        let starsWidth = winSize * 0.56
        let starsAspect: CGFloat = 440.0 / 724.0
        starsImage.size = CGSize(width: starsWidth, height: starsWidth * starsAspect)
        starsImage.position = CGPoint(x: 0, y: winImage.position.y - (winSize * 0.5))

        cardFontSize = min(size.width * 0.07, 32)
        scoreLabel.position = CGPoint(x: 0, y: winImage.position.y - winSize * 0.1)
        moveBonusLabel.position = CGPoint(x: 0, y: winImage.position.y - winSize * 0.1 - cardFontSize * 1.4)

        
        let buttonSize = size.width * 0.2
        let spacing = buttonSize + 16
        continueButton.size = CGSize(width: buttonSize, height: buttonSize)
        continueButton.position = CGPoint(x: spacing, y: -size.height / 2 + buttonSize * 0.75 + 24)

        restartButton.size = CGSize(width: buttonSize, height: buttonSize)
        restartButton.position = CGPoint(x: -spacing, y: continueButton.position.y)
    }

    private func styledText(_ string: String, fontSize: CGFloat) -> NSAttributedString {
        NSAttributedString(
            string: string,
            attributes: [
                .font: UIFont(name: "AvenirNext-Bold", size: fontSize) as Any,
                .foregroundColor: UIColor.white,
                .strokeColor: UIColor.black,
                .strokeWidth: -3,
            ]
        )
    }

    /// Shows the win card immediately (title/win image/running score), then -
    /// once the fade-in finishes - ticks `movesRemaining` move-bonus awards
    /// into the score one at a time (`runMoveBonusSequence`), only revealing
    /// the star rating and Continue button once that tally finishes. `stars`
    /// is the *final* rating, i.e. already computed against the score
    /// including the move bonus, and clamped to 1-3 by the caller - the
    /// engine can report 0 for a win below the first star threshold, but
    /// there's no "zero stars" art, so the stars image is just hidden rather
    /// than guessing.
    /// `onBonusTick(currentScore, movesLeft)` fires once immediately (with the
    /// starting score/move count) and again after every tick of the bonus
    /// countdown, so a caller whose own UI (e.g. the HUD behind this overlay)
    /// still shows score/moves can stay in sync with the on-card numbers
    /// rather than jumping straight to the final total once this overlay hides.
    func show(baseScore: Int, movesRemaining: Int, finalStars: Int, onBonusTick: @escaping (Int, Int) -> Void = { _, _ in }) {
        starsImage.isHidden = true
        continueButton.isHidden = true
        restartButton.isHidden = true
        scoreLabel.alpha = 1
        moveBonusLabel.alpha = 1
        scoreLabel.attributedText = styledText("\(baseScore)", fontSize: cardFontSize)
        moveBonusLabel.isHidden = movesRemaining <= 0
        moveBonusLabel.attributedText = styledText(
            "Moves Bonus: \(movesRemaining) x \(Self.moveBonusPerMove)",
            fontSize: cardFontSize * 0.6
        )
        onBonusTick(baseScore, movesRemaining)

        isHidden = false
        alpha = 0
        run(.sequence([
            .fadeIn(withDuration: 0.3),
            .run { [weak self] in
                self?.runMoveBonusSequence(baseScore: baseScore, movesRemaining: movesRemaining, finalStars: finalStars, onBonusTick: onBonusTick)
            },
        ]))
    }

    /// Ticks the score up by `moveBonusPerMove` once per remaining move, each
    /// tick separated by `moveBonusTickInterval` so the player can watch the
    /// moves count drain to zero as the score climbs, before revealing the
    /// star rating and Continue button.
    private func runMoveBonusSequence(baseScore: Int, movesRemaining: Int, finalStars: Int, onBonusTick: @escaping (Int, Int) -> Void) {
        guard movesRemaining > 0 else {
            revealStarsAndContinue(stars: finalStars)
            return
        }

        var runningScore = baseScore
        var tickActions: [SKAction] = []
        for movesLeftAfterTick in stride(from: movesRemaining - 1, through: 0, by: -1) {
            tickActions.append(.wait(forDuration: Self.moveBonusTickInterval))
            tickActions.append(.run { [weak self] in
                runningScore += Self.moveBonusPerMove
                self?.scoreLabel.attributedText = self?.styledText("\(runningScore)", fontSize: self?.cardFontSize ?? 24)
                self?.scoreLabel.run(.sequence([.scale(to: 1.15, duration: 0.06), .scale(to: 1.0, duration: 0.09)]))
                self?.moveBonusLabel.attributedText = self?.styledText(
                    "Moves Bonus: \(movesLeftAfterTick) x \(Self.moveBonusPerMove)",
                    fontSize: (self?.cardFontSize ?? 24) * 0.6
                )
                onBonusTick(runningScore, movesLeftAfterTick)
            })
        }
        tickActions.append(.wait(forDuration: Self.moveBonusTickInterval))
        tickActions.append(.run { [weak self] in self?.revealStarsAndContinue(stars: finalStars) })
        run(.sequence(tickActions), withKey: "moveBonusSequence")
    }

    private func revealStarsAndContinue(stars: Int) {
        moveBonusLabel.run(.fadeOut(withDuration: 0.2))

        if stars <= 0 {
            starsImage.isHidden = true
        } else {
            starsImage.isHidden = false
            starsImage.alpha = 0
            starsImage.setScale(1.6)
            starsImage.texture = SKTexture(imageNamed: "starsx\(min(stars, 3))")
            let scaleDown = SKAction.scale(to: 1.0, duration: 0.3)
            scaleDown.timingMode = .easeOut
            starsImage.run(.group([.fadeIn(withDuration: 0.3), scaleDown]))
        }

        continueButton.isHidden = false
        continueButton.alpha = 0
        continueButton.run(.fadeIn(withDuration: 0.3))

        restartButton.isHidden = false
        restartButton.alpha = 0
        restartButton.run(.fadeIn(withDuration: 0.3))
    }

    /// Hit test for the Continue button, in this node's own coordinate space
    /// (i.e. pass `touch.location(in: winOverlay)`).
    func containsContinueButton(_ pointInOverlay: CGPoint) -> Bool {
        continueButton.contains(pointInOverlay)
    }

    /// Hit test for the Restart button, in this node's own coordinate space
    /// (i.e. pass `touch.location(in: winOverlay)`).
    func containsRestartButton(_ pointInOverlay: CGPoint) -> Bool {
        restartButton.contains(pointInOverlay)
    }
}
