package com.idanalyzer.docupass.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.idanalyzer.docupass.camera.ImageUtils
import com.idanalyzer.docupass.catalog.CountryCatalog
import com.idanalyzer.docupass.model.DocuPassSession
import com.idanalyzer.docupass.ui.DocuPassViewModel
import kotlinx.coroutines.launch

private const val DOCUMENT_MAX_SIZE = 1600
private const val DOCUMENT_QUALITY = 90

@Composable
fun DocumentScreen(vm: DocuPassViewModel, session: DocuPassSession) {
    // Selection must happen before capture (server requires save_document_selection).
    if (session.selectedDocumentType.isBlank()) {
        DocumentSelection(vm, session)
    } else {
        DocumentCapture(vm, session)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DocumentSelection(vm: DocuPassViewModel, session: DocuPassSession) {
    val context = LocalContext.current
    val catalog = remember { CountryCatalog.load(context) }
    val countries = remember(session) { catalog.countries(session.acceptedCountries) }

    var country by remember { mutableStateOf(session.selectedDocumentCountry.ifBlank { countries.firstOrNull()?.iso ?: "" }) }
    var type by remember { mutableStateOf("") }
    val types = remember(country) { catalog.documentTypes(country, session.acceptedTypes) }
    var countryOpen by remember { mutableStateOf(false) }
    var typeOpen by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Select your document", style = MaterialTheme.typography.headlineSmall)

        ExposedDropdownMenuBox(expanded = countryOpen, onExpandedChange = { countryOpen = it }) {
            OutlinedTextField(
                value = catalog.country(country)?.name_en ?: country,
                onValueChange = {},
                readOnly = true,
                label = { Text("Country") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(countryOpen) },
                modifier = Modifier.menuAnchor().fillMaxWidth(),
            )
            androidx.compose.material3.ExposedDropdownMenu(
                expanded = countryOpen,
                onDismissRequest = { countryOpen = false },
            ) {
                countries.forEach { c ->
                    DropdownMenuItem(text = { Text(c.name_en) }, onClick = {
                        country = c.iso; type = ""; countryOpen = false
                    })
                }
            }
        }

        ExposedDropdownMenuBox(expanded = typeOpen, onExpandedChange = { typeOpen = it }) {
            OutlinedTextField(
                value = types.firstOrNull { it.code == type }?.label ?: "",
                onValueChange = {},
                readOnly = true,
                label = { Text("Document type") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(typeOpen) },
                modifier = Modifier.menuAnchor().fillMaxWidth(),
            )
            androidx.compose.material3.ExposedDropdownMenu(
                expanded = typeOpen,
                onDismissRequest = { typeOpen = false },
            ) {
                types.forEach { t ->
                    DropdownMenuItem(text = { Text(t.label) }, onClick = {
                        type = t.code; typeOpen = false
                    })
                }
            }
        }

        Button(
            onClick = { vm.submitDocumentSelection(country, type) },
            enabled = country.isNotBlank() && type.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Continue") }
    }
}

@Composable
private fun DocumentCapture(vm: DocuPassViewModel, session: DocuPassSession) {
    val camera = rememberCameraController()
    val scope = rememberCoroutineScope()
    var front by remember { mutableStateOf<Bitmap?>(null) }
    var capturing by remember { mutableStateOf(false) }
    val needBack = !session.isFrontOnly

    DisposableEffect(Unit) { onDispose { camera.stop() } }

    val capturingBack = front != null && needBack
    val label = if (front == null) "Capture the front of your document"
    else "Capture the back of your document"

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CameraPreview(modifier = Modifier.fillMaxSize()) { view -> camera.startDocumentCapture(view) }

        Column(
            Modifier.fillMaxWidth().padding(24.dp).align(Alignment.BottomCenter),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            Button(
                enabled = !capturing,
                onClick = {
                    capturing = true
                    scope.launch {
                        try {
                            val bmp = camera.captureDocument()
                            if (front == null) {
                                front = bmp
                                if (!needBack) {
                                    submit(vm, front!!, null)
                                }
                            } else {
                                submit(vm, front!!, bmp)
                            }
                        } finally {
                            capturing = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().aspectRatio(6f),
            ) { Text(if (capturingBack) "Capture back" else "Capture") }
        }
    }
}

private fun submit(vm: DocuPassViewModel, front: Bitmap, back: Bitmap?) {
    val frontB64 = ImageUtils.prepareUpload(front, DOCUMENT_MAX_SIZE, DOCUMENT_QUALITY)
    val backB64 = back?.let { ImageUtils.prepareUpload(it, DOCUMENT_MAX_SIZE, DOCUMENT_QUALITY) }
    vm.submitDocument(frontB64, backB64)
}
