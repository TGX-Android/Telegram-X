# Forum topic history and drafts

This layer supports an explicitly opened `MessageTopicForum` in the existing
messages controller. The topic-list entry point, deep links and notification
navigation are separate work; no feature flag or synthetic `ThreadInfo` is used.

## History boundary

- Initial history, older/newer pages, message anchors and album boundary requests
  use `GetForumTopicHistory`, which returns `Messages` and has no `onlyLocal` flag.
- `ForumHistory` filters forum responses by chat, full topic identity and scheduled
  mode before any album merging. Scheduled history is fetched with the existing
  chat API, then filtered; its displayed count is scoped to the topic.
- Search, pinned messages, shared media, sends and typing carry the typed topic.
  A reply from another topic cannot redirect an outgoing message from this view.
  Bulk unpin uses `UnpinAllForumTopicMessages`. Local pin dismissal has its own
  topic key, independent of the group's dismissal state.
- Album fetching never uses whole-chat local history as a forum fallback. The
  local album pass returns the known items; the remote pass uses forum history.
- History/album results are guarded by the loader epoch. Switching chat/topic
  invalidates old requests immediately, even before the new initial request.
  History errors preserve pagination state and have an explicit retry action;
  they are not converted into an empty/exhausted result.

## Read state and lifecycle

`ForumTopicContext` belongs to one controller, chat and topic. Its full topic
comes from the account's `TdlibForumTopicManager` subscription. It never borrows
read IDs, last message, unread counts or drafts from the group.

Automatic entry waits for topic metadata before choosing an unread anchor or the
existing topic-specific saved scroll position. Explicit message anchors retain
priority. Scheduled messages do not restore unread/scroll anchors or save drafts.
The viewport reports `MessageSourceForumTopicHistory`; topic updates refresh
inbox/outbox state and counters. Cold metadata failures expose retry rather than
promising an offline topic cache.

Subscriptions are closed on reuse/destruction and renewed after TDLib startup
following a restart. Replies requested for a draft are guarded by the controller
epoch and local edits. Account cleanup drops topic state and prevents the old
controller from writing its draft back into cleared settings.

## Draft precedence

1. A pending local draft for this account/chat/topic, including an explicit clear.
2. This topic's nullable `ForumTopic.draftMessage`.
3. Empty input. **Never the group's draft.**

`InputView.setChat` accepts an authoritative nullable draft; null is not a request
to fall back. Remote topic drafts are applied only while input is untouched and
not editing a message. A local edit, closed topic, failed request or deletion
must not replace the text with a remote/group draft.

On blur, controller reuse or destruction, text drafts are checkpointed to the
existing account-scoped settings store before `SetChatDraftMessage`. `TdBundle`
preserves the text entities, reply/quote and link-preview fields; `ForumDraftCodec`
stores its primitive fields in a deterministic, versioned binary format (not an
Android Parcel). An acknowledgement only removes its exact stored snapshot, so
an older acknowledgement cannot erase a newer pending draft. Failed saves remain
available on reopening. Pending drafts are removed on account cleanup and are
never logged. This is not autosave on every keystroke; unsaved process termination
and the existing rich-media/edit-draft limitations are not covered here.

## Header and availability

The header shows the topic name and a colored letter/General icon or a custom
emoji using the existing text-media renderer. Topic updates change it in place.
Closed status is shown even for a topic creator/administrator who can still send.
The send guard follows TDLib's topic-creator/`canManageTopics` exception in a
supergroup, in addition to the existing chat permissions. Missing/inaccessible
topics and transient errors have an explicit retry state; the input text is kept.

## Validation boundary

`ForumHistoryTest` and `ForumDraftCodecTest` use synthetic data, without network,
JNI or a real account. They cover request routing, paging offsets, membership,
scheduled filtering, album isolation, reply routing, draft precedence and clears,
late save acknowledgements, read anchors, closed-topic permissions, error recovery,
search arguments, binary round trips and corrupt input. Existing store, identity,
listener and scroll-key tests remain part of the suite.

JVM tests and an ARM64 debug build do not establish device acceptance. The
controller/rendering/Android settings integration still needs a device matrix
once the topic-list navigation is connected: opening A/B/General, bidirectional
draft sync and clears, independent read/scroll state, albums and media sends,
closed/deleted topics while typing, retry, custom emoji, process recreation and
ordinary chat/thread regressions. Use disposable synthetic fixtures and keep
account data, screenshots, credentials, device identifiers and APKs out of Git.
