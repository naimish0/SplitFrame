# SplitFrame Current State

Audited from the working source tree through 2026-07-23. Older phase/design documents were used only as historical context when they agreed with current code.

## Product summary

SplitFrame is an offline-first Android media utility with three reachable workflows:

1. Build a lossless PNG photo collage from a template.
2. Resize one image and save it as JPEG, PNG, or WebP.
3. Join two or more videos, in order, into one H.264/AAC MP4.

The home screen exposes Photo Collage and Video Merge. Resize is reachable from an icon on the template-picker toolbar. The app has no account, cloud project, backend processing, subscription, or purchase flow.

## Shared-contract profile notes

- Localization is `MULTILINGUAL`: English (`en`) is the default and the app also ships German,
  French, Japanese, Hindi, Russian, Spanish, Portuguese (Portugal), Portuguese (Brazil), Italian,
  Indonesian, Arabic, Korean, and Urdu. Settings provides system-default and explicit per-app
  language choices. Arabic and Urdu use Android RTL layout direction. Release App Bundles disable
  language resource splitting so every listed language is installed in the base APK and the
  in-app picker works immediately and offline after Google Play delivery.
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
| Home, Settings, and privacy entry | Implemented | Photo and video cards plus Settings, its 14-language selector, and the in-app privacy page are reachable. |
| App navigation | Implemented | Saveable routes restore Home, Settings, template discovery and its caller, photo and resize editors, Privacy, the video-project browser, and the exact active video editor/project. |
| Template engine | Implemented | 110 runtime templates: 35 explicit plus 75 generated layouts covering 1–15 slots across five variants. |
| Template discovery | Implemented | Search, collections, favorites, recents, slot-count/aspect filters, human-readable generated names, and an explicitly watermarked synthetic sample collage are reachable. |
| Photo selection | Implemented | Android image Photo Picker, up to 15 images, ordered assignment, replacement, MIME validation, sampled decode, and EXIF orientation. |
| Collage editing | Implemented | Crop-to-fill pan/zoom, swap/reorder, spacing, rounded corners, background choices, adaptive gradient, before/after divider, undo/redo. |
| Collage border | Implemented | Reachable border controls update width, color, style, and gradient state shared by preview, export, undo, and draft restoration. |
| Photo export | Partially implemented | Lossless PNG export durably reserves a unique output before insertion and binds its exact MediaStore URI before writing, validates encode success, reopens and bounded-decodes exact dimensions/MIME, then publishes. Resolution choices show deterministic pixel dimensions instead of an unreliable predicted compressed-file size; after publication the UI reports the measured saved-file size. Startup removes an interrupted owned row or retains an already-published output. Preview/export share width-relative geometry and one theme-independent white divider color; pixel parity remains unverified. |
| Photo sharing | Implemented | A successful photo export can be shared; its post-export interstitial is suppressed for that share action. |
| Resize core | Implemented | Social, 1080, 2K, 4K, percentage, 2×, 4×, wallpaper, and custom planning; aspect lock; JPEG/PNG/WebP; 8,192-edge and 24 MP limits. JPEG/WebP can target KB or MB using the highest encoding quality that fits, with an explicit failure when the quality floor cannot meet the target. Named reusable presets persist dimensions, percentage, format, quality, target, metadata, and Fit/Fill settings. A recreation-safe, sequential batch queue accepts up to 20 images, isolates item failures, reports aggregate progress, and supports cancellation. Publication reopens and bounded-decodes the pending output with exact dimensions/MIME before publish and rolls back caught validation/transaction failures. |
| Resize preview | Implemented | Preview and export share the selected Fit/Fill geometry and target aspect, including unlocked custom dimensions; comparison uses actual source/output metadata. |
| Resize metadata | Implemented | Orientation is normalized. The explicit privacy-safe default removes metadata; Preserve details copies a bounded EXIF whitelist (date, camera, copyright, exposure, and GPS) and revalidates target bytes before publication. |
| Video selection | Implemented | Ordered, video-only Photo Picker selection/replacement is reachable from Add Videos, the empty preview card, and empty clip cells; metadata and trim validation are bounded to 20 clips. |
| Video preview | Implemented | One ExoPlayer playlist shows one full-canvas clip at a time with a cumulative trimmed-duration timeline matching export order. |
| Sequential video export | Implemented | Media3 Transformer concatenates trimmed videos in order with Cut or a persisted 250 ms Fade through black. Users can select persisted, user-owned audio that loops to output duration and replaces clip audio; the existing None/source-audio choices now match export. Preflight limits projects to 30 minutes and an estimated 2 GiB, requires an H.264 encoder for the selected dimensions, and requires working storage before rendering. Its foreground Worker validates the cache MP4, durably reserves a unique output before insertion and binds the exact row/cache path before copying, performs an exact-length cancellable MediaStore copy, and commits success only through the exact current Room work row. |
| Video export estimate | Implemented | Estimated MP4 size accounts for output pixels, each clip's trimmed duration and frame rate, AAC audio, and container overhead. It remains an estimate because device encoders may select different effective bitrates. |
| Video fit/transform parity | Cannot verify | Reachable preview is crop-fill only and now derives placement from the export crop model; real-device frame, rotation, and aspect comparison is pending. |
| True video collage/split screen | Not implemented | No multi-sequence compositor or cell geometry is used by export. |
| Mixed image/video composition | Implemented but inaccessible | Models/readers/persistence support images, but UI filters them and export rejects non-video media. |
| Video share action | Implemented | A successful video export exposes an Android share chooser with the exact saved content URI and read grant; absence of a compatible target is recoverable. |
| Favorites | Implemented | Template discovery exposes favorite controls and Home renders the persisted favorites rail when it has data. |
| Recent photo exports | Implemented | Collage and resize/batch successes are recorded in Room. Home reopens the saved output in Resize so it can be inspected, resized, compressed, shared, or re-exported. |
| Recent layouts | Implemented | Substantive template use and successful export update persisted recents; discovery and Home consume the ranked records. |
| Recent video projects | Implemented | Video Merge opens a timestamped local browser with resume, rename, duplicate, API 27+ fixed-size frame thumbnail/placeholder, delete confirmation, token-guarded Undo, and missing/corrupt states. Untouched new sessions remain transient and legacy empty records are hidden. API 24–26 deliberately uses placeholders; photo and resize projects are not included. |
| Photo/resize recovery | Implemented | Photo drafts use a strict Room-backed primitive codec plus `SavedStateHandle`; resize source, batch queue, request, result, Fit/Fill, target, metadata policy, and output metadata restore through saved state and preferences with validity checks. |
| Video project persistence | Implemented | The saveable route carries one canonical project UUID into a project-keyed Koin ViewModel. New sessions remain transient until valid media is persisted, survive recreation with that exact ID, and restore-only sessions never resurrect a missing row. |
| WorkManager video recovery | Implemented | The UI observes the exact project's Room work row across Activity recreation. Work writes are guarded by WorkManager ID and allowed prior state; the final running-to-succeeded update is the cancellation-shielded publication commit. Controlled process-death validation remains manual. |
| Video export notifications | Removed | The app requests no notification permission and creates no completion/failure notifications or notification deep links. Android's required silent foreground-service status exists only while a long-running export is active. |
| Ads and consent | Partially implemented | UMP-gated banner/native/interstitial/app-open formats exist; release-console declarations and real rendering cannot be proved from source. |
| Offline editing/export | Implemented | Core selection, editing, resize, and export do not require a service call. Ads/consent naturally depend on network. |

## Architecture and state flow

- `SplitFrameApp` owns manual navigation. Its saveable route retains every reachable destination, including Privacy, and Koin creates the video ViewModel with an exact project-key and session arguments.
- Photo state follows an intent/state ViewModel pattern, keeps up to 30 undo snapshots, and persists a strict active-draft payload. The template catalog and geometry live in `domain`.
- Resize has its own intent/state ViewModel and repository; its saved state retains the source and Fit/Fill request while comparison uses validated output metadata.
- Video state is persisted through `VideoProjectStore`, while video exports run via WorkManager and `VideoExportWorker`.
- Room version 6 has explicit migrations 1→2 through 5→6. Physical-device fixtures cover the complete 1→6 chain and the 4→6 metadata-backfill path; schema export remains disabled.
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

The video project uses a custom URL-encoded, tab/newline-delimited positional blob inside Room. Modern nonblank payloads now decode all-or-nothing; malformed/unknown/duplicate/invalid rows are surfaced as corrupt and are never normalized or rewritten. A historical blank modern payload remains decodable but is excluded from recent-project surfaces, while `NULL` retains legacy clip fallback. There is still no schema-level media-item table.

## Risk register

Risks are intentionally ranked in the required product-safety order.

1. **Crash or data loss.** Durable exact-output cleanup is implemented for interrupted photo, resize, and video publication, but controlled OS process death remains unverified. Video selection, duration, estimated output size, encoder support, and working storage are bounded before rendering; large bitmap outputs can still exhaust memory. Active video recovery is implemented. No reproducible baseline crash was found in tests.
2. **Invalid/corrupt export.** Photo and resize reopen and bounded-decode pending output with exact
   dimensions/MIME; video validates a non-empty cache MP4 with a video track and valid known
   duration. All three roll back the exact row after caught in-process failure/cancellation, but
   provider deletion failure is retained for a later startup retry. Video still has no free-space or
   proactive codec capability preflight, and full playable frame/audio integrity is unverified. A
   post-save history-write exception can also report photo failure after a valid file exists.
3. **Preview/export mismatch.** Photo effect geometry, the before/after divider color, and resize Fit/Fill geometry share their export contracts, but actual renderer pixels and video frames still need visual comparison.
4. **Video inconsistency.** The primary preview/export contract is aligned; remaining risk is unverified rotation, codecs, audio boundaries, final-frame behavior, and retained legacy split-state fields.
5. **Project recovery failure.** All destinations and photo/resize/video working state have restoration paths, but the narrow Room-queued-before-WorkManager-enqueue window is not reconciled, revoked/cloud grants need real-provider validation, and OEM/system process restoration still needs manual validation.
6. **Ad policy risk.** UMP gating is present, but app-open timing, deployed privacy/data declarations, age positioning, and real native rendering still need verification.
7. **Discoverability and UX.** Migrated video projects initially use placeholder thumbnails, and live viewer/share target behavior still depends on installed external applications.
8. **Creative enhancements.** True video collage, mixed photo/video export, on-device background
   removal, and multilingual release waves remain gated/unimplemented. Batch resize, richer
   discovery/sample content, percentage resize, target-size JPEG/WebP output, named presets,
   EXIF control, user-owned audio, and a basic fade transition are implemented.

## Quality baseline

- `debug` and `release` are the only build types; there are no flavors. Release code/resource minification is disabled, and no repository CI workflow was found.
- The current JVM suite has 282 tests, including target-size encoding, percentage
  planning, saved-preset persistence, first-session app-open gating,
  photo geometry, publication, recovery, persistence, templates, and video work ownership. It still
  does not render a real Media3 output.
- Seventeen Android test files provide 51 connected tests. All 51 pass in three bounded shards on a physical
  Samsung SM-S928B running API 36, including real JPEG/PNG/WebP pending MediaStore rows,
  durable recovery against exact MediaStore rows, H.264/storage preflight, historical migration fixtures, and
  corrupt-byte rejection. Existing coverage includes
  first-session marker persistence, activity recreation, current Room migration coverage, Home,
  template discovery, editor navigation, untouched-video-session abandonment, privacy restoration,
  video-project loading/empty separation, resize reachability, video sharing, and
  accessibility-state assertions.
- Debug lint reports 0 errors, 127 warnings, and 1 hint. The remaining findings are non-blocking
  resource, API-style, localization, deprecation, and dependency-update guidance.
- There is no lint baseline, historical v1→v4 migration fixture, controlled process-death test,
  real revoked/cloud URI or ContentResolver/MediaStore fault-injection test, alpha pixel-parity
  assertion, real MP4 playback-integrity test, or ad/consent lifecycle test.

## Evidence boundaries

Source inspection cannot verify OEM Photo Picker grants, device codecs, HDR tone mapping, mixed-codec/audio continuity, low-storage failure behavior, actual MediaStore corruption visibility, process killing by different OEMs, Play Console declarations, deployed privacy URLs, or live ad rendering. These are explicit manual-test items rather than assumed functionality.
