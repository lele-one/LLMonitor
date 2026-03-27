package com.lele.llmonitor.ui.theme

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.lele.llmonitor.R

enum class ThemePalettePreset(
    val preferenceValue: String,
    @StringRes val labelRes: Int
) {
    DYNAMIC(
        preferenceValue = "dynamic",
        labelRes = R.string.app_icon_palette_dynamic_multicolor
    ),
    OCEAN(
        preferenceValue = "ocean",
        labelRes = R.string.app_icon_palette_ocean
    ),
    FOREST(
        preferenceValue = "forest",
        labelRes = R.string.app_icon_palette_forest
    ),
    SUNSET(
        preferenceValue = "sunset",
        labelRes = R.string.app_icon_palette_sunset
    ),
    BLOSSOM(
        preferenceValue = "blossom",
        labelRes = R.string.app_icon_palette_blossom
    ),
    JIZI(
        preferenceValue = "jizi",
        labelRes = R.string.app_icon_palette_jizi
    );

    companion object {
        val default: ThemePalettePreset = DYNAMIC
        val visibleEntries: List<ThemePalettePreset> = listOf(
            DYNAMIC,
            BLOSSOM,
            SUNSET,
            FOREST,
            OCEAN,
            JIZI
        )

        fun fromPreferenceValue(value: String?): ThemePalettePreset {
            return entries.firstOrNull { it.preferenceValue == value } ?: default
        }
    }
}

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40
)

private object NoRippleIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode {
        return object : Modifier.Node(), DrawModifierNode {
            override fun ContentDrawScope.draw() {
                drawContent()
            }
        }
    }

    override fun hashCode(): Int = javaClass.hashCode()

    override fun equals(other: Any?): Boolean = other === this
}

private val LocalAppDarkTheme = staticCompositionLocalOf { false }

@Composable
fun isAppInDarkTheme(): Boolean = LocalAppDarkTheme.current

@Composable
fun LLMonitorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    themePalettePreset: ThemePalettePreset = ThemePalettePreset.DYNAMIC,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val useDynamicColor = themePalettePreset == ThemePalettePreset.DYNAMIC
    val dynamicColorRefreshToken = rememberDynamicColorRefreshToken(
        enabled = useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    )
    if (useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        dynamicColorRefreshToken
    }
    val colorScheme = resolveAppColorScheme(
        context = context,
        darkTheme = darkTheme,
        themePalettePreset = themePalettePreset
    )
    val view = LocalView.current
    if (!view.isInEditMode) {
        DisposableEffect(view, darkTheme) {
            val window = (view.context as Activity).window
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme
            onDispose { }
        }
    }

    CompositionLocalProvider(
        LocalIndication provides NoRippleIndication,
        LocalAppDarkTheme provides darkTheme
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

fun resolveAppColorScheme(
    context: Context,
    darkTheme: Boolean,
    themePalettePreset: ThemePalettePreset
): ColorScheme {
    val useDynamicColor = themePalettePreset == ThemePalettePreset.DYNAMIC
    return when {
        useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        !useDynamicColor -> resolveStaticThemeColorScheme(
            preset = themePalettePreset,
            darkTheme = darkTheme
        )

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
}

@Composable
@Suppress("DEPRECATION")
private fun rememberDynamicColorRefreshToken(enabled: Boolean): Int {
    if (!enabled) return 0

    val context = LocalContext.current
    var refreshToken by remember { mutableIntStateOf(0) }
    DisposableEffect(context, enabled) {
        if (!enabled) return@DisposableEffect onDispose { }

        val appContext = context.applicationContext
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                refreshToken++
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_WALLPAPER_CHANGED)
            addAction(Intent.ACTION_CONFIGURATION_CHANGED)
        }
        ContextCompat.registerReceiver(
            appContext,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        onDispose {
            appContext.unregisterReceiver(receiver)
        }
    }
    return refreshToken
}

private fun resolveStaticThemeColorScheme(
    preset: ThemePalettePreset,
    darkTheme: Boolean
): ColorScheme = when (preset) {
    ThemePalettePreset.OCEAN -> if (darkTheme) {
        darkColorScheme(
            primary = Color(0xFF84DDF6),
            onPrimary = Color(0xFF003545),
            primaryContainer = Color(0xFF09465B),
            onPrimaryContainer = Color(0xFFC4EEFF),
            secondary = Color(0xFF95DBEA),
            onSecondary = Color(0xFF003640),
            secondaryContainer = Color(0xFF12535F),
            onSecondaryContainer = Color(0xFFC5EEF7),
            tertiary = Color(0xFF84D7E1),
            onTertiary = Color(0xFF00363E),
            tertiaryContainer = Color(0xFF0E525B),
            onTertiaryContainer = Color(0xFFC0F0F2),
            background = Color(0xFF07171D),
            onBackground = Color(0xFFE5F2F7),
            surface = Color(0xFF0D1D24),
            onSurface = Color(0xFFE5F2F7),
            surfaceVariant = Color(0xFF314D55),
            onSurfaceVariant = Color(0xFFB8CCD2),
            outline = Color(0xFF7D9AA2),
            outlineVariant = Color(0xFF314D55),
            inverseSurface = Color(0xFFE5F2F7),
            inverseOnSurface = Color(0xFF18252B),
            surfaceTint = Color(0xFF84DDF6)
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF0E7392),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFB9EDFF),
            onPrimaryContainer = Color(0xFF001F2A),
            secondary = Color(0xFF2C879C),
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFC2EFF5),
            onSecondaryContainer = Color(0xFF001F26),
            tertiary = Color(0xFF1A7C8C),
            onTertiary = Color.White,
            tertiaryContainer = Color(0xFFC4F0F4),
            onTertiaryContainer = Color(0xFF002126),
            background = Color(0xFFECFAFD),
            onBackground = Color(0xFF162328),
            surface = Color(0xFFF5FBFD),
            onSurface = Color(0xFF162328),
            surfaceVariant = Color(0xFFCCE6ED),
            onSurfaceVariant = Color(0xFF375058),
            outline = Color(0xFF63818A),
            outlineVariant = Color(0xFFB1CCD4),
            inverseSurface = Color(0xFF2B3134),
            inverseOnSurface = Color(0xFFECF2F5),
            surfaceTint = Color(0xFF0E7392)
        )
    }

    ThemePalettePreset.FOREST -> if (darkTheme) {
        darkColorScheme(
            primary = Color(0xFF82D9AA),
            onPrimary = Color(0xFF063823),
            primaryContainer = Color(0xFF184D36),
            onPrimaryContainer = Color(0xFFB9F2D1),
            secondary = Color(0xFFB8D788),
            onSecondary = Color(0xFF1E3700),
            secondaryContainer = Color(0xFF304F0B),
            onSecondaryContainer = Color(0xFFD5F0AB),
            tertiary = Color(0xFF8EDDC2),
            onTertiary = Color(0xFF00382B),
            tertiaryContainer = Color(0xFF004F3F),
            onTertiaryContainer = Color(0xFF9EF2D7),
            background = Color(0xFF0D1511),
            onBackground = Color(0xFFE3F1E8),
            surface = Color(0xFF111A15),
            onSurface = Color(0xFFE3F1E8),
            surfaceVariant = Color(0xFF3E4941),
            onSurfaceVariant = Color(0xFFBECABF),
            outline = Color(0xFF88948A),
            outlineVariant = Color(0xFF3E4941),
            inverseSurface = Color(0xFFE3F1E8),
            inverseOnSurface = Color(0xFF1D2620),
            surfaceTint = Color(0xFF82D9AA)
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF2D7A55),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFB9F2D1),
            onPrimaryContainer = Color(0xFF002113),
            secondary = Color(0xFF56751E),
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFD5F0AB),
            onSecondaryContainer = Color(0xFF152000),
            tertiary = Color(0xFF006C55),
            onTertiary = Color.White,
            tertiaryContainer = Color(0xFF9EF2D7),
            onTertiaryContainer = Color(0xFF002119),
            background = Color(0xFFF5FBF6),
            onBackground = Color(0xFF17211B),
            surface = Color(0xFFF8FBF7),
            onSurface = Color(0xFF17211B),
            surfaceVariant = Color(0xFFDCE8DD),
            onSurfaceVariant = Color(0xFF404B42),
            outline = Color(0xFF707A71),
            outlineVariant = Color(0xFFC0CBC0),
            inverseSurface = Color(0xFF2C322D),
            inverseOnSurface = Color(0xFFEDF2EC),
            surfaceTint = Color(0xFF2D7A55)
        )
    }

    ThemePalettePreset.SUNSET -> if (darkTheme) {
        darkColorScheme(
            primary = Color(0xFFFFB68A),
            onPrimary = Color(0xFF512300),
            primaryContainer = Color(0xFF703500),
            onPrimaryContainer = Color(0xFFFFDCC4),
            secondary = Color(0xFFF0BB97),
            onSecondary = Color(0xFF4B250A),
            secondaryContainer = Color(0xFF6A3B1D),
            onSecondaryContainer = Color(0xFFFFDCC7),
            tertiary = Color(0xFFE7B6A3),
            onTertiary = Color(0xFF4A251A),
            tertiaryContainer = Color(0xFF664034),
            onTertiaryContainer = Color(0xFFFFDCD1),
            background = Color(0xFF18100D),
            onBackground = Color(0xFFF5EDE8),
            surface = Color(0xFF1E1512),
            onSurface = Color(0xFFF5EDE8),
            surfaceVariant = Color(0xFF51433D),
            onSurfaceVariant = Color(0xFFD7C2BA),
            outline = Color(0xFF9F8D85),
            outlineVariant = Color(0xFF51433D),
            inverseSurface = Color(0xFFF5EDE8),
            inverseOnSurface = Color(0xFF362F2B),
            surfaceTint = Color(0xFFFFB68A)
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF9D4C16),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFFFDCC4),
            onPrimaryContainer = Color(0xFF2F1400),
            secondary = Color(0xFFA25E34),
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFFFE0D1),
            onSecondaryContainer = Color(0xFF37160D),
            tertiary = Color(0xFFA96B55),
            onTertiary = Color.White,
            tertiaryContainer = Color(0xFFFFE0D6),
            onTertiaryContainer = Color(0xFF34130A),
            background = Color(0xFFFEF4ED),
            onBackground = Color(0xFF261A16),
            surface = Color(0xFFFFF8F5),
            onSurface = Color(0xFF261A16),
            surfaceVariant = Color(0xFFF2DDD4),
            onSurfaceVariant = Color(0xFF52443D),
            outline = Color(0xFF85736B),
            outlineVariant = Color(0xFFD6C2BA),
            inverseSurface = Color(0xFF3A2F2A),
            inverseOnSurface = Color(0xFFFFEDE7),
            surfaceTint = Color(0xFF9D4C16)
        )
    }

    ThemePalettePreset.BLOSSOM -> if (darkTheme) {
        darkColorScheme(
            primary = Color(0xFFFFB0CE),
            onPrimary = Color(0xFF5B1139),
            primaryContainer = Color(0xFF7A2851),
            onPrimaryContainer = Color(0xFFFFD9E7),
            secondary = Color(0xFFE6B7CF),
            onSecondary = Color(0xFF4E2438),
            secondaryContainer = Color(0xFF684051),
            onSecondaryContainer = Color(0xFFFFD8E7),
            tertiary = Color(0xFFDCC0D5),
            onTertiary = Color(0xFF40273E),
            tertiaryContainer = Color(0xFF594055),
            onTertiaryContainer = Color(0xFFF6DAEE),
            background = Color(0xFF171017),
            onBackground = Color(0xFFF2ECF2),
            surface = Color(0xFF1D161D),
            onSurface = Color(0xFFF2ECF2),
            surfaceVariant = Color(0xFF4F4450),
            onSurfaceVariant = Color(0xFFD3C2D0),
            outline = Color(0xFF9D8D9A),
            outlineVariant = Color(0xFF4F4450),
            inverseSurface = Color(0xFFF2ECF2),
            inverseOnSurface = Color(0xFF352F35),
            surfaceTint = Color(0xFFFFB0CE)
        )
    } else {
        lightColorScheme(
            primary = Color(0xFFA04572),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFFFD9E7),
            onPrimaryContainer = Color(0xFF35001E),
            secondary = Color(0xFF8A5C71),
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFF9DDE8),
            onSecondaryContainer = Color(0xFF34111F),
            tertiary = Color(0xFF94667C),
            onTertiary = Color.White,
            tertiaryContainer = Color(0xFFF6E1EA),
            onTertiaryContainer = Color(0xFF35111E),
            background = Color(0xFFFCF2F8),
            onBackground = Color(0xFF251A24),
            surface = Color(0xFFFFF7FB),
            onSurface = Color(0xFF251A24),
            surfaceVariant = Color(0xFFF1DCE5),
            onSurfaceVariant = Color(0xFF51444E),
            outline = Color(0xFF866F79),
            outlineVariant = Color(0xFFD4C1CF),
            inverseSurface = Color(0xFF3B3039),
            inverseOnSurface = Color(0xFFFEEEF7),
            surfaceTint = Color(0xFFA04572)
        )
    }

    ThemePalettePreset.JIZI -> if (darkTheme) {
        darkColorScheme(
            primary = Color(0xFFCDB8FF),
            onPrimary = Color(0xFF35205E),
            primaryContainer = Color(0xFF4C3978),
            onPrimaryContainer = Color(0xFFE8DDFF),
            secondary = Color(0xFFBFC8FF),
            onSecondary = Color(0xFF22305E),
            secondaryContainer = Color(0xFF384678),
            onSecondaryContainer = Color(0xFFDBE1FF),
            tertiary = Color(0xFFDDB6F0),
            onTertiary = Color(0xFF4A245A),
            tertiaryContainer = Color(0xFF633A72),
            onTertiaryContainer = Color(0xFFF7D8FF),
            background = Color(0xFF14121B),
            onBackground = Color(0xFFEAE6F2),
            surface = Color(0xFF1C1824),
            onSurface = Color(0xFFEAE6F2),
            surfaceVariant = Color(0xFF4C4559),
            onSurfaceVariant = Color(0xFFCFC4D9),
            outline = Color(0xFF988EA3),
            outlineVariant = Color(0xFF4C4559),
            inverseSurface = Color(0xFFEAE6F2),
            inverseOnSurface = Color(0xFF322E39),
            surfaceTint = Color(0xFFCDB8FF)
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF7252B8),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFEBDDFF),
            onPrimaryContainer = Color(0xFF28104D),
            secondary = Color(0xFF4E6FC0),
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFDBE4FF),
            onSecondaryContainer = Color(0xFF0F214F),
            tertiary = Color(0xFF9560AE),
            onTertiary = Color.White,
            tertiaryContainer = Color(0xFFF4D9FF),
            onTertiaryContainer = Color(0xFF33103F),
            background = Color(0xFFFAF6FF),
            onBackground = Color(0xFF201B29),
            surface = Color(0xFFFEF8FF),
            onSurface = Color(0xFF201B29),
            surfaceVariant = Color(0xFFE7DDF1),
            onSurfaceVariant = Color(0xFF4E4658),
            outline = Color(0xFF7F748B),
            outlineVariant = Color(0xFFCDC2D9),
            inverseSurface = Color(0xFF352F3E),
            inverseOnSurface = Color(0xFFF6EEFF),
            surfaceTint = Color(0xFF7252B8)
        )
    }

    ThemePalettePreset.DYNAMIC -> if (darkTheme) DarkColorScheme else LightColorScheme
}
