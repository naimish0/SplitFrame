# SplitFrame Current State

Audited from the working source tree through 2026-07-21. Older phase/design documents were used only as historical context when they agreed with current code.

## Product summary

SplitFrame is an offline-first Android media utility with three reachable workflows:

1. Build a JPEG photo collage from a template.
2. Resize one image and save it as JPEG, PNG, or WebP.
3. Join two or more videos, in order, into one H.264/AAC MP4.

The home screen exposes Photo Collage and Video Merge. Resize is reachable from an icon on the template-picker toolbar. The app has no account, cloud project, backend processing, subscription, or purchase flow.

## Classification legend

- **Implemented** — reachable and the main behavior is present in source.
- **Partially implemented** — a useful subset exists, but a stated or modeled part is missing.
- **Implemented but inaccessible** — code/data support exists without a reachable user path.
- **Inconsistent or broken** — reachable behavior conflicts with another layer or can produce an incorrect result.
- **Not implemented** — no production path provides the behavior.
- **Cannot verify** — source inspection is insufficient; device, console, or release evidence is required.

## Feature inventory

| Area | Status | Verified current behavior |
|---|---|---|
| Home and privacy entry | Implemented | Photo and video cards plus an in-app privacy page are reachable. |
| App navigation | Partially implemented | A private enum still drives screens, but a saveable route restores the video-project browser or exact active video editor/project. Photo, resize, template, editor, and privacy destinations still restore to Home. |
| Template engine | Implemented | 110 runtime templates: 35 explicit plus 75 generated layouts covering 1–15 slots across five variants. |
| Template discovery | Inconsistent or broken | Filters and slot compatibility exist, but generated cards can show the app name/tagline; resize is buried in a toolbar; no search/collections. |
| Photo selection | Implemented | Android image Photo Picker, up to 15 images, ordered assignment, replacement, MIME validation, sampled decode, and EXIF orientation. |
| Collage editing | Implemented | Crop-to-fill pan/zoom, swap/reorder, spacing, rounded corners, background choices, adaptive gradient, before/after divider, undo/redo. |
| Collage border | Implemented but inaccessible | State and preview/export renderers exist, but no intent or UI changes the zero-width transparent default. |
| Photo export | Partially implemented | JPEG export validates encode/publish success and rolls back rows after caught in-process failures. Preview/export now share width-relative spacing, corners, border, and divider geometry; pixel parity is unverified and the before/after divider color differs in dark theme. |
| Photo sharing | Implemented | A successful photo export can be shared; its post-export interstitial is suppressed for that share action. |
| Resize core | Implemented | 1080, 2K, 4K, 2×, 4×, and custom planning; aspect lock; JPEG/PNG/WebP; 8,192-edge and 24 MP limits. Publication validates encode/publish success and rolls back caught in-process failures. It is conventional scaling, not AI upscaling. |
| Resize preview | Inconsistent or broken | Local zoom/pan affects only the preview; unlocked custom dimensions can stretch export without previewing that distortion. |
| Resize metadata | Partially implemented | EXIF orientation is applied while decoding, but source metadata is not copied to the output. |
| Video selection | Implemented | Ordered, video-only Photo Picker selection/replacement plus metadata and trim validation. |
| Video preview | Implemented | One ExoPlayer playlist shows one full-canvas clip at a time with a cumulative trimmed-duration timeline matching export order. |
| Sequential video export | Implemented | Media3 Transformer concatenates trimmed videos in order. Its foreground Worker validates the cache MP4, performs an exact-length cancellable MediaStore copy, and commits success only through the exact current Room work row. |
| Video fit/transform parity | Cannot verify | Reachable preview is crop-fill only and now derives placement from the export crop model; real-device frame, rotation, and aspect comparison is pending. |
| True video collage/split screen | Not implemented | No multi-sequence compositor or cell geometry is used by export. |
| Mixed image/video composition | Implemented but inaccessible | Models/readers/persistence support images, but UI filters them and export rejects non-video media. |
| Video share action | Not implemented | The saved video has no direct share UI. |
| Favorites | Implemented but inaccessible | Room and `ProjectStore` can add/remove/observe favorite template IDs; no UI calls the feature. |
| Recent photo exports | Implemented but inaccessible | Room and `ProjectStore` expose the 20 newest export records; no screen consumes them. |
| Recent layouts | Not implemented | No usage record, ranking, or UI exists. |
| Recent video projects | Implemented | Video Merge opens a timestamped local browser with resume, rename, duplicate, API 27+ fixed-size frame thumbnail/placeholder, delete confirmation, token-guarded Undo, and empty/missing/corrupt states. API 24–26 deliberately uses placeholders; photo and resize projects are not included. |
| Photo/resize recovery | Not implemented | Editor projects are ViewModel memory only; only the last photo export resolution is persisted. |
| Video project persistence | Implemented | The saveable route carries one canonical project UUID into a project-keyed Koin ViewModel; explicit new sessions create that exact ID and restore-only sessions never resurrect a missing row. |
| WorkManager video recovery | Implemented | The UI observes the exact project's Room work row across Activity recreation. Work writes are guarded by WorkManager ID and allowed prior state; the final running-to-succeeded update is the cancellation-shielded publication commit. Controlled process-death validation remains manual. |
| Video export notification routing | Implemented | Foreground and terminal notifications use a strict action, destination, canonical project UUID, matching data URI, and project-specific completion identity to open the exact stored project. Missing/malformed/deleted targets fall back to Home. |
| Ads and consent | Partially implemented | UMP-gated banner/native/interstitial/app-open formats exist; release-console declarations and real rendering cannot be proved from source. |
| Offline editing/export | Implemented | Core selection, editing, resize, and export do not require a service call. Ads/consent naturally depend on network. |

## Architecture and state flow

- `SplitFrameApp` owns manual navigation. Its saveable route retains the video browser or active video project, and Koin creates the video ViewModel with an exact project-key and session arguments.
- Photo state follows an intent/state ViewModel pattern and keeps up to 30 undo snapshots. The template catalog and geometry live in `domain`.
- Resize has its own intent/state ViewModel and repository; preview zoom/pan remains local Compose state rather than domain state.
- Video state is persisted through `VideoProjectStore`, while video exports run via WorkManager and `VideoExportWorker`.
- Room version 5 has explicit migrations 1→2, 2→3, 3→4, and 4→5. The tested 4→5 migration backfills video rows into a separate recent metadata index; historical migrations remain untested and schema export is disabled.
- Koin wires concrete implementations in one `appModule`. The Worker retrieves dependencies from Koin's global context rather than an injected Worker factory.

## Persistence and lifecycle findings

| Data | Storage | Recovery status |
|---|---|---|
| Current app destination | Saveable `AppRoute` | Video browser or active editor/project is restored; other destinations intentionally fall back to Home. |
| Template filter/grid position | `rememberSaveable` | Restored when the screen itself is restored, but the route is not. |
| Photo collage project | `MergeViewModel` memory | Survives ordinary configuration only while the ViewModel is retained; no process recovery. |
| Resize project | `SingleImageViewModel` memory plus local preview state | No process recovery. |
| Last photo export resolution | Room preference | Restored. |
| Favorites/export history | Room | Persisted but inaccessible. |
| Video project | Room plus recent metadata and saveable route UUID | Exact-ID open/create semantics, strict payload decoding, a keyed ViewModel, and the browser restore persisted video drafts without creating unrelated UUIDs. |
| Video export work/result | WorkManager plus Room | Recreated UI observes queued/running/terminal state for the exact work ID; stale worker transitions are rejected. |
| Notification destination | Strict explicit `MainActivity` intent | Exact action, destination, data URI, and canonical project UUID are validated before routing. |

The video project uses a custom URL-encoded, tab/newline-delimited positional blob inside Room. Modern nonblank payloads now decode all-or-nothing; malformed/unknown/duplicate/invalid rows are surfaced as corrupt and are never normalized or rewritten. A blank modern payload is a valid empty draft, while `NULL` retains legacy clip fallback. There is still no schema-level media-item table.

## Risk register

Risks are intentionally ranked in the required product-safety order.

1. **Crash or data loss.** Photo/resize work is volatile; video selection is unbounded; large bitmap outputs can exhaust memory. An EXIF rotation allocation failure can leak an internal decoded bitmap before repository ownership. Active video recovery is implemented, but controlled system process restoration still needs device validation. No reproducible baseline crash was found in tests.
2. **Invalid/corrupt export.** Photo, resize, and video publication now check write/encode/publish results and roll back the exact row after caught in-process failure/cancellation, but process death/provider rollback failure can bypass cleanup. Video still has no free-space, codec, or playable-output integrity validation. A post-save history-write exception can also report photo failure after a valid file exists.
3. **Preview/export mismatch.** Photo effect geometry now shares one source calculation, but actual renderer pixels and before/after dark-theme divider color remain unverified/inconsistent. Resize zoom/pan remains decorative; video still needs device frame comparison.
4. **Video inconsistency.** The primary preview/export contract is aligned; remaining risk is unverified rotation, codecs, audio boundaries, final-frame behavior, and retained legacy split-state fields.
5. **Project recovery failure.** Video browser/editor routes, indexed drafts, and notification return are restored without `SavedStateHandle`; photo/resize remain volatile, the narrow Room-queued-before-WorkManager-enqueue window is not reconciled, revoked/cloud grants need real-provider validation, and OEM/system process restoration still needs manual validation.
6. **Ad policy risk.** UMP gating is present, but app-open timing, deployed privacy/data declarations, age positioning, and real native rendering still need verification.
7. **Discoverability and UX.** Generated template labels are wrong, resize is hidden, favorites/photo history remain inaccessible, migrated video projects initially use placeholder thumbnails, and video lacks direct share.
8. **Creative enhancements.** Social presets, batch/target-size resize, true video collage, and richer discovery are lower priority than correctness.

## Quality baseline

- `debug` and `release` are the only build types; there are no flavors. Release code/resource minification is disabled, and no repository CI workflow was found.
- The current JVM suite has 126 tests across 18 classes. It adds strict video payload/round-trip and browser-route coverage to the existing photo geometry, publication, recovery, and math suites. It still does not render/encode a real bitmap or Media3 output.
- Seven Android test files provide 15 tests. All 15 pass on a physical API 36 Samsung, including the five new generated-DAO/recents/v4→v5 migration tests and both recovery tests.
- Debug lint reports 0 errors and 114 warnings: 64 `UnusedResources`, 21 `UseKtx`, 9 `PluralsCandidate`, 8 `GradleDependency`, 6 `TypographyEllipsis`, 3 `NewerVersionAvailable`, 2 `AndroidGradlePluginVersion`, and 1 `IconLocation`.
- There is no lint baseline, historical v1→v4 migration fixture, controlled process-death test, real revoked/cloud URI or ContentResolver/MediaStore fault-injection test, real JPEG/PNG/WebP integrity/alpha test, real MP4 integrity test, or ad/consent lifecycle test.

## Evidence boundaries

Source inspection cannot verify OEM Photo Picker grants, device codecs, HDR tone mapping, mixed-codec/audio continuity, low-storage failure behavior, actual MediaStore corruption visibility, process killing by different OEMs, Play Console declarations, deployed privacy URLs, or live ad rendering. These are explicit manual-test items rather than assumed functionality.
