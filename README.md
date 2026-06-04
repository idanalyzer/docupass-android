# DocuPass Android SDK — Native In-App ID Verification, KYC & Liveness for Android

[![Maven Central](https://img.shields.io/maven-central/v/com.idanalyzer/docupass)](https://central.sonatype.com/artifact/com.idanalyzer/docupass)
[![min SDK 24](https://img.shields.io/badge/minSdk-24-green)](#requirements)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![ID Analyzer](https://img.shields.io/badge/by-ID%20Analyzer-0b5cff)](https://www.idanalyzer.com)

Add **identity verification and KYC** to your Android app in minutes. The DocuPass
Android SDK runs the entire flow **natively, on-device** — ID document scanning,
biometric **face match**, and **active liveness detection** — with **no external
browser and no WebView**. Drop in one Jetpack Compose component, get a result
callback.

Built by **[ID Analyzer](https://www.idanalyzer.com)** — the identity verification
platform trusted for [ID document recognition](https://www.idanalyzer.com/products/id-scanner-api.html),
[biometric verification](https://www.idanalyzer.com/products/biometric-verification.html),
and [AML screening](https://www.idanalyzer.com/products/aml-api.html) across 190+
countries and 14,000+ document types.

> **Why native instead of a WebView?** Wrapping the DocuPass web link
> (`v.idanalyzer.com`) in a `WebView` breaks the camera (`getUserMedia` permission
> failures, blocked liveness). This SDK owns the camera with **CameraX** and runs
> liveness on-device with **Google MediaPipe**, so verification just works inside
> your app.

**📚 Full documentation:** [developer.idanalyzer.com/help/docupass-android-sdk](https://developer.idanalyzer.com/help/docupass-android-sdk)
· **🌐 Product:** [DocuPass](https://www.idanalyzer.com/products/docupass.html)
· **📦 Other platforms:** [iOS](https://github.com/idanalyzer/docupass-ios) ·
[React Native](https://github.com/idanalyzer/docupass-react-native) ·
[Flutter](https://github.com/idanalyzer/docupass-flutter)

---

## Features

- 📱 **Fully native capture** — CameraX document & selfie capture; no WebView, no `getUserMedia` issues.
- 🧠 **On-device active liveness** — MediaPipe face landmarks; the user holds still, then turns left/right.
- 🪪 **Global document support** — passports, driver licenses, and ID cards from 190+ countries.
- ✍️ **Full DocuPass flow** — document selection & capture, face match, custom forms, phone (SMS/voice OTP) verification, and **e-signature contracts**.
- 🎨 **White-label** — override every label (any language) and theme the brand color & logo. One-line drop-in *or* fully headless.
- 🔒 **Your API key never touches the device** — the app only holds a short-lived `reference`.
- 🌍 **US & EU data regions** — selected automatically from the reference.

## How it works

DocuPass is server-driven, so your **API key stays on your backend** and the device
only ever holds a short-lived verification `reference`:

1. **Server → create a session.** Call `POST /docupass` with your API key (use any
   [ID Analyzer server SDK](https://developer.idanalyzer.com/help) — Node, Python,
   PHP, .NET, Java, Go). You get back a **`reference`**.
2. **App → run the SDK.** Pass that `reference` to `DocuPassView`. The SDK guides the
   user through capture + liveness on-device and returns a `DocuPassResult`.
3. **Server → fetch the result.** Call `GET /docupass/{reference}` with your API key
   to read the verified identity data and decision.

## Requirements

- **Android 7.0+ (minSdk 24)**, compileSdk 35
- Jetpack Compose (for the drop-in UI; the headless API has no Compose requirement)
- The MediaPipe liveness model and the country/document catalog are **bundled** — no extra downloads.

## Installation

`com.idanalyzer:docupass` is published on **Maven Central**.

```kotlin
// build.gradle.kts (app module)
dependencies {
    implementation("com.idanalyzer:docupass:0.1.1")
}
```

```groovy
// build.gradle (Groovy)
dependencies {
    implementation 'com.idanalyzer:docupass:0.1.1'
}
```

The library manifest already declares the `CAMERA` and `INTERNET` permissions
(`ACCESS_FINE_LOCATION` is only requested at runtime if your DocuPass profile
enables GPS) — nothing to add.

## Quick start (drop-in UI)

This is the entire integration. `DocuPassView` requests camera permission, renders
every step the session asks for, runs liveness, and calls you back with the outcome:

```kotlin
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.idanalyzer.docupass.DocuPassConfig
import com.idanalyzer.docupass.DocuPassResult
import com.idanalyzer.docupass.ui.DocuPassView

class VerifyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DocuPassView(
                config = DocuPassConfig(reference = "US...your-reference..."),
                onResult = { result ->
                    when (result) {
                        is DocuPassResult.Completed -> {
                            // Verified. Fetch the data server-side: GET /docupass/{reference}
                        }
                        is DocuPassResult.Failed    -> { /* rejected */ }
                        is DocuPassResult.Cancelled -> { /* user dismissed */ }
                        is DocuPassResult.Error     -> { /* network / fatal error */ }
                    }
                },
            )
        }
    }
}
```

### Getting a `reference` (server side)

Create the DocuPass session on your backend, never in the app. Example with the
official Node.js server SDK:

```javascript
import { DocuPass } from "idanalyzer2";

const docupass = new DocuPass("YOUR_API_KEY", "YOUR_PROFILE_ID", "US");
const session = await docupass.createDocuPass();
// Send session.reference down to your app and pass it to DocuPassView.
```

See the [server SDK docs](https://developer.idanalyzer.com/help) for Python, PHP,
.NET, Java, and Go.

## Customization — labels, languages & branding

Both customization points are optional parameters on `DocuPassView`, so one-line
usage stays unchanged.

### Re-label or translate to any language

`DocuPassStrings` exposes **every** user-facing label as an overridable field
(English by default). Override any subset to re-word the copy or localize to any
language — you supply the translations, so you're never limited to a fixed set:

```kotlin
import com.idanalyzer.docupass.ui.DocuPassStrings

DocuPassView(
    config = DocuPassConfig(reference = reference),
    strings = DocuPassStrings(
        selectDocumentTitle = "Sélectionnez votre document",
        phoneTitle = "Vérifiez votre téléphone",
        phoneSendSms = "Envoyer le SMS",
        faceForward = "Regardez droit devant et ne bougez pas",
        faceTurnLeft = "Tournez lentement la tête vers la gauche",
    ),
    onResult = { /* ... */ },
)
```

### Brand color & logo

`DocuPassTheme` applies your brand color to the primary controls and shows a logo
on the welcome screen (defaults to the logo configured on your DocuPass profile):

```kotlin
import androidx.compose.ui.graphics.Color
import com.idanalyzer.docupass.ui.DocuPassTheme

DocuPassView(
    config = DocuPassConfig(reference = reference),
    theme = DocuPassTheme(
        primaryColor = Color(0xFF1565C0),
        logoUrl = "https://yourbrand.example.com/logo.png",
    ),
    onResult = { /* ... */ },
)
```

## Headless API (build your own UI)

For complete control over layout and look, skip `DocuPassView` and drive the
protocol yourself. Everything is public: `DocuPassController` (the state machine),
`DocuPassClient` (the 9 protocol endpoints), `LivenessController` + `FaceLandmarkerEngine`
(the liveness pipeline), and `CameraController`.

```kotlin
import com.idanalyzer.docupass.session.DocuPassController
import com.idanalyzer.docupass.session.DocuPassState

val controller = DocuPassController(DocuPassConfig(reference = reference))

lifecycleScope.launch {
    controller.state.collect { state ->
        when (state) {
            is DocuPassState.Step -> {
                // state.session.parsedTask tells you which screen to show:
                // DOCUMENT, FACE, CUSTOM_FORM, PHONE, CONTRACT, PARTY_PENDING
            }
            is DocuPassState.Finished -> { /* state.result */ }
            else -> Unit
        }
    }
}

controller.start()
controller.submitDocumentSelection(country = "US", type = "D") // D = driver license
controller.submitDocument(frontBase64, backBase64)             // your own capture
controller.submitFace(listOf(faceBase64))                      // your own liveness
```

## Handling the result

`DocuPassResult` is a sealed interface:

| Result | Meaning |
|---|---|
| `Completed(reference, redirectUrl?, code?)` | Verification finished (accepted / under review). Fetch the data with `GET /docupass/{reference}`. |
| `Failed(reference, code?, message?, redirectUrl?)` | Rejected or failed. |
| `Cancelled(reference)` | The user dismissed the flow. |
| `Error(reference, error)` | Network or fatal session error. |

The verification **data and decision live server-side** — always fetch them with
your API key via `GET /docupass/{reference}`; never trust a client result alone.

## Links

- 🌐 ID Analyzer: [www.idanalyzer.com](https://www.idanalyzer.com)
- 🪪 DocuPass product: [idanalyzer.com/products/docupass.html](https://www.idanalyzer.com/products/docupass.html)
- 📚 Developer docs & KB: [developer.idanalyzer.com/help](https://developer.idanalyzer.com/help)
- 📱 This SDK's guide: [developer.idanalyzer.com/help/docupass-android-sdk](https://developer.idanalyzer.com/help/docupass-android-sdk)
- 🔑 Get API keys / customer portal: [portal2.idanalyzer.com](https://portal2.idanalyzer.com)
- 🧩 Other SDKs: [iOS](https://github.com/idanalyzer/docupass-ios) · [React Native](https://github.com/idanalyzer/docupass-react-native) · [Flutter](https://github.com/idanalyzer/docupass-flutter)

## License

[MIT](LICENSE) © [ID Analyzer](https://www.idanalyzer.com)
