package com.lele.llmonitor.ui.theme

import androidx.compose.ui.graphics.Color

fun resolveThemePaletteActiveRingAccentColors(
    preset: ThemePalettePreset,
    isDarkTheme: Boolean
): List<Color> {
    return when (preset) {
        ThemePalettePreset.DYNAMIC -> listOf(
            Color(0xFF4285F4),
            Color(0xFFEA4335),
            Color(0xFFFBBC04),
            Color(0xFF34A853)
        )
        ThemePalettePreset.OCEAN -> if (isDarkTheme) {
            listOf(
                Color(0xFF95F1FF),
                Color(0xFF47D7FF),
                Color(0xFF149EFF),
                Color(0xFF5B75F0)
            )
        } else {
            listOf(
                Color(0xFF7DEAFF),
                Color(0xFF18CCFF),
                Color(0xFF0077E3),
                Color(0xFF325BC8)
            )
        }
        ThemePalettePreset.FOREST -> if (isDarkTheme) {
            listOf(
                Color(0xFFA8E5B3),
                Color(0xFF63D57D),
                Color(0xFF45B25A),
                Color(0xFFB8C55C)
            )
        } else {
            listOf(
                Color(0xFF8BDEA0),
                Color(0xFF2FB36A),
                Color(0xFF2F7A3E),
                Color(0xFF9AAA39)
            )
        }
        ThemePalettePreset.SUNSET -> if (isDarkTheme) {
            listOf(
                Color(0xFFFFE0A1),
                Color(0xFFFFBA67),
                Color(0xFFFF7D50),
                Color(0xFFD08A30)
            )
        } else {
            listOf(
                Color(0xFFFFD68A),
                Color(0xFFFFA749),
                Color(0xFFF1693B),
                Color(0xFFC37923)
            )
        }
        ThemePalettePreset.BLOSSOM -> if (isDarkTheme) {
            listOf(
                Color(0xFFFFD4E2),
                Color(0xFFFFA5CA),
                Color(0xFFEF679A),
                Color(0xFFB77FC5)
            )
        } else {
            listOf(
                Color(0xFFFFC4D8),
                Color(0xFFFF87B7),
                Color(0xFFDA4D86),
                Color(0xFFA96DB7)
            )
        }
        ThemePalettePreset.JIZI -> if (isDarkTheme) {
            listOf(
                Color(0xFFE5DBFF),
                Color(0xFFCDB7FF),
                Color(0xFFA07DFF),
                Color(0xFF7582FF)
            )
        } else {
            listOf(
                Color(0xFFD9CBFF),
                Color(0xFFBBA3FF),
                Color(0xFF8B72FF),
                Color(0xFF5D69D9)
            )
        }
    }
}
