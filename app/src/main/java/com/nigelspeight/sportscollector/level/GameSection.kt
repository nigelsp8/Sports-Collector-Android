package com.nigelspeight.sportscollector.level

import com.nigelspeight.sportscollector.LevelProgressStore
import com.nigelspeight.sportscollector.engine.ObjectiveKind

/// The 6 sport sections the user-facing carousel pages through.
enum class GameSection(
    val displayName: String,
    /// Same art used (at full opacity) on the card, and (at 30% alpha) as the
    /// full-screen background on the section screen.
    val backgroundImageName: String,
    /// Name of the section's icon in the tile atlas (`assets/images/pills`).
    val iconTextureName: String,
) {
    SOCCER("Soccer", "bkg1", "goalkeepernet"),
    ICE_HOCKEY("Ice Hockey", "bkg2", "hockeypuck"),
    BOXING("Boxing", "bkg3", "boxingglovepurple"),
    TENNIS("Tennis", "bkg4", "tennisracquet"),
    BASKETBALL("Basketball", "bkg5", "basketballhoop"),
    RUGBY("Rugby", "bkg6", "rugbyball");

    val levelCountText: String get() = "20 Levels"

    /// 20 levels per section, laid out in section order - map IDs 1-20 are
    /// Soccer, 21-40 Ice Hockey, and so on through Rugby at 101-120.
    val levelRange: IntRange
        get() {
            val start = ordinal * 20 + 1
            return start..(start + 19)
        }

    /// Whether this section's level files actually exist yet - guards against
    /// a section whose range starts beyond `LevelOrder.LEVEL_COUNT` (relevant
    /// again whenever new sections are added ahead of their levels).
    val hasPlayableLevels: Boolean get() = levelRange.first <= LevelOrder.LEVEL_COUNT

    val previousSection: GameSection? get() = entries.getOrNull(ordinal - 1)
    val nextSection: GameSection? get() = entries.getOrNull(ordinal + 1)

    companion object {
        /// The section a given `mapID` (1-120) belongs to - the inverse of
        /// `levelRange`.
        fun forMapID(mapID: Int): GameSection? = entries.getOrNull((mapID - 1) / 20)
    }
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

    /// Whether this row actually represents a playable level (`NextLevel` or
    /// `Won`) as opposed to one of the section-boundary filler cards - used by
    /// `SectionScreen` to decide whether any level is showing in the list at all.
    val isLevelRow: Boolean
        get() = this is NextLevel || this is Won
}

/// The two lines of copy on the launch "pundit" screen.
data class PunditCopy(val speaker: String, val message: String)

object SectionRows {
    /// What the launch pundit says - about whichever section `resumeSection`
    /// will land on, so the copy always refers to the card the player is about
    /// to see. Until any level has been won there's nothing to report on, so
    /// generic launch copy is shown instead.
    fun punditCopy(lastPlayedMapID: Int?, bestResult: (Int) -> LevelProgressStore.Result?): PunditCopy {
        val hasAnyProgress = GameSection.entries.any { wonCount(it, bestResult) > 0 }
        if (!hasAnyProgress) return PunditCopy("Olivia says...", "Collect the sports cards one at a time")

        val section = resumeSection(lastPlayedMapID, bestResult)
        val message = when (wonCount(section, bestResult)) {
            0 -> "Let's make a start on collecting this card"
            in 1..6 -> "Great start to collecting this card"
            in 7..14 -> "Keep up the great work"
            else -> "Nearly there, WOW!"
        }
        return PunditCopy("${section.displayName} card", message)
    }

    /// Section to land on at launch: wherever the player last played, falling
    /// back to their progress frontier (the earliest section with an unplayed
    /// level) if nothing's been recorded yet - e.g. a fresh install, or
    /// upgrading from a build that predates this tracking.
    fun resumeSection(lastPlayedMapID: Int?, bestResult: (Int) -> LevelProgressStore.Result?): GameSection =
        lastPlayedMapID?.let(GameSection::forMapID) ?: frontierSection(bestResult)

    /// Earliest playable section that isn't fully won yet. If every playable
    /// section is complete (or none exist yet), lands on the last playable one
    /// so the user sees its "advance"/"completed" card.
    fun frontierSection(bestResult: (Int) -> LevelProgressStore.Result?): GameSection {
        for (section in GameSection.entries) {
            if (!section.hasPlayableLevels) break
            if (wonCount(section, bestResult) < section.levelRange.count()) return section
        }
        return GameSection.entries.lastOrNull { it.hasPlayableLevels } ?: GameSection.SOCCER
    }

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
