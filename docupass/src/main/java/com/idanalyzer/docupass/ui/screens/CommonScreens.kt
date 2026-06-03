package com.idanalyzer.docupass.ui.screens

import android.content.Context
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.idanalyzer.docupass.camera.CameraController
import com.idanalyzer.docupass.model.DocuPassSession

/** Create a [CameraController] bound to the current composition's lifecycle. */
@Composable
fun rememberCameraController(): CameraController {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    return remember(context, lifecycleOwner) {
        CameraController(context.applicationContext as Context, lifecycleOwner)
    }
}

/** A CameraX [PreviewView] surface; [onSurface] receives the view once created. */
@Composable
fun CameraPreview(modifier: Modifier = Modifier, onSurface: (PreviewView) -> Unit) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                onSurface(this)
            }
        },
    )
}

@Composable
fun WelcomeScreen(session: DocuPassSession, onContinue: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (session.companyName.isNotBlank()) {
            Text(session.companyName, style = MaterialTheme.typography.headlineSmall)
        }
        Text(
            text = session.welcomeMessage.ifBlank { "You'll be guided through a quick identity verification." },
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(vertical = 16.dp),
        )
        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) { Text("Start") }
    }
}

@Composable
fun MessageScreen(title: String, body: String) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Text(body, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
    }
}
