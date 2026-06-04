# Changelog

## 0.1.0 (unreleased)

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
