# Forum topic list data layer

`TdlibForumTopicManager` owns one `ForumTopicStore` per TDLib account. Individual message badges, list pages and search results share records keyed by `(chatId, int forumTopicId)`. The store does not inherit a second data-manager cache. This is the list data layer, not the topic-list UI or forum-history implementation; there is no feature flag.

## Consumer contract

Use `tdlib.topics().openList(chatId, query, observer)` and keep the returned `ListSession` until the consumer is destroyed. Close it explicitly. `session.snapshot` exposes the current cache immediately; observer callbacks arrive on the UI thread. Delivery is coalesced to the latest snapshot and cancelled when a session closes. `setQuery`, `refresh`, `loadMore` and `retry` operate on that session. Query keys trim only surrounding whitespace; different chats and queries keep separate memberships and cursors.

Snapshots expose initial loading, loading more, refreshing, initialization, empty/end state, staleness, error, approximate total and the next cursor. Rows are an unmodifiable list of read-only TDLib values. Consumers must not mutate nested TDLib objects. Store updates replace top-level rows instead of modifying already delivered snapshots.

Warm rows remain visible during refresh and network errors. This is an in-memory cache only: logout and TDLib restart clear it, and inactive entries can be evicted. An uninitialized offline list is an error/loading state, not an authoritative empty list. Consumers must reopen after an account/client reset; old sessions receive a terminal empty snapshot and cannot issue new requests.

The existing weak `findAndObserve` message subscriptions still work. They now receive merged full values through `onTopicFound`, including info/full-update changes. New consumers should use closeable list sessions or `tdlib.topics().observeTopic(key, observer)` subscriptions, not depend on the old data-manager request queue.

## Requests, merging and pagination

- `GetForumTopics` uses the returned date/message/topic cursor verbatim. The approximate total and a short page do not prove that the list has ended. An empty page with an empty cursor does.
- Identity-based merging removes duplicates; rows sort by TDLib `order` descending, then topic ID for deterministic ties. Pinned state is not a substitute for server ordering.
- Initial/refresh pages replace old membership but preserve rows changed while the request was in flight. Search membership remains server-authoritative, not a guessed local substring filter. A refresh restarts pagination from the first page.
- Repeated/cyclic cursors, empty nonterminal pages and pagination with no new IDs stop with a recoverable pagination error. Retrying that error starts a refresh; a normal page error retries the failed cursor.
- Query switches, superseding refreshes and account resets invalidate obsolete callbacks. Sequence stamps prevent an older full response from overwriting a newer full response or newer info/update fields. Deletion tombstones protect against resurrection by old pages.
- Logical 15-second timeouts clear loading even if TDLib has not completed. Late replies are ignored. This does not cancel TDLib's server-side work.

## Live reconciliation

`UpdateForumTopicInfo` updates metadata immediately. `UpdateForumTopic` updates pin/read markers, mention/reaction/poll counters, notification settings and the nullable draft immediately. It does not contain `order`, `lastMessage` or `unreadCount`, so those fields need a fresh full topic. New/send-result/edit/delete/read events invalidate the appropriate cached records or active chat lists instead of guessing counters locally. Scheduled messages and other topic constructors are not treated as forum messages.

Reconciliation is batched over 300 ms with at most four concurrent single-topic requests. Bursts of messages for unseen topics use a list refresh. Matching updates emitted during a list request are absorbed by its full result, avoiding a redundant request for every row. An unknown topic fetch waits for an in-flight unfiltered list to have a chance to provide it. Searches are refreshed when metadata or membership may have changed. Reconnection retries active stale data; ordinary errors do not start a blind retry loop.

The default inactive cache budget is eight list/query states and 512 topic records. Active lists, subscriptions, in-flight requests and required deletion tombstones are protected: this is a soft bound on inactive data, not a hard cap on a currently displayed/paginated list. All mutations are serialized on the TDLib owner thread; cache reads are synchronized, and publication goes through the UI dispatcher.

## Management methods

`tdlib.topics().actions` wraps create/edit, close/reopen, General hide/show, pin/unpin/reorder, delete, notifications, read-all mentions/reactions/poll votes, unpin-all messages, link lookup and topic-view preference. Typed callbacks deliver either a result or `TdApi.Error`. Unexpected response types and timeouts use the same error path. Permission checks, confirmations and menus belong to the later UI work; TDLib remains authoritative for permissions and limits.

Successful mutations mark data stale and request reconciliation, not optimistic fabricated server state. In particular, clearing/deleting General does not assume the topic disappears. Mutation timeouts have an uncertain outcome and are never retried automatically: refresh/check state before an explicit retry. Link lookup does not invalidate data. Reset invalidates pending operation callbacks.

## Verification

Run the app's `testLatestArm64DebugUnitTest` task and `assembleLatestArm64Debug` for compilation/packaging. `ForumTopicStoreTest` and `ForumTopicActionsTest` use `ForumTopicTestBackend` with virtual time, manually reordered replies and a separate publication queue. They require no Android device, native TDLib, network, account or real chat fixtures. Existing identity/navigation/listener tests remain part of the same suite.

Live device acceptance of list UI, forum history, drafts and navigation is intentionally separate and belongs to subsequent implementation stages.
