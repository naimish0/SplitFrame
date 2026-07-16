# SplitFrame UI/UX Audit

Date: 2026-07-16

## Scope Reviewed

- App shell and navigation: `MainActivity.kt`, `SplitFrameApplication.kt`, `presentation/SplitFrameApp.kt`
- Theme and tokens: `ui/theme/Color.kt`, `Theme.kt`, `Type.kt`, `Dimens.kt`
- Screens and reusable UI: `TemplatePickerScreen.kt`, `EditorScreen.kt`, `ExportSheet.kt`, `SplitFrameComponents.kt`
- MVI and state: `MergeContract.kt`, `MergeViewModel.kt`
- Domain/layout math: `Models.kt`, `Geometry.kt`, `Templates.kt`
- Export and image loading: `ImageExportRepository.kt`, `ImageSourceReader.kt`, `SuperResolutionProcessor.kt`
- Storage/ads/DI: `ProjectStore.kt`, Room entities/DAOs, `AdsConfigRepository.kt`, `BannerAd.kt`, `AppModule.kt`
- Resources/tests/docs: `strings.xml`, `LayoutMathTest.kt`, `TemplateRepositoryTest.kt`

## Baseline Status

- `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`: passing before this production-ready pass.
- Existing warning: `android.disallowKotlinSourceSets=false` is experimental in the current Gradle/Android plugin setup.
- No baseline lint errors were present before the changes in this pass.

## Critical

- `MergePreviewCanvas.kt` and `ImageExportRepository.kt` both crop images, but only support centered crop-to-fill. Users cannot reposition/crop individual photos, and preview/export cannot preserve user crop intent.
- `EditorScreen.kt` only uses `PickVisualMedia` for one selected cell. It does not support 2-9 image bulk selection even though the product workflow is collage-first.
- `MergeProject` has no per-cell transform state, so image pan/zoom/crop would be UI-only unless the domain model is extended.

## Major

- `Color.kt` still uses the previous indigo/purple palette, not the requested teal-and-coral production brand.
- Template thumbnail colors are randomized and friendly, but they are independent of the production brand and should stay bright without becoming dark or dull.
- `MergeViewModel.kt` can swap images and dimensions, but it cannot remove images, bulk-assign images, or swap/reorder crop transforms.
- `EditorScreen.kt` exposes replace/enhance/swap/export, but remove, add-many, crop reset, and zoom controls are missing.
- `MergePreviewCanvas.kt` uses tap gestures only. Drag/pinch gestures are needed for per-cell pan/zoom.
- Export currently requires all template cells to be filled, which is correct for stable output but needs clearer UI feedback when cells are empty.
- AI Enhance has success/failure messaging, but the selected cell can still feel static during enhancement without a strong per-cell editing context.
- Resolution warnings exist in `ExportSheet.kt`, but selected output quality still depends on source dimensions loading successfully.

## Minor

- Many screen-level dimensions remain direct `dp` literals. They are acceptable for Compose layout, but shared repeated values should use tokens where practical.
- Some interactive actions are text buttons without supporting icons, which makes dense editor actions harder to scan.
- Color swatch labels cover four swatches only; if swatches change, descriptions must be kept in sync.
- Empty cell state is visible in the canvas, but no bulk add action appears near the canvas itself.
- Source thumbnails in the selected-cell panel do not expose crop state or a reset affordance.
- The editor uses adaptive two-column layout at wide widths, but the crop interactions need to remain usable in landscape.

## Enhancement

- Add a domain-level `ImageTransform` with zoom and normalized pan offsets.
- Use the same `LayoutMath.cropToFillSourceRect(...)` overload for preview and export.
- Add multi-image picker support with `PickMultipleVisualMedia(maxItems = 9)`.
- Add crop/pan/zoom controls with gestures plus a deterministic reset action.
- Add focused tests for transform math so crop behavior is stable across preview/export.
- Keep AdMob, AI Enhance, export history, sharing, Room, and MVI flow intact while improving UI state and accessibility.
