# DocuPass Android SDK — in-app ID verification & KYC for Android

[![Maven Central](https://img.shields.io/badge/Maven%20Central-com.idanalyzer%3Adocupass-blue)](https://central.sonatype.com/artifact/com.idanalyzer/docupass)
[![min SDK](https://img.shields.io/badge/minSdk-24-green)](#requirements)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

Embed [ID Analyzer **DocuPass**](https://www.idanalyzer.com/products/docupass.html) identity
verification **natively inside your Android app** — document scanning, face match,
and active liveness — with **no external browser and no WebView**. Drop in one
Compose component, get a result callback.

This is the native answer to "the DocuPass web link doesn't work wrapped in a
WebView" (camera/permission failures): the SDK owns the camera (CameraX) and runs
liveness on-device (MediaPipe `FaceLandmarker`), talking directly to the DocuPass
session API.

- 📱 **Native camera + liveness** — no `getUserMedia`/WebView permission issues.
- 🧩 **One-line drop-in** Compose UI, plus a **headless API** for custom UIs.
- 🔒 **No API key on the device** — the app only holds a DocuPass `reference`.
- 🌍 **US & EU** regions auto-selected from the reference.
- 🪶 Server-driven flow: document selection, capture, liveness, custom forms,
  phone verification, and e-signature contracts — all orchestrated by DocuPass.

> Sibling SDKs: [iOS](https://github.com/idanalyzer/docupass-ios) ·
> [React Native](https://github.com/idanalyzer/docupass-react-native) ·
> [Flutter](https://github.com/idanalyzer/docupass-flutter). Server-side scanning
> SDKs (scan/face/AML/DocuPass management) live under the
> [`id-analyzer-v2-*`](https://github.com/idanalyzer) repos.

## How it works

1. **Server-side**, create a DocuPass session with your API key (any
   [ID Analyzer v2 server SDK](https://developer.idanalyzer.com/help)):
   `POST /docupass` → you get a `reference`.
2. **In your app**, pass that `reference` to `DocuPassView`. The SDK runs the full
   verification on-device and returns a `DocuPassResult`.
3. **Server-side**, fetch the verified result: `GET /docupass/{reference}`.

The API key never touches the device — only the short-lived `reference` does.

## Install

```kotlin
// settings.gradle.kts -> dependencyResolutionManagement { repositories { mavenCentral() } }
dependencies {
    implementation("com.idanalyzer:docupass:0.1.0")
}
```

Add the permissions (the library manifest already declares `CAMERA` + `INTERNET`;
`ACCESS_FINE_LOCATION` is only used if your DocuPass profile enables GPS):

```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.INTERNET" />
```

## Quick start (drop-in UI)

```kotlin
import com.idanalyzer.docupass.DocuPassConfig
import com.idanalyzer.docupass.DocuPassResult
import com.idanalyzer.docupass.ui.DocuPassView

setContent {
    DocuPassView(
        config = DocuPassConfig(reference = "US…"), // from POST /docupass
        onResult = { result ->
            when (result) {
                is DocuPassResult.Completed -> { /* verified — fetch result server-side */ }
                is DocuPassResult.Failed    -> { /* rejected */ }
                is DocuPassResult.Cancelled -> { /* user dismissed */ }
                is DocuPassResult.Error     -> { /* network / fatal */ }
            }
        },
    )
}
```

That's the whole integration. The component requests camera permission, renders
every step the DocuPass session asks for, and runs liveness on-device.

## Headless usage (your own UI)

Skip the bundled UI and drive the protocol yourself:

```kotlin
val controller = DocuPassController(DocuPassConfig(reference = "US…"))
controller.state.collect { state -> /* DocuPassState.Step / Finished */ }

controller.start()
controller.submitDocumentSelection(country = "US", type = "D")
controller.submitDocument(frontBase64, backBase64)   // your capture
controller.submitFace(listOf(faceBase64))            // your liveness
```

The liveness building blocks are reusable too: `FaceLandmarkerEngine` (MediaPipe)
and `LivenessController` (the neutral → turn-left → turn-right state machine).

## Requirements

- **minSdk 24** (Android 7.0+), compileSdk 35
- Jetpack Compose (drop-in UI). The headless API has no Compose requirement at
  call sites.
- Bundled: the MediaPipe `face_landmarker.task` model and the country/document
  catalog — no extra downloads.

## Links

- DocuPass product: https://www.idanalyzer.com/products/docupass.html
- Developer docs / KB: https://developer.idanalyzer.com/help
- Customer portal: https://portal2.idanalyzer.com

## License

[MIT](LICENSE) © ID Analyzer
