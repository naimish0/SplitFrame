# ADR: Video Composition Direction

- **Status:** Accepted and implemented — real-device release validation remains pending
- **Date:** 2026-07-20
- **Decision:** Option A, sequential video merge

## Current verified behavior

- The reachable product promise is sequential: Home says **Video Merge**, “Join multiple videos into one MP4,” and “Pick videos to merge in order” (`app/src/main/res/values/strings.xml:211-215`).
- Selection is ordered and video-only. The metadata layer can describe images, but the ViewModel filters them out and reports `video_only_required` (`VideoEditorScreen.kt:135-170`, `VideoMergeViewModel.kt:125-151`).
- The preview is sequential. One ExoPlayer playlist presents one full-canvas clip at a time in `orderedClips` order. A global slider maps through cumulative trimmed durations, stops on the final frame, and restarts from zero when Play is pressed at the end.
- The export is sequential. `VideoExportRepository` builds one `EditedMediaItemSequence` from `project.orderedClips`; each trimmed clip fills the complete output frame and follows the previous clip (`VideoExportRepository.kt:73-145`).
- Preview and export duration both use the sum of normalized trimmed durations through `VideoLayoutMath.outputDurationForMergedVideos`. Exact internal boundaries select the next clip; the terminal boundary holds the last clip.
- Export retains each clip's audio during its segment via `setRemoveAudio(false)`. Preview uses the same single active playlist item at normal volume; primary-audio controls are not reachable.
- Export and preview are crop-fill only. Reachable `FIT` controls were removed, and preview transform placement now derives from `LayoutMath.cropToFillSourceRect`, the same crop model used by export.
- Rotation metadata and oriented dimensions are captured. Output size supports 16:9, 9:16, 1:1, and 4:5 in the domain, but the aspect-ratio action has no reachable control and new projects normally remain 16:9 (`VideoModels.kt:14-22,102-103`). Real rotated-output parity is untested.
- Media3 1.10.1, ExoPlayer, Transformer, effects, and WorkManager are already dependencies. The app supports API 24+ and forces H.264/AAC export with encoder fallback disabled.
- The video-detail schema introduced `mergeMode` in Room v4, and migration 3→4 defaults existing rows to `SEQUENCE`. Room is now v5 because Cycle 3 added a separate recent-project metadata index; it did not change the video composition fields or `SEQUENCE` behavior.

## Inconsistencies

1. Crop/pan/zoom, rotation, aspect, audio transitions, and the last decoded frame share one intended contract but still need real-device preview/export frame comparison.
2. Mixed image/video models, persistence, strings, and layout helpers exist, but the reachable picker and exporter require videos only.
3. Legacy template, duration-mode, fit-mode, and primary-audio fields remain for Room compatibility even though sequential controls do not expose them.
4. Historical split-screen design documents describe an intended compositor that current production source never implemented.
5. The umbrella **SplitFrame** name and photo-collage product suggest layout composition, while the current video-specific copy explicitly promises ordered merge.
6. Automated tests validate timeline boundaries but do not yet render or decode a real Transformer MP4.

## Options

### Option A — sequential video merge

Clips play one after another in the chosen order. One full-canvas clip is active at a time. The global timeline is the sum of trimmed durations, and each clip keeps its own audio while it plays.

Required correction:

- Replace the simultaneous grid preview with a single-player, full-canvas sequential preview.
- Map global playback time to the active clip and trim-relative position.
- Keep reorder, trim, crop/pan/zoom, resolution, progress, cancel, and background export.
- Hide or remove split-only duration/audio/template controls unless sequential export gains an equivalent behavior.
- Expose only fit/transform choices that preview and export can render identically.

This leaves the existing exporter and persisted `SEQUENCE` meaning intact.

### Option B — simultaneous split-screen composition

Videos and optional images occupy layout cells and render at the same time. A template, common duration policy, per-cell transform, and audio policy determine the output.

Required implementation:

- Build one overlapping Media3 sequence per cell and custom compositor placement from `LayoutTemplate` geometry.
- Add a reachable template and mixed-media selection flow.
- Define still-image duration/frame-rate behavior.
- Implement and test unequal-duration rules: loop and shortest are feasible; freeze requires a generated or held final-frame tail.
- Choose one authoritative audio policy. Primary-source-or-silent is lower risk than mixing concurrent tracks.
- Add strict concurrent-video/resolution limits, codec capability checks, GL/background/corner/FIT/FILL parity, and a much larger real-device matrix.
- Preserve existing `SEQUENCE` projects and queued work rather than silently changing their output.

Media3 documents multi-sequence composition and custom grid/PiP placement, but `VideoCompositorSettings` is a Beta API: [composition guide](https://developer.android.com/media/media3/transformer/composition), [custom compositor settings](https://developer.android.com/media/media3/transformer/videocompositorsettings).

## Evidence

| Criterion | Option A — sequential | Option B — simultaneous |
|---|---|---|
| Existing branding | Matches all reachable video-specific names and copy. | Better literal fit for “SplitFrame” and the photo-layout identity. |
| Photo layout-engine reuse | Reuses shared crop math, transforms, aspect/output sizing; not multi-cell templates. | Reuses normalized templates and cell frames, but not the missing timeline, compositor, image-duration, audio, or device-capability work. |
| Current maturity | Export, trim/order, WorkManager, progress/cancel, HDR→SDR request, and MediaStore publishing already exist. | Preview/domain scaffolding exists; no production simultaneous exporter exists. |
| Preview/export parity | Requires a bounded preview replacement; exporter stays authoritative. | Requires a new high-risk exporter plus validation of the existing preview. |
| Mixed image/video | Deliberately video-only; images remain in Photo Collage. | Natural product fit, but selection, completion, duration, export, and tests are missing. |
| Unequal durations | Naturally concatenate; total is deterministic. | Requires product rules for loop, stop, freeze, gaps, and image duration. |
| Audio | Each clip's audio follows its segment; silent clips remain silent. | Requires primary-source selection or tested mixing across simultaneous streams. |
| Rotation/aspect ratios | One decoded source fills one output frame; still needs real-device tests. | Every source needs verified per-cell rotation, crop/FIT, placement, and color handling. |
| API 24+ performance | Export does not require concurrent cell streams; one preview player is sufficient. | Requires concurrent decoders, GL textures/composition, audio handling, and one encoder. Current two-player preview cap already signals resource pressure. |
| Implementation cost | Low–medium. Timeline/preview/UI cleanup and parity tests. | High. New composition plan, renderer, policies, migration behavior, caps, fixtures, and device qualification. |
| Maintenance cost | Lower; follows established Media3 sequence APIs and current product semantics. | Higher; includes Beta compositor APIs and a device-specific concurrency matrix. |

Current automated evidence remains asymmetric: 12 `VideoLayoutMathTest`, 7 `MixedMediaModelTest`, and 7 `VideoSequenceTimelineTest` cases cover geometry, duration, and exact sequence boundaries, but no test renders a real video. `VideoExportWorkerTest` only checks foreground-service type. There are no video Android tests, exporter tests, persistence compatibility tests, golden-frame tests, or codec fixtures.

## Recommended decision

Choose **Option A — sequential video merge** as SplitFrame's primary video product direction.

The decisive evidence is not merely that A is already implemented. A is the only direction for which the user promise, order/trim model, displayed export duration, persisted mode, Worker pipeline, and actual output agree. Aligning preview to that contract is substantially safer on API 24+ and is achievable without a renderer or database migration.

The SplitFrame name and latent photo-layout scaffolding make Option B a credible future product, but they do not outweigh current behavior, reliability, or maintenance cost. If pursued later, it should be introduced as a separate, explicitly named **Video Collage** mode with its own ADR and compatibility contract—not by reinterpreting existing sequential projects.

The user explicitly approved both the decision and its bounded production migration. The sequential timeline and one-player preview are now implemented without changing the exporter, Room schema, production identifiers, or offline operation.

## Consequences

### Positive

- Preview, duration, audio, order, trim, and output can share one understandable contract.
- Existing exports, persisted `SEQUENCE` rows, queued WorkManager jobs, and video-specific copy remain semantically valid.
- Preview can use one live player instead of multiple decoders, reducing API 24+/low-memory pressure.
- No Room schema migration, new media dependency, cloud service, or FFmpeg integration is required.
- A focused migration is smaller to test and maintain than a new compositor.

### Negative

- The video product will not provide the simultaneous split-screen behavior suggested by the umbrella name.
- Existing template, duration-mode, primary-audio, mixed-media, and cell-preview scaffolding becomes legacy code to retain temporarily or remove carefully.
- Photo templates will not become video layouts under this decision.
- Existing sequential risks—codec/4K preflight, MediaStore rollback, output validation, project recovery, and abandoned cache cleanup—remain separate work.

### Compatibility

- Existing and queued projects remain `SEQUENCE` and must export exactly as before.
- The first migration should not remove Room columns or custom blob fields. Older rows must continue to decode, normalize to ordered video clips, and save without destructive migration.
- `mergeMode` should remain a stable compatibility boundary even while only `SEQUENCE` is reachable.

## Migration plan

1. **Completed:** add a pure sequential-timeline model and exact-boundary JVM tests.
2. **Completed:** replace the grid/common clock with one full-canvas ExoPlayer playlist while retaining ordered thumbnails and reorder controls.
3. **Completed:** map play, pause, seek, selection, reorder, trim, and project-duration changes to the cumulative sequence.
4. **Completed:** let the active playlist item provide audio and hide primary-audio/simultaneous-duration behavior from the reachable editor.
5. **Completed in source; device validation pending:** use shared crop-fill geometry and hide `FIT`.
6. **Completed for this composition slice:** preserve the v4 video-detail schema, `SEQUENCE`, exporter, and queued-work semantics. Cycle 3 later added a non-destructive v4→v5 metadata-index migration without changing composition.
7. **Completed:** change reachable presentation/accessibility copy from cells to ordered videos.
8. **Pending release validation:** run preview/export parity, rotation, codec, audio-transition, lifecycle, and offline cases on real devices.
9. **Deferred:** remove obsolete split-only compatibility code only after persistence coverage; keep cleanup as a separate slice.

## Non-goals

- No change to the sequential exporter, output format, WorkManager contract, or Room schema.
- No simultaneous video collage, picture-in-picture, mixed image/video export, freeze-frame generation, or audio mixing.
- No new template library, social presets, effects, transitions, music, stickers, or filters.
- No Navigation rewrite, recent-project UI, recovery implementation, MediaStore hardening, codec-preflight redesign, ad change, or monetization change.
- No Room schema rewrite or deletion of compatibility fields.
- No new backend, cloud processing, FFmpeg dependency, or change to offline operation.

## Acceptance criteria

1. Home and editor consistently describe an ordered video merge.
2. Preview shows exactly one full-canvas clip at a time in export order; it never implies simultaneous output.
3. Global preview/export duration equals the sum of normalized trimmed durations.
4. Seeking immediately before, at, and after every boundary selects the correct clip and trim-relative position.
5. Reorder, trim, remove, replace, undo, and redo immediately rebuild the same deterministic sequence used by export.
6. Preview plays only the active clip's audio; a clip without audio is silent. Export keeps the same per-segment audio policy.
7. Preview and export use matching crop-fill, pan/zoom, rotation, and output-aspect semantics within documented visual tolerance. Unsupported `FIT` is not shown.
8. Preview uses no more than one live decoder/player regardless of sequence length. API 24+ behavior remains responsive for the supported input contract.
9. Existing Room v4 projects and queued/running `SEQUENCE` work retain their order and output semantics; the later v4→v5 migration only backfills recent metadata and does not rewrite video payloads.
10. WorkManager progress/cancel, H.264/AAC output, HDR→SDR request, MediaStore destination, and offline editing/export continue to work.
11. Photo collage, resize, ads/consent, production identifiers, signing, and the user's existing TemplatePicker change are unaffected.
12. No UI or store copy claims split-screen, video collage, or mixed-media video export.

## Test strategy

### JVM

- Sequential timeline: empty/invalid input, first frame, trimmed start/end, exact boundary, final frame, reordered clips, removed/replaced clips, and total duration.
- ViewModel: reorder/trim/undo produces the same ordered clips consumed by the exporter; split-only actions are unreachable or deterministic no-ops.
- Persistence: old v4 rows and legacy template IDs decode to stable sequence order; `SEQUENCE` round-trips; queued work is not reinterpreted.
- Export plan: every item retains its trim and audio, uses the selected output size, and is emitted once in order.

### Instrumented media

- Use short checked-in/generated fixtures with distinctive colors, rotations, durations, and audio tones.
- Export a real MP4 and assert non-zero size, H.264/AAC decodability, dimensions, duration tolerance, clip order, boundary frames, rotation/crop, and audio transitions.
- Compare preview screenshots/frames with export at the beginning, each boundary, and the end.
- Verify cancel/failure cleanup and no misleading MediaStore success.

### Device matrix

- API 24, 29, 33, 35, and 36; low- and mid-tier physical devices where available.
- Portrait/landscape sources; 0/90/180/270-degree rotation; audio and silent clips; mixed resolutions, frame rates, and supported containers/codecs.
- 720p and 1080p as baseline. Treat 4K/HDR as separately gated until capability and output tests pass.
- Low memory/storage, background export, process recreation, foreground return, permission denial, and offline mode.

### Regression commands

- Run the new focused JVM tests first.
- Then run `:app:testDebugUnitTest`, focused video connected tests, `:app:lintDebug`, and `:app:assembleDebug`.
- Manually compare at least one real preview/export sequence before approval for release.
