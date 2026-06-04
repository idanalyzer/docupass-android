package com.idanalyzer.docupass.liveness

import android.graphics.Bitmap
import com.idanalyzer.docupass.LivenessConfig
import kotlin.math.abs
import kotlin.math.hypot

/** The active-liveness step the user is currently being guided through. */
enum class LivenessStep { FRONT, FRONT_SUCCESS, TURN_LEFT, TURN_RIGHT, DONE_SUCCESS, COMPLETE }

/**
 * Result of feeding one frame to [LivenessController.update].
 *
 * @property step       current step
 * @property progress   0..1 hold-progress for the active step
 * @property faceVisible whether a face was detected this frame
 * @property bestNeutralFrame non-null exactly once, when [step] becomes COMPLETE
 */
data class LivenessUpdate(
    val step: LivenessStep,
    val progress: Float,
    val faceVisible: Boolean,
    val bestNeutralFrame: Bitmap? = null,
)

/**
 * Direct port of the DocuPass v3 web `FaceChecker`:
 * neutral-face hold -> turn left -> turn right active-liveness, with best-neutral
 * frame selection by eye-polygon area. Pure logic — feed it landmarks + the frame
 * they were computed from; it tracks timing and picks the capture.
 *
 * Head-pose `percent` from landmarks 4 (nose), 137 (left cheek), 366 (right
 * cheek): > 0 turned left, < 0 turned right.
 */
class LivenessController(
    private val config: LivenessConfig = LivenessConfig(),
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    private var step = LivenessStep.FRONT
    private var holdStart: Long = -1
    private var phaseStart: Long = -1

    private var bestNeutral: Bitmap? = null
    private var bestNeutralArea: Double = -1.0

    val currentStep: LivenessStep get() = step

    fun reset() {
        step = LivenessStep.FRONT
        holdStart = -1
        phaseStart = -1
        bestNeutral?.recycle()
        bestNeutral = null
        bestNeutralArea = -1.0
    }

    /**
     * Advance the state machine by one frame.
     *
     * @param landmarks normalized landmarks, or null if no face this frame
     * @param frame     the camera frame the landmarks came from (already scaled
     *                  to the upload size). The controller may retain a copy as
     *                  the best-neutral capture; the caller should not recycle it.
     */
    fun update(landmarks: List<Landmark2D>?, frame: Bitmap): LivenessUpdate {
        val t = now()
        val faceVisible = landmarks != null && landmarks.size > 366

        return when (step) {
            LivenessStep.FRONT -> updateFront(landmarks, frame, t, faceVisible)
            LivenessStep.FRONT_SUCCESS -> updateSuccessAnim(t, LivenessStep.TURN_LEFT, faceVisible)
            LivenessStep.TURN_LEFT -> updateTurn(landmarks, t, faceVisible, left = true)
            LivenessStep.TURN_RIGHT -> updateTurn(landmarks, t, faceVisible, left = false)
            LivenessStep.DONE_SUCCESS -> updateSuccessAnim(t, LivenessStep.COMPLETE, faceVisible)
            LivenessStep.COMPLETE -> LivenessUpdate(LivenessStep.COMPLETE, 1f, faceVisible)
        }
    }

    private fun updateFront(landmarks: List<Landmark2D>?, frame: Bitmap, t: Long, faceVisible: Boolean): LivenessUpdate {
        if (!faceVisible) {
            holdStart = -1
            return LivenessUpdate(LivenessStep.FRONT, 0f, false)
        }
        val percent = headPose(landmarks!!)
        if (holdStart < 0) holdStart = t
        if (abs(percent) > config.thresholdOffsetPercent) holdStart = t // out of range -> reset

        val diff = t - holdStart
        if (diff > config.frontStayMs) {
            // Time held — commit the best neutral frame and move on.
            if (bestNeutral != null) {
                step = LivenessStep.FRONT_SUCCESS
                phaseStart = -1
                return LivenessUpdate(LivenessStep.FRONT_SUCCESS, 1f, true)
            } else {
                holdStart = t // no good frame captured yet; keep holding
            }
        } else {
            // Within range and holding — consider this frame as a neutral candidate.
            considerNeutral(landmarks, frame)
        }
        return LivenessUpdate(LivenessStep.FRONT, (diff.toFloat() / config.frontStayMs).coerceIn(0f, 1f), true)
    }

    private fun updateTurn(landmarks: List<Landmark2D>?, t: Long, faceVisible: Boolean, left: Boolean): LivenessUpdate {
        val stepNow = if (left) LivenessStep.TURN_LEFT else LivenessStep.TURN_RIGHT
        if (!faceVisible) {
            holdStart = -1
            return LivenessUpdate(stepNow, 0f, false)
        }
        val percent = headPose(landmarks!!)
        if (holdStart < 0) holdStart = t
        val inRange = if (left) percent > config.thresholdTurnPercent
        else percent < -config.thresholdTurnPercent
        if (!inRange) holdStart = t

        val stayMs = if (left) config.leftStayMs else config.rightStayMs
        val diff = t - holdStart
        if (diff > stayMs) {
            step = if (left) LivenessStep.TURN_RIGHT else LivenessStep.DONE_SUCCESS
            holdStart = -1
            phaseStart = -1
            return LivenessUpdate(step, 1f, true)
        }
        return LivenessUpdate(stepNow, (diff.toFloat() / stayMs).coerceIn(0f, 1f), true)
    }

    private fun updateSuccessAnim(t: Long, next: LivenessStep, faceVisible: Boolean): LivenessUpdate {
        if (phaseStart < 0) phaseStart = t
        val diff = t - phaseStart
        if (diff > config.successStayMs) {
            val wasFinal = next == LivenessStep.COMPLETE
            step = next
            phaseStart = -1
            holdStart = -1
            return LivenessUpdate(
                next,
                if (wasFinal) 1f else 0f,
                faceVisible,
                bestNeutralFrame = if (wasFinal) bestNeutral else null,
            )
        }
        val current = if (next == LivenessStep.TURN_LEFT) LivenessStep.FRONT_SUCCESS else LivenessStep.DONE_SUCCESS
        return LivenessUpdate(current, (diff.toFloat() / config.successStayMs).coerceIn(0f, 1f), faceVisible)
    }

    /** Keep the frame with the largest combined eye-polygon area as best-neutral. */
    private fun considerNeutral(landmarks: List<Landmark2D>, frame: Bitmap) {
        val area = eyeArea(landmarks)
        if (area > bestNeutralArea) {
            bestNeutralArea = area
            bestNeutral?.recycle()
            // Copy so the caller's frame buffer can be reused/recycled.
            bestNeutral = frame.copy(frame.config ?: Bitmap.Config.ARGB_8888, false)
        }
    }

    /** percent = (dist(nose,leftCheek) - dist(nose,rightCheek)) / total * 100. */
    private fun headPose(lm: List<Landmark2D>): Float {
        val pc = lm[4]; val pl = lm[137]; val pr = lm[366]
        val dL = hypot((pc.x - pl.x).toDouble(), (pc.y - pl.y).toDouble())
        val dR = hypot((pc.x - pr.x).toDouble(), (pc.y - pr.y).toDouble())
        val total = dL + dR
        if (total == 0.0) return 0f
        return ((dL / total) * 100 - (dR / total) * 100).toFloat()
    }

    private fun eyeArea(lm: List<Landmark2D>): Double =
        polygonArea(lm, LEFT_EYE) + polygonArea(lm, RIGHT_EYE)

    private fun polygonArea(lm: List<Landmark2D>, idx: IntArray): Double {
        val n = idx.size
        var area = 0.0
        for (k in idx.indices) {
            val i = idx[k]
            val j = idx[(k + 1) % n]
            if (i >= lm.size || j >= lm.size) return 0.0
            area += lm[i].x * lm[j].y
            area -= lm[j].x * lm[i].y
        }
        return abs(area) / 2.0
    }

    companion object {
        // Eye landmark rings (MediaPipe FaceMesh 478) — from the web FaceChecker.
        private val LEFT_EYE = intArrayOf(33, 7, 163, 144, 145, 153, 154, 155, 133, 173, 157, 158, 159, 160, 161, 246)
        private val RIGHT_EYE = intArrayOf(263, 249, 390, 373, 374, 380, 381, 382, 362, 398, 384, 385, 386, 387, 388, 466)
    }
}
