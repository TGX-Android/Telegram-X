# Forum topic management

Long-press topic rows to select them; the selection header offers only actions common to all selected topics. Empty action menus and technical success summaries are not shown. The list overflow menu creates topics, adds members through the existing group workflow and refreshes the list.

Tapping the topic history header opens its profile with the topic icon/name, parent group, a visible topic link, mute/pin/edit controls and permission-aware overflow actions. Shared media is filtered to the topic and offers only nonempty categories, using the existing media viewers and paging controllers. Back returns to the topic's messages.

## Contract and permissions

`ForumTopicPolicy` contains Android-independent rules for the pinned TDLib schema. Topic creation requires group/member `canCreateTopics` or administrator `canManageTopics`. Editing/closing also allows the topic creator. Hiding General and pinning/reordering topics require `canManageTopics`. **Pinning messages is a different permission** (`canPinMessages`), and stays available to authorized administrators in closed topics.

Deletion requires `canDeleteMessages`, or the topic-creator exception. For the latter, Telegram must verify the limit of 11 messages and the absence of messages from others; the UI never claims a partial local history proves eligibility. The destructive confirmation explains this condition and that messages are deleted for everyone. General is presented as clearing history, not deleting the permanent topic.

Menus use current cached status, and each confirmation/action checks it again. Permission or state changes while a menu is open cannot reverse the meaning of the selected command. Server permission checks are authoritative, including changes not delivered to this client yet.

## Editor

Creation and editing share `ForumTopicEditController`, including an icon preview, name field and searchable icon picker with a clear action. Names contain 1–128 Unicode code points. Creation offers the six TDLib colors. Editing deliberately has no RGB control: `EditForumTopic` accepts a name and custom-emoji change, not a color. General's icon is not editable. Default icons are loaded from `GetForumTopicDefaultIcons`; Premium accounts can open the existing custom-emoji selector. The ordinary icon removes the custom emoji. Errors keep the editor and entered values open for retry.

## Reconciliation

Operations use `ForumTopicActions` and its timeout/lifecycle handling. No cached TDLib object is modified and no row is removed optimistically. A successful result invalidates relevant caches and triggers full-topic/list reconciliation; `Ok` is not treated as a new topic state. Unhiding General does not reopen it.

Pin limits come from `pinned_forum_topic_count_max`. Reordering first loads an unfiltered, fresh, complete pinned prefix and submits every pinned ID in order. Search matches are never used as a replacement pinned list. Moving past an edge produces an explicit explanation. Concurrent server changes or limits are surfaced as errors.

Per-topic notification choices override only the mute/inheritance fields and copy all other notification fields. Choices: group default, always notify, one hour, one day, forever. No group-wide setting is changed.

## Verification boundary

Pure policy tests cover member/admin/creator/restricted/left/banned roles, permission changes, independent topic/message permissions, Unicode names, colors/icons, pin-order completeness and immutability, and notification copying. Existing store/action tests cover typed requests, errors, timeout/reset, and authoritative reconciliation.

Device acceptance is separate: create/edit with default and Premium emoji; rotate with an open editor; change a role from another client; test General hide/show/reopen; concurrent pin/reorder; failed/offline operations; deletion permissions; per-topic notification inheritance. Automated JVM checks do not establish this acceptance.
