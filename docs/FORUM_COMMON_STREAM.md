# Forum modes and common stream

The topic-list overflow menu offers **Show all messages**. The common-stream overflow offers **View as topics**, and a topic's actions expose both modes. `ToggleChatViewAsTopics` stores the preference in TDLib; `UpdateChatViewAsTopics` also follows changes from another client. No feature flag or second local preference is used. An already open explicit topic keeps its identity when the preference changes. Mode navigation preserves the originating chat list and waits for the navigation stack to become available.

## Messages and replies

In the common stream, each message block starts with a topic button. It shows the current name, custom emoji and closed state when known. Adjacent messages from different typed topics cannot merge into one block. Tapping opens the exact topic and highlights the originating message; Back retains the common-stream parent. Topic history, comment threads, scheduled messages and previews do not show nested topic buttons.

`ForumPresentation` centralizes Android-independent display, reply, read-action and mute rules. A same-chat reply in the common stream targets the message's `MessageTopicForum`, including General. A foreign-chat reply never imports its source topic. An explicitly opened topic takes precedence over a cross-topic reply. Whole-chat `null` is not General.

The common-stream composer observes the selected reply topic separately from the viewed history. Its permissions/loading/error state gates sending and typing, and its draft is saved under the typed topic identity. The input hint names the destination. The reply context is saved in controller state and restored with the topic draft; a missing reply message must not silently retarget the draft to General. **Cancel topic reply** is also available from the overflow when this context is active.

Typing cancels the previous destination on a reply/topic change. Text and inline file-batch sends capture the destination, reply and editor revision. A mixed music/document batch shares one captured reply. A late result from a different controller context cannot clear the new editor, and an error does not clear the forum draft. Successful text/file sends clear the corresponding local and TDLib topic draft. The slow-file notice alone does not clear a forum draft.

Keyboard/IME media captures the visible controller identity separately from its outgoing destination: All remains `null` while a send can target General or the replied-to topic. Both media preparation and schedule confirmation recheck controller/account/arguments, typed view identity and lifecycle before sending to the captured destination.

## Links, read state and notifications

Topic actions copy the link returned by `GetForumTopicLink`. The existing typed message-link resolver handles opening the link, including explicit General, inaccessible messages and stale navigation requests.

Long-pressing the mention/reaction navigation button in a topic reads only that topic. In the common stream these actions retain chat-wide scope. A topic's menu also offers its unread mention/reaction actions, unread poll-vote count, navigation to the next matching message and marking the topic's poll votes read. Poll-vote search and message validation use the exact typed topic; visible unread poll-vote messages participate in `ViewMessages` even if their message read state was already current. Topic counters continue to come from authoritative store reconciliation, not sums over the loaded list.

List badges and the topic header resolve mute inheritance against current group settings; an explicit topic override wins. Group/scope setting changes rebind inherited indicators. The topic reaction button does not reuse a chat-wide single-reaction emoji from another topic. Android notification grouping remains chat-based; this change does not add per-topic notification channels.

## Rendering and lifecycle

`TGMessage.onTopicUpdated` consumes reconciled topic information and refreshes message UI. Topic lookup callbacks are registered before observation so warm-cache delivery cannot lose a callback. Custom-emoji receivers follow message-view attachment and destruction.

Topic buttons use theme colors, RTL alignment, a minimum 48 dp hit target, the existing text-size provider and a named accessibility action. Selection mode suppresses topic navigation. Topic-list rows scale their height and text with the system font scale and are rebound on configuration/language changes. The Premium icon picker is hosted in a dialog above the topic editor.

## Verification boundary

The JVM presentation tests use synthetic data and cover button visibility, typed reply destinations (including production `ReplyInfo`), quote/checklist/poll metadata, scoped read actions and poll-vote search, and mute inheritance. They run together with existing store, action, history, navigation, draft and permission tests.

JVM/build success does not verify Android drawing or server behavior. The acceptance matrix includes light/dark themes, RTL, large fonts, TalkBack, orientation changes, long-press/selection, mixed-media replies, typing and draft restoration, mode updates from another client, poll votes, notification delivery, phone/tablet Back stacks and process death. Record the actual verification scope with each candidate; the checklist itself is not a claim that every device and server case has passed.

## Topic tabs

Forums that use the common-stream presentation can show an All/General/topic selector above, below or beside the message history. The selector keeps server ordering, independent custom-emoji receivers and stable topic identities. The selected topic is fully revealed after placement changes; subsequent manual scrolling is preserved. Entering a topic, switching modes and returning to the list retain the originating chat list and the per-chat selector state. `ForumTopicsTabsView` handles rendering only; `ForumTabsHost` owns subscriptions, pagination and selection lifecycle.
