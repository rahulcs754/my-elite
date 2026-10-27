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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elite.assistant.ui.EliteScreen
import com.elite.assistant.ui.theme.EliteTheme
import java.io.File

/**
 * Single-screen app. Handles everything that needs an Activity: runtime permissions
 * and the camera for "what is this?" questions. Every tap goes to [EliteViewModel].
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
                // Other permissions Elite asks for mid-conversation (e.g. contacts for calls).
                val actionPermission = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission(),
                    viewModel::onPermissionResult,
                )
                LaunchedEffect(state.permissionRequest) {
                    state.permissionRequest?.let { actionPermission.launch(it) }
                }

                // Camera for LOOK: the photo goes to a private cache file and is deleted after use.
                val photoFile = remember { File(cacheDir, "look/look.jpg") }
                val takePhoto = rememberLauncherForActivityResult(
                    ActivityResultContracts.TakePicture()
                ) { saved -> viewModel.onPhotoResult(saved, photoFile) }
                LaunchedEffect(state.photoRequest) {
                    if (state.photoRequest) {
                        viewModel.onPhotoRequestHandled()
                        photoFile.parentFile?.mkdirs()
                        val uri = FileProvider.getUriForFile(
                            this@MainActivity, "$packageName.fileprovider", photoFile
                        )
                        takePhoto.launch(uri)
                    }
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
                    onLanguageTapped = viewModel::cycleLanguage,
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
