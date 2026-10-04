package com.nigelspeight.sportscollector.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.nigelspeight.sportscollector.AppServices
import com.nigelspeight.sportscollector.level.Level
import com.nigelspeight.sportscollector.level.LevelOrder

/// Simple level-select list. Deliberately minimal - a plain list of "Level N"
/// rows, no map theming from the original. Row tap plays the level; the edit
/// button opens the level editor.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LevelSelectScreen(onPlay: (mapID: Int) -> Unit, onEdit: (mapID: Int, level: Level) -> Unit) {
    // Recreated every time this screen re-enters composition (e.g. returning from
    // a played level or the editor), so best scores and edited level data are
    // always re-read fresh. Loaded lazily per row, since only rows actually
    // scrolled into view need their thresholds computed.
    val cachedLevels = remember { HashMap<Int, Level?>() }
    var progressVersion by remember { mutableIntStateOf(0) }
    var confirmReset by remember { mutableStateOf(false) }

    fun level(row: Int): Level? = cachedLevels.getOrPut(row) {
        runCatching { AppServices.loadLevel(row + 1) }.getOrNull()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sports Collector") },
                actions = { TextButton(onClick = { confirmReset = true }) { Text("Reset Stars") } },
            )
        },
    ) { padding ->
        LazyColumn(contentPadding = padding) {
            items(LevelOrder.LEVEL_COUNT) { row ->
                val mapID = row + 1
                val lines = mutableListOf<String>()
                // Keyed on `progressVersion` so rows re-read progress after a reset.
                val best = progressVersion.let { AppServices.progress.bestResult(mapID) }
                if (best != null) {
                    val stars = "★".repeat(best.stars) + "☆".repeat(3 - best.stars)
                    lines += "$stars  Best Score: ${best.score}"
                } else {
                    lines += "Not yet played"
                }
                val level = level(row)
                if (level != null) {
                    val t = level.starThresholds
                    lines += "★${t.one}  ★★${t.two}  ★★★${t.three}"

                    val objectiveTags = mutableListOf<String>()
                    if (level.cells.any { it.isActive && it.hasJelly }) objectiveTags += "Jellies"
                    if (level.activeBlocks.any { (type, spec) -> type.isBacteria && spec.blocksToWin > 0 }) {
                        objectiveTags += "Bacteria"
                    }
                    if (objectiveTags.isNotEmpty()) lines += objectiveTags.joinToString(" · ")
                }

                ListItem(
                    headlineContent = { Text("Level $mapID (${LevelOrder.order[mapID]})") },
                    supportingContent = { Text(lines.joinToString("\n"), style = MaterialTheme.typography.bodySmall) },
                    trailingContent = {
                        if (level != null) {
                            IconButton(onClick = { onEdit(mapID, level) }) {
                                Icon(Icons.Filled.Edit, contentDescription = "Edit level $mapID")
                            }
                        }
                    },
                    modifier = Modifier.clickable { onPlay(mapID) },
                )
                HorizontalDivider()
            }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset All Stars?") },
            text = { Text("This clears your best score and stars for every level. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    AppServices.progress.resetAllProgress()
                    progressVersion++
                    confirmReset = false
                }) { Text("Reset", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
        )
    }
}
