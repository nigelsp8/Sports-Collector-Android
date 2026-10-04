import UIKit

/// Simple level-select list. Deliberately minimal - a plain table of "Level N"
/// rows, no map/hospital theming from the original (that's a separate, much
/// bigger UI effort not in scope for this pass).
final class LevelSelectViewController: UITableViewController {

    static let levelCount = 100
    private static let cellReuseID = "LevelCell"

    /// Loaded lazily per row and cached, rather than up front, since only the
    /// rows actually scrolled into view need their thresholds computed.
    private var cachedLevels: [Int: Level] = [:]

    override func viewDidLoad() {
        super.viewDidLoad()
        title = "Sports Collector"
        tableView.rowHeight = UITableView.automaticDimension
        tableView.estimatedRowHeight = 64
        navigationItem.rightBarButtonItem = UIBarButtonItem(
            title: "Reset Stars",
            style: .plain,
            target: self,
            action: #selector(resetAllStarsTapped)
        )
    }

    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        // Best score/stars can change after returning from a played level, and
        // the level data itself can change after returning from the editor
        // (a save/reset flips which override - if any - LevelLoader resolves
        // to) - drop the cache so every row re-reads fresh each time this
        // screen comes back on screen.
        cachedLevels.removeAll()
        tableView.reloadData()
    }

    @objc private func resetAllStarsTapped() {
        let alert = UIAlertController(
            title: "Reset All Stars?",
            message: "This clears your best score and stars for every level. This cannot be undone.",
            preferredStyle: .alert
        )
        alert.addAction(UIAlertAction(title: "Cancel", style: .cancel))
        alert.addAction(UIAlertAction(title: "Reset", style: .destructive) { [weak self] _ in
            LevelProgressStore.resetAllProgress()
            self?.tableView.reloadData()
        })
        present(alert, animated: true)
    }

    override func numberOfSections(in tableView: UITableView) -> Int { 1 }

    override func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        Self.levelCount
    }

    override func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell = tableView.dequeueReusableCell(withIdentifier: Self.cellReuseID)
            ?? UITableViewCell(style: .subtitle, reuseIdentifier: Self.cellReuseID)
        cell.textLabel?.text = "Level \(indexPath.row + 1) (\(ViewController.levelOrder[indexPath.row+1]))"
        cell.detailTextLabel?.numberOfLines = 3

        var lines: [String] = []
        if let best = LevelProgressStore.bestResult(forLevel: indexPath.row + 1) {
            let stars = String(repeating: "★", count: best.stars) + String(repeating: "☆", count: 3 - best.stars)
            lines.append("\(stars)  Best Score: \(best.score)")
        } else {
            lines.append("Not yet played")
        }
        if let level = level(forRow: indexPath.row) {
            let thresholds = level.starThresholds
            lines.append("★\(thresholds.one)  ★★\(thresholds.two)  ★★★\(thresholds.three)")

            var objectiveTags: [String] = []
            if level.cells.contains(where: { $0.isActive && $0.hasJelly }) {
                objectiveTags.append("Jellies")
            }
            if level.activeBlocks.contains(where: { $0.key.isBacteria && $0.value.blocksToWin > 0 }) {
                objectiveTags.append("Bacteria")
            }
            if !objectiveTags.isEmpty {
                lines.append(objectiveTags.joined(separator: " · "))
            }
        }
        cell.detailTextLabel?.text = lines.joined(separator: "\n")
        // Row tap plays the level (`didSelectRowAt`); the detail-disclosure
        // button is the standard UIKit pairing for a secondary per-row action
        // and opens the level editor (`accessoryButtonTappedForRowWith`).
        cell.accessoryType = .detailDisclosureButton
        return cell
    }

    /// `row` is the table's 0-based index; `mapID`/`level.levelNumber` are
    /// 1-based (see `ViewController.mapID`), so the lookup into
    /// `ViewController.levelOrder` mirrors what `ViewController.viewDidLoad`
    /// does for the pushed game scene.
    private func level(forRow row: Int) -> Level? {
        if let cached = cachedLevels[row] { return cached }
        let mapID = row + 1
        let actualLevelNumber = ViewController.levelOrder[mapID]
        guard let level = try? LevelLoader.loadLevel(resourceName: "\(actualLevelNumber)", level: mapID) else {
            return nil
        }
        cachedLevels[row] = level
        return level
    }

    override func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
        tableView.deselectRow(at: indexPath, animated: true)
        let gameViewController = ViewController()
        gameViewController.mapID = indexPath.row + 1
        navigationController?.pushViewController(gameViewController, animated: true)
    }

    override func tableView(_ tableView: UITableView, accessoryButtonTappedForRowWith indexPath: IndexPath) {
        guard let level = level(forRow: indexPath.row) else { return }
        let mapID = indexPath.row + 1
        let resourceName = "\(ViewController.levelOrder[mapID])"
        navigationController?.pushViewController(
            LevelEditorViewController(level: level, mapID: mapID, resourceName: resourceName),
            animated: true
        )
    }

}
