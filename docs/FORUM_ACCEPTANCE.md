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

## Upstream PR packaging

The fork's main working branch includes separate Windows-build, TDLib-prebuilt selection and branding commits before the functional forum commits. Do not submit that combined branch directly upstream. Prepare the feature-only branch from current upstream `main`, select only the forum commits, review the complete diff and repeat the relevant checks. Any required build/TDLib fix should be handled as a clearly declared separate dependency, not hidden in the feature PR. Keep branding, local reports, build artifacts and private captures out.

Follow `PULL_REQUEST_TEMPLATE.md`. The PR description must distinguish implemented behavior, verified cases and remaining limitations. Do not check “Completed” or claim merge readiness while agreed acceptance gates remain outstanding. Topic tabs are implemented; see `FORUM_COMMON_STREAM.md`. Explicitly distinguish synthetic component renders from full-screen device/server acceptance.

### Account-isolated Android checks

The opt-in `-Pstage8.synthetic=true` build uses a separate `.stage8synthetic` application ID, a plain `Application`, no application components and no network permissions. It is a test target, not a runtime feature flag. Never install the instrumentation against the normal client. Build the target and instrumentation together with `:app:assembleLatestArm64Debug :app:assembleLatestArm64DebugAndroidTest`, then invoke the runner with `-e stage8Synthetic true`.

The runner validates the target before application initialization and tests real drawing/layout with synthetic fixtures. It covers light/dark rendering, RTL, larger fonts, transitions, independent receivers, tab placement/restoration, draft/media geometry and safe teardown. `ForumUpstreamDemo` exports three component-only PNGs under the synthetic target's private `files/forum-upstream-demo/` directory. These contain invented data and are not live account screenshots or evidence of server behavior.
