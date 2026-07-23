# SplitFrame Product Roadmap

This roadmap is risk-led. Cycle 0 was analysis-only; the sequential-video migration and active-video recovery slices were subsequently approved and implemented.

Status update, 2026-07-23: exact video recovery, video-only recent projects, bounded photo/resize/video publication integrity, durable export recovery, and the highest-risk photo effect-geometry parity slice are complete in source. Controlled system process death, real revoked/cloud URI and MediaStore failure injection, and real pixel/output decoding/playback remain manual release checks.

## Prioritization basis

1. Crash or data loss
2. Invalid/corrupt export
3. Preview/export mismatch
4. Video inconsistency
5. Project recovery failure
6. Ad policy risk
7. Discoverability and UX
8. Creative enhancements

Project recovery was addressed ahead of broader correctness work because already-persisted video project/work state was unreachable. Photo, resize, and video publication then addressed caught in-process invalid/corrupt export paths; Cycle 2 aligned the confirmed photo effect-unit mismatch. The next bounded target returns to the highest-ranked remaining bitmap-lifecycle risk.

## Completed recovery target

### Restore the active video editor/export session

Restore the exact video destination, project ID, and associated WorkManager export after Activity recreation and Android system process recreation. Notification routing was part of this historical slice and was removed on 2026-07-23. Do not add a project browser or rewrite navigation.

### Why this target

- Video projects and export-work records are already persisted by project ID.
- The old fresh `VideoMergeViewModel` path generated another UUID and could not observe the stored record.
- The old plain route returned to Home on UI recreation.
- The completed slice now saves the active video route, opens one exact project, and reattaches its exact work row without adding a project browser.

### Affected module and likely files

Only the `:app` module is affected.

| File | Likely responsibility |
|---|---|
| `app/src/main/java/com/rameshta/splitframe/MainActivity.kt` | Own Activity creation and restoration without notification deep-link routing. |
| `app/src/main/java/com/rameshta/splitframe/presentation/SplitFrameApp.kt` | Save/restore the video destination/project, key the ViewModel, and route missing targets safely to Home. |
| `app/src/main/java/com/rameshta/splitframe/presentation/video/VideoMergeViewModel.kt` | Open the supplied project, preserve selection, observe its exact work row, and reject stale work events. |
| `app/src/main/java/com/rameshta/splitframe/data/VideoProjectStore.kt`, `data/local/Daos.kt` | Separate explicit create from restore and atomically guard work transitions by work ID/prior state. |
| `app/src/main/java/com/rameshta/splitframe/di/AppModule.kt` | Pass exact session arguments through the existing Koin ViewModel definition. |
| `app/src/main/java/com/rameshta/splitframe/export/VideoExportWorker.kt`, `VideoExportRepository.kt` | Keep only Android's required active foreground-service status and prevent canceled/replaced workers from publishing or reporting stale output. |
| Focused JVM/Compose tests | Cover exact-ID loading, route restoration, work reattachment, malformed arguments, state races, and Activity recreation. |

The implementation owner must inspect current APIs before deciding whether every listed file needs a change. Room tables/migrations should remain unchanged unless a verified need is found; such a schema change would exceed this slice.

### Acceptance criteria

1. Rotating or recreating the Activity while editing a video returns to the video editor with the same project ID and edit state.
2. Android saved-state process recreation returns to the same video editor/project rather than Home or a new blank UUID.
3. If that project has `ENQUEUED` or `RUNNING` export work, the recreated UI observes the same work ID and current progress.
4. If the work completed while the process was absent, the recreated UI resolves the saved result/error for that same project.
5. Completion and failure are surfaced from persisted work state in the exact project UI, without completion/failure notifications or notification deep links.
6. A missing, malformed, or no-longer-existing restored project ID falls back safely without a crash or accidental access to another stale project.
7. Home, photo collage, resize, back navigation, export cancellation, and the existing sequential video exporter retain current behavior.
8. Editing and export remain fully usable offline; ad/consent code and production identifiers are untouched.
9. No recent-project UI, Navigation component migration, video preview redesign, export-format change, or Room schema redesign is included.

### Targeted tests

- JVM: `VideoProjectStore` returns the requested stored project, creates only for an explicit missing/new ID, and keeps different IDs isolated.
- ViewModel coroutine test: initialization with a known ID restores the project and observes its matching `video_export_work` row.
- Compose/instrumentation: saved destination/project survives `recreate()` and displays the restored edit state.
- Negative tests: blank/invalid/deleted ID, failed work, completed work, and canceled work all produce a safe deterministic state.
- Regression commands: `:app:testDebugUnitTest`, the focused connected test class, `:app:lintDebug`, then `:app:assembleDebug`.

### Implementation risks

- Creating two ViewModels for one project if Compose keys/parameters are unstable.
- Reprocessing an Activity intent after recomposition and reopening a screen unexpectedly.
- Showing stale WorkManager state after reset/cancel.
- Confusing Android system state restoration with a force-stop/cold-start “recent projects” feature, which is explicitly outside scope.

### Completion evidence

- Source and JVM tests cover acceptance criteria 1 and 3–9 at contract/state level.
- A physical API 36 connected test proves the exact stored project and route survive Activity `recreate()`.
- Acceptance criterion 2 and real queued/running/completed background-work behavior still require the controlled manual process-death run in `docs/qa/manual-test-matrix.md`.

## Completed photo-export integrity target

Photo-collage JPEG publication now follows one explicit in-process transaction in `ImageExportRepository`: insert the MediaStore row, require successful JPEG encoding, flush and close the stream, require exactly one Android Q+ publish update, and only then return the URI. Any post-insert failure best-effort deletes that exact URI while preserving the original failure; output bitmap ownership is exception-safe. Nine focused JVM tests cover success ordering, insert/write/publish failures, injected transaction cancellation, rollback false/exception behavior, self-suppression safety, compression failure, and flushing.

That photo slice intentionally left photo preview/export geometry, video publication, UI, Room, dependencies, ads, signing, and production identifiers unchanged; video publication was addressed later in its own target. Process death after insert is outside the in-process rollback boundary, and a later Room history-write error can still report failure after a valid save; both remain explicit follow-up risks. Real API 24–29+ low-storage/provider failure behavior still requires device validation.

## Completed resize-export integrity target

Single-image JPEG, PNG, and WebP publication now uses its own in-process transaction in `SingleImageProcessingRepository`: check cancellation, insert, require encoding, flush/close, check cancellation, require exactly one Android Q+ publish update, check cancellation again, then return success. Caught failure or cancellation after insertion best-effort deletes the exact URI while preserving the original failure.

Decoded input and rendered output are cleaned identity-safely across cancellation, OOM, render, save, and cleanup failures. Existing dimensions, white JPEG background, alpha-capable PNG/WebP rendering, API-specific WebP mapping, quality, MIME/extension, naming, permissions, path, and offline behavior remain unchanged. Thirteen focused JVM tests cover the transaction, encoders, mappings, and aliased/distinct cleanup; all nine connected regressions pass on API 36, but they do not encode real files.

Process death, real provider/low-storage faults, alpha/pixel-parity verification, and cancellation
timing inside non-interruptible compression remain explicit residuals. Bounded real JPEG/PNG/WebP
reopen/decode validation and the upstream EXIF rotation-allocation ownership leak were addressed in
later Phase 0 slices.

## Completed video-publication integrity target

Final MP4 publication now remains inside one repository/Worker ownership transaction while preserving the accepted sequential composition. The repository rejects a missing/directory/empty cache source before insert, copies the exact expected length through a cancellation-aware 64 KiB buffer, requires flush and both closes, checks exact work ownership before publication, and requires exactly one Android Q+ pending-row update.

The exact Room `running`-to-`succeeded` compare-and-set is the final commit after MediaStore publication. A final active check runs before that call; the call itself is cancellation-shielded so SQLite cannot commit success and then have cancellation discard the Boolean result. A false result or database error still deletes only the inserted MediaStore URI. Any rollback failure is suppressed without replacing the original copy, close, publish, ownership, or commit failure.

Sixteen focused JVM tests cover transaction order, missing/directory/empty/nonempty cache files, multi-buffer copy and cancellation, incomplete input, flush/close failure, publish/ownership/commit failure, rollback false/throw/self-suppression, and the terminal cancellation shield. Focused worker/store/recovery, all 114 JVM tests, lint, assembly, one targeted recovery instrumentation test, and all nine connected tests pass. Real ContentResolver/provider faults, API 24–29 behavior, process death, codec compatibility, and playable-MP4 validation remain explicit residuals.

## Completed Cycle 2 photo-geometry parity target

Photo preview gesture/hit/render paths and JPEG export now derive spacing, rounded corners, border width, and before/after divider thickness from one pure canvas-width-relative metric: existing dp-named values are design units on a canonical 360-unit width. Before/after position also uses one shared clamped calculation. Existing `MergeProject` fields, sliders, immutable export snapshot, output dimensions, crop/pan/zoom, EXIF handling, Room, format/quality/path, and offline behavior remain unchanged.

`LayoutMath.cellFrame` retains the existing outer/full and inner/half spacing contract for normal values. When maximum spacing exceeds a dense cell, it now scales those insets while reserving 5% of the base cell extent, preventing inverted/zero-area photos in 15-cell templates.

Seven new pure tests cover canonical metrics, exact FHD square/16:9/4:5 output sizes, normalized preview/export cells and effects, zero/max effects, all 110 templates at maximum spacing, extreme oriented-dimension crop, and divider position/thickness. The focused 25-test gate, all 122 JVM tests, lint, assembly, five targeted photo-editor connected tests, and all nine device tests pass. Pixel-level Compose/Android Canvas rounding/antialiasing, actual EXIF decoding, real JPEG comparison, and the before/after dark-theme divider color remain residuals.

## Completed Cycle 3 video-recents target

Video Merge now opens a video-only recent-project browser backed by Room v5. A tested v4→v5 migration indexes existing video rows without rewriting them. Stable metadata is separate from the editor payload so rename survives content saves and tombstones prevent late editor jobs from resurrecting deleted projects.

The browser supports new/resume, rename, duplicate, timestamps, fixed-size API 27+ thumbnail frames with safe placeholders on older APIs/failure, delete confirmation, token-guarded Undo/final purge, export-active deletion guards, and explicit empty/missing/corrupt status. URI readability and thumbnails run off-main. Strict all-or-nothing decoding preserves blank and legacy compatibility while preventing corrupt rows from being partially rewritten.

Four additional JVM assertions, five generated-DAO/migration device tests, and one additional browser/editor Activity-recreation test cover the bounded slice; all 15 connected tests pass on physical API 36. Photo/resize persistence, Navigation/SavedStateHandle adoption, a normalized media-item table, true process-death/provider revocation, and video composition/export changes remain non-goals or manual boundaries.

## Completed EXIF orientation ownership target

`ImageSourceReader` now releases its decoded, decoder-owned bitmap if EXIF transformation
allocation throws. Ownership is retained when the transformation returns the same bitmap and
transferred normally when it returns a distinct bitmap. Focused tests prove exact-once release,
identity retention, distinct-result transfer, original-failure preservation, and cleanup of a
distinct transformed result if releasing the original fails.

MIME validation, EXIF mappings, sampled decode planning, oriented dimensions, repository ownership,
and offline behavior remain unchanged.

## Completed durable export-recovery target

### Reconcile pending MediaStore publication after process death

Design and implement a durable, exact-output publication journal/reconciler for photo, resize, and
video exports. It must distinguish incomplete publication from a successfully committed output,
delete only SplitFrame-owned incomplete rows/cache files, preserve valid completed output, and work
across API 24–36 without broad storage access.

- **Affected module/files:** `:app`; export transaction boundaries, application startup/recovery,
  focused persistence tests, provider/device tests, and audit documentation.
- **Risks:** deleting a valid completed export, retaining a visible pre-Q partial, journaling after
  insertion too late, URI reuse, and disagreement between video Room work state and MediaStore.
- **Acceptance criteria:** exact durable ownership before vulnerable writes; startup reconciliation
  is idempotent; incomplete output is removed; committed output is retained; cancellation and
  process recreation cannot create duplicate success; provider failure remains actionable.
- **Non-goals:** target-size compression, batch work, UI redesign, localization, billing, ads,
  schema normalization, or video composition.

Photo, resize, and video publication now synchronously journal a unique output reservation before
MediaStore insertion, then bind the exact returned content URI before writing bytes. The journal
records prepared, writing, validated/ready, and published phases plus the exact owned video cache
path. Startup ignores current-process entries, deletes only journaled incomplete
rows, retains already-published output, confines cache deletion to `cache/video_exports/*.mp4`, and
leaves provider failures journaled for retry. Recovery is idempotent and requires no broad storage
access. Eight focused recovery tests plus all 277 JVM tests, lint, and debug assembly pass.

Controlled process death at each transaction boundary and API 24–29 provider behavior remain manual
device checks.

## Completed bounded video-resource target

### Bound video selection and export resource use

Add explicit clip-count/duration/output-size limits and disk-space/codec preflight before Media3
rendering. Keep the accepted sequential composition and current project format unchanged.

The picker and project contract now accept at most 20 clips. Worker preflight rejects output over
30 minutes or an estimated 2 GiB before rendering. Repository preflight requires an H.264 encoder
supporting the selected dimensions and enough cache capacity for the working and output copies.
Four focused JVM tests and a physical API 36 FHD encoder/storage check pass.

## Next recommended slice

### Validate real video frames, rotation, audio boundaries, and final-frame behavior

Exercise representative device-generated source clips through preview and export, then compare
decoded frames, rotation, duration, transition boundaries, replacement audio, and final-frame
behavior on the supported API/device matrix.

## Deferred backlog

These remain pending and must be proposed as separate, small slices in the established risk order:

- Align resize controls with exported pixels and resolve the remaining before/after divider-color mismatch in separate bounded slices.
- Validate the implemented sequential preview against real exported frames, rotations, audio boundaries, lifecycle transitions, and supported codecs before release.
- Add more aggressive abandoned-temp cleanup only if device evidence shows the durable journal and existing age-bounded sweep are insufficient.
- Add historical v1→v4 migration fixtures and a deliberate future media-item persistence format strategy; v4→v5 is covered.
- Correct generated template labels and expose existing favorites/history only after correctness work.
- Validate and refine app-open/ad rendering; consider adaptive banners and a no-ads purchase only as a separate policy/product decision.
- Consider social presets, batch/target-KB resize, video share, and true split-screen composition as later enhancements.
