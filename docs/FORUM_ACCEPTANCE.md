# Forum regression and acceptance checklist

Forum support has no feature flag. Automated checks are necessary but do not replace device/server acceptance. Do not publish private chat links, topic names/IDs, account/device identifiers, messages, credentials, APKs or diagnostic captures with the change.

## Automated regression coverage

Use the configured SDK/JDK and ignored local credentials, never command-line credentials:

```text
./gradlew :app:testLatestArm64DebugUnitTest :app:assembleLatestArm64Debug
./gradlew :app:lintLatestArm64Debug
```

Fork-specific Windows-host and TDLib-prebuilt validation are separate from this feature and must not be included in its upstream PR.

The suite covers typed identity and routing, nullable draft overrides and persistence, forum history/album filtering, pagination/search, errors/timeouts/reconnect, old requests after reset, permissions, operation reconciliation, common-stream replies and mute inheritance. `ForumTopicStressTest` additionally loads up to 1000 synthetic topics, exercises 10000 message events, bounds concurrent topic fetches, closes hundreds of observers, and repeats account-cache resets. Its assertions are deterministic request/publication counts, not machine-dependent timing limits.

Search regressions cover unrelated pinned rows, case-insensitive name matching, cursor progress through filtered pages and renamed topics. Pin tests preserve the server's pinned sequence independently of activity order, across pages and refreshes. Compact forum-service previews distinguish creation, editing, close/reopen and General hide/show.

### List-update performance

Repeated invalidations mark a list stale once; they do not sort and publish unchanged rows for every message. Metadata/counter changes still publish while stale. Known-topic bursts use coalesced `GetForumTopic` requests (maximum four in flight) without reloading the whole list. Unseen-topic bursts use one discovery refresh.

`ForumTopicListDiff` uses the immutable store-row identity and the full chat/topic key. A changed row does not rebind the other rows; the loading/error footer is updated separately. External changes such as theme, language, inherited mute and local drafts still explicitly rebind affected views.

On the same Windows JVM test host, one 1000-topic / 10000-event run changed from 10000 UI publications and 492 ms before the fix to 1 publication and 14 ms after it. An unseen-topic burst changed from 5000 publications to 1. These are model-processing samples, not Android frame-rate measurements or performance guarantees. Tests also verify one changed row in a 1000-row diff and move/removal behavior.

## Device/server acceptance

Record each case as **pass**, **fail**, or **not run**, with the build revision and the actual role/device. Never infer a device pass from JVM tests, a successful build, a `TDLib Ok`, or the acceptance of an earlier baseline APK.

| Area | Cases |
|---|---|
| List and navigation | Entry from chat list; General and hidden General; search/clear; pagination/retry; A → B; common stream ↔ topic list; topic button and Back |
| Topic actions | Creator/member/admin; create/edit/default and Premium emoji; close/reopen; pin/reorder limits; hidden General; server refusal and role revocation |
| Messaging and drafts | Text, photo/video/album, voice, sticker, poll, forward, scheduled; reply from common stream; independent A/B drafts; empty vs absent draft; typing destination |
| Read state | Per-topic inbox/outbox, mentions, reactions, poll votes and mute overrides; group counters are not sums over a partial list |
| Links and notifications | Topic/message links, inaccessible/deleted targets, notification tap, quick reply, foreground/background, inherited/overridden notifications |
| Lifecycle and network | Cold start with a pre-created controller before any chat is bound; warm/cold offline, reconnect, deletion while open, account switch/logout, process death, old replies after navigation, retry without loops |
| UI | Light/dark, RTL, large text, TalkBack, rotation, selection/long-press, editor/picker lifecycle, phone and tablet stacks |
| Performance | Long list scrolling/frame times, update bursts, observer lifetime, request counts and memory after repeated navigation |
| Non-forum regressions | Ordinary groups/private chats, channel comments, Saved Messages, Direct Messages, bot topics and secret chats |

Deletion, role changes, logout, message sending and notification-delivery tests require a suitable authorized test account/data set. Do not destroy existing user data merely to complete the checklist. Public before/after media must use a separate synthetic demonstration data set with no private identifiers or content.

## Maintainer-reported device acceptance - 2026-10-04

The maintainer manually tested the installed development-fork builds below. This is user-reported functional acceptance, not a new instrumentation run or an independent device retest. Source revisions come from the recorded build/install provenance.

| Device | Installed variant and source | Reported result |
|---|---|---|
| Pixel 6, modern Android, ARM64 | `latestArm64Debug`, `4fef04fb` | **PASS** for the performed manual checks; no errors reported |
| Pixel 6, modern Android, ARM64 | `latestArm64Release`, `6b3ee60e` | **PASS** for the performed manual checks; no errors reported |
| Android 4.2.1 / API 17 / armeabi-v7a | `legacyArm32Debug`, `6b3ee60e` | Login and the forum flows listed below **PASS**; one minor icon-color defect remains |

The legacy report covers both forum presentations: the common stream with topic selectors and the dedicated topic list with a chat rail.

| Legacy scenario | User-reported result |
|---|---|
| Sign in and load authenticated forum content | **PASS**; supersedes the earlier network-blocked, startup-only result |
| Topic previews and unread badges in the chat list, for both presentations | **PASS** for display; not a claim about every read-state scenario |
| Change topic-selector placement, switch topics, display messages | **PASS** |
| Open and use the create-topic interface | **PASS** for the interface; a new server-created topic was not separately reported |
| Display the dedicated topic list, enter topics and load their message histories | **PASS** |
| Display/scroll the chat rail and animate the transition | **PASS** |
| Display/filter topic attachments and copy the topic link | **PASS** for the exercised categories and link-copy flow |
| Theme colors of topic-profile action icons | **FAIL, minor visual issue**; tracked as P8-10 below, with no functional impact reported |

The old device is noticeably slow. The maintainer attributes this to its hardware; no benchmark or root-cause measurement was performed, so this is not performance acceptance.

These APKs belong to the development fork, not the feature-only PR APK at production-code revision `e19ed7b5`. The forum compatibility fixes are present in both branches, but their packaging and remaining changes differ. This report does not replace an exact-PR-APK runtime check, the final 153-case synthetic Android rerun, API 16 testing, or the remaining role-revocation, offline/process-death, live IME/mixed-topic push, ABI and tablet matrix. Existing legacy lint limitations are unchanged. Private screen photos and chat/account data are deliberately not included.

### P8-10 - Legacy topic-profile action icon colors

On Android 4.2.1, the Messages, Mute/Unmute and Pin/Unpin action icons in the topic profile could appear black while their labels and card backgrounds retained theme colors. The October 4 report recorded this as a cosmetic follow-up, with no functional failure reported.

The October 5 fix changes only `ForumTopicProfileController.ActionView` to `AppCompatTextView`. Before API 23, `TextViewCompat` compound-icon tint requires a support interface that the framework `TextView` does not implement. The existing semantic `ColorId.textLink`, theme listeners, icon replacement, disabled alpha, click handlers and accessibility role are retained. Topic actions, permissions and navigation are unchanged.

Acceptance: on API 17 and a modern device, action icons use the intended semantic theme color on first display, light/dark theme changes and mute/pin icon replacement; disabled/pressed states remain legible; actions and accessibility remain unchanged. Six account-isolated Android rendering checks pass, including the pre-23 support-tint path, live theme updates and independent drawable colors. On October 5 the maintainer also manually confirmed that the updated legacy APK now displays colored icons on Android 4.2.1. **P8-10 is fixed and accepted** for the reported defect; the manual report did not separately enumerate every theme/state combination.

## Upstream integration and regression rerun - 2026-10-05

Both branches merge upstream `805209e60e4bcccff5757ad5f0e8014f0dcbd7a0`, including upstream Windows support, AppContext/multidex startup fixes, the OpenGL intro fallback and FFmpeg `e594a518`. TDLib/OpenSSL pins are unchanged. The PR adds no native, submodule, branding or Windows-build changes relative to this new base.

- Production fix: development fork `4493005b`, feature-only PR `3c111fa3`. The topic-profile production file is identical in both.
- Modern JVM checks: **517/517**, 36 suites, zero failures/errors/skips, in each branch.
- Legacy JVM checks: **517/517**, 36 suites, zero failures/errors/skips, in each branch.
- PR synthetic Android checks at `1b16636a`: **159/159 PASS** on a physical API 37 device, including all six P8-10 checks. This supersedes the previous final-candidate instrumentation NOT RUN. The first rerun exposed one stale reflection reference in the test-only component gallery after upstream moved AppContext; it was adapted before the successful full rerun.
- PR modern Debug production-source lint: no new issues; the existing baseline filters 17 warnings. Test-source UAST remains excluded locally, with executable tests reported separately.
- PR normal ARM64 Release at `1b16636a`: compilation/packaging, explicit R8/resource shrinking and production-source Release lint pass. The unchanged baseline filters 17 warnings. This is an unsigned validation APK, not the signed development-fork Release installed on the device.
- Legacy production-source lint still reports **51 errors / 15 warnings**, plus 17 baseline-filtered warnings. Each of the 51 diagnostic source snippets is present unchanged in upstream `805209e6`; no issue is reported in the new forum classes. This is source comparison, not an independent lint run of a clean base, and no new baseline hides these findings.
- Fork modern ARM64 Debug/Release and legacy ARMv7 Debug builds pass after running their native build tasks against the updated source pins. Release R8/resource shrinking and production-source lint pass (17 existing baseline-filtered warnings). APK metadata, signatures, selected native binaries and modern 16 KiB alignment pass validation.
- All three fork APKs were installed as updates on their intended devices. Installed APK hashes match the verified artifacts and original UIDs/first-install timestamps are preserved. Modern Debug/Release cold starts pass. Legacy visual acceptance is maintainer-reported; an initial ADB wait timed out while two host ADB versions conflicted, so that timing is not a performance result.

Synthetic packages have a separate UID, no application components or network permissions and no account input; they were removed after the run. PR validation on Windows uses a local-only worktree harness and native libraries from the newly built fork at the same upstream pins; it is not an independent native build of the PR checkout. These results are not a new full server/role/lifecycle/ABI acceptance matrix or an API 16 device test. Private account data, device identifiers and captures are not published.

## Upstream integration and build verification - 2026-10-06

Both branches merge upstream `51a2ba25d3be54b656e4fcf5eea484fdab5820b1` (version 1816). This updates the primary NDK to `30.0.16248370`, tgcalls to `1a00b961`, WebRTC to `7b03082f` and the TDLib module to `33726bdd`. The TDLib source/API remains `42e6a525`; its Java API file is unchanged, while native prebuilts now target r30. FFmpeg remains `e594a518`.

- The development fork's product branch is now `main`; the feature-only PR source remains `upstream/forum-topics`. Product branding and Windows refinements are not merged into the PR branch.
- Product build source: `fd94dc5e`. PR build source: `9a0903af`. The subsequent acceptance-record commits change documentation only.
- Modern JVM checks in each branch: **517/517**, 36 suites, no failures/errors/skips.
- PR normal ARM64 Release compilation/packaging, explicit R8/resource shrinking and production-source Debug/Release lint pass. Lint reports no new issues, with the same 17 baseline-filtered warnings. The APK embeds the expected full PR commit; forum classes are represented in the archived R8 mapping.
- The unsigned PR validation APK includes seven supplied native inputs matching the new product native outputs at the same r30 pins. All 11 packaged native libraries pass ELF/ZIP 16 KiB alignment checks.
- The signed product ARM64 Release passes R8/resource shrinking and production-source Release lint (17 existing baseline-filtered warnings). APK identity, production/Firebase configuration, the existing v2/v3 certificate, native inputs and 16 KiB ZIP/ELF alignment pass validation. Its embedded full commit and all 11 packaged libraries match the new build outputs; APK and mapping are archived together.
- Fork build infrastructure: **29/29** tests, including the real MSYS2 check. **9/9** synthetic CMake TDLib resolver fixtures pass; these do not replace the actual r30 native build above.
- All 56 recursive submodules match their pins without tracked edits after the native build. The PR diff adds no gitlink, native or Windows-build changes relative to the new upstream base. Source endings retain upstream LF, with the prescribed CRLF checkout for `gradlew.bat`.

Qualification: PR checks use the same local-only Windows worktree harness, with Java/Kotlin/resources/tests from the PR checkout and native libraries from the newly built product at matching upstream pins. TDLib/OpenSSL remain pinned prebuilts. This is not an independent PR-native or Linux build. Test-source UAST remains excluded locally; executable JVM checks are reported separately.

The build-verification phase did not install APKs. In a subsequent maintainer-requested update, the signed product ARM64 Release at `fd94dc5e` was installed on the Pixel 6 (API 37). The installed APK hash matches the verified artifact, the original app UID/first-install timestamp are preserved, and the existing Debug package/hash/install metadata are unchanged. Explicit-activity cold launch passed; the process remained alive with no fatal exception observed for that process. This is an install/startup smoke check, not authenticated forum/server acceptance or a runtime test of the normal PR APK.

Modern Debug packaging, legacy builds/lint, the 159-case Android instrumentation suite and manual forum acceptance were not rerun on the October 6 native update. Their October 4-5 results above remain historical evidence, not validation of this exact new native candidate. Existing API 16, legacy-lint and wider role/lifecycle/ABI-matrix limitations remain open.

## Upstream PR packaging

The fork's main working branch includes separate Windows-build, TDLib-prebuilt selection and branding commits before the functional forum commits. Do not submit that combined branch directly upstream. Prepare the feature-only branch from current upstream `main`, select only the forum commits, review the complete diff and repeat the relevant checks. Any required build/TDLib fix should be handled as a clearly declared separate dependency, not hidden in the feature PR. Keep branding, local reports, build artifacts and private captures out.

Follow `PULL_REQUEST_TEMPLATE.md`. The PR description must distinguish implemented behavior, verified cases and remaining limitations. Do not check “Completed” or claim merge readiness while agreed acceptance gates remain outstanding. Topic tabs are implemented; see `FORUM_COMMON_STREAM.md`. Explicitly distinguish synthetic component renders from full-screen device/server acceptance.

### Account-isolated Android checks

The opt-in `-Pstage8.synthetic=true` build uses a separate `.stage8synthetic` application ID, a plain `Application`, no application components and no network permissions. It is a test target, not a runtime feature flag. Never install the instrumentation against the normal client. Build the target and instrumentation together with `:app:assembleLatestArm64Debug :app:assembleLatestArm64DebugAndroidTest`, then invoke the runner with `-e stage8Synthetic true`.

The runner validates the target before application initialization and tests real drawing/layout with synthetic fixtures. It covers light/dark rendering, RTL, larger fonts, transitions, independent receivers, tab placement/restoration, draft/media geometry and safe teardown. `ForumUpstreamDemo` exports three component-only PNGs under the synthetic target's private `files/forum-upstream-demo/` directory. These contain invented data and are not live account screenshots or evidence of server behavior.
