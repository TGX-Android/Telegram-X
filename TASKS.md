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
| | | Fix: Chat list preview showing "Topic created" for non-forum service messages (switch fallthrough bug)
| | | Create topic option in tabs mode (ForumTopicTabsController - via 3 dots menu)
| | | Permission checks for topic actions UI (hide create/edit/pin/close/delete based on user rights)
| | | Group Info access from tabs mode (ForumTopicTabsController - admin-only menu option)
| | | Forum layout toggle (tabs vs list) in ProfileController (ToggleSupergroupIsForum with hasForumTabs)
| | | Fix: Forum layout toggle instant apply (wasForumTabsChanged check in processEditContentChanged)
| | | Fix: Visual flash when entering forum tabs (LoadingController placeholder instead of MessagesController)
| | | Fix: External forum toggle detection (onSupergroupUpdated handler for non-admin users)
| | | Change topic icon feature (GetForumTopicDefaultIcons + EditForumTopic with iconCustomEmojiId)
| | | Fix: Muted topic notifications (client-side filter in TdlibNotificationHelper.updateGroup)
| | | Fix: Closed topic input disabled (isTopicClosedForUser check in MessagesController.updateBottomBar)
| | | Search messages in topics (toggle Topics/Messages search in ForumTopicsController)
| | | Flat message search results with sender avatar and topic icon in corner (ForumTopicView)
| | | Message search pagination (infinite scroll with nextFromMessageId in ForumTopicsController)
| | | Filter message search results by topic (FAB button with multi-select checkboxes)
| | | Fix: Preserve search results when navigating back from topic (allowLeavingSearchMode override)
| | | Fix: Topic filter missing old messages (multi-page preloading + auto-retry)
| | | Topic filter dialog with proper icons (TopicIconModifier with colored circles + custom emoji)
| | | Fix: Settings popup Done/Cancel buttons ripple effect (use ?android:attr/colorControlHighlight for theme-adaptive ripple)
| | | Message search loading indicator (ClearButton spinner in search bar instead of centered ProgressComponentView)
| | | Fix: Topic filter dialog icon positioning (LEFT_OFFSET_DP 68→18dp to place icons in left padding area)
| | | Fix: ForumTopicView emoji rendering (use Text class instead of canvas.drawText for proper emoji support)
| | | Fix: ForumTopicView icon-based indicators (mute/lock icons instead of emoji prefixes)
| | | Fix: ForumTopicView text width calculation (prevent overlap with time/counters)
| | | Fix: ForumTopicsController header margin (prevent title overlap with menu buttons)
| | | Fix: Forums with tabs always open in tabs view (TdlibUi.java hasForumTabs condition)
| | | Fix: View as chat navigation (direct MessagesController instead of openChat)
| | | Star/Paid reactions support (TdExt.kt, TGStickerObj, TGReaction, TGReactions, Tdlib.java)
| | | Fix: Premium bot crash on Buy button (TGInlineKeyboard null check + payment form handling)
| | | Fix: Windows file lock issue (kotlin.compiler.execution.strategy=in-process in gradle.properties)
| | | View Forum navigation from topic (btn_viewForum menu option when viewing topic via message link)
| | | Stories Settings screen (SettingsStoriesController - new settings section)
| | | Customizable story ring colors (StoryColorPickerController - 1-3 color gradient picker)
| | | Optional "Add Story" button border (SETTING_FLAG_SHOW_ADD_STORY_BORDER)
| | | Story bar as RecyclerView item (scrolls with chat list instead of overlay)

## Implementation Notes

### Files Created
- `app/src/main/java/org/thunderdog/challegram/ui/ForumTopicsController.java` - Main controller for forum topics list
- `app/src/main/java/org/thunderdog/challegram/ui/ForumTopicView.java` - Custom view for topic items
- `app/src/main/java/org/thunderdog/challegram/ui/ForumTopicTabsController.java` - ViewPager-based tabs controller for forum topics (used when hasForumTabs is enabled)
- `app/src/main/java/org/thunderdog/challegram/util/TopicIconModifier.java` - DrawModifier for rendering topic icons (colored circles + custom emoji) in list items

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
- `SearchChatMessages` - Search messages in forum chat, group by topicId for message search mode

### Future Enhancements (TODO)
- [x] Tabs layout support (`hasForumTabs`) - Show topics as horizontal tabs when admin enables "Tabs" layout
- [x] User typing in topics - Show typing indicator per-topic instead of per-chat

All major forum topics features have been implemented.

---

## Bug Fixes

### InternalLinkTypeInvoice Crash Fix
Fixed ClassCastException when opening invoice links. The crash occurred because `InternalLinkTypeInvoice` was falling through to `InternalLinkTypeBuyStars` case in the switch statement, causing an invalid cast.

**Files Modified:**
- `app/src/main/java/org/thunderdog/challegram/telegram/TdlibUi.java`:
  - Separated `InternalLinkTypeInvoice` from `InternalLinkTypeBuyStars` cases
  - Added new `openPaymentForm(TdlibDelegate, String invoiceName, ...)` method
  - Invoice links now properly open payment forms via `GetPaymentForm` API

### MessageGift Support
Added support for the `MessageGift` message type which was previously showing as "Unsupported message".

**Files Created:**
- `app/src/main/java/org/thunderdog/challegram/data/TGMessageGiftRegular.java` - Handler for regular gift messages (extends TGMessageGiveawayBase)

**Files Modified:**
- `app/src/main/java/org/thunderdog/challegram/data/TGMessage.java`:
  - Added case for `MessageGift.CONSTRUCTOR` → `TGMessageGiftRegular`
  - Removed `MessageGift` from unsupported message types list
- `app/src/main/res/values/strings.xml` - Added gift-related strings:
  - GiftReceived, GiftSent, GiftConverted, GiftUpgraded, GiftRefunded
  - ViewGift, xGiftValue, xGiftCanBeSold

### Visual HSV Color Picker
Replaced hex keyboard input with visual HSV color picker for story ring color customization.

**Files Modified:**
- `app/src/main/java/org/thunderdog/challegram/ui/StoryColorPickerController.java`:
  - Added `ColorPickerPopupView` inner class with:
    - Saturation/Value gradient square
    - Hue rainbow bar
    - Color preview circle
    - Cancel/Done buttons
  - Touch handling for dragging color selection

### Choose Gift Recipient Button Fix
Fixed "Choose Gift Recipient" keyboard button (and similar bot buttons) doing nothing on click.

**Root Cause:** `CommandKeyboardLayout.onClick` was missing handlers for `KeyboardButtonTypeRequestUsers` and `KeyboardButtonTypeRequestChat` button types.

**Files Modified:**
- `app/src/main/java/org/thunderdog/challegram/component/chat/CommandKeyboardLayout.java`:
  - Added cases for `KeyboardButtonTypeRequestUsers` and `KeyboardButtonTypeRequestChat`
  - Added `onRequestUsers()` and `onRequestChat()` to `Callback` interface
- `app/src/main/java/org/thunderdog/challegram/ui/MessagesController.java`:
  - Implemented `onRequestUsers()` - opens contact picker, then calls `ShareUsersWithBot` API
  - Implemented `onRequestChat()` - shows "not yet supported" (chat picker not implemented)

**TDLib Function Used:**
- `ShareUsersWithBot(chatId, messageId, buttonId, sharedUserIds, onlyCheck)` - shares selected users with the bot after pressing a `KeyboardButtonTypeRequestUsers` button

### Contact Picker Navigation Fix
Fixed contact picker not navigating back after selecting a contact for "Choose Gift Recipient" button.

**Root Cause:** `ContactsController.onFoundChatClick()` wasn't calling `navigateBack()` after delegate callback.

**Files Modified:**
- `app/src/main/java/org/thunderdog/challegram/ui/ContactsController.java`:
  - Added `navigateBack()` call after `delegate.onSenderPick()` returns true

### MessageUsersShared / MessageChatShared Support
Added support for `MessageUsersShared` and `MessageChatShared` service message types which were showing as "Unsupported message".

**Files Modified:**
- `app/src/main/java/org/thunderdog/challegram/data/TGMessageService.java`:
  - Added constructor for `MessageUsersShared` - shows "You shared [user name]"
  - Added constructor for `MessageChatShared` - shows "You shared [chat name]"
- `app/src/main/java/org/thunderdog/challegram/data/TGMessage.java`:
  - Added cases for `MessageUsersShared` and `MessageChatShared`
  - Removed from unsupported message types list
- `app/src/main/res/values/strings.xml`:
  - Added `YouSharedUser`, `YouSharedUsers`, `YouSharedChat` strings

### User Sharing Confirmation Toast
Added toast notification when sharing user with bot to provide UX feedback.

**Files Modified:**
- `app/src/main/java/org/thunderdog/challegram/ui/MessagesController.java`:
  - Added toast showing "You shared [user name]" after successful ShareUsersWithBot call

### Payment Card Input Validation & Formatting
Fixed payment card input fields lacking proper validation and formatting.

**Issues Fixed:**
- Card number, expiry, CVC fields now show numeric keyboard
- Card number auto-formats as `XXXX XXXX XXXX XXXX`
- Expiry date auto-formats as `MM/YY`
- CVC limited to 3-4 digits
- Card holder shows text keyboard with auto-capitalization
- Cannot type letters/symbols in numeric fields

**Files Modified:**
- `app/src/main/java/org/thunderdog/challegram/ui/PaymentFormController.java`:
  - Added imports for `Editable`, `InputFilter`, `InputType`, `TextWatcher`
  - Overrode `modifyEditText()` in adapter to configure each field:
    - Card number: `TYPE_CLASS_PHONE` + custom filter (digits/spaces) + formatting TextWatcher
    - Expiry: `TYPE_CLASS_PHONE` + custom filter (digits/slash) + formatting TextWatcher
    - CVC: `TYPE_CLASS_NUMBER` + max length 4
    - Card holder: `TYPE_CLASS_TEXT | TYPE_TEXT_FLAG_CAP_CHARACTERS`

### Paid Reaction Crash Fix
Fixed crash when opening reactions selector with paid (star) reactions.

**Root Cause:** `TGReaction.newCenterAnimationSicker()` and `newStaticIconSicker()` didn't handle paid reactions - they fell through to code that accessed null `customReaction` field.

**Files Modified:**
- `app/src/main/java/org/thunderdog/challegram/data/TGReaction.java`:
  - Added `isPaid` check to `newStaticIconSicker()` - returns cached or new paid star sticker
  - Added `isPaid` check to `newCenterAnimationSicker()` - returns cached or new paid star sticker

### Archive Pin/Unpin Overlap with Stories Fix
Fixed archive row scroll handling using hardcoded positions that didn't account for story bar.

**Root Cause:** The archive collapse/expand scroll listener used hardcoded positions (0, 1) assuming archive was always at position 0. With story bar at position 0, archive is at position 1, causing incorrect scroll behavior and visual overlap.

**Files Modified:**
- `app/src/main/java/org/thunderdog/challegram/ui/ChatsController.java`:
  - Updated `onScrollStateChanged` to use dynamic `archivePosition` from `adapter.getArchiveItemPosition()`
  - Updated `onScrolled` to check against dynamic archive position instead of hardcoded 0
  - Updated `getLiveLocationPosition()` to account for story bar offset
  - Fixed ItemDecoration to not apply negative collapse offset when story bar is present
  - Changed story bar loading to only add to adapter when content is available
  - Added `scrollToPosition(0)` when story bar is first added to ensure visibility on app start
- `app/src/main/java/org/thunderdog/challegram/widget/StoryBarView.java`:
  - Changed initial visibility from GONE to VISIBLE (adapter now controls presence)
  - Removed GONE state from updateVisibility() - adapter handles add/remove

### Paid Reaction Empty Icon Fix
Fixed paid/star reactions showing as empty (no icon visible) on channels.

**Root Cause:** `StickerSmallView.setSticker()` didn't handle stickers with `isDefaultPremiumStar()` flag. When a paid reaction sticker was set, `getImage()` and `getPreviewAnimation()` returned null (no actual sticker file), so nothing was drawn.

**Files Modified:**
- `app/src/main/java/org/thunderdog/challegram/component/sticker/StickerSmallView.java`:
  - Added check for `sticker.isDefaultPremiumStar()` in `setSticker()`
  - When true, sets `premiumStarDrawable` from `R.drawable.baseline_premium_star_28`
  - Clears `premiumStarDrawable` for normal stickers to avoid stale state
- `app/src/main/java/org/thunderdog/challegram/data/TD.java`:
  - Added error translation for "BALANCE_TOO_LOW" and "not enough stars" → `PaidReactionInsufficientStars`
- `app/src/main/res/values/strings.xml`:
  - Added `PaidReactionInsufficientStars` string with user-friendly message

### ForumTopicView Custom Emoji Crash Fix
Fixed crash when opening forum topics with custom emoji in message preview.

**Root Cause:** `ForumTopicView.buildTextLayouts()` was passing `FormattedText` (which may contain custom emoji) to `Text.Builder` without a `TextMediaListener`. When `Text.newOrExistingMedia()` is called without a listener, it throws `IllegalStateException`.

**Solution:** Implemented proper custom emoji support in ForumTopicView:
- Made ForumTopicView implement `Text.TextMediaListener`
- Added `textMediaReceiver` (ComplexReceiver) for loading custom emoji
- Pass `this` as textMediaListener when building Text with FormattedText
- Call `requestTextMedia()` after building displayPreview
- Pass textMediaReceiver to `displayPreview.draw()` for rendering custom emoji

**Files Modified:**
- `app/src/main/java/org/thunderdog/challegram/ui/ForumTopicView.java`:
  - Implemented `Text.TextMediaListener` interface
  - Added `textMediaReceiver` field initialized in constructor
  - Added `onInvalidateTextMedia()` callback for view invalidation
  - Added `requestTextMedia()` helper method
  - Updated `buildTextLayouts()` to pass `this` as listener
  - Updated `displayPreview.draw()` to pass textMediaReceiver
  - Updated attach/detach/destroy to handle textMediaReceiver lifecycle

### Forum Preview Swipe-Up Fix
Fixed long-pressing forum chat in chat list and swiping up opening old chat interface instead of ForumTopicsController.

**Root Cause:** `BaseView.openChatPreviewAsync()` always created `MessagesController` for preview, even for forums.

**Solution:** Added check for forum chats - skip preview and fall through to normal long-press menu behavior.

**Files Modified:**
- `app/src/main/java/org/thunderdog/challegram/widget/BaseView.java`:
  - Added `tdlib.isForum(chat.id)` check in `onLongPressRequestedAt()`
  - Forums now skip preview mode

### Star Reaction Icon Size Fix
Fixed star icon in reaction bubbles being too large (overflowing its border).

**Root Cause:** `Drawables.draw()` ignores `setBounds()` and draws at the drawable's intrinsic size. The star was drawn at 24dp regardless of the reaction bubble bounds.

**Files Modified:**
- `app/src/main/java/org/thunderdog/challegram/data/TGReactions.java`:
  - Changed `drawReceiver()` to use `drawable.draw(canvas)` directly instead of `Drawables.draw()`
  - Now properly respects the bounds set with `setBounds(l, t, r, b)`
  - Star icon scales to fit the reaction bubble correctly

### Forum Topic UI Improvements (from logopek/reX)
Applied topic-related fixes from https://github.com/logopek/reX repository.

**Changes Applied:**

1. **TdlibUi.java - Forums with tabs always open in tabs view**
   - Changed condition from `chat.viewAsTopics` to `chat.viewAsTopics || hasForumTabs`
   - Forums with tabs layout now correctly open in tabs mode by default

2. **ForumTopicView.java - Icon-based indicators instead of emojis**
   - Removed emoji prefixes from topic title (🔒, 📌, 🔕)
   - Added mute icon (deproko_baseline_notifications_off_24) drawn after title text (14dp, 4dp gap)
   - Added lock icon (deproko_baseline_lock_24) where counter normally is when topic is closed and has no unread (18dp)
   - Improved text width calculation to reserve space for time, status icons, counters, and mute icon
   - Prevents text overlap with icons/counters on right side

3. **ForumTopicsController.java - Header margin fix**
   - Added `headerCell.setInnerMargins(56dp, 104dp)` to prevent chat title overlapping menu buttons
   - Right margin: 48dp (more button) + 48dp (search button) + 8dp (padding) = 104dp

4. **ForumTopicsController.java - View as chat navigation fix**
   - Changed "View as chat" to create MessagesController directly instead of using openChat()
   - Prevents forum from re-opening in tabs mode after switching to unified view
   - Uses focus listener to remove ForumTopicsController from navigation stack

**Files Modified:**
- `app/src/main/java/org/thunderdog/challegram/telegram/TdlibUi.java` (line 2139)
- `app/src/main/java/org/thunderdog/challegram/ui/ForumTopicView.java` (lines 222-223, 329-359, 563-623)
- `app/src/main/java/org/thunderdog/challegram/ui/ForumTopicsController.java` (lines 599-601, 708-724)
