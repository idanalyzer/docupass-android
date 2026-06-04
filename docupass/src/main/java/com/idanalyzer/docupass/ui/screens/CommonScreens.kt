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
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.idanalyzer.docupass.camera.CameraController
import com.idanalyzer.docupass.model.DocuPassSession
import com.idanalyzer.docupass.ui.LocalDocuPassStrings
import com.idanalyzer.docupass.ui.LocalDocuPassTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

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
    val s = LocalDocuPassStrings.current
    val theme = LocalDocuPassTheme.current
    val logoUrl = (theme.logoUrl ?: session.logoURL).takeIf { theme.showLogo && it.isNotBlank() }
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (logoUrl != null) {
            RemoteLogo(logoUrl, Modifier.padding(bottom = 16.dp).heightIn(max = 96.dp))
        }
        if (session.companyName.isNotBlank()) {
            Text(session.companyName, style = MaterialTheme.typography.headlineSmall)
        }
        Text(
            text = session.welcomeMessage.ifBlank { s.welcomeFallback },
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(vertical = 16.dp),
        )
        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) { Text(s.start) }
    }
}

/** Loads a remote logo without any image-loading dependency. */
@Composable
private fun RemoteLogo(url: String, modifier: Modifier = Modifier) {
    var bitmap by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(url) {
        bitmap = withContext(Dispatchers.IO) {
            runCatching { URL(url).openStream().use { BitmapFactory.decodeStream(it)?.asImageBitmap() } }.getOrNull()
        }
    }
    bitmap?.let { Image(bitmap = it, contentDescription = null, modifier = modifier) }
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
