# Forum Topics Implementation Tasks

| Waiting | In Progress | Completed |
|---------|-------------|-----------|
| | | TDLib bindings available |
| | | ForumTopicInfoListener infrastructure |
| | | Topic info caching in Tdlib.java |
| | | Service message handling |
| | | Message topic ID filtering |
| | | Permission checks |
| | | ForumTopicsController - main topics list screen |
| | | ForumTopicView - topic list item view |
| | | Navigation integration (TdlibUi.java) |
| | | Pin/Unpin topics UI |
| | | Close/Reopen topics UI |
| | | Build successful (arm64 + x64 debug APK)
| | | Topic message filtering fix (GetForumTopicHistory)
| | | Topic-specific unread counter fix
| | | Mark messages as read fix (onForumTopicUpdated)
| | | Open topic at first unread position
| | | Cross-device read state sync (GetForumTopic refresh)
| | | Fix server-side read marking (needForceRead delay)
| | | Unread topics count in chat list (forum supergroups)
| | | Fix: Per-chat unread topic count refresh (BetterChatView/VerticalChatView)
| | | Fix: Main chat list unread topic count refresh (ChatsController/TGChat)
| | | Topic icon/avatar display (custom emoji + colored circle fallback)
| | | Transparent background for loaded custom emoji icons
| | | Topic Creation Dialog (CreateForumTopic with FAB button)
| | | Topic Editing Dialog (EditForumTopic - via long-press menu)
| | | Topic Header in Chat (ChatHeaderView shows topic name + chat name)
| | | Topic Notifications Settings (per-topic mute/unmute via long-press menu)
| | | Topic-specific typing indicator (per-topic send/receive)
| | | Tabs layout support (ForumTopicTabsController + hasForumTabs check)
| | | Forum Toggle in Group Settings (ToggleSupergroupIsForum via ProfileController)
| | | Search topics functionality (client-side name filtering + highlighting)
| | | Notification topic separation (shows "Chat > Topic" in notification title)
| | | Fix: Tab-style forums (hasForumTabs) always showing tabs
| | | Fix: "Topic icon changed" message instead of "Topic created" for icon edits
| | | "View as chat" option in ForumTopicsController (ToggleChatViewAsTopics)
| | | Fix: ForumTopicTabsController tabs and menu display (loading placeholder + more menu)
| | | Fix: ForumTopicTabsController "View as chat" navigation (destroyStackItemAt pattern)
| | | Fix: ForumTopicTabsController tabs layout margin (getMenuButtonsWidth override)
| | | "View as topics" option in MessagesController (for switching back from unified chat view)
| | | Fix: "View as topics" direct navigation (avoid stale chat.viewAsTopics issue)
| | | Default forum navigation to topics view (TdlibUi.java - matches official Telegram behavior)
| | | Fix: Topic-specific pinned messages in tabs mode (pass topicId to MessageListManager)
| | | Fix: Search button in ForumTopicTabsController (added search/clear mode support)
| | | Topic actions in tabs mode (mute, close, pin, edit via 3 dots menu)
| | | Fix: New messages appearing in wrong topic tab (updateNewMessage topic filtering)
| | | Fix: Topic mention/reaction counters not updating (onForumTopicUpdated extended)

## Implementation Notes

### Files Created
- `app/src/main/java/org/thunderdog/challegram/ui/ForumTopicsController.java` - Main controller for forum topics list
- `app/src/main/java/org/thunderdog/challegram/ui/ForumTopicView.java` - Custom view for topic items
- `app/src/main/java/org/thunderdog/challegram/ui/ForumTopicTabsController.java` - ViewPager-based tabs controller for forum topics (used when hasForumTabs is enabled)

### Files Modified
- `app/src/main/java/org/thunderdog/challegram/telegram/TdlibUi.java` - Added forum navigation hook (lines 2117-2134)
- `app/src/main/java/org/thunderdog/challegram/component/chat/MessagesLoader.java` - Added forum topic history loading using GetForumTopicHistory (lines 1154-1158)
- `app/src/main/java/org/thunderdog/challegram/ui/MessagesController.java` - Added forumTopic field, unread counter fix (updateCounters), and onForumTopicUpdated override for read state handling
- `app/src/main/java/org/thunderdog/challegram/component/chat/MessagesManager.java` - Fixed needForceRead to delay read marking for forum topics until user scrolls (lines 782-792)
- `app/src/main/java/org/thunderdog/challegram/telegram/Tdlib.java` - Added forum unread topic count caching and methods (forumUnreadTopicCount, fetchForumUnreadTopicCount, updateForumTopicUnreadCount)
- `app/src/main/java/org/thunderdog/challegram/telegram/TdlibListeners.java` - Added updateForumUnreadTopicCount listener method
- `app/src/main/java/org/thunderdog/challegram/telegram/ChatListener.java` - Added onForumUnreadTopicCountChanged callback
- `app/src/main/java/org/thunderdog/challegram/data/TGChat.java` - Modified getUnreadCount() to return unread topic count for forum chats
- `app/src/main/java/org/thunderdog/challegram/widget/BetterChatView.java` - Added onForumUnreadTopicCountChanged handler
- `app/src/main/java/org/thunderdog/challegram/widget/VerticalChatView.java` - Added onForumUnreadTopicCountChanged handler
- `app/src/main/java/org/thunderdog/challegram/data/TGChat.java` - Added updateForumUnreadTopicCount() method
- `app/src/main/java/org/thunderdog/challegram/component/dialogs/ChatsAdapter.java` - Added updateForumUnreadTopicCount() method
- `app/src/main/java/org/thunderdog/challegram/v/ChatsRecyclerView.java` - Added updateForumUnreadTopicCount() method
- `app/src/main/java/org/thunderdog/challegram/ui/ChatsController.java` - Added onForumUnreadTopicCountChanged callback handler
- `app/src/main/res/values/ids.xml` - Added controller_forumTopics and button IDs
- `app/src/main/res/values/strings.xml` - Added topic-related strings
- `app/src/main/java/org/thunderdog/challegram/component/chat/ChatHeaderView.java` - Added forum topic header support (topic name as title, chat name as subtitle)
- `app/src/main/java/org/thunderdog/challegram/ui/ProfileController.java` - Added forum toggle for supergroup owners (ToggleSupergroupIsForum)
- `app/src/main/java/org/thunderdog/challegram/telegram/ForumTopicInfoListener.java` - Extended onForumTopicUpdated to include unreadMentionCount and unreadReactionCount

### Current Status: Build Successful + Tested
- arm64 APK: `app/build/outputs/apk/arm64/debug/TGX-Example-0.28.2.1778-arm64-v8a-debug.apk`
- x64 APK: `app/build/outputs/apk/x64/debug/TGX-Example-0.28.2.1778-x64-debug.apk`

Running on emulator (Medium_Phone_API_36.1). Each topic now shows only its own messages.

### TDLib Functions Used
- `GetForumTopics` - Fetch topics list (implemented in loadTopics/loadMoreTopics)
- `GetForumTopicHistory` - Load messages for a specific topic (fixed in MessagesLoader.java)
- `ToggleForumTopicIsClosed` - Close/reopen (implemented)
- `ToggleForumTopicIsPinned` - Pin/unpin (implemented)
- `DeleteForumTopic` - Delete topic (implemented)
- `CreateForumTopic` - Create new topic (FAB button + dialog in ForumTopicsController)
- `EditForumTopic` - Edit topic name (long-press menu in ForumTopicsController)
- `SetForumTopicNotificationSettings` - Per-topic mute/unmute (long-press menu in ForumTopicsController)
- `ToggleSupergroupIsForum` - Enable/disable forum topics mode (toggle in ProfileController group settings)
- `ToggleChatViewAsTopics` - Toggle between topics view and unified chat view (more menu in ForumTopicsController)

### Future Enhancements (TODO)
- [x] Tabs layout support (`hasForumTabs`) - Show topics as horizontal tabs when admin enables "Tabs" layout
- [x] User typing in topics - Show typing indicator per-topic instead of per-chat

All major forum topics features have been implemented.
