# SplitFrame Mixed Media Audit

Date: 2026-07-17

## Baseline Verification

Commands run before mixed-media changes:

- `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`
  - Result: passed.
  - Baseline notes: Kotlin source-set warning for `android.disallowKotlinSourceSets=false`.
- `./gradlew :app:connectedDebugAndroidTest`
  - Result: passed on attached `SM-S928B`, API 36.
  - Baseline notes: existing Compose test-rule deprecation warnings; `appops` reported no UID for `androidx.test.services`, but the instrumentation suite completed successfully.

No baseline test, lint, or build failures were present before this phase.

## Existing Reusable Architecture

### Phase 1 Photo Templates

- `TemplateRepository` already defines reusable `LayoutTemplate` and `LayoutCell` instances for 1-15 slots.
- Existing 2-9 slot coverage includes side-by-side, top/bottom, large/small, 2x2, 3-column, 3-row, featured mosaics, 2x3, 3x2, 3x3, adaptive grids, and balanced mosaics.
- `TemplateCatalog.compatibleTemplates(...)` filters templates by slot count and can be reused for mixed media.
- `LayoutMath.cellFrame(...)`, crop-to-fill, transform gesture math, and output-size calculation are generic and not photo-specific.

### Phase 2 Video Implementation

- Current Phase 2 stores exactly two `VideoClip` values in `VideoMergeProject.clips`.
- The editor uses two remembered `ExoPlayer` instances and side-by-side/top-bottom layout branching.
- Preview lives inside the main vertically scrolling content, so layout changes can happen while the preview is off-screen.
- Export already uses Media3 `Composition`, `EditedMediaItemSequence`, `Transformer`, custom `VideoCompositorSettings`, H.264, AAC, HDR-to-SDR tone mapping, temporary output, and MediaStore publishing.
- WorkManager is already used for background export, the platform-required foreground-service status, progress state, cancellation, and durable export state.
- Undo/redo is project-snapshot based in `VideoMergeViewModel`.

### Persistence

- Room is currently version 2 with `video_projects` and `video_export_work`.
- `video_projects` stores `clip0` and `clip1` encoded as tab-separated fields.
- Supporting 2-9 mixed-media items requires a non-destructive schema extension because the existing two columns cannot represent arbitrary image/video cells.

### Media Selection

- Photo workflow uses Android Photo Picker with `ImageOnly`.
- Video workflow uses Android Photo Picker with `VideoOnly` and `maxItems = 2`.
- Image metadata validation exists in `ImageSourceReader`.
- Video metadata validation exists in `VideoMetadataReader`.

### Gaps To Address

- Replace the two-video project shape with a generic media-by-cell model while keeping migration compatibility for existing two-video projects.
- Use Phase 1 templates for mixed-media projects instead of duplicate video layouts.
- Allow image-and-video picker selection up to nine items.
- Make preview/player coordination dynamic for multiple video cells.
- Add composition timeline looping in preview.
- Add static image visual items to video export when at least one video is present.
- Keep preview fixed above/alongside independently scrolling controls.
- Add decoder-capability fallback messaging instead of assuming every device can preview nine videos live.
