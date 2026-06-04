package com.idanalyzer.docupass.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.util.Base64
import android.webkit.WebView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.idanalyzer.docupass.model.DocuPassSession
import com.idanalyzer.docupass.ui.DocuPassViewModel
import com.idanalyzer.docupass.ui.LocalDocuPassStrings
import java.io.ByteArrayOutputStream

// Signature fields are `<img data-signature …>` / `<div data-signature …>` elements
// carrying a data-uid (matches the DocuPass v3 web client). Other data-uid elements
// (e.g. data-image placeholders) are NOT signature fields and must be ignored.
private val SIGNATURE_TAG = Regex("<[a-zA-Z][^>]*\\bdata-signature\\b[^>]*>", RegexOption.IGNORE_CASE)
private val UID_IN_TAG = Regex("data-uid=\"([^\"]+)\"")
private val PREFILL_PLACEHOLDER = Regex("%\\{[0-9A-Za-z_.\\-]+}")

@Composable
fun ContractScreen(vm: DocuPassViewModel, session: DocuPassSession) {
    val s = LocalDocuPassStrings.current
    val uids = remember(session.contractSource) {
        SIGNATURE_TAG.findAll(session.contractSource)
            .mapNotNull { UID_IN_TAG.find(it.value)?.groupValues?.get(1) }
            .distinct().toList()
    }
    // Strip leftover unfilled prefill placeholders, like the web client does.
    val displayHtml = remember(session.contractSource) {
        session.contractSource.replace(PREFILL_PLACEHOLDER, "")
    }
    val signatures = remember { mutableStateMapOf<String, String>() } // uid -> dataURL

    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(s.contractTitle, style = MaterialTheme.typography.headlineSmall)

        if (session.contractSource.isNotBlank()) {
            AndroidView(
                modifier = Modifier.fillMaxWidth().height(360.dp),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = false
                        loadDataWithBaseURL(null, displayHtml, "text/html", "utf-8", null)
                    }
                },
            )
        }

        uids.forEach { uid ->
            Text(s.contractSignature, style = MaterialTheme.typography.titleSmall)
            SignaturePad(
                modifier = Modifier.fillMaxWidth().height(160.dp),
                onCaptured = { bmp -> signatures[uid] = bmp.toPngDataUrl() },
                onCleared = { signatures.remove(uid) },
            )
        }

        val ready = uids.all { signatures.containsKey(it) }
        Button(
            onClick = { vm.submitContract(signatures.toMap()) },
            enabled = ready,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (uids.isEmpty()) s.contractAccept else s.contractSubmit) }
    }
}

@Composable
private fun SignaturePad(
    modifier: Modifier = Modifier,
    onCaptured: (Bitmap) -> Unit,
    onCleared: () -> Unit,
) {
    val strokes = remember { mutableStateListOf<MutableList<Offset>>() }
    var size by remember { mutableStateOf(IntSize.Zero) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(
            modifier = modifier
                .background(Color.White)
                .border(1.dp, Color.LightGray)
                .onSizeChanged { size = it }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { strokes.add(mutableListOf(it)) },
                        onDrag = { change, _ ->
                            strokes.lastOrNull()?.add(change.position)
                            change.consume()
                        },
                        onDragEnd = {
                            if (size.width > 0 && size.height > 0) onCaptured(renderSignature(strokes, size))
                        },
                    )
                },
        ) {
            strokes.forEach { pts ->
                for (i in 1 until pts.size) {
                    drawLine(Color.Black, pts[i - 1], pts[i], strokeWidth = 6f)
                }
            }
        }
        OutlinedButton(onClick = { strokes.clear(); onCleared() }) { Text(LocalDocuPassStrings.current.contractClear) }
    }
}

private fun renderSignature(strokes: List<List<Offset>>, size: IntSize): Bitmap {
    val bmp = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bmp)
    canvas.drawColor(AndroidColor.WHITE)
    val paint = Paint().apply {
        color = AndroidColor.BLACK
        strokeWidth = 6f
        isAntiAlias = true
        style = Paint.Style.STROKE
    }
    strokes.forEach { pts ->
        for (i in 1 until pts.size) {
            canvas.drawLine(pts[i - 1].x, pts[i - 1].y, pts[i].x, pts[i].y, paint)
        }
    }
    return bmp
}

/** DocuPass signatures are sent as a PNG data URL (matches the web flow). */
private fun Bitmap.toPngDataUrl(): String {
    val out = ByteArrayOutputStream()
    compress(Bitmap.CompressFormat.PNG, 100, out)
    return "data:image/png;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
}
