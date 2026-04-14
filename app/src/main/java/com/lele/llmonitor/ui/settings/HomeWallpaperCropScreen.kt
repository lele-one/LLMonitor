package com.lele.llmonitor.ui.settings

import android.graphics.Bitmap
import android.net.Uri
import android.os.SystemClock
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector4D
import androidx.compose.animation.core.TwoWayConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.RotateRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.lele.llmonitor.data.HomeWallpaperManager
import androidx.navigation.NavHostController
import com.lele.llmonitor.ui.components.NavigationBarBottomInsetSpacer
import com.lele.llmonitor.ui.motion.AppMotion
import com.lele.llmonitor.ui.motion.AppMotionTokens
import com.lele.llmonitor.ui.theme.AppCorners
import com.lele.llmonitor.ui.theme.AppShapes
import com.lele.llmonitor.ui.theme.isAppInDarkTheme
import com.lele.llmonitor.ui.theme.pageSurfaceColor
import com.lele.llmonitor.ui.theme.pageSurfaceTopAppBarColors
import com.lele.llmonitor.ui.wallpaper.WallpaperCropPose
import com.lele.llmonitor.ui.wallpaper.WallpaperCropTransform
import com.lele.llmonitor.ui.wallpaper.WallpaperCropViewport
import com.lele.llmonitor.ui.wallpaper.applyWallpaperGestureToPose
import com.lele.llmonitor.ui.wallpaper.areWallpaperCropPosesEquivalent
import com.lele.llmonitor.ui.wallpaper.canonicalWallpaperRotationDegrees
import com.lele.llmonitor.ui.wallpaper.clampWallpaperCropPose
import com.lele.llmonitor.ui.wallpaper.exportWallpaperCropToFile
import com.lele.llmonitor.ui.wallpaper.initialWallpaperCropPose
import com.lele.llmonitor.ui.wallpaper.loadWallpaperCropBitmap
import com.lele.llmonitor.ui.wallpaper.rememberHomeWallpaperViewportSize
import com.lele.llmonitor.ui.wallpaper.resolveWallpaperCropPoseForRenderRotation
import com.lele.llmonitor.ui.wallpaper.resolveWallpaperCropTransformFromPose
import com.lele.llmonitor.ui.wallpaper.rotateWallpaperCropPoseToDegrees
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.coroutines.coroutineContext

internal const val HOME_WALLPAPER_CROP_SOURCE_URI_ARG = "sourceUri"
internal const val HOME_WALLPAPER_CROP_ROUTE =
    "home_wallpaper_crop?$HOME_WALLPAPER_CROP_SOURCE_URI_ARG={$HOME_WALLPAPER_CROP_SOURCE_URI_ARG}"

internal fun createHomeWallpaperCropRoute(sourceUri: Uri): String {
    return "home_wallpaper_crop?$HOME_WALLPAPER_CROP_SOURCE_URI_ARG=${Uri.encode(sourceUri.toString())}"
}

private data class WallpaperCropSourceState(
    val bitmap: Bitmap?,
    val failed: Boolean
)

private const val ROTATION_BUTTON_QUEUE_THRESHOLD = 0.9f
private const val WALLPAPER_CROP_OUTER_REGION_ALPHA = 0.24f
private const val WALLPAPER_CROP_FRAME_SCALE = 0.9f
private val WALLPAPER_CROP_BOTTOM_HORIZONTAL_PADDING = 16.dp
private val WALLPAPER_CROP_FRAME_EDGE_GAP = 18.dp
private val WALLPAPER_CROP_BOTTOM_CONTROLS_TOP_PADDING = 12.dp
private val WallpaperCropPoseVectorConverter =
    TwoWayConverter<WallpaperCropPose, AnimationVector4D>(
        convertToVector = { pose ->
            AnimationVector4D(
                pose.rotationDegrees,
                pose.preferredScale,
                pose.localOffsetX,
                pose.localOffsetY
            )
        },
        convertFromVector = { vector ->
            WallpaperCropPose(
                rotationDegrees = vector.v1,
                preferredScale = vector.v2,
                localOffsetX = vector.v3,
                localOffsetY = vector.v4
            )
        }
    )

private enum class CropTransformAnimationKind {
    Rotate,
    Reset,
    Settle,
    Snap
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeWallpaperCropScreen(
    navController: NavHostController,
    encodedSourceUri: String
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val exportViewport = rememberHomeWallpaperViewportSize()
    val sourceUri = remember(encodedSourceUri) {
        Uri.parse(Uri.decode(encodedSourceUri))
    }
    val requestedMaxDimension = remember(exportViewport) {
        max(exportViewport.width.toInt(), exportViewport.height.toInt()).coerceAtLeast(2048)
    }
    var sourceBitmapState by remember(sourceUri, requestedMaxDimension) {
        mutableStateOf(WallpaperCropSourceState(bitmap = null, failed = false))
    }
    LaunchedEffect(context, sourceUri, requestedMaxDimension) {
        val bitmap = loadWallpaperCropBitmap(
            context = context,
            sourceUri = sourceUri,
            requestedMaxDimension = requestedMaxDimension
        )
        sourceBitmapState = WallpaperCropSourceState(
            bitmap = bitmap,
            failed = bitmap == null
        )
    }
    val sourceBitmap = sourceBitmapState.bitmap
    val sourceBitmapLoadFailed = sourceBitmapState.failed
    val sourceImageBitmap = remember(sourceBitmap) { sourceBitmap?.asImageBitmap() }
    val viewportAspectRatio = remember(exportViewport) { exportViewport.width / exportViewport.height }
    val density = LocalDensity.current
    val outerRegionAlpha = WALLPAPER_CROP_OUTER_REGION_ALPHA
    val controlCardShape = AppShapes.g2(AppCorners.lg)
    val controlCardBackdropColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = outerRegionAlpha)
    val controlCardBorderColor = settingsCardBorderColor(accentColor = settingsAccentColor())
    var bottomBarHeightPx by remember(sourceUri) { mutableIntStateOf(0) }
    val cropBottomPadding = (
        with(density) { bottomBarHeightPx.toDp() } - WALLPAPER_CROP_BOTTOM_CONTROLS_TOP_PADDING
    ).coerceAtLeast(0.dp) + WALLPAPER_CROP_FRAME_EDGE_GAP

    var previewViewport by remember(sourceUri) {
        mutableStateOf(WallpaperCropViewport(0f, 0f))
    }
    var previewViewportSize by remember(sourceUri) {
        mutableStateOf(IntSize.Zero)
    }
    var cropPose by remember(sourceUri) {
        mutableStateOf<WallpaperCropPose?>(null)
    }
    val poseAnimator = remember(sourceUri) {
        Animatable(
            initialValue = WallpaperCropPose(
                rotationDegrees = 0f,
                preferredScale = 1f,
                localOffsetX = 0f,
                localOffsetY = 0f
            ),
            typeConverter = WallpaperCropPoseVectorConverter
        )
    }
    var transformAnimationJob by remember(sourceUri) { mutableStateOf<Job?>(null) }
    var transformAnimationStartedAtMillis by remember(sourceUri) { mutableStateOf<Long?>(null) }
    var transformAnimationDurationMillis by remember(sourceUri) { mutableIntStateOf(0) }
    var activeTransformAnimationKind by remember(sourceUri) {
        mutableStateOf<CropTransformAnimationKind?>(null)
    }
    var hasQueuedQuarterTurn by remember(sourceUri) { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }

    val cropTransform = run {
        val bitmap = sourceBitmap
        val pose = cropPose
        if (
            bitmap == null ||
            pose == null ||
            previewViewport.width <= 0f ||
            previewViewport.height <= 0f
        ) {
            null
        } else {
            resolveWallpaperCropTransformFromPose(
                sourceWidth = bitmap.width,
                sourceHeight = bitmap.height,
                viewport = previewViewport,
                pose = pose
            )
        }
    }
    val rotationControlDegrees = cropPose?.rotationDegrees ?: 0f
    val isTransformAnimating = transformAnimationJob != null
    val canSave = sourceBitmap != null &&
        cropTransform != null &&
        previewViewport.width > 0f &&
        previewViewport.height > 0f &&
        !isSaving &&
        !isTransformAnimating

    fun buildInitialCropPose(): WallpaperCropPose? {
        val bitmap = sourceBitmap ?: return null
        if (previewViewport.width <= 0f || previewViewport.height <= 0f) return null
        return initialWallpaperCropPose(
            sourceWidth = bitmap.width,
            sourceHeight = bitmap.height,
            viewport = previewViewport
        )
    }

    fun applyCropPose(nextPose: WallpaperCropPose) {
        cropPose = nextPose
    }

    fun clearAnimationState(clearQueuedQuarterTurn: Boolean) {
        transformAnimationStartedAtMillis = null
        transformAnimationDurationMillis = 0
        activeTransformAnimationKind = null
        if (clearQueuedQuarterTurn) {
            hasQueuedQuarterTurn = false
        }
    }

    fun syncPoseAnimator(nextPose: WallpaperCropPose) {
        coroutineScope.launch {
            poseAnimator.stop()
            poseAnimator.snapTo(nextPose)
        }
    }

    fun interruptPoseAnimation(clearQueuedQuarterTurn: Boolean = true) {
        val activePose = cropPose ?: return
        val activeJob = transformAnimationJob
        transformAnimationJob = null
        activeJob?.cancel()
        clearAnimationState(clearQueuedQuarterTurn = clearQueuedQuarterTurn)
        applyCropPose(activePose)
        syncPoseAnimator(activePose)
    }

    fun snapPose(
        nextPose: WallpaperCropPose,
        clearQueuedQuarterTurn: Boolean = true
    ) {
        val activeJob = transformAnimationJob
        transformAnimationJob = null
        activeJob?.cancel()
        clearAnimationState(clearQueuedQuarterTurn = clearQueuedQuarterTurn)
        applyCropPose(nextPose)
        syncPoseAnimator(nextPose)
    }

    fun animatePose(
        targetPose: WallpaperCropPose,
        animationSpec: AnimationSpec<WallpaperCropPose>,
        durationEstimateMillis: Int,
        kind: CropTransformAnimationKind,
        onFinished: (() -> Unit)? = null
    ) {
        val startPose = cropPose ?: return
        if (areWallpaperCropPosesEquivalent(startPose, targetPose)) {
            snapPose(
                nextPose = targetPose,
                clearQueuedQuarterTurn = false
            )
            onFinished?.invoke()
            return
        }

        transformAnimationStartedAtMillis = SystemClock.uptimeMillis()
        transformAnimationDurationMillis = durationEstimateMillis
        activeTransformAnimationKind = kind
        transformAnimationJob = coroutineScope.launch {
            poseAnimator.stop()
            poseAnimator.snapTo(startPose)
            try {
                val motionScaleFactor = coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f
                if (motionScaleFactor <= 0f) {
                    applyCropPose(targetPose)
                    poseAnimator.snapTo(targetPose)
                } else {
                    poseAnimator.animateTo(
                        targetValue = targetPose,
                        animationSpec = animationSpec
                    ) {
                        applyCropPose(value)
                    }
                    applyCropPose(targetPose)
                }
            } finally {
                if (transformAnimationJob === this) {
                    transformAnimationJob = null
                    clearAnimationState(clearQueuedQuarterTurn = false)
                    onFinished?.invoke()
                }
            }
        }
    }

    fun settlePoseIfNeeded() {
        val bitmap = sourceBitmap ?: return
        val currentPose = cropPose ?: return
        val targetPose = clampWallpaperCropPose(
            sourceWidth = bitmap.width,
            sourceHeight = bitmap.height,
            viewport = previewViewport,
            pose = currentPose
        )
        if (areWallpaperCropPosesEquivalent(currentPose, targetPose)) {
            snapPose(
                nextPose = targetPose,
                clearQueuedQuarterTurn = false
            )
            return
        }

        animatePose(
            targetPose = targetPose,
            animationSpec = AppMotion.cropSettleSpring(),
            durationEstimateMillis = AppMotionTokens.CropSettleDurationMs,
            kind = CropTransformAnimationKind.Settle
        )
    }

    fun applyRulerRotation(rawRotationDegrees: Float) {
        val bitmap = sourceBitmap ?: return
        val currentPose = cropPose ?: return
        applyCropPose(
            resolveWallpaperCropPoseForRenderRotation(
                sourceWidth = bitmap.width,
                sourceHeight = bitmap.height,
                viewport = previewViewport,
                basePose = currentPose,
                rotationDegrees = rawRotationDegrees
            )
        )
    }

    fun normalizedCurrentRotationPose(): WallpaperCropPose? {
        return cropPose?.let { currentPose ->
            currentPose.copy(
                rotationDegrees = canonicalWallpaperRotationDegrees(currentPose.rotationDegrees)
            )
        }
    }

    fun animateRotationDegrees(nextRotationDegrees: Float) {
        val bitmap = sourceBitmap ?: return
        val startPose = cropPose ?: return
        val targetPose = resolveWallpaperCropPoseForRenderRotation(
            sourceWidth = bitmap.width,
            sourceHeight = bitmap.height,
            viewport = previewViewport,
            basePose = startPose,
            rotationDegrees = nextRotationDegrees
        )

        transformAnimationStartedAtMillis = SystemClock.uptimeMillis()
        transformAnimationDurationMillis = AppMotionTokens.CropRotateDurationMs
        activeTransformAnimationKind = CropTransformAnimationKind.Rotate
        transformAnimationJob = coroutineScope.launch {
            poseAnimator.stop()
            poseAnimator.snapTo(startPose)
            try {
                val motionScaleFactor = coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f
                if (motionScaleFactor <= 0f) {
                    applyCropPose(targetPose)
                    poseAnimator.snapTo(targetPose)
                } else {
                    val rotationAnimator = Animatable(startPose.rotationDegrees)
                    rotationAnimator.animateTo(
                        targetValue = nextRotationDegrees,
                        animationSpec = tween(
                            durationMillis = AppMotionTokens.CropRotateDurationMs,
                            easing = AppMotionTokens.CropStandardEasing
                        )
                    ) {
                        applyCropPose(
                            resolveWallpaperCropPoseForRenderRotation(
                                sourceWidth = bitmap.width,
                                sourceHeight = bitmap.height,
                                viewport = previewViewport,
                                basePose = startPose,
                                rotationDegrees = value
                            )
                        )
                    }
                    applyCropPose(targetPose)
                    poseAnimator.snapTo(targetPose)
                }
            } finally {
                if (transformAnimationJob === this) {
                    transformAnimationJob = null
                    clearAnimationState(clearQueuedQuarterTurn = false)
                    val shouldRunQueuedQuarterTurn = hasQueuedQuarterTurn
                    hasQueuedQuarterTurn = false
                    if (shouldRunQueuedQuarterTurn) {
                        animateRotationDegrees(nextRotationDegrees + 90f)
                    } else {
                        val normalizedPose = normalizedCurrentRotationPose()
                        if (normalizedPose != null) {
                            snapPose(
                                nextPose = normalizedPose,
                                clearQueuedQuarterTurn = false
                            )
                        }
                    }
                }
            }
        }
    }

    fun resetCropPose() {
        if (isTransformAnimating) return
        val initialPose = buildInitialCropPose() ?: return
        hasQueuedQuarterTurn = false
        animatePose(
            targetPose = initialPose,
            animationSpec = tween(
                durationMillis = AppMotionTokens.CropResetDurationMs,
                easing = AppMotionTokens.CropResetEasing
            ),
            durationEstimateMillis = AppMotionTokens.CropResetDurationMs,
            kind = CropTransformAnimationKind.Reset
        )
    }

    fun queueQuarterTurnAnimation() {
        if (cropPose == null || isSaving) return
        if (!isTransformAnimating) {
            hasQueuedQuarterTurn = false
            animateRotationDegrees(rotationControlDegrees + 90f)
            return
        }
        if (hasQueuedQuarterTurn) return
        if (activeTransformAnimationKind != CropTransformAnimationKind.Rotate) return

        val startedAtMillis = transformAnimationStartedAtMillis ?: return
        if (transformAnimationDurationMillis <= 0) return
        val elapsedMillis = SystemClock.uptimeMillis() - startedAtMillis
        val progress = elapsedMillis.toFloat() / transformAnimationDurationMillis.toFloat()
        if (progress >= ROTATION_BUTTON_QUEUE_THRESHOLD) {
            hasQueuedQuarterTurn = true
        }
    }

    LaunchedEffect(sourceBitmap, previewViewport) {
        if (sourceBitmap == null) return@LaunchedEffect
        if (previewViewport.width <= 0f || previewViewport.height <= 0f) return@LaunchedEffect
        if (cropPose != null) return@LaunchedEffect

        val initialPose = buildInitialCropPose() ?: return@LaunchedEffect
        applyCropPose(initialPose)
        poseAnimator.snapTo(initialPose)
        clearAnimationState(clearQueuedQuarterTurn = true)
    }

    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        containerColor = pageSurfaceColor(),
        topBar = {
            TopAppBar(
                colors = pageSurfaceTopAppBarColors(),
                title = { Text(com.lele.llmonitor.i18n.l10n("裁切壁纸")) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = com.lele.llmonitor.i18n.l10n("返回"))
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            val activeBitmap = sourceBitmap ?: return@IconButton
                            val activeTransform = cropTransform ?: return@IconButton
                            if (previewViewport.width <= 0f || previewViewport.height <= 0f) {
                                return@IconButton
                            }

                            isSaving = true
                            coroutineScope.launch {
                                val exportedUri = exportWallpaperCropToFile(
                                    context = context,
                                    sourceBitmap = activeBitmap,
                                    previewViewport = previewViewport,
                                    previewTransform = activeTransform
                                )
                                if (exportedUri == null) {
                                    isSaving = false
                                    Toast.makeText(context, com.lele.llmonitor.i18n.l10n("保存失败，请重试"), Toast.LENGTH_SHORT).show()
                                    return@launch
                                }

                                HomeWallpaperManager.importWallpaper(
                                    uri = exportedUri,
                                    deleteSourceAfterImport = true
                                ) { success ->
                                    isSaving = false
                                    if (!success) {
                                        Toast.makeText(context, com.lele.llmonitor.i18n.l10n("壁纸导入失败，请重试"), Toast.LENGTH_SHORT).show()
                                        return@importWallpaper
                                    }
                                    navController.popBackStack()
                                }
                            }
                        },
                        enabled = canSave
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(20.dp)
                            )
                        } else {
                            Icon(Icons.Rounded.Check, contentDescription = com.lele.llmonitor.i18n.l10n("保存"))
                        }
                    }
                }
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .padding(
                        start = WALLPAPER_CROP_BOTTOM_HORIZONTAL_PADDING,
                        top = WALLPAPER_CROP_BOTTOM_CONTROLS_TOP_PADDING,
                        end = WALLPAPER_CROP_BOTTOM_HORIZONTAL_PADDING
                    )
                    .onSizeChanged { size ->
                        bottomBarHeightPx = size.height
                    }
            ) {
                SettingsContentCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = controlCardBackdropColor,
                            shape = controlCardShape
                        ),
                    contentPadding = PaddingValues(18.dp),
                    containerColorOverride = Color.Transparent,
                    borderColorOverride = controlCardBorderColor
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = com.lele.llmonitor.i18n.l10n("角度"),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = formatRotationDegrees(rotationControlDegrees),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        AngleRuler(
                            angle = rotationControlDegrees,
                            onDragStarted = {
                                interruptPoseAnimation()
                            },
                            onAngleChange = ::applyRulerRotation,
                            onDragStopped = {
                                settlePoseIfNeeded()
                            },
                            enabled = cropPose != null && !isSaving,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    resetCropPose()
                                },
                                enabled = cropPose != null && !isSaving && !isTransformAnimating,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Rounded.Refresh, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(com.lele.llmonitor.i18n.l10n("重置"))
                            }
                            FilledTonalButton(
                                onClick = {
                                    queueQuarterTurnAnimation()
                                },
                                enabled = cropPose != null && !isSaving,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.RotateRight, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(com.lele.llmonitor.i18n.l10n("90度"))
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(WALLPAPER_CROP_BOTTOM_HORIZONTAL_PADDING))
                NavigationBarBottomInsetSpacer()
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = paddingValues.calculateTopPadding())
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center
        ) {
            if (sourceBitmapLoadFailed) {
                SettingsContentCard(
                    modifier = Modifier.padding(horizontal = 24.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(com.lele.llmonitor.i18n.l10n("图片加载失败"))
                        OutlinedButton(onClick = { navController.popBackStack() }) {
                            Text(com.lele.llmonitor.i18n.l10n("返回重选"))
                        }
                    }
                }
                return@Box
            }

            if (sourceBitmap == null || sourceImageBitmap == null) {
                CircularProgressIndicator()
                return@Box
            }

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp)
                    .padding(top = WALLPAPER_CROP_FRAME_EDGE_GAP, bottom = cropBottomPadding),
                contentAlignment = Alignment.Center
            ) {
                val maxFrameWidth = if (maxWidth / viewportAspectRatio <= maxHeight) {
                    maxWidth
                } else {
                    maxHeight * viewportAspectRatio
                }
                val targetWidth = maxFrameWidth * WALLPAPER_CROP_FRAME_SCALE
                val targetHeight = targetWidth / viewportAspectRatio
                val cropFrameShape = AppShapes.g2(AppCorners.lg)

                WallpaperCropPreview(
                    imageBitmap = sourceImageBitmap,
                    cropTransform = cropTransform,
                    imageAlpha = outerRegionAlpha,
                    modifier = Modifier.fillMaxSize()
                )

                Box(
                    modifier = Modifier
                        .size(targetWidth, targetHeight)
                        .clip(cropFrameShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .border(
                            border = BorderStroke(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f)
                            ),
                            shape = cropFrameShape
                        )
                        .onSizeChanged { newSize ->
                            if (newSize == previewViewportSize) return@onSizeChanged
                            val bitmap = sourceBitmap ?: return@onSizeChanged

                            val nextViewport = WallpaperCropViewport(
                                width = newSize.width.toFloat(),
                                height = newSize.height.toFloat()
                            )
                            val currentPose = cropPose

                            previewViewport = nextViewport
                            previewViewportSize = newSize

                            val nextPose = when {
                                currentPose == null -> initialWallpaperCropPose(
                                    sourceWidth = bitmap.width,
                                    sourceHeight = bitmap.height,
                                    viewport = nextViewport
                                )

                                else -> clampWallpaperCropPose(
                                    sourceWidth = bitmap.width,
                                    sourceHeight = bitmap.height,
                                    viewport = nextViewport,
                                    pose = currentPose
                                )
                            }
                            snapPose(nextPose)
                        }
                        .wallpaperCropTransformInput(
                            enabled = cropPose != null && !isSaving,
                            onGestureStart = {
                                interruptPoseAnimation()
                            },
                            onGesture = { centroid, pan, zoom ->
                                val currentPose = cropPose ?: return@wallpaperCropTransformInput
                                val bitmap = sourceBitmap ?: return@wallpaperCropTransformInput
                                applyCropPose(
                                    applyWallpaperGestureToPose(
                                        sourceWidth = bitmap.width,
                                        sourceHeight = bitmap.height,
                                        viewport = previewViewport,
                                        currentPose = currentPose,
                                        centroid = centroid,
                                        pan = pan,
                                        zoom = zoom,
                                        rotationDegrees = 0f
                                    )
                                )
                            },
                            onGestureEnd = {
                                settlePoseIfNeeded()
                            }
                        )
                ) {
                    WallpaperCropPreview(
                        imageBitmap = sourceImageBitmap,
                        cropTransform = cropTransform,
                        modifier = Modifier.fillMaxSize()
                    )
                    WallpaperCropGridOverlay()
                }
            }
        }
    }
}

private fun Modifier.wallpaperCropTransformInput(
    enabled: Boolean,
    onGestureStart: () -> Unit,
    onGesture: (centroid: Offset, pan: Offset, zoom: Float) -> Unit,
    onGestureEnd: () -> Unit
): Modifier {
    if (!enabled) return this

    return pointerInput(enabled) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            var gestureActive = false

            while (true) {
                val event = awaitPointerEvent()
                val pan = event.calculatePan()
                val zoom = event.calculateZoom()
                val hasTransformChange = pan != Offset.Zero || zoom != 1f

                if (hasTransformChange) {
                    if (!gestureActive) {
                        gestureActive = true
                        onGestureStart()
                    }
                    onGesture(
                        event.calculateCentroid(useCurrent = true),
                        pan,
                        zoom
                    )
                    event.changes.forEach { change ->
                        if (change.positionChanged()) {
                            change.consume()
                        }
                    }
                }

                if (event.changes.none { it.pressed }) {
                    break
                }
            }

            if (gestureActive) {
                onGestureEnd()
            }
        }
    }
}

@Composable
private fun WallpaperCropPreview(
    imageBitmap: ImageBitmap,
    cropTransform: WallpaperCropTransform?,
    modifier: Modifier = Modifier,
    imageAlpha: Float = 1f
) {
    val transform = cropTransform ?: return
    val density = LocalDensity.current
    val imageWidth = with(density) { imageBitmap.width.toDp() }
    val imageHeight = with(density) { imageBitmap.height.toDp() }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Image(
            bitmap = imageBitmap,
            contentDescription = null,
            contentScale = ContentScale.None,
            modifier = Modifier
                .requiredSize(imageWidth, imageHeight)
                .graphicsLayer {
                    translationX = transform.offsetX
                    translationY = transform.offsetY
                    rotationZ = transform.rotationDegrees
                    scaleX = transform.scale
                    scaleY = transform.scale
                    alpha = imageAlpha
                    transformOrigin = TransformOrigin.Center
                }
        )
    }
}

@Composable
private fun WallpaperCropGridOverlay() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val strokeColor = Color.White.copy(alpha = 0.22f)
        val firstVertical = size.width / 3f
        val secondVertical = size.width * 2f / 3f
        val firstHorizontal = size.height / 3f
        val secondHorizontal = size.height * 2f / 3f

        drawLine(strokeColor, Offset(firstVertical, 0f), Offset(firstVertical, size.height))
        drawLine(strokeColor, Offset(secondVertical, 0f), Offset(secondVertical, size.height))
        drawLine(strokeColor, Offset(0f, firstHorizontal), Offset(size.width, firstHorizontal))
        drawLine(strokeColor, Offset(0f, secondHorizontal), Offset(size.width, secondHorizontal))
    }
}

@Composable
private fun AngleRuler(
    angle: Float,
    onDragStarted: () -> Unit,
    onAngleChange: (Float) -> Unit,
    onDragStopped: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val pixelsPerDegree = with(density) { 10.dp.toPx() }
    val indicatorColor = MaterialTheme.colorScheme.primary
    val isDark = isAppInDarkTheme()
    val majorTickColor = indicatorColor.copy(alpha = if (isDark) 0.82f else 0.78f)
    val minorTickColor = indicatorColor.copy(alpha = if (isDark) 0.48f else 0.42f)
    val axisColor = indicatorColor.copy(alpha = if (isDark) 0.28f else 0.22f)
    val edgeFadeWidth = with(density) { 72.dp.toPx() }
    val indicatorHeight = with(density) { 42.dp.toPx() }
    val indicatorWidth = with(density) { 10.dp.toPx() }
    val layerPaint = remember { Paint() }
    var dragAngle by remember { mutableStateOf(angle) }

    LaunchedEffect(angle) {
        dragAngle = angle
    }

    Canvas(
        modifier = modifier
            .height(72.dp)
            .draggable(
                orientation = Orientation.Horizontal,
                enabled = enabled,
                onDragStarted = {
                    onDragStarted()
                },
                onDragStopped = {
                    onDragStopped()
                },
                state = rememberDraggableState { delta ->
                    dragAngle -= delta / pixelsPerDegree
                    onAngleChange(dragAngle)
                }
            )
    ) {
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        val visibleDegrees = (size.width / pixelsPerDegree / 2f).roundToInt() + 2
        val startDegree = dragAngle.roundToInt() - visibleDegrees
        val endDegree = dragAngle.roundToInt() + visibleDegrees
        val fadeFraction = (edgeFadeWidth / size.width).coerceIn(0f, 0.5f)
        val fadeMaskStops = buildAngleRulerFadeMaskStops(fadeFraction)
        drawContext.canvas.saveLayer(Rect(Offset.Zero, size), layerPaint)
        try {
            drawLine(
                color = axisColor,
                start = Offset(0f, centerY),
                end = Offset(size.width, centerY),
                strokeWidth = 1.dp.toPx()
            )

            for (degree in startDegree..endDegree) {
                val x = centerX + (degree - dragAngle) * pixelsPerDegree
                if (x < 0f || x > size.width) continue

                val isMajor = degree % 15 == 0
                val tickHeight = if (isMajor) 28.dp.toPx() else 16.dp.toPx()
                drawLine(
                    color = if (isMajor) majorTickColor else minorTickColor,
                    start = Offset(x, centerY - tickHeight / 2f),
                    end = Offset(x, centerY + tickHeight / 2f),
                    strokeWidth = if (isMajor) 2.dp.toPx() else 1.dp.toPx()
                )
            }

            drawRect(
                brush = Brush.horizontalGradient(
                    colorStops = fadeMaskStops
                ),
                topLeft = Offset.Zero,
                size = size,
                blendMode = BlendMode.DstIn
            )
        } finally {
            drawContext.canvas.restore()
        }

        drawRoundRect(
            color = indicatorColor.copy(alpha = 0.18f),
            topLeft = Offset(
                x = centerX - indicatorWidth / 2f,
                y = centerY - indicatorHeight / 2f
            ),
            size = Size(indicatorWidth, indicatorHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                x = indicatorWidth / 2f,
                y = indicatorHeight / 2f
            )
        )
        drawCircle(
            color = indicatorColor,
            radius = 4.dp.toPx(),
            center = Offset(centerX, centerY),
            style = Stroke(width = 2.dp.toPx())
        )
        drawLine(
            color = indicatorColor,
            start = Offset(centerX, centerY - 18.dp.toPx()),
            end = Offset(centerX, centerY + 18.dp.toPx()),
            strokeWidth = 2.dp.toPx()
        )
    }
}

private fun buildAngleRulerFadeMaskStops(fadeFraction: Float): Array<Pair<Float, Color>> {
    if (fadeFraction <= 0f) return arrayOf(
        0f to Color.Black,
        1f to Color.Black
    )
    if (fadeFraction >= 0.5f) return arrayOf(
        0f to Color.Transparent,
        0.5f to Color.Black,
        1f to Color.Transparent
    )

    val sampleCount = 256
    val stops = ArrayList<Pair<Float, Color>>(sampleCount * 2 + 2)
    repeat(sampleCount + 1) { index ->
        val t = index / sampleCount.toFloat()
        val position = fadeFraction * t
        stops.add(position to Color.Black.copy(alpha = flatStep(t)))
    }
    stops.add(fadeFraction to Color.Black)
    stops.add((1f - fadeFraction) to Color.Black)
    repeat(sampleCount + 1) { index ->
        val t = index / sampleCount.toFloat()
        val position = (1f - fadeFraction) + fadeFraction * t
        stops.add(position to Color.Black.copy(alpha = flatStep(1f - t)))
    }
    return stops.toTypedArray()
}

private fun flatStep(value: Float): Float {
    val t = value.coerceIn(0f, 1f)
    if (t <= 0f) return 0f
    if (t >= 1f) return 1f

    val left = kotlin.math.exp((-1f / t).toDouble())
    val right = kotlin.math.exp((-1f / (1f - t)).toDouble())
    return (left / (left + right)).toFloat()
}

private fun formatRotationDegrees(rotationDegrees: Float): String {
    val roundedTenths = (canonicalWallpaperRotationDegrees(rotationDegrees) * 10f).roundToInt() / 10f
    val integerDegrees = roundedTenths.roundToInt().toFloat()
    return if (roundedTenths == integerDegrees) {
        "${integerDegrees.toInt()}°"
    } else {
        "${roundedTenths}°"
    }
}
