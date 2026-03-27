package com.lele.llmonitor.ui.settings

import android.os.Build
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lele.llmonitor.ui.theme.AppCorners
import com.lele.llmonitor.ui.theme.AppShapes
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

internal val SettingsCardHorizontalPadding = 16.dp
internal val SettingsCardVerticalPadding = 8.dp
private val SettingsHeroContentPadding = 22.dp
private val SettingsHeroSwitchAlignmentOffset = 4.dp
private val SettingsHeroGlassStrokeWidth = 1.dp
private val SettingsHeroGlassInnerStrokeWidth = 0.75.dp
private val SettingsHeroFallbackSceneBlur = 36.dp
private const val SettingsHeroSceneBlurRadiusPx = 55f
private val SettingsHeroSheenBlur = 20.dp
private const val SettingsHeroLoopTau = 6.2831855f
private const val SettingsHeroFieldPhaseADurationMillis = 4500
private const val SettingsHeroFieldPhaseBDurationMillis = 6500
private const val SettingsHeroFieldPhaseCDurationMillis = 9000
private const val SettingsHeroMaterialPhaseDurationMillis = 12000
private const val SettingsHeroSheenPhaseDurationMillis = 16000
private val SettingsHeroCorner = AppCorners.xl
internal val LocalSettingsHeroBlurEnabled = compositionLocalOf { true }
internal val LocalSettingsHeroRawSceneDebug = compositionLocalOf { false }

private data class SettingsHeroPalette(
    val backgroundBase: Color,
    val fieldMist: Color,
    val fieldShadow: Color,
    val fieldGlowPrimary: Color,
    val fieldGlowSecondary: Color,
    val fieldGlowTertiary: Color,
    val fieldGlowAccent: Color,
    val auroraPrimary: Color,
    val auroraSecondary: Color,
    val hotspot: Color,
    val streamColors: List<Color>,
    val glassTint: Color,
    val glassStrokeOuter: Color,
    val glassStrokeInner: Color,
    val glassBottomShadow: Color,
    val glassSheen: Color
)

internal fun settingsCardOuterPadding(
    vertical: Dp = SettingsCardVerticalPadding
): PaddingValues = PaddingValues(
    horizontal = SettingsCardHorizontalPadding,
    vertical = vertical
)

@Composable
internal fun SettingsInlineFieldActionRow(
    modifier: Modifier = Modifier,
    spacing: Dp = 8.dp,
    minFieldWidth: Dp = 136.dp,
    field: @Composable (Modifier) -> Unit,
    action: @Composable (Modifier) -> Unit
) {
    SubcomposeLayout(modifier = modifier.fillMaxWidth()) { constraints ->
        val spacingPx = spacing.roundToPx()
        val minFieldWidthPx = minFieldWidth.roundToPx()

        val preferredActionPlaceables = subcompose("actionPreferred") {
            action(Modifier)
        }.map { measurable ->
            measurable.measure(
                constraints.copy(
                    minWidth = 0,
                    minHeight = 0
                )
            )
        }
        val preferredActionWidthPx = preferredActionPlaceables.maxOfOrNull { it.width } ?: 0

        val availableWidth = constraints.maxWidth
        val maxActionWidthForField = (availableWidth - spacingPx - minFieldWidthPx).coerceAtLeast(0)
        val actionWidthPx = preferredActionWidthPx.coerceAtMost(maxActionWidthForField)
            .takeIf { preferredActionWidthPx > maxActionWidthForField }
            ?: preferredActionWidthPx
        val fieldWidthPx = (availableWidth - spacingPx - actionWidthPx).coerceAtLeast(0)

        val fieldPlaceables = subcompose("field") {
            field(Modifier.widthIn(min = 0.dp))
        }.map { measurable ->
            measurable.measure(
                constraints.copy(
                    minWidth = 0,
                    maxWidth = fieldWidthPx
                )
            )
        }

        val actionPlaceables = subcompose("action") {
            action(Modifier.widthIn(min = 0.dp, max = actionWidthPx.toDp()))
        }.map { measurable ->
            measurable.measure(
                constraints.copy(
                    minWidth = 0,
                    maxWidth = actionWidthPx.coerceAtLeast(0)
                )
            )
        }

        val rowHeight = maxOf(
            fieldPlaceables.maxOfOrNull { it.height } ?: 0,
            actionPlaceables.maxOfOrNull { it.height } ?: 0
        )

        layout(availableWidth, rowHeight) {
            fieldPlaceables.forEach { placeable ->
                placeable.placeRelative(0, (rowHeight - placeable.height) / 2)
            }
            val actionX = (availableWidth - (actionPlaceables.maxOfOrNull { it.width } ?: 0)).coerceAtLeast(0)
            actionPlaceables.forEach { placeable ->
                placeable.placeRelative(actionX, (rowHeight - placeable.height) / 2)
            }
        }
    }
}

@Composable
internal fun SettingsContentCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    accentColor: Color = settingsAccentColor(),
    enabled: Boolean = true,
    containerColorOverride: Color? = null,
    borderColorOverride: Color? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val containerColor = containerColorOverride ?: settingsCardContainerColor(
        accentColor = accentColor,
        enabled = enabled
    )
    val borderColor = borderColorOverride ?: settingsCardBorderColor(
        accentColor = accentColor,
        enabled = enabled
    )

    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            enabled = enabled,
            shape = AppShapes.g2(AppCorners.lg),
            colors = CardDefaults.cardColors(containerColor = containerColor),
            border = BorderStroke(1.dp, borderColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(contentPadding),
                content = content
            )
        }
    } else {
        Card(
            modifier = modifier.fillMaxWidth(),
            shape = AppShapes.g2(AppCorners.lg),
            colors = CardDefaults.cardColors(containerColor = containerColor),
            border = BorderStroke(1.dp, borderColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(contentPadding),
                content = content
            )
        }
    }
}

@Composable
internal fun settingsCardContainerColor(
    accentColor: Color = settingsAccentColor(),
    enabled: Boolean = true
): Color {
    val baseColor = accentColor.copy(alpha = 0.08f)
        .compositeOver(MaterialTheme.colorScheme.surfaceContainerLow)
    return if (enabled) {
        baseColor
    } else {
        lerp(baseColor, MaterialTheme.colorScheme.surfaceContainerLow, 0.52f)
    }
}

@Composable
internal fun settingsCardBorderColor(
    accentColor: Color = settingsAccentColor(),
    enabled: Boolean = true
): Color {
    return if (enabled) {
        accentColor.copy(alpha = 0.16f)
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.62f)
    }
}

@Composable
internal fun SettingsHeroCard(
    title: String,
    subtitle: String,
    chips: List<String>,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    iconSize: Dp = 24.dp,
    accentContainerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    accentContentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    secondaryGradientColor: Color = MaterialTheme.colorScheme.tertiaryContainer,
    switchLabel: String? = null,
    switchChecked: Boolean = false,
    switchEnabled: Boolean = true,
    onSwitchCheckedChange: ((Boolean) -> Unit)? = null
) {
    SettingsHeroCard(
        title = title,
        subtitle = subtitle,
        chips = chips,
        iconPainter = null,
        iconImageVector = icon,
        modifier = modifier,
        iconSize = iconSize,
        accentContainerColor = accentContainerColor,
        accentContentColor = accentContentColor,
        secondaryGradientColor = secondaryGradientColor,
        switchLabel = switchLabel,
        switchChecked = switchChecked,
        switchEnabled = switchEnabled,
        onSwitchCheckedChange = onSwitchCheckedChange
    )
}

@Composable
internal fun SettingsHeroCard(
    title: String,
    subtitle: String,
    chips: List<String>,
    iconPainter: Painter,
    modifier: Modifier = Modifier,
    iconSize: Dp = 24.dp,
    accentContainerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    accentContentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    secondaryGradientColor: Color = MaterialTheme.colorScheme.tertiaryContainer,
    switchLabel: String? = null,
    switchChecked: Boolean = false,
    switchEnabled: Boolean = true,
    onSwitchCheckedChange: ((Boolean) -> Unit)? = null
) {
    SettingsHeroCard(
        title = title,
        subtitle = subtitle,
        chips = chips,
        iconPainter = iconPainter,
        iconImageVector = null,
        modifier = modifier,
        iconSize = iconSize,
        accentContainerColor = accentContainerColor,
        accentContentColor = accentContentColor,
        secondaryGradientColor = secondaryGradientColor,
        switchLabel = switchLabel,
        switchChecked = switchChecked,
        switchEnabled = switchEnabled,
        onSwitchCheckedChange = onSwitchCheckedChange
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsHeroCard(
    title: String,
    subtitle: String,
    chips: List<String>,
    iconPainter: Painter?,
    iconImageVector: ImageVector?,
    modifier: Modifier = Modifier,
    iconSize: Dp = 24.dp,
    accentContainerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    accentContentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    secondaryGradientColor: Color = MaterialTheme.colorScheme.tertiaryContainer,
    switchLabel: String? = null,
    switchChecked: Boolean = false,
    switchEnabled: Boolean = true,
    onSwitchCheckedChange: ((Boolean) -> Unit)? = null
) {
    val themeAccentColor = settingsAccentColor()
    val themeSecondaryFlowColor = MaterialTheme.colorScheme.secondaryContainer
    val palette = rememberSettingsHeroPalette(
        themeAccentColor = themeAccentColor,
        themeSecondaryFlowColor = themeSecondaryFlowColor,
        accentContainerColor = accentContainerColor,
        secondaryGradientColor = secondaryGradientColor
    )
    val fieldPhaseA = rememberHeroPhase(
        durationMillis = SettingsHeroFieldPhaseADurationMillis,
        label = "hero_field_a"
    )
    val fieldPhaseB = rememberHeroPhase(
        durationMillis = SettingsHeroFieldPhaseBDurationMillis,
        label = "hero_field_b"
    )
    val fieldPhaseC = rememberHeroPhase(
        durationMillis = SettingsHeroFieldPhaseCDurationMillis,
        label = "hero_field_c"
    )
    val materialPhase = rememberHeroPhase(
        durationMillis = SettingsHeroMaterialPhaseDurationMillis,
        label = "hero_material"
    )
    val sheenPhase = rememberHeroPhase(
        durationMillis = SettingsHeroSheenPhaseDurationMillis,
        label = "hero_sheen"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = AppShapes.g2(SettingsHeroCorner),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .clip(AppShapes.g2(SettingsHeroCorner))
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(palette.backgroundBase)
            )
            SettingsHeroBlurredScene(
                modifier = Modifier.matchParentSize(),
                palette = palette,
                fieldPhaseA = fieldPhaseA,
                fieldPhaseB = fieldPhaseB,
                fieldPhaseC = fieldPhaseC,
                materialPhase = materialPhase
            )
            SettingsHeroGlassMaterial(
                modifier = Modifier.matchParentSize(),
                palette = palette,
                materialPhase = materialPhase,
                sheenPhase = sheenPhase
            )
            SettingsHeroContent(
                title = title,
                subtitle = subtitle,
                chips = chips,
                iconPainter = iconPainter,
                iconImageVector = iconImageVector,
                iconSize = iconSize,
                accentContentColor = accentContentColor,
                switchLabel = switchLabel,
                switchChecked = switchChecked,
                switchEnabled = switchEnabled,
                onSwitchCheckedChange = onSwitchCheckedChange
            )
        }
    }
}

@Composable
private fun rememberHeroPhase(
    durationMillis: Int,
    label: String
): Float {
    val transition = rememberInfiniteTransition(label = label)
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = SettingsHeroLoopTau,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "${label}_phase"
    ).value
}

private fun heroWaveCentered(
    phase: Float,
    shift: Float = 0f
): Float = sin(phase + shift).toFloat()

private fun heroOrbitOffset(
    xAmplitude: Float,
    yAmplitude: Float,
    phase: Float,
    shift: Float = 0f
): androidx.compose.ui.geometry.Offset = androidx.compose.ui.geometry.Offset(
    x = xAmplitude * heroWaveCentered(phase, shift),
    y = yAmplitude * heroWaveCentered(phase, shift + (SettingsHeroLoopTau / 4f))
)

private fun heroLissajousOffset(
    xAmplitude: Float,
    yAmplitude: Float,
    phase: Float,
    xFrequency: Float = 1f,
    yFrequency: Float = 1.618f,
    xShift: Float = 0f,
    yShift: Float = 0f
): Offset = Offset(
    x = xAmplitude * sin((phase * xFrequency) + xShift),
    y = yAmplitude * sin((phase * yFrequency) + yShift)
)

private data class HeroOklch(
    val l: Float,
    val c: Float,
    val h: Float
)

private fun Float.srgbToLinear(): Double {
    val channel = coerceIn(0f, 1f).toDouble()
    return if (channel <= 0.04045) {
        channel / 12.92
    } else {
        ((channel + 0.055) / 1.055).pow(2.4)
    }
}

private fun Double.linearToSrgb(): Float {
    val channel = if (this <= 0.0031308) {
        this * 12.92
    } else {
        1.055 * this.pow(1.0 / 2.4) - 0.055
    }
    return channel.toFloat().coerceIn(0f, 1f)
}

private fun Color.toHeroOklch(): HeroOklch {
    val r = red.srgbToLinear()
    val g = green.srgbToLinear()
    val b = blue.srgbToLinear()

    val l = 0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b
    val m = 0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b
    val s = 0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b

    val lRoot = l.pow(1.0 / 3.0)
    val mRoot = m.pow(1.0 / 3.0)
    val sRoot = s.pow(1.0 / 3.0)

    val labL = 0.2104542553 * lRoot + 0.7936177850 * mRoot - 0.0040720468 * sRoot
    val labA = 1.9779984951 * lRoot - 2.4285922050 * mRoot + 0.4505937099 * sRoot
    val labB = 0.0259040371 * lRoot + 0.7827717662 * mRoot - 0.8086757660 * sRoot

    val chroma = sqrt(labA * labA + labB * labB).toFloat()
    val hue = Math.toDegrees(atan2(labB, labA)).toFloat().let {
        if (it < 0f) it + 360f else it
    }

    return HeroOklch(
        l = labL.toFloat().coerceIn(0f, 1f),
        c = chroma.coerceIn(0f, 0.37f),
        h = hue
    )
}

private fun heroOklchColor(
    l: Float,
    c: Float,
    h: Float,
    alpha: Float = 1f
): Color {
    val hueRadians = Math.toRadians((((h % 360f) + 360f) % 360f).toDouble())
    val safeL = l.coerceIn(0f, 1f).toDouble()
    val safeC = c.coerceIn(0f, 0.37f).toDouble()
    val labA = safeC * cos(hueRadians)
    val labB = safeC * sin(hueRadians)

    val lRoot = safeL + 0.3963377774 * labA + 0.2158037573 * labB
    val mRoot = safeL - 0.1055613458 * labA - 0.0638541728 * labB
    val sRoot = safeL - 0.0894841775 * labA - 1.2914855480 * labB

    val ll = lRoot * lRoot * lRoot
    val mm = mRoot * mRoot * mRoot
    val ss = sRoot * sRoot * sRoot

    val r = (+4.0767416621 * ll - 3.3077115913 * mm + 0.2309699292 * ss).linearToSrgb()
    val g = (-1.2684380046 * ll + 2.6097574011 * mm - 0.3413193965 * ss).linearToSrgb()
    val b = (-0.0041960863 * ll - 0.7034186147 * mm + 1.7076147010 * ss).linearToSrgb()

    return Color(red = r, green = g, blue = b, alpha = alpha.coerceIn(0f, 1f))
}

private fun tuneHeroColorForSurface(
    color: Color,
    pageSurface: Color,
    cardSurface: Color
): Color {
    val source = color.toHeroOklch()
    val page = pageSurface.toHeroOklch()
    val card = cardSurface.toHeroOklch()
    val surfaceLight = (page.l + card.l) * 0.5f
    val surfaceChroma = (page.c + card.c) * 0.5f
    val isLightSurface = surfaceLight >= 0.5f

    val targetDistance = (
        0.06f +
            source.c * 0.95f +
            abs(source.l - surfaceLight) * 0.24f +
            abs(page.l - card.l) * 0.18f
        ).coerceIn(0.07f, 0.22f)

    val targetLight = if (isLightSurface) {
        (surfaceLight - targetDistance * 0.38f).coerceIn(0.64f, 0.88f)
    } else {
        (surfaceLight + targetDistance * 0.82f).coerceIn(0.42f, 0.88f)
    }

    val targetChroma = (
        source.c * 0.90f +
            0.03f +
            abs(targetLight - surfaceLight) * 0.10f -
            surfaceChroma * 0.12f
        ).coerceIn(if (isLightSurface) 0.05f else 0.04f, if (isLightSurface) 0.20f else 0.18f)

    return heroOklchColor(
        l = targetLight,
        c = targetChroma,
        h = source.h,
        alpha = color.alpha
    )
}

private fun tuneHeroBaseForSurfaces(
    base: Color,
    pageSurface: Color,
    cardSurface: Color,
    accentColor: Color
): Color {
    val page = pageSurface.toHeroOklch()
    val card = cardSurface.toHeroOklch()
    val accent = accentColor.toHeroOklch()
    val baseTone = base.toHeroOklch()
    val surfaceLight = (page.l + card.l) * 0.5f
    val isLightSurface = surfaceLight >= 0.5f

    val targetLight = if (isLightSurface) {
        (card.l - (0.003f + accent.c * 0.05f)).coerceIn(0.94f, 0.988f)
    } else {
        (card.l + (0.014f + accent.c * 0.12f)).coerceIn(0.14f, 0.28f)
    }

    val targetChroma = (
        accent.c * 0.18f +
            abs(page.l - card.l) * 0.08f +
            baseTone.c * 0.12f
        ).coerceIn(0.008f, 0.05f)

    return heroOklchColor(
        l = targetLight,
        c = targetChroma,
        h = accent.h,
        alpha = base.alpha
    )
}

private fun deriveHeroSupportTone(
    pageSurface: Color,
    cardSurface: Color,
    accentColor: Color
): Color {
    val page = pageSurface.toHeroOklch()
    val card = cardSurface.toHeroOklch()
    val accent = accentColor.toHeroOklch()
    val surfaceLight = (page.l + card.l) * 0.5f
    val isLightSurface = surfaceLight >= 0.5f

    val targetLight = if (isLightSurface) {
        (surfaceLight - (accent.c * 0.012f)).coerceIn(0.945f, 0.985f)
    } else {
        (surfaceLight + (0.008f + accent.c * 0.06f)).coerceIn(0.16f, 0.30f)
    }

    val targetChroma = (accent.c * if (isLightSurface) 0.12f else 0.22f)
        .coerceIn(0.004f, 0.04f)

    return heroOklchColor(
        l = targetLight,
        c = targetChroma,
        h = accent.h,
    )
}

@Composable
private fun rememberSettingsHeroPalette(
    themeAccentColor: Color,
    themeSecondaryFlowColor: Color,
    accentContainerColor: Color,
    secondaryGradientColor: Color
): SettingsHeroPalette {
    val colorScheme = MaterialTheme.colorScheme
    val pageSurface = colorScheme.surface
    val regularCardSurface = themeAccentColor.copy(alpha = 0.08f)
        .compositeOver(colorScheme.surfaceContainerLow)
    val surface = pageSurface
    val surfaceHigh = colorScheme.surfaceContainerHigh
    val isLightTheme = surface.luminance() > 0.5f
    fun toned(color: Color, alpha: Float): Color {
        return color.copy(alpha = alpha)
    }
    fun lifted(color: Color, amount: Float): Color = lerp(color, Color.White, amount)
    fun deepened(color: Color, amount: Float): Color = lerp(color, surface, amount)
    return androidx.compose.runtime.remember(
        themeAccentColor,
        themeSecondaryFlowColor,
        accentContainerColor,
        secondaryGradientColor,
        colorScheme.primary,
        colorScheme.secondary,
        colorScheme.tertiary,
        colorScheme.inversePrimary,
        surface,
        surfaceHigh,
        regularCardSurface
    ) {
        val surfaceL = surface.luminance()
        val darkFactor = 1f - surfaceL
        val cardGap = abs(regularCardSurface.luminance() - pageSurface.luminance())
        val liftFactor = (surfaceL * 0.18f).coerceIn(0.04f, 0.18f)
        val containerBlend = (darkFactor * 0.28f).coerceIn(0f, 0.28f)

        val vividPrimaryRaw = lifted(
            lerp(colorScheme.primary, colorScheme.primaryContainer, containerBlend),
            liftFactor
        )
        val vividSecondaryRaw = lifted(
            lerp(colorScheme.secondary, colorScheme.secondaryContainer, containerBlend * 0.9f),
            liftFactor * 0.8f
        )
        val vividTertiaryRaw = lifted(
            lerp(colorScheme.tertiary, colorScheme.tertiaryContainer, containerBlend * 0.95f),
            liftFactor * 0.9f
        )
        val vividInverseRaw = lifted(
            lerp(colorScheme.inversePrimary, colorScheme.primaryContainer, containerBlend * 0.72f),
            liftFactor * 0.75f
        )

        val vividPrimary = tuneHeroColorForSurface(vividPrimaryRaw, pageSurface, regularCardSurface)
        val vividSecondary = tuneHeroColorForSurface(vividSecondaryRaw, pageSurface, regularCardSurface)
        val vividTertiary = tuneHeroColorForSurface(vividTertiaryRaw, pageSurface, regularCardSurface)
        val vividInverse = tuneHeroColorForSurface(vividInverseRaw, pageSurface, regularCardSurface)
        val deepPrimary = tuneHeroColorForSurface(
            deepened(colorScheme.primary, 0.10f + darkFactor * 0.14f),
            pageSurface,
            regularCardSurface
        )
        val supportTone = deriveHeroSupportTone(
            pageSurface = pageSurface,
            cardSurface = regularCardSurface,
            accentColor = vividPrimary
        )

        val accentEnergy = (
            vividPrimary.toHeroOklch().c +
                vividSecondary.toHeroOklch().c +
                vividTertiary.toHeroOklch().c +
                vividInverse.toHeroOklch().c
            ) / 4f

        val baseBlend = (0.02f + accentEnergy * 0.08f + darkFactor * 0.03f).coerceIn(0.02f, 0.12f)
        val baseAlpha = (0.94f + surfaceL * 0.045f).coerceIn(0.93f, 0.985f)
        val mistAlpha = (0.02f + darkFactor * 0.05f + cardGap * 0.10f).coerceIn(0.02f, 0.11f)
        val shadowAlpha = (0.004f + darkFactor * darkFactor * 0.22f).coerceIn(0.004f, 0.12f)
        val primaryGlowAlpha = (0.16f + accentEnergy * 0.18f + darkFactor * 0.10f).coerceIn(0.18f, 0.36f)
        val secondaryGlowAlpha = (0.13f + accentEnergy * 0.15f + darkFactor * 0.09f).coerceIn(0.15f, 0.32f)
        val tertiaryGlowAlpha = (0.12f + accentEnergy * 0.13f + darkFactor * 0.08f).coerceIn(0.14f, 0.29f)
        val accentGlowAlpha = (0.11f + accentEnergy * 0.13f + darkFactor * 0.07f).coerceIn(0.13f, 0.27f)
        val auroraPrimaryAlpha = (0.08f + accentEnergy * 0.09f + darkFactor * 0.05f).coerceIn(0.09f, 0.18f)
        val auroraSecondaryAlpha = (0.07f + accentEnergy * 0.08f + darkFactor * 0.04f).coerceIn(0.08f, 0.15f)
        val hotspotAlpha = (0.06f + darkFactor * 0.09f).coerceIn(0.06f, 0.15f)
        val streamAAlpha = (0.20f + accentEnergy * 0.19f + darkFactor * 0.08f).coerceIn(0.24f, 0.40f)
        val streamBAlpha = (0.16f + accentEnergy * 0.17f + darkFactor * 0.07f).coerceIn(0.20f, 0.36f)
        val streamCAlpha = (0.14f + accentEnergy * 0.15f + darkFactor * 0.06f).coerceIn(0.18f, 0.33f)
        val streamDAlpha = (0.13f + accentEnergy * 0.14f + darkFactor * 0.05f).coerceIn(0.16f, 0.30f)
        val streamEAlpha = (0.15f + accentEnergy * 0.15f + darkFactor * 0.05f).coerceIn(0.18f, 0.33f)
        val streamShadowAlpha = (darkFactor * darkFactor * 0.24f).coerceIn(0f, 0.16f)
        val glassTintAlpha = (0.034f + surfaceL * 0.022f).coerceIn(0.038f, 0.056f)
        val glassOuterStrokeAlpha = (0.16f + darkFactor * 0.04f).coerceIn(0.17f, 0.20f)
        val glassInnerStrokeAlpha = (0.07f + darkFactor * 0.03f).coerceIn(0.08f, 0.10f)
        val glassBottomAlpha = (0.01f + darkFactor * darkFactor * 0.12f).coerceIn(0.01f, 0.08f)
        val glassSheenAlpha = (0.06f + darkFactor * 0.03f).coerceIn(0.07f, 0.09f)
        val baseRaw = lerp(
            surfaceHigh,
            accentContainerColor,
            baseBlend
        ).copy(alpha = baseAlpha)
            .compositeOver(surfaceHigh)
        SettingsHeroPalette(
            backgroundBase = tuneHeroBaseForSurfaces(
                base = baseRaw,
                pageSurface = pageSurface,
                cardSurface = regularCardSurface,
                accentColor = vividPrimary
            ),
            fieldMist = toned(lerp(surfaceHigh, vividTertiary, 0.08f + darkFactor * 0.08f), mistAlpha),
            fieldShadow = toned(supportTone, shadowAlpha),
            fieldGlowPrimary = toned(vividPrimary, primaryGlowAlpha),
            fieldGlowSecondary = toned(vividSecondary, secondaryGlowAlpha),
            fieldGlowTertiary = toned(vividTertiary, tertiaryGlowAlpha),
            fieldGlowAccent = toned(vividInverse, accentGlowAlpha),
            auroraPrimary = toned(lifted(vividPrimary, 0.06f + surfaceL * 0.04f), auroraPrimaryAlpha),
            auroraSecondary = toned(lifted(vividSecondary, 0.04f + surfaceL * 0.04f), auroraSecondaryAlpha),
            hotspot = Color.White.copy(alpha = hotspotAlpha),
            streamColors = listOf(
                toned(lifted(vividPrimary, 0.08f + surfaceL * 0.10f), streamAAlpha),
                toned(vividSecondary, streamBAlpha),
                toned(vividTertiary, streamCAlpha),
                toned(vividInverse, streamDAlpha),
                toned(tuneHeroColorForSurface(themeAccentColor, pageSurface, regularCardSurface), streamEAlpha),
                toned(supportTone, streamShadowAlpha),
                toned(lifted(vividPrimary, 0.08f + surfaceL * 0.10f), streamAAlpha)
            ),
            glassTint = Color.White.copy(alpha = glassTintAlpha),
            glassStrokeOuter = Color.White.copy(alpha = glassOuterStrokeAlpha),
            glassStrokeInner = Color.White.copy(alpha = glassInnerStrokeAlpha),
            glassBottomShadow = lerp(supportTone, surfaceHigh, 0.62f).copy(alpha = glassBottomAlpha),
            glassSheen = Color.White.copy(alpha = glassSheenAlpha)
        )
    }
}

@Composable
private fun SettingsHeroBlurredScene(
    modifier: Modifier = Modifier,
    palette: SettingsHeroPalette,
    fieldPhaseA: Float,
    fieldPhaseB: Float,
    fieldPhaseC: Float,
    materialPhase: Float
) {
    val blurEnabled = LocalSettingsHeroBlurEnabled.current
    Box(
        modifier = modifier
            .graphicsLayer {
                compositingStrategy = CompositingStrategy.Offscreen
            }
            .then(
                if (!blurEnabled) {
                    Modifier
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Modifier.graphicsLayer {
                        val blurEffect = android.graphics.RenderEffect.createBlurEffect(
                            SettingsHeroSceneBlurRadiusPx,
                            SettingsHeroSceneBlurRadiusPx,
                            android.graphics.Shader.TileMode.CLAMP
                        )
                        renderEffect = blurEffect.asComposeRenderEffect()
                    }
                } else {
                    Modifier.blur(SettingsHeroFallbackSceneBlur)
                }
            )
    ) {
        SettingsHeroBackgroundField(
            modifier = Modifier.matchParentSize(),
            palette = palette,
            fieldPhaseA = fieldPhaseA,
            fieldPhaseB = fieldPhaseB,
            fieldPhaseC = fieldPhaseC,
            materialPhase = materialPhase
        )
    }
}

@Composable
private fun SettingsHeroBackgroundField(
    modifier: Modifier = Modifier,
    palette: SettingsHeroPalette,
    fieldPhaseA: Float,
    fieldPhaseB: Float,
    fieldPhaseC: Float,
    materialPhase: Float
) {
    Canvas(modifier = modifier) {
        val isLightTheme = palette.backgroundBase.luminance() > 0.5f
        val mistHeadAlpha = if (isLightTheme) 0.64f else 0.92f
        val mistTailAlpha = if (isLightTheme) 0.16f else 0.28f
        val shadowTailAlpha = if (isLightTheme) 0.10f else 0.24f
        val streamLayerAAlpha = if (isLightTheme) 0.26f else 0.52f
        val streamLayerBAlpha = if (isLightTheme) 0.22f else 0.36f
        val streamLayerCAlpha = if (isLightTheme) 0.20f else 0.34f
        val radialGlowTailAlpha = if (isLightTheme) 0.40f else 0.48f
        val radialAccentTailAlpha = if (isLightTheme) 0.44f else 0.50f
        val radialMixTailAlpha = if (isLightTheme) 0.68f else 0.76f
        val width = size.width
        val height = size.height
        val longSide = maxOf(width, height)
        val fieldRadius = longSide * 1.18f
        val primaryCenter = Offset(width * 0.08f, height * 0.18f) +
            heroOrbitOffset(width * 0.20f, height * 0.14f, fieldPhaseA, 0.15f)
        val secondaryCenter = Offset(width * 0.90f, height * 0.22f) +
            heroOrbitOffset(width * 0.18f, height * 0.16f, fieldPhaseB, 0.95f)
        val tertiaryCenter = Offset(width * 0.22f, height * 0.88f) +
            heroOrbitOffset(width * 0.24f, height * 0.14f, fieldPhaseC, 1.45f)
        val quaternaryCenter = Offset(width * 0.86f, height * 0.82f) +
            heroOrbitOffset(width * 0.18f, height * 0.18f, materialPhase, 2.2f)
        val hotspotCenter = Offset(width * 0.52f, height * 0.48f) +
            heroOrbitOffset(width * 0.18f, height * 0.14f, materialPhase, 0.70f)
        val flowShiftA = width * 0.22f * heroWaveCentered(fieldPhaseA, 0.35f)
        val flowShiftB = width * 0.20f * heroWaveCentered(fieldPhaseB, 1.25f)
        val flowShiftC = height * 0.14f * heroWaveCentered(fieldPhaseC, 0.85f)
        val fieldDriftA = heroLissajousOffset(
            xAmplitude = width * 0.24f,
            yAmplitude = height * 0.20f,
            phase = fieldPhaseA,
            xFrequency = 1f,
            yFrequency = 1.37f,
            xShift = 0.4f,
            yShift = 1.1f
        )
        val fieldDriftB = heroLissajousOffset(
            xAmplitude = width * 0.22f,
            yAmplitude = height * 0.18f,
            phase = fieldPhaseB,
            xFrequency = 1.12f,
            yFrequency = 1.73f,
            xShift = 1.2f,
            yShift = 0.3f
        )
        val fieldDriftC = heroLissajousOffset(
            xAmplitude = width * 0.18f,
            yAmplitude = height * 0.24f,
            phase = fieldPhaseC,
            xFrequency = 0.86f,
            yFrequency = 1.54f,
            xShift = 2.2f,
            yShift = 1.7f
        )

        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    palette.fieldMist.copy(alpha = palette.fieldMist.alpha * mistHeadAlpha),
                    palette.fieldMist.copy(alpha = palette.fieldMist.alpha * mistTailAlpha),
                    Color.Transparent
                ),
                center = Offset(width * 0.50f, height * 0.50f),
                radius = longSide * 1.16f
            )
        )

        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    palette.fieldShadow,
                    palette.fieldShadow.copy(alpha = palette.fieldShadow.alpha * shadowTailAlpha),
                    Color.Transparent
                ),
                center = Offset(width * 0.54f, height * 0.56f),
                radius = longSide
            )
        )

        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    palette.streamColors[0],
                    palette.streamColors[2],
                    palette.streamColors[4],
                    Color.Transparent
                ),
                start = Offset(
                    -width * 0.42f + fieldDriftA.x,
                    -height * 0.24f + fieldDriftA.y
                ),
                end = Offset(
                    width * 1.34f + fieldDriftB.x,
                    height * 1.12f + fieldDriftB.y
                )
            ),
            alpha = streamLayerAAlpha
        )

        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    palette.streamColors[3],
                    palette.streamColors[1],
                    palette.streamColors[5],
                    Color.Transparent
                ),
                start = Offset(
                    -width * 0.36f + fieldDriftB.x,
                    height * 1.06f + fieldDriftC.y
                ),
                end = Offset(
                    width * 1.28f + fieldDriftC.x,
                    -height * 0.18f + fieldDriftA.y
                )
            ),
            alpha = streamLayerBAlpha
        )

        drawRect(
            brush = Brush.linearGradient(
                colorStops = arrayOf(
                    0f to Color.Transparent,
                    0.18f to palette.fieldGlowPrimary.copy(alpha = palette.fieldGlowPrimary.alpha * 0.30f),
                    0.38f to Color.Transparent,
                    0.54f to palette.fieldGlowSecondary.copy(alpha = palette.fieldGlowSecondary.alpha * 0.34f),
                    0.74f to palette.fieldGlowTertiary.copy(alpha = palette.fieldGlowTertiary.alpha * 0.32f),
                    0.90f to palette.fieldGlowAccent.copy(alpha = palette.fieldGlowAccent.alpha * 0.26f),
                    1f to Color.Transparent
                ),
                start = Offset(
                    -width * 0.30f + fieldDriftC.x,
                    height * 0.08f + fieldDriftA.y
                ),
                end = Offset(
                    width * 1.26f + fieldDriftA.x,
                    height * 0.92f + fieldDriftC.y
                )
            ),
            alpha = streamLayerCAlpha
        )

        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    palette.fieldGlowPrimary,
                    palette.fieldGlowPrimary.copy(alpha = palette.fieldGlowPrimary.alpha * radialGlowTailAlpha),
                    Color.Transparent
                ),
                center = primaryCenter,
                radius = fieldRadius
            )
        )

        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    palette.fieldGlowSecondary,
                    palette.fieldGlowSecondary.copy(alpha = palette.fieldGlowSecondary.alpha * radialGlowTailAlpha),
                    Color.Transparent
                ),
                center = secondaryCenter,
                radius = fieldRadius * 0.92f
            )
        )

        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    palette.fieldGlowAccent,
                    palette.fieldGlowAccent.copy(alpha = palette.fieldGlowAccent.alpha * radialAccentTailAlpha),
                    Color.Transparent
                ),
                center = quaternaryCenter,
                radius = fieldRadius * 0.88f
            )
        )

        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    palette.fieldGlowTertiary,
                    palette.fieldGlowAccent.copy(alpha = palette.fieldGlowAccent.alpha * radialMixTailAlpha),
                    Color.Transparent
                ),
                center = tertiaryCenter,
                radius = fieldRadius * 0.88f
            )
        )

        rotate(
            degrees = -13f + 4f * heroWaveCentered(fieldPhaseC, 0.55f),
            pivot = Offset(width * 0.54f, height * 0.44f)
        ) {
            val drift = flowShiftC
            drawRect(
                brush = Brush.linearGradient(
                    colorStops = arrayOf(
                        0f to Color.Transparent,
                        0.10f to Color.Transparent,
                        0.24f to palette.fieldGlowAccent.copy(alpha = palette.fieldGlowAccent.alpha * 0.36f),
                        0.40f to palette.auroraPrimary,
                        0.54f to palette.auroraSecondary,
                        0.70f to palette.fieldGlowSecondary.copy(alpha = palette.fieldGlowSecondary.alpha * 0.28f),
                        0.90f to Color.Transparent,
                        1f to Color.Transparent
                    ),
                    start = Offset(-width * 0.42f + flowShiftA, height * -0.02f + drift),
                    end = Offset(width * 1.30f + flowShiftA, height * 0.96f + drift)
                ),
                topLeft = Offset(-width * 0.46f + flowShiftA, -height * 0.14f + drift),
                size = Size(width * 1.92f, height * 1.18f)
            )
        }

        rotate(
            degrees = 12f + 3f * heroWaveCentered(materialPhase, 1.05f),
            pivot = Offset(width * 0.48f, height * 0.52f)
        ) {
            val drift = height * 0.12f * heroWaveCentered(fieldPhaseA, 1.75f)
            drawRect(
                brush = Brush.linearGradient(
                    colorStops = arrayOf(
                        0f to Color.Transparent,
                        0.10f to Color.Transparent,
                        0.24f to palette.fieldGlowPrimary.copy(alpha = palette.fieldGlowPrimary.alpha * 0.24f),
                        0.40f to palette.auroraSecondary.copy(alpha = palette.auroraSecondary.alpha * 0.74f),
                        0.54f to palette.auroraPrimary.copy(alpha = palette.auroraPrimary.alpha * 0.58f),
                        0.72f to palette.fieldGlowTertiary.copy(alpha = palette.fieldGlowTertiary.alpha * 0.26f),
                        0.90f to Color.Transparent,
                        1f to Color.Transparent
                    ),
                    start = Offset(-width * 0.34f + flowShiftB, height * 0.86f + drift),
                    end = Offset(width * 1.24f + flowShiftB, height * 0.10f + drift)
                ),
                topLeft = Offset(-width * 0.40f + flowShiftB, height * -0.02f + drift),
                size = Size(width * 1.84f, height * 1.12f)
            )
        }

        drawRect(
            brush = Brush.linearGradient(
                colorStops = arrayOf(
                    0f to Color.Transparent,
                    0.20f to palette.fieldGlowSecondary.copy(alpha = palette.fieldGlowSecondary.alpha * 0.24f),
                    0.46f to palette.fieldGlowPrimary.copy(alpha = palette.fieldGlowPrimary.alpha * 0.30f),
                    0.72f to palette.fieldGlowAccent.copy(alpha = palette.fieldGlowAccent.alpha * 0.20f),
                    1f to Color.Transparent
                ),
                start = Offset(-width * 0.32f + flowShiftA, height * 0.08f - flowShiftC),
                end = Offset(width * 1.22f + flowShiftB, height * 0.94f + flowShiftC)
            ),
            topLeft = Offset(-width * 0.38f, -height * 0.12f),
            size = Size(width * 1.72f, height * 1.28f)
        )

        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    palette.hotspot,
                    palette.hotspot.copy(alpha = palette.hotspot.alpha * 0.34f),
                    Color.Transparent
                ),
                center = hotspotCenter,
                radius = fieldRadius * 0.34f
            )
        )
    }
}

@Composable
private fun SettingsHeroGlassMaterial(
    modifier: Modifier = Modifier,
    palette: SettingsHeroPalette,
    materialPhase: Float,
    sheenPhase: Float
) {
    val rawSceneDebug = LocalSettingsHeroRawSceneDebug.current
    val shape = AppShapes.g2(SettingsHeroCorner)
    Box(
        modifier = modifier
            .clip(shape)
            .graphicsLayer {
                compositingStrategy = CompositingStrategy.Offscreen
            }
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val isLightTheme = palette.backgroundBase.luminance() > 0.5f
            val darkFactor = 1f - palette.backgroundBase.luminance()
            fun shapePathForInset(insetPx: Float): Path? {
                val inset = insetPx.coerceAtLeast(0f)
                val insetSize = Size(
                    width = (size.width - inset * 2f).coerceAtLeast(0f),
                    height = (size.height - inset * 2f).coerceAtLeast(0f)
                )
                if (insetSize.width <= 0f || insetSize.height <= 0f) return null
                val insetPath = shape.createOutline(
                    size = insetSize,
                    layoutDirection = layoutDirection,
                    density = this
                ).toPath()
                if (inset <= 0f) return insetPath
                return Path().apply { addPath(insetPath, Offset(inset, inset)) }
            }

            val outlinePath = shapePathForInset(0f) ?: return@Canvas
            val glassTintBodyAlpha = if (rawSceneDebug) {
                0.12f
            } else if (isLightTheme) {
                0.84f
            } else {
                0.92f
            }
            val glassBottomLift = if (rawSceneDebug) {
                0.08f
            } else {
                (0.18f + darkFactor * darkFactor * 0.92f).coerceIn(0.18f, 0.68f)
            }

            drawPath(
                path = outlinePath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        palette.glassTint.copy(alpha = palette.glassTint.alpha * if (rawSceneDebug) 0.18f else 1f),
                        palette.glassTint.copy(alpha = palette.glassTint.alpha * glassTintBodyAlpha),
                        palette.glassBottomShadow.copy(
                            alpha = palette.glassBottomShadow.alpha * if (rawSceneDebug) 0.18f else 1f
                        )
                    ),
                    startY = 0f,
                    endY = size.height
                )
            )

            clipPath(outlinePath) {
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            palette.glassBottomShadow.copy(alpha = palette.glassBottomShadow.alpha * glassBottomLift)
                        ),
                        startY = size.height * 0.44f,
                        endY = size.height
                    ),
                    topLeft = Offset(0f, size.height * 0.44f),
                    size = Size(size.width, size.height * 0.56f)
                )
            }

            val edgeStrokeWidthPx = SettingsHeroGlassStrokeWidth.toPx()
            val edgePath = shapePathForInset(edgeStrokeWidthPx / 2f)
            if (edgePath != null) {
                // Keep one geometric curve and stack lighting on the same path.
                val innerStrokeWidthPx = SettingsHeroGlassInnerStrokeWidth.toPx()
                val edgeGlowWidthPx = edgeStrokeWidthPx + innerStrokeWidthPx * 2.4f
                val edgeHotlineWidthPx = (edgeStrokeWidthPx * 0.58f).coerceAtLeast(0.5f)

                drawPath(
                    path = edgePath,
                    color = palette.glassStrokeInner.copy(
                        alpha = palette.glassStrokeInner.alpha * if (rawSceneDebug) 0.22f else 0.52f
                    ),
                    style = Stroke(width = edgeGlowWidthPx)
                )
                drawPath(
                    path = edgePath,
                    color = palette.glassStrokeOuter.copy(
                        alpha = palette.glassStrokeOuter.alpha * if (rawSceneDebug) 0.72f else 1f
                    ),
                    style = Stroke(width = edgeStrokeWidthPx)
                )
                drawPath(
                    path = edgePath,
                    color = palette.glassStrokeInner.copy(
                        alpha = palette.glassStrokeInner.alpha * if (rawSceneDebug) 0.35f else 0.82f
                    ),
                    style = Stroke(width = edgeHotlineWidthPx)
                )
            }
        }
        if (!rawSceneDebug) {
            SettingsHeroGlassSheen(
                modifier = Modifier.matchParentSize(),
                palette = palette,
                phase = sheenPhase
            )
        }
    }
}

private fun Outline.toPath(): Path = when (this) {
    is Outline.Generic -> path
    is Outline.Rounded -> Path().apply { addRoundRect(roundRect) }
    is Outline.Rectangle -> Path().apply { addRect(rect) }
}

@Composable
private fun SettingsHeroGlassSheen(
    modifier: Modifier = Modifier,
    palette: SettingsHeroPalette,
    phase: Float
) {
    Canvas(
        modifier = modifier
            .graphicsLayer {
                compositingStrategy = CompositingStrategy.Offscreen
            }
            .blur(SettingsHeroSheenBlur)
    ) {
        val width = size.width
        val height = size.height
        val orbit = heroOrbitOffset(width * 0.08f, height * 0.06f, phase, 0.25f)
        val sheenWidth = width * 0.22f
        val top = -height * 0.18f
        val left = width * 0.42f + orbit.x

        rotate(
            degrees = 16f + 4f * heroWaveCentered(phase, 1.15f),
            pivot = Offset(width * 0.50f + orbit.x, height * 0.42f + orbit.y)
        ) {
            drawRect(
                brush = Brush.linearGradient(
                    colorStops = arrayOf(
                        0f to Color.Transparent,
                        0.38f to Color.Transparent,
                        0.54f to palette.glassSheen,
                        0.70f to palette.glassSheen.copy(alpha = palette.glassSheen.alpha * 0.30f),
                        1f to Color.Transparent
                    ),
                    start = Offset(left, top),
                    end = Offset(left + sheenWidth, height * 1.08f)
                ),
                topLeft = Offset(left, top),
                size = Size(sheenWidth, height * 1.36f)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsHeroContent(
    title: String,
    subtitle: String,
    chips: List<String>,
    iconPainter: Painter?,
    iconImageVector: ImageVector?,
    iconSize: Dp,
    accentContentColor: Color,
    switchLabel: String?,
    switchChecked: Boolean,
    switchEnabled: Boolean,
    onSwitchCheckedChange: ((Boolean) -> Unit)?
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(SettingsHeroContentPadding),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                color = accentContentColor.copy(alpha = 0.12f),
                shape = AppShapes.g2(AppCorners.md)
            ) {
                when {
                    iconImageVector != null -> Icon(
                        imageVector = iconImageVector,
                        contentDescription = null,
                        tint = accentContentColor,
                        modifier = Modifier
                            .padding(12.dp)
                            .size(iconSize)
                    )

                    iconPainter != null -> Icon(
                        painter = iconPainter,
                        contentDescription = null,
                        tint = accentContentColor,
                        modifier = Modifier
                            .padding(12.dp)
                            .size(iconSize)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = accentContentColor
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = accentContentColor.copy(alpha = 0.84f)
                    )
                }
            }
            if (onSwitchCheckedChange != null) {
                Switch(
                    modifier = Modifier.offset(x = SettingsHeroSwitchAlignmentOffset),
                    checked = switchChecked,
                    onCheckedChange = onSwitchCheckedChange,
                    enabled = switchEnabled,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.primary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.36f),
                        uncheckedThumbColor = MaterialTheme.colorScheme.surfaceBright,
                        uncheckedTrackColor = accentContentColor.copy(alpha = 0.18f),
                        uncheckedBorderColor = Color.Transparent,
                        checkedBorderColor = Color.Transparent
                    )
                )
            }
        }

        if (!switchLabel.isNullOrBlank() && onSwitchCheckedChange != null) {
            val switchContainerColor = accentContentColor.copy(alpha = 0.10f)
            val switchBorderColor = accentContentColor.copy(alpha = 0.14f)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = switchContainerColor,
                        shape = AppShapes.g2(AppCorners.md)
                    )
                    .border(
                        width = 1.dp,
                        color = switchBorderColor,
                        shape = AppShapes.g2(AppCorners.md)
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = switchLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color = accentContentColor
                )
                Switch(
                    modifier = Modifier.offset(x = SettingsHeroSwitchAlignmentOffset),
                    checked = switchChecked,
                    onCheckedChange = onSwitchCheckedChange,
                    enabled = switchEnabled,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.primary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.36f),
                        uncheckedThumbColor = MaterialTheme.colorScheme.surface,
                        uncheckedTrackColor = accentContentColor.copy(alpha = 0.18f),
                        uncheckedBorderColor = Color.Transparent,
                        checkedBorderColor = Color.Transparent
                    )
                )
            }
        }

        if (chips.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                chips.forEach { chip ->
                    SettingsMetaChip(
                        label = chip,
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    }
}

@Composable
internal fun SettingsMetaChip(
    label: String,
    containerColor: Color,
    contentColor: Color
) {
    val ringColor = lerp(containerColor, contentColor, 0.28f).copy(alpha = 0.42f)
    Surface(
        color = containerColor,
        shape = AppShapes.g2(AppCorners.sm),
        border = BorderStroke(1.dp, ringColor)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = contentColor,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}

@Composable
internal fun SettingsEntryCard(
    title: String,
    subtitle: String? = null,
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    outerPadding: PaddingValues = settingsCardOuterPadding()
) {
    SettingsPreferenceCard(
        title = title,
        subtitle = subtitle,
        icon = icon,
        enabled = enabled,
        onClick = onClick,
        modifier = modifier,
        outerPadding = outerPadding
    )
}

@Composable
internal fun SettingsPreferenceCard(
    title: String,
    subtitle: String? = null,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accentColor: Color = settingsAccentColor(),
    outerPadding: PaddingValues = PaddingValues(0.dp),
    onClick: (() -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null
) {
    val titleAlpha = if (enabled) 1f else 0.65f
    val supportingAlpha = if (enabled) 1f else 0.65f
    val accentAlpha = if (enabled) 1f else 0.6f
    val trailingAlpha = if (enabled) 1f else 0.65f
    SettingsContentCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(outerPadding),
        accentColor = accentColor,
        enabled = enabled,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 0.dp, vertical = 0.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                color = accentColor.copy(alpha = 0.10f),
                shape = AppShapes.g2(AppCorners.sm)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor.copy(alpha = accentAlpha),
                    modifier = Modifier
                        .padding(10.dp)
                        .size(18.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = titleAlpha)
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                            alpha = supportingAlpha
                        ),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            when {
                trailingContent != null -> trailingContent()
                onClick != null -> Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = trailingAlpha)
                )
            }
        }
    }
}

@Composable
internal fun SettingsSwitchEntryCard(
    title: String,
    subtitle: String? = null,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accentColor: Color = settingsAccentColor(),
    outerPadding: PaddingValues = PaddingValues(0.dp)
) {
    SettingsPreferenceCard(
        title = title,
        subtitle = subtitle,
        icon = icon,
        modifier = modifier,
        enabled = enabled,
        accentColor = accentColor,
        outerPadding = outerPadding,
        onClick = { onCheckedChange(!checked) },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    )
}
