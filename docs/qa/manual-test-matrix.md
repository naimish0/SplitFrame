# SplitFrame Manual Test Matrix

Status values: **Passed**, **Failed**, **Blocked**, **Manual verification required**, **Not applicable**. Use disposable media and official Google test ads. Run the core matrix once with networking disabled.

## Automated checkpoint

| Check | Status | Evidence |
|---|---|---|
| JVM source suite | Passed | 237 test methods in 32 files. Combined execution plus targeted post-fix regressions pass. |
| Debug lint | Passed | 0 errors, 124 warnings, 1 hint. |
| Release build | Passed | Fresh `:app:assembleRelease`, including release lint-vital. |
| Instrumentation compile | Passed | 32 test methods in 12 files compile. |
| Connected instrumentation | Blocked | API 36 run found and drove a fix for the app-open logo crash. Post-fix rerun was blocked when wireless ADB became offline; the screen-off retries were environmental, not product assertions. |

## Required devices

- API 24/28: legacy MediaStore and storage permission behavior.
- API 29: scoped-storage pending/publish behavior.
- API 33: Photo Picker and notification permission.
- API 36: current target-SDK, foreground work, ads, and UI.
- At least one low-memory device, one limited-storage device, and one codec-diverse physical device.
- Repeat key UX cases in portrait/landscape, compact/tablet widths, light/dark, 100%/200% font, TalkBack, gesture/three-button navigation.

## P0 — crash, recovery, and persistence

| ID | Scenario | Expected result | Status |
|---|---|---|---|
| P0-01 | Create a collage with transforms, text, background, border, shapes; rotate, background, kill, relaunch. | Exact draft and route restore, or explicit corrupt/missing-media repair; no partial decode. | Manual verification required |
| P0-02 | Configure Resize preset/custom dimensions, Fit/Fill, format/quality; rotate and process-recreate. | Request/source restore; result restores only if readable and metadata/geometry match. | Manual verification required |
| P0-03 | Create, rename, duplicate, delete/Undo, and resume multiple video drafts. | Exact UUID/name/media/trim/transform/target/timestamp remain isolated; late saves never resurrect tombstones. | Manual verification required |
| P0-04 | Kill during video Room-row creation, WorkManager enqueue, queued/running work, and terminal delivery. | Same work resumes or fails actionably; fresh enqueue is not invalidated; no duplicate success. | Manual verification required |
| P0-05 | Open running/completed notifications for two projects after recreation. | Each opens its exact project; malformed/missing/deleted targets fall back safely. | Manual verification required |
| P0-06 | Revoke URI permission, delete source, and use cloud-only media before reopen/export. | Missing cells are identified; export is blocked; repair/remove/rename/delete remain available. | Manual verification required |
| P0-07 | Seed malformed photo draft and malformed/unknown-enum video rows. | Project is corrupt/resettable, never partially decoded, normalized, or silently rewritten. | Passed |
| P0-08 | Upgrade real user databases from every historically shipped schema. | All projects/preferences survive without destructive migration. | Blocked until shipped versions are confirmed |

## P0 — output integrity and resources

| ID | Scenario | Expected result | Status |
|---|---|---|---|
| P0-09 | Fill storage; export collage JPEG, resize JPEG/PNG/WebP, and video MP4 on API 24/28 and 29+. | Actionable failure; no misleading success, zero-byte file, or caught-failure pending/partial row. | Manual verification required |
| P0-10 | Cancel during decode/render/compress, Media3 render, MediaStore copy, publish, and work replacement. | UI recovers; owned bitmaps/codecs/streams/temp files release; exact inserted row rolls back. | Manual verification required |
| P0-11 | Force process death after MediaStore insert/copy/publish. | No pending, partial, orphan, or duplicate output remains. | Failed — durable publication reconciliation is absent |
| P0-12 | Open every output in Gallery and independent decoder/player. | Correct MIME, dimensions, transparency/background, duration, seekability, and audio. | Manual verification required |
| P0-13 | Export rotated/mixed-resolution/frame-rate/codec/HDR/audio and silent clips at 720p/1080p/4K. | Requested H.264/AAC dimensions or actionable unsupported-encoder/decoder error; no silent fallback. | Manual verification required |
| P0-14 | Relaunch after intentionally leaving old `video_exports` cache files. | Old SplitFrame MP4 temps are swept; current/unrelated files remain. | Manual verification required |
| P0-15 | Disable network before launch and complete all three workflows. | Selection, editing, persistence, export, result open/share continue; ad/consent failure is invisible. | Manual verification required |

## P1 — preview/export parity and creative tools

| ID | Scenario | Expected result | Status |
|---|---|---|---|
| P1-01 | Export square/portrait/landscape/dense collages at 1080/2K/4K with extreme spacing/corners/pan/zoom. | Canvas, cells, crop, transforms, and dimensions match preview within pixel tolerance. | Manual verification required |
| P1-02 | Exercise Unicode/multiline text, every font/effect/transform, duplicate/delete/undo/redo. | Bounds/baselines/effects/layer selection match export and restoration. | Manual verification required |
| P1-03 | Exercise solid/linear/radial/media-blur/pattern backgrounds, including missing blur source. | Preview/export configuration matches; missing source degrades safely; no retained large bitmap. | Manual verification required |
| P1-04 | Exercise solid/gradient/dashed borders with corners at multiple resolutions. | Stroke position/width/dash scaling match; cell geometry is unchanged. | Manual verification required |
| P1-05 | Exercise rectangle/circle/heart/hexagon/star at extreme aspects and transforms. | Same normalized path and pan/zoom behavior in preview/export/restoration. | Manual verification required |
| P1-06 | Auto Arrange and Undo across portrait/landscape/mixed media. | Deterministic one-to-one assignment; no loss/duplication; transforms follow media and shapes stay with cells. | Manual verification required |
| P1-07 | Drag/swap, cancel mid-drag, pan selected media, use accessibility swap actions. | Gesture separation is clear, atomic transforms survive, one undo step reverses the gesture. | Manual verification required |
| P1-08 | Resize with each preset/custom dimension and Fit/Fill; compare before/after. | No silent crop; displayed dimensions/bytes/reduction use actual output metadata; invalid/missing output is safe. | Manual verification required |
| P1-09 | Seek/export two and many trimmed videos at every boundary. | One full-frame clip plays at a time; order, trims, crop, duration, final frame, and per-segment audio match. | Manual verification required |

## P1 — Home, discovery, and navigation

| ID | Scenario | Expected result | Status |
|---|---|---|---|
| P1-10 | Fresh Home in loading/empty/error/populated states. | Three primary actions remain above saved work; no empty rails or content jump; ads are separate. | Manual verification required |
| P1-11 | Search name/category/ratio/count; combine/reset all filters; clear query/no results. | Search/filter composition is case-insensitive, stable, and does not affect organic positions. | Manual verification required |
| P1-12 | Favorite/unfavorite, force persistence failure, restart; select layouts repeatedly. | Optimistic rollback works; no duplicate favorites; recent use is bounded, deduped, newest-first. | Manual verification required |
| P1-13 | Select portrait/landscape/square/mixed photos and add/remove/reorder. | Recommended order/reasons update deterministically; incompatible layouts are excluded/separate; All stays complete. | Manual verification required |
| P1-14 | Back/rotate/recreate from every Home, template, editor, resize, privacy, project, and result path. | Intended caller and exact project restore; no duplicate navigation event or accidental exit. | Manual verification required |

## P1 — ads, consent, accessibility, and policy

| ID | Scenario | Expected result | Status |
|---|---|---|---|
| P1-15 | UMP required/not-required/denied states and Privacy options. | No request before eligibility; denial preserves core workflows; privacy entry reflects UMP. | Manual verification required |
| P1-16 | Complete unique collage/resize/video successes, duplicates, batches, failures, cancels. | First success no interstitial; second one opportunity; reset only when display begins; output/result never blocked. | Manual verification required |
| P1-17 | Cold start; <30s/>30s return; picker/camera/share/viewer/permission/config/export/interstitial transitions. | Four-hour cap, bounded windows, and suppressions hold; no late/stacked app-open ad. | Manual verification required |
| P1-18 | Load/no-fill/rotate/navigate banners on safe browsing screens. | One adaptive anchored banner, stable reserved space, correct insets, clean hidden failure, lifecycle disposal. | Manual verification required |
| P1-19 | Validate native ads in long template/project feeds. | Insert only after organic content, never first/consecutive/short-list; attribution and AdChoices visible; replacement destroyed. | Manual verification required |
| P1-20 | TalkBack, keyboard/accessibility actions, 200% font, themes, cutouts, nav modes, compact/tablet/foldable. | Honest labels/focus order, 48dp targets, no clipping/overlap, adequate contrast, all actions remain reachable. | Manual verification required |
| P1-21 | Verify Play Console and public policy URLs against the final signed artifact. | Ads, Data safety, Advertising ID, target audience, foreground service, privacy, SDK and content declarations are accurate. | Manual verification required |

## Release exit

Do not mark production-ready until all P0 rows are Passed or explicitly accepted by the release owner, the 32-test connected suite passes post-fix, release signing is verified, and all P1 ad/accessibility/media cases have recorded device evidence.
