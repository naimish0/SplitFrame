# SplitFrame Handoff

## Verified architecture

- Single Android `:app` module; Kotlin, Compose Material 3, coroutines/StateFlow, Koin, Room, WorkManager, Coil, Media3 ExoPlayer/Transformer, Google Mobile Ads, and UMP. `minSdk` 24, `targetSdk` 36, debug/release build types, no flavors.
- Manual saveable `AppRoute` navigation restores Home/Template callers, photo editor/resize destinations, and exact video project UUIDs. ViewModels own domain state; `SavedStateHandle`, preferences, and Room store stable primitives only.
- Core collage, resize, video edit/save/export are local and remain usable without network. Network use is limited to consent/advertising SDK behavior.
- Room version 6 stores preferences, export history, favorite/recent layouts, video project/work payloads, and recent-project metadata. The verified migration chain is v4→v5→v6.
- Home exposes Create Collage, Resize Image, and Merge Videos before conditional Continue Editing, Recent Projects, Favorites, and Recent Layouts; empty rails are omitted.
- The canonical template repository contains 110 layouts. Discovery uses search, verified metadata filters, favorites, stable lazy keys, and deterministic offline recommendation scoring. Every card responds: exact/larger layouts preserve all selected images, while smaller layouts stay unapplied and explain that extra photos must be removed. Selection/navigation alone never updates recents; the first substantive media/editor action or successful export does.
- Single-image export centralizes 13 presets: six fixed social targets, WhatsApp Status, runtime wallpaper, Custom, and five original-aspect choices. Canvas dimensions, Fit/Fill, format, and quality are separate persisted values.
- Photo preview/export share canvas, cell, spacing, corner, border, crop/pan/zoom, shape, text, background, and EXIF-oriented geometry. Output allocation is capped at 24 MP.
- Photo creative state supports system-font text/effects/transforms, solid/linear/radial/media-blur/pattern backgrounds, solid/gradient/dashed borders, rectangle/circle/heart/hexagon/star masks, deterministic Auto Arrange, atomic drag/swap, and gesture-grouped undo.
- Photo active-draft persistence uses a strict versioned URL-safe Base64 primitive codec in the existing preference table plus SavedStateHandle. Missing media blocks export and remains repairable; malformed drafts require reset.
- Resize result restoration requires a readable output URI and matching actual dimensions, format, quality, and Fit/Fill semantics. Comparison uses actual source/output metadata.
- Video preview/export implement the accepted sequential contract: one full-frame trimmed clip at a time, ordered cumulative timeline, per-segment clip audio, crop-fill transforms, H.264/AAC MP4, and no silent encoder fallback.
- Video export preflights current URIs/metadata, maps codec/storage failures, validates a non-empty playable video track/duration, sweeps stale cache files, and publishes only under exact work ownership.
- Global and project-local video recovery compare exact WorkManager UUID/state. Startup cut-off and a five-second enqueue grace prevent recovery from invalidating fresh work.
- Photo/resize/video MediaStore transactions provide exact-row caught-failure/cancellation rollback. They are not durable across OS process death.
- One Koin ad manager centralizes consent, full-screen state, workflow interstitial quota, app-open opportunities, export/external-UI suppression, and cross-format separation.
- App-open permits launcher cold starts within 1.5 seconds and qualified returns within 750 ms after 30 seconds background, with a four-hour show cap. Its cold surface uses a Compose-compatible raster logo.
- Embedded ads use adaptive anchored banners on safe browsing screens and capped native insertion after organic Template/Recent content. Lifecycle owners destroy replaced/removed SDK objects.
- Debug/testing ad units use official Google test inventory. Production IDs, signing, dependencies, and secrets were not changed.

## Important file/module map

- Shell/navigation/lifecycle: `MainActivity.kt`, `presentation/SplitFrameApp.kt`, `presentation/ModeSelectionScreen.kt`, `presentation/home/*`.
- DI/application: `SplitFrameApplication.kt`, `di/AppModule.kt`.
- Photo domain/state/UI/render/export: `domain/Models.kt`, `domain/Geometry.kt`, `domain/CreativeTools.kt`, `presentation/merge/*`, `render/CollageCreativeRenderer.kt`, `export/ImageSourceReader.kt`, `export/ImageExportRepository.kt`.
- Templates/discovery/ranking: `domain/Templates.kt`, `domain/TemplateCatalog.kt`, `domain/TemplateDiscovery.kt`, `domain/SmartLayoutRecommendations.kt`.
- Resize: `domain/SingleImageModels.kt`, `domain/ExportPresets.kt`, `presentation/single/*`, `export/SingleImageProcessingRepository.kt`.
- Video: `domain/VideoModels.kt`, `presentation/video/*`, `data/VideoProjectStore.kt`, `data/RecentVideoProjectStore.kt`, `export/VideoExportRepository.kt`, `export/VideoExportWorker.kt`, `export/VideoExportRecoveryCoordinator.kt`.
- Persistence: `data/ProjectStore.kt`, `data/PhotoDraftCodec.kt`, `data/local/Entities.kt`, `data/local/Daos.kt`, `data/local/SplitFrameDatabase.kt`.
- Ads: `ads/AdsConfigRepository.kt`, `ads/SplitFrameAdManager.kt`, `ads/AppOpenAdPolicy.kt`, `ads/WorkflowInterstitialPolicy.kt`, `ads/EmbeddedAdPolicy.kt`, `ads/BannerAd.kt`, `ads/NativeAdvancedAd.kt`.
- Release evidence: `docs/qa/release-readiness-report.md`, `docs/qa/manual-test-matrix.md`, `docs/product/play-store-positioning.md`.

## Current git baseline

- Work is on local `main` with a large uncommitted stack spanning Cycles 0–10. No commit, push, branch, remote, dependency, production ID, signing, or secret change was made.
- The original user-owned `TemplatePickerScreen.kt` change that keeps every template visible was preserved. Exact and larger layouts are actionable; smaller layouts also expose a click action for feedback but cannot apply because the current model has no safe overflow-media store.
- All unrelated working-tree changes must continue to be preserved. Generated build output is not part of the intended diff.

## Completed cycles

- Cycle 0: repository/product/media/quality/ads/competitor/monetization audit and baseline docs.
- Cycle 1: accepted sequential video merge ADR, then implemented one-player sequential preview/timeline parity.
- Recovery/export integrity continuations: exact video routing/work ownership plus caught-failure publication transactions for collage, resize, and video.
- Cycle 2: shared photo geometry and output-dimension parity.
- Cycle 3: recent video projects, rename/duplicate/delete/Undo, missing/corrupt states, Room v4→v5, and process recreation.
- Cycle 4: Home dashboard and primary activation.
- Cycle 5: template search/filters/favorites/meaningful recents and Room v5→v6.
- Cycle 6A: deterministic offline layout recommendations.
- Cycle 6B: centralized export presets, Fit/Fill, persistence, and shared preview/export ratio.
- Cycle 7A: central consent/full-screen eligibility and every-second-success workflow interstitials.
- Cycle 7B: cold/foreground app-open policy, preload, bounded launch window, suppression, lifecycle safety, and shared full-screen state.
- Cycle 7C: adaptive browsing banners and capped native feed insertion with explicit lifecycle ownership.
- Cycle 8: Text, Backgrounds, Borders, Shape Crops, Auto Arrange, Drag/Drop, and actual Resize Comparison with persistence/restoration/shared rendering.
- Cycle 9: strict restoration, missing-media export guards, cancellation/resource/bitmap hardening, output pixel cap, actionable storage/codec errors, video preflight/playability/temp cleanup, and race-safe work recovery.
- Cycle 10: combined release verification, app-open loading crash fix, release/manual/positioning docs, and blocker classification.
- Post-Cycle 10 layout fixes: removed the favorite-overlay dead tap area; preserved selected images across exact/larger layout changes and partial-layout restoration; prevented smaller layouts from dropping media; and changed recents to require a substantive action on the active layout or successful export.

## Decisions and ADRs

- `docs/architecture/adr-video-composition-direction.md`: existing Video Merge remains sequential. Simultaneous split-screen/video collage is a separate future product requiring explicit authority and a new compositor contract.
- Existing Room fields for video template/duration/primary audio remain compatibility data and do not change sequential playback/export.
- Encoder fallback stays disabled because Media3 fallback can silently change the requested output contract; unsupported devices receive actionable lower-resolution guidance.
- Crop transforms follow media during Auto Arrange/drag; crop shapes remain attached to layout cells.
- No AI/network service is used for recommendations and no personal-media metadata is persisted for ads/analytics by recommendation code.

## Known baseline failures

- Final publish signing is external/unconfigured; `assembleRelease` produces build evidence, not a publish-signed artifact.
- MediaStore rollback is in-process. Process death can leave a pending row on API 29+ or a visible partial/orphan row on API 24–28.
- Direct historical Room migrations from v1–v3 lack fixture coverage; confirm whether those schemas shipped.
- Connected API 36 execution found and prompted the app-open logo crash fix. The post-fix suite now
  passes all 39 instrumentation tests on the physical Samsung device.
- Debug lint has 0 errors and 124 warnings/1 hint, mainly existing resource, deprecation, plural/KTX, and dependency-update findings.
- Release minification/resource shrinking are disabled by existing configuration.
- Template discovery and resize instrumentation now use the actual labels, explicit nested-row
  scrolling, unique semantics, and explicit vertical scrolling; the prior four brittle assertions
  pass in both focused and full API 36 runs.

## Current risks

1. Crash/data loss: controlled process death and provider revocation remain unverified; historical migration exposure is unknown.
2. Invalid/corrupt export: OS death can bypass MediaStore rollback; real low-storage/provider fault injection is absent.
3. Preview/export mismatch: no decoded-output versus Compose pixel-tolerance suite exists for creative effects.
4. Video inconsistency: rotation, HDR, codec, audio boundaries, and final frames need real-media API 24+ qualification.
5. Project recovery: permission continuations and delete-Undo/process-kill windows need device coverage.
6. Ad policy/lifecycle: UMP/SDK callbacks, no-fill, AdChoices, click returns, and console declarations need real-device/console validation.
7. Discoverability/UX: TalkBack, 200% font, themes, insets, compact/tablet/foldable layouts need runtime verification.
8. Creative enhancements: batch resize, target file size, true video collage, cloud sync, and background removal remain out of scope.

## Pending work

- Execute every P0 row in `docs/qa/manual-test-matrix.md` on API 24/28, 29, 33, and 36, including network-off and limited-storage cases.
- Decide whether to implement durable MediaStore publication journaling/reconciliation or explicitly accept the process-death risk.
- Confirm shipped Room versions; add v1/v2/v3 fixtures when required.
- Add real image golden/pixel tests and representative video codec/rotation/HDR/audio fixtures.
- Validate UMP test geographies, app-open/interstitial separation, banners/native with Native Validator, TalkBack/font/theme/insets, and Play declarations.
- Produce and inspect the final publisher-signed AAB; verify versioning, third-party notices, privacy/Data safety, Advertising ID, target audience, and foreground-service declarations.

## Tests already available

- JVM: 245 test methods across 34 files, covering geometry, presets, resize stats, strict codecs, templates/ranking/application/use policy, navigation/state, all ad policies, export transactions/failure mapping, recovery, output caps, and work ownership.
- Instrumentation: 39 methods across 13 files, covering first-session app-open persistence,
  Room/current migration chain, recents, route/activity recreation, Home, template discovery/card
  hit targets/count handling, editor media visibility, resize reachability, and durable
  interstitial preferences. All 39 pass on a physical API 36 Samsung.
- Missing: controlled OS death, real provider revocation/cloud media, historical v1→v3 migrations, actual MediaStore interruption, image goldens, Transformer media fixtures, real UMP/AdMob lifecycle, and accessibility/form-factor automation.

## Commands that passed or failed

- Passed: focused Cycle 7B app-open/interstitial/store suite — 38 JVM tests; debug and Android-test compilation.
- Passed: focused Cycle 7C embedded-ad suite — 9 JVM tests; debug and Android-test compilation.
- Passed: focused Cycle 8 creative/draft/route/resize suite; production and all instrumentation sources compiled.
- Passed: focused Cycle 9 strict draft/store/undo/resize/image/video recovery/failure suite and affected debug compilation.
- Initial Cycle 10 combined JVM run: 235 tests, one new photo pixel-cap rounding failure. `PhotoGeometryParityTest` passed after truncation-safe clamping; both later video fallback/recovery tests passed targeted reruns.
- Passed: `:app:lintDebug` — 0 errors, 124 warnings, 1 hint after API-24 Base64 and Media3 opt-in fixes.
- Passed twice: fresh `:app:assembleRelease`; final run includes all production fixes and release lint-vital.
- Connected API 36 failed first on a real app-open cold-surface crash caused by an inset drawable;
  production now uses a raster resource and the full 39-test rerun passes.
- Connected post-fix attempts did not produce product assertion evidence: the device was screen-off/dozing, then wireless ADB became offline. Android-test compilation remained successful.
- Passed: targeted recovery policy after startup-cutoff/enqueue-grace change; targeted video transaction after disabling encoder fallback.
- Passed: 14 focused template discovery/favorite/recent-use JVM tests; debug production and Android-test compilation.
- Passed: focused layout-card and favorite-action tap tests on an API 37 emulator and API 36 Samsung device, including the disabled-favorite overlay hit region.
- Passed: all template-discovery and resize UI checks after replacing brittle nested-scroll,
  label, visibility, and non-unique matcher assumptions.
- Passed: 35 focused layout application, action-qualified recents, draft restoration, discovery, catalog, and favorite JVM tests.
- Passed: seven focused template-card/count/error/favorite/editor-media-visibility UI tests on both an API 37 emulator and API 36 Samsung device; production and Android-test compilation passed.

## Next recommended slice

- Release-blocking validation slice: run the P0 process-death/MediaStore and real codec/provider
  matrix across the required API levels. Do not add product features.
- If process-death output artifacts reproduce, propose a small durable publication journal/reconciler design for explicit approval because it affects persistence and cleanup authority.
