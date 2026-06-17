# DocuPass Android SDK

Native Android SDK for running an ID Analyzer DocuPass verification flow inside
your app. The SDK includes:

- A ready-to-use Jetpack Compose Quick UI through `KYCScreen`
- An event-driven session API for building your own UI
- Native document capture with CameraX in the Quick UI
- Active face verification with MediaPipe in the Quick UI
- Phone, custom form, document, face, contract, and pending-party flow handling

The mobile app only needs a short-lived DocuPass `reference`. Your API key must
stay on your backend.

## Installation

Add the SDK from Maven Central:

```kotlin
dependencies {
    implementation("com.idanalyzer:docupass:0.1.6")
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

Quick UI arguments:

| Argument | Type | When to use |
| --- | --- | --- |
| `reference` | `String` | Required. The short-lived DocuPass reference created by your backend. |
| `partyId` | `String?` | Optional. Use for a specific party in a multi-party signing flow. |
| `geolocation` | `String?` | Optional. Use `"lat,lng,accuracy"` when the DocuPass profile requires GPS. |
| `onFinish` | `(KYCResult) -> Unit` | Called when the user taps `FINISH` on success or failure. |
| `onBackAtFirstStep` | `() -> Unit` | Called when Android back or the SDK back button is pressed on the first non-terminal step. |

## Event API

Use the event API when you want to build your own UI. The SDK owns the DocuPass
state machine and API calls; your app renders screens and provides captured data.
The event API does not own camera, liveness UI, WebView, or signature drawing.

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
        if (state.errorMessage != null) {
            // Show an error message, then call session.clearError().
        }

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
                // Capture or select images in your UI, convert them to raw base64 JPEG,
                // then call:
                // session.uploadDocument(frontBase64, backBase64)
            }

            DocupassKycEventKind.FACE_VERIFICATION -> {
                val actions = state.face?.actions.orEmpty()
                // Run your own liveness UI, then call:
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

### Session Lifecycle

Create one `DocupassKycSession` for one verification screen or custom UI flow.
Subscribe before `start()` so your UI receives every state update.

```kotlin
val config = DocupassConfigFactory.fromReference(
    reference = reference,
    partyId = null,
    geolocation = null,
)

val session = DocupassKycSession(config)
val subscription = session.subscribe(listener)

session.start()
```

Call `subscription.close()` and `session.close()` when your Activity, Fragment,
ViewModel, or Compose screen is destroyed.

### Configuration

Most apps should create config with `DocupassConfigFactory.fromReference(...)` or
`docupassConfigFromReference(...)`.

| API | Use |
| --- | --- |
| `DocupassConfigFactory.fromReference(reference, partyId, geolocation, enabled)` | Recommended config factory for normal app usage. |
| `docupassConfigFromReference(reference, partyId, geolocation, enabled)` | Top-level helper equivalent to `DocupassConfigFactory.fromReference`. |
| `DocupassApiConfig(...)` | Advanced/manual config when you need to override endpoint, authorization, or timeouts. |

`DocupassApiConfig` fields:

| Field | Default | Meaning |
| --- | --- | --- |
| `enabled` | `true` | `true` calls the DocuPass API. `false` runs local fallback workflow only; use for demos/tests. |
| `baseUrl` | Resolved from reference | Optional API base URL override. References starting with `EU` use the EU endpoint; others use the US endpoint. |
| `reference` | `null` | DocuPass reference created by your backend. |
| `partyId` | `null` | Optional party id for multi-party signing. |
| `sessionId` | `null` | Optional existing DocuPass session id. Normally discovered from the API response. |
| `authorization` | `null` | Optional full `Authorization` header value. If set, it overrides generated DocuPass auth. |
| `geolocation` | `null` | Optional `"lat,lng,accuracy"` value sent as the `Geolocation` header. |
| `disableSslValidation` | `false` | Present for API parity. The Android transport ignores this and keeps SSL validation enabled. |
| `connectTimeoutMs` | `20000` | OkHttp connect timeout in milliseconds. |
| `readTimeoutMs` | `20000` | OkHttp read/write timeout in milliseconds. |

Authorization is generated automatically when `authorization` is not set:

```text
DOCUPASS <reference>
DOCUPASS <reference> <partyId>
DOCUPASS_SESSION <sessionId>
```

### Session Methods

| Method | Call when | Parameters |
| --- | --- | --- |
| `subscribe(listener)` | Before `start()`. Also useful after UI recreation. | `listener` receives `DocupassKycNativeState`. Returns `DocupassSubscription`; call `close()`. |
| `currentState()` | When the UI needs a synchronous snapshot. | No parameters. |
| `start()` | After subscribing, when the screen should begin loading the DocuPass task. | No parameters. |
| `refresh()` | On `PARTY_PENDING`, or when you want to resync with the server. | No parameters. Calls `get_action`. |
| `back()` | When the user taps your back button and `state.canGoBack && !state.isBusy`. | No parameters. Does nothing on first step, busy state, or terminal screens. |
| `clearError()` | After your UI dismisses `state.errorMessage`. | No parameters. |
| `restart()` | When you intentionally want to reset local SDK state and start again. | No parameters. |
| `sendPhoneCode(number, type)` | On `PHONE_VERIFICATION`, before the OTP is entered. | `number`: `String?`; `type`: `"sms"` or `"call"`. |
| `verifyPhoneCode(number, code)` | On `PHONE_VERIFICATION`, after the user enters the OTP. | `number`: same number used for sending, or `null` for a preset server phone; `code`: OTP string. |
| `saveCustomForm(answers)` | On `CUSTOM_FORM`, after all required fields are filled. | `answers`: `Map<String, String>` keyed by `field.fieldId` or fallback label. |
| `selectDocumentCountry(countryCode)` | On `DOCUMENT_COUNTRY_SELECTION`, when the user chooses a country. | `countryCode`: ISO-2 code from `state.documentCountrySelection.countries[].code`. |
| `selectDocumentType(documentTypeCode)` | On `DOCUMENT_SELECTION`, when the user chooses a document type. | `documentTypeCode`: from `state.documentSelection.documentTypes[].apiTypeCode`. |
| `uploadDocument(frontBase64, backBase64)` | On `DOCUMENT_CAPTURE`, after your UI captures the required side(s). | Raw JPEG base64 without a `data:image/...` prefix. `backBase64` can be `null` for front-only documents. |
| `uploadFace(faceBase64List)` | On `FACE_VERIFICATION`, after your liveness UI captures face frames. | Non-empty list of raw JPEG base64 strings without a `data:image/...` prefix. |
| `submitContract(signatures)` | On `CONTRACT`, after the user signs every required field. | `Map<signatureField.uid, image>`. Signature images should be `data:image/png;base64,...`. |
| `close()` | When the UI owner is destroyed. | No parameters. Cancels work and closes HTTP resources. |

Do not call step-specific methods before the matching event is emitted. If
`state.isBusy` is `true`, keep your UI controls disabled until a new state is
emitted.

### State Model

Every listener update receives `DocupassKycNativeState`.

| Field | Meaning |
| --- | --- |
| `event` | Current flow step as `DocupassKycEventKind`. |
| `isBusy` | `true` while the SDK is making an API call or processing a submitted action. |
| `canGoBack` | `true` when a custom UI can call `session.back()`. Terminal screens always return `false`. |
| `errorMessage` | User-displayable error message. It is independent from `event`. |
| `normalizedError` | Structured error details when the SDK can classify the DocuPass error. |
| `result` | Local SDK flow result collected so far. This is not the authoritative verification decision. |
| `phone` | Payload for `PHONE_VERIFICATION`; otherwise `null`. |
| `customForm` | Payload for `CUSTOM_FORM`; otherwise `null`. |
| `documentCountrySelection` | Payload for `DOCUMENT_COUNTRY_SELECTION`; otherwise `null`. |
| `documentSelection` | Payload for `DOCUMENT_SELECTION`; otherwise `null`. |
| `documentCapture` | Payload for `DOCUMENT_CAPTURE`; otherwise `null`. |
| `face` | Payload for `FACE_VERIFICATION`; otherwise `null`. |
| `contract` | Payload for `CONTRACT`; otherwise `null`. |
| `completed` | Payload for `COMPLETED`; otherwise `null`. |
| `failed` | Payload for `FAILED`; otherwise `null`. |

Events and payloads:

| Event | Payload | What your UI should do |
| --- | --- | --- |
| `LOADING` | None | Show loading UI. |
| `PHONE_VERIFICATION` | `state.phone` | Show phone entry/OTP UI. Use `sendPhoneCode` and `verifyPhoneCode`. |
| `CUSTOM_FORM` | `state.customForm` | Render fields and submit with `saveCustomForm`. |
| `DOCUMENT_COUNTRY_SELECTION` | `state.documentCountrySelection` | Render `countries` and call `selectDocumentCountry`. |
| `DOCUMENT_SELECTION` | `state.documentSelection` | Render `documentTypes` and call `selectDocumentType`. |
| `DOCUMENT_CAPTURE` | `state.documentCapture` | Capture document image(s) and call `uploadDocument`. |
| `FACE_VERIFICATION` | `state.face` | Run liveness using `actions` and call `uploadFace`. |
| `CONTRACT` | `state.contract` | Render HTML, collect signatures, and call `submitContract`. |
| `PARTY_PENDING` | None | Show pending UI and call `refresh()` when the user retries/checks status. |
| `COMPLETED` | `state.completed` | Show final success UI. |
| `FAILED` | `state.failed` | Show final failure UI. |

## Parameter Reference

### Document Country

`session.selectDocumentCountry(countryCode)` accepts a single `String`.

Use the values emitted by the current state:

```kotlin
val countries = state.documentCountrySelection?.countries.orEmpty()
session.selectDocumentCountry(countries.first().code)
```

`countryCode` is an ISO 3166-1 alpha-2 country code such as `US`, `TW`, or `JP`.
The server may restrict available countries through the DocuPass profile. The SDK
preserves unknown server country codes as `KYCCountry(code = code, name = code)`.

The built-in known country list currently contains:

| Code | Name |
| --- | --- |
| `AU` | Australia |
| `CA` | Canada |
| `DE` | Germany |
| `FR` | France |
| `GB` | United Kingdom |
| `HK` | Hong Kong |
| `JP` | Japan |
| `KR` | South Korea |
| `SG` | Singapore |
| `TH` | Thailand |
| `TW` | Taiwan |
| `US` | United States |

### Document Type

`session.selectDocumentType(documentTypeCode)` accepts one of the document type
codes emitted by `state.documentSelection.documentTypes`. Use that payload instead
of hardcoding the full list, because the server can restrict available document
types.

| Enum | `apiTypeCode` | Label | Default back side requirement |
| --- | --- | --- | --- |
| `KYCDocumentType.PASSPORT` | `P` | `Passport` | No |
| `KYCDocumentType.DRIVER_LICENSE` | `D` | `Driver License` | Yes |
| `KYCDocumentType.IDENTITY_CARD` | `I` | `Identity Card` | Yes |

Server `documentSide` overrides the local default:

| `documentSide` | Meaning |
| --- | --- |
| `1` | Front only. |
| `2` | Front and back may be required. Passport remains front-only in the Quick UI. |
| `0` or `null` | Use the local document type default. |

For custom UI, check both `state.documentCapture.documentSide` and
`state.documentCapture.documentType?.requiresBackSide` before deciding whether to
ask for a back image.

### Document Images

`session.uploadDocument(frontBase64, backBase64)` expects:

| Parameter | Value |
| --- | --- |
| `frontBase64` | Required raw JPEG base64 for the front image. Do not include a `data:image/...` prefix. |
| `backBase64` | Raw JPEG base64 for the back image, or `null` when the document is front-only. |

### Face Verification

`state.face?.actions` tells your custom UI which liveness actions to perform.
The SDK randomizes face actions and requires at least two actions when candidates
are available.

| Enum | Instruction |
| --- | --- |
| `KYCAction.TURN_LEFT` | `TURN HEAD LEFT` |
| `KYCAction.TURN_RIGHT` | `TURN HEAD RIGHT` |
| `KYCAction.TURN_UP` | `TURN HEAD UP` |
| `KYCAction.MOUTH_OPEN` | `OPEN MOUTH O-SHAPE` |

`session.uploadFace(faceBase64List)` expects a non-empty list of raw JPEG base64
strings without a `data:image/...` prefix.

### Phone Verification

`state.phone` contains:

| Field | Meaning |
| --- | --- |
| `state` | Full `DocupassSessionState`, including preset phone and country code options. |
| `codeSent` | `true` after `sendPhoneCode` succeeds. |
| `currentNumber` | The number used by the last send request, if provided by your UI. |

`session.sendPhoneCode(number, type)` parameters:

| Parameter | Value |
| --- | --- |
| `number` | `null` if the server already has `state.phone.state.userPhone`; otherwise send the phone number your UI collected, preferably in international format such as `+15551234567`. |
| `type` | `"sms"` or `"call"`. |

`session.verifyPhoneCode(number, code)` parameters:

| Parameter | Value |
| --- | --- |
| `number` | Same number used for `sendPhoneCode`, or `null` for a preset server phone. |
| `code` | OTP code entered by the user. |

Available phone country codes are exposed as `state.phone.state.phoneCountryCodes`:

| Field | Example | Meaning |
| --- | --- | --- |
| `name` | `United States` | Country display name. |
| `dialCode` | `+1` | Dialing prefix. |
| `code` | `US` | ISO-2 country code. |

### Custom Form

`state.customForm?.fields` contains `DocupassCustomField` values:

| Field | Meaning |
| --- | --- |
| `fieldId` | Preferred key for `saveCustomForm`. |
| `fieldLabel` | User-visible label. Use as a fallback key only if `fieldId` is blank. |
| `fieldDescription` | Optional helper text from the DocuPass profile. |
| `fieldType` | `0` text, `1` multi-line text, `2` dropdown/options. |
| `fieldData` | Raw option data for dropdown fields. Preserve the selected server value when submitting. |

Submit answers with:

```kotlin
val key = field.fieldId.ifBlank { field.fieldLabel }
session.saveCustomForm(mapOf(key to answer))
```

For dropdown fields, the Quick UI accepts one option per line and supports
`label;value`, `label<TAB>value`, or `label|value`. If no separator is present,
the same text is used for label and value.

### Contract

`state.contract` contains:

| Field | Meaning |
| --- | --- |
| `html` | Contract HTML. Custom UI can render it in a `WebView`. |
| `signatureFields` | Signature placeholders extracted from `data-signature` elements. |
| `state` | Full `DocupassSessionState`. |

`DocupassContractSignatureField`:

| Field | Meaning |
| --- | --- |
| `uid` | Required key for `submitContract(signatures)`. |
| `label` | User-visible signature label. |
| `party` | Optional party identifier from the contract template. |

Submit signatures as PNG data URLs:

```kotlin
val signatures = state.contract?.signatureFields.orEmpty().associate { field ->
    field.uid to "data:image/png;base64,..."
}

session.submitContract(signatures)
```

Signature values should include the `data:image/png;base64,` prefix.

## Back Navigation

`KYCScreen` handles Android back presses. Non-terminal steps go back to the
previous SDK step when possible. If the user is already on the first step,
`onBackAtFirstStep` is called so your app can close the screen.

Final success and failure screens are terminal. Back presses do not leave those
screens; the user must tap `FINISH`.

For custom UI:

```kotlin
if (state.canGoBack && !state.isBusy) {
    session.back()
} else {
    // Close your screen or keep the user on the current step.
}
```

## Results

`KYCResult` contains local flow details such as selected document country/type,
uploaded image base64 values, the current session state, and terminal error
information. It is useful for app UI decisions, but it is not the authoritative
identity verification result.

Use your backend webhook or server-side DocuPass result lookup to decide whether
the user is accepted, rejected, or under review.

## License

[MIT](LICENSE) (c) [ID Analyzer](https://www.idanalyzer.com)
