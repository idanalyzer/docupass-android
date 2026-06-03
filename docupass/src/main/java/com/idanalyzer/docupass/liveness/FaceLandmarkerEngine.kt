package com.idanalyzer.docupass.liveness

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult

/** A 2D normalized facial landmark (x,y in [0,1]); z is unused for head-pose. */
data class Landmark2D(val x: Float, val y: Float)

/**
 * Wraps the MediaPipe Tasks Vision `FaceLandmarker` (VIDEO mode, single face)
 * using the bundled `face_landmarker.task` — the SAME model the web flow uses,
 * so liveness behaviour is identical across web/Android/iOS.
 *
 * Not thread-safe: call [detect] from a single analysis thread, then [close].
 */
class FaceLandmarkerEngine(
    context: Context,
    modelAssetPath: String = "face_landmarker.task",
    useGpu: Boolean = false,
) {
    private val landmarker: FaceLandmarker

    init {
        val base = BaseOptions.builder()
            .setModelAssetPath(modelAssetPath)
            .apply { if (useGpu) setDelegate(com.google.mediapipe.tasks.core.Delegate.GPU) }
            .build()
        val options = FaceLandmarker.FaceLandmarkerOptions.builder()
            .setBaseOptions(base)
            .setRunningMode(RunningMode.VIDEO)
            .setNumFaces(1)
            .setOutputFaceBlendshapes(false)
            .setOutputFacialTransformationMatrixes(false)
            .build()
        landmarker = FaceLandmarker.createFromOptions(context, options)
    }

    /**
     * Detect landmarks for one video frame. [timestampMs] must increase
     * monotonically across calls. Returns null when no face is found.
     */
    fun detect(bitmap: Bitmap, timestampMs: Long): List<Landmark2D>? {
        val image = BitmapImageBuilder(bitmap).build()
        val result: FaceLandmarkerResult = landmarker.detectForVideo(image, timestampMs)
        val faces = result.faceLandmarks()
        if (faces.isEmpty() || faces[0].isEmpty()) return null
        return faces[0].map { Landmark2D(it.x(), it.y()) }
    }

    fun close() {
        runCatching { landmarker.close() }
    }
}
