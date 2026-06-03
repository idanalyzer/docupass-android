package com.idanalyzer.docupass.camera

import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Base64
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream
import kotlin.math.min

/** Frame/image helpers shared by capture and upload (mirrors the web flow). */
object ImageUtils {

    /** ImageProxy -> upright Bitmap (applies sensor rotation). Does NOT mirror. */
    fun toUprightBitmap(image: ImageProxy): Bitmap {
        val bitmap = image.toBitmap()
        val rotation = image.imageInfo.rotationDegrees
        if (rotation == 0) return bitmap
        val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /** Scale so the longest side is at most [maxSize], preserving aspect ratio. */
    fun scaleToMax(bitmap: Bitmap, maxSize: Int): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= maxSize) return bitmap
        val scale = maxSize.toFloat() / longest
        val w = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val h = (bitmap.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, w, h, true)
    }

    /** Encode to base64 JPEG with NO `data:` prefix (what upload_* expects). */
    fun toJpegBase64(bitmap: Bitmap, quality: Int): String {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    /** Convenience: scale to [maxSize] then encode. */
    fun prepareUpload(bitmap: Bitmap, maxSize: Int, quality: Int): String =
        toJpegBase64(scaleToMax(bitmap, maxSize), quality)

    fun squareCenterCrop(bitmap: Bitmap): Bitmap {
        val side = min(bitmap.width, bitmap.height)
        val x = (bitmap.width - side) / 2
        val y = (bitmap.height - side) / 2
        return Bitmap.createBitmap(bitmap, x, y, side, side)
    }
}
