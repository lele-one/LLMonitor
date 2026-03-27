package com.lele.llmonitor.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.roundToInt

private const val HOME_WALLPAPER_DIRECTORY = "home_wallpaper"
private const val HOME_WALLPAPER_HISTORY_DIRECTORY = "history"
private const val HOME_WALLPAPER_FILE_NAME = "current_wallpaper.img"
private const val HOME_WALLPAPER_TEMP_FILE_NAME = "current_wallpaper.tmp"
private const val HOME_WALLPAPER_BACKUP_FILE_NAME = "current_wallpaper.bak"
private const val HOME_WALLPAPER_STARTUP_PREVIEW_FILE_NAME = "startup_preview.png"
private const val HOME_WALLPAPER_HISTORY_FILE_PREFIX = "wallpaper_"
private const val HOME_WALLPAPER_HISTORY_FILE_SUFFIX = ".img"
private const val HOME_WALLPAPER_STARTUP_PREVIEW_MAX_DIMENSION = 960

internal const val HOME_WALLPAPER_HISTORY_LIMIT = 10

internal object HomeWallpaperStorage {
    private fun resolveWallpaperDirectory(context: Context): File {
        return File(context.filesDir, HOME_WALLPAPER_DIRECTORY)
    }

    private fun resolveHistoryDirectory(context: Context): File {
        return File(resolveWallpaperDirectory(context), HOME_WALLPAPER_HISTORY_DIRECTORY)
    }

    fun resolveWallpaperFile(context: Context): File {
        return File(resolveWallpaperDirectory(context), HOME_WALLPAPER_FILE_NAME)
    }

    fun resolveStartupPreviewFile(context: Context): File {
        return File(resolveWallpaperDirectory(context), HOME_WALLPAPER_STARTUP_PREVIEW_FILE_NAME)
    }

    fun resolveHistoryFiles(
        context: Context,
        excludeSameAsFile: File? = null
    ): List<File> {
        val excludeFile = excludeSameAsFile
            ?.takeIf { it.exists() && it.isFile && it.length() > 0L }

        return resolveHistoryDirectory(context)
            .listFiles()
            ?.asSequence()
            ?.filter { it.isFile && it.length() > 0L }
            ?.filter { historyFile ->
                if (excludeFile == null) {
                    true
                } else {
                    // 启动路径避免对大图做整文件哈希，使用轻量签名避免主线程 I/O 峰值。
                    !historyFile.fastSignatureEquals(excludeFile)
                }
            }
            ?.sortedByDescending { it.lastModified() }
            ?.take(HOME_WALLPAPER_HISTORY_LIMIT)
            ?.toList()
            .orEmpty()
    }

    suspend fun importWallpaper(
        context: Context,
        sourceUri: Uri,
        deleteSourceAfterImport: Boolean = false
    ): Boolean = withContext(Dispatchers.IO) {
        ensureWallpaperDirectories(context)

        val historyFile = createHistoryFile(context)
        val copiedToHistory = copyUriToFile(
            context = context,
            sourceUri = sourceUri,
            targetFile = historyFile
        )
        if (!copiedToHistory) return@withContext false

        val applied = replaceCurrentWallpaper(
            context = context,
            sourceFile = historyFile
        )
        if (!applied) {
            historyFile.delete()
            if (deleteSourceAfterImport) {
                deleteLocalFileUri(sourceUri)
            }
            return@withContext false
        }

        pruneHistory(context)
        if (deleteSourceAfterImport) {
            deleteLocalFileUri(sourceUri)
        }
        true
    }

    suspend fun applyWallpaperFromHistory(
        context: Context,
        historyFileName: String
    ): Boolean = withContext(Dispatchers.IO) {
        ensureWallpaperDirectories(context)

        val historyFile = resolveHistoryFiles(context)
            .firstOrNull { it.name == historyFileName }
            ?: return@withContext false

        val applied = replaceCurrentWallpaper(
            context = context,
            sourceFile = historyFile
        )
        if (!applied) return@withContext false

        historyFile.setLastModified(System.currentTimeMillis())
        pruneHistory(context)
        true
    }

    suspend fun deleteHistoryWallpaper(
        context: Context,
        historyFileName: String
    ): Boolean = withContext(Dispatchers.IO) {
        ensureWallpaperDirectories(context)

        val historyFile = resolveHistoryFiles(context)
            .firstOrNull { it.name == historyFileName }
            ?: return@withContext false

        historyFile.delete()
    }

    suspend fun clearWallpaper(context: Context) = withContext(Dispatchers.IO) {
        resolveWallpaperFile(context).delete()
        resolveStartupPreviewFile(context).delete()
    }

    suspend fun refreshStartupPreview(
        context: Context,
        backgroundArgb: Int,
        wallpaperAlpha: Float,
        wallpaperBlur: Float
    ): Boolean = withContext(Dispatchers.IO) {
        ensureWallpaperDirectories(context)
        val wallpaperFile = resolveWallpaperFile(context)
        if (!wallpaperFile.exists() || wallpaperFile.length() <= 0L) {
            resolveStartupPreviewFile(context).delete()
            return@withContext false
        }
        val displayDensity = context.resources.displayMetrics.density
        exportStartupPreview(
            sourceFile = wallpaperFile,
            targetFile = resolveStartupPreviewFile(context),
            backgroundArgb = backgroundArgb,
            wallpaperAlpha = wallpaperAlpha,
            wallpaperBlur = wallpaperBlur,
            displayDensity = displayDensity
        )
    }

    private fun ensureWallpaperDirectories(context: Context) {
        resolveWallpaperDirectory(context).mkdirs()
        resolveHistoryDirectory(context).mkdirs()
    }

    private fun createHistoryFile(context: Context): File {
        val uniqueSuffix = UUID.randomUUID().toString().take(8)
        return File(
            resolveHistoryDirectory(context),
            "$HOME_WALLPAPER_HISTORY_FILE_PREFIX${System.currentTimeMillis()}_$uniqueSuffix$HOME_WALLPAPER_HISTORY_FILE_SUFFIX"
        )
    }

    private fun copyUriToFile(
        context: Context,
        sourceUri: Uri,
        targetFile: File
    ): Boolean {
        val copied = runCatching {
            val inputStream = if (sourceUri.scheme == "file") {
                val path = sourceUri.path ?: return false
                FileInputStream(File(path))
            } else {
                context.contentResolver.openInputStream(sourceUri)
            } ?: return false

            inputStream.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }
            targetFile.length() > 0L
        }.getOrElse {
            false
        }

        if (!copied) {
            targetFile.delete()
        }

        return copied
    }

    private fun replaceCurrentWallpaper(
        context: Context,
        sourceFile: File
    ): Boolean {
        val targetFile = resolveWallpaperFile(context)
        val wallpaperDirectory = targetFile.parentFile ?: return false
        val tempFile = File(wallpaperDirectory, HOME_WALLPAPER_TEMP_FILE_NAME)
        val backupFile = File(wallpaperDirectory, HOME_WALLPAPER_BACKUP_FILE_NAME)

        tempFile.delete()
        backupFile.delete()

        val staged = runCatching {
            sourceFile.inputStream().use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }
            tempFile.length() > 0L
        }.getOrElse {
            false
        }
        if (!staged) {
            tempFile.delete()
            return false
        }

        val hadCurrentWallpaper = targetFile.exists()
        if (hadCurrentWallpaper && !targetFile.renameTo(backupFile)) {
            tempFile.delete()
            return false
        }

        val replaced = tempFile.renameTo(targetFile)
        if (!replaced) {
            tempFile.delete()
            if (hadCurrentWallpaper) {
                backupFile.renameTo(targetFile)
            }
            return false
        }

        if (backupFile.exists()) {
            backupFile.delete()
        }
        return true
    }

    private fun pruneHistory(context: Context) {
        resolveHistoryDirectory(context)
            .listFiles()
            ?.asSequence()
            ?.filter { it.isFile }
            ?.sortedByDescending { it.lastModified() }
            ?.drop(HOME_WALLPAPER_HISTORY_LIMIT)
            ?.forEach { it.delete() }
    }

    private fun deleteLocalFileUri(sourceUri: Uri) {
        if (sourceUri.scheme != "file") return
        sourceUri.path?.let { path ->
            File(path).delete()
        }
    }

    private fun exportStartupPreview(
        sourceFile: File,
        targetFile: File,
        backgroundArgb: Int,
        wallpaperAlpha: Float,
        wallpaperBlur: Float,
        displayDensity: Float
    ): Boolean {
        val tempFile = File(targetFile.parentFile, "${targetFile.name}.tmp")
        tempFile.delete()
        val previewBitmap = decodeStartupPreviewBitmap(sourceFile) ?: run {
            tempFile.delete()
            targetFile.delete()
            return false
        }
        val compositedBitmap = Bitmap.createBitmap(
            previewBitmap.width,
            previewBitmap.height,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(compositedBitmap)
        canvas.drawColor(backgroundArgb)
        val blurredPreviewBitmap = applyWallpaperBlur(
            source = previewBitmap,
            blurAmount = wallpaperBlur,
            displayDensity = displayDensity
        )
        canvas.drawBitmap(
            blurredPreviewBitmap,
            0f,
            0f,
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                alpha = (wallpaperAlpha.coerceIn(0f, 1f) * 255f).roundToInt()
            }
        )

        val written = runCatching {
            FileOutputStream(tempFile).use { output ->
                compositedBitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            }
            tempFile.length() > 0L
        }.getOrElse {
            false
        }
        if (blurredPreviewBitmap !== previewBitmap) {
            previewBitmap.recycle()
            blurredPreviewBitmap.recycle()
        } else {
            previewBitmap.recycle()
        }
        compositedBitmap.recycle()

        if (!written) {
            tempFile.delete()
            return false
        }

        if (targetFile.exists()) {
            targetFile.delete()
        }
        return tempFile.renameTo(targetFile)
    }

    private fun decodeStartupPreviewBitmap(
        sourceFile: File
    ): Bitmap? {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(sourceFile)
                return@runCatching ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    decoder.isMutableRequired = false
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.setTargetSampleSize(
                        calculateInSampleSize(
                            sourceWidth = info.size.width,
                            sourceHeight = info.size.height,
                            requestedMaxDimension = HOME_WALLPAPER_STARTUP_PREVIEW_MAX_DIMENSION
                        )
                    )
                }.copy(Bitmap.Config.ARGB_8888, false)
            }

            BitmapFactory.Options().run {
                inJustDecodeBounds = true
                BitmapFactory.decodeFile(sourceFile.absolutePath, this)
                if (outWidth <= 0 || outHeight <= 0) {
                    return@runCatching null
                }
                val decodeOptions = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                    inSampleSize = calculateInSampleSize(
                        sourceWidth = outWidth,
                        sourceHeight = outHeight,
                        requestedMaxDimension = HOME_WALLPAPER_STARTUP_PREVIEW_MAX_DIMENSION
                    )
                }
                BitmapFactory.decodeFile(sourceFile.absolutePath, decodeOptions)
                    ?.copy(Bitmap.Config.ARGB_8888, false)
            }
        }.getOrNull()
    }

    private fun calculateInSampleSize(
        sourceWidth: Int,
        sourceHeight: Int,
        requestedMaxDimension: Int
    ): Int {
        var sampleSize = 1
        var sampledWidth = sourceWidth
        var sampledHeight = sourceHeight
        while (maxOf(sampledWidth, sampledHeight) / 2 >= requestedMaxDimension) {
            sampledWidth /= 2
            sampledHeight /= 2
            sampleSize *= 2
        }
        return sampleSize.coerceAtLeast(1)
    }

    private fun applyWallpaperBlur(
        source: Bitmap,
        blurAmount: Float,
        displayDensity: Float
    ): Bitmap {
        val blurRadiusPx = resolveHomeWallpaperBlurRadiusPx(
            wallpaperBlur = blurAmount,
            displayDensity = displayDensity
        )
        if (blurRadiusPx <= 0.5f) return source
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val rendered = applyRenderNodeBlur(source, blurRadiusPx)
            if (rendered != null) return rendered
        }
        return applyApproximateGaussianBlur(source, blurRadiusPx)
    }

    private fun applyRenderNodeBlur(
        source: Bitmap,
        blurRadiusPx: Float
    ): Bitmap? {
        return runCatching {
            val width = source.width
            val height = source.height
            val renderNode = RenderNode("home_wallpaper_blur").apply {
                setPosition(0, 0, width, height)
                setRenderEffect(
                    RenderEffect.createBlurEffect(
                        blurRadiusPx,
                        blurRadiusPx,
                        Shader.TileMode.CLAMP
                    )
                )
            }
            val recordingCanvas = renderNode.beginRecording(width, height)
            recordingCanvas.drawBitmap(
                source,
                0f,
                0f,
                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            )
            renderNode.endRecording()

            val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            Canvas(output).drawRenderNode(renderNode)
            output
        }.getOrNull()
    }

    private fun applyApproximateGaussianBlur(
        source: Bitmap,
        blurRadiusPx: Float
    ): Bitmap {
        val downsampleFactor = when {
            blurRadiusPx >= 24f -> 0.45f
            blurRadiusPx >= 14f -> 0.55f
            else -> 0.65f
        }
        val downsampledWidth = (source.width * downsampleFactor).roundToInt().coerceAtLeast(1)
        val downsampledHeight = (source.height * downsampleFactor).roundToInt().coerceAtLeast(1)
        val workingBitmap = if (downsampledWidth == source.width && downsampledHeight == source.height) {
            source
        } else {
            Bitmap.createScaledBitmap(source, downsampledWidth, downsampledHeight, true)
        }
        val workingRadius = (blurRadiusPx * downsampleFactor).roundToInt().coerceAtLeast(1)

        var blurred = workingBitmap
        repeat(3) { pass ->
            val next = boxBlur(blurred, workingRadius)
            if (pass > 0 || workingBitmap !== source) {
                blurred.recycle()
            }
            blurred = next
        }

        return if (blurred.width == source.width && blurred.height == source.height) {
            blurred
        } else {
            Bitmap.createScaledBitmap(blurred, source.width, source.height, true).also {
                blurred.recycle()
            }
        }
    }

    private fun boxBlur(
        source: Bitmap,
        radius: Int
    ): Bitmap {
        if (radius <= 0 || source.width <= 1 || source.height <= 1) {
            return source.copy(Bitmap.Config.ARGB_8888, false)
        }

        val width = source.width
        val height = source.height
        val input = IntArray(width * height)
        source.getPixels(input, 0, width, 0, 0, width, height)
        val horizontal = IntArray(width * height)
        val output = IntArray(width * height)
        val windowSize = radius * 2 + 1

        for (y in 0 until height) {
            val rowOffset = y * width
            var alphaSum = 0
            var redSum = 0
            var greenSum = 0
            var blueSum = 0

            for (sampleX in -radius..radius) {
                val color = input[rowOffset + sampleX.coerceIn(0, width - 1)]
                alphaSum += color ushr 24
                redSum += (color ushr 16) and 0xFF
                greenSum += (color ushr 8) and 0xFF
                blueSum += color and 0xFF
            }

            for (x in 0 until width) {
                horizontal[rowOffset + x] =
                    ((alphaSum / windowSize) shl 24) or
                        ((redSum / windowSize) shl 16) or
                        ((greenSum / windowSize) shl 8) or
                        (blueSum / windowSize)

                val removeColor = input[rowOffset + (x - radius).coerceIn(0, width - 1)]
                val addColor = input[rowOffset + (x + radius + 1).coerceIn(0, width - 1)]
                alphaSum += (addColor ushr 24) - (removeColor ushr 24)
                redSum += ((addColor ushr 16) and 0xFF) - ((removeColor ushr 16) and 0xFF)
                greenSum += ((addColor ushr 8) and 0xFF) - ((removeColor ushr 8) and 0xFF)
                blueSum += (addColor and 0xFF) - (removeColor and 0xFF)
            }
        }

        for (x in 0 until width) {
            var alphaSum = 0
            var redSum = 0
            var greenSum = 0
            var blueSum = 0

            for (sampleY in -radius..radius) {
                val color = horizontal[sampleY.coerceIn(0, height - 1) * width + x]
                alphaSum += color ushr 24
                redSum += (color ushr 16) and 0xFF
                greenSum += (color ushr 8) and 0xFF
                blueSum += color and 0xFF
            }

            for (y in 0 until height) {
                output[y * width + x] =
                    ((alphaSum / windowSize) shl 24) or
                        ((redSum / windowSize) shl 16) or
                        ((greenSum / windowSize) shl 8) or
                        (blueSum / windowSize)

                val removeColor = horizontal[(y - radius).coerceIn(0, height - 1) * width + x]
                val addColor = horizontal[(y + radius + 1).coerceIn(0, height - 1) * width + x]
                alphaSum += (addColor ushr 24) - (removeColor ushr 24)
                redSum += ((addColor ushr 16) and 0xFF) - ((removeColor ushr 16) and 0xFF)
                greenSum += ((addColor ushr 8) and 0xFF) - ((removeColor ushr 8) and 0xFF)
                blueSum += (addColor and 0xFF) - (removeColor and 0xFF)
            }
        }

        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            setPixels(output, 0, width, 0, 0, width, height)
        }
    }

    private fun File.fastSignatureEquals(other: File): Boolean {
        if (this.absolutePath == other.absolutePath) return true
        return this.length() == other.length() &&
            this.lastModified() == other.lastModified()
    }
}
