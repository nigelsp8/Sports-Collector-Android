package com.nigelspeight.sportscollector.engine

/// Bitmask of the six tablet colors. A tile "matches" another tile when their
/// color bits intersect, which is what lets a combo pill (carrying one color bit)
/// match plain tablets of that color.
@JvmInline
value class TileColor(val rawValue: Int) {
    companion object {
        val PINK = TileColor(1 shl 0)
        val ORANGE = TileColor(1 shl 1)
        val YELLOW = TileColor(1 shl 2)
        val GREEN = TileColor(1 shl 3)
        val PURPLE = TileColor(1 shl 4)
        val BLUE = TileColor(1 shl 5)
    }
}

/// A tile's content type. Raw values match the original game's `blockcontained + 1`
/// encoding from level JSON, so `TileType.fromBlockContained` is a direct decode.
enum class TileType(val rawValue: Int) {
    PINK_TABLET(1),
    ORANGE_TABLET(2),
    YELLOW_TABLET(3),
    GREEN_TABLET(4),
    PURPLE_TABLET(5),
    BLUE_TABLET(6),

    ORANGE_BLUE_PILL(7),
    PINK_GREEN_PILL(8),
    PURPLE_YELLOW_PILL(9),

    PINK_BACTERIA(10),
    ORANGE_BACTERIA(11),
    YELLOW_BACTERIA(12),
    GREEN_BACTERIA(13),
    PURPLE_BACTERIA(14),
    BLUE_BACTERIA(15),

    SOLID_STAGE_1(16),
    SOLID_STAGE_2(17),
    SOLID_STAGE_3(18);

    /// The color bit this tile matches against. Combo pills carry exactly one of
    /// their two colors; bacteria and solids never color-match.
    val colorBit: TileColor?
        get() = when (this) {
            PINK_TABLET, PINK_GREEN_PILL -> TileColor.PINK
            ORANGE_TABLET, ORANGE_BLUE_PILL -> TileColor.ORANGE
            YELLOW_TABLET -> TileColor.YELLOW
            GREEN_TABLET -> TileColor.GREEN
            PURPLE_TABLET, PURPLE_YELLOW_PILL -> TileColor.PURPLE
            BLUE_TABLET -> TileColor.BLUE
            else -> null
        }

    val isBacteria: Boolean
        get() = when (this) {
            PINK_BACTERIA, ORANGE_BACTERIA, YELLOW_BACTERIA,
            GREEN_BACTERIA, PURPLE_BACTERIA, BLUE_BACTERIA -> true
            else -> false
        }

    val isSolid: Boolean
        get() = this == SOLID_STAGE_1 || this == SOLID_STAGE_2 || this == SOLID_STAGE_3

    val isComboPill: Boolean
        get() = this == ORANGE_BLUE_PILL || this == PINK_GREEN_PILL || this == PURPLE_YELLOW_PILL

    /// The plain tablet a combo pill downgrades to when matched. Downgrading
    /// replaces the tile in place rather than removing it.
    val downgradedForm: TileType?
        get() = when (this) {
            ORANGE_BLUE_PILL -> ORANGE_TABLET
            PINK_GREEN_PILL -> PINK_TABLET
            PURPLE_YELLOW_PILL -> PURPLE_TABLET
            else -> null
        }

    /// The next damage stage for a solid blocker. `null` for `SOLID_STAGE_3` means
    /// the solid is fully destroyed (cell clears) rather than advancing further.
    val nextDamageStage: TileType?
        get() = when (this) {
            SOLID_STAGE_1 -> SOLID_STAGE_2
            SOLID_STAGE_2 -> SOLID_STAGE_3
            else -> null
        }

    companion object {
        fun fromRawValue(rawValue: Int): TileType? = entries.firstOrNull { it.rawValue == rawValue }

        /// `blockContained` is the level JSON's 0-based type index, or -1 for "no fixed content".
        fun fromBlockContained(blockContained: Int): TileType? =
            if (blockContained < 0) null else fromRawValue(blockContained + 1)
    }
}
