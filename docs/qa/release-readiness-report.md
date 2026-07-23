# SplitFrame Release Readiness Report

Date: 2026-07-23
Scope: Current local `main` through the Home history, route-restoration, and video-sharing completion.

Status values are **Passed**, **Failed**, **Blocked**, **Manual verification required**, and **Not applicable**.

## Release decision

**Not ready for Play publication.** The current source compiles as a release APK, focused and
combined JVM evidence passes, lint has no errors, and all 44 instrumentation tests pass on a
physical API 36 Samsung. Publication remains blocked by external release signing. Process-death
MediaStore durability, real codecs/providers, UMP/AdMob callbacks, accessibility, and broader
API-level coverage also remain manual release gates.

## Build and code

| Check | Status | Evidence |
|---|---|---|
| Debug production compilation | Passed | Repeated focused JVM/device builds compiled current debug source. |
| Release compilation/package | Passed | Fresh `:app:assembleRelease`; release lint-vital also passed. |
| JVM tests | Passed | 249 current test methods across 34 files, including route restoration, recent-export state mapping, and frame-rate-aware MP4 estimation. |
| Static analysis | Passed | `:app:lintDebug`: 0 errors, 127 warnings, 1 hint. |
| Instrumentation source compilation | Passed | 44 tests across 14 files compile. |
| Connected instrumentation | Passed on API 36 | All 44 tests pass on a physical Samsung API 36 phone; the TV device was explicitly excluded. |
| Accidental debug code | Passed | No new `BuildConfig.DEBUG` bypass, localhost endpoint, test-only import in main, stack trace printing, or test-device override was found. |
| Credentials/secrets | Passed | No credential-shaped material or signing secret was added; production identifiers were not changed or copied into docs. |
| Ad ID routing | Passed | Debug ad units remain official Google test inventory; release units remain the unmodified production configuration. |
| Dependencies/licenses introduced | Not applicable | These cycles added no dependency or repository. Final SDK-index/third-party notice review remains publisher-owned. |
| Publish signing | Blocked | Repository release signing is intentionally unconfigured; final signed AAB verification requires the release owner. |
| Versioning/minification | Manual verification required | Confirm store version values and publisher decision; release minification/resource shrinking remain disabled. |

## Persistence and recovery

| Check | Status | Evidence |
|---|---|---|
| Room current schema and v4→v5→v6 chain | Passed | DAO/current-schema and chained migration instrumentation exist; last executed migration suite passed before this final cycle. |
| Historical v1→v4 upgrades | Blocked | No fixture-backed direct migration coverage. Confirm whether those versions reached users before release. |
| Existing video-project compatibility | Passed | Legacy-null versus modern-blank decoding behavior is retained; blank projects are hidden from recent surfaces, while malformed payloads and unknown modern enums remain actionable corrupt records instead of being silently normalized. |
| Untouched video sessions | Passed | New project IDs remain transient until valid media is persisted; abandoning the editor creates no recent-project entry, and historical empty records are hidden. |
| Rename/duplicate/delete/Undo | Passed | DAO/domain implementation and tests exist; real lifecycle/provider matrix remains manual. |
| Photo active draft | Passed | Strict versioned primitive codec, preferences, SavedStateHandle, malformed-draft blocking, and missing-media repair state. |
| Resize restoration | Passed | Source, request, actual output metadata, output readability, and Fit/Fill semantics are validated before result restoration. |
| Video work restoration | Passed | Exact project/work identity, global orphan audit, live-state checks, and enqueue grace/startup cut-off prevent stale or freshly queued rows from being invalidated. |
| Controlled OS process death | Manual verification required | Activity recreation is covered; OS kill during editor, enqueue, publication, and delete-Undo windows is not automated. |
| Missing/revoked/cloud media | Manual verification required | Source validation is graceful and export is blocked, but real provider revocation and cloud hydration need device tests. |
| Corrupt project rows | Passed | Photo/video decoding is all-or-nothing with actionable recovery/reset paths. |

## Media and export

| Check | Status | Evidence |
|---|---|---|
| Photo geometry/state parity | Passed | Shared canvas/cell/style calculations cover dimensions, spacing, corners, borders, transforms, shapes, text, and backgrounds. |
| Resize Fit/Fill parity | Passed | Preview and export share normalized geometry and target aspect; fixed/custom preset tests pass. |
| Text/background/border/shape persistence | Passed | Stable domain state and strict codec cover restoration without Compose/Bitmap/runtime objects. |
| Preview/export pixel tolerance | Manual verification required | No golden screenshot versus decoded-output suite exists. |
| Video sequence semantics | Passed | Preview and export remain sequential, trimmed, ordered, crop-fill, and per-clip audio; legacy primary-audio state is inaccessible compatibility data. |
| Video requested codec/size contract | Passed | H.264/AAC is explicit, silent encoder fallback is disabled, and unsupported encoders map to actionable errors. |
| Video estimated size | Passed | Calculation includes output pixels, per-clip trimmed duration/frame rate, AAC audio, and MP4 overhead. Five-minute 1080p/30 fps regression coverage yields roughly 305 MB instead of the prior 8 MB under-estimate. |
| Video playable-output validation | Passed | Cache MP4 must be non-empty with a video track and valid known duration before publication. |
| Codec/rotation/HDR/audio boundary parity | Manual verification required | Requires representative real media on API 24+. |
| Caught failure/cancellation cleanup | Passed | Photo, resize, and video transactions roll back exact MediaStore entries; bitmap/codecs/files are released best-effort. |
| Process-death publication cleanup | Failed | In-process `finally`/rollback cannot run after process death. API 29+ may retain a pending row; API 24–28 may expose a partial row. A durable publication journal/reconciler is not implemented. |
| Video retry duplication | Passed | Exact work-ID/state compare-and-set and final publication ownership prevent ordinary stale-worker completion. |
| Stale video temp files | Passed | Conservative age-bounded cache sweep plus per-run cleanup is implemented. |
| Out-of-storage/provider faults | Manual verification required | Error mapping is actionable; real ContentResolver and low-storage injection remain unexecuted. |
| Offline core operation | Manual verification required | Source architecture is local-only, but all three workflows still need a network-off device pass. |

## UX and accessibility

| Check | Status | Evidence |
|---|---|---|
| Home primary actions/state | Passed | Create Collage, Resize Image, and Merge Videos are first; empty rails are omitted. |
| Recent photo exports | Passed | Valid Room history appears conditionally on Home and dispatches the exact saved URI to an installed image viewer; invalid history is excluded. |
| Template discovery | Passed | Search, composable filters, favorites, recents, stable keys, and empty/error states are implemented. |
| Smart recommendations | Passed | Pure deterministic offline ranking is explainable and preserves access to all valid layouts. |
| Export presets | Passed | Centralized fixed/social/wallpaper/custom dimensions with explicit Fit/Fill and validation. |
| Creative tools | Passed | Text, four background families, three border families, five crop shapes, Auto Arrange, drag/swap accessibility actions, and actual resize comparison are reachable. |
| Back/navigation restoration | Passed | Saveable routes preserve every reachable destination, including Privacy, Home/Template callers, and exact video projects. |
| Video result sharing | Passed | Successful export exposes a chooser-backed `video/mp4` share intent with the exact content URI, clip data, and read grant. |
| Light/dark, large font, TalkBack, touch, insets | Manual verification required | Source uses Material 3 semantics/targets/insets; runtime focus order, clipping, contrast, 200% font, and form-factor coverage remain open. |
| Loading/empty/error states | Passed | Implemented across Home, discovery, recents, editors, resize, and ads. Video Projects now renders an explicit loading state and cannot flash the empty state before its first database emission. |

## Advertising and privacy

| Check | Status | Evidence |
|---|---|---|
| UMP eligibility/privacy entry | Passed | All formats share consent state; privacy options are exposed when required. Real geography/console behavior is manual. |
| Workflow interstitials | Passed | Stable workflow IDs count committed successes only; one opportunity follows every second unique success and never blocks output. |
| App-open policy | Passed | First-session exclusion, cold/qualified return windows, four-hour cap, external-UI/export/config suppression, late-ad expiry, and full-screen separation are centralized. |
| App-open loading surface | Passed | Device testing found the inset-drawable crash; it now uses a Compose-compatible raster logo, and the current 44-test API 36 suite passes. |
| Banner lifecycle/placement | Passed | One adaptive anchored banner is limited to safe browsing surfaces with reserved layout and lifecycle disposal. |
| Native lifecycle/placement | Passed | Stable insertion after organic content, caps, attribution/AdChoices, and explicit destruction are implemented. |
| Real SDK/UMP/no-fill/click callbacks | Manual verification required | Run with official test inventory and UMP test geography on physical devices. |
| Advertising ID/Data safety/target audience | Manual verification required | Confirm actual merged SDK behavior and Play Console declarations with the release owner. |
| Ads obstruct offline/core work | Passed in source | Ad absence/failure never gates editor, save, export, or result access; runtime network-off verification remains required. |

## Release blockers and exit criteria

1. Produce and inspect a publisher-signed release AAB with approved versioning.
2. Repeat applicable connected and manual coverage on the required API 24/28, 29, and 33 targets.
3. Resolve or explicitly accept the process-death MediaStore durability risk, especially for API 24–28.
4. Establish whether v1–v3 database versions shipped; add migration fixtures if they did.
5. Complete P0 manual tests in `manual-test-matrix.md` on API 24/28, 29, 33, and 36, including low storage, revoked URIs, offline use, and representative codecs.
6. Complete UMP/AdMob, TalkBack, 200% font, theme, inset, and Play Console checks.
