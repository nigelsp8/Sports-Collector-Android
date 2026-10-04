import Foundation

enum GameEvent: Equatable {
    case introFadeInStarted(duration: TimeInterval)
    case tilePlaced(GridPoint, TileType)
    case swapRejected(GridPoint, GridPoint)
    case swapAnimated(GridPoint, GridPoint, committed: Bool)
    case tileMoved(from: GridPoint, to: GridPoint, kind: MoveKind)
    case tileRemoved(GridPoint, TileType, scoreAwarded: Int)
    case comboPillDowngraded(GridPoint, from: TileType, to: TileType)
    case solidDamaged(GridPoint, from: TileType, to: TileType?)
    case wallDestroyed(GridPoint, GridPoint)
    case jellyCleared(GridPoint)
    case objectiveProgressed(ObjectiveKind, remaining: Int)
    case objectiveFlyCompleted(ObjectiveKind, bonusScore: Int)
    case bacteriaFellOff(GridPoint)
    case scoreChanged(Int)
    case movesChanged(Int)
    case hintSuggested(GridPoint, GridPoint)
    case noMoreMovesShuffleStarted
    case noMoreMovesShuffleFinished
    case tutorialUnlocked
    case won(score: Int, stars: Int)
    case lost
}
