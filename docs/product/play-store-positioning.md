# SplitFrame Play Store Positioning

## Product truth

SplitFrame is an offline-capable Android media editor with three clear workflows:

- Photo Collage: choose from 110 layouts, arrange photos, and export a styled JPEG.
- Resize Image: use common/social dimensions or Custom with explicit Fit/Fill and JPEG, PNG, or WebP output.
- Merge Videos: trim, reorder, crop, and join video clips sequentially into one H.264/AAC MP4.

Creation, editing, saved state, and export are local. Network access is used for consent and advertising, not for media processing. Smart layout recommendations are deterministic on-device ranking, not AI or a network service.

Do not position the current video workflow as simultaneous split-screen/video collage. Do not claim cloud backup, cross-device sync, batch resize, target-file-size compression, automatic background removal, or codec support beyond what has been device-qualified.

## Recommended listing direction

Suggested title:

> SplitFrame: Collage & Resize

Suggested short description:

> Create photo collages, resize images, and merge video clips on your device.

Core value order:

1. Create quickly: Home exposes Collage, Resize, and Video Merge immediately.
2. Find the right layout: search, filters, favorites, recent layouts, and offline recommendations.
3. Make it personal: text, colors, gradients, blur, patterns, borders, shape crops, Auto Arrange, and drag/swap.
4. Export intentionally: common social dimensions, Custom sizes, explicit Fit/Fill, actual compression comparison, and sequential video merge.
5. Keep working offline: media is processed locally; unavailable ads never block creation or export.

## Feature claims safe after release gates pass

- 110 collage layouts with search, filters, favorites, and recents.
- On-device layout recommendations based on photo count and shape.
- Text with bundled system-font choices, outlines, shadows, transforms, and Unicode/multiline support.
- Solid, gradient, blurred-media, and bundled pattern backgrounds.
- Solid, gradient, and dashed borders plus circle, heart, hexagon, and star crops.
- Instagram post/story, WhatsApp Status, YouTube thumbnail, Pinterest, wallpaper, and custom resize targets.
- Explicit Fit or Fill behavior; content is never silently cropped by a preset change.
- Actual original/output dimensions and file-size reduction after resize.
- Ordered trim-and-merge video export with progress, cancellation, and saved projects.
- Local editing/export without an account.

Avoid “works with every format/device,” “lossless,” “zero quality loss,” “instant,” and “crash-free.” Avoid an “AI collage” claim because ranking is deterministic local scoring.

## Monetization disclosure

The app contains ads. The implemented inventory includes consent-gated app-open, workflow interstitial, adaptive banner, and native formats. Listing screenshots must not disguise ads as projects/templates/actions. Do not promise “ad-free” or “no interruptions.”

## Privacy and policy notes

- State clearly that selected media is processed on-device and is not uploaded by SplitFrame for editing/export.
- Keep this separate from advertising SDK network/data behavior in Data safety and the privacy policy.
- Confirm Advertising ID, Ads, Data safety, target-audience, foreground-service, UMP, and privacy-option declarations against the final signed artifact and console configuration.
- Do not state that no data is collected until the release owner verifies the behavior of every bundled SDK.

## Screenshot plan

Use app-owned or licensed disposable media only:

1. Home with the three primary actions and no ad covering product controls.
2. Template discovery with search/filter and clearly organic layout cards.
3. Collage editor showing text, background, border, and shape tools.
4. Resize preset with visible dimensions, Fit/Fill, and actual before/after statistics.
5. Recent video projects and the sequential trim/reorder editor.

Exclude consent dialogs, test ads, production identifiers, personal media, unsupported future features, and competitor assets.

## Publication gate

This positioning is implementation-aligned, but the listing must not be published until `release-readiness-report.md` no longer reports release blockers and all screenshots/claims are verified on the final signed build.
