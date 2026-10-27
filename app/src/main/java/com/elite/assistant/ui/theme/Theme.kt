package com.elite.assistant.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Sci-fi palette: near-black space, arc-reactor cyan, amber for "thinking". */
object EliteColors {
    val Background = Color(0xFF05080F)
    val Panel = Color(0xFF0C1422)
    val Cyan = Color(0xFF3FE0FF)
    val CyanSoft = Color(0xFF8AF3FF)
    val Amber = Color(0xFFFFB547)
    val Teal = Color(0xFF3DFFC2)
    val TextPrimary = Color(0xFFE6F7FF)
    val TextMuted = Color(0xFF7A93A8)
}

@Composable
fun EliteTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = EliteColors.Cyan,
            background = EliteColors.Background,
            surface = EliteColors.Panel,
            onBackground = EliteColors.TextPrimary,
            onSurface = EliteColors.TextPrimary,
        ),
        content = content,
    )
}
