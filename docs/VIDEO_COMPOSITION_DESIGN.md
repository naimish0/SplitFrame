# Video Composition Design

## Shared Geometry

- A video project owns a `LayoutTemplate` derived from the existing `SIDE_BY_SIDE` or `TOP_BOTTOM` templates.
- `VideoCanvasAspectRatio` determines the template aspect ratio: `16:9`, `9:16`, `1:1`, or `4:5`.
- `LayoutMath.cellFrame` remains the single source for preview and export cell rectangles.
- Crop, zoom, and pan reuse `ImageTransform`; rotated metadata dimensions are normalized before layout math.

## Preview

- The editor preview uses two ExoPlayer instances instead of relying on multi-asset composition preview.
- Clip 0 is the master clock unless it is missing; all play, pause, and seek actions are routed through the ViewModel/coordinator.
- Both players seek to trim-relative positions.
- Secondary drift is corrected only when it exceeds a threshold to avoid seek thrashing.
- Only the selected primary-audio player is audible; unavailable audio options are disabled.
- Preview surfaces use Media3 Compose `PlayerSurface` with texture surfaces for Compose clipping.

## Export

- Export runs outside the Activity through WorkManager with unique work per project.
- Transformer work is isolated to `VideoExportRepository` and `VideoExportWorker`.
- Output starts in app-controlled temporary storage and is published to `MediaStore.Video` after success.
- `IS_PENDING` is used on API 29+ while publishing the MP4.
- The exporter computes output size from `ExportResolution` and `VideoCanvasAspectRatio`.
- Duration mode `SHORTEST` clips both sequences to the shorter trimmed duration.
- Duration mode `LONGEST_FREEZE_SHORTER` targets the longest trimmed duration. If Media3 does not hold the final frame for the shorter sequence on device, the next implementation step is a generated final-frame still segment.
- HDR inputs are tone-mapped to SDR by default where Media3 support is available; preserve-HDR multi-video export is out of scope.

## Error Model

- User-facing errors are stored as string resources and technical failures are logged.
- Recoverable errors preserve valid clips.
- Export cancellation removes temporary output.
- Unsupported 4K or encoder initialization failure returns a clear export error instead of crashing; proactive per-device disabling is still pending.
