package com.lele.llmonitor.ui.wallpaper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.media.ExifInterface
import android.net.Uri
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

internal suspend fun loadWallpaperCropBitmap(
    context: Context,
    sourceUri: Uri,
    requestedMaxDimension: Int
): Bitmap? = withContext(Dispatchers.IO) {
    decodeWallpaperBitmap(
        context = context,
        sourceUri = sourceUri,
        requestedMaxDimension = requestedMaxDimension
    ) ?: decodeWallpaperBitmapWithCoil(
        context = context,
        sourceUri = sourceUri,
        requestedMaxDimension = requestedMaxDimension
    )
}

private fun decodeWallpaperBitmap(
    context: Context,
    sourceUri: Uri,
    requestedMaxDimension: Int
): Bitmap? {
    return runCatching {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            return@runCatching decodeWallpaperBitmapWithImageDecoder(
                context = context,
                sourceUri = sourceUri,
                requestedMaxDimension = requestedMaxDimension
            )
        }

        decodeWallpaperBitmapWithBitmapFactory(
            context = context,
            sourceUri = sourceUri,
            requestedMaxDimension = requestedMaxDimension
        )
    }.getOrNull()
}

@androidx.annotation.RequiresApi(android.os.Build.VERSION_CODES.P)
private fun decodeWallpaperBitmapWithImageDecoder(
    context: Context,
    sourceUri: Uri,
    requestedMaxDimension: Int
): Bitmap? {
    val source = ImageDecoder.createSource(context.contentResolver, sourceUri)
    return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
        decoder.isMutableRequired = false
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        val sampleSize = calculateInSampleSize(
            sourceWidth = info.size.width,
            sourceHeight = info.size.height,
            requestedMaxDimension = requestedMaxDimension
        )
        decoder.setTargetSampleSize(sampleSize)
    }.copy(Bitmap.Config.ARGB_8888, false)
}

private fun decodeWallpaperBitmapWithBitmapFactory(
    context: Context,
    sourceUri: Uri,
    requestedMaxDimension: Int
): Bitmap? {
    val boundsOptions = BitmapFactory.Options().apply {
        inJustDecodeBounds = true
    }
    context.contentResolver.openInputStream(sourceUri)?.use { input ->
        BitmapFactory.decodeStream(input, null, boundsOptions)
    } ?: return null

    if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) return null

    val decodeOptions = BitmapFactory.Options().apply {
        inPreferredConfig = Bitmap.Config.ARGB_8888
        inSampleSize = calculateInSampleSize(
            sourceWidth = boundsOptions.outWidth,
            sourceHeight = boundsOptions.outHeight,
            requestedMaxDimension = requestedMaxDimension
        )
    }

    val decodedBitmap = context.contentResolver.openInputStream(sourceUri)?.use { input ->
        BitmapFactory.decodeStream(input, null, decodeOptions)
    } ?: return null

    val exifMatrix = context.contentResolver.openInputStream(sourceUri)?.use { input ->
        resolveExifMatrix(input)
    } ?: Matrix()

    if (exifMatrix.isIdentity) {
        return decodedBitmap.copy(Bitmap.Config.ARGB_8888, false)
    }

    val orientedBitmap = Bitmap.createBitmap(
        decodedBitmap,
        0,
        0,
        decodedBitmap.width,
        decodedBitmap.height,
        exifMatrix,
        true
    )
    if (orientedBitmap !== decodedBitmap) {
        decodedBitmap.recycle()
    }
    return orientedBitmap.copy(Bitmap.Config.ARGB_8888, false)
}

private suspend fun decodeWallpaperBitmapWithCoil(
    context: Context,
    sourceUri: Uri,
    requestedMaxDimension: Int
): Bitmap? {
    return runCatching {
        val request = ImageRequest.Builder(context)
            .data(sourceUri)
            .allowHardware(false)
            .size(requestedMaxDimension)
            .build()

        val result = context.imageLoader.execute(request)
        val drawable = result.drawable ?: return null
        drawable.toBitmap().copy(Bitmap.Config.ARGB_8888, false)
    }.getOrNull()
}

internal suspend fun exportWallpaperCropToFile(
    context: Context,
    sourceBitmap: Bitmap,
    previewViewport: WallpaperCropViewport,
    previewTransform: WallpaperCropTransform
): Uri? = withContext(Dispatchers.IO) {
    val exportViewport = resolveWallpaperCropExportViewport(
        sourceBitmap = sourceBitmap,
        previewViewport = previewViewport,
        previewTransform = previewTransform
    )
    val exportTransform = scaleWallpaperCropTransformToViewport(
        transform = previewTransform,
        fromViewport = previewViewport,
        toViewport = exportViewport
    )

    val outputBitmap = Bitmap.createBitmap(
        exportViewport.width.roundToInt(),
        exportViewport.height.roundToInt(),
        Bitmap.Config.ARGB_8888
    )
    val canvas = Canvas(outputBitmap)
    val matrix = Matrix().apply {
        postTranslate(-sourceBitmap.width / 2f, -sourceBitmap.height / 2f)
        postScale(exportTransform.scale, exportTransform.scale)
        postRotate(exportTransform.rotationDegrees)
        postTranslate(
            exportViewport.width / 2f + exportTransform.offsetX,
            exportViewport.height / 2f + exportTransform.offsetY
        )
    }
    canvas.drawBitmap(
        sourceBitmap,
        matrix,
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    )

    val outputFile = File(
        context.cacheDir,
        "wallpaper_crop_${System.currentTimeMillis()}.png"
    )
    FileOutputStream(outputFile).use { output ->
        outputBitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
    }
    outputBitmap.recycle()

    Uri.fromFile(outputFile)
}

private fun calculateInSampleSize(
    sourceWidth: Int,
    sourceHeight: Int,
    requestedMaxDimension: Int
): Int {
    val safeMaxDimension = max(1, requestedMaxDimension)
    var inSampleSize = 1
    var sampledWidth = sourceWidth
    var sampledHeight = sourceHeight

    while (maxOf(sampledWidth, sampledHeight) / 2 >= safeMaxDimension) {
        sampledWidth /= 2
        sampledHeight /= 2
        inSampleSize *= 2
    }

    return inSampleSize.coerceAtLeast(1)
}

private fun resolveExifMatrix(input: java.io.InputStream): Matrix {
    val exif = ExifInterface(input)
    return Matrix().apply {
        when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                postRotate(90f)
                postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                postRotate(270f)
                postScale(-1f, 1f)
            }
        }
    }
}

private fun resolveWallpaperCropExportViewport(
    sourceBitmap: Bitmap,
    previewViewport: WallpaperCropViewport,
    previewTransform: WallpaperCropTransform
): WallpaperCropViewport {
    val safeScale = previewTransform.scale.coerceAtLeast(0.0001f)
    val desiredWidth = (previewViewport.width / safeScale).coerceAtLeast(1f)
    val desiredHeight = (previewViewport.height / safeScale).coerceAtLeast(1f)
    val fitRatio = minOf(
        1f,
        sourceBitmap.width / desiredWidth,
        sourceBitmap.height / desiredHeight
    )

    return WallpaperCropViewport(
        width = desiredWidth * fitRatio,
        height = desiredHeight * fitRatio
    )
}
