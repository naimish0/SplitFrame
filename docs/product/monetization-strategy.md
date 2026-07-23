# SplitFrame Monetization Strategy

## Current implementation

| Format/control | Status | Placement and behavior |
|---|---|---|
| UMP consent | Implemented | Ad requests stay disabled until UMP reports that ads may be requested. Privacy options are shown when required, and cached ads are cleared when ads become unavailable. |
| Banner | Partially implemented | A fixed banner container is attached to the app shell. It is not an anchored adaptive banner and uses a fixed-height slot. |
| Native advanced | Implemented, device verification required | One native ad appears below the Home mode cards, includes sponsorship attribution and AdChoices, and destroys the loaded ad with the composable lifecycle. |
| Interstitial | Implemented | Requested only after successful photo/video export, with a persisted two-minute minimum interval. A photo export performed specifically for sharing suppresses the interstitial. |
| App-open | Partially implemented | Eligible only when returning to Home after at least 30 seconds in the background; not used at cold start. Loaded-ad freshness is capped. It may still cover already-rendered Home content unexpectedly. |
| Rewarded | Not implemented | No user-value exchange exists. |
| Purchase/subscription | Not implemented | No ad-removal or premium entitlement exists. |

Debug/testing builds use Google's official test ad inventory. Release identifiers, signing, and secrets are outside this audit and must not be placed in documentation or tests.

## Assessment

The current post-export interstitial is the strongest placement: it follows a completed task, is frequency-limited, and does not interrupt editing/export. The Home native placement is understandable if its attribution and click geometry validate on real devices. UMP gating follows the required high-level sequence.

The weakest behavior is app-open presentation. It is limited to a real background return and Home, but the app does not wait on a loading surface; an available ad can appear after Home has rendered. This can feel like an interruption even if it is technically eligible. The fixed banner also leaves revenue and layout quality on the table compared with adaptive sizing.

## Strategy guardrails

- Preserve offline editing and export. Never make consent, ad load, or network availability a prerequisite for media work or saved output.
- Keep interstitials after confirmed successful exports only. Do not show them on picker return, editor entry, failed/canceled export, back navigation, or permission denial.
- Keep the existing persisted interval as a minimum safeguard; analyze real usage before increasing frequency.
- Do not add an interstitial to direct share after a user has explicitly asked to share.
- Delay first app-open exposure until the user has used the app several times, and show only from a deliberate loading/transition state if a later cycle changes it.
- Never stack app-open, interstitial, native, and banner presentations or obscure app controls with ads.
- Replace the fixed banner with an anchored adaptive implementation only in a separately tested slice.
- A future one-time “remove ads” option is category-appropriate, but it should follow export correctness, recovery, and policy validation. Do not introduce a subscription without recurring user value.
- Do not gate no-watermark output, basic offline export, or project recovery behind monetization.
- Configure age/child treatment only from an approved audience decision; do not infer it from source. App-open ads are unsuitable for apps in the Families program.

## Consent and policy requirements

- Follow the [UMP Android guide](https://developers.google.com/admob/android/privacy): update consent information on every launch, then rely on `canRequestAds`; tolerate update errors by using valid cached consent state.
- Follow [app-open implementation guidance](https://developers.google.com/admob/android/app-open) and [app-open best practices](https://support.google.com/admob/answer/9341964?hl=en-GB): introduce after several uses, use a loading context, control frequency, and avoid adjacent/overlapping ads.
- Keep interstitials at natural breaks as described in the [Google Mobile Ads interstitial guide](https://developers.google.com/admob/android/interstitial?hl=en).
- Prefer anchored adaptive sizing from the [banner guide](https://developers.google.com/admob/android/banner) when banner work is approved.
- Verify attribution and AdChoices against the [native advanced guide](https://developers.google.com/admob/android/native/advanced).
- Validate placements against [Google Play ads policy](https://support.google.com/googleplay/android-developer/answer/9857753?hl=en).
- Keep Play Data safety and the public privacy policy synchronized with all included SDK behavior using the [Data safety guidance](https://support.google.com/googleplay/android-developer/answer/10787469?hl=en) and [User Data policy](https://support.google.com/googleplay/android-developer/answer/10144311?hl=en).

## Release checks that cannot be verified from source

- The public privacy-policy URL is live, accessible, non-editable, and matches the in-app policy.
- Play Console Data safety, Ads, Target audience, UMP messages, and foreground-service declarations match the release artifact.
- Production ad-unit ownership and app association are valid.
- Native Validator passes; attribution, AdChoices, touch targets, contrast, and scrolling are compliant.
- App-open and interstitial frequency are acceptable across cold start, configuration change, short backgrounding, process recreation, and repeated exports.
- Children/family treatment and maximum content rating match the approved audience decision.

## Near-term recommendation

Do not expand monetization during the video-direction decision or any approved preview-parity migration. Preserve the existing UMP gate and post-export frequency cap, manually validate release behavior, and schedule app-open loading/timing plus adaptive banner work only after the higher-ranked media correctness risks.
