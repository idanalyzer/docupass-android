# Changelog

## Unreleased

- Expanded the public README with detailed Quick UI, event session lifecycle,
  state payload, session method, and parameter reference documentation.
- Added `DocupassKycSession.back()` and `DocupassKycNativeState.canGoBack` so
  custom UI integrations can implement SDK back navigation.
- Ignored the local Kotlin/Gradle `.kotlin/` cache directory.

## 0.1.5

Major SDK API refresh.

- Replaced the legacy `DocuPassView` entry point with the new `KYCScreen` Quick UI.
  The Quick UI now exposes `onFinish` for the final FINISH button and
  `onBackAtFirstStep` for app-level dismissal from the first step.
- Added an event-driven integration API for custom UI builds:
  `DocupassKycSession`, `DocupassKycListener`, `DocupassKycNativeState`, and
  `DocupassKycEventKind`.
- Consolidated the SDK implementation under the public `com.idanalyzer.docupass`
  package. The native workflow, API client, error handling, and Compose UI are now
  packaged directly in the Android SDK artifact.
- Reworked the server-driven flow with back-stack support. Non-terminal steps can
  navigate back; terminal success/failure screens stay terminal until FINISH is
  tapped.
- Updated face verification to randomize liveness actions while requiring at least
  two actions.
- Removed the old `DocuPassClient`, `DocuPassController`, `DocuPassView`,
  customization objects, old screen implementations, and legacy model packages.
- Updated the sample app to use `KYCScreen`.
- Rewrote the README for the new Quick UI and event API.
- Fixed local Maven publishing so `publishToMavenLocal` works without signing
  credentials, while Maven Central publishing still signs when signing credentials
  are provided.

## 0.1.4

Terminal/display error-code classification.

- Fixed an infinite resync loop: `DOCUPASS_ERROR_MESSAGE` (e.g. session expired) is
  a hard stop, but was treated as recoverable — the controller re-ran `get_action`,
  got the same error, and looped, hammering the network. It is now a terminal failure
  surfaced as a stable result.
- Classified the remaining display/message codes instead of treating them as
  recoverable: `DOCUPASS_SUCCESS_MESSAGE` and `DOCUPASS_REVIEW_CONTRACT` are terminal
  (success / signed-and-under-review); `DOCUPASS_ERROR_POPUP` (phone-step alerts like
  "incorrect format" / "limit reached") now shows the message and **stays on the
  current step** so the user can retry, rather than resyncing.

## 0.1.3

GPS / location support.

- Fixed `DOCUPASS_FATAL_ERROR` (`LOCATION_HEADER_MISSING`) right after document
  selection on DocuPass profiles that have **location tracking enabled**. When the
  session sets `gps = true`, the drop-in `DocuPassView` now requests location
  permission, obtains a device fix (via the framework `LocationManager` — no Google
  Play Services dependency), and sends the `Geolocation` header on every subsequent
  request. The flow is held on a brief "getting your location" screen until the fix
  is set, so the next step can't be submitted without it. (Previously the
  `setGeolocation` plumbing existed but was never invoked, so any GPS-enabled profile
  failed on the second server call.)
- Added the `ACCESS_COARSE_LOCATION` permission (so users who grant only
  "approximate" location still work) and three overridable strings:
  `locationTitle`, `locationBody`, `locationPermissionRequired`.

## 0.1.2

Crash fix.

- Fixed a JSON parse crash when the session response contains an explicit
  `null` for a list field (e.g. `customField` is `null` whenever no custom form
  is configured). The client now coerces server-sent nulls to each field's
  default, so the document / face / phone / contract flows no longer crash for
  profiles without custom fields.

## 0.1.1

Audit fixes, customization hooks, and documentation corrections.

- **Customization** — `DocuPassStrings` (override any user-facing label, in any
  language) and `DocuPassTheme` (`primaryColor`, `logoUrl`, `showLogo`) threaded
  through `DocuPassView` via CompositionLocals; one-line usage unchanged, headless
  API unaffected.
- **E-signature** — contract fields are now detected by `data-signature` (reading
  each element's `data-uid`), matching the DocuPass v3 web flow; leftover `%{…}`
  prefill placeholders are stripped before display.
- **Phone** — the dialing code is now chosen from a country picker populated from
  `session.phoneCountryCode` (was free text).
- Internal-only comments scrubbed from the published sources.

## 0.1.0

Initial DocuPass Android SDK — Phase 1 of the mobile-SDK program.

- `DocuPassClient` — full docupassappv3 protocol client: all 9 endpoints, the
  `DOCUPASS`/`DOCUPASS_SESSION` auth-header progression, region-by-reference
  (US/EU), Geolocation header, and the `{success,error{code,message}}` envelope
  (terminal states surfaced as typed `DocuPassErrorCode`s).
- `DocuPassController` — headless state-machine driver exposing the flow as a
  `StateFlow<DocuPassState>` with recoverable-error resync.
- On-device active liveness — `FaceLandmarkerEngine` (MediaPipe Tasks Vision,
  bundled `face_landmarker.task`) + `LivenessController` (neutral → turn-left →
  turn-right, best-neutral-by-eye-area capture; ported from the DocuPass v3 web
  flow).
- CameraX capture (`CameraController`) — front-camera liveness analysis + back-
  camera document still capture.
- Bundled country / document-type catalog.
- Drop-in Jetpack Compose UI (`DocuPassView`) covering document, face, custom
  form, phone verification, and e-signature contract steps.
- Sample app under `:sample`.

Built against the DocuPass v3 API and verified against the production service.
