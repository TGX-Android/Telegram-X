# Forum topic list and navigation

`ForumTopicsController` consumes the account's `ForumTopicStore.ListSession`. It does not implement another cache or issue its own topic pagination requests. The session owns refresh, retry, search and cursor pagination; the controller discards callbacks from an old session/query and closes its subscriptions on destruction. TDLib restart reopens the session; logout clears displayed rows.

## List

- Rows follow TDLib's descending `order`, retain stable topic ids and use RecyclerView diff updates.
- General and hidden/closed/pinned states are displayed, including a renamed General. Hidden topics are not silently filtered out of server results.
- Icons use a colored letter/General symbol or custom emoji with attach/detach-aware media receivers.
- Previews use existing Telegram X content descriptions. Text drafts have priority, including locally pending topic drafts and explicit clears. Group drafts never substitute for a topic draft.
- Message, mention, reaction and poll-vote counters belong to the topic. Muted badge coloring uses the topic override or inherited chat setting.
- Initial loading, refresh, pagination, empty results, cached/stale data and errors are distinct states. The header refresh action and error footer provide explicit retry.
- Search uses the store's server query and separate cursors. Query and scroll offset are saved alongside chat/list identity; restoration can load subsequent pages to reach the saved position.

## Routing

The chat-list tap must not attach a group-wide unread/scroll anchor before deciding whether to show topics. `TdlibUi.openChat` opens the list only for a plain forum request with `chat.viewAsTopics`. Explicit topics, message targets, generic threads, scheduled messages, filters and share/draft/call payloads keep their own routes. `ChatOpenParameters.forumMessages()` is an internal bypass, not a setting or feature flag; the mode-switching UI uses TDLib's server preference (see `FORUM_COMMON_STREAM.md`).

Rows pass `MessageTopicForum` through `ChatOpenParameters.messageTopic()` and `MessagesController.Arguments`. No synthetic `ThreadInfo` is created. Null still means the common stream; General is an explicit forum topic.

For message links, an explicit `MessageLinkInfo.topicId` takes precedence over the message's topic. A deleted/missing message does not discard a known topic. An untyped message target is resolved with `GetMessage` before controller identity and anchor selection. Generic discussion links retain the existing `GetMessageThread` route. Reply highlights and pinned-preview returns also check topic identity.

Late metadata/availability replies cannot supersede a newer chat-open request. A list opened from the chat list becomes the topic's Back destination. A cold link/notification supplies a list or common-stream parent according to `viewAsTopics`; an existing same-forum list/common-stream parent is retained. A direct A-to-B transition replaces A in the stack instead of conflating A and B. Account identity remains part of controller reuse. Navigation uses the existing responsive controller framework, not a phone-only overlay.

The new controller is registered in `MainActivity`'s saved-state factory. The existing typed message-controller state provides the topic side of process recreation.

## Notifications

The pinned TDLib schema groups notifications by chat and does not expose a topic on `NotificationGroup`. A child notification therefore opens the latest message and, for a full-message notification, carries its forum topic in the content/bubble intent. It does not assume every notification in the group has the same topic. A summary with a specific message uses normal message resolution; a multi-message summary without a specific target opens the list/common stream.

Quick reply uses the same latest message's topic. When the topic is unavailable (notably `NotificationTypeNewPushMessage`, which has no topic field in this schema), it attempts `GetMessage`. An unresolved forum or unknown supergroup fails visibly instead of falling back to General. A known non-forum supergroup retains the existing reply behavior. Sending permissions are ultimately enforced by TDLib. A successful forum reply marks only its target message read, not messages from other topics in the aggregated notification group.

The notification's **Mark as read** action has a separate aggregate scope. A complete, typed mention group reads all represented forum topics, not just the latest message's topic. Old intents, incomplete groups and push-only/unknown topics keep the pre-existing chat-wide mention-read semantics. The group is dismissed only after every mention-read request succeeds; an error retains the notification and is reported. The scope is serialized with the action so it does not depend on a warm in-memory group.

This is routing, not per-topic notification grouping or management UI. Push-only messages may remain unresolvable until the full notification arrives; a quick reply then reports failure without sending.

## Verification boundary

`ForumNavigationTest` uses synthetic fixtures for list routing exclusions, explicit/fallback link identity, missing messages, notification and quick-reply identity, constructor separation, typed arguments, A/B equality and stale request tickets. Existing store/history/draft suites cover the data layer.

JVM tests do not render RecyclerView, execute Android Bundle lifecycle, receive real notifications or send messages. Device acceptance must separately cover phone/tablet layouts, Back/gesture Back, fast A/B navigation, search/pagination, process recreation, custom emoji, themes/RTL/accessibility, locked/cold notification launch and quick reply. No real group links, topic names, account data or device screenshots belong in public tests or this document.
