package com.lele.llmonitor.ui.wallpaper

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.io.File

@Composable
internal fun HomeWallpaperImage(
    wallpaperFile: File,
    alpha: Float,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    crossfade: Boolean = true
) {
    val context = LocalContext.current
    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(wallpaperFile)
            .memoryCacheKey(
                "home_wallpaper:${wallpaperFile.absolutePath}:${wallpaperFile.lastModified()}"
            )
            .crossfade(crossfade)
            .build(),
        contentDescription = null,
        modifier = modifier,
        alpha = alpha.coerceIn(0f, 1f),
        contentScale = contentScale
    )
}
