import Foundation

protocol TileRandomSource {
    mutating func nextSpawnTile(from distribution: [(TileType, Int)]) -> TileType
    mutating func shuffled<T>(_ items: [T]) -> [T]
}

struct SystemTileRandomSource: TileRandomSource {
    mutating func nextSpawnTile(from distribution: [(TileType, Int)]) -> TileType {
        Self.pick(from: distribution) { Int.random(in: 0..<$0) }
    }

    mutating func shuffled<T>(_ items: [T]) -> [T] {
        items.shuffled()
    }

    static func pick(from distribution: [(TileType, Int)], roll rollProvider: (Int) -> Int) -> TileType {
        let total = distribution.reduce(0) { $0 + $1.1 }
        guard total > 0, let first = distribution.first?.0 else { return .pinkTablet }
        var roll = rollProvider(total)
        for (type, weight) in distribution {
            if roll < weight { return type }
            roll -= weight
        }
        return distribution.last?.0 ?? first
    }
}
