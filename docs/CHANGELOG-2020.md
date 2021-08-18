# 26 August

- opus,opusfile,ogg,flac as submodules
- clean Android.mk build
- updated dependencies

# ?? August

- Select output video quality

# 17 August

- Native components re-structuring
- Fast dependencies update: update_dependencies.sh
- New build scripts for thirdparty libraries

# 25 May..16 August

- IV 2.0 basic support
- Refactoring to Kotlin
- Restructure in jni sources folder
- Supported all API changes in TDLib
- Catch up with upstream in most third-party libraries
- Option to choose digital storage unit
- Trim videos

# 28 Mar..25 May

- See [blog post](https://telegra.ph/Telegram-X-04-23)

# 27 Mar

- Updated TDLib: bugfixes
- Updated other dependencies

**Bugfixes**

- Many crashes from GP
- Sponsor channel in the share menu
- Missing autocorrect option

# 7 Mar p.3

- Updated TDLib: fixed file redownloading after rescheduling
- When applying monospace formatting, other entities will be removed from selected text
- When applying non-monospace formatting, monospace entities will be removed from selected text
- When creating text url, other entities will be removed / updated properly in the selected text

# 7 Mar p.2

- Holding scroll to bottom button will scroll chat to the very bottom (ignoring any other cases)
- Send as File from full-screen image viewer
- Minimum time in date picker (current + 1 min)
- Date picker when blocking/restricting users

**Bugfixes**

- Mention button appears on Scheduled Messages screen
- Unstable text size of inline buttons sizes
- Sending media without sound/ sending as file from Scheduled message screen is sending it normally 

# 7 Mar

- Size caching for broken gallery items on first encounter (slow opening first time expected, then should be OK)
- Couple crash fixes
- User name instead of "Saved messages" when opening self profile
- Cannot set custom title while promoting admins and you are not owner of the chat
- Date picker bug should be fixed.

# 4 Mar p.2

- Reference & anchor click support in IV
- Removed Show forward options setting
- Combine "send without sound" with other options
- Server-synced bell should be completely ignored by TGX now

# 4 Mar

- Tapping scroll to bottom button when staying above the first unread message will scroll to the first unread message

**Bugfixes**

- Crash when trying to pin the first chat
- Optimized unpinning chats

# 3 Mar

- Tapping scroll to bottom button after pressing t.me/c/[msg-id] or t.me/channel/[post-id] links, will return to the origin message with the link you pressed, like with replies
- Origin message is now remembered between chat re-openings and restarts

**Bugfixes**

- Contacts without phone number in attach > Contacts

# 2 Mar p.3

- Saved Messages avatar instead of profile photo in reminder notifications
- software keyboard will not hide when scheduling a text message (emoji/sticker keyboard still hides, but it's difficult to share its instance) 

**Bugfixes**

- chat text width does not update when scheduled messages availability icon appears/disappears

# 2 Mar p.2

- Select... (all, muted, unread) now follows currently selected chat filter

**Bugfixes**

- Correct audio playlist management while scheduling audio files (or playing from scheduled messages)
- Markdown gets parsed inside monospace entities (visual ones)
- Missing time in the restriction bar if the unblock time is within the current year
- 1970 while playing voice messages
- Tapping on scheduled message voice message in the header does not work 
- Show in Chat for audio files playing from scheduled messages does not work

# 2 Mar

- "Block X bots" + "Blocked/Unblocked X bots" strings when bulk blocking
- "Pin Chat" now pins to the bottom of the pinned chats list
- Schedule menu when sending media from the keyboard

**Updated TDLib**

- Amount of cleared bytes after using "Clear from cache" from the chats list
- Username/hashtag highlighting in Telegram, Twitter and Instagram link previews
- Removed view counter in scheduled messages

**Bugfixes**

- Pinning multiple chats while using chat filter may result in losing some pinned chats
- Tapping on the X button while browsing inline results on Scheduled Messages screen opens schedule menu
- Sending scheduled animated sticker from sticker preview is frozen
- Freezing all animated stickers while sticker preview is opened does not work
- Gap between messages when sending text from scheduled messages screen
- Messed up header after deleting all scheduled messages

# 1 Mar

- Localized error message when trying to participate in scheduled poll
- Long-press to send without sound / schedule a poll
- Changed the way animated emoji vibration performed
- "Delete X messages" instead of just "Delete Messages"
- "Delete scheduled message" and "Delete reminder" for scheduled messages
- Removed view counter in scheduled messages

- Added Settings – Notifications – Sent Scheduled Messages
- Removed Reply button if notification contains only scheduled messages
- Hint to hold send button inside a chat
- Fixed send button animation in the first opened chat after app launch on first typing 

# 27-29 Feb

// missing

# 26 Feb

- Send without Sound
- Send without Markdown
- Save without Markdown (for message & caption editing)
- Removed Silent Broadcast toggle in favor of "Send without Sound"

# 25 Feb

- Updated TDLib: bugfixes
- When "Loop animated stickers" is enabled, it will pause at the last frame, instead of the first one.
- Channel title when receiving notification from group chat for auto-forwarded message when there's no connection with Telegram server (previously just "Channel")

- View Statistics from profile page, when available (previously only on manage chat page)
- Scheduled icon in the chats list

**Bugfixes**

- Cannot replay animated stickers without sticker pack

# 24 Feb

- Changed algorithm how links for "Getting link preview" are collected
- When editing a message, cached link preview will be displayed instantly

**Bugfixes**

- Weird crash related to hiding passcode
- Inconsistency when sending / editing some text messages: link preview is not displayed before sending, but is displayed afterwards

# 23 Feb

- Icons in more menu on chats screen

**Bugfixes**

- Crashes
- Missing caption changes after editing photo caption & re-entering the chat while offline
- Incorrect rendering of nested entities in this case: `__a**b**c__`
- Incorrect touch handling in centered texts in IV
- Suggestion underline might be saved in the chat draft

# 22 Feb

- More bulk actions

**Bugfixes**

- When you report a message, only chat gets reported

# 21 Feb

- Select multiple chats for bulk actions

**Bugfixes**

- Animated Emoji toggle does not work

# 19 Feb

- Colored animated emoji (thumbs up, probably etc)

# 18 Feb

- Improved API of SettingsAdapter
- Loop Animated Stickers setting
- Animated emoji
- Animated emoji settings
- Vibrations for some animated emoji
- Bank card number highlighting in messages
- Bank card number click handling

# 11 Feb

- Gray color for custom title field when it is read-only
- Admin's custom title updates in Recent Actions

**Bugfixes**

- Channels without admin rights in the share menu
- Unable to specify admin rights with only custom title, when neither of permission differ from the global chat permissions
- When saving too long custom title, it just gets trimmed, without any warnings (though the counter is present)
- Missing time in restriction warnings

# 10 Feb

- Admin titles in members list
- Custom Titles in chat admins list
- Edit Custom Title for owner (self) from members list

# 09 Feb

- Specific restriction / ban time in warnings
- Configure Custom Title for chat admins or owner

**Bugfixes**

- Incorrect parsing of multiple text links markdown in the text
- Incorrect conversion of text links to markdown when Edit Markdown is enabled
- Text links are parsing inside `code`, when they should not
- Incorrect position of scroll to bottom button when viewing in-chat hastag results
- Incorrect position of header title when viewing global hashtag results

# 08 Feb

- Properly handled underline spans
- Consistency between inputs in handling entities: text message, caption, share comment

**Bugfixes**

- Android 10: Incorrectly rotated image previews in the attach menu

# 07 Feb

- Full support for nested markdown and new styles in messages
- Edit Markdown
- Include Archived Chats option 

# 04 Feb

- Support for underline, strikethrough entities in messages and chats list
- Separate color id for links in IVs, no longer underlined
- Strike-through shortcut for message / caption input
- Support for anchor spans, superscripts, subscripts and marked text in IV parser
- Highlight of cached links in IVs

**Bugfixes**

- "Read all chats" does not read marked as unread chats
- Nested styling in Instant View does not work anymore
- Link press highlight in Instant View does not work properly when multiple entities are present within it

# 03 Feb

- Build scripts reworks & optimizations
- Automatic build publishing
- Removed x86,x86_64 from universal builds

# 02 Feb

**Bugfixes**

- Incorrect calculation of the finger position when using previews (i.e. you need to hover actions like pin/unpin higher or lower than needed)

# 24 Jan

- Updated TDLib
- Available disk space on the crash screen (previously `0 bytes`)
- Account is displayed on crash screen, when technically possible
- Erase Database & Launch App option on crash screen
- Check for updates button on the crash screen

**Bugfixes**

- Crash on the poll results screen
- Other crash fixes from Google Play

# 23 Jan

- Updated TDLib
- Updated rlottie
- Change log for `0.22.4.1270`: https://telegra.ph/Telegram-X-01-23-2
- 1/1000 chance to get batman transition while participating in quizzes
- Allowed to send polls in bot chats again

**Bugfixes**

- Anonymous toggle is available when creating polls in channels
- When sending a poll, selected reply is ignored

# 22 Jan

- Create multiple choice polls, quizzes
- "View X Results" string with counter
- Quiz icon in poll message menu
- Batman Transitions option in Settings > Interface for channel readers
- Archive pin state is now per-account
- Separate archive color when it is hidden above the list 

**Bugfixes**

- Can't forward public polls anywhere
- `#5 poll can't be answered` when trying to participate in not yet sent poll

## 21 Jan

- Poll/Quiz strings
- Chat previews will now work with users with whom chat was not initiated
- Centered total voter counters in anonymous single-op polls, like in iOS client, for consistency reasons
- Hint that public poll cannot be forwarded to channels
- Multi-line & emoji support in headers in Poll Results screen, built on top of custom text renderer
- "X correct answers" under the correct answer on the poll results screen
- Supported quiz push content type (with separate emoji)
- Separate icon in the chats list for quizzes
- Added colors: messageCorrectChosenFilling, messageCorrectChosenFillingContent, bubbleOut_chatCorrectChosenFilling, bubbleOut_chatCorrectChosenFillingContent

**Bugfixes**

- View Results unavailable for finished polls/quizzes, if user not participated in it
- Too big Vote/View Results button selection in forwarded messages
- Another fix for View results unavailable
- "X answers" instead of "X votes" on view results screen for quizzes
- Press highlight is missing in non-bubble mode for the poll button

## 20 Jan

- Made an utility that animates replacements
- Finished supporting displaying new poll types
- Nice animations when current poll state changes
- Progress animation when participating in multi-select polls
- Made a `UserListManager` based on `ListManager<T>`
- View poll results
- Optimized list animator

## 19 Jan

- Animations when participating in quiz
- Supported poll request bot keyboard button type
- Refactored `TGMessagePoll`'s options list

**Updated TDLib**

- Typing cancellation is back again when receiving a message
- Typing statuses are sent again for users who do not share online status with you until this will be changed on server.
- `updateUnreadMessageCount`/`updateUnreadChatCount` get sent after first chats list retrieval 
- Fixed outgoing file deletion when deleting one of several known messages with the same file

## 18 Jan

- Made an utility that animates changes in the custom lists
- Animated recent voter avatars list in public polls / quizzes
- Added cross animation in the SimplestCheckBox
- Added new colors: bubbleOut_chatNeutralFillingContent, bubbleOut_chatCorrectFilling, bubbleOut_chatCorrectFillingContent, bubbleOut_chatNegativeFilling, bubbleOut_chatNegativeFillingContent, messageNeutralFillingContent, messageCorrectFilling, messageCorrectFillingContent, messageNegativeLine, messageNegativeLineContent
- Colored correct/incorrect quiz answers
- Animated check box under the chosen options' percentage
- Separate warning when stopping quizzes

## 16 Jan

- Counter in archive shows number of unread chats
- Separate strings for messages search results header inside filtered archive
- Sorted unsorted colors in theme editor 

**Bugfixes**

- Adding account immediately after logging out may result in `#7: setAuthPhoneNumber unexpected`
- Bold / uncolored color names for `avatar*_big`
- Crash when trying to submit or retract poll vote

## 15 Jan

- Unread counter color checks for unmuted chats inside an archive
- "Mark as Unread" is displayed in the chats list, if some chat is marked as unread inside an archive
- Optimized the way archive preview gets updated based for each type of event

**Bugfixes**

- Long admin signs may overlap the user name in bubble mode
- Long admin signs may go outside of bubble bounds
- Long admin sign + name may cause bubble going outside of screen
- Scroll to bottom button overlaps the search controls bar in channels
- **[?]** Chat content is slightly (about 1px) visible below the follow/discuss bottom bar on some OEMs
- Games work only after re-entering the chat after sending them to a chat (#5: Message not found)
- Search controls bar is not visible when follow/discuss bottom bar is present
- After using "hide above the list", if the list was not in the beginning, animation will end not in the beginning of the list
- New chat button is not accessible after hiding an archive, if there are too few items in the list
- After using chats filter, new chat button no longer reacts to chats list scroll (does not show/hide)
- New chat button is not accessible after using chat filter, if it was hidden and there are too few items in the chats list
- "Mark as Unread" badge is not displaying

## 14 Jan

- Setup new development environent
- Updated `compileSdkVersion` to `19`
- Updated `buildToolsVersion` to `29.0.2`
- Updated TDLib
- Started working on public polls

**Bugfixes**

- Software keyboard does not hide when opening emoji/stickers keyboard after performing some actions
- Link Preview does not display if link is just a domain name with slash on the end: `example.com/`

## 13 Jan

- Improved Unread Messages separator appearance logic
- Specific error code & message will be displayed in the failed message's menu

**Bugfixes**

- Some of the chats on the bottom of the list may be only partially visible, when archive is hidden

## 12 Jan p.2

- Unread Messages separator when new message arrives
- Animation when live location shows/collapses in the chats list & there is an archive
- Separate warning strings when deleting chat / clearing history for Saved Messages

**Bugfixes**

- Cannot pull down the archive while sharing live location
- Incorrect scroll bounds when there are too few chats on the screen & archive is hidden
- Live location overlaps the archive when collapsing
- Tapping "Chats" tab may scroll to an incorrect locaion when sharing live location and having an archive

## 12 Jan

- Improved opening groups & channels, when you are not a member and if many messages have been posted since your last visit
- Improved logic of reading incoming messages
- Updated TDLib
- Updated dependencies

**Bugfixes**

- In rare cases some messages could become read even though they didn't appear on screen at all
- Crash when trying to search GIFs

**Dependencies**

- rlottie:https://github.com/DrKLO/Telegram/commit/31736964
- androidx.browser:browser:1.2.0
- com.google.firebase:firebase-messaging:20.1.0
- com.google.android.exoplayer:exoplayer-core:2.11.1
- com.squareup.okio:okio:2.4.3
- com.squareup.okhttp3:okhttp:4.3.1
- com.getkeepsafe.relinker:relinker:1.4.0

**RELEASED BETA: 0.22.4.1253, https://t.me/tgx_android/264**