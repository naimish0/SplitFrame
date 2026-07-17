# SplitFrame Mixed Media Implementation Plan

Date: 2026-07-17

## Direction

Use one project model for image/video cells and one layout system for all modes. The mixed-media editor should reuse Phase 1 `LayoutTemplate`, `LayoutMath`, `ImageTransform`, and export-resolution behavior, while preserving the existing Phase 2 WorkManager/Media3 export boundary.

## Implementation Steps

1. Extend domain models.
   - Add a sealed mixed-media source for images and videos.
   - Add `MediaDurationMode` with `LOOP_SHORTER` as the default.
   - Add helpers for template selection, required cells, project completion, output duration, looped video position, and selected audio lookup.
   - Keep existing `VideoClip`, `VideoFitMode`, and `VideoCanvasAspectRatio` where they are already used.

2. Extend persistence without destructive migration.
   - Add `templateId`, `selectedCellIndex`, `primaryAudioMediaId`, and `mediaItems` to `video_projects`.
   - Bump Room to version 3 and add a `2 -> 3` migration.
   - Decode legacy `clip0`/`clip1` when `mediaItems` is absent.

3. Update metadata intake.
   - Change video editor picker to image-and-video with a maximum of nine items.
   - Use `ContentResolver.getType(...)` to route images to `ImageSourceReader` and videos to `VideoMetadataReader`.
   - Preserve valid existing cells when an individual URI fails.

4. Update MVI and reducer behavior.
   - Rename/add generic media intents while keeping old video-named intents as compatibility wrappers.
   - Support add, replace, remove, select, template selection, swap, auto-arrange, reorder, trim, transform, fit/fill, duration, resolution, and audio selection.
   - Keep undo/redo snapshot behavior.

5. Update preview UI.
   - Move the preview outside the scrollable controls.
   - Use a portrait structure with fixed preview, scrollable controls, and persistent export action.
   - Use a wide layout with fixed preview left and independently scrolling controls right.
   - Render image cells and video cells in the same normalized template.

6. Update player coordination.
   - Create/reuse a dynamic player map keyed by media id.
   - Use one project timeline; map timeline position to each video trim range with looping.
   - Auto-replay only when the user had playback active.
   - Mute every non-primary audio player.
   - Limit live preview to a conservative number of simultaneous videos and keep poster cells/editing available beyond that.

7. Update export.
   - If a project has no video, keep using the photo workflow rather than forcing MP4.
   - If at least one video exists, build a Media3 composition with one visual sequence per cell and optional selected audio.
   - Use image inputs with `MediaItem.imageDurationMs` for static cells.
   - Loop shorter visual and selected-audio sequences to the finite project output duration.
   - Keep placement from the same normalized template used by preview.

8. Add focused tests.
   - Cover 2-9 template compatibility, mixed assignment, swap/reorder, duration calculation, loop-position mapping, audio source selection, and output sizing.
   - Extend reducer tests where existing test scaffolding makes that practical.

9. Verify.
   - Run formatting/compilation via Gradle tasks.
   - Run unit tests, lint, debug assemble, and available connected tests.
   - Record any remaining device-specific validation gaps.

## Constraints

- No dependency upgrades unless compilation proves the current Media3 version lacks required APIs.
- No duplicate layout catalogs for photo/video/mixed media.
- No broad storage permission.
- No FFmpeg.
- No commit or push.
