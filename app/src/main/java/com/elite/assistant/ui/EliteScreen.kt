package com.elite.assistant.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elite.assistant.EliteUiState
import com.elite.assistant.Phase
import com.elite.assistant.ui.theme.EliteColors

/** Full-screen HUD: title, status, the arc reactor, and what was heard / said. */
@Composable
fun EliteScreen(
    state: EliteUiState,
    onCoreTapped: () -> Unit,
    onSaveApiKey: (String) -> Unit,
    onCoachToggle: (Boolean) -> Unit,
    onSpeakSuggestion: () -> Unit,
    onDismissSuggestion: () -> Unit,
) {
    // Keep the screen awake while coaching, since coach mode stops when the app leaves the screen.
    val view = LocalView.current
    DisposableEffect(state.coachOn) {
        view.keepScreenOn = state.coachOn
        onDispose { view.keepScreenOn = false }
    }

    // Opened by the KEY button; also forced open while no key is set.
    var keyDialogOpen by rememberSaveable { mutableStateOf(false) }
    if (keyDialogOpen || state.needsApiKey) {
        ApiKeyDialog(
            onSave = { onSaveApiKey(it); keyDialogOpen = false },
            onDismiss = { keyDialogOpen = false },
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(listOf(EliteColors.Panel, EliteColors.Background))
            )
            .safeDrawingPadding(),
    ) {
        Text(
            text = "KEY",
            color = EliteColors.TextMuted,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .clickable { keyDialogOpen = true }
                .padding(16.dp),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "E L I T E",
                color = EliteColors.Cyan,
                fontSize = 28.sp,
                fontWeight = FontWeight.Light,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = 16.dp),
            )
            if (state.coachOn) {
                CoachBanner(Modifier.padding(top = 12.dp))
            }
            AnimatedContent(targetState = state.phase to state.coachOn, label = "status") { (phase, coach) ->
                Text(
                    text = if (coach) coachStatus(phase) else statusLabel(phase),
                    color = EliteColors.TextMuted,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            Spacer(Modifier.weight(1f))

            ArcReactorButton(
                phase = state.phase,
                level = state.level,
                onClick = onCoreTapped,
                modifier = Modifier.size(if (state.coachOn) 200.dp else 260.dp),
            )

            Spacer(Modifier.weight(1f))

            CoachToggle(
                on = state.coachOn,
                onToggle = onCoachToggle,
                modifier = Modifier.padding(bottom = 12.dp),
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (state.coachOn) 230.dp else 180.dp)
                    .background(EliteColors.Panel.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (state.coachOn) {
                    CoachPanel(
                        live = state.coachLive,
                        suggestion = state.suggestion,
                        onSpeak = onSpeakSuggestion,
                        onDismiss = onDismissSuggestion,
                    )
                } else {
                    if (state.heard.isNotBlank()) {
                        Text("YOU", color = EliteColors.TextMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        Text(state.heard, color = EliteColors.TextPrimary, fontSize = 16.sp)
                    }
                    Text("ELITE", color = EliteColors.Cyan, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    Text(state.reply, color = EliteColors.TextPrimary, fontSize = 16.sp, textAlign = TextAlign.Start)
                }
            }
        }
    }
}

private fun statusLabel(phase: Phase) = when (phase) {
    Phase.IDLE -> "STANDING BY · TAP TO SPEAK"
    Phase.LISTENING -> "LISTENING · TAP TO FINISH"
    Phase.THINKING -> "PROCESSING"
    Phase.SPEAKING -> "RESPONDING · TAP TO STOP"
}

private fun coachStatus(phase: Phase) = when (phase) {
    Phase.THINKING -> "THINKING OF A GOOD REPLY"
    Phase.SPEAKING -> "WHISPERING"
    else -> "COACHING"
}
