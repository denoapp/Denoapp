package com.deno.social.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class DenoColors(
    val background: Color,
    val surface: Color,
    val card: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val border: Color,
    val primary: Color,
    val onPrimary: Color,
    val isDark: Boolean
)

val LocalDenoColors = staticCompositionLocalOf {
    DenoColors(
        background = LightBg, surface = LightSurface, card = LightCard,
        textPrimary = Gray900, textSecondary = Gray600, border = Gray300,
        primary = DenoBlue, onPrimary = PureWhite, isDark = false
    )
}

private val LightColors = DenoColors(
    background = LightBg, surface = LightSurface, card = LightCard,
    textPrimary = Gray900, textSecondary = Gray600, border = Gray300,
    primary = DenoBlue, onPrimary = PureWhite, isDark = false
)

private val DarkColors = DenoColors(
    background = DarkBg, surface = DarkSurface, card = DarkCard,
    textPrimary = PureWhite, textSecondary = Gray400, border = Gray800,
    primary = DenoBlue, onPrimary = PureWhite, isDark = true
)

private val LightColorScheme = lightColorScheme(
    primary = DenoBlue, onPrimary = PureWhite, background = LightBg,
    surface = LightSurface, onBackground = Gray900, onSurface = Gray900
)

private val DarkColorScheme = darkColorScheme(
    primary = DenoBlue, onPrimary = PureWhite, background = DarkBg,
    surface = DarkSurface, onBackground = PureWhite, onSurface = PureWhite
)

@Composable
fun DenoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val scheme = if (darkTheme) DarkColorScheme else LightColorScheme
    CompositionLocalProvider(LocalDenoColors provides colors) {
        MaterialTheme(colorScheme = scheme, typography = Typography, content = content)
    }
}

object DenoTheme {
    val colors: DenoColors
        @Composable
        get() = LocalDenoColors.current
}
