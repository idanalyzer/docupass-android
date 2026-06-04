package com.idanalyzer.docupass.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.idanalyzer.docupass.liveness.LivenessStep
import com.idanalyzer.docupass.model.DocuPassSession
import com.idanalyzer.docupass.ui.DocuPassStrings
import com.idanalyzer.docupass.ui.DocuPassViewModel
import com.idanalyzer.docupass.ui.LocalDocuPassStrings

@Composable
fun FaceScreen(vm: DocuPassViewModel, session: DocuPassSession) {
    val context = LocalContext.current
    val camera = rememberCameraController()
    val liveness by vm.liveness.collectAsState()
    val ready by vm.livenessReady.collectAsState()

    DisposableEffect(Unit) {
        vm.resetLiveness()
        vm.prepareLiveness(context)
        onDispose { camera.stop() }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CameraPreview(modifier = Modifier.fillMaxSize()) { view ->
            camera.startFaceAnalysis(view) { bmp, ts -> vm.onFaceFrame(bmp, ts) }
        }

        Column(
            Modifier.fillMaxWidth().padding(24.dp).align(Alignment.BottomCenter),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val s = LocalDocuPassStrings.current
            val step = liveness?.step ?: LivenessStep.FRONT
            Text(
                text = if (!ready) s.faceLoading else instructionFor(step, s),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            liveness?.let {
                LinearProgressIndicator(
                    progress = { it.progress },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
                if (!it.faceVisible && ready) {
                    Text(
                        s.faceNoFace,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
}

private fun instructionFor(step: LivenessStep, s: DocuPassStrings): String = when (step) {
    LivenessStep.FRONT -> s.faceForward
    LivenessStep.FRONT_SUCCESS -> s.faceGreat
    LivenessStep.TURN_LEFT -> s.faceTurnLeft
    LivenessStep.TURN_RIGHT -> s.faceTurnRight
    LivenessStep.DONE_SUCCESS, LivenessStep.COMPLETE -> s.faceDone
}
