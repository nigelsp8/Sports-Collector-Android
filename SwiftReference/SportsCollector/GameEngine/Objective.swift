import Foundation

enum ObjectiveKind: Hashable {
    case collect(TileType)
    case clearJelly
}

struct Objective {
    let kind: ObjectiveKind
    let total: Int
    private(set) var remaining: Int

    init(kind: ObjectiveKind, total: Int) {
        self.kind = kind
        self.total = total
        self.remaining = total
    }

    var isComplete: Bool { remaining <= 0 }

    mutating func decrement(by amount: Int = 1) {
        remaining = max(0, remaining - amount)
    }
}
