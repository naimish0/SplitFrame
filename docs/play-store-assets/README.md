# SplitFrame Play Store Release Assets

Package: `com.example.splitframe`

> The package name is still a template-style identifier. Confirm or replace it with the publisher's permanent, owned namespace before the first Play upload; a published package name cannot be changed in place.

## Upload Files

- App icon: `app-icon-512.png` (`512 x 512`, 32-bit RGBA PNG)
- Feature graphic: `feature-graphic-1024x500.png` (`1024 x 500`)
- Captioned screenshots: `screenshots/captioned/*.png` (`1080 x 1920`)
- Deployable privacy policy: `privacy-policy.html`
- Markdown policy source: `privacy-policy-draft.md`
- AdMob authorized-seller file: `app-ads.txt`
- Play Console worksheet: `play-console-release-checklist.md`

Use `screenshots/captioned/` for the Play Store gallery. These screenshots include marketing captions and exclude ad areas.

## Short Description

Create photo collages, resize images, and merge videos into MP4s.

## Full Description

SplitFrame is a clean, lightweight editor for creating photo collages, resizing single images, and merging videos into one MP4.

Build polished layouts from your photos with ready-made split-frame templates, grids, mosaics, story stacks, portrait layouts, and landscape layouts. Pick a frame, add your photos, adjust crop and position, then export a share-ready image.

For single photos, resize and export images in common formats including JPEG, PNG, and WebP. Choose preset sizes, custom dimensions, output quality, and aspect-ratio settings when you need a quick clean resize.

For videos, select multiple clips and merge them into one MP4 in order. Trim clips, choose export resolution, and save the result to your gallery. Source audio is preserved when selected clips include audio.

Key features:

- Photo collage templates for 2 to 15 photos
- Crop, zoom, pan, spacing, corners, and background controls
- Single-image resize tool with preset and custom sizes
- Multiple-video merge into one MP4
- Video trim and export resolution controls
- Gallery export for easy sharing

SplitFrame is built for fast everyday edits without complicated timelines or heavy design tools.

## Screenshot Order

1. `screenshots/captioned/01-home-captioned.png` - Create Photo Collages
2. `screenshots/captioned/02-photo-templates-captioned.png` - Choose Clean Layouts
3. `screenshots/captioned/03-video-merge-captioned.png` - Merge Multiple Videos
4. `screenshots/captioned/04-collage-editor-captioned.png` - Fine-Tune Every Frame
5. `screenshots/captioned/05-multi-panel-layouts-captioned.png` - More Grid Choices
6. `screenshots/captioned/06-adaptive-grids-captioned.png` - Layouts for Big Sets
7. `screenshots/captioned/07-grid-editor-captioned.png` - Edit 15-Photo Grids

Caption text is listed in `screenshot-captions.md`.

## Privacy Policy Deployment

1. Confirm that the Play developer name is `SplitFrame`, or replace the publisher name consistently in both policy files and the Play listing.
2. Add a monitored developer-contact email to the Play listing. The policy tells users to use the email displayed under **App support > Developer contact**.
3. Publish `privacy-policy.html` at an active, public, non-geofenced HTTPS URL that requires no login. The URL must serve a normal HTML page, not a PDF or editable document.
4. Enter the same URL in Play Console's **Privacy policy** field and configure the app's in-app Privacy policy control to open it.
5. Re-check the policy whenever app data handling, permissions, ads, analytics, SDK versions, backup behavior, or retention changes.

A repository file alone does not satisfy Play's public-URL requirement; deployment to a publisher-controlled host is still required.

## AdMob app-ads.txt Deployment

1. Add the publisher's HTTPS website to the Play listing's **Developer website** field.
2. Compare `app-ads.txt` with the personalized snippet in AdMob. The included line uses the publisher ID present in the supplied production AdMob IDs.
3. Publish the file at the website root as `https://<developer-host>/app-ads.txt`, served as plain text with no login or geographic restriction.
4. After the Play listing is public, request a re-crawl in AdMob and wait for verification.

The privacy policy may be hosted below a path, but `app-ads.txt` must be discoverable from the developer website's host according to AdMob crawler rules.

## Play Console Submission

Work through `play-console-release-checklist.md` against the final release AAB. It records the current AdMob Data Safety types, Advertising ID answer, app-content answers, foreground-service evidence, and the remaining publisher-owned decisions.

Official references:

- Google Play User Data policy: https://support.google.com/googleplay/android-developer/answer/10144311
- Google Play Data Safety guidance: https://support.google.com/googleplay/android-developer/answer/10787469
- Google Mobile Ads SDK disclosure: https://developers.google.com/admob/android/privacy/play-data-disclosure
- AdMob app-ads.txt setup: https://support.google.com/admob/answer/9363762
