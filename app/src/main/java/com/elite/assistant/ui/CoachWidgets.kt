package com.elite.assistant.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elite.assistant.ui.theme.EliteColors

private val RecRed = Color(0xFFFF4D5E)

/**
 * Always-visible "recording" banner while Coach mode is on, so everyone in the
 * conversation can see the phone is listening.
 */
@Composable
fun CoachBanner(modifier: Modifier = Modifier) {
    val blink by rememberInfiniteTransition(label = "rec").animateFloat(
        initialValue = 1f, targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "blink",
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(RecRed.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
            .border(1.dp, RecRed.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(10.dp)
                .alpha(blink)
                .background(RecRed, CircleShape)
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                "COACH MODE · LISTENING",
                color = RecRed, fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Say \"Elite Elite\" or tap the core for a reply",
                color = EliteColors.TextMuted, fontSize = 12.sp,
            )
        }
    }
}

/** Pill switch that turns Coach mode on and off. */
@Composable
fun CoachToggle(on: Boolean, onToggle: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val color = if (on) RecRed else EliteColors.Cyan
    Text(
        text = if (on) "■  STOP COACH" else "●  CONVERSATION COACH",
        color = color,
        fontSize = 13.sp,
        fontFamily = FontFamily.Monospace,
        modifier = modifier
            .border(1.dp, color.copy(alpha = 0.7f), RoundedCornerShape(50))
            .clickable { onToggle(!on) }
            .padding(horizontal = 18.dp, vertical = 10.dp),
    )
}

/** Panel content in Coach mode: the suggested reply (if any) and the live transcript. */
@Composable
fun CoachPanel(
    live: String,
    suggestion: String?,
    onSpeak: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (suggestion != null) {
            Text("SAY THIS", color = EliteColors.Teal, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            Text(suggestion, color = EliteColors.TextPrimary, fontSize = 19.sp, fontWeight = FontWeight.Medium)
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Text(
                    "▶ WHISPER IT", color = EliteColors.Cyan, fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace, modifier = Modifier.clickable(onClick = onSpeak),
                )
                Text(
                    "✕ DISMISS", color = EliteColors.TextMuted, fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace, modifier = Modifier.clickable(onClick = onDismiss),
                )
            }
        }
        Text("HEARD", color = EliteColors.TextMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        Text(
            live.ifBlank { "Waiting for the conversation…" },
            color = EliteColors.TextMuted, fontSize = 14.sp,
        )
    }
}
