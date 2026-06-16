package com.idanalyzer.docupass
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Matrix
import android.graphics.Paint
import android.Manifest
import android.util.Base64
import android.util.Log
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt
import kotlinx.coroutines.launch
/**
 * Fixed-workflow DocuPass KYC settings for the Android Compose SDK.
 *
 * This SDK intentionally does not expose workflow customization. Server task routing
 * is handled by DocuPass; the local fallback/face-action workflow uses SDK defaults.
 */
data class KYCSettings(
    val apiConfig: DocupassApiConfig = DocupassApiConfig(),
    val maskCircleRadius: Float = 0.42f,
    val maskCircleY: Float = 0.45f,
    val turnTimeSeconds: Float = 2.0f,
    val onFinish: (KYCResult) -> Unit = {},
    val onBackAtFirstStep: () -> Unit = {}
)

fun kycSettingsFromReference(
    reference: String,
    partyId: String? = null,
    geolocation: String? = null,
    enabled: Boolean = true,
    onFinish: (KYCResult) -> Unit = {},
    onBackAtFirstStep: () -> Unit = {}
): KYCSettings {
    return KYCSettings(
        apiConfig = DocupassConfigFactory.fromReference(
            reference = reference,
            partyId = partyId,
            geolocation = geolocation,
            enabled = enabled
        ),
        onFinish = onFinish,
        onBackAtFirstStep = onBackAtFirstStep
    )
}

@Composable
fun KYCScreen(settings: KYCSettings) {
    val controller = remember(settings.apiConfig) {
        DocupassKycController(config = settings.apiConfig)
    }
    val uiState by controller.state.collectAsState()

    LaunchedEffect(controller) {
        controller.emit(DocupassKycIntent.Start)
    }
    DisposableEffect(controller) {
        onDispose { controller.close() }
    }
    val isResultScreen =
        uiState.event is DocupassKycEvent.Completed || uiState.event is DocupassKycEvent.Failed
    val canShowBack = !isResultScreen && uiState.event !is DocupassKycEvent.Loading
    fun goBackOrExitFirstStep() {
        when {
            uiState.canGoBack -> controller.emit(DocupassKycIntent.Back)
            else -> settings.onBackAtFirstStep()
        }
    }
    BackHandler(enabled = true) {
        when {
            uiState.error != null -> controller.emit(DocupassKycIntent.ClearError)
            isResultScreen -> Unit
            !uiState.isBusy -> goBackOrExitFirstStep()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF050A08))
    ) {
        when (val event = uiState.event) {
            DocupassKycEvent.Loading -> DefaultInitializingOverlay()

            is DocupassKycEvent.PhoneVerification -> PhoneVerificationScreen(
                state = event.state,
                isBusy = uiState.isBusy,
                codeSent = event.codeSent,
                currentNumber = event.currentNumber,
                onSendCode = { number, type ->
                    controller.emit(DocupassKycIntent.SendPhoneCode(number, type))
                },
                onVerifyCode = { number, code ->
                    controller.emit(DocupassKycIntent.VerifyPhoneCode(number, code))
                }
            )

            is DocupassKycEvent.CustomForm -> CustomFormScreen(
                fields = event.fields,
                isBusy = uiState.isBusy,
                onSubmit = { answers ->
                    controller.emit(DocupassKycIntent.SaveCustomForm(answers))
                }
            )

            is DocupassKycEvent.DocumentCountrySelection -> CountryPickerScreen(
                countries = event.countries,
                onSelected = { country ->
                    controller.emit(DocupassKycIntent.SelectDocumentCountry(country.code))
                }
            )

            is DocupassKycEvent.DocumentSelection -> IDTypePickerScreen(
                country = event.country,
                documentTypes = event.documentTypes,
                isLoading = uiState.isBusy,
                onSelected = { documentType ->
                    controller.emit(DocupassKycIntent.SelectDocumentType(documentType.apiTypeCode))
                }
            )

            is DocupassKycEvent.DocumentCapture -> DocumentCaptureScreen(
                documentType = event.documentType,
                documentSide = event.documentSide,
                isBusy = uiState.isBusy,
                onCaptured = { front, back ->
                    controller.emit(DocupassKycIntent.UploadDocument(front, back))
                }
            )

            is DocupassKycEvent.FaceVerification -> BiometricScreen(
                actions = event.actions,
                globalSettings = settings,
                isBusy = uiState.isBusy,
                onComplete = { faceBase64List ->
                    controller.emit(DocupassKycIntent.UploadFace(faceBase64List))
                }
            )

            is DocupassKycEvent.Contract -> ContractScreen(
                state = event.state,
                isBusy = uiState.isBusy,
                onSubmit = { signatures ->
                    controller.emit(DocupassKycIntent.SubmitContract(signatures))
                }
            )

            DocupassKycEvent.PartyPending -> PartyPendingScreen(
                isBusy = uiState.isBusy,
                onRefresh = {
                    controller.emit(DocupassKycIntent.Refresh)
                }
            )

            is DocupassKycEvent.Completed -> {
                SuccessResultScreen(onFinish = {
                    settings.onFinish(event.result)
                })
            }

            is DocupassKycEvent.Failed -> {
                FailedResultScreen(error = event.error, onFinish = {
                    settings.onFinish(event.result)
                })
            }
        }

        if (uiState.isBusy) {
            DefaultBusyProgress()
        }

        if (canShowBack) {
            KycBackButton(
                enabled = !uiState.isBusy,
                onBack = { goBackOrExitFirstStep() }
            )
        }

        uiState.error?.let { errorEvent ->
            DefaultApiErrorAlert(
                message = errorEvent.message,
                onDismiss = {
                    controller.emit(DocupassKycIntent.ClearError)
                }
            )
        }
    }
}

@Composable
fun KYCScreen(
    reference: String,
    partyId: String? = null,
    geolocation: String? = null,
    onFinish: (KYCResult) -> Unit = {},
    onBackAtFirstStep: () -> Unit = {}
) {
    KYCScreen(
        settings = kycSettingsFromReference(
            reference = reference,
            partyId = partyId,
            geolocation = geolocation,
            onFinish = onFinish,
            onBackAtFirstStep = onBackAtFirstStep
        )
    )
}

private fun bitmapToBase64Jpeg(bitmap: Bitmap, quality: Int = 90): String {
    val stream = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
    return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
}

@Composable
private fun KycBackButton(
    enabled: Boolean,
    onBack: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(start = 16.dp, top = 8.dp),
        contentAlignment = Alignment.TopStart
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(if (enabled) Color.Black.copy(alpha = 0.58f) else Color.Black.copy(alpha = 0.28f))
                .border(1.dp, Color.White.copy(alpha = if (enabled) 0.35f else 0.14f), CircleShape)
                .clickable(enabled = enabled) { onBack() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                "<",
                color = if (enabled) Color.White else Color.White.copy(alpha = 0.42f),
                fontWeight = FontWeight.Black,
                fontSize = 24.sp
            )
        }
    }
}

@Composable
private fun DefaultBusyProgress() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(12.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp),
            color = Color(0xFF00FFAB),
            trackColor = Color.White.copy(alpha = 0.1f)
        )
    }
}

@Composable
private fun DefaultApiErrorAlert(
    message: String,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF451515))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("ERROR", color = Color(0xFFFFA3A3), fontWeight = FontWeight.Black)
                Spacer(modifier = Modifier.height(4.dp))
                Text(message, color = Color.White)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(onClick = onDismiss) {
                    Text("DISMISS")
                }
            }
        }
    }
}

@Composable
private fun DefaultInitializingOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.65f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(
                color = Color(0xFF00FFAB),
                trackColor = Color.White.copy(alpha = 0.2f)
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Initializing...",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun PhoneVerificationScreen(
    state: DocupassSessionState,
    isBusy: Boolean,
    codeSent: Boolean,
    currentNumber: String?,
    onSendCode: (number: String?, type: String) -> Unit,
    onVerifyCode: (number: String?, code: String) -> Unit
) {
    val presetPhone = state.userPhone?.takeIf { it.isNotBlank() }
    val countryCodes = state.phoneCountryCodes
    var selectedDialCode by remember(countryCodes) {
        mutableStateOf(countryCodes.firstOrNull()?.dialCode ?: "+1")
    }
    var localNumber by remember { mutableStateOf("") }
    var otp by remember { mutableStateOf("") }

    fun buildNumber(): String? {
        if (presetPhone != null) return null
        val digits = localNumber.trim().trimStart('0')
        return if (digits.isBlank()) null else selectedDialCode + digits
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, top = 72.dp, end = 24.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("STEP: PHONE VERIFICATION", color = Color(0xFF00FFAB), fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Verify your phone number to continue.", color = Color.White.copy(alpha = 0.78f))
        Spacer(modifier = Modifier.height(24.dp))

        if (presetPhone != null) {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f))) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Phone number", color = Color.Gray, fontSize = 12.sp)
                    Text(presetPhone, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            }
        } else {
            if (countryCodes.isNotEmpty()) {
                Text("Country code", color = Color.Gray, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(countryCodes) { code ->
                        val selected = selectedDialCode == code.dialCode
                        Button(
                            onClick = { selectedDialCode = code.dialCode },
                            enabled = !isBusy,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selected) Color(0xFF00FFAB) else Color.White.copy(alpha = 0.1f)
                            )
                        ) {
                            Text(
                                text = "${code.name} ${code.dialCode}",
                                color = if (selected) Color(0xFF00261A) else Color.White
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
            OutlinedTextField(
                value = localNumber,
                onValueChange = { localNumber = it.filter { ch -> ch.isDigit() } },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isBusy,
                label = { Text("Phone number") },
                prefix = { Text(selectedDialCode, color = Color.White) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF00FFAB),
                    unfocusedBorderColor = Color.DarkGray,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )
        }

        Spacer(modifier = Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = {
                    val number = buildNumber()
                    onSendCode(number, "sms")
                },
                enabled = !isBusy && (presetPhone != null || buildNumber() != null),
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FFAB))
            ) {
                Text("SEND SMS", color = Color(0xFF00261A), fontWeight = FontWeight.Black)
            }
            OutlinedButton(
                onClick = {
                    val number = buildNumber()
                    onSendCode(number, "call")
                },
                enabled = !isBusy && (presetPhone != null || buildNumber() != null),
                modifier = Modifier.weight(1f)
            ) {
                Text("CALL")
            }
        }

        if (codeSent) {
            Spacer(modifier = Modifier.height(24.dp))
            OutlinedTextField(
                value = otp,
                onValueChange = { otp = it.filter { ch -> ch.isDigit() }.take(6) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isBusy,
                label = { Text("6 digit code") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF00FFAB),
                    unfocusedBorderColor = Color.DarkGray,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = { onVerifyCode(currentNumber, otp) },
                enabled = !isBusy && otp.length == 6,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.16f))
            ) {
                Text("VERIFY CODE", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun CustomFormScreen(
    fields: List<DocupassCustomField>,
    isBusy: Boolean,
    onSubmit: (Map<String, String>) -> Unit
) {
    val answers = remember(fields) { mutableStateMapOf<String, String>() }
    val requiredAnswered = fields.all { field ->
        val key = field.fieldId.ifBlank { field.fieldLabel }
        !answers[key].isNullOrBlank()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, top = 72.dp, end = 24.dp, bottom = 24.dp)
    ) {
        Text("STEP: CUSTOM FORM", color = Color(0xFF00FFAB), fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(18.dp))

        fields.forEach { field ->
            val key = field.fieldId.ifBlank { field.fieldLabel }
            Text(field.fieldLabel.ifBlank { key }, color = Color.White, fontWeight = FontWeight.Bold)
            if (field.fieldDescription.isNotBlank()) {
                Text(field.fieldDescription, color = Color.Gray, fontSize = 12.sp)
            }
            Spacer(modifier = Modifier.height(8.dp))

            if (field.fieldType == 2) {
                val options = remember(field.fieldData) { parseCustomFieldOptions(field.fieldData) }
                options.forEach { option ->
                    val selected = answers[key] == option.value
                    Button(
                        onClick = { answers[key] = option.value },
                        enabled = !isBusy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selected) Color(0xFF00FFAB) else Color.White.copy(alpha = 0.1f)
                        )
                    ) {
                        Text(option.label, color = if (selected) Color(0xFF00261A) else Color.White)
                    }
                }
            } else {
                OutlinedTextField(
                    value = answers[key].orEmpty(),
                    onValueChange = { answers[key] = it },
                    enabled = !isBusy,
                    modifier = Modifier.fillMaxWidth(),
                    minLines = if (field.fieldType == 1) 3 else 1,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF00FFAB),
                        unfocusedBorderColor = Color.DarkGray,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
            }
            Spacer(modifier = Modifier.height(18.dp))
        }

        Button(
            onClick = { onSubmit(answers.toMap()) },
            enabled = !isBusy && fields.isNotEmpty() && requiredAnswered,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FFAB))
        ) {
            Text("SAVE FORM", color = Color(0xFF00261A), fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun ContractScreen(
    state: DocupassSessionState,
    isBusy: Boolean,
    onSubmit: (Map<String, String>) -> Unit
) {
    val contractSource = state.contractSource.orEmpty()
    val signatureFields = remember(contractSource) { extractContractSignatureFields(contractSource) }
    val signatureStrokes = remember(contractSource) { mutableStateListOf<List<Offset>>() }
    var activeSignatureStroke by remember(contractSource) { mutableStateOf<List<Offset>>(emptyList()) }
    var signaturePadSize by remember(contractSource) { mutableStateOf(IntSize.Zero) }
    val hasSignature = signatureStrokes.any { it.isNotEmpty() } || activeSignatureStroke.isNotEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(start = 16.dp, top = 64.dp, end = 16.dp, bottom = 16.dp)
    ) {
        Text("STEP: REVIEW CONTRACT", color = Color(0xFF00FFAB), fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(10.dp))
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = false
                        loadDataWithBaseURL(
                            null,
                            cleanupContractHtml(contractSource),
                            "text/html",
                            "UTF-8",
                            null
                        )
                    }
                },
                update = { webView ->
                    webView.loadDataWithBaseURL(
                        null,
                        cleanupContractHtml(contractSource),
                        "text/html",
                        "UTF-8",
                        null
                    )
                }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        if (signatureFields.isNotEmpty()) {
            Text("${signatureFields.size} signature field(s) required", color = Color.White)
            Spacer(modifier = Modifier.height(8.dp))
            val signatureShape = RoundedCornerShape(8.dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(176.dp)
                    .clip(signatureShape)
                    .background(Color.White)
                    .border(1.dp, if (hasSignature) Color(0xFF00FFAB) else Color(0xFF4A5C55), signatureShape)
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .onSizeChanged { signaturePadSize = it }
                        .pointerInput(isBusy) {
                            if (!isBusy) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        activeSignatureStroke = listOf(offset)
                                    },
                                    onDrag = { change, _ ->
                                        activeSignatureStroke = activeSignatureStroke + change.position
                                    },
                                    onDragEnd = {
                                        if (activeSignatureStroke.isNotEmpty()) {
                                            signatureStrokes.add(activeSignatureStroke)
                                        }
                                        activeSignatureStroke = emptyList()
                                    },
                                    onDragCancel = {
                                        activeSignatureStroke = emptyList()
                                    }
                                )
                            }
                        }
                ) {
                    val strokeWidth = 5.dp.toPx()
                    (signatureStrokes + listOf(activeSignatureStroke)).forEach { stroke ->
                        when (stroke.size) {
                            0 -> Unit
                            1 -> drawCircle(Color.Black, radius = strokeWidth / 2f, center = stroke.first())
                            else -> stroke.zipWithNext().forEach { (start, end) ->
                                drawLine(
                                    color = Color.Black,
                                    start = start,
                                    end = end,
                                    strokeWidth = strokeWidth,
                                    cap = StrokeCap.Round
                                )
                            }
                        }
                    }
                }
                if (!hasSignature) {
                    Text(
                        "Draw signature here",
                        modifier = Modifier.align(Alignment.Center),
                        color = Color(0xFF6C7772),
                        fontSize = 14.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(
                    onClick = {
                        signatureStrokes.clear()
                        activeSignatureStroke = emptyList()
                    },
                    enabled = !isBusy && hasSignature
                ) {
                    Text("CLEAR")
                }
            }
        } else {
            Text("No signature image is required for this contract.", color = Color.White.copy(alpha = 0.78f))
        }
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = {
                val signatures = if (signatureFields.isEmpty()) {
                    emptyMap()
                } else {
                    val image = createHandwrittenSignatureDataUrl(signatureStrokes.toList(), signaturePadSize)
                        ?: return@Button
                    signatureFields.associate { it.uid to image }
                }
                onSubmit(signatures)
            },
            enabled = !isBusy && (signatureFields.isEmpty() || hasSignature),
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FFAB))
        ) {
            Text("ACCEPT AND SUBMIT", color = Color(0xFF00261A), fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun PartyPendingScreen(
    isBusy: Boolean,
    onRefresh: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(start = 24.dp, top = 72.dp, end = 24.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("SIGNATURE PENDING", color = Color(0xFF00FFAB), fontWeight = FontWeight.Black, fontSize = 16.sp)
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            "Your part is complete. The contract is waiting for another party to finish signing.",
            color = Color.White,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onRefresh, enabled = !isBusy, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FFAB))) {
            Text("REFRESH STATUS", color = Color(0xFF00261A), fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun CountryPickerScreen(countries: List<KYCCountry>, onSelected: (KYCCountry) -> Unit) {
    var query by remember { mutableStateOf("") }

    val displayList =
        if (query.isEmpty()) {
            countries
        } else {
            countries.filter {
                it.name.contains(query, ignoreCase = true) || it.code.contains(query, ignoreCase = true)
            }
        }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(start = 24.dp, top = 72.dp, end = 24.dp, bottom = 24.dp)
    ) {
        Text(
            "STEP: SELECT COUNTRY",
            color = Color(0xFF00FFAB),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search...", color = Color.Gray) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF00FFAB),
                unfocusedBorderColor = Color.DarkGray,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            shape = RoundedCornerShape(12.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.navigationBarsPadding()
        ) {
            items(displayList) { country ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelected(country) },
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f))
                ) {
                    Row(modifier = Modifier.padding(16.dp)) {
                        Text(country.flag, fontSize = 24.sp)
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(country.name, color = Color.White, fontSize = 18.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun IDTypePickerScreen(
    country: KYCCountry?,
    documentTypes: List<KYCDocumentType>,
    isLoading: Boolean,
    onSelected: (KYCDocumentType) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(start = 24.dp, top = 72.dp, end = 24.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "STEP: SELECT DOCUMENT",
            color = Color(0xFF00FFAB),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
        if (country != null) {
            Text("For ${country.flag} ${country.name}", color = Color.Gray)
        }
        Spacer(modifier = Modifier.height(32.dp))
        documentTypes.forEach { type ->
            Button(
                onClick = { onSelected(type) },
                enabled = !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .padding(vertical = 4.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f))
            ) {
                Text(type.label, color = Color.White, fontSize = 18.sp)
            }
        }
    }
}

private enum class DocumentCaptureSide {
    FRONT, BACK
}

@Composable
private fun DocumentCaptureScreen(
    documentType: KYCDocumentType?,
    documentSide: Int?,
    isBusy: Boolean,
    onCaptured: (frontBase64: String, backBase64: String?) -> Unit
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasPermission = it
    }
    LaunchedEffect(Unit) {
        if (!hasPermission) launcher.launch(Manifest.permission.CAMERA)
    }

    val requiresBack = when (documentSide) {
        1 -> false
        2 -> documentType != KYCDocumentType.PASSPORT
        else -> documentType?.requiresBackSide == true
    }
    var activeCaptureSide by remember { mutableStateOf<DocumentCaptureSide?>(null) }
    var latestFrame by remember { mutableStateOf<Bitmap?>(null) }
    var frontBase64 by remember { mutableStateOf<String?>(null) }
    var backBase64 by remember { mutableStateOf<String?>(null) }
    var frontPreviewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var backPreviewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var previewSize by remember { mutableStateOf(IntSize.Zero) }
    var cardMaskType by remember(documentType) {
        mutableStateOf(
            when (documentType) {
                KYCDocumentType.DRIVER_LICENSE -> KYCDocumentType.DRIVER_LICENSE
                KYCDocumentType.IDENTITY_CARD -> KYCDocumentType.IDENTITY_CARD
                else -> null
            }
        )
    }

    if (!hasPermission) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Camera permission is required", color = Color.White)
        }
        return
    }

    val readyToSubmit = frontBase64 != null && (!requiresBack || backBase64 != null)
    if (activeCaptureSide == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(start = 24.dp, top = 72.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.45f))
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Text(
                        "STEP: DOCUMENT UPLOAD",
                        color = Color(0xFF00FFAB),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Front: ${if (frontBase64 != null) "Done" else "Pending"} | Back: ${
                            if (!requiresBack) "Not Required" else if (backBase64 != null) "Done" else "Pending"
                        }",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            DocumentCapturePreviewCard(
                bitmap = frontPreviewBitmap,
                contentDescription = "Front capture preview",
                emptyLabel = "No front photo yet"
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    latestFrame = null
                    activeCaptureSide = DocumentCaptureSide.FRONT
                },
                enabled = !isBusy,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FFAB))
            ) {
                Text("CAPTURE DOCUMENT FRONT", color = Color(0xFF00261A), fontWeight = FontWeight.Black)
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (requiresBack) {
                DocumentCapturePreviewCard(
                    bitmap = backPreviewBitmap,
                    contentDescription = "Back capture preview",
                    emptyLabel = "No back photo yet"
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        latestFrame = null
                        activeCaptureSide = DocumentCaptureSide.BACK
                    },
                    enabled = !isBusy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f))
                ) {
                    Text("CAPTURE DOCUMENT BACK", color = Color.White, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(20.dp))
            } else {
                Text("Back side is not required for passport.", color = Color.Gray, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(20.dp))
            }

            Button(
                onClick = {
                    val front = frontBase64 ?: return@Button
                    val back = if (requiresBack) backBase64 else null
                    onCaptured(front, back)
                },
                enabled = !isBusy && readyToSubmit,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF00FFAB),
                    contentColor = Color(0xFF00261A),
                    disabledContainerColor = Color.White.copy(alpha = 0.1f),
                    disabledContentColor = Color.White.copy(alpha = 0.35f)
                )
            ) {
                Text("UPLOAD DOCUMENT", fontWeight = FontWeight.Black)
            }
        }
    } else {
        val side = activeCaptureSide ?: DocumentCaptureSide.FRONT
        val isPhonePortrait =
            LocalConfiguration.current.orientation != Configuration.ORIENTATION_LANDSCAPE
        val previewAspect = if (isPhonePortrait) 9f / 16f else 16f / 9f
        val canToggleCardMask =
            documentType == KYCDocumentType.DRIVER_LICENSE || documentType == KYCDocumentType.IDENTITY_CARD
        val effectiveMaskType = if (canToggleCardMask) cardMaskType ?: documentType else documentType
        val maskSpec = resolveDocumentMaskSpec(effectiveMaskType, isPhonePortrait)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF090909))
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(top = 64.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(if (isPhonePortrait) 1f else 0.9f)
                    .aspectRatio(previewAspect)
                    .clip(RoundedCornerShape(20.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                    .background(Color.Black)
                    .onSizeChanged { previewSize = it }
            ) {
                CameraPreview(
                    cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA,
                    mirrorX = false
                ) { bitmap ->
                    latestFrame = bitmap
                }
                DocumentMaskOverlay(
                    documentType = effectiveMaskType,
                    maskSpec = maskSpec,
                    isPhonePortrait = isPhonePortrait
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            val captureEnabled = !isBusy && latestFrame != null
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                CameraShutterButton(
                    enabled = captureEnabled,
                    onClick = {
                        val frame = latestFrame ?: return@CameraShutterButton
                        val cropped = cropBitmapToMask(
                            source = frame,
                            previewSize = previewSize,
                            maskSpec = maskSpec,
                            isPhonePortrait = isPhonePortrait
                        )
                        val encoded = bitmapToBase64Jpeg(cropped)
                        if (side == DocumentCaptureSide.FRONT) {
                            frontBase64 = encoded
                            frontPreviewBitmap = cropped
                        } else {
                            backBase64 = encoded
                            backPreviewBitmap = cropped
                        }
                        activeCaptureSide = null
                        latestFrame = null
                    }
                )

                if (canToggleCardMask) {
                    OutlinedButton(
                        onClick = {
                            cardMaskType =
                                if (effectiveMaskType == KYCDocumentType.DRIVER_LICENSE) {
                                    KYCDocumentType.IDENTITY_CARD
                                } else {
                                    KYCDocumentType.DRIVER_LICENSE
                                }
                        },
                        enabled = !isBusy,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .width(70.dp)
                            .height(42.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (effectiveMaskType == KYCDocumentType.DRIVER_LICENSE) {
                                    "VERT"
                                } else {
                                    "LAND"
                                },
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CameraShutterButton(
    enabled: Boolean,
    onClick: () -> Unit
) {
    val outerColor = if (enabled) Color.White else Color.White.copy(alpha = 0.42f)
    val innerColor = if (enabled) Color.White else Color.White.copy(alpha = 0.42f)

    Box(
        modifier = Modifier
            .size(84.dp)
            .clip(CircleShape)
            .border(3.dp, outerColor, CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(66.dp)
                .clip(CircleShape)
                .background(innerColor),
        )
    }
}

@Composable
private fun DocumentCapturePreviewCard(
    bitmap: Bitmap?,
    contentDescription: String,
    emptyLabel: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(132.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
                .clip(RoundedCornerShape(10.dp))
                .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(10.dp))
                .background(Color.Black.copy(alpha = 0.35f)),
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = contentDescription,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            } else {
                Text(
                    text = emptyLabel,
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp
                )
            }
        }
    }
}

private data class DocumentMaskSpec(
    val aspectRatio: Float,
    val widthRatioPortrait: Float,
    val widthRatioLandscape: Float,
    val centerYRatio: Float,
    val lowerHalfOnly: Boolean
)

@Composable
private fun DocumentMaskOverlay(
    documentType: KYCDocumentType?,
    maskSpec: DocumentMaskSpec,
    isPhonePortrait: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                val frame = calculateMaskFrame(
                    containerWidth = size.width,
                    containerHeight = size.height,
                    spec = maskSpec,
                    isPhonePortrait = isPhonePortrait
                )
                val cornerRadius = 16.dp.toPx()

                drawRect(Color.Black.copy(alpha = 0.56f))
                drawRoundRect(
                    color = Color.Transparent,
                    topLeft = Offset(frame.left, frame.top),
                    size = Size(frame.width, frame.height),
                    cornerRadius = CornerRadius(cornerRadius, cornerRadius),
                    blendMode = BlendMode.Clear
                )
                drawRoundRect(
                    color = Color(0xFF00FFAB),
                    topLeft = Offset(frame.left, frame.top),
                    size = Size(frame.width, frame.height),
                    cornerRadius = CornerRadius(cornerRadius, cornerRadius),
                    style = Stroke(width = 2.5.dp.toPx())
                )

                if (maskSpec.lowerHalfOnly) {
                    val halfY = frame.top + (frame.height * 0.5f)
                    drawRect(
                        color = Color.Black.copy(alpha = 0.36f),
                        topLeft = Offset(frame.left, frame.top),
                        size = Size(frame.width, frame.height * 0.5f)
                    )
                    drawLine(
                        color = Color(0xFFFFD166),
                        start = Offset(frame.left, halfY),
                        end = Offset(frame.left + frame.width, halfY),
                        strokeWidth = 2.dp.toPx()
                    )
                }
            }
    )
}

private data class MaskFrame(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float
)

private fun calculateMaskFrame(
    containerWidth: Float,
    containerHeight: Float,
    spec: DocumentMaskSpec,
    isPhonePortrait: Boolean
): MaskFrame {
    val frameWidthRatio = if (isPhonePortrait) spec.widthRatioPortrait else spec.widthRatioLandscape
    var frameWidth = containerWidth * frameWidthRatio
    var frameHeight = frameWidth / spec.aspectRatio

    val maxFrameHeight = containerHeight * if (isPhonePortrait) 0.94f else 0.86f
    if (frameHeight > maxFrameHeight) {
        frameHeight = maxFrameHeight
        frameWidth = frameHeight * spec.aspectRatio
    }

    val centerX = containerWidth * 0.5f
    val centerY = containerHeight * spec.centerYRatio
    val left = centerX - (frameWidth * 0.5f)
    val top = centerY - (frameHeight * 0.5f)
    return MaskFrame(left = left, top = top, width = frameWidth, height = frameHeight)
}

private fun cropBitmapToMask(
    source: Bitmap,
    previewSize: IntSize,
    maskSpec: DocumentMaskSpec,
    isPhonePortrait: Boolean
): Bitmap {
    if (previewSize.width <= 0 || previewSize.height <= 0) return source

    val frame = calculateMaskFrame(
        containerWidth = previewSize.width.toFloat(),
        containerHeight = previewSize.height.toFloat(),
        spec = maskSpec,
        isPhonePortrait = isPhonePortrait
    )

    var left = ((frame.left / previewSize.width) * source.width).toInt()
    var top = ((frame.top / previewSize.height) * source.height).toInt()
    var width = ((frame.width / previewSize.width) * source.width).toInt()
    var height = ((frame.height / previewSize.height) * source.height).toInt()

    if (maskSpec.lowerHalfOnly) {
        top += height / 2
        height -= height / 2
    }

    left = max(0, left)
    top = max(0, top)
    width = min(source.width - left, width)
    height = min(source.height - top, height)

    if (width <= 2 || height <= 2) return source
    return Bitmap.createBitmap(source, left, top, width, height)
}

private fun resolveDocumentMaskSpec(
    documentType: KYCDocumentType?,
    isPhonePortrait: Boolean
): DocumentMaskSpec {
    // Reference dimensions:
    // ID-1 cards (ID card / most DL): 85.60 x 53.98 mm (ratio ~1.586)
    // ID-3 passport booklet page: 125 x 88 mm (ratio ~1.420 landscape / 0.704 portrait)
    val id1LandscapeRatio = 85.60f / 53.98f
    val id1PortraitRatio = 53.98f / 85.60f
    val passportLandscapeRatio = 125f / 88f
    val passportPortraitRatio = 88f / 125f

    return when (documentType) {
        KYCDocumentType.DRIVER_LICENSE -> {
            if (isPhonePortrait) {
                DocumentMaskSpec(
                    aspectRatio = id1PortraitRatio,
                    widthRatioPortrait = 0.96f,
                    widthRatioLandscape = 0.90f,
                    centerYRatio = 0.52f,
                    lowerHalfOnly = false
                )
            } else {
                DocumentMaskSpec(
                    aspectRatio = id1LandscapeRatio,
                    widthRatioPortrait = 0.96f,
                    widthRatioLandscape = 0.92f,
                    centerYRatio = 0.50f,
                    lowerHalfOnly = false
                )
            }
        }

        KYCDocumentType.PASSPORT -> {
            if (isPhonePortrait) {
                DocumentMaskSpec(
                    aspectRatio = passportPortraitRatio,
                    widthRatioPortrait = 0.96f,
                    widthRatioLandscape = 0.90f,
                    centerYRatio = 0.55f,
                    lowerHalfOnly = true
                )
            } else {
                DocumentMaskSpec(
                    aspectRatio = passportLandscapeRatio,
                    widthRatioPortrait = 0.96f,
                    widthRatioLandscape = 0.92f,
                    centerYRatio = 0.52f,
                    lowerHalfOnly = true
                )
            }
        }

        else -> {
            DocumentMaskSpec(
                aspectRatio = id1LandscapeRatio,
                widthRatioPortrait = 0.96f,
                widthRatioLandscape = 0.92f,
                centerYRatio = 0.50f,
                lowerHalfOnly = false
            )
        }
    }
}

@Composable
private fun SuccessResultScreen(onFinish: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("✅", fontSize = 80.sp)
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            "VERIFIED SUCCESSFULLY",
            color = Color(0xFF00FFAB),
            fontSize = 20.sp,
            fontWeight = FontWeight.Black
        )
        Spacer(modifier = Modifier.height(40.dp))
        Button(onClick = onFinish) {
            Text("FINISH")
        }
    }
}

@Composable
private fun FailedResultScreen(error: DocupassNormalizedError?, onFinish: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("VERIFICATION FAILED", color = Color(0xFFFFA3A3), fontSize = 20.sp, fontWeight = FontWeight.Black)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = error?.toDisplayMessage() ?: "The DocuPass verification did not complete successfully.",
            color = Color.White,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onFinish, colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.16f))) {
            Text("FINISH", color = Color.White)
        }
    }
}

@Composable
private fun BiometricScreen(
    actions: List<KYCAction>,
    globalSettings: KYCSettings,
    isBusy: Boolean,
    onComplete: (faceBase64List: List<String>) -> Unit
) {
    val context = LocalContext.current
    val infiniteTransition = rememberInfiniteTransition(label = "dreamy")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
        label = "pulse"
    )
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(tween(1500), RepeatMode.Reverse),
        label = "glow"
    )

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasPermission = it
    }
    LaunchedEffect(Unit) {
        if (!hasPermission) launcher.launch(Manifest.permission.CAMERA)
    }

    var actionIdx by remember { mutableIntStateOf(-1) }
    var instruction by remember { mutableStateOf("ALIGN FACE TO CIRCLE") }
    var timer by remember { mutableStateOf(0f) }
    var latestFrame by remember { mutableStateOf<Bitmap?>(null) }
    val capturedFaces = remember { mutableStateListOf<String>() }
    var completeTriggered by remember { mutableStateOf(false) }

    val aspectRatio =
        LocalConfiguration.current.screenHeightDp.toFloat() / LocalConfiguration.current.screenWidthDp.toFloat()

    val landmarker = remember {
        val options = FaceLandmarker.FaceLandmarkerOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath("face_landmarker.task").build())
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setResultListener { result, _ ->
                if (isBusy || completeTriggered) return@setResultListener

                if (actionIdx in actions.indices) {
                    processAction(
                        res = result,
                        act = actions[actionIdx],
                        actions = actions,
                        ratio = aspectRatio,
                        set = globalSettings
                    ) { nextIdx, nextInstruction, nextTimer, stepCompleted ->
                        if (stepCompleted) {
                            latestFrame?.let { capturedFaces.add(bitmapToBase64Jpeg(it)) }
                        }

                        actionIdx = nextIdx
                        instruction = nextInstruction
                        timer = nextTimer

                        if (nextIdx == actions.size && !completeTriggered) {
                            completeTriggered = true
                            onComplete(capturedFaces.toList())
                        }
                    }
                } else if (actionIdx == -1) {
                    checkAlignment(result, aspectRatio, globalSettings) { aligned ->
                        instruction = if (aligned) "READY TO SCAN" else "ALIGN FACE TO CIRCLE"
                    }
                }
            }
            .build()
        FaceLandmarker.createFromOptions(context, options)
    }

    DisposableEffect(landmarker) {
        onDispose {
            landmarker.close()
        }
    }

    if (hasPermission) {
        Box(modifier = Modifier.fillMaxSize()) {
            CameraPreview(
                cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA,
                mirrorX = true
            ) { frame ->
                latestFrame = frame
                if (!isBusy && !completeTriggered) {
                    landmarker.detectAsync(BitmapImageBuilder(frame).build(), System.currentTimeMillis())
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        val cx = size.width * 0.5f
                        val cy = size.height * globalSettings.maskCircleY
                        val r = size.width * globalSettings.maskCircleRadius
                        drawContent()
                        drawRect(Color.Black.copy(alpha = 0.7f))
                        drawCircle(Color.Transparent, r * 1.02f, Offset(cx, cy), blendMode = BlendMode.Clear)

                        val isWarn = instruction.contains("KEEP") || instruction.contains("ALIGN")
                        val ringColor = if (isWarn) Color(0xFFFF1E56) else Color(0xFF00FFAB)
                        for (i in 1..3) {
                            drawCircle(
                                ringColor.copy(alpha = glowAlpha / (i * 2)),
                                r * pulseScale + (i * 8).dp.toPx(),
                                Offset(cx, cy),
                                style = Stroke((4 - i) * 2.dp.toPx())
                            )
                        }
                        drawCircle(
                            ringColor.copy(alpha = 0.3f),
                            r * pulseScale,
                            Offset(cx, cy),
                            style = Stroke(4.dp.toPx())
                        )

                        if (actionIdx in actions.indices && !isWarn) {
                            drawArc(
                                color = Color(0xFF00FFAB),
                                startAngle = -90f,
                                sweepAngle = (timer / globalSettings.turnTimeSeconds) * 360f,
                                useCenter = false,
                                topLeft = Offset(cx - r * pulseScale, cy - r * pulseScale),
                                size = androidx.compose.ui.geometry.Size(
                                    r * 2 * pulseScale,
                                    r * 2 * pulseScale
                                ),
                                style = Stroke(6.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }
                    }
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 32.dp, vertical = 64.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
            ) {
                Card(colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.4f))) {
                    Text(
                        instruction,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Captured faces: ${capturedFaces.size}",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(20.dp))
                if (actionIdx == -1) {
                    Button(
                        onClick = {
                            if (actions.isEmpty()) {
                                val frame = latestFrame
                                if (frame != null) {
                                    onComplete(listOf(bitmapToBase64Jpeg(frame)))
                                }
                            } else {
                                actionIdx = 0
                                instruction = actions[0].instruction
                            }
                        },
                        enabled = !isBusy
                    ) {
                        Text("INITIATE SCAN")
                    }
                }
            }
        }
    }
}

private var stepStartTime = 0L

private fun checkAlignment(
    res: FaceLandmarkerResult,
    ratio: Float,
    set: KYCSettings,
    onRes: (Boolean) -> Unit
) {
    if (res.faceLandmarks().isEmpty()) {
        onRes(false)
        return
    }
    val landmarks = res.faceLandmarks()[0]
    fun isInCircle(point: com.google.mediapipe.tasks.components.containers.NormalizedLandmark): Boolean {
        return sqrt((point.x() - 0.5f).pow(2) + ((point.y() - set.maskCircleY) * ratio).pow(2)) < set.maskCircleRadius
    }
    onRes(
        isInCircle(landmarks[10]) &&
            isInCircle(landmarks[152]) &&
            isInCircle(landmarks[234]) &&
            isInCircle(landmarks[454])
    )
}

private fun processAction(
    res: FaceLandmarkerResult,
    act: KYCAction,
    actions: List<KYCAction>,
    ratio: Float,
    set: KYCSettings,
    onUpd: (nextIdx: Int, nextInstruction: String, nextTimer: Float, stepCompleted: Boolean) -> Unit
) {
    if (res.faceLandmarks().isEmpty()) {
        stepStartTime = 0L
        return
    }
    val landmarks = res.faceLandmarks()[0]

    fun isInCircle(point: com.google.mediapipe.tasks.components.containers.NormalizedLandmark): Boolean {
        return sqrt((point.x() - 0.5f).pow(2) + ((point.y() - set.maskCircleY) * ratio).pow(2)) < set.maskCircleRadius
    }

    if (!isInCircle(landmarks[10]) || !isInCircle(landmarks[152]) || !isInCircle(landmarks[234]) || !isInCircle(landmarks[454])) {
        stepStartTime = 0L
        onUpd(actions.indexOf(act), "KEEP FACE INSIDE", 0f, false)
        return
    }

    val minX = landmarks.minOf { it.x() }
    val maxX = landmarks.maxOf { it.x() }
    val faceW = maxX - minX
    val minY = landmarks.minOf { it.y() }
    val maxY = landmarks.maxOf { it.y() }
    val faceH = maxY - minY

    val noseX = (landmarks[1].x() - minX) / faceW
    val noseY = (landmarks[1].y() - minY) / faceH
    val triggered = when (act) {
        KYCAction.TURN_LEFT -> noseX < 0.38f
        KYCAction.TURN_RIGHT -> noseX > 0.62f
        KYCAction.TURN_UP -> noseY < 0.42f
        KYCAction.MOUTH_OPEN -> (abs(landmarks[14].y() - landmarks[13].y()) / faceH) > 0.12f
    }

    if (!triggered) {
        stepStartTime = 0L
        onUpd(actions.indexOf(act), act.instruction, 0f, false)
        return
    }

    if (stepStartTime == 0L) stepStartTime = System.currentTimeMillis()
    val elapsed = (System.currentTimeMillis() - stepStartTime) / 1000f
    if (elapsed >= set.turnTimeSeconds) {
        stepStartTime = 0L
        val nextIdx = actions.indexOf(act) + 1
        val nextInstruction = if (nextIdx < actions.size) actions[nextIdx].instruction else "VERIFIED"
        onUpd(nextIdx, nextInstruction, 0f, true)
    } else {
        onUpd(actions.indexOf(act), "HOLDING...", elapsed, false)
    }
}

@Composable
private fun CameraPreview(
    cameraSelector: CameraSelector,
    mirrorX: Boolean,
    onCap: (Bitmap) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val analyzerExecutor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        onDispose {
            analyzerExecutor.shutdown()
        }
    }

    LaunchedEffect(cameraSelector, mirrorX, lifecycleOwner) {
        cameraProviderFuture.addListener(
            {
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                val analyzer = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()

                analyzer.setAnalyzer(analyzerExecutor) { image ->
                    try {
                        val buffer = image.planes[0].buffer
                        val bytes = ByteArray(buffer.remaining())
                        buffer.get(bytes)
                        val bitmap = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888).apply {
                            copyPixelsFromBuffer(ByteBuffer.wrap(bytes))
                        }

                        val matrix = Matrix().apply {
                            postRotate(image.imageInfo.rotationDegrees.toFloat())
                            if (mirrorX) postScale(-1f, 1f)
                        }

                        val adjustedBitmap = Bitmap.createBitmap(
                            bitmap,
                            0,
                            0,
                            bitmap.width,
                            bitmap.height,
                            matrix,
                            true
                        )
                        onCap(adjustedBitmap)
                    } catch (e: Exception) {
                        Log.e("CameraPreview", "Analyzer frame failure", e)
                    } finally {
                        image.close()
                    }
                }

                fun bind(selector: CameraSelector) {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview, analyzer)
                }

                try {
                    bind(cameraSelector)
                } catch (primaryError: Exception) {
                    val fallback =
                        if (cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA) CameraSelector.DEFAULT_BACK_CAMERA
                        else CameraSelector.DEFAULT_FRONT_CAMERA
                    try {
                        bind(fallback)
                    } catch (secondaryError: Exception) {
                        Log.e("CameraPreview", "Unable to bind camera use cases", secondaryError)
                    }
                    Log.w("CameraPreview", "Primary camera unavailable, switched camera", primaryError)
                }
            },
            ContextCompat.getMainExecutor(context)
        )
    }

    AndroidView(
        factory = { previewView },
        modifier = Modifier.fillMaxSize()
    )
}

private data class CustomFieldOption(val label: String, val value: String)

private fun parseCustomFieldOptions(raw: String): List<CustomFieldOption> {
    return raw
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .map { line ->
            val parts = when {
                line.contains(";") -> line.split(";", limit = 2)
                line.contains("\t") -> line.split("\t", limit = 2)
                line.contains("|") -> line.split("|", limit = 2)
                else -> listOf(line, line)
            }
            CustomFieldOption(
                label = parts.getOrNull(0)?.trim().orEmpty(),
                value = parts.getOrNull(1)?.trim().orEmpty().ifBlank { parts.getOrNull(0)?.trim().orEmpty() }
            )
        }
        .toList()
}

private fun cleanupContractHtml(contractSource: String): String {
    val cleaned = contractSource.replace(Regex("""%\{[0-9A-Za-z_.\-]+\}"""), "")
    return if (cleaned.contains("<html", ignoreCase = true)) {
        cleaned
    } else {
        "<html><body>$cleaned</body></html>"
    }
}

private fun createHandwrittenSignatureDataUrl(strokes: List<List<Offset>>, size: IntSize): String? {
    val drawableStrokes = strokes.filter { it.isNotEmpty() }
    if (drawableStrokes.isEmpty() || size.width <= 0 || size.height <= 0) return null

    val bitmap = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)
    canvas.drawColor(android.graphics.Color.WHITE)
    val strokeWidth = (size.height * 0.025f).coerceIn(6f, 16f)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.BLACK
        style = Paint.Style.STROKE
        this.strokeWidth = strokeWidth
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    val dotPaint = Paint(paint).apply {
        style = Paint.Style.FILL
    }

    drawableStrokes.forEach { stroke ->
        when (stroke.size) {
            1 -> canvas.drawCircle(stroke.first().x, stroke.first().y, strokeWidth / 2f, dotPaint)
            else -> stroke.zipWithNext().forEach { (start, end) ->
                canvas.drawLine(start.x, start.y, end.x, end.y, paint)
            }
        }
    }

    val output = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
    return "data:image/png;base64,${Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)}"
}
