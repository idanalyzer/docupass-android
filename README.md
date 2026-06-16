# DocuPass Android SDK

Native Android SDK for running an ID Analyzer DocuPass verification flow inside
your app. The SDK includes:

- A ready-to-use Jetpack Compose UI through `KYCScreen`
- An event-driven API for building your own UI
- Native document capture with CameraX
- Active face verification with MediaPipe
- Phone, custom form, document, face, contract, and pending-party flow handling

The mobile app only needs a short-lived DocuPass `reference`. Your API key must
stay on your backend.

## Installation

Add the SDK from Maven Central:

```kotlin
dependencies {
    implementation("com.idanalyzer:docupass:0.1.4")
}
```

The SDK requires Android 7.0 or newer.

```kotlin
android {
    defaultConfig {
        minSdk = 24
    }
}
```

The SDK manifest declares `INTERNET`, `CAMERA`, and optional location
permissions. Camera permission is requested by the Quick UI when the verification
flow needs capture. If your DocuPass profile requires GPS, obtain location in
your app and pass it as `geolocation = "lat,lng,accuracy"` when starting
`KYCScreen` or creating an event session.

## Create a Reference

Create a DocuPass session on your server, then pass the returned `reference` to
your Android app. Do not create sessions from the mobile app, and do not put your
ID Analyzer API key in the APK.

Example server-side flow:

1. Your backend calls ID Analyzer to create a DocuPass session.
2. Your backend sends the returned `reference` to your Android app.
3. The Android app runs the SDK with that reference.
4. Your backend receives the final verification result through webhook or a
   server-side result lookup.

The SDK's finish callback is a UI signal. Your backend remains the source of
truth for the final verification decision and identity data.

## Quick UI

Use `KYCScreen` when you want the SDK to render the complete verification flow.

```kotlin
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.idanalyzer.docupass.KYCScreen
import com.idanalyzer.docupass.KYCResult

class VerifyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)

        val reference = intent.getStringExtra("docupass_reference").orEmpty()

        setContent {
            KYCScreen(
                reference = reference,
                onFinish = { result: KYCResult ->
                    // Called when the user taps FINISH on the final screen.
                    finish()
                },
                onBackAtFirstStep = {
                    finish()
                },
            )
        }
    }
}
```

`KYCScreen` handles:

- Loading the server-driven DocuPass task
- Document country and type selection
- Document capture and upload
- Face verification with randomized actions
- Phone verification
- Custom form submission
- Contract review and signature submission
- Back navigation between non-terminal steps
- Final success or failure screen

`onFinish` is called only after the user taps the final `FINISH` button. It is
not called immediately when the server reaches a terminal state.

## Event API

Use the event API when you want to build your own UI. The SDK owns the DocuPass
state machine and API calls; your app renders screens and provides captured data.

```kotlin
import com.idanalyzer.docupass.DocupassConfigFactory
import com.idanalyzer.docupass.DocupassKycEventKind
import com.idanalyzer.docupass.DocupassKycListener
import com.idanalyzer.docupass.DocupassKycNativeState
import com.idanalyzer.docupass.DocupassKycSession

val session = DocupassKycSession(
    DocupassConfigFactory.fromReference(reference)
)

val subscription = session.subscribe(object : DocupassKycListener {
    override fun onStateChanged(state: DocupassKycNativeState) {
        when (state.event) {
            DocupassKycEventKind.LOADING -> {
                // Show loading UI.
            }

            DocupassKycEventKind.DOCUMENT_COUNTRY_SELECTION -> {
                val countries = state.documentCountrySelection?.countries.orEmpty()
                // Render country choices, then call:
                // session.selectDocumentCountry(country.code)
            }

            DocupassKycEventKind.DOCUMENT_SELECTION -> {
                val documentTypes = state.documentSelection?.documentTypes.orEmpty()
                // Render document type choices, then call:
                // session.selectDocumentType(documentType.apiTypeCode)
            }

            DocupassKycEventKind.DOCUMENT_CAPTURE -> {
                // Capture or select images in your UI, convert them to base64 JPEG,
                // then call:
                // session.uploadDocument(frontBase64, backBase64)
            }

            DocupassKycEventKind.FACE_VERIFICATION -> {
                val actions = state.face?.actions.orEmpty()
                // Run your own face UI, then call:
                // session.uploadFace(faceBase64List)
            }

            DocupassKycEventKind.PHONE_VERIFICATION -> {
                // Send an OTP:
                // session.sendPhoneCode(number, "sms")
                // Verify an OTP:
                // session.verifyPhoneCode(number, code)
            }

            DocupassKycEventKind.CUSTOM_FORM -> {
                // Submit answers keyed by fieldId:
                // session.saveCustomForm(answers)
            }

            DocupassKycEventKind.CONTRACT -> {
                // Submit signatures keyed by signature field uid:
                // session.submitContract(signatures)
            }

            DocupassKycEventKind.PARTY_PENDING -> {
                // Let the user wait, then call:
                // session.refresh()
            }

            DocupassKycEventKind.COMPLETED,
            DocupassKycEventKind.FAILED -> {
                // Show your final UI. Fetch authoritative results on your backend.
            }
        }
    }
})

session.start()

// Close when your screen is destroyed.
subscription.close()
session.close()
```

The event API intentionally does not own your camera UI. For custom UI
integrations, your app captures document and face images and submits base64 data
through the session methods.

## Back Navigation

`KYCScreen` handles Android back presses. Non-terminal steps go back to the
previous SDK step when possible. If the user is already on the first step,
`onBackAtFirstStep` is called so your app can close the screen.

Final success and failure screens are terminal. Back presses do not leave those
screens; the user must tap `FINISH`.

For custom UI, observe `state` and route your own back affordance to the session
methods you expose in your screen.

## Results

`KYCResult` contains local flow details such as selected document country/type,
uploaded image base64 values, the current session state, and terminal error
information. It is useful for app UI decisions, but it is not the authoritative
identity verification result.

Use your backend webhook or server-side DocuPass result lookup to decide whether
the user is accepted, rejected, or under review.

## License

[MIT](LICENSE) © [ID Analyzer](https://www.idanalyzer.com)
