package com.idanalyzer.docupass.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.idanalyzer.docupass.DocuPassConfig
import com.idanalyzer.docupass.DocuPassResult
import com.idanalyzer.docupass.location.LocationProvider
import com.idanalyzer.docupass.model.DocuPassTask
import com.idanalyzer.docupass.session.DocuPassState
import com.idanalyzer.docupass.ui.screens.ContractScreen
import com.idanalyzer.docupass.ui.screens.CustomFormScreen
import com.idanalyzer.docupass.ui.screens.DocumentScreen
import com.idanalyzer.docupass.ui.screens.FaceScreen
import com.idanalyzer.docupass.ui.screens.MessageScreen
import com.idanalyzer.docupass.ui.screens.PhoneScreen
import com.idanalyzer.docupass.ui.screens.WelcomeScreen
import kotlinx.coroutines.launch

/**
 * The drop-in DocuPass verification UI. Give it a [DocuPassConfig] (just a
 * `reference`) and a result callback — it handles camera permission, the whole
 * server-driven flow, on-device liveness, and capture.
 *
 * ```
 * DocuPassView(
 *     config = DocuPassConfig(reference = "US…"),
 *     onResult = { result -> /* Completed | Failed | Cancelled | Error */ },
 * )
 * ```
 */
@Composable
fun DocuPassView(
    config: DocuPassConfig,
    modifier: Modifier = Modifier,
    strings: DocuPassStrings = DocuPassStrings(),
    theme: DocuPassTheme = DocuPassTheme(),
    onResult: (DocuPassResult) -> Unit,
) {
    val context = LocalContext.current
    val vm: DocuPassViewModel = viewModel(factory = DocuPassViewModel.Factory(config))
    val state by vm.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var cameraGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var welcomeAcknowledged by remember { mutableStateOf(false) }

    // GPS: only when the session sets gps=true. The server requires a Geolocation
    // header on every call after get_action, so we must obtain a fix before the
    // next step (document selection) can be submitted.
    var geoReady by remember { mutableStateOf(false) }
    var geoRequested by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        cameraGranted = granted
        if (granted) vm.start() else onResult(DocuPassResult.Cancelled(config.reference))
    }

    suspend fun acquireLocation() {
        val loc = LocationProvider.current(context)
        if (loc != null) {
            vm.setGeolocation(loc.first, loc.second, loc.third)
            geoReady = true
        } else {
            snackbar.showSnackbar(strings.locationPermissionRequired)
            geoRequested = false // allow a retry
        }
    }

    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            scope.launch { acquireLocation() }
        } else {
            scope.launch { snackbar.showSnackbar(strings.locationPermissionRequired) }
            geoRequested = false
        }
    }

    LaunchedEffect(Unit) {
        if (cameraGranted) vm.start() else permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    // When the session asks for GPS, request location + obtain a fix exactly once.
    val needsGps = (state as? DocuPassState.Step)?.session?.gps == true
    LaunchedEffect(needsGps) {
        if (needsGps && !geoReady && !geoRequested) {
            geoRequested = true
            val hasPermission =
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED
            if (hasPermission) {
                acquireLocation()
            } else {
                locationLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                    )
                )
            }
        }
    }

    // Surface recoverable rejections as a snackbar.
    LaunchedEffect(Unit) {
        vm.transientErrors.collect { err ->
            scope.launch { snackbar.showSnackbar(err.message ?: err.code ?: "Please try again") }
        }
    }

    // Deliver the terminal result once.
    LaunchedEffect(state) {
        (state as? DocuPassState.Finished)?.let { onResult(it.result) }
    }

    CompositionLocalProvider(
        LocalDocuPassStrings provides strings,
        LocalDocuPassTheme provides theme,
    ) {
      val themedScheme = theme.primaryColor?.let { MaterialTheme.colorScheme.copy(primary = it) }
          ?: MaterialTheme.colorScheme
      MaterialTheme(colorScheme = themedScheme) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            when (val s = state) {
                DocuPassState.Idle, DocuPassState.Loading -> CircularProgressIndicator()
                is DocuPassState.Finished -> CircularProgressIndicator()
                is DocuPassState.Step -> {
                    val session = s.session
                    val showWelcome = !welcomeAcknowledged &&
                        (session.welcomeMessage.isNotBlank() || session.companyName.isNotBlank())
                    val needLocation = session.gps && !geoReady
                    if (showWelcome) {
                        WelcomeScreen(session = session, onContinue = { welcomeAcknowledged = true })
                    } else if (needLocation) {
                        // Block the flow until the Geolocation fix is set, otherwise
                        // the next server call fails with LOCATION_HEADER_MISSING.
                        MessageScreen(title = strings.locationTitle, body = strings.locationBody)
                    } else {
                        when (session.parsedTask) {
                            DocuPassTask.DOCUMENT -> DocumentScreen(vm, session)
                            DocuPassTask.FACE -> FaceScreen(vm, session)
                            DocuPassTask.CUSTOM_FORM -> CustomFormScreen(vm, session)
                            DocuPassTask.PHONE -> PhoneScreen(vm, session)
                            DocuPassTask.CONTRACT -> ContractScreen(vm, session)
                            DocuPassTask.PARTY_PENDING -> MessageScreen(
                                title = strings.waitingTitle,
                                body = strings.waitingBody,
                            )
                            DocuPassTask.UNKNOWN -> MessageScreen(
                                title = strings.pleaseWaitTitle,
                                body = strings.pleaseWaitBody,
                            )
                        }
                    }
                }
            }
            if (!cameraGranted) {
                Text(strings.cameraPermissionRequired, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
      }
    }
}
