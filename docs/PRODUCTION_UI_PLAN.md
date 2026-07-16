# SplitFrame Production UI Plan

Date: 2026-07-16

## Implementation Principles

- Preserve the existing MVI screen flow and Android-only Compose architecture.
- Do not start Phase 2 video, billing, subscriptions, watermarks, or feature locks.
- Keep business logic intact unless a UI workflow requires new state, such as per-cell crop transforms.
- Use central Material 3 tokens and semantic app colors rather than screen-local raw colors.
- Keep the collage workflow focused: select a template, add 2-9 photos, crop/reposition, enhance if desired, export/share.

## Theme And Design Tokens

- Files: `ui/theme/Color.kt`, `ui/theme/Theme.kt`, `ui/theme/Type.kt`, `ui/theme/Dimens.kt`
- Replace the current indigo/purple theme with the requested teal-and-coral light/dark palettes.
- Keep SplitFrame branding as the default; do not let Android dynamic colors override it.
- Map semantic colors to production intent:
  - teal: primary actions, selected cells, active controls
  - coral: AI Enhance and creative accents
  - amber: warnings and quality notices
  - Material error: destructive and failed states
- Keep editor canvas separate from the app background using neutral editor surface tokens.

## Collage Domain And Layout Math

- Files: `domain/Models.kt`, `domain/Geometry.kt`, `presentation/merge/MergeContract.kt`, `MergeViewModel.kt`
- Add `ImageTransform` for each cell with `zoom`, `panX`, and `panY`.
- Add intents/actions for bulk image assignment, image removal, and transform updates.
- Preserve transforms during swap/reorder and reset transforms when replacing/removing a source.
- Extend `LayoutMath.cropToFillSourceRect(...)` so preview and export share identical crop calculations.

## Template Picker

- File: `presentation/merge/TemplatePickerScreen.kt`
- Keep the existing polished top app bar, cards, selected border/icon/elevation, descriptions, and ad separation.
- Keep random template colors per app launch, but ensure generated colors stay bright, friendly, and not too dark.
- Preserve the current 18 templates, which already cover 2-9 image collages.

## Editor

- Files: `presentation/merge/EditorScreen.kt`, `MergePreviewCanvas.kt`
- Add `PickMultipleVisualMedia(maxItems = 9)` for bulk add.
- Keep single-cell replacement for precise edits.
- Add remove image, crop reset, zoom slider, and gesture-based drag/pinch crop controls.
- Show crop instructions and selected-cell state without relying on color alone.
- Pass source dimensions and transforms into the preview renderer so transform math is consistent with export.
- Keep reset confirmation, selected cell panel, AI Enhance state, layout sliders, background swatches, and export CTA.

## Export

- File: `export/ImageExportRepository.kt`
- Use the same transform-aware crop source rect as the preview.
- Preserve MediaStore saving, export history, share flow, resolution picker, and source quality warning behavior.
- Keep duplicate export prevention in the ViewModel.

## Accessibility And Responsiveness

- Files: UI screen/component files and `strings.xml`
- Keep meaningful labels in `strings.xml`.
- Ensure add/remove/replace/reset/zoom actions have text labels.
- Keep 48dp touch targets for swatches and action buttons.
- Maintain navigation bar and IME padding in editor/export surfaces.
- Preserve the existing adaptive editor layout and ensure crop controls remain usable on narrow and wide screens.

## Tests And Verification

- Files: `LayoutMathTest.kt`, `TemplateRepositoryTest.kt`
- Add transform crop tests for zoom, pan, clamping, and default behavior.
- Run:
  - `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`
  - `rg` searches for hardcoded UI colors and likely hardcoded strings after implementation.

## Real-Device Verification Still Required

- Android Photo Picker multiple-select behavior.
- Pinch/drag feel on physical touch hardware.
- AdMob banner/interstitial rendering.
- TFLite enhancement speed and memory behavior on low-end devices.
- Export quality at 1440p/4K with large photos.
