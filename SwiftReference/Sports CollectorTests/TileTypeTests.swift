import Testing
@testable import Sports_Collector

struct TileTypeTests {

    @Test func blockContainedDecodesToExpectedType() {
        #expect(TileType(blockContained: -1) == nil)
        #expect(TileType(blockContained: 0) == .pinkTablet)
        #expect(TileType(blockContained: 5) == .blueTablet)
        #expect(TileType(blockContained: 6) == .orangeBluePill)
        #expect(TileType(blockContained: 8) == .purpleYellowPill)
        #expect(TileType(blockContained: 9) == .pinkBacteria)
        #expect(TileType(blockContained: 14) == .blueBacteria)
        #expect(TileType(blockContained: 15) == .solidStage1)
    }

    @Test func comboPillsCarryOneColorAndDowngradeCorrectly() {
        #expect(TileType.orangeBluePill.colorBit == .orange)
        #expect(TileType.orangeBluePill.downgradedForm == .orangeTablet)

        #expect(TileType.pinkGreenPill.colorBit == .pink)
        #expect(TileType.pinkGreenPill.downgradedForm == .pinkTablet)

        #expect(TileType.purpleYellowPill.colorBit == .purple)
        #expect(TileType.purpleYellowPill.downgradedForm == .purpleTablet)

        #expect(TileType.pinkTablet.downgradedForm == nil)
    }

    @Test func bacteriaAndSolidsNeverColorMatch() {
        for type in TileType.allCases where type.isBacteria || type.isSolid {
            #expect(type.colorBit == nil)
        }
    }

    @Test func solidDamageStagesProgressThenDestroy() {
        #expect(TileType.solidStage1.nextDamageStage == .solidStage2)
        #expect(TileType.solidStage2.nextDamageStage == .solidStage3)
        #expect(TileType.solidStage3.nextDamageStage == nil)
    }
}
