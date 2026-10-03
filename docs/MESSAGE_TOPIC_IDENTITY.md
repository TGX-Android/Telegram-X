# Message topic identity

This is the identity and navigation foundation for forum support, not a topic-list or forum-history implementation.

## Contract

- A conversation context includes the TDLib account, chat, complete `MessageTopic` value and scheduled/normal mode. Navigation compares topics exactly with the existing generated `Td.equalsTo`; null identifies the whole-chat view and is not a wildcard. Generic `ThreadInfo` navigation also retains its comment/context-chat identity.
- Message membership is different: `Td.matchesTopic(messageTopic, viewingTopic)` deliberately accepts all topics when `viewingTopic` is null. Do not use it to deduplicate screens or route recording results.
- `MessageTopics.effectiveTopic` preserves the existing generic-thread precedence and otherwise returns the explicit topic. Controller, loader, search/source selection and scroll/anchor callers now agree on this value. A forum topic does not need a `ThreadInfo` wrapper.
- Topic constructors are part of identity. A forum ID is an `int`; generic-thread, direct-message and saved-message IDs are `long`. Equal numbers in different constructors are not interchangeable. No generated TDLib code or submodule is modified.

## Saved positions and anchors

`ScrollStateKey` preserves the existing account/chat/field/typed-topic key format. Reads accept only exact keys for the requested account, chat and topic. This also rejects numeric chat-prefix collisions and keeps negative saved-message topic IDs separate from the whole-chat view. Message-ID acknowledgement remapping recognizes all four topic types, including anchor aliases and the return stack.

Only normal history persists positions; scheduled/search/event-log modes do not share that position. Scheduled opening does not restore normal-history or unread anchors. Until topic read state is available, explicit topic views do not borrow the whole-chat unread anchor. Topic-specific unread positioning and history loading belong to the subsequent history work.

## Forum updates

`ForumTopicInfoListener.onForumTopicUpdated` receives the complete `TdApi.UpdateForumTopic`, including mention/reaction/poll-vote counters and a nullable draft. Consumers must not mutate the update. Both subscription and dispatch use `(chatId, int forumTopicId)`; topic-specific, chat-wide and global listeners retain their existing routing.

## Regression tests

```text
./gradlew :app:testLatestArm64DebugUnitTest
./gradlew :app:assembleLatestArm64Debug
```

Use `gradlew.bat` on Windows with the normal local build configuration. Tests use synthetic in-memory values only, with no account credentials, network calls or device fixtures:

- `MessageTopicsTest`: nine tests for null, constructors, numeric ID boundaries, exact navigation, wildcard membership, effective-topic resolution and generated cache keys.
- `ScrollStateKeyTest`: seven tests covering existing key compatibility, isolation across accounts/chats/topics/fields, signed IDs, prefix collisions and acknowledgement remapping.
- `ForumTopicUpdatesTest`: three tests for full-update delivery, precise subscription routing and unsubscription.
- `TopicAnchorTest`: a regression test preventing whole-chat unread anchors from being used in topic views.

Device acceptance of a topic list, topic-to-topic navigation, drafts and history will be performed when those UI/data paths are implemented. There is no topics feature flag.
