# SplitFrame Export Pipeline

Audited from production source on 2026-07-20. The three pipelines are independent and remain usable without network access.

## Photo collage

```text
Android Photo Picker
  -> ImageSourceReader (MIME, bounds, sampled decode, EXIF transform)
  -> MergeViewModel project state
  -> MergePreviewCanvas
  -> ImageExportRepository bitmap render
  -> JPEG compression
  -> MediaStore Pictures/SplitFrame
```

### Geometry and rendering

- Preview and export both use `LayoutMath.cropToFillSourceRect` for each image's zoom/pan crop, which is a sound shared basis.
- Templates resolve normalized cells to their respective canvas sizes. Background gradient/solid color, before/after composition, spacing, corners, and border render in both layers.
- Preview and export now call one pure `LayoutMath.collageRenderMetrics` contract for `spacingDp`, `cornerRadiusDp`, `borderWidthDp`, and the before/after divider: `pixels = design value × canvas width / 360`.
- The editor fills available width, so width-relative scaling preserves normalized geometry across square, portrait, landscape, 1080, 2K, and 4K canvases without storing device density or preview size in the project.
- `LayoutMath.cellFrame` continues to share normalized template bounds and outer/full versus inner/half spacing. If extreme spacing would consume a dense cell, inset scaling now preserves 5% of that cell's base width/height instead of returning an inverted or zero-area frame.
- Before/after preview hit testing/rendering and export share the same clamped divider position and proportional thickness. Preview divider color remains theme-surface while export remains white, so dark-theme color parity is still a separate mismatch.
- Border state/rendering exists, but no UI intent changes the transparent zero-width default.

### Output and failure handling

- Export is JPEG with quality 94 in `Pictures/SplitFrame`.
- Android Q+ uses `IS_PENDING`; pre-Q relies on legacy storage permission.
- Publication is ordered as MediaStore insert, required JPEG encode, flush/close, and Android Q+ publish; the publish update must affect exactly one row.
- Every failure after insert triggers best-effort deletion of that exact URI. If deletion also fails, the original encode/write/close/publish failure remains primary and cleanup failure is suppressed.
- The renderer recycles its newly allocated output on an internal exception, and the export boundary recycles the returned bitmap after save success or failure.
- Nine pure JVM tests cover generic transaction ordering/failures, injected transaction cancellation rollback, and JPEG encode/flush behavior. Seven additional pure tests cover canonical metrics, representative output sizes/aspects, normalized cell/effect parity, dense-template bounds, extreme crop, and divider geometry. Concrete open-stream, close, Android API branch, bitmap ownership, process-death, real ContentResolver/provider, and low-storage failure paths are not automated.
- The transaction is in-process: process death after insert bypasses rollback, so API 29+ can retain a pending row until provider cleanup and API 24–28 can expose a partial row.
- After a successful publication, a separate Room export-history insert can still throw in `MergeViewModel`; that currently reports failure even though the file exists and a retry can duplicate it.

Classification: standard collage effect geometry and MediaStore publication are **Implemented** in source; pixel-level renderer parity is **Cannot verify**, and before/after dark-theme divider color remains **Inconsistent or broken**.

## Single-image resize

```text
Android Photo Picker
  -> ImageSourceReader (sampled, EXIF-oriented bitmap)
  -> SingleImageResizePlanner (preset/custom dimensions and limits)
  -> ARGB bitmap/Canvas render to requested width/height
  -> JPEG / PNG / WebP compression
  -> MediaStore Pictures/SplitFrame
```

### Planning and processing

- Presets include 1080, 2K, 4K, 2×, 4×, and custom dimensions.
- Aspect lock derives the other dimension; validation caps an edge at 8,192 pixels and total output at 24 megapixels.
- Processing is conventional bitmap scaling. There is no AI model, enhancement service, or super-resolution pipeline.
- With aspect lock disabled, scaling to arbitrary width/height stretches the complete bitmap.
- EXIF orientation is applied during decode; output EXIF metadata is not copied.
- If EXIF rotation allocation itself throws inside `ImageSourceReader`, its internal decoded bitmap can leak before ownership reaches this repository; that upstream gap remains out of scope.

### Preview mismatch

- Preview zoom and pan are local `rememberSaveable` graphics-layer values.
- They do not enter `SingleImageIntent`, the processing request, or the repository.
- Preview maintains the source aspect presentation even when an unlocked custom export will stretch it.

### Output and failure handling

- JPEG, PNG, and WebP retain their existing MIME types/extensions and clamped quality input. WebP remains legacy `WEBP` on API 24–29 and `WEBP_LOSSY` on API 30+; JPEG retains its white background and PNG/WebP retain alpha-capable rendering.
- Publication is ordered as insert, required encode, explicit flush, successful close, and Android Q+ publish affecting exactly one row. Cancellation is checked before insertion/writing and before/after publish.
- Every caught post-insert write/close/publish/cancellation failure best-effort deletes that exact URI; rollback failure is suppressed without replacing the original.
- Decoded input and rendered output ownership is identity-aware. Render failures recycle their owned output, and best-effort `finally` cleanup cannot turn a valid save into a reported failure.
- Thirteen JVM tests cover generic transaction ordering, encode/flush/close/publish failure, rollback preservation, API-specific format selection, and distinct/aliased cleanup. Actual Bitmap/ContentResolver encoding, provider fault injection, and cancellation timing are not automated.
- The transaction is in-process only: process death can still leave an API 29+ pending row until provider cleanup or expose a partial API 24–28 row.

Classification: core scaling and in-process publication are **Implemented**; the end-to-end reachable preview/export experience remains **Inconsistent or broken** because its visual controls do not represent export.

## Video merge

```text
Ordered video-only Photo Picker
  -> VideoMetadataReader
  -> VideoMergeViewModel
  -> VideoProjectStore (Room)
  -> WorkManager / VideoExportWorker foreground job
  -> VideoExportRepository / Media3 Transformer
  -> cacheDir/video_exports/<project>.mp4
  -> MediaStore Movies/SplitFrame
```

### Actual composition

- The exporter sorts selected cells and builds one `EditedMediaItemSequence` from all clips.
- Each clip is trimmed and presented into the full output frame; clips play consecutively.
- Reported export duration is the sum of trimmed clip durations.
- Output is MP4 using H.264 video and AAC audio. HDR input requests OpenGL HDR-to-SDR tone mapping.
- Encoder fallback is disabled. 4K is selectable with a warning, but there is no codec capability or free-space preflight.
- Core trimming/order/export runs locally and supports progress/cancel through foreground WorkManager.

### Output and failure handling

- Transformer first renders to `cacheDir/video_exports`. Publication rejects a missing, non-file, or empty cache output before inserting a MediaStore row.
- The cache MP4 is copied with a 64 KiB loop that checks coroutine cancellation for every chunk, requires the final byte count to equal the preflight size, and requires flush plus both stream closes to succeed.
- Exact work ownership is checked before insert and again before publish. Android Q+ publication must update exactly one row from pending to visible.
- The exact Room `running`-to-`succeeded` compare-and-set is the terminal commit after MediaStore publish. After the last active check, that database call is cancellation-shielded so it cannot commit and then lose its Boolean result to cancellation; false or a real database exception still triggers rollback.
- Every caught failure/cancellation after insert best-effort deletes only that URI. A rollback false/exception is suppressed without replacing the original copy, close, publish, ownership, or database failure.
- Sixteen pure JVM tests cover transaction ordering, cache preflight, multi-buffer exact copy and cancellation, flush/close failures, ownership/publish/commit failures, rollback preservation, and the cancellation-shielded terminal commit. Concrete ContentResolver/API branches, provider failure, process death, and playable-output validation remain unautomated.
- Cache-file removal remains best-effort in `finally`; deletion failure or process death can leave an abandoned cache MP4.

### Preview/export alignment and residual gaps

- The editor now uses one ExoPlayer playlist and shows one full-canvas clip at a time in exported order.
- Its global position and duration use cumulative trimmed durations; exact internal boundaries select the next clip and playback stops at the sequence end.
- The active playlist item supplies audio, matching the export policy of retaining each clip's audio during that segment.
- Preview is crop-fill only, and its pan/zoom placement derives from `LayoutMath.cropToFillSourceRect`, which also drives export. Real-device frame parity, rotation, and aspect-ratio validation remain pending.
- Template cells, spacing, corners, background, legacy duration mode, and primary-audio state remain compatibility data and do not drive preview or export.
- Mixed image/video infrastructure is present, but current picker/state/export paths accept video only.

Classification: sequential merge and its sequence-oriented preview are **Implemented**; real-device visual/audio parity is **Cannot verify**; true split-screen composition is **Not implemented**.

### Recovery and work ownership

- The saveable app route retains the active canonical project UUID and creates a Koin ViewModel keyed to that exact project.
- Room work rows remain keyed by project, while updates now also require the exact WorkManager UUID and an allowed prior state. Queued/running work may cancel; canceled or replaced work cannot return to running or terminal success.
- The repository checks exact work ownership before insertion and immediately before MediaStore publication. It then makes the exact Room success compare-and-set the publication transaction's final commit; a false/throw rolls back the just-published URI.
- Long-running WorkManager exports retain only Android's required silent, low-priority foreground-service status while active. It has no tap action, requests no notification permission, and is removed when work ends.
- Completion and failure are reported through the persisted project work state and editor UI; no terminal notifications or notification deep links are created.
- Activity recreation for the exact active project is automated on a physical API 36 device. Controlled process death while queued/running/terminal remains a manual validation boundary.

## Preview/export control parity

| Control | Photo | Resize | Video |
|---|---|---|---|
| Selection/order | Matches in main path | Single source | Preview and export use the same ordered clips. |
| Trim | N/A | N/A | Preview and export use the same clip boundaries. |
| Pan/zoom | Shared crop math | Preview-only | Shared crop math; real-frame parity is not yet device-verified. |
| Fit | Crop-fill | Full-image scale/stretch | Crop-fill only; legacy FIT state is not reachable. |
| Spacing/corners | Both render, units mismatch | N/A | Legacy model only; sequence preview/export ignore them. |
| Border/background | Both render; border inaccessible | N/A | Legacy model only; sequence preview/export ignore them. |
| Aspect/resolution | Export selection works | Output dimensions work | Resolution works; aspect action is inaccessible and usually 16:9. |
| Format | JPEG | JPEG/PNG/WebP | MP4 H.264/AAC. |
| Share | Available | No direct result share | Not implemented. |

## Persistence, cleanup, and integrity

| Pipeline | Temporary data | Confirmed cleanup | Gap |
|---|---|---|---|
| Photo | Decoded/render bitmaps; MediaStore row | Owned output recycling plus exact-row rollback after in-process post-insert failure | Process-death leftovers and real provider/low-storage/API 24–29+ behavior are not device-verified. |
| Resize | Input/output bitmaps; MediaStore row | Identity-safe bitmap cleanup plus exact-row rollback after caught in-process failure/cancellation | Process-death leftovers and real provider/low-storage/API 24–29+ behavior are not device-verified. |
| Video | Cache MP4, MediaStore row, and Room work row | Exact-length copy, exact-row rollback after caught failure/cancellation, terminal exact-work success commit, and best-effort active-cache deletion | Process death/provider rollback failure can cross the MediaStore/Room boundary or leave a cache/row artifact; real MP4 integrity is unverified. |

No pipeline performs a post-save decode/duration/file-size integrity check. Video has no startup sweep for abandoned `cacheDir/video_exports` files. Photo Picker URI persistence is attempted, but OEM/device behavior and long-term grants require instrumentation.

## Technical risk sequence

1. Release the decoder-owned bitmap if EXIF orientation allocation fails inside `ImageSourceReader`.
2. Align resize preview controls with the data passed to its exporter; handle the remaining before/after divider-color mismatch separately.
3. Validate sequential video preview/export frames, rotations, audio boundaries, and supported codecs on real devices.
4. Bound input/resource use and add codec, storage, and output validation.
5. Add real provider/render/MP4, controlled process-death, and in-flight WorkManager recovery tests.

The accepted video-direction ADR is implemented: preview represents the sequential exporter. Recovery hardened Worker/repository orchestration without changing the composition contract, Room schema, dependencies, or offline behavior. Photo effect geometry now shares one source contract between preview and export, and photo, resize, and video publication remain separately guarded for caught in-process failures without changing formats, quality/codecs, paths, permissions, or offline behavior; device media validation remains required before release.
