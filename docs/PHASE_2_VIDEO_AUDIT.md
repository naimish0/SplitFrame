# Phase 2 Video Audit

## Baseline Checks

- `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` passed before Phase 2 edits.
- `./gradlew :app:connectedDebugAndroidTest` passed on attached Android 16 / SDK 36 device before Phase 2 edits.
- Existing connected-test warning: `createComposeRule` deprecation in `ExportSheetTest.kt`.
- Existing environment warning: `android.disallowKotlinSourceSets=false` is experimental.

## Existing Architecture

- UI is native Compose with manual screen state in `SplitFrameApp`.
- Photo collage uses `MergeIntent -> MergeAction -> MergeResultEvent -> MergeState` in `MergeViewModel`.
- Undo/redo is an in-memory `ArrayDeque` of project snapshots.
- Koin wires app singletons and ViewModels in `AppModule`.
- Room currently stores preferences, export history, and favorite templates only; collage projects are not persisted.
- AdMob interstitial is shown from the foreground UI after a successful photo export.

## Reusable Phase 1 Pieces

- `LayoutTemplate`, `LayoutCell`, and `NormalizedRect` already represent normalized cell geometry.
- `TemplateIds.SIDE_BY_SIDE` and `TemplateIds.TOP_BOTTOM` exactly match Phase 2 video layouts.
- `LayoutMath.cellFrame` applies shared normalized layout plus spacing.
- `LayoutMath.cropToFillSourceRect`, `transformAfterGesture`, and `transformAfterDoubleTap` provide crop-fill transform behavior.
- `ImageTransform` can be reused as a generic crop/zoom/pan model for video.
- `ExportResolution` already defines 480p, 720p, 1080p, 1440p, 4K, and Original.
- `ImageExportRepository` demonstrates current MediaStore save flow, including `IS_PENDING`.
- `SplitFrameTopAppBar`, `SplitFrameSection`, action buttons, `StatusMessage`, and theme tokens are reusable.

## Current Gaps For Video

- No Media3 dependencies are present.
- No WorkManager dependency is present.
- No persisted project model exists for process recreation.
- No video metadata reader exists.
- No video preview/player boundary exists.
- No background export queue or durable export-progress state exists.
- Current export result is image-specific enough that video needs its own repository/state.

## Official Documentation Consulted

- Media3 release page lists stable `1.10.1` as the latest stable release as of July 7, 2026.
- Media3 Transformer docs state Transformer is for transcoding, trimming, cropping, and editing, and is implemented with MediaCodec and OpenGL.
- Media3 Transformer getting-started docs require all Media3 artifacts to use the same version.
- Media3 Compose UI docs expose `ContentFrame` and `PlayerSurface` for custom Compose playback surfaces.
- Media3 surface docs describe `SURFACE_TYPE_SURFACE_VIEW` and `SURFACE_TYPE_TEXTURE_VIEW`.
- Media3 editing docs recommend OpenGL HDR-to-SDR tone mapping where available.
- Photo Picker docs support `PickMultipleVisualMedia` and `PickVisualMedia.VideoOnly`, and describe persistable URI access for long-running work.
- WorkManager docs recommend unique work for avoiding duplicate jobs and foreground workers for long-running work.

