package com.nigelspeight.sportscollector.ui

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.nigelspeight.sportscollector.AppServices
import com.nigelspeight.sportscollector.engine.GridPoint
import com.nigelspeight.sportscollector.engine.StarThreshold
import com.nigelspeight.sportscollector.engine.TileType
import com.nigelspeight.sportscollector.game.Textures
import com.nigelspeight.sportscollector.level.ActiveBlockSpec
import com.nigelspeight.sportscollector.level.EditableLevel
import com.nigelspeight.sportscollector.level.Level
import com.nigelspeight.sportscollector.level.LevelEncoder
import com.nigelspeight.sportscollector.level.LevelLoader
import com.nigelspeight.sportscollector.level.StarThresholds
import java.io.File

/// What a tap on an editor grid cell does. `TILE` additionally needs a selected
/// `TileType?` (null = "clear fixed tile").
enum class EditorTool(val title: String) {
    ACTIVE("Active"),
    WALL_BELOW("Wall Below"),
    WALL_RIGHT("Wall Right"),
    SPAWN("Spawn"),
    EXIT("Exit"),
    JELLY("Jelly"),
    TILE("Tile"),
}

/// UI-facing display names for the level editor's tile palette/objectives list.
val TileType.editorDisplayName: String
    get() = when (this) {
        TileType.PINK_TABLET -> "Pink Tablet"
        TileType.ORANGE_TABLET -> "Orange Tablet"
        TileType.YELLOW_TABLET -> "Yellow Tablet"
        TileType.GREEN_TABLET -> "Green Tablet"
        TileType.PURPLE_TABLET -> "Purple Tablet"
        TileType.BLUE_TABLET -> "Blue Tablet"
        TileType.ORANGE_BLUE_PILL -> "Orange/Blue Pill"
        TileType.PINK_GREEN_PILL -> "Pink/Green Pill"
        TileType.PURPLE_YELLOW_PILL -> "Purple/Yellow Pill"
        TileType.PINK_BACTERIA -> "Pink Bacteria"
        TileType.ORANGE_BACTERIA -> "Orange Bacteria"
        TileType.YELLOW_BACTERIA -> "Yellow Bacteria"
        TileType.GREEN_BACTERIA -> "Green Bacteria"
        TileType.PURPLE_BACTERIA -> "Purple Bacteria"
        TileType.BLUE_BACTERIA -> "Blue Bacteria"
        TileType.SOLID_STAGE_1 -> "Solid (Stage 1)"
        TileType.SOLID_STAGE_2 -> "Solid (Stage 2)"
        TileType.SOLID_STAGE_3 -> "Solid (Stage 3)"
    }

/// Editor state lives outside composition (held by the back stack entry), so an
/// unsaved edit survives a play-test round trip.
class LevelEditorState(level: Level, val mapID: Int, val resourceName: String) {
    var level by mutableStateOf(EditableLevel(level))
    var tool by mutableStateOf(EditorTool.ACTIVE)
    var selectedTile by mutableStateOf<TileType?>(null)
    /// Bumped whenever the level is replaced wholesale (reset, fallback
    /// thresholds), so text fields re-seed from the new values.
    var fieldsRevision by mutableIntStateOf(0)

    fun cellTapped(point: GridPoint) {
        val spec = level[point]
        val updated = when (tool) {
            EditorTool.ACTIVE -> spec.copy(isActive = !spec.isActive)
            EditorTool.WALL_BELOW -> spec.copy(hasWallBelow = !spec.hasWallBelow)
            EditorTool.WALL_RIGHT -> spec.copy(hasWallRight = !spec.hasWallRight)
            EditorTool.SPAWN -> spec.copy(isSpawnPoint = !spec.isSpawnPoint)
            EditorTool.EXIT -> spec.copy(isExit = !spec.isExit)
            EditorTool.JELLY -> spec.copy(hasJelly = !spec.hasJelly)
            EditorTool.TILE -> spec.copy(fixedTile = selectedTile)
        }
        level = level.withCell(point, updated)
    }
}

private val BACKGROUND_FILENAMES = listOf("grad_1.jpg", "grad_2.jpg", "grad_3.jpg")
private val BACKGROUND_TITLES = listOf("Blue", "Orange", "Green")

/// Root level editor screen: a board grid, a tool/tile palette, level-settings
/// fields, and an objectives list, composed in one scrollable column.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LevelEditorScreen(state: LevelEditorState, onBack: () -> Unit, onPlayTest: (Level) -> Unit) {
    val context = LocalContext.current
    var alert by remember { mutableStateOf<Pair<String, String>?>(null) }
    var menuOpen by remember { mutableStateOf(false) }

    fun save() {
        alert = try {
            AppServices.editStore.save(state.level.makeLevel(), state.resourceName)
            "Saved" to "This level's edits will now load in place of the original."
        } catch (e: Exception) {
            "Save Failed" to (e.message ?: e.toString())
        }
    }

    fun reset() {
        alert = try {
            AppServices.editStore.resetToOriginal(state.resourceName)
            val reloaded = LevelLoader.loadLevel(state.resourceName, state.mapID, AppServices.levelBundle, AppServices.editStore)
            state.level = EditableLevel(reloaded)
            state.fieldsRevision++
            "Reset" to "Reverted to the original level data."
        } catch (e: Exception) {
            "Reset Failed" to (e.message ?: e.toString())
        }
    }

    fun export() {
        try {
            val dir = File(context.cacheDir, "exports").apply { mkdirs() }
            val file = File(dir, "${state.resourceName}.txt")
            file.writeText(LevelEncoder.encode(state.level.makeLevel()))
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(send, "Export Level"))
        } catch (e: Exception) {
            alert = "Export Failed" to (e.message ?: e.toString())
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Level ${state.mapID}") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { onPlayTest(state.level.makeLevel()) }) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "Play test")
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(text = { Text("Save") }, onClick = { menuOpen = false; save() })
                            DropdownMenuItem(
                                text = { Text("Reset to Original", color = MaterialTheme.colorScheme.error) },
                                onClick = { menuOpen = false; reset() },
                            )
                            DropdownMenuItem(text = { Text("Export") }, onClick = { menuOpen = false; export() })
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                EditorGrid(state.level, onCellTapped = state::cellTapped)
            }
            EditorPalette(
                tool = state.tool,
                selectedTile = state.selectedTile,
                onToolChanged = { state.tool = it },
                onTileSelected = { state.selectedTile = it },
            )
            HorizontalDivider()
            EditorSettingsSection(state)
            HorizontalDivider()
            EditorObjectivesSection(state)
        }
    }

    alert?.let { (title, message) ->
        AlertDialog(
            onDismissRequest = { alert = null },
            title = { Text(title) },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { alert = null }) { Text("OK") } },
        )
    }
}

/// The editable board grid: one tappable square per cell.
@Composable
private fun EditorGrid(level: EditableLevel, onCellTapped: (GridPoint) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        for (row in 0 until level.height) {
            Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                for (col in 0 until level.width) {
                    val point = GridPoint(row, col)
                    val spec = level[point]
                    val background = when {
                        !spec.isActive -> Color.Black
                        spec.hasJelly -> Color(0x4D007AFF)
                        else -> Color.White
                    }
                    val border = if (spec.isActive) Color.Black else Color.DarkGray
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(background)
                            .border(0.5.dp, border)
                            .clickable { onCellTapped(point) },
                    ) {
                        if (spec.isActive) {
                            spec.fixedTile?.let { TileImage(it, Modifier.fillMaxSize()) }
                            if (spec.hasWallBelow) {
                                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp).background(ORANGE))
                            }
                            if (spec.hasWallRight) {
                                Box(Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(3.dp).background(ORANGE))
                            }
                            if (spec.isSpawnPoint) Marker(Color(0xFF34C759), Modifier.align(Alignment.TopStart))
                            if (spec.isExit) Marker(Color(0xFFFF3B30), Modifier.align(Alignment.TopEnd))
                        }
                    }
                }
            }
        }
    }
}

private val ORANGE = Color(0xFFFF9500)

/// Green spawn marker at top-start, red exit marker at top-end.
@Composable
private fun Marker(color: Color, modifier: Modifier) {
    Box(modifier.padding(2.dp).size(6.dp).background(color, CircleShape))
}

@Composable
private fun TileImage(type: TileType, modifier: Modifier = Modifier) {
    val bitmap = remember(type) { Textures.tile(type).bitmap.asImageBitmap() }
    Image(bitmap, contentDescription = type.editorDisplayName, modifier = modifier, contentScale = ContentScale.Fit)
}

/// Tool-mode picker plus, only in `TILE` mode, a horizontal palette of every
/// `TileType` and a "None" button for clearing a cell's fixed tile.
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditorPalette(
    tool: EditorTool,
    selectedTile: TileType?,
    onToolChanged: (EditorTool) -> Unit,
    onTileSelected: (TileType?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            EditorTool.entries.forEach { entry ->
                FilterChip(selected = entry == tool, onClick = { onToolChanged(entry) }, label = { Text(entry.title) })
            }
        }
        if (tool == EditorTool.TILE) {
            val options: List<TileType?> = listOf(null) + TileType.entries
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.height(48.dp)) {
                items(options) { type ->
                    val isSelected = type == selectedTile
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .border(
                                BorderStroke(
                                    if (isSelected) 3.dp else 1.dp,
                                    if (isSelected) Color(0xFF007AFF) else Color(0xFFC7C7CC),
                                ),
                                RoundedCornerShape(4.dp),
                            )
                            .clickable { onTileSelected(type) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (type == null) Text("None", fontSize = 11.sp) else TileImage(type, Modifier.padding(3.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun NumberField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier, label: String? = null) {
    OutlinedTextField(
        value = value,
        onValueChange = { text -> onValueChange(text.filter(Char::isDigit)) },
        singleLine = true,
        label = label?.let { { Text(it) } },
        textStyle = MaterialTheme.typography.bodyMedium.copy(textAlign = TextAlign.End),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        modifier = modifier,
    )
}

@Composable
private fun LabeledNumberRow(title: String, value: String, onValueChange: (String) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(110.dp))
        NumberField(value, onValueChange, Modifier.weight(1f))
    }
}

/// Moves-allowed, star thresholds (with a live validity check and a one-tap
/// fallback derived from `StarThreshold.fallback`), and a background picker
/// limited to the three gradient filenames the game knows about.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorSettingsSection(state: LevelEditorState) {
    val revision = state.fieldsRevision
    var movesText by remember(revision) { mutableStateOf(state.level.movesAllowed.toString()) }
    var oneText by remember(revision) { mutableStateOf(state.level.starThresholds.one.toString()) }
    var twoText by remember(revision) { mutableStateOf(state.level.starThresholds.two.toString()) }
    var threeText by remember(revision) { mutableStateOf(state.level.starThresholds.three.toString()) }

    fun currentThresholds() = StarThresholds(oneText.toIntOrNull() ?: 0, twoText.toIntOrNull() ?: 0, threeText.toIntOrNull() ?: 0)
    fun thresholdsEdited() {
        state.level = state.level.copy(starThresholds = currentThresholds())
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LabeledNumberRow("Moves Allowed", movesText) {
            movesText = it
            state.level = state.level.copy(movesAllowed = it.toIntOrNull() ?: state.level.movesAllowed)
        }
        LabeledNumberRow("1 Star", oneText) { oneText = it; thresholdsEdited() }
        LabeledNumberRow("2 Star", twoText) { twoText = it; thresholdsEdited() }
        LabeledNumberRow("3 Star", threeText) { threeText = it; thresholdsEdited() }

        if (!StarThreshold.isValidTriple(currentThresholds())) {
            Text(
                "Thresholds must be positive and strictly increasing, or gameplay falls back to a computed value.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        TextButton(onClick = {
            val fallback = StarThreshold.fallback(state.level.movesAllowed)
            state.level = state.level.copy(starThresholds = fallback)
            state.fieldsRevision++
        }) { Text("Use Fallback Thresholds") }

        Text("Background", style = MaterialTheme.typography.titleSmall)
        val selectedIndex = BACKGROUND_FILENAMES.indexOf(state.level.backgroundImageFilename)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            BACKGROUND_TITLES.forEachIndexed { index, title ->
                SegmentedButton(
                    selected = index == selectedIndex,
                    onClick = { state.level = state.level.copy(backgroundImageFilename = BACKGROUND_FILENAMES[index]) },
                    shape = SegmentedButtonDefaults.itemShape(index, BACKGROUND_TITLES.size),
                ) { Text(title) }
            }
        }
    }
}

/// One row per `activeBlocks` entry (blocks-to-win / spawn-percentage, plus
/// delete), and an "Add Objective" icon picker for `TileType`s not yet present.
@Composable
private fun EditorObjectivesSection(state: LevelEditorState) {
    var picking by remember { mutableStateOf(false) }
    val activeBlocks = state.level.activeBlocks

    fun setBlocks(blocks: Map<TileType, ActiveBlockSpec>) {
        state.level = state.level.copy(activeBlocks = blocks.toSortedMap(compareBy { it.rawValue }))
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (type in activeBlocks.keys.sortedBy { it.rawValue }) key(type) {
            val spec = activeBlocks[type] ?: ActiveBlockSpec(0, 0)
            var winText by remember(type, state.fieldsRevision) { mutableStateOf(spec.blocksToWin.toString()) }
            var percentText by remember(type, state.fieldsRevision) { mutableStateOf(spec.spawnPercentage.toString()) }

            fun applyEdit() {
                setBlocks(
                    state.level.activeBlocks + (type to ActiveBlockSpec(winText.toIntOrNull() ?: 0, percentText.toIntOrNull() ?: 0)),
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TileImage(type, Modifier.size(32.dp))
                NumberField(winText, { winText = it; applyEdit() }, Modifier.weight(1f), label = "To win")
                NumberField(percentText, { percentText = it; applyEdit() }, Modifier.weight(1f), label = "Spawn %")
                IconButton(onClick = { setBlocks(state.level.activeBlocks - type) }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Remove ${type.editorDisplayName}")
                }
            }
        }

        TextButton(onClick = { picking = true }) { Text("Add Objective") }
    }

    if (picking) {
        val available = TileType.entries.filter { it !in activeBlocks }
        AlertDialog(
            onDismissRequest = { picking = false },
            title = { Text("Add Objective") },
            text = {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(64.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(available) { type ->
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .border(1.dp, Color(0xFFC7C7CC), RoundedCornerShape(6.dp))
                                .clickable {
                                    setBlocks(state.level.activeBlocks + (type to ActiveBlockSpec(0, 0)))
                                    picking = false
                                }
                                .padding(6.dp),
                        ) { TileImage(type, Modifier.fillMaxSize()) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancel") } },
        )
    }
}
