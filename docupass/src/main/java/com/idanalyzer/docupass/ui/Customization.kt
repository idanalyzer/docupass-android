package com.idanalyzer.docupass.ui

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Every user-facing label in the drop-in UI, with English defaults. Override any
 * subset to re-word or localize the flow to any language — e.g.
 *
 * ```
 * DocuPassView(
 *     config = DocuPassConfig(reference = "US…"),
 *     strings = DocuPassStrings(
 *         phoneTitle = "Vérifiez votre téléphone",
 *         phoneSendSms = "Envoyer le SMS",
 *     ),
 *     onResult = { … },
 * )
 * ```
 *
 * For full control of layout/look, use the headless API
 * (`DocuPassController` + `LivenessController` + `CameraController`) instead.
 */
data class DocuPassStrings(
    // Common
    val start: String = "Start",
    val continueButton: String = "Continue",
    val pleaseWaitTitle: String = "Please wait",
    val pleaseWaitBody: String = "Preparing the next step…",
    val waitingTitle: String = "Waiting",
    val waitingBody: String = "Waiting for another party to complete their part.",
    val cameraPermissionRequired: String = "Camera permission is required",

    // Welcome
    val welcomeFallback: String = "You'll be guided through a quick identity verification.",

    // Document selection
    val selectDocumentTitle: String = "Select your document",
    val countryLabel: String = "Country",
    val documentTypeLabel: String = "Document type",
    val pleaseMakeSure: String = "Please make sure:",
    val reqClear: String = "The whole document is in frame, in focus, and free of glare.",
    val reqDocumentNo: String = "The document number is clearly visible.",
    val reqName: String = "Your full name is readable.",
    val reqDob: String = "Your date of birth is readable.",
    val reqAddress: String = "Your address is readable.",
    val reqPostcode: String = "Your postcode is readable.",

    // Document capture
    val capturePassport: String = "Capture the passport data page",
    val captureFront: String = "Capture the front of your document",
    val captureBack: String = "Capture the back of your document",
    val capture: String = "Capture",
    val captureBackButton: String = "Capture back",

    // Face / liveness
    val faceLoading: String = "Loading face check…",
    val faceForward: String = "Face forward and hold still",
    val faceGreat: String = "Great — keep going",
    val faceTurnLeft: String = "Slowly turn your head to the left",
    val faceTurnRight: String = "Slowly turn your head to the right",
    val faceDone: String = "All done",
    val faceNoFace: String = "No face detected — center your face",

    // Custom form
    val customFormTitle: String = "A few more details",

    // Phone
    val phoneTitle: String = "Verify your phone",
    /** Shown as `"$phonePresetPrefix<number>"` when the number is preset. */
    val phonePresetPrefix: String = "We'll send a code to ",
    val phoneCodeLabel: String = "Code",
    val phoneNumberLabel: String = "Phone number",
    val phoneSendSms: String = "Send SMS",
    val phoneCall: String = "Call me",
    val phoneCodeEntryLabel: String = "6-digit code",
    val phoneVerify: String = "Verify",

    // Contract / e-signature
    val contractTitle: String = "Review & sign",
    val contractSignature: String = "Signature",
    val contractClear: String = "Clear",
    val contractAccept: String = "Accept & Submit",
    val contractSubmit: String = "Submit signatures",
)

/**
 * Branding for the drop-in UI.
 *
 * @property primaryColor brand color for primary buttons / progress (null = inherit
 *   the host app's `MaterialTheme`).
 * @property logoUrl a logo to show on the welcome screen (null = use the
 *   server-configured `logoURL` from the session, if any).
 * @property showLogo set false to never show a logo.
 */
data class DocuPassTheme(
    val primaryColor: Color? = null,
    val logoUrl: String? = null,
    val showLogo: Boolean = true,
)

val LocalDocuPassStrings = staticCompositionLocalOf { DocuPassStrings() }
val LocalDocuPassTheme = staticCompositionLocalOf { DocuPassTheme() }
