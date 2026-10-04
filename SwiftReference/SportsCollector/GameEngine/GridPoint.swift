import Foundation

struct GridPoint: Hashable {
    var row: Int
    var col: Int
}

struct GridRect {
    var minRow: Int
    var maxRow: Int
    var minCol: Int
    var maxCol: Int

    func contains(_ point: GridPoint) -> Bool {
        point.row >= minRow && point.row <= maxRow && point.col >= minCol && point.col <= maxCol
    }
}
