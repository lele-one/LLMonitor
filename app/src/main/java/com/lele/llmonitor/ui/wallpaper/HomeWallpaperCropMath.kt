package com.lele.llmonitor.ui.wallpaper

import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

private const val DefaultWallpaperCropMaxPreferredScaleMultiplier = 6f

internal data class WallpaperCropViewport(
    val width: Float,
    val height: Float
)

internal data class WallpaperCropTransform(
    val scale: Float,
    val rotationDegrees: Float,
    val offsetX: Float,
    val offsetY: Float
)

internal data class WallpaperCropPose(
    val rotationDegrees: Float,
    val preferredScale: Float,
    val localOffsetX: Float,
    val localOffsetY: Float
)

private data class WallpaperCropLocalOffsetBounds(
    val maxLocalOffsetX: Float,
    val maxLocalOffsetY: Float
)

internal fun initialWallpaperCropPose(
    sourceWidth: Int,
    sourceHeight: Int,
    viewport: WallpaperCropViewport
): WallpaperCropPose {
    return WallpaperCropPose(
        rotationDegrees = 0f,
        preferredScale = referenceWallpaperCropScale(
            sourceWidth = sourceWidth,
            sourceHeight = sourceHeight,
            viewport = viewport
        ),
        localOffsetX = 0f,
        localOffsetY = 0f
    )
}

internal fun resolveWallpaperCropTransformFromPose(
    sourceWidth: Int,
    sourceHeight: Int,
    viewport: WallpaperCropViewport,
    pose: WallpaperCropPose
): WallpaperCropTransform {
    val normalizedRotation = normalizeRotationDegrees(pose.rotationDegrees)
    val minimumScale = minimumScaleToCoverViewport(
        sourceWidth = sourceWidth,
        sourceHeight = sourceHeight,
        viewport = viewport,
        rotationDegrees = normalizedRotation
    )
    val resolvedScale = max(pose.preferredScale, minimumScale).coerceAtLeast(0.0001f)
    val screenOffset = rotateLocalVectorToScreen(
        vector = Offset(
            x = pose.localOffsetX * resolvedScale,
            y = pose.localOffsetY * resolvedScale
        ),
        rotationDegrees = normalizedRotation
    )

    return WallpaperCropTransform(
        scale = resolvedScale,
        // Preserve the raw rotation path for render continuity; geometry still uses the canonical angle.
        rotationDegrees = pose.rotationDegrees,
        offsetX = screenOffset.x,
        offsetY = screenOffset.y
    )
}

internal fun clampWallpaperCropPose(
    sourceWidth: Int,
    sourceHeight: Int,
    viewport: WallpaperCropViewport,
    pose: WallpaperCropPose,
    maxPreferredScaleMultiplier: Float = DefaultWallpaperCropMaxPreferredScaleMultiplier
): WallpaperCropPose {
    val normalizedRotation = normalizeRotationDegrees(pose.rotationDegrees)
    val preferredScaleBounds = resolveWallpaperCropPreferredScaleBounds(
        sourceWidth = sourceWidth,
        sourceHeight = sourceHeight,
        viewport = viewport,
        maxPreferredScaleMultiplier = maxPreferredScaleMultiplier
    )
    val clampedPreferredScale = pose.preferredScale.coerceIn(
        minimumValue = preferredScaleBounds.minimumPreferredScale,
        maximumValue = preferredScaleBounds.maximumPreferredScale
    )
    val resolvedScale = max(
        clampedPreferredScale,
        minimumScaleToCoverViewport(
            sourceWidth = sourceWidth,
            sourceHeight = sourceHeight,
            viewport = viewport,
            rotationDegrees = normalizedRotation
        )
    )
    val bounds = resolveWallpaperCropLocalOffsetBounds(
        sourceWidth = sourceWidth,
        sourceHeight = sourceHeight,
        viewport = viewport,
        scale = resolvedScale,
        rotationDegrees = normalizedRotation
    )

    return WallpaperCropPose(
        rotationDegrees = normalizedRotation,
        preferredScale = clampedPreferredScale,
        localOffsetX = pose.localOffsetX.coerceIn(-bounds.maxLocalOffsetX, bounds.maxLocalOffsetX),
        localOffsetY = pose.localOffsetY.coerceIn(-bounds.maxLocalOffsetY, bounds.maxLocalOffsetY)
    )
}

internal fun rotateWallpaperCropPoseToDegrees(
    sourceWidth: Int,
    sourceHeight: Int,
    viewport: WallpaperCropViewport,
    currentPose: WallpaperCropPose,
    rotationDegrees: Float,
    maxPreferredScaleMultiplier: Float = DefaultWallpaperCropMaxPreferredScaleMultiplier
): WallpaperCropPose {
    return clampWallpaperCropPose(
        sourceWidth = sourceWidth,
        sourceHeight = sourceHeight,
        viewport = viewport,
        pose = currentPose.copy(rotationDegrees = rotationDegrees),
        maxPreferredScaleMultiplier = maxPreferredScaleMultiplier
    )
}

internal fun resolveWallpaperCropPoseForRenderRotation(
    sourceWidth: Int,
    sourceHeight: Int,
    viewport: WallpaperCropViewport,
    basePose: WallpaperCropPose,
    rotationDegrees: Float,
    maxPreferredScaleMultiplier: Float = DefaultWallpaperCropMaxPreferredScaleMultiplier
): WallpaperCropPose {
    val clampedPose = rotateWallpaperCropPoseToDegrees(
        sourceWidth = sourceWidth,
        sourceHeight = sourceHeight,
        viewport = viewport,
        currentPose = basePose,
        rotationDegrees = rotationDegrees,
        maxPreferredScaleMultiplier = maxPreferredScaleMultiplier
    )
    return clampedPose.copy(rotationDegrees = rotationDegrees)
}

internal fun applyWallpaperGestureToPose(
    sourceWidth: Int,
    sourceHeight: Int,
    viewport: WallpaperCropViewport,
    currentPose: WallpaperCropPose,
    centroid: Offset,
    pan: Offset,
    zoom: Float,
    rotationDegrees: Float,
    maxPreferredScaleMultiplier: Float = DefaultWallpaperCropMaxPreferredScaleMultiplier
): WallpaperCropPose {
    val currentTransform = resolveWallpaperCropTransformFromPose(
        sourceWidth = sourceWidth,
        sourceHeight = sourceHeight,
        viewport = viewport,
        pose = currentPose
    )
    val currentScale = currentTransform.scale.coerceAtLeast(0.0001f)
    val nextRotationDegrees = normalizeRotationDegrees(currentPose.rotationDegrees + rotationDegrees)
    val centeredCentroid = Offset(
        x = centroid.x - viewport.width / 2f,
        y = centroid.y - viewport.height / 2f
    )
    val localCentroid = rotateScreenVectorToLocal(
        vector = centeredCentroid,
        rotationDegrees = currentPose.rotationDegrees
    )
    val sourcePointUnderCentroid = Offset(
        x = localCentroid.x / currentScale - currentPose.localOffsetX,
        y = localCentroid.y / currentScale - currentPose.localOffsetY
    )

    val preferredScaleBounds = resolveWallpaperCropPreferredScaleBounds(
        sourceWidth = sourceWidth,
        sourceHeight = sourceHeight,
        viewport = viewport,
        maxPreferredScaleMultiplier = maxPreferredScaleMultiplier
    )
    val nextPreferredScale = (currentTransform.scale * zoom)
        .coerceIn(
            minimumValue = preferredScaleBounds.minimumPreferredScale,
            maximumValue = preferredScaleBounds.maximumPreferredScale
        )
        .coerceAtLeast(0.0001f)
    val nextScale = max(
        nextPreferredScale,
        minimumScaleToCoverViewport(
            sourceWidth = sourceWidth,
            sourceHeight = sourceHeight,
            viewport = viewport,
            rotationDegrees = nextRotationDegrees
        )
    )
    val targetCentroid = centeredCentroid + pan
    val targetLocalCentroid = rotateScreenVectorToLocal(
        vector = targetCentroid,
        rotationDegrees = nextRotationDegrees
    )
    val candidateLocalOffset = Offset(
        x = targetLocalCentroid.x / nextScale - sourcePointUnderCentroid.x,
        y = targetLocalCentroid.y / nextScale - sourcePointUnderCentroid.y
    )
    val bounds = resolveWallpaperCropLocalOffsetBounds(
        sourceWidth = sourceWidth,
        sourceHeight = sourceHeight,
        viewport = viewport,
        scale = nextScale,
        rotationDegrees = nextRotationDegrees
    )

    return WallpaperCropPose(
        rotationDegrees = nextRotationDegrees,
        preferredScale = nextPreferredScale,
        localOffsetX = candidateLocalOffset.x.coerceIn(-bounds.maxLocalOffsetX, bounds.maxLocalOffsetX),
        localOffsetY = candidateLocalOffset.y.coerceIn(-bounds.maxLocalOffsetY, bounds.maxLocalOffsetY)
    )
}

internal fun areWallpaperCropPosesEquivalent(
    first: WallpaperCropPose,
    second: WallpaperCropPose,
    epsilon: Float = 0.0001f
): Boolean {
    return abs(normalizeRotationDegrees(first.rotationDegrees - second.rotationDegrees)) <= epsilon &&
        abs(first.preferredScale - second.preferredScale) <= epsilon &&
        abs(first.localOffsetX - second.localOffsetX) <= epsilon &&
        abs(first.localOffsetY - second.localOffsetY) <= epsilon
}

internal fun initialWallpaperCropTransform(
    sourceWidth: Int,
    sourceHeight: Int,
    viewport: WallpaperCropViewport
): WallpaperCropTransform {
    return resolveWallpaperCropTransformFromPose(
        sourceWidth = sourceWidth,
        sourceHeight = sourceHeight,
        viewport = viewport,
        pose = initialWallpaperCropPose(
            sourceWidth = sourceWidth,
            sourceHeight = sourceHeight,
            viewport = viewport
        )
    )
}

internal fun clampWallpaperCropTransform(
    sourceWidth: Int,
    sourceHeight: Int,
    viewport: WallpaperCropViewport,
    transform: WallpaperCropTransform,
    maxPreferredScaleMultiplier: Float = DefaultWallpaperCropMaxPreferredScaleMultiplier
): WallpaperCropTransform {
    return resolveWallpaperCropTransformFromPose(
        sourceWidth = sourceWidth,
        sourceHeight = sourceHeight,
        viewport = viewport,
        pose = clampWallpaperCropPose(
            sourceWidth = sourceWidth,
            sourceHeight = sourceHeight,
            viewport = viewport,
            pose = poseFromWallpaperCropTransform(
                sourceWidth = sourceWidth,
                sourceHeight = sourceHeight,
                viewport = viewport,
                transform = transform
            ),
            maxPreferredScaleMultiplier = maxPreferredScaleMultiplier
        )
    )
}

internal fun scaleWallpaperCropTransformToViewport(
    transform: WallpaperCropTransform,
    fromViewport: WallpaperCropViewport,
    toViewport: WallpaperCropViewport
): WallpaperCropTransform {
    if (fromViewport.width <= 0f || fromViewport.height <= 0f) return transform

    val scaleX = toViewport.width / fromViewport.width
    val scaleY = toViewport.height / fromViewport.height

    return WallpaperCropTransform(
        scale = transform.scale * scaleX,
        rotationDegrees = transform.rotationDegrees,
        offsetX = transform.offsetX * scaleX,
        offsetY = transform.offsetY * scaleY
    )
}

internal fun applyWallpaperGestureTransform(
    sourceWidth: Int,
    sourceHeight: Int,
    viewport: WallpaperCropViewport,
    currentTransform: WallpaperCropTransform,
    centroid: Offset,
    pan: Offset,
    zoom: Float,
    rotationDegrees: Float
): WallpaperCropTransform {
    val pose = applyWallpaperGestureToPose(
        sourceWidth = sourceWidth,
        sourceHeight = sourceHeight,
        viewport = viewport,
        currentPose = poseFromWallpaperCropTransform(
            sourceWidth = sourceWidth,
            sourceHeight = sourceHeight,
            viewport = viewport,
            transform = currentTransform
        ),
        centroid = centroid,
        pan = pan,
        zoom = zoom,
        rotationDegrees = rotationDegrees
    )
    return resolveWallpaperCropTransformFromPose(
        sourceWidth = sourceWidth,
        sourceHeight = sourceHeight,
        viewport = viewport,
        pose = clampWallpaperCropPose(
            sourceWidth = sourceWidth,
            sourceHeight = sourceHeight,
            viewport = viewport,
            pose = pose
        )
    )
}

internal fun rotateWallpaperCropTransformToDegrees(
    sourceWidth: Int,
    sourceHeight: Int,
    viewport: WallpaperCropViewport,
    currentTransform: WallpaperCropTransform,
    rotationDegrees: Float
): WallpaperCropTransform {
    return resolveWallpaperCropTransformFromPose(
        sourceWidth = sourceWidth,
        sourceHeight = sourceHeight,
        viewport = viewport,
        pose = rotateWallpaperCropPoseToDegrees(
            sourceWidth = sourceWidth,
            sourceHeight = sourceHeight,
            viewport = viewport,
            currentPose = poseFromWallpaperCropTransform(
                sourceWidth = sourceWidth,
                sourceHeight = sourceHeight,
                viewport = viewport,
                transform = currentTransform
            ),
            rotationDegrees = rotationDegrees
        )
    )
}

internal fun doesWallpaperCropCoverViewport(
    sourceWidth: Int,
    sourceHeight: Int,
    viewport: WallpaperCropViewport,
    transform: WallpaperCropTransform
): Boolean {
    val radians = Math.toRadians(normalizeRotationDegrees(transform.rotationDegrees).toDouble())
    val cosValue = cos(radians).toFloat()
    val sinValue = sin(radians).toFloat()
    val halfSourceWidth = sourceWidth * transform.scale / 2f
    val halfSourceHeight = sourceHeight * transform.scale / 2f
    val halfViewportWidth = viewport.width / 2f
    val halfViewportHeight = viewport.height / 2f

    val corners = arrayOf(
        Pair(-halfViewportWidth, -halfViewportHeight),
        Pair(halfViewportWidth, -halfViewportHeight),
        Pair(-halfViewportWidth, halfViewportHeight),
        Pair(halfViewportWidth, halfViewportHeight)
    )

    return corners.all { (x, y) ->
        val translatedX = x - transform.offsetX
        val translatedY = y - transform.offsetY
        val localX = cosValue * translatedX + sinValue * translatedY
        val localY = -sinValue * translatedX + cosValue * translatedY
        abs(localX) <= halfSourceWidth + 0.5f && abs(localY) <= halfSourceHeight + 0.5f
    }
}

internal fun canonicalWallpaperRotationDegrees(rotationDegrees: Float): Float {
    return normalizeRotationDegrees(rotationDegrees)
}

private fun poseFromWallpaperCropTransform(
    sourceWidth: Int,
    sourceHeight: Int,
    viewport: WallpaperCropViewport,
    transform: WallpaperCropTransform
): WallpaperCropPose {
    val normalizedRotation = normalizeRotationDegrees(transform.rotationDegrees)
    val minimumScale = minimumScaleToCoverViewport(
        sourceWidth = sourceWidth,
        sourceHeight = sourceHeight,
        viewport = viewport,
        rotationDegrees = normalizedRotation
    ).coerceAtLeast(0.0001f)
    val safeScale = transform.scale.coerceAtLeast(0.0001f)
    val localScreenOffset = rotateScreenVectorToLocal(
        vector = Offset(transform.offsetX, transform.offsetY),
        rotationDegrees = normalizedRotation
    )

    return WallpaperCropPose(
        rotationDegrees = normalizedRotation,
        preferredScale = safeScale,
        localOffsetX = localScreenOffset.x / safeScale,
        localOffsetY = localScreenOffset.y / safeScale
    )
}

private data class WallpaperCropPreferredScaleBounds(
    val minimumPreferredScale: Float,
    val maximumPreferredScale: Float
)

private fun referenceWallpaperCropScale(
    sourceWidth: Int,
    sourceHeight: Int,
    viewport: WallpaperCropViewport
): Float {
    return minimumScaleToCoverViewport(
        sourceWidth = sourceWidth,
        sourceHeight = sourceHeight,
        viewport = viewport,
        rotationDegrees = 0f
    )
}

private fun resolveWallpaperCropPreferredScaleBounds(
    sourceWidth: Int,
    sourceHeight: Int,
    viewport: WallpaperCropViewport,
    maxPreferredScaleMultiplier: Float
): WallpaperCropPreferredScaleBounds {
    val minimumPreferredScale = referenceWallpaperCropScale(
        sourceWidth = sourceWidth,
        sourceHeight = sourceHeight,
        viewport = viewport
    )
    return WallpaperCropPreferredScaleBounds(
        minimumPreferredScale = minimumPreferredScale,
        maximumPreferredScale = minimumPreferredScale * maxPreferredScaleMultiplier
    )
}

private fun minimumScaleToCoverViewport(
    sourceWidth: Int,
    sourceHeight: Int,
    viewport: WallpaperCropViewport,
    rotationDegrees: Float
): Float {
    val radians = Math.toRadians(normalizeRotationDegrees(rotationDegrees).toDouble())
    val cosValue = abs(cos(radians)).toFloat()
    val sinValue = abs(sin(radians)).toFloat()
    val halfViewportWidth = viewport.width / 2f
    val halfViewportHeight = viewport.height / 2f

    val widthScale = (cosValue * halfViewportWidth + sinValue * halfViewportHeight) * 2f /
        sourceWidth.coerceAtLeast(1)
    val heightScale = (sinValue * halfViewportWidth + cosValue * halfViewportHeight) * 2f /
        sourceHeight.coerceAtLeast(1)

    return max(widthScale, heightScale).coerceAtLeast(0.0001f)
}

private fun resolveWallpaperCropLocalOffsetBounds(
    sourceWidth: Int,
    sourceHeight: Int,
    viewport: WallpaperCropViewport,
    scale: Float,
    rotationDegrees: Float
): WallpaperCropLocalOffsetBounds {
    val safeScale = scale.coerceAtLeast(0.0001f)
    val radians = Math.toRadians(normalizeRotationDegrees(rotationDegrees).toDouble())
    val absCosValue = abs(cos(radians)).toFloat()
    val absSinValue = abs(sin(radians)).toFloat()
    val halfSourceWidth = sourceWidth / 2f
    val halfSourceHeight = sourceHeight / 2f
    val halfViewportWidth = viewport.width / 2f
    val halfViewportHeight = viewport.height / 2f

    return WallpaperCropLocalOffsetBounds(
        maxLocalOffsetX = (
            halfSourceWidth - (absCosValue * halfViewportWidth + absSinValue * halfViewportHeight) / safeScale
            ).coerceAtLeast(0f),
        maxLocalOffsetY = (
            halfSourceHeight - (absSinValue * halfViewportWidth + absCosValue * halfViewportHeight) / safeScale
            ).coerceAtLeast(0f)
    )
}

private fun rotateLocalVectorToScreen(
    vector: Offset,
    rotationDegrees: Float
): Offset {
    val radians = Math.toRadians(normalizeRotationDegrees(rotationDegrees).toDouble())
    val cosValue = cos(radians).toFloat()
    val sinValue = sin(radians).toFloat()
    return Offset(
        x = cosValue * vector.x - sinValue * vector.y,
        y = sinValue * vector.x + cosValue * vector.y
    )
}

private fun rotateScreenVectorToLocal(
    vector: Offset,
    rotationDegrees: Float
): Offset {
    val radians = Math.toRadians(normalizeRotationDegrees(rotationDegrees).toDouble())
    val cosValue = cos(radians).toFloat()
    val sinValue = sin(radians).toFloat()
    return Offset(
        x = cosValue * vector.x + sinValue * vector.y,
        y = -sinValue * vector.x + cosValue * vector.y
    )
}

private fun normalizeRotationDegrees(rotationDegrees: Float): Float {
    var normalized = rotationDegrees % 360f
    if (normalized > 180f) normalized -= 360f
    if (normalized < -180f) normalized += 360f
    return normalized
}
