package com.lele.llmonitor.ui.motion

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import kotlin.math.roundToInt

object AppMotionTokens {
    val StandardDecelerateEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val StandardAccelerateEasing = CubicBezierEasing(0.3f, 0f, 1f, 1f)
    val ContainerTransformEasing = FastOutSlowInEasing

    const val RouteEnterDurationMs = 300
    const val RouteExitDurationMs = 220
    const val DialogEnterDurationMs = 280
    const val DialogExitDurationMs = 180
    const val VisibilityEnterDurationMs = 220
    const val VisibilityExitDurationMs = 180
    const val VisibilityQuickExitDurationMs = 140
    const val QuickActionHandoffDelayMs = 80
    const val StateChangeDurationMs = 220
    const val ScheduleDayExpandDurationMs = 260
    const val ContainerTransformEnterDurationMs = 360
    const val ContainerTransformExitDurationMs = 320
    const val ContainerTransformSettleDurationMs = 220
    const val CropRotateDurationMs = 420
    const val CropResetDurationMs = 520
    const val CropSettleDurationMs = 260
    const val CropSnapDurationMs = 180
    const val CropSettleSpringDamping = 0.82f
    const val CropSettleSpringStiffness = 680f

    const val DialogScrimMaxAlpha = 0.42f
    const val DialogInitialScale = 0.92f
    const val DialogExitScale = 0.98f
    const val DialogEnterSpringDamping = 0.76f
    const val DialogEnterSpringStiffness = 600f

    val CropStandardEasing = StandardDecelerateEasing
    val CropResetEasing = CubicBezierEasing(0.22f, 0.84f, 0.18f, 1f)
}

object AppMotion {
    fun routeEnterTransition(): EnterTransition {
        return slideInHorizontally(
            initialOffsetX = { fullWidth -> (fullWidth * 0.12f).roundToInt() },
            animationSpec = tween(
                durationMillis = AppMotionTokens.RouteEnterDurationMs,
                easing = AppMotionTokens.StandardDecelerateEasing
            )
        ) + fadeIn(
            animationSpec = tween(
                durationMillis = AppMotionTokens.RouteEnterDurationMs,
                easing = AppMotionTokens.StandardDecelerateEasing
            )
        )
    }

    fun routeExitTransition(): ExitTransition {
        return slideOutHorizontally(
            targetOffsetX = { fullWidth -> -(fullWidth * 0.08f).roundToInt() },
            animationSpec = tween(
                durationMillis = AppMotionTokens.RouteExitDurationMs,
                easing = AppMotionTokens.StandardAccelerateEasing
            )
        ) + fadeOut(
            animationSpec = tween(
                durationMillis = AppMotionTokens.RouteExitDurationMs,
                easing = AppMotionTokens.StandardAccelerateEasing
            )
        )
    }

    fun routePopEnterTransition(): EnterTransition {
        return slideInHorizontally(
            initialOffsetX = { fullWidth -> -(fullWidth * 0.08f).roundToInt() },
            animationSpec = tween(
                durationMillis = AppMotionTokens.RouteEnterDurationMs,
                easing = AppMotionTokens.StandardDecelerateEasing
            )
        ) + fadeIn(
            animationSpec = tween(
                durationMillis = AppMotionTokens.RouteEnterDurationMs,
                easing = AppMotionTokens.StandardDecelerateEasing
            )
        )
    }

    fun routePopExitTransition(): ExitTransition {
        return slideOutHorizontally(
            targetOffsetX = { fullWidth -> (fullWidth * 0.12f).roundToInt() },
            animationSpec = tween(
                durationMillis = AppMotionTokens.RouteExitDurationMs,
                easing = AppMotionTokens.StandardAccelerateEasing
            )
        ) + fadeOut(
            animationSpec = tween(
                durationMillis = AppMotionTokens.RouteExitDurationMs,
                easing = AppMotionTokens.StandardAccelerateEasing
            )
        )
    }

    fun dialogEnterFloatTween(): FiniteAnimationSpec<Float> {
        return tween(
            durationMillis = AppMotionTokens.DialogEnterDurationMs,
            easing = AppMotionTokens.StandardDecelerateEasing
        )
    }

    fun dialogExitFloatTween(): FiniteAnimationSpec<Float> {
        return tween(
            durationMillis = AppMotionTokens.DialogExitDurationMs,
            easing = AppMotionTokens.StandardAccelerateEasing
        )
    }

    fun dialogScaleSpring(): SpringSpec<Float> {
        return spring(
            dampingRatio = AppMotionTokens.DialogEnterSpringDamping,
            stiffness = AppMotionTokens.DialogEnterSpringStiffness
        )
    }

    fun standardVisibilityEnter(): EnterTransition {
        return fadeIn(
            animationSpec = tween(
                durationMillis = AppMotionTokens.VisibilityEnterDurationMs,
                easing = AppMotionTokens.StandardDecelerateEasing
            )
        )
    }

    fun standardVisibilityExit(): ExitTransition {
        return fadeOut(
            animationSpec = tween(
                durationMillis = AppMotionTokens.VisibilityExitDurationMs,
                easing = AppMotionTokens.StandardAccelerateEasing
            )
        )
    }

    fun quickVisibilityExit(): ExitTransition {
        return fadeOut(
            animationSpec = tween(
                durationMillis = AppMotionTokens.VisibilityQuickExitDurationMs,
                easing = AppMotionTokens.StandardAccelerateEasing
            )
        )
    }

    fun stateChangeTweenFloat(): FiniteAnimationSpec<Float> {
        return tween(
            durationMillis = AppMotionTokens.StateChangeDurationMs,
            easing = AppMotionTokens.ContainerTransformEasing
        )
    }

    fun containerTransformTweenFloat(durationMillis: Int): FiniteAnimationSpec<Float> {
        return tween(
            durationMillis = durationMillis,
            easing = AppMotionTokens.ContainerTransformEasing
        )
    }

    fun <T> cropSettleSpring(): SpringSpec<T> {
        return spring(
            dampingRatio = AppMotionTokens.CropSettleSpringDamping,
            stiffness = AppMotionTokens.CropSettleSpringStiffness
        )
    }
}
