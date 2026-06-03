package com.idanalyzer.docupass.camera

import android.content.Context
import android.graphics.Bitmap
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * Thin CameraX wrapper: a front-camera analysis stream for liveness, and a
 * back-camera still capture for documents. UI-agnostic — give it a [PreviewView].
 */
class CameraController(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
) {
    private val analysisExecutor = Executors.newSingleThreadExecutor()
    private var provider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null

    /**
     * Bind the FRONT camera with a live analysis stream for liveness. [onFrame]
     * is called on a background thread with an upright (non-mirrored) bitmap and
     * a monotonically increasing timestamp; the bitmap is recycled after the
     * callback returns, so copy anything you keep.
     */
    fun startFaceAnalysis(previewView: PreviewView, onFrame: (Bitmap, Long) -> Unit) {
        bind(previewView, CameraSelector.DEFAULT_FRONT_CAMERA) { provider, preview, selector ->
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(analysisExecutor) { proxy: ImageProxy ->
                try {
                    val bmp = ImageUtils.toUprightBitmap(proxy)
                    onFrame(bmp, proxy.imageInfo.timestamp / 1_000_000) // ns -> ms
                    bmp.recycle()
                } catch (_: Throwable) {
                    // drop the frame; never crash the stream
                } finally {
                    proxy.close()
                }
            }
            provider.bindToLifecycle(lifecycleOwner, selector, preview, analysis)
        }
    }

    /** Bind the BACK camera for still document capture. */
    fun startDocumentCapture(previewView: PreviewView) {
        bind(previewView, CameraSelector.DEFAULT_BACK_CAMERA) { provider, preview, selector ->
            val capture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()
            imageCapture = capture
            provider.bindToLifecycle(lifecycleOwner, selector, preview, capture)
        }
    }

    /** Take a document photo and return it as an upright bitmap. */
    suspend fun captureDocument(): Bitmap {
        val capture = imageCapture ?: error("Document capture not started")
        return suspendCoroutine { cont ->
            capture.takePicture(
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        try {
                            cont.resume(ImageUtils.toUprightBitmap(image))
                        } catch (t: Throwable) {
                            cont.resumeWithException(t)
                        } finally {
                            image.close()
                        }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        cont.resumeWithException(exception)
                    }
                },
            )
        }
    }

    fun stop() {
        runCatching { provider?.unbindAll() }
        imageCapture = null
    }

    fun release() {
        stop()
        analysisExecutor.shutdown()
    }

    private fun bind(
        previewView: PreviewView,
        selector: CameraSelector,
        bindUseCases: (ProcessCameraProvider, Preview, CameraSelector) -> Unit,
    ) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            val cameraProvider = future.get()
            provider = cameraProvider
            cameraProvider.unbindAll()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            bindUseCases(cameraProvider, preview, selector)
        }, ContextCompat.getMainExecutor(context))
    }
}
