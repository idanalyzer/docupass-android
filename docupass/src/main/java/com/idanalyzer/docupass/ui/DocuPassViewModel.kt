package com.idanalyzer.docupass.ui

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.idanalyzer.docupass.DocuPassConfig
import com.idanalyzer.docupass.camera.ImageUtils
import com.idanalyzer.docupass.liveness.FaceLandmarkerEngine
import com.idanalyzer.docupass.liveness.LivenessController
import com.idanalyzer.docupass.liveness.LivenessStep
import com.idanalyzer.docupass.liveness.LivenessUpdate
import com.idanalyzer.docupass.model.PhoneChannel
import com.idanalyzer.docupass.session.DocuPassController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Drives [DocuPassController] for the bundled Compose UI and owns the on-device
 * liveness pipeline (MediaPipe engine + [LivenessController]). The camera itself
 * is hosted in the composable, which forwards frames to [onFaceFrame].
 */
class DocuPassViewModel(private val config: DocuPassConfig) : ViewModel() {

    val controller = DocuPassController(config)
    val state = controller.state
    val transientErrors = controller.transientErrors

    private val _liveness = MutableStateFlow<LivenessUpdate?>(null)
    val liveness = _liveness.asStateFlow()

    private val _livenessReady = MutableStateFlow(false)
    val livenessReady = _livenessReady.asStateFlow()

    private var engine: FaceLandmarkerEngine? = null
    private val livenessController = LivenessController(config.liveness)
    private val faceSubmitted = AtomicBoolean(false)

    fun start() = viewModelScope.launch { controller.start() }

    fun setGeolocation(lat: Double, lng: Double, acc: Double) =
        controller.setGeolocation(lat, lng, acc)

    fun submitDocumentSelection(country: String, type: String) =
        viewModelScope.launch { controller.submitDocumentSelection(country, type) }

    fun submitDocument(frontBase64: String, backBase64: String?) =
        viewModelScope.launch { controller.submitDocument(frontBase64, backBase64) }

    fun submitForm(answers: Map<String, String>) =
        viewModelScope.launch { controller.submitForm(answers) }

    fun submitContract(signatures: Map<String, String>) =
        viewModelScope.launch { controller.submitContract(signatures) }

    fun sendPhoneCode(number: String?, channel: PhoneChannel) =
        viewModelScope.launch { controller.sendPhoneCode(number, channel) }

    fun verifyPhoneCode(number: String?, code: String) =
        viewModelScope.launch { controller.verifyPhoneCode(number, code) }

    fun cancel() = controller.cancel()

    // --- Liveness ---------------------------------------------------------

    /** Build the MediaPipe engine (call when the FACE task begins). */
    fun prepareLiveness(context: Context) {
        if (engine != null) return
        val appContext = context.applicationContext
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                runCatching { FaceLandmarkerEngine(appContext) }
                    .onSuccess { engine = it; _livenessReady.value = true }
            }
        }
    }

    /** Restart the liveness sequence (e.g. after a face rejection). */
    fun resetLiveness() {
        faceSubmitted.set(false)
        livenessController.reset()
        _liveness.value = null
    }

    /**
     * Process one front-camera frame (called on the analysis thread). Runs
     * detection + the liveness state machine; on completion uploads the
     * best-neutral frame exactly once.
     */
    fun onFaceFrame(frame: Bitmap, timestampMs: Long) {
        val eng = engine ?: return
        if (faceSubmitted.get()) return

        val scaled = ImageUtils.scaleToMax(frame, config.liveness.maxImageSize)
        val landmarks = runCatching { eng.detect(scaled, timestampMs) }.getOrNull()
        val update = livenessController.update(landmarks, scaled)
        _liveness.value = update

        val best = update.bestNeutralFrame
        if (update.step == LivenessStep.COMPLETE && best != null && faceSubmitted.compareAndSet(false, true)) {
            val base64 = ImageUtils.toJpegBase64(best, config.liveness.jpegQuality)
            viewModelScope.launch { controller.submitFace(listOf(base64)) }
        }
    }

    override fun onCleared() {
        engine?.close()
        engine = null
    }

    class Factory(private val config: DocuPassConfig) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            DocuPassViewModel(config) as T
    }
}
