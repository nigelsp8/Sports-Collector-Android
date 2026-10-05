package com.nigelspeight.sportscollector.level

import com.nigelspeight.sportscollector.LevelProgressStore
import com.nigelspeight.sportscollector.engine.ObjectiveKind

/// The 6 sport sections the user-facing carousel pages through.
enum class GameSection(
    val displayName: String,
    /// Same art used (at full opacity) on the card, and (at 30% alpha) as the
    /// full-screen background - the level backgrounds `BackgroundNode` uses.
    val backgroundImageName: String,
    /// Name of the section's icon in the tile atlas (`assets/images/pills`).
    val iconTextureName: String,
) {
    SOCCER("Soccer", "bkg1b", "soccerballcoloured"),
    ICE_HOCKEY("Ice Hockey", "bkg2b", "hockeypuck"),
    BOXING("Boxing", "bkg3b", "boxingglove"),
    TENNIS("Tennis", "bkg4b", "tennisracquet"),
    BASKETBALL("Basketball", "bkg5b", "basketball"),
    RUGBY("Rugby", "bkg6b", "rugbyball");

    val levelCountText: String get() = "20 Levels"

    /// 20 levels per section, laid out in section order - map IDs 1-20 are
    /// Soccer, 21-40 Ice Hockey, and so on through Rugby at 101-120 (that last
    /// range doesn't have levels yet - see `hasPlayableLevels`).
    val levelRange: IntRange
        get() {
            val start = ordinal * 20 + 1
            return start..(start + 19)
        }

    /// Whether this section's level files actually exist yet. Only
    /// `LevelOrder.LEVEL_COUNT` levels have been created so far, so a section
    /// whose range starts beyond that (currently just Rugby) has none to show.
    val hasPlayableLevels: Boolean get() = levelRange.first <= LevelOrder.LEVEL_COUNT

    val previousSection: GameSection? get() = entries.getOrNull(ordinal - 1)
    val nextSection: GameSection? get() = entries.getOrNull(ordinal + 1)
}

/// One row in the section screen's level list: either a playable level, or one
/// of the non-level "filler" cards shown at section boundaries.
sealed interface LevelListRow {
    /// The next level to play - not yet won, so no stars/score to show.
    /// Rendered wider with its objective icons to draw focus as the player's
    /// priority card.
    data class NextLevel(val mapID: Int, val objectiveKinds: List<ObjectiveKind>) : LevelListRow
    data class Won(val mapID: Int, val result: LevelProgressStore.Result) : LevelListRow
    data class AdvanceToNextSection(val next: GameSection) : LevelListRow
    data object GameCompleted : LevelListRow
    data object ComingSoon : LevelListRow
    data class Locked(val previousSection: GameSection) : LevelListRow
}

object SectionRows {
    /// Number of consecutive wins from the start of `section`'s range - the
    /// player's progress frontier, since levels are meant to be played in order
    /// (a count equal to the full range means the section is fully complete).
    fun wonCount(section: GameSection, bestResult: (Int) -> LevelProgressStore.Result?): Int {
        var count = 0
        for (mapID in section.levelRange) {
            if (bestResult(mapID) == null) break
            count++
        }
        return count
    }

    /// Top row is the next level to play (or, once the whole section is won, a
    /// filler card pointing at the next section / congratulating the player).
    /// Below it, every already-won level in the section appears in reverse
    /// order - e.g. 3 wins shows [4, 3, 2, 1].
    ///
    /// Sections can be peeked at freely, but a section's levels stay locked
    /// until the previous section is fully won - only its card list is gated.
    fun build(
        section: GameSection,
        bestResult: (Int) -> LevelProgressStore.Result?,
        objectiveKinds: (Int) -> List<ObjectiveKind>,
    ): List<LevelListRow> {
        val previous = section.previousSection
        if (previous != null && wonCount(previous, bestResult) < previous.levelRange.count()) {
            return listOf(LevelListRow.Locked(previous))
        }
        if (!section.hasPlayableLevels) return listOf(LevelListRow.ComingSoon)

        val wonCount = wonCount(section, bestResult)
        val rows = mutableListOf<LevelListRow>()
        if (wonCount == section.levelRange.count()) {
            val next = section.nextSection
            rows += if (next != null) LevelListRow.AdvanceToNextSection(next) else LevelListRow.GameCompleted
        } else {
            val nextMapID = section.levelRange.first + wonCount
            rows += LevelListRow.NextLevel(nextMapID, objectiveKinds(nextMapID))
        }

        val lastWonMapID = section.levelRange.first + wonCount - 1
        for (mapID in lastWonMapID downTo section.levelRange.first) {
            val result = bestResult(mapID) ?: continue
            rows += LevelListRow.Won(mapID, result)
        }
        return rows
    }
}
