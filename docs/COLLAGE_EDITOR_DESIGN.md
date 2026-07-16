# SplitFrame Collage Editor Design

Date: 2026-07-16

## Goals

- Support collages with 2-9 images using the existing template system.
- Let users add many photos at once, then replace, remove, swap, enhance, and crop individual cells.
- Store crop intent in immutable project state so preview and export are deterministic.
- Keep the implementation Android-only and preserve the existing MVI flow.

## State Model

- `MergeProject.assignedImages`: cell index to selected source.
- `MergeProject.imageTransforms`: cell index to `ImageTransform`.
- `ImageTransform.zoom`: crop zoom multiplier, clamped to a production-safe range.
- `ImageTransform.panX` and `panY`: normalized offsets where `-1f..1f` maps to the available pan range inside the source crop.

## User Flow

- Template picker chooses one of the existing 2-9 cell templates.
- Editor supports:
  - Add photos: launches Android Photo Picker multiple-select with max 9.
  - Replace: launches single-image picker for the selected cell.
  - Remove: clears the selected cell source, dimensions, enhancement, and crop state.
  - Swap previous/next: swaps sources, dimensions, and crop state.
  - Crop: drag inside a selected filled cell to pan; pinch to zoom; slider gives accessible zoom control.
  - Reset crop: returns selected cell to centered crop-to-fill.

## Shared Preview And Export Math

- `LayoutMath.cellFrame(...)` remains the single source of truth for template cell placement.
- `LayoutMath.cropToFillSourceRect(...)` has a transform-aware overload used by both:
  - `MergePreviewCanvas.kt` for on-screen drawing.
  - `ImageExportRepository.kt` for final MediaStore rendering.
- The renderer computes a source crop rectangle from:
  - source image dimensions
  - destination cell frame
  - per-cell transform
- Preview draws the full image translated/scaled so the same source crop rect fills the cell.
- Export draws the bitmap using the same crop rect into the final cell destination.

## Error And Edge Handling

- Empty cells stay visible and tappable.
- Failed image dimension loads do not crash; default centered crop is used until dimensions are available.
- Crop transform is reset when replacing or removing an image to avoid stale crop from another photo.
- Export remains blocked until required cells are filled, preserving current output guarantees.
- AI Enhance keeps its existing fallback behavior and updates dimensions after the enhanced source is applied.

## Deferred

- Arbitrary rotation/freeform placement remains out of scope for this pass.
- Per-cell aspect-ratio controls are out of scope because templates own layout geometry.
- Video crop/transform behavior is not implemented in this Android photo-focused phase.
