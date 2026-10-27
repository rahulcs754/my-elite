package com.elite.assistant.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.elite.assistant.Phase
import com.elite.assistant.ui.theme.EliteColors

/**
 * The arc-reactor mic button.
 *
 * - Outer segmented ring and inner ring rotate in opposite directions (faster while thinking).
 * - The core breathes when idle and pulses with your voice while listening.
 * - Colour shows the phase: cyan idle, bright cyan listening, amber thinking, teal speaking.
 */
@Composable
fun ArcReactorButton(
    phase: Phase,
    level: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val infinite = rememberInfiniteTransition(label = "reactor")
    val spin by infinite.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing)),
        label = "spin",
    )
    val breathe by infinite.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800), RepeatMode.Reverse),
        label = "breathe",
    )

    val color by animateColorAsState(
        targetValue = when (phase) {
            Phase.IDLE -> EliteColors.Cyan
            Phase.LISTENING -> EliteColors.CyanSoft
            Phase.THINKING -> EliteColors.Amber
            Phase.SPEAKING -> EliteColors.Teal
        },
        animationSpec = tween(400),
        label = "color",
    )
    // Smooth the raw mic level so the core doesn't jitter.
    val voice by animateFloatAsState(level, spring(stiffness = 300f), label = "voice")

    val speed = when (phase) {
        Phase.THINKING -> 4f
        Phase.LISTENING -> 2f
        else -> 1f
    }
    val pulse = when (phase) {
        Phase.LISTENING -> 0.35f + 0.65f * voice
        Phase.SPEAKING -> 0.4f + 0.4f * breathe
        Phase.THINKING -> 0.5f + 0.3f * breathe
        Phase.IDLE -> 0.25f * breathe
    }

    Canvas(
        modifier = modifier
            .semantics { contentDescription = "Talk to Elite" }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
    ) {
        val r = size.minDimension / 2f
        drawGlow(color, r, pulse)
        rotate(spin * speed) { drawSegmentedRing(color, r * 0.86f, segments = 12, stroke = r * 0.05f) }
        rotate(-spin * speed * 1.5f) { drawSegmentedRing(color.copy(alpha = 0.7f), r * 0.66f, segments = 6, stroke = r * 0.025f) }
        drawCircle(color.copy(alpha = 0.5f), radius = r * 0.52f, style = Stroke(r * 0.012f))
        drawCore(color, r * (0.30f + 0.08f * pulse))
        drawMic(Color(0xFF05080F).copy(alpha = 0.85f), r * 0.16f)
    }
}

private fun DrawScope.drawGlow(color: Color, r: Float, pulse: Float) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color.copy(alpha = 0.35f + 0.35f * pulse), color.copy(alpha = 0.08f), Color.Transparent),
            center = center, radius = r,
        ),
        radius = r,
    )
}

/** A ring broken into [segments] arcs, like the reactor's coil housing. */
private fun DrawScope.drawSegmentedRing(color: Color, radius: Float, segments: Int, stroke: Float) {
    val sweep = 360f / segments
    val topLeft = Offset(center.x - radius, center.y - radius)
    repeat(segments) { i ->
        drawArc(
            color = color,
            startAngle = i * sweep,
            sweepAngle = sweep * 0.7f,
            useCenter = false,
            topLeft = topLeft,
            size = Size(radius * 2, radius * 2),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
    }
}

private fun DrawScope.drawCore(color: Color, radius: Float) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color.White, color, color.copy(alpha = 0.6f)),
            center = center, radius = radius,
        ),
        radius = radius,
    )
}

/** Minimal microphone glyph drawn in the centre of the core. */
private fun DrawScope.drawMic(color: Color, s: Float) {
    val w = s * 0.7f
    val h = s * 1.1f
    drawRoundRect(
        color = color,
        topLeft = Offset(center.x - w / 2, center.y - h * 0.75f),
        size = Size(w, h),
        cornerRadius = CornerRadius(w / 2, w / 2),
    )
    val cupR = s * 0.6f
    drawArc(
        color = color,
        startAngle = 0f, sweepAngle = 180f, useCenter = false,
        topLeft = Offset(center.x - cupR, center.y - h * 0.35f),
        size = Size(cupR * 2, cupR * 1.4f),
        style = Stroke(width = s * 0.14f, cap = StrokeCap.Round),
    )
    drawLine(
        color = color,
        start = Offset(center.x, center.y + h * 0.35f + cupR * 0.4f),
        end = Offset(center.x, center.y + h * 0.75f),
        strokeWidth = s * 0.14f,
        cap = StrokeCap.Round,
    )
}
