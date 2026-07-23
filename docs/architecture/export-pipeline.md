# SplitFrame Export Pipeline

Audited from production source on 2026-07-20. The three pipelines are independent and remain usable without network access.

## Photo collage

```text
Android Photo Picker
  -> ImageSourceReader (MIME, bounds, sampled decode, EXIF transform)
  -> MergeViewModel project state
  -> MergePreviewCanvas
  -> ImageExportRepository bitmap render
  -> lossless PNG encoding
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

- Export is lossless PNG in `Pictures/SplitFrame`.
- Android Q+ uses `IS_PENDING`; pre-Q relies on legacy storage permission.
- Publication is ordered as MediaStore insert, required lossless PNG encode, flush/close, bounded sampled
  reopen/decode validation with exact dimensions and MIME, and Android Q+ publish; the publish
  update must affect exactly one row.
- Every failure after insert triggers best-effort deletion of that exact URI. If deletion also fails, the original encode/write/close/publish failure remains primary and cleanup failure is suppressed.
- The renderer recycles its newly allocated output on an internal exception, and the export boundary recycles the returned bitmap after save success or failure.
- Ten pure JVM tests cover generic transaction ordering/failures, validation-before-publish,
  injected transaction cancellation rollback, and lossless PNG encode/flush behavior. Seven additional
  pure tests cover canonical metrics, representative output sizes/aspects, normalized cell/effect
  parity, dense-template bounds, extreme crop, and divider geometry. A physical API 36 test writes
  real JPEG, PNG, and WebP pending MediaStore rows and proves the shared validator can reopen and
  decode them; process-death, provider fault injection, and low-storage failure paths remain
  unautomated.
- A unique display-name reservation is synchronously journaled before insertion, then the exact returned URI is bound before bytes are written. Startup removes an interrupted journaled row, while a validated row already made visible by the provider is retained.
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
- EXIF orientation is applied during decode and normalized in output. Metadata removal is the
  privacy-safe default; Preserve details copies a bounded date/camera/copyright/exposure/GPS
  whitelist.
- EXIF orientation uses an explicit ownership-transfer helper: an allocation failure releases the
  decoder-owned bitmap exactly once, a distinct oriented result releases only the original, and an
  identity result retains ownership for the caller. Cleanup failure is suppressed behind the
  original transform failure.

### Preview mismatch

- Preview zoom and pan are local `rememberSaveable` graphics-layer values.
- They do not enter `SingleImageIntent`, the processing request, or the repository.
- Preview maintains the source aspect presentation even when an unlocked custom export will stretch it.

### Output and failure handling

- JPEG, PNG, and WebP retain their existing MIME types/extensions and clamped quality input. WebP remains legacy `WEBP` on API 24–29 and `WEBP_LOSSY` on API 30+; JPEG retains its white background and PNG/WebP retain alpha-capable rendering.
- Percentage resize supports 1–400% through the same dimension and pixel guards. JPEG/WebP target
  size accepts KB or MB and performs a bounded quality search from the selected maximum down to a
  quality floor of 40. It uses the highest encoding quality whose actual bytes fit the target and
  fails explicitly instead of reporting false success when the floor cannot fit.
- Up to 12 named resize presets persist percentage/custom dimensions, format, quality, target
  value/unit, metadata policy, aspect lock, and Fit/Fill. The settings codec reads legacy v1/v2
  state and writes v3.
- A recreation-safe queue accepts up to 20 distinct batch sources and processes them sequentially
  through the same transaction. Item failures are isolated, aggregate progress is reported, and
  cancellation leaves already published outputs intact.
- Metadata is written before validation. Target-size exports recheck actual bytes after EXIF so
  metadata can never silently push a published output above the requested limit.
- Publication is ordered as insert, required encode, explicit flush, successful close, bounded
  sampled reopen/decode validation with exact requested dimensions and MIME, and Android Q+
  publish affecting exactly one row. Cancellation is checked before insertion/writing, around
  validation, and before/after publish.
- Every caught post-insert write/close/publish/cancellation failure best-effort deletes that exact URI; rollback failure is suppressed without replacing the original.
- Decoded input and rendered output ownership is identity-aware. Render failures recycle their owned output, and best-effort `finally` cleanup cannot turn a valid save into a reported failure.
- Fourteen JVM tests cover generic transaction ordering, validation failure, encode/flush/close/
  publish failure, rollback preservation, API-specific format selection, and distinct/aliased
  cleanup. Four shared validator tests cover metadata and bounded sampling. A physical API 36 test
  covers real Bitmap/ContentResolver JPEG, PNG, and WebP output plus corrupt-byte rejection;
  provider fault injection and cancellation timing are not automated.
- A unique display-name reservation is synchronously journaled before insertion, then the exact returned URI is bound before bytes are written. Startup removes an interrupted journaled row, while a validated row already made visible by the provider is retained.

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
- Projects persist either a hard cut or a 250 ms RGB fade-through-black effect at boundaries.
- A user-selected audio document is built as a looping audio-only sequence, replaces clip audio,
  and ends with the video composition.
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
- Sixteen pure JVM transaction tests plus focused durable-recovery tests cover transaction ordering, cache preflight, multi-buffer exact copy and cancellation, flush/close failures, ownership/publish/commit failures, rollback preservation, phase-aware recovery, and the cancellation-shielded terminal commit. The cache MP4 is inspected for a video track and valid known duration before MediaStore insertion; concrete ContentResolver/API branches, provider failure, process death, and real playback remain unautomated.
- Cache-file removal remains best-effort in `finally`; after process death, startup deletes only the exact journaled MP4 whose canonical parent is the owned `cache/video_exports` directory.

### Preview/export alignment and residual gaps

- The editor now uses one ExoPlayer playlist and shows one full-canvas clip at a time in exported order.
- Its global position and duration use cumulative trimmed durations; exact internal boundaries select the next clip and playback stops at the sequence end.
- Preview continues to use clip audio. User-owned audio replacement and fade effects are currently
  export-only and therefore require device parity validation.
- Preview is crop-fill only, and its pan/zoom placement derives from `LayoutMath.cropToFillSourceRect`, which also drives export. Real-device frame parity, rotation, and aspect-ratio validation remain pending.
- Template cells, spacing, corners, background, and legacy duration mode remain compatibility data
  and do not drive preview or export.
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
| Format | Lossless PNG | JPEG/PNG/WebP | MP4 H.264/AAC. |
| Share | Available | No direct result share | Not implemented. |

## Persistence, cleanup, and integrity

| Pipeline | Temporary data | Confirmed cleanup | Gap |
|---|---|---|---|
| Photo | Decoded/render bitmaps; MediaStore row | Bounded sampled reopen/decode with exact dimensions/MIME, owned output recycling, and exact-row rollback after in-process post-insert failure | Process-death leftovers and real provider/low-storage/API 24–29 behavior are not device-verified. |
| Resize | Input/output bitmaps; MediaStore row | Bounded sampled reopen/decode with exact dimensions/MIME, identity-safe bitmap cleanup, and exact-row rollback after caught in-process failure/cancellation | Process-death leftovers and real provider/low-storage/API 24–29 behavior are not device-verified. |
| Video | Cache MP4, MediaStore row, and Room work row | Non-empty/video-track/known-duration cache validation, exact-length copy, exact-row rollback after caught failure/cancellation, terminal exact-work success commit, and best-effort active-cache deletion | Process death/provider rollback failure can cross the MediaStore/Room boundary or leave a cache/row artifact; full MP4 playback integrity is unverified. |

Photo and resize now reopen and bounded-decode their encoded MediaStore row before publication.
Video validates its cache container before insertion and sweeps stale `cacheDir/video_exports`
files. Full pixel/frame/audio parity, durable process-death reconciliation, and real provider/
low-storage behavior remain release gates. Photo Picker URI persistence is attempted, but OEM/device
behavior and long-term grants require instrumentation.

## Technical risk sequence

1. Add durable process-death reconciliation for pending photo, resize, and video publication.
2. Validate sequential video preview/export frames, rotations, audio boundaries, and supported codecs on real devices.
3. Bound input/resource use and add codec, storage, and output validation.
4. Add real provider/render/MP4, controlled process-death, and in-flight WorkManager recovery tests.

The accepted video-direction ADR is implemented: preview represents the sequential exporter. Recovery hardened Worker/repository orchestration without changing the composition contract, Room schema, dependencies, or offline behavior. Photo effect geometry now shares one source contract between preview and export, and photo, resize, and video publication remain separately guarded for caught in-process failures without changing formats, quality/codecs, paths, permissions, or offline behavior; device media validation remains required before release.
