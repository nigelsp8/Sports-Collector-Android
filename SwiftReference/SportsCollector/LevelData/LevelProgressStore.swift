import Foundation

/// Persists each level's best win result (highest score, with its matching star
/// count) across launches. Keyed by `Level.mapNumber` - the same 0-based slot
/// index `LevelSelectViewController`'s row and `ViewController.mapID` use, not the
/// shuffled on-disk level file number (`ViewController.levelOrder`).
struct LevelProgressStore {
    struct Result: Equatable {
        let score: Int
        let stars: Int
    }

    private static let keyPrefix = "levelProgress."

    static func bestResult(forLevel mapNumber: Int, defaults: UserDefaults = .standard) -> Result? {
        guard let dict = defaults.dictionary(forKey: key(for: mapNumber)) as? [String: Int],
              let score = dict["score"], let stars = dict["stars"] else { return nil }
        return Result(score: score, stars: stars)
    }

    /// Records a win, keeping the previous best if it already had an equal or
    /// higher score. Returns whichever result ends up stored.
    @discardableResult
    static func recordWin(mapNumber: Int, score: Int, stars: Int, defaults: UserDefaults = .standard) -> Result {
        let candidate = Result(score: score, stars: stars)
        if let existing = bestResult(forLevel: mapNumber, defaults: defaults), existing.score >= candidate.score {
            return existing
        }
        defaults.set(["score": candidate.score, "stars": candidate.stars], forKey: key(for: mapNumber))
        return candidate
    }

    /// Clears every recorded best score/stars, regardless of level count.
    static func resetAllProgress(defaults: UserDefaults = .standard) {
        let keys = defaults.dictionaryRepresentation().keys.filter { $0.hasPrefix(keyPrefix) }
        keys.forEach { defaults.removeObject(forKey: $0) }
    }

    private static func key(for mapNumber: Int) -> String {
        keyPrefix + String(mapNumber)
    }
}
