import Foundation

/// Star-rating cutoffs. The original game parsed `onestarscore`/`twostarscore`/
/// `threestarscore` from level JSON but never actually used them - it derived
/// thresholds from a now-defunct backend's aggregate win stats instead. Fresh data
/// analysis of the shipped levels found the JSON fields are only sane (strictly
/// increasing, positive) on 3 of 100 levels; the rest are placeholder/zeroed data.
/// This type uses the JSON values when they're sane, and falls back to a formula
/// derived from the level's move budget otherwise.
enum StarThreshold {
    static let baselineTileScore = 375
    static let tilesPerAverageMatch = 3
    static let oneStarMultiplier = 0.5
    static let twoStarMultiplier = 0.8
    static let threeStarMultiplier = 1.1
    static let roundingUnit = 500

    static func resolve(meta: LevelMeta) -> (one: Int, two: Int, three: Int) {
        let jsonTriple = (meta.onestarscore, meta.twostarscore, meta.threestarscore)
        if isValidTriple(jsonTriple) {
            return jsonTriple
        }
        return fallback(movesAllowed: meta.numberofmoves)
    }

    static func isValidTriple(_ triple: (Int, Int, Int)) -> Bool {
        triple.0 > 0 && triple.0 < triple.1 && triple.1 < triple.2
    }

    /// `par` assumes every move nets one basic 3-tile match. Each threshold is
    /// rounded to the nearest `roundingUnit` for a clean HUD number, then nudged up
    /// if rounding collapsed it into its neighbor - guaranteeing strict monotonic
    /// increase for any `movesAllowed >= 1`.
    static func fallback(movesAllowed: Int) -> (one: Int, two: Int, three: Int) {
        let par = Double(max(movesAllowed, 1) * tilesPerAverageMatch * baselineTileScore)

        func rounded(_ multiplier: Double) -> Int {
            let value = par * multiplier
            return max(roundingUnit, Int((value / Double(roundingUnit)).rounded()) * roundingUnit)
        }

        let one = rounded(oneStarMultiplier)
        let two = max(rounded(twoStarMultiplier), one + roundingUnit)
        let three = max(rounded(threeStarMultiplier), two + roundingUnit)
        return (one, two, three)
    }

    static func stars(forScore score: Int, thresholds: (one: Int, two: Int, three: Int)) -> Int {
        if score >= thresholds.three { return 3 }
        if score >= thresholds.two { return 2 }
        if score >= thresholds.one { return 1 }
        return 0
    }
}
