# SplitFrame Play Console Release Checklist

Prepared July 18, 2026. These answers match the repository and Google Mobile Ads SDK 25.4.0 at that date. Re-check the final merged release manifest and SDK disclosures before every submission.

## External Identity Decisions

- [x] Permanent package name confirmed and configured as `com.rameshta.splitframe`.
- [ ] Confirm that the Play developer name is `SplitFrame`, or replace `SplitFrame` consistently in the policy and store listing with the actual legal/publishing entity.
- [ ] Add a monitored developer-contact email in **Store presence > Store settings > Store listing contact details**. The privacy policy directs users to that email.

## Store Listing and Public URLs

- [ ] Upload `app-icon-512.png`, `feature-graphic-1024x500.png`, and the seven images under `screenshots/captioned/`.
- [ ] Publish `privacy-policy.html` as a normal, non-editable HTML page at an active, public, non-geofenced HTTPS URL. Do not use a PDF or a URL requiring sign-in.
- [ ] Enter that exact URL in **Policy and programs > App content > Privacy policy**.
- [ ] Verify the in-app **Privacy policy** control opens the same production URL.
- [ ] Add the publisher's HTTPS website to the Play listing's **Developer website** field.
- [ ] Copy `app-ads.txt` to the website root so it is available as `https://<developer-host>/app-ads.txt` with content type `text/plain`.
- [ ] Compare the file with the personalized snippet shown in **AdMob > Apps > app-ads.txt > How to set up app-ads.txt** before deployment.
- [ ] After the Play listing and website are public, request an app-ads.txt re-crawl in AdMob and wait for **Verified** status.

## App Content Declarations

- [ ] **Contains ads:** Yes.
- [ ] **App access:** All functionality is available without special access. No account, login, membership, location restriction, or access instructions are required.
- [ ] **Target audience:** Select the publisher's actual intended age groups. The current policy and implementation exclude children under 13; do not select an under-13 group without reassessing Families requirements, consent, SDK certification, ad content, and all store copy. If ages 13–17 are selected, keep the AdMob maximum content rating and served ads consistent with the final IARC rating.
- [ ] **News app:** No.
- [ ] **COVID-19/contact tracing:** No.
- [ ] **Data deletion / account deletion:** The app does not allow account creation, so the account-deletion URL requirement is not applicable. Do not claim that clearing app data deletes gallery exports or data held by Google.
- [ ] **Government app:** No.
- [ ] **Financial features:** No.
- [ ] **Health features:** No.
- [ ] **VPN service:** No.
- [ ] **User-generated content/social features:** No. Android share-sheet export is a user-initiated transfer, not an in-app social or UGC service.

## Advertising ID Declaration

- [ ] **Does the app use Advertising ID?** Yes. The Google Mobile Ads SDK merges `com.google.android.gms.permission.AD_ID` into the release manifest and uses advertising identifiers.
- [ ] Select the applicable purposes shown by Play Console: **Advertising or marketing**, **Analytics**, and **Fraud prevention, security, and compliance**.
- [ ] Confirm the final release manifest contains `com.google.android.gms.permission.AD_ID`; do not remove it while AdMob uses the identifier.

## Data Safety

### Top-level answers

- [ ] **Does the app collect or share any required user data types?** Yes.
- [ ] **Is all collected data encrypted in transit?** Yes, based on Google's TLS statement for the Mobile Ads SDK. Reassess if another network SDK is added.
- [ ] **Can users request that data be deleted?** No. SplitFrame has no developer server or account datastore, and the publisher cannot promise deletion of Google-held advertising data. Local app data can be cleared through Android; gallery exports must be deleted separately.
- [ ] **Independent security review:** No, unless one is completed before submission.
- [ ] **UPI payment verification:** Not applicable.

### Data types to declare

Declare each row below as both **collected** and **shared**, **required** rather than optional, and used for all three listed purposes. Google's SDK documents automatic collection and sharing; regional consent or limited ads do not guarantee that every user can use the ad-supported app with none of these transmissions.

| Play data type | Source | Collection/sharing | Purposes |
|---|---|---|---|
| Approximate location | IP address used to estimate general location | Collected and shared; required | Advertising or marketing; Analytics; Fraud prevention, security, and compliance |
| App interactions | App launches, taps, ad interactions, and video views | Collected and shared; required | Advertising or marketing; Analytics; Fraud prevention, security, and compliance |
| Diagnostics | Launch time, hang rate, energy usage, and SDK/app performance | Collected and shared; required | Advertising or marketing; Analytics; Fraud prevention, security, and compliance |
| Device or other IDs | Advertising ID, app set ID, and applicable device/account identifiers | Collected and shared; required | Advertising or marketing; Analytics; Fraud prevention, security, and compliance |

Do not declare selected **Photos** or **Videos** as collected solely because SplitFrame processes them on-device. Play defines collection as transmission off the device, and this app does not upload selected media to a SplitFrame server or AdMob. Reassess this answer if any future SDK, cloud backup feature controlled by the publisher, remote editor, crash attachment, or analytics event transmits media or its metadata.

## Content Rating

- [ ] Start the IARC questionnaire under **Policy and programs > App content > Content ratings** and select the non-game app category that best matches a photo/video editing utility.
- [ ] Answer **No** to violence, sexual content, offensive language, controlled substances, gambling, simulated gambling, user communication, user-generated public content, and location sharing, provided the final build adds none of those features.
- [ ] Disclose that the app contains third-party advertising where the questionnaire asks.
- [ ] After IARC assigns the rating, configure AdMob blocking controls and maximum ad content rating so served ads do not exceed the app's rating.
- [ ] Save the issued rating certificate and re-run the questionnaire whenever content or social features change.

## Foreground Service Declaration

- [ ] Confirm the final release manifest declares `dataSync|mediaProcessing`, with both matching foreground-service permissions. The worker uses `mediaProcessing` on Android 15/API 35 and later, `dataSync` on API 29–34 where `mediaProcessing` does not exist, and no typed foreground service on API 28 and earlier.
- [ ] In Play Console's foreground-service declaration, disclose both merged types. Choose **Media processing > Media transcoding** for current Android and explain that `dataSync` is the compatibility type used only for the same user-started export on API 29–34.
- [ ] Suggested functionality description: “After the user selects clips and taps Export, SplitFrame transcodes the selected on-device media into an MP4 and saves it to the user's gallery. Android may show its required foreground-service status only while the export runs.”
- [ ] Suggested user-impact statement: “Deferring the task delays the export the user explicitly requested. Interrupting it can leave the requested output incomplete and requires the user to retry.”
- [ ] Provide an unlisted or public video showing: select at least two clips, tap Export, put the app in the background, show that the export continues, then return to the completed saved result.
- [ ] Attach the same user-started export evidence to every foreground-service type that Play Console requires you to declare; do not omit `dataSync` when it remains in the merged manifest for older Android versions.

## Permissions and Release Artifact Checks

- [ ] On Android 7–9, complete a real-device/emulator export test and confirm any legacy storage permission is requested only when needed and denied permission produces a clear, recoverable message.
- [ ] On Android 13+, verify the app requests no notification permission and background export still completes without a terminal notification.
- [ ] Inspect the final release manifest from the AAB, not only `src/main/AndroidManifest.xml`, because libraries merge permissions and components.
- [ ] Verify the production AAB contains the production AdMob app ID and all four production unit IDs, while debug builds continue to use Google's test IDs.
- [ ] Verify the release AAB is signed separately; signing is intentionally outside this repository checklist's implementation work.

## Final Console and AdMob Checks

- [ ] Complete the store listing contact fields, app category/tags, content rating, target audience, app access, ads, Advertising ID, Data Safety, privacy policy, and foreground-service sections with no drafts remaining.
- [ ] Link the published Play listing to the AdMob app entry.
- [ ] Confirm app-ads.txt verification and AdMob app-readiness review.
- [ ] If the developer account is subject to a pre-production testing requirement, complete the currently displayed Play Console requirement before applying for production access.
- [ ] Run Play pre-launch reports and review crashes, ANRs, accessibility findings, security findings, and policy warnings before production rollout.

## Source References

- Google Play User Data policy: https://support.google.com/googleplay/android-developer/answer/10144311
- Google Play Data Safety guidance: https://support.google.com/googleplay/android-developer/answer/10787469
- Google Mobile Ads SDK Data Safety disclosure: https://developers.google.com/admob/android/privacy/play-data-disclosure
- Advertising ID guidance: https://support.google.com/googleplay/android-developer/answer/6048248
- Foreground-service declarations: https://support.google.com/googleplay/android-developer/answer/13392821
- App preview asset requirements: https://support.google.com/googleplay/android-developer/answer/9866151
- AdMob app-ads.txt setup: https://support.google.com/admob/answer/9363762
