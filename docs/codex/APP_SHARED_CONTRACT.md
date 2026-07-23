# SplitFrame Android Application Shared Contract

Contract version: `1.0.0`

Last reviewed: `2026-07-23`

Status: Active for the SplitFrame repository.

This contract defines reusable engineering, product-quality, localization, privacy, advertising,
testing, release, and store-asset rules for an Android application. Product-specific behavior and
feature requirements belong in the configured product-context file and current feature
specification, not in this contract.

Before every release or store-asset export, revalidate all time-sensitive requirements against
current official Android, Google Play, Google Mobile Ads, User Messaging Platform, billing, privacy,
accessibility, and target-SDK policies. This contract is guidance, not evidence that a current policy
requirement has been met.

## 1. Quick Start

For each new Android repository:

1. Copy this template to `docs/codex/APP_SHARED_CONTRACT.md`.
2. Complete only the App Profile and its placeholder-completion checklist.
3. Add the following instruction snippet to the root `AGENTS.md`.
4. Keep product-specific requirements in the separate product-context document identified by the
   App Profile.
5. Reference the activated shared contract from every feature instead of pasting its rules again.
6. Update the activated contract whenever a global product decision changes.

```md
## Required Shared Context

Before planning, changing, reviewing or testing the application, read:

1. `docs/codex/APP_SHARED_CONTRACT.md`
2. The product-context file identified by that contract
3. The current feature specification

Do not ask the user to paste shared rules already documented in these files.
```

An activated contract must not contain unresolved required placeholders. Optional capability fields
must still be completed explicitly with `ENABLED`, `DISABLED`, `NONE`, or another concrete value;
do not delete them or leave their applicability ambiguous.

## 2. App Profile

The App Profile is the only section that should normally require per-application configuration.
Values in this table govern capability-driven sections elsewhere in the contract.

| Field | Value |
| --- | --- |
| Application name | `SplitFrame` |
| Play Store title | `SplitFrame: Collage & Resize` |
| Package name | `com.rameshta.splitframe` |
| Namespace | `com.rameshta.splitframe` |
| Product-context path | `docs/product/current-state.md` |
| Feature-queue path | `NONE` |
| Application type | Offline-capable Android photo-collage, image-resize, and sequential video-merge utility |
| Product vision | Make polished everyday photo and video edits fast, approachable, and local to the user's device |
| Target users | Android users who need quick collage creation, image resizing, or ordered video merging without an account or complex timeline |
| Primary use cases | Create and export photo collages; resize and convert images; trim, reorder, and merge video clips sequentially |
| Core differentiator | Three focused on-device media workflows with 110 collage layouts, creative editing, explicit Fit/Fill resizing, and recoverable local video projects |
| Processing model | `HYBRID`: media editing/export is offline and on-device; Google consent and advertising require network access |
| Account support | `DISABLED` |
| Minimum SDK | `24` |
| Target SDK | `36` |
| Compile SDK | `36` with minor API level `1` |
| UI framework | Kotlin and Jetpack Compose with Material 3 |
| Architecture | Single-activity, ViewModel-driven unidirectional state/intent flows with repositories and domain models |
| Dependency injection framework | Koin |
| Persistence technology | Room, SharedPreferences, SavedStateHandle, and MediaStore |
| Background-processing technology | WorkManager foreground work for video export; Kotlin coroutines for scoped asynchronous work |
| Navigation framework | Manual saveable `AppRoute`/`AppScreen` navigation in Compose |
| Minimum supported form factors | Android phones and tablets, including compact, 7-inch, and 10-inch layouts |
| Supported orientations | Portrait and landscape |
| Localization mode | `MULTILINGUAL` |
| Supported locales | English `en`, German `de`, French `fr`, Japanese `ja`, Hindi `hi`, Russian `ru`, Spanish `es`, Portuguese (Portugal) `pt-PT`, Portuguese (Brazil) `pt-BR`, Italian `it`, Indonesian `id`, Arabic `ar`, Korean `ko`, and Urdu `ur` |
| Default locale | English `en` |
| RTL support | `ENABLED` for Arabic `ar` and Urdu `ur`; platform `supportsRtl` is enabled |
| Theme support | System-controlled Light and Dark themes |
| Dynamic Color support | `DISABLED` |
| Accessibility requirements | Material 3 semantics; 48 dp targets; TalkBack labels and logical traversal; keyboard/focus support where applicable; 200% font-scale resilience; accessible contrast; reduced-motion-safe behavior |
| AdMob enabled | `ENABLED` |
| Ad formats enabled | Adaptive anchored banner, native, workflow interstitial, and app-open |
| Billing enabled | `DISABLED` |
| Analytics enabled | `DISABLED` |
| Crash reporting enabled | `DISABLED` |
| Cloud services enabled | `DISABLED` |
| Store asset root | `docs/play-store-assets/` |
| Git workflow mode | `main-only` |
| External runner ownership | `NONE` |
| Repository-local Git name | `Naimish Gupta` |
| Repository-local Git email | `naimishgupta983842377@gmail.com` |
| Banner placements | Bottom anchored banner on Home, Template Discovery, and Recent Video Projects only |
| Native placements | Template Discovery after every 7 organic items, maximum 2; Recent Video Projects after 6 organic items, maximum 1; never first or last |
| Interstitial trigger count | After every 2 unique successful Photo Collage, Image Resize, or Video Composition workflows, at a natural post-success break |
| Interstitial cooldown | 2 minutes, with 2-minute cross-format full-screen separation |
| App-open cooldown | 4 hours; foreground return requires at least 30 seconds in background; 2-minute interstitial separation |
| Rewarded-ad purpose | `NONE` |
| No-ad screens | Photo Editor, Resize Editor, Video Editor, and Privacy screens have no embedded banner/native placements |
| No-ad workflows | Media selection; editing; active export; permission and consent dialogs; sharing/external viewers; failed, cancelled, or incomplete workflows |
| First-session advertising behavior | Consent may be requested, but no app-open ad may be shown to a first-time user; embedded ads may appear only after consent permits requests; an interstitial requires two successful workflows |
| Consent provider | Google User Messaging Platform (UMP) |
| Advertising debug test mode | `ENABLED`: official Google test ad-unit IDs in debug; production IDs only in release |

The 14 supported locales above are the required reusable baseline for multilingual applications
using this contract and must be supported throughout the application, Settings language selector,
accessibility review, tests, and store-asset planning. A product explicitly configured as
`SINGLE_LANGUAGE` must choose one of these locales as its default, document the exception in product
context, and must not advertise unshipped translations. Multilingual applications may add locales
but may not silently remove baseline locales.

Repository-local Git identity is mandatory whenever the configured workflow authorizes Codex or an
external runner to commit. Configure it only inside the repository:

```text
git config user.name "Naimish Gupta"
git config user.email "naimishgupta983842377@gmail.com"
```

Never use `git config --global`.

### Placeholder-completion checklist

- [x] Identity, package, product, users, use cases, and processing model completed.
- [x] SDK, UI, architecture, dependency injection, persistence, background work, and navigation completed.
- [x] Form-factor, orientation, theme, accessibility, and localization values completed.
- [x] All 14 baseline locales are configured for app-wide resources and the Settings selector.
- [x] Advertising, billing, analytics, crash reporting, cloud, and store-asset capabilities completed.
- [x] Git workflow and repository-local identity completed without global configuration.
- [x] Every ad placement, trigger, cooldown, no-ad rule, first-session rule, consent provider, and debug mode completed.
- [x] Product-context path exists and feature queue is explicitly `NONE`.
- [x] Activated contract contains no unresolved required placeholders.

## 3. Authority and Precedence

Apply instructions in this order, from highest to lowest authority:

1. Current explicit user instruction
2. Applicable `AGENTS.md`
3. Activated `docs/codex/APP_SHARED_CONTRACT.md`
4. Configured product-context document
5. Current feature specification
6. Existing source code and tests
7. General conventions

Source code is the truth for the current implementation. Product context is the truth for product
intent. The current feature specification defines the only authorized change for that run. A
feature must not silently modify global rules; global decisions belong in this contract or product
context. Report material conflicts instead of guessing. A lower-precedence source cannot broaden or
override a higher-precedence instruction.

## 4. Token-Efficient Codex Operation

- Read the activated shared contract once per task.
- Do not reproduce the entire contract in plans or final reports.
- Do not ask the user to paste documented context again.
- Use `rg`, `rg --files`, and targeted file inspection.
- Avoid repeatedly scanning unchanged directories.
- Inspect only modules relevant to the feature.
- Run scoped tests during implementation.
- Run broader verification once before completion when the change's risk requires it.
- Reuse existing build outputs where safe and valid.
- Keep progress updates concise.
- Do not write a long research document unless requested or required.
- Do not repeat completed work.
- Do not begin unrelated roadmap features.
- Stop when the current feature's acceptance criteria are satisfied.

## 5. Git and Feature Workflow

The App Profile must select exactly one workflow mode. Never perform a Git action that an applicable
`AGENTS.md` assigns to the user or an external runner.

### `feature-branch`

1. Start from a clean `main`.
2. Pull with fast-forward-only behavior.
3. Create the exact assigned branch.
4. Implement only the assigned feature.
5. Add focused tests.
6. Run relevant verification.
7. Review for unrelated changes.
8. Create one intentional feature commit using the repository-local identity.
9. Push without force-pushing.
10. Merge only after verification succeeds and required review is complete.
11. Pull and verify `main`.
12. Start the next feature only after the current feature is merged.

### `main-only`

- Work directly on `main` only when the App Profile explicitly selects this mode.
- Pull with fast-forward-only behavior.
- Preserve unrelated changes.
- Create focused commits with the repository-local identity.
- Never force-push.

### `external-runner`

- Codex must not stage, commit, switch branches, pull, push, merge, open pull requests, or update
  queue status.
- Codex may inspect Git state read-only only when applicable repository instructions permit it.
- Codex must report required runner actions and the verification performed.
- The configured external runner owns every Git action listed in the App Profile.

### Prohibited in every mode

Never:

- Mix queued features.
- Force-push.
- Rewrite published history.
- Discard unrelated changes.
- Resolve uncertain conflicts automatically.
- Merge with failing scoped tests.
- Bypass branch protection.
- Continue through security or licensing blockers.
- Use global Git identity configuration.

## 6. Scope and Change Discipline

Every feature specification must provide:

- Feature ID
- Branch, when applicable
- User problem
- Desired outcome
- In-scope behavior
- Out-of-scope behavior
- Acceptance criteria
- Required tests
- Dependencies or blockers

Codex must:

- Preserve the current architecture unless the feature requires an approved change.
- Avoid broad rewrites and unrelated cleanup.
- Avoid unnecessary dependencies.
- Preserve unrelated behavior and user data.
- Keep public APIs stable unless change is required.
- Avoid changing ads, analytics, billing, privacy, or consent behavior incidentally.
- Never expose secrets or sensitive user data.
- Never add unrequested cloud processing.
- Never change production identifiers without explicit authorization.
- Inspect dependency licensing, maintenance, security, size, and runtime behavior before adoption.

## 7. Production UI Contract

Use production-quality Material 3 UI and the repository's existing design system. Require:

- Consistent design tokens.
- Light, dark, and system themes when configured.
- Dynamic Color when enabled.
- Responsive phone and tablet layouts.
- Foldable support when enabled.
- Predictable navigation and visible hierarchy.
- Touch targets of at least 48 dp unless an official platform component has a justified exception.
- Accessible contrast and scalable typography.
- Screen-reader labels, logical traversal, and meaningful state announcements.
- Keyboard and hardware navigation where applicable.
- Smooth, purposeful animation that respects reduced-motion preferences where available.
- Visible pressed, selected, disabled, and focused states.
- No overlapping controls, blank screens, unexplained disabled actions, loading layout jumps, or
  main-thread blocking during long operations.

Every data-driven screen must model and render:

- Loading
- Empty
- Content
- Recoverable error
- Terminal error where relevant

Preserve appropriate state across rotation, process recreation, background/foreground transitions,
configuration changes, and navigation away and back. Never expose routes or actions that lead only
to placeholders or incomplete workflows.

## 8. Localization Contract

Ship one application and one package, not separate applications per language. Ratings, reviews,
package identity, billing configuration, and AdMob configuration remain attached to that one package
across locales.

The multilingual baseline is:

| Language | Locale code | Direction | Required throughout app |
| --- | --- | --- | --- |
| English | `en` | LTR | Yes |
| German | `de` | LTR | Yes |
| French | `fr` | LTR | Yes |
| Japanese | `ja` | LTR | Yes |
| Hindi | `hi` | LTR | Yes |
| Russian | `ru` | LTR | Yes |
| Spanish | `es` | LTR | Yes |
| Portuguese (Portugal) | `pt-PT` | LTR | Yes |
| Portuguese (Brazil) | `pt-BR` | LTR | Yes |
| Italian | `it` | LTR | Yes |
| Indonesian | `id` | LTR | Yes |
| Arabic | `ar` | RTL | Yes |
| Korean | `ko` | LTR | Yes |
| Urdu | `ur` | RTL | Yes |

Applications must support:

- System-default language.
- User-selected language.
- Immediate language switching or clearly documented restart/recreation behavior.
- Safe fallback to the configured default locale.
- No hardcoded user-facing strings.
- Android string resources, plurals, and parameterized strings.
- Locale-aware dates, times, numbers, currency where applicable, and file sizes.
- RTL mirroring, shaping, alignment, focus order, and directional-icon review where applicable.
- Text expansion, truncation, wrapping, font/glyph, and pseudo-localization testing.
- Accessibility in every shipped language.
- Translation parity for all shipped user-facing strings, including errors, permissions,
  notifications, widgets, shortcuts, and accessibility labels.

In-app language and Play Store listing language are separate. Store titles, descriptions,
screenshots, and feature graphics may be localized while retaining the same package, ratings,
reviews, and AdMob setup. Translations and store copy must accurately describe shipped
functionality.

### Localization completion matrix

Copy one row per supported locale, retaining all 14 baseline rows for multilingual applications:

| Locale | Android strings | Plurals/formatting | In-app switch | RTL/layout | Accessibility | Phone assets | 7-inch assets | 10-inch assets | Feature graphic | Status |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `en` | ☐ | ☐ | ☐ | N/A | ☐ | ☐ | ☐ | ☐ | ☐ | IN PROGRESS |
| `de` | ☐ | ☐ | ☐ | N/A | ☐ | ☐ | ☐ | ☐ | ☐ | IN PROGRESS |
| `fr` | ☐ | ☐ | ☐ | N/A | ☐ | ☐ | ☐ | ☐ | ☐ | IN PROGRESS |
| `ja` | ☐ | ☐ | ☐ | N/A | ☐ | ☐ | ☐ | ☐ | ☐ | IN PROGRESS |
| `hi` | ☐ | ☐ | ☐ | N/A | ☐ | ☐ | ☐ | ☐ | ☐ | IN PROGRESS |
| `ru` | ☐ | ☐ | ☐ | N/A | ☐ | ☐ | ☐ | ☐ | ☐ | IN PROGRESS |
| `es` | ☐ | ☐ | ☐ | N/A | ☐ | ☐ | ☐ | ☐ | ☐ | IN PROGRESS |
| `pt-PT` | ☐ | ☐ | ☐ | N/A | ☐ | ☐ | ☐ | ☐ | ☐ | IN PROGRESS |
| `pt-BR` | ☐ | ☐ | ☐ | N/A | ☐ | ☐ | ☐ | ☐ | ☐ | IN PROGRESS |
| `it` | ☐ | ☐ | ☐ | N/A | ☐ | ☐ | ☐ | ☐ | ☐ | IN PROGRESS |
| `id` | ☐ | ☐ | ☐ | N/A | ☐ | ☐ | ☐ | ☐ | ☐ | IN PROGRESS |
| `ar` | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | IN PROGRESS |
| `ko` | ☐ | ☐ | ☐ | N/A | ☐ | ☐ | ☐ | ☐ | ☐ | IN PROGRESS |
| `ur` | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | IN PROGRESS |

Use `IN PROGRESS`, `VERIFIED`, or `COMPLETE` as explicit status values. Do not mark a row complete
while any applicable cell is unchecked.

## 9. Settings Screen Contract

Settings is capability-driven. Show only sections supported by the App Profile and shipped
behavior. Candidate sections include:

- Language
- Appearance
- System, Light, and Dark theme
- Dynamic Color
- Privacy and data
- Advertising and privacy choices
- Storage and export
- Permissions
- Notifications, when applicable
- Account, when applicable
- Subscription or purchases, when applicable
- Restore purchases, when applicable
- Help and feedback
- Share app
- Rate app
- Privacy policy
- Terms of service
- Open-source licences
- Application version
- About

Settings must:

- Show only relevant, functional controls.
- Never display nonfunctional placeholders.
- Persist choices reliably and migrate them safely.
- Use clear labels and summaries that describe consequences.
- Confirm destructive actions.
- Keep required privacy and consent choices discoverable and accessible.
- Avoid duplicate controls and conflicting sources of truth.
- Be searchable only when its size justifies search.
- Restore state across lifecycle changes.
- Test theme, locale, account, billing, consent, and notification changes when enabled.
- Present all configured baseline languages in the language selector for multilingual apps.

## 10. Privacy and Security Contract

Require:

- Data minimization and purpose limitation.
- Local processing when configured.
- No accidental uploads or undisclosed network processing.
- No sensitive content or identifiers in logs, analytics, crash reports, ad requests, filenames,
  saved state, or telemetry unless explicitly authorized and disclosed.
- No document, photo, audio, video, contact, message, location, or filename analytics unless
  explicitly authorized and disclosed.
- Secure content-URI handling and temporary grants.
- Correct `FileProvider` configuration; never expose `file://` URIs.
- Minimum required permissions and graceful denial behavior.
- No broad storage permission when a system picker or scoped alternative is safer.
- Encryption in transit for transmitted data and appropriate encryption at rest where required.
- Secure secret handling through approved local or CI configuration.
- No API keys, signing material, tokens, passwords, or production secrets in committed source.
- Dependency and licence review before adoption and at release.
- Privacy-policy consistency with actual app and SDK behavior.
- Accurate Google Play Data Safety declarations.

At release, audit and disclose the behavior of every enabled:

- Advertising SDK
- Analytics SDK
- Crash-reporting SDK
- Attribution SDK
- Billing SDK
- Cloud SDK
- Social SDK
- AI SDK

Do not assume files are "collected" merely because they are processed on-device and never leave the
device. Conversely, do not describe data as on-device-only if an enabled SDK, backup mechanism,
account sync, or cloud service transmits it. Resolve privacy or security uncertainty before release.

## 11. AdMob and Advertising Contract

This section applies only when the App Profile enables AdMob or another advertising capability.
When advertising is disabled, do not ship SDKs, permissions, Settings entries, containers, or copy
that imply advertising unless needed for a documented migration.

When AdMob is enabled:

- Use official test ad-unit IDs in debug builds.
- Use production IDs only in release builds and secure configuration.
- Never interchange application IDs and ad-unit IDs.
- Never print production IDs in logs or reports.
- Implement consent before requesting ads where required.
- Provide an accessible advertising privacy-options entry point.
- Account for the EEA, UK, Switzerland, and every other applicable consent region.
- Declare Advertising ID usage accurately.
- Include `AD_ID` only when required by the actual SDK configuration and policy.
- Keep Data Safety answers consistent with actual SDK behavior.
- Isolate advertising from sensitive content and workflows.

Default safe advertising rules:

- No app-open ad for first-time users.
- Do not show app-open and interstitial ads back-to-back.
- Interstitials occur only after successful workflow completion.
- Interstitial frequency and cooldown come only from the App Profile.
- Never show an interstitial before Save, Open, or Share.
- Never interrupt reading, editing, scanning, recording, processing, password entry, authentication,
  payment, permission, or consent dialogs.
- Never cover navigation or primary controls.
- Native and banner ads must be clearly distinguishable from application content.
- Ads must not cause content, layout, scroll-position, or focus jumps.
- Failed, unavailable, offline, or no-fill ads must not block a workflow.
- No ad is more important than task completion, user safety, or data integrity.
- Do not change placement, frequency, formats, consent behavior, or identifiers during unrelated
  features.

The App Profile must explicitly configure banner placements, native placements, interstitial trigger
count, interstitial cooldown, app-open cooldown, rewarded-ad purpose, no-ad screens, no-ad workflows,
first-session behavior, consent provider, and debug test mode. `NONE` is a valid explicit value when
a capability is disabled. Never invent production identifiers.

## 12. Absolute No-Ads Store-Asset Rule

No Play Store or marketing asset may contain:

- Banner ads
- Native ads
- Interstitial ads
- App-open ads
- Rewarded ads
- Test ads
- Sponsored labels
- Empty ad containers
- Advertisement placeholders
- Advertisement loading states
- Third-party advertiser content

This applies to phone, 7-inch tablet, 10-inch tablet, foldable, and Chromebook screenshots; feature
graphics; app-icon previews; marketing mockups; and contact sheets.

Use an ad-disabled asset-capture build, harness, or deterministic capture configuration that neither
initializes the advertising SDK nor reserves ad layout. This test-only setup must not change release
advertising behavior. Every final raster and contact sheet must receive an explicit manual QA result:

`NO ADS: VERIFIED`

## 13. Play Store Asset Contract

Verify current official Google Play specifications immediately before export or upload. Do not rely
on dimensions, file-size limits, device categories, or policy wording copied from an old project.

The reusable asset pipeline must support, where genuinely applicable:

- App icon
- Adaptive launcher icon
- Monochrome themed icon
- Feature graphic
- Phone screenshots
- 7-inch tablet screenshots
- 10-inch tablet screenshots
- Foldable assets
- Chromebook assets
- Other form factors only when the application genuinely supports them
- Localized store copy
- Localized graphics

For every supported listing language, maintain separate folders for:

- `phone/`
- `tablet-7/`
- `tablet-10/`
- `feature-graphic/`

Each asset must have the correct locale and in-app language, authentic UI, truthful feature
representation, correct dimensions and color profile, readable text, safe margins, alt text, and an
asset-inventory entry. It must contain no fabricated features, fake values, fake ratings or download
claims, competitor trademarks, personal data, copyrighted sample content, ads, debug indicators, or
placeholders.

Create contact sheets and visually inspect both them and original-resolution exports for clipping,
layout, glyphs, contrast, alignment, directionality, authenticity, and no-ad compliance. Screenshots
may be polished promotional compositions using authentic UI fragments; they need not be plain
emulator screenshots.

Use different safe sample content across creatives, benefit-focused headlines, genuine
before-and-after comparisons only when the application produces the transformation, and actual
measured output values. Create device-specific layouts instead of stretching phone screenshots.
Do not claim completion with templates or editable sources alone: final rendered PNG or JPG outputs
are required.

The 14 baseline locales must be considered throughout store-asset planning. Store listing locale
codes may be more region-specific than in-app locale tags; document and validate that mapping rather
than changing the app's language model implicitly.

## 14. App Icon Contract

Require:

- Editable vector source.
- A 512×512 Play Store PNG.
- sRGB color profile.
- Validation against the current official maximum file size.
- No baked rounded corners or outer shadow.
- Adaptive foreground and background layers.
- A monochrome themed-icon layer.
- Legacy fallbacks for supported Android versions.
- Small-size previews.
- Android mask previews.
- Light and dark surface previews.
- Splash-screen verification.
- A distinctive original design with no competitor imitation.
- Store-listing experiment assets when experimentation is planned.

An icon cannot guarantee installs. Treat designs as hypotheses and validate conversion through a
controlled Play Store experiment while monitoring retention, uninstall, ratings, and stability
guardrails.

## 15. Long-Running Work Contract

For any task exceeding approximately one second:

- Never block the main thread.
- Show real, determinate progress where measurable.
- Show indeterminate progress only when measurement is unavailable.
- Support cancellation where safe.
- Prevent duplicate submission.
- Handle background and foreground transitions.
- Preserve partial work only where safe and appropriate.
- Use the configured background-work mechanism.
- Clearly report success and the output location or resulting state.
- Explain errors specifically and provide a recovery action where possible.
- Never show only "Something went wrong."

Reusable error categories include:

- Permission denied
- Invalid input
- Unsupported format
- Corrupted input
- Incorrect password
- Insufficient storage
- Network unavailable
- Server failure
- Operation cancelled
- Output creation failure

Include only categories relevant to the current application and workflow. Cancellation must release
owned resources, stop avoidable work, clean unsafe partial output where practical, and never report
false success.

## 16. Performance and Reliability Contract

Require:

- No main-thread I/O or expensive computation.
- No avoidable memory spikes.
- Streaming, paging, lazy loading, and bounded caches where appropriate.
- Bitmap, camera, audio, video, and media memory discipline.
- Large-input and low-RAM testing proportional to the product.
- No ANRs or crashes in supported workflows.
- No corrupted output, silent data loss, or false success.
- Stable repeated workflows and idempotent retry where appropriate.
- Correct process-death recovery and stale-result rejection.
- Correct lifecycle ownership and structured cancellation.
- No leaked activities, views, contexts, streams, descriptors, cursors, workers, or native resources.
- Transactional output or safe temporary files where data integrity requires it.

Never claim a device, input size, throughput, startup, battery, or memory target without measured
evidence.

## 17. Testing Contract

Verification must be proportional to risk and must report exact commands, environments, results, and
untested constraints.

### Every feature

- Focused unit tests
- State-transition tests
- Error-path tests
- Relevant UI tests
- Accessibility checks when UI changes

### Workflow features

- Happy path
- Cancellation
- Retry
- Back navigation
- Background/foreground
- Rotation
- Process recreation where relevant
- Low storage
- Invalid input
- Repeated execution

### Localization changes

- Default locale
- At least one long-text locale
- Arabic or Urdu RTL behavior when multilingual localization is enabled
- Missing-translation fallback
- Locale switch
- Date, number, and file-size formatting
- Resource parity across all 14 baseline locales

### Advertising changes

- Debug test IDs
- Consent accepted
- Consent denied
- No-fill
- Load failure
- Frequency cap
- Cooldown
- First-session exclusion
- No-ad workflow verification
- Lifecycle transitions

### Store assets

- Dimensions
- Color profile
- Locale
- No ads and explicit `NO ADS: VERIFIED`
- No personal data
- No placeholders
- Text clipping and glyph rendering
- Authentic feature representation
- Visual contact-sheet and original-resolution review

Use repository-provided linting, unit, instrumentation, screenshot, asset-validation, and release
checks when relevant. Do not claim a test passed if it did not execute.

## 18. Definition of Done

A feature is complete only when:

- Acceptance criteria are satisfied.
- Relevant tests and verification pass.
- Error states exist.
- Loading and empty states exist where relevant.
- Accessibility is reviewed.
- Localization is complete across configured locales.
- No unrelated behavior changed.
- No secrets were introduced.
- Privacy policy and Data Safety declarations remain accurate.
- Advertising and consent behavior remain compliant.
- Store assets are updated when the feature materially changes their UI or claims.
- The diff is focused.
- Remaining limitations and untested cases are reported.
- Git workflow is completed according to the configured mode and applicable `AGENTS.md` ownership.

If any required condition is blocked, report the blocker honestly rather than claiming completion.

## 19. Final Response Contract

Codex's final response must report only:

- Outcome
- Important implementation decisions
- Files changed
- Tests and verification
- Known limitations
- Manual steps
- Git status, commit, and push/merge status where applicable and permitted

Do not repeat the shared contract in the final response. Do not imply that Git, device, release, or
store-console work was performed when it was not.

## 20. Minimal Future Prompts

### New feature

```text
Read AGENTS.md, docs/codex/APP_SHARED_CONTRACT.md and the configured product-context file.

Feature ID: [ID]
Branch: [BRANCH]
Outcome: [USER-VISIBLE OUTCOME]

Acceptance criteria:
- [CRITERION]
- [CRITERION]

Implement and verify this feature end to end.
```

### Bug fix

```text
Read AGENTS.md, docs/codex/APP_SHARED_CONTRACT.md and the configured product-context file.

Issue: [TITLE]

Steps:
1. [STEP]

Current:
[CURRENT BEHAVIOR]

Expected:
[EXPECTED BEHAVIOR]

Evidence:
[LOG/SCREENSHOT/VIDEO PATH]

Diagnose, implement the smallest production-ready fix and add regression coverage.
```

### UI production pass

```text
Read AGENTS.md, docs/codex/APP_SHARED_CONTRACT.md and the configured product-context file.

Improve production quality of: [SCREEN/FLOW]

Preserve:
- Business logic
- Navigation behavior
- Persistence
- Advertising behavior
- Existing identifiers

Acceptance criteria:
- [UI REQUIREMENT]
- [ACCESSIBILITY REQUIREMENT]
- [RESPONSIVE REQUIREMENT]

Implement and verify.
```

### Play Store assets

```text
Read AGENTS.md, docs/codex/APP_SHARED_CONTRACT.md and the configured product-context file.

Generate final Play Store assets for all configured locales and supported device categories.

Use authentic localized UI, different safe sample content and genuine before/after comparisons where applicable.

No asset may contain an ad, test ad, sponsored content or empty ad container.

Render, visually inspect and validate every final asset.
```
