//
//  ViewController.swift
//  Sports Collector
//
//  Created by Nigel Speight on 27/09/2026.
//

import UIKit
import SpriteKit

class ViewController: UIViewController {

    /// Which level to host. Defaults to 1 for storyboard-based instantiation;
    /// `LevelSelectViewController` sets this before pushing a programmatically
    /// created instance.
    var mapID = 1     // this is the order 1 - 100

    /// When set, `viewDidLoad` uses this directly instead of resolving
    /// `levelOrder[mapID]` through `LevelLoader` - lets the level editor
    /// play-test an unsaved in-memory edit.
    var injectedLevel: Level?

    static let levelOrder:[Int] = [
        0, // not actually used
        1,36,46,52,17,14,51,23,73,22,
        30,48,86,8,11,33,35,38,47,27,
        44,78,32,43,49,76,85,34,72,88,
        65,15,66,68,19,28,50,90,7,21,
        24,77,79,16,41,57,74,63,45,6,
        2,40,4,69,61,71,75,12,55,56,
        20,60,89,10,25,26,80,84,82,62,
        53,37,13,87,18,9,31,3,42,54,
        83,67,39,81,64,29,59,70,58,5,
        91,92,93,94,95,96,97,98,99,100,
        -1
    ]

    override var prefersStatusBarHidden: Bool { true }

    /// Created once in `viewDidLoad`, then reused by `presentGameScene` every
    /// time the win overlay's Restart button rebuilds the scene from scratch.
    private var skView: SKView!

    /// Resolved once in `viewDidLoad` (either `injectedLevel` or the loaded
    /// bundle/override level) and kept around so a restart doesn't need to
    /// re-resolve it - `Level` is immutable value data, so the same instance
    /// is safe to hand to a brand-new `GameScene`/`GameEngine` repeatedly.
    private var level: Level?

    override func viewDidLoad() {
        super.viewDidLoad()

        let skView = SKView()
        skView.translatesAutoresizingMaskIntoConstraints = false
        // Temporarily hidden while iterating on the HUD art.
        skView.showsFPS = false
        skView.showsNodeCount = false
        view.addSubview(skView)
        NSLayoutConstraint.activate([
            skView.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor),
            skView.bottomAnchor.constraint(equalTo: view.bottomAnchor),
            skView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            skView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
        ])
        self.skView = skView

        do {
            if let injectedLevel {
                level = injectedLevel
            } else {
                let actualLevelNumber = Self.levelOrder[mapID]
                level = try LevelLoader.loadLevel(resourceName: "\(actualLevelNumber)", level: mapID)
            }
            title = injectedLevel != nil ? "Level \(mapID+1) (Preview)" : "Level \(mapID+1)" // think this is for navigation purposes when showing a header
            presentGameScene()
        } catch {
            print("Failed to load level \(mapID): \(error)")
        }

        self.navigationController?.interactivePopGestureRecognizer?.isEnabled = false
        if #available(iOS 26.0, *) {
            self.navigationController?.interactiveContentPopGestureRecognizer?.isEnabled = false
        }
    }

    /// Builds a fresh `GameScene` from the already-resolved `level` and
    /// presents it, replacing whatever scene `skView` currently shows -
    /// called once from `viewDidLoad` and again from the win overlay's
    /// Restart button, so restarting a level is just "do it again."
    private func presentGameScene() {
        guard let level else { return }
        let scene = GameScene(level: level, size: skView.bounds.size)
        scene.scaleMode = .resizeFill
        scene.onContinue = { [weak self] in
            AudioManager.shared.stopMusic()
            self?.navigationController?.popViewController(animated: true)
        }
        scene.onRestart = { [weak self] in
            self?.presentGameScene()
        }
        skView.presentScene(scene)
    }

}

