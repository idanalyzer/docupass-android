# Changelog

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
