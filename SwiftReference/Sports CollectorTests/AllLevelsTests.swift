import Testing
import Foundation
@testable import Sports_Collector

/// Sweeps every one of the 100 bundled level files - not just level 1 - to catch
/// data-shape edge cases (walls, fixed solids, bacteria, larger/smaller move
/// budgets, etc.) that level 1 alone doesn't exercise.
@Suite(.serialized)
struct AllLevelsTests {

    private static let allLevelIDs = Array(1...100)

    @Test(arguments: allLevelIDs)
    func levelDecodesWithSaneShape(id: Int) throws {
        let level = try LevelLoader.loadLevel(resourceName: "\(id)", level: id)
        #expect(level.id == id)
        #expect(level.width > 0 && level.height > 0)
        #expect(level.cells.count == level.width * level.height)
        #expect(level.movesAllowed > 0, "level \(id) has no moves allowed")

        let totalSpawnPercentage = level.activeBlocks.values.reduce(0) { $0 + $1.spawnPercentage }
        #expect(totalSpawnPercentage > 0, "level \(id) has no spawnable tile types at all")

        #expect(level.starThresholds.one > 0)
        #expect(level.starThresholds.one < level.starThresholds.two)
        #expect(level.starThresholds.two < level.starThresholds.three)
    }

    @Test(arguments: allLevelIDs)
    func levelInitializesWithEveryActiveCellPopulated(id: Int) throws {
        let level = try LevelLoader.loadLevel(resourceName: "\(id)", level: id)
        let engine = GameEngine(level: level)

        for (index, spec) in level.cells.enumerated() where spec.isActive {
            let point = GridPoint(row: index / level.width, col: index % level.width)
            #expect(engine.board[point]?.tile != nil, "level \(id) cell \(point) should have a tile at start")
        }
    }

    /// Not a strict correctness requirement (the original also just falls back to
    /// its last shuffle attempt if it can't find a perfect one) - but worth knowing
    /// about, since a level that starts with zero legal moves would be unplayable.
    @Test(arguments: allLevelIDs)
    func levelStartsWithAtLeastOneLegalMove(id: Int) throws {
        let level = try LevelLoader.loadLevel(resourceName: "\(id)", level: id)
        let engine = GameEngine(level: level)
        #expect(engine.legalSwapExists(), "level \(id) has no legal move after initial fill")
    }

    /// Regression for a real bug found via user report + confirmed by direct data
    /// analysis: levels 1 and 50 (file numbers) have jelly flagged on some
    /// *inactive* cells. Inactive cells never hold a tile, so `resolveMatches`
    /// can never clear jelly there, and `BoardNode` never renders it - counting
    /// it in the objective total silently created an un-completable "clear N
    /// jelly" objective (level 1: 32 jelly cells, all 32 inactive, so the
    /// objective could never reach zero - the level was unwinnable). The jelly
    /// objective must only count active cells.
    @Test(arguments: allLevelIDs)
    func clearJellyObjectiveOnlyCountsActiveJellyCells(id: Int) throws {
        let level = try LevelLoader.loadLevel(resourceName: "\(id)", level: id)
        let engine = GameEngine(level: level)
        let activeJellyCount = level.cells.filter { $0.hasJelly && $0.isActive }.count

        guard let jellyObjective = engine.objectives.first(where: { $0.kind == .clearJelly }) else {
            #expect(activeJellyCount == 0, "level \(id) has \(activeJellyCount) active jelly cells but no clearJelly objective")
            return
        }
        #expect(jellyObjective.total == activeJellyCount, "level \(id) jelly objective total should match active jelly cells only")
    }
}
