package com.nigelspeight.sportscollector.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nigelspeight.sportscollector.AppServices
import com.nigelspeight.sportscollector.game.Textures
import com.nigelspeight.sportscollector.level.SectionRows
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/// Full-screen intro/"pundit" screen shown at launch (when
/// `MainActivity.launchIntoSectionScreen` is `true`), before handing off to
/// `SectionScreen`. Shows a full-screen portrait with a two-tone message strip
/// near the bottom; tapping anywhere replaces it with `SectionScreen`.
@Composable
fun PunditScreen(onDismiss: () -> Unit) {
    HideStatusBar()

    val copy = remember {
        SectionRows.punditCopy(AppServices.progress.lastPlayedMapID, AppServices.progress::bestResult)
    }
    val portrait by produceState<ImageBitmap?>(null) {
        value = withContext(Dispatchers.IO) {
            runCatching { Textures.uncachedImage("olivia").bitmap.asImageBitmap() }.getOrNull()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
    ) {
        portrait?.let {
            Image(it, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)),
        ) {
            Text(
                copy.speaker,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0.04f, 0.10f, 0.28f, 0.92f))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
            Text(
                copy.message,
                color = Color.White,
                fontSize = 16.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0.27f, 0.52f, 0.86f, 0.92f))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }
}
