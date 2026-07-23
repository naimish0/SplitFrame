# SplitFrame Current State

Audited from the working source tree through 2026-07-23. Older phase/design documents were used only as historical context when they agreed with current code.

## Product summary

SplitFrame is an offline-first Android media utility with three reachable workflows:

1. Build a JPEG photo collage from a template.
2. Resize one image and save it as JPEG, PNG, or WebP.
3. Join two or more videos, in order, into one H.264/AAC MP4.

The home screen exposes Photo Collage and Video Merge. Resize is reachable from an icon on the template-picker toolbar. The app has no account, cloud project, backend processing, subscription, or purchase flow.

## Shared-contract profile notes

- Localization is intentionally `SINGLE_LANGUAGE` with English (`en`) as the only shipped and
  default locale. This is the approved exception to the shared contract's 14-locale multilingual
  baseline. Do not advertise or generate store assets for unshipped translations.
- The activated shared contract prohibits app-open ads for first-time users.
  `SplitFrameAdManager` suppresses app-open loading and presentation for the entire first installed
  process session, records completion after the first genuine background transition, and enables
  the existing app-open policy only in a later process. Configuration changes do not complete the
  first session; an interrupted first launch safely receives suppression again.

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
| App navigation | Implemented | Saveable routes restore Home, template discovery and its caller, photo and resize editors, Privacy, the video-project browser, and the exact active video editor/project. |
| Template engine | Implemented | 110 runtime templates: 35 explicit plus 75 generated layouts covering 1–15 slots across five variants. |
| Template discovery | Inconsistent or broken | Filters and slot compatibility exist, but generated cards can show the app name/tagline; resize is buried in a toolbar; no search/collections. |
| Photo selection | Implemented | Android image Photo Picker, up to 15 images, ordered assignment, replacement, MIME validation, sampled decode, and EXIF orientation. |
| Collage editing | Implemented | Crop-to-fill pan/zoom, swap/reorder, spacing, rounded corners, background choices, adaptive gradient, before/after divider, undo/redo. |
| Collage border | Implemented | Reachable border controls update width, color, style, and gradient state shared by preview, export, undo, and draft restoration. |
| Photo export | Partially implemented | JPEG export validates encode/publish success and rolls back rows after caught in-process failures. Preview/export now share width-relative spacing, corners, border, and divider geometry; pixel parity is unverified and the before/after divider color differs in dark theme. |
| Photo sharing | Implemented | A successful photo export can be shared; its post-export interstitial is suppressed for that share action. |
| Resize core | Implemented | 1080, 2K, 4K, 2×, 4×, and custom planning; aspect lock; JPEG/PNG/WebP; 8,192-edge and 24 MP limits. Publication validates encode/publish success and rolls back caught in-process failures. It is conventional scaling, not AI upscaling. |
| Resize preview | Implemented | Preview and export share the selected Fit/Fill geometry and target aspect, including unlocked custom dimensions; comparison uses actual source/output metadata. |
| Resize metadata | Partially implemented | EXIF orientation is applied while decoding, but source metadata is not copied to the output. |
| Video selection | Implemented | Ordered, video-only Photo Picker selection/replacement plus metadata and trim validation. |
| Video preview | Implemented | One ExoPlayer playlist shows one full-canvas clip at a time with a cumulative trimmed-duration timeline matching export order. |
| Sequential video export | Implemented | Media3 Transformer concatenates trimmed videos in order. Its foreground Worker validates the cache MP4, performs an exact-length cancellable MediaStore copy, and commits success only through the exact current Room work row. |
| Video fit/transform parity | Cannot verify | Reachable preview is crop-fill only and now derives placement from the export crop model; real-device frame, rotation, and aspect comparison is pending. |
| True video collage/split screen | Not implemented | No multi-sequence compositor or cell geometry is used by export. |
| Mixed image/video composition | Implemented but inaccessible | Models/readers/persistence support images, but UI filters them and export rejects non-video media. |
| Video share action | Implemented | A successful video export exposes an Android share chooser with the exact saved content URI and read grant; absence of a compatible target is recoverable. |
| Favorites | Implemented | Template discovery exposes favorite controls and Home renders the persisted favorites rail when it has data. |
| Recent photo exports | Implemented | Home renders valid recent photo exports from Room and opens the exact saved content URI in a compatible viewer. Missing or unsupported output is reported without blocking other work. |
| Recent layouts | Implemented | Substantive template use and successful export update persisted recents; discovery and Home consume the ranked records. |
| Recent video projects | Implemented | Video Merge opens a timestamped local browser with resume, rename, duplicate, API 27+ fixed-size frame thumbnail/placeholder, delete confirmation, token-guarded Undo, and empty/missing/corrupt states. API 24–26 deliberately uses placeholders; photo and resize projects are not included. |
| Photo/resize recovery | Implemented | Photo drafts use a strict Room-backed primitive codec plus `SavedStateHandle`; resize source, request, result, Fit/Fill, and output metadata restore through saved state and preferences with validity checks. |
| Video project persistence | Implemented | The saveable route carries one canonical project UUID into a project-keyed Koin ViewModel; explicit new sessions create that exact ID and restore-only sessions never resurrect a missing row. |
| WorkManager video recovery | Implemented | The UI observes the exact project's Room work row across Activity recreation. Work writes are guarded by WorkManager ID and allowed prior state; the final running-to-succeeded update is the cancellation-shielded publication commit. Controlled process-death validation remains manual. |
| Video export notification routing | Implemented | Foreground and terminal notifications use a strict action, destination, canonical project UUID, matching data URI, and project-specific completion identity to open the exact stored project. Missing/malformed/deleted targets fall back to Home. |
| Ads and consent | Partially implemented | UMP-gated banner/native/interstitial/app-open formats exist; release-console declarations and real rendering cannot be proved from source. |
| Offline editing/export | Implemented | Core selection, editing, resize, and export do not require a service call. Ads/consent naturally depend on network. |

## Architecture and state flow

- `SplitFrameApp` owns manual navigation. Its saveable route retains every reachable destination, including Privacy, and Koin creates the video ViewModel with an exact project-key and session arguments.
- Photo state follows an intent/state ViewModel pattern, keeps up to 30 undo snapshots, and persists a strict active-draft payload. The template catalog and geometry live in `domain`.
- Resize has its own intent/state ViewModel and repository; its saved state retains the source and Fit/Fill request while comparison uses validated output metadata.
- Video state is persisted through `VideoProjectStore`, while video exports run via WorkManager and `VideoExportWorker`.
- Room version 5 has explicit migrations 1→2, 2→3, 3→4, and 4→5. The tested 4→5 migration backfills video rows into a separate recent metadata index; historical migrations remain untested and schema export is disabled.
- Koin wires concrete implementations in one `appModule`. The Worker retrieves dependencies from Koin's global context rather than an injected Worker factory.

## Persistence and lifecycle findings

| Data | Storage | Recovery status |
|---|---|---|
| Current app destination | Saveable `AppRoute` | Every reachable destination restores; exact video-project identity and the template caller are retained. |
| Template filter/grid position | `rememberSaveable` | Restored with the saveable template route. |
| Photo collage project | Room preference payload plus `SavedStateHandle` | Strict versioned primitive draft restores valid state; missing media remains repairable and malformed drafts require reset. |
| Resize project | `SavedStateHandle` plus preferences | Source, request, actual output metadata, output readability, and Fit/Fill semantics are validated on restoration. |
| Last photo export resolution | Room preference | Restored. |
| Favorites/layout use/export history | Room | Favorites and recent layouts feed discovery and Home; recent valid photo exports are reachable from Home. |
| Video project | Room plus recent metadata and saveable route UUID | Exact-ID open/create semantics, strict payload decoding, a keyed ViewModel, and the browser restore persisted video drafts without creating unrelated UUIDs. |
| Video export work/result | WorkManager plus Room | Recreated UI observes queued/running/terminal state for the exact work ID; stale worker transitions are rejected. |
| Notification destination | Strict explicit `MainActivity` intent | Exact action, destination, data URI, and canonical project UUID are validated before routing. |

The video project uses a custom URL-encoded, tab/newline-delimited positional blob inside Room. Modern nonblank payloads now decode all-or-nothing; malformed/unknown/duplicate/invalid rows are surfaced as corrupt and are never normalized or rewritten. A blank modern payload is a valid empty draft, while `NULL` retains legacy clip fallback. There is still no schema-level media-item table.

## Risk register

Risks are intentionally ranked in the required product-safety order.

1. **Crash or data loss.** Photo/resize recovery is implemented, but controlled OS process death remains unverified; video selection is unbounded and large bitmap outputs can exhaust memory. Active video recovery is implemented. No reproducible baseline crash was found in tests.
2. **Invalid/corrupt export.** Photo, resize, and video publication now check write/encode/publish results and roll back the exact row after caught in-process failure/cancellation, but process death/provider rollback failure can bypass cleanup. Video still has no free-space, codec, or playable-output integrity validation. A post-save history-write exception can also report photo failure after a valid file exists.
3. **Preview/export mismatch.** Photo effect geometry and resize Fit/Fill geometry share their export calculations, but actual renderer pixels, the before/after dark-theme divider color, and video frames still need visual comparison.
4. **Video inconsistency.** The primary preview/export contract is aligned; remaining risk is unverified rotation, codecs, audio boundaries, final-frame behavior, and retained legacy split-state fields.
5. **Project recovery failure.** All destinations and photo/resize/video working state have restoration paths, but the narrow Room-queued-before-WorkManager-enqueue window is not reconciled, revoked/cloud grants need real-provider validation, and OEM/system process restoration still needs manual validation.
6. **Ad policy risk.** UMP gating is present, but app-open timing, deployed privacy/data declarations, age positioning, and real native rendering still need verification.
7. **Discoverability and UX.** Migrated video projects initially use placeholder thumbnails, and live viewer/share target behavior still depends on installed external applications.
8. **Creative enhancements.** Social presets, batch/target-size resize, true video collage, and richer discovery are lower priority than correctness.

## Quality baseline

- `debug` and `release` are the only build types; there are no flavors. Release code/resource minification is disabled, and no repository CI workflow was found.
- The current JVM suite has 248 tests across 34 classes, including first-session app-open gating,
  photo geometry, publication, recovery, persistence, templates, and video work ownership. It still
  does not render/encode a real bitmap or Media3 output.
- Fourteen Android test files provide 42 tests. All 42 pass on a physical API 36 Samsung, including
  first-session marker persistence, activity recreation, current Room migration coverage, Home,
  template discovery, editor navigation, privacy restoration, resize reachability, video sharing,
  and accessibility-state assertions.
- Debug lint reports 0 errors, 127 warnings, and 1 hint. The remaining findings are non-blocking
  resource, API-style, localization, deprecation, and dependency-update guidance.
- There is no lint baseline, historical v1→v4 migration fixture, controlled process-death test, real revoked/cloud URI or ContentResolver/MediaStore fault-injection test, real JPEG/PNG/WebP integrity/alpha test, real MP4 integrity test, or ad/consent lifecycle test.

## Evidence boundaries

Source inspection cannot verify OEM Photo Picker grants, device codecs, HDR tone mapping, mixed-codec/audio continuity, low-storage failure behavior, actual MediaStore corruption visibility, process killing by different OEMs, Play Console declarations, deployed privacy URLs, or live ad rendering. These are explicit manual-test items rather than assumed functionality.
