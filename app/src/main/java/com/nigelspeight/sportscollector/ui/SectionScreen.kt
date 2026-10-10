package com.nigelspeight.sportscollector.ui

import android.app.Activity
import android.util.LruCache
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.nigelspeight.sportscollector.AppServices
import com.nigelspeight.sportscollector.audio.AudioManager
import com.nigelspeight.sportscollector.engine.Objective
import com.nigelspeight.sportscollector.engine.ObjectiveKind
import com.nigelspeight.sportscollector.game.Textures
import com.nigelspeight.sportscollector.level.GameSection
import com.nigelspeight.sportscollector.level.LevelListRow
import com.nigelspeight.sportscollector.level.SectionRows
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

/// Which section the carousel is showing - held outside composition so it
/// survives a round trip into a level and back.
class SectionScreenState {
    var section by mutableStateOf(GameSection.SOCCER)
}

/// User-facing landing screen: a full-screen themed background behind a single
/// card describing the current section, with left/right arrows (or swipes) to
/// page through all 6 sections, and the section's level cards below. The
/// polished counterpart to `LevelSelectScreen`, which stays around for
/// editing/debugging.
@Composable
fun SectionScreen(state: SectionScreenState, onPlay: (mapID: Int) -> Unit) {
    HideStatusBar()

    val section = state.section
    // Recomputed whenever this screen re-enters composition (e.g. returning from
    // a played level), so the list reflects the latest progress.
    val rows = remember(section) {
        SectionRows.build(section, AppServices.progress::bestResult, ::objectiveKinds)
    }
    val swipeThreshold = with(LocalDensity.current) { 48.dp.toPx() }

    // Read `state.section` live rather than the captured `section`: the swipe
    // handler below is installed once and would otherwise keep paging from
    // whichever section was showing when it was created.
    fun previous() {
        state.section.previousSection?.let { state.section = it }
    }

    fun next() {
        state.section.nextSection?.let { state.section = it }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                var total = 0f
                detectHorizontalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = {
                        if (total < -swipeThreshold) next() else if (total > swipeThreshold) previous()
                    },
                    onHorizontalDrag = { _, dragAmount -> total += dragAmount },
                )
            },
    ) {
        val cardWidth = maxWidth * 0.5f

        Crossfade(targetState = section, animationSpec = tween(250), label = "background") { shown ->
            SectionImage(shown, Modifier.fillMaxSize().alpha(0.3f))
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Top)),
        ) {
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // First/last sections have no predecessor/successor, so their
                // arrow is hidden - but still occupies its slot so the card
                // stays centred.
                ArrowButton(left = true, visible = section.previousSection != null, onClick = ::previous)
                Spacer(Modifier.width(16.dp))
                Crossfade(targetState = section, animationSpec = tween(250), label = "card") { shown ->
                    SectionCard(shown, Modifier.width(cardWidth))
                }
                Spacer(Modifier.width(16.dp))
                ArrowButton(left = false, visible = section.nextSection != null, onClick = ::next)
            }
            Spacer(Modifier.height(24.dp))
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)),
            ) {
                items(rows) { row ->
                    LevelCard(row, section) {
                        when (row) {
                            is LevelListRow.NextLevel -> {
                                AudioManager.playSound("Menu1")
                                onPlay(row.mapID)
                            }
                            is LevelListRow.Won -> {
                                AudioManager.playSound("Menu1")
                                onPlay(row.mapID)
                            }
                            is LevelListRow.AdvanceToNextSection -> {
                                AudioManager.playSound("Menu1")
                                state.section = row.next
                            }
                            LevelListRow.GameCompleted, LevelListRow.ComingSoon, is LevelListRow.Locked -> Unit
                        }
                    }
                }
            }
        }
    }
}

/// Loads just enough of the level to preview its objective icons on the
/// next-to-play card.
private fun objectiveKinds(mapID: Int): List<ObjectiveKind> =
    runCatching { Objective.objectivesFor(AppServices.loadLevel(mapID)).map { it.kind } }.getOrDefault(emptyList())

/// The iOS screen hides the status bar; restore it when leaving.
@Composable
private fun HideStatusBar() {
    val view = LocalView.current
    DisposableEffect(Unit) {
        val controller = WindowCompat.getInsetsController((view.context as Activity).window, view)
        controller.hide(WindowInsetsCompat.Type.statusBars())
        onDispose { controller.show(WindowInsetsCompat.Type.statusBars()) }
    }
}

/// Decoded section art, shared by the background and the card. Each image is
/// 2048x2048 (~16MB), so only the few most recently shown are kept.
private val sectionArtCache = LruCache<GameSection, ImageBitmap>(3)

/// A section's background art, decoded off the main thread.
@Composable
private fun SectionImage(section: GameSection, modifier: Modifier) {
    val bitmap by produceState(sectionArtCache.get(section), section) {
        if (value == null) {
            value = withContext(Dispatchers.IO) {
                sectionArtCache.get(section) ?: runCatching {
                    Textures.uncachedImage(section.backgroundImageName).bitmap.asImageBitmap()
                }.getOrNull()?.also { sectionArtCache.put(section, it) }
            }
        }
    }
    bitmap?.let { Image(it, contentDescription = null, modifier = modifier, contentScale = ContentScale.Crop) }
}

@Composable
private fun SectionCard(section: GameSection, modifier: Modifier) {
    Column(
        modifier = modifier
            .aspectRatio(600f / 880f)
            .shadow(14.dp, RoundedCornerShape(20.dp))
            .background(Color.White, RoundedCornerShape(20.dp))
            .padding(12.dp),
    ) {
        Text(section.displayName, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.Black, maxLines = 1)
        Spacer(Modifier.height(4.dp))
        Text(section.levelCountText, fontSize = 14.sp, color = Color.DarkGray, maxLines = 1)
        Spacer(Modifier.height(12.dp))
        SectionImage(
            section,
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(12.dp)),
        )
    }
}

@Composable
private fun ArrowButton(left: Boolean, visible: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .then(if (visible) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (visible) {
            Box(
                modifier = Modifier.size(32.dp).background(Color.Gray, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (left) Icons.AutoMirrored.Filled.KeyboardArrowLeft else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = if (left) "Previous section" else "Next section",
                    tint = Color.Black,
                )
            }
        }
    }
}

@Composable
private fun AtlasImage(name: String, modifier: Modifier) {
    val bitmap = remember(name) { Textures.pill(name).bitmap.asImageBitmap() }
    Image(bitmap, contentDescription = null, modifier = modifier, contentScale = ContentScale.Fit)
}

/// A translucent white, rounded, white-stroked card row. Shows a level's icon
/// and number (plus stars/high score once won, or objective icons while it's the
/// next one to play), or a centred message for the section-boundary filler rows.
@Composable
private fun LevelCard(row: LevelListRow, section: GameSection, onClick: () -> Unit) {
    // The next-to-play card is wider, to read as the player's priority focus;
    // won cards get a dimmed border to mark them as already played.
    val widthFraction = if (row is LevelListRow.NextLevel) 0.92f else 0.8f
    val borderColor = if (row is LevelListRow.Won) Color.White.copy(alpha = 0.5f) else Color.White
    val shape = RoundedCornerShape(16.dp)

    Box(Modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .fillMaxWidth(widthFraction)
                .heightIn(min = 72.dp)
                // Soft blurred shadow matching the iOS CALayer one (opacity 0.3,
                // radius 8, y-offset 4) - a plain elevation shadow shows through
                // the translucent card as a hard-edged dark rectangle.
                .dropShadow(shape, Shadow(radius = 8.dp, color = Color.Black.copy(alpha = 0.3f), offset = DpOffset(0.dp, 4.dp)))
                .background(Color.White.copy(alpha = 0.3f), shape)
                .border(4.dp, borderColor, shape)
                .clip(shape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            when (row) {
                is LevelListRow.NextLevel -> LevelCardContent(section, row.mapID) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        for (kind in row.objectiveKinds) {
                            val bitmap = remember(kind) { Textures.objective(kind).bitmap.asImageBitmap() }
                            Image(bitmap, contentDescription = null, modifier = Modifier.size(28.dp))
                        }
                    }
                }

                is LevelListRow.Won -> LevelCardContent(section, row.mapID) {
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        val stars = min(max(row.result.stars, 1), 3)
                        val bitmap = remember(stars) { Textures.image("starsx$stars").bitmap.asImageBitmap() }
                        Image(bitmap, contentDescription = "$stars stars", modifier = Modifier.size(72.dp, 24.dp))
                        Text("High Score: ${row.result.score}", fontSize = 12.sp, color = Color.White)
                    }
                }

                is LevelListRow.AdvanceToNextSection -> CardMessage("Section complete!\nContinue to ${row.next.displayName}")
                LevelListRow.GameCompleted -> CardMessage("You've completed Sports Collector!")
                LevelListRow.ComingSoon -> CardMessage("Coming soon")
                is LevelListRow.Locked -> CardMessage("🔒 Complete ${row.previousSection.displayName} to unlock")
            }
        }
    }
}

@Composable
private fun LevelCardContent(section: GameSection, mapID: Int, trailing: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AtlasImage(section.iconTextureName, Modifier.size(40.dp))
        Spacer(Modifier.width(12.dp))
        Text("Level $mapID", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
        Spacer(Modifier.weight(1f).width(8.dp))
        trailing()
    }
}

@Composable
private fun CardMessage(text: String) {
    Text(
        text,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        color = Color.White,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(16.dp),
    )
}
