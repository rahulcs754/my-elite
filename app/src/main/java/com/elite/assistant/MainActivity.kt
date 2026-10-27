package com.elite.assistant

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elite.assistant.ui.EliteScreen
import com.elite.assistant.ui.theme.EliteTheme

/**
 * Single-screen app. Asks for the microphone on first tap, then hands every tap
 * to [EliteViewModel], which runs the voice loop.
 */
class MainActivity : ComponentActivity() {

    private val viewModel: EliteViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            EliteTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()

                val micPermission = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { granted ->
                    if (granted) viewModel.onCoreTapped() else viewModel.onMicPermissionDenied()
                }
                val coachPermission = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { granted ->
                    if (granted) viewModel.setCoachMode(true) else viewModel.onMicPermissionDenied()
                }

                EliteScreen(
                    state = state,
                    onCoreTapped = {
                        if (hasMicPermission()) viewModel.onCoreTapped()
                        else micPermission.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    onSaveApiKey = viewModel::saveApiKey,
                    onCoachToggle = { on ->
                        if (!on || hasMicPermission()) viewModel.setCoachMode(on)
                        else coachPermission.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    onSpeakSuggestion = viewModel::speakSuggestionSoftly,
                    onDismissSuggestion = viewModel::dismissSuggestion,
                )
            }
        }
    }

    /** Coach mode only ever listens while Elite is on screen. */
    override fun onStop() {
        super.onStop()
        viewModel.onAppBackgrounded()
    }

    private fun hasMicPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
}
