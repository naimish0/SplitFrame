# Phase 2 Video Plan

## Implementation Shape

1. Add Media3 `1.10.1` dependencies for ExoPlayer, Transformer, effects, and Compose UI.
2. Add WorkManager for durable background export.
3. Add video domain models beside existing domain models, reusing `ImageTransform`, `ExportResolution`, `NormalizedRect`, and `LayoutMath`.
4. Add Room entities/DAOs for video projects and video export work state with migration `1 -> 2`.
5. Add `VideoMetadataReader` using `MediaMetadataRetriever` off the main thread for duration, dimensions, rotation, MIME, size, audio availability, frame rate where available, and HDR hints where available.
6. Add `VideoMergeViewModel` with the same MVI/undo/redo style as Phase 1.
7. Add a mode-selection entry screen with Photo Collage and Video Split Screen without changing the photo editor behavior.
8. Add video template selection and editor screens using existing components.
9. Preview with two synchronized ExoPlayer instances; use one master clock, mute the non-primary clip, and seek/play/pause both together.
10. Export through a `VideoExportRepository` and WorkManager boundary. Start with Media3 Transformer composition where supported by the installed API; keep `@UnstableApi` usage in the video preview/export boundary files.
11. Persist export progress and completion through Room; show foreground notification from the worker and AdMob only from UI after success.
12. Add focused unit tests for layout, output sizing, trim, duration, audio selection, reducer behavior, undo/redo, and migration.

## Scope Decisions

- Exactly two clips only.
- Video layouts map to existing side-by-side and top/bottom normalized cells.
- Output aspect ratios: 16:9, 9:16, 1:1, 4:5.
- Default duration mode: longest with shorter clip held on its final visible frame.
- Version 1 audio: clip 1, clip 2, or no audio. No mixing.
- Default output is SDR MP4 with H.264/AVC and AAC where audio is selected.
- Preview uses `TextureView` surfaces because Compose clipping/transforming is required per cell; this trades some efficiency for correct cell clipping.

## Risks

- Multi-input Media3 Transformer composition APIs and Media3 Compose surfaces are `@UnstableApi`; usage must remain behind small video boundary files.
- Frame freezing for the shorter clip may require a last-frame still segment if Media3 cannot directly hold the final frame in the active API. This needs real-device export validation before it can be called complete.
- Device encoder capability varies; 4K must be disabled or reported unsupported where needed.
- Full export instrumentation needs short fixture videos, which should be generated or kept tiny rather than committed as large binaries.

## Validation

- Keep Phase 1 checks green: unit tests, lint, debug build, connected tests.
- Add video unit tests first for deterministic math and reducer behavior.
- Validate on the attached Android 16 device for preview lifecycle, launch, selection UI, and basic export where possible.
