import Testing
@testable import Sports_Collector

struct StarThresholdTests {

    @Test func validJSONTripleIsUsedUntouched() {
        let meta = LevelMeta(numberofmoves: 24, onestarscore: 10000, twostarscore: 15000, threestarscore: 20000,
                              levelgridsize: 9, backgroundimagefilename: "grad_1.jpg")
        let resolved = StarThreshold.resolve(meta: meta)
        #expect(resolved == (10000, 15000, 20000))
    }

    @Test func zeroedTripleFallsBackAndStaysIncreasing() {
        let meta = LevelMeta(numberofmoves: 24, onestarscore: 0, twostarscore: 0, threestarscore: 0,
                              levelgridsize: 9, backgroundimagefilename: "grad_3.jpg")
        let resolved = StarThreshold.resolve(meta: meta)
        #expect(resolved.one > 0)
        #expect(resolved.one < resolved.two)
        #expect(resolved.two < resolved.three)
    }

    @Test func equalTripleFallsBack() {
        let meta = LevelMeta(numberofmoves: 15, onestarscore: 15, twostarscore: 15, threestarscore: 15,
                              levelgridsize: 9, backgroundimagefilename: "grad_3.jpg")
        let resolved = StarThreshold.resolve(meta: meta)
        #expect(resolved.one < resolved.two)
        #expect(resolved.two < resolved.three)
    }

    @Test func fallbackStaysIncreasingAcrossSmallMoveBudgets() {
        for moves in 1...5 {
            let (one, two, three) = StarThreshold.fallback(movesAllowed: moves)
            #expect(one > 0)
            #expect(one < two)
            #expect(two < three)
        }
    }

    @Test func fallbackScalesWithMoveBudget() {
        let small = StarThreshold.fallback(movesAllowed: 10)
        let large = StarThreshold.fallback(movesAllowed: 60)
        #expect(large.one > small.one)
        #expect(large.three > small.three)
    }
}
