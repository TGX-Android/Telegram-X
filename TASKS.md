# Stories Implementation Tasks

## Overview
Full stories feature implementation for Telegram-X with complete feature parity.

## Completed

### Phase 1: Story Viewer Foundation
- [x] **StoryViewController.java** - Full-screen story viewer
  - PopupLayout.AnimatedPopupProvider for overlay
  - Segmented progress bars at top
  - Left/right tap navigation
  - Swipe down to close
  - Story content display (photo/video via ImageReceiver)
  - Caption overlay with gradient
  - TdApi.OpenStory/CloseStory calls

### Phase 2: Story Bar in Chat List
- [x] **StoryBarView.java** - Horizontal RecyclerView with:
  - StoryAvatarItemView with gradient ring for unread
  - Gray ring for read stories
  - Name truncation below avatar
  - Click handling to open StoryViewController
  - Respects hideStories setting

### Phase 2.3: ChatsController Integration
- [x] Added storyBarView as header in ChatsController
- [x] Fetches active stories from TdApi
- [x] Updates visibility based on settings

### Phase 3: Avatar Story Rings
- [x] **AvatarView.java** - Added story ring support:
  - STORY_NONE, STORY_READ, STORY_UNREAD states
  - Gradient ring (blue/turquoise/green) for unread
  - Gray ring for read
  - setStoryState() method
  - drawStoryRing() in onDraw()
  - Respects hideStories setting

### Phase 6: Supporting Features
- [x] **ReplyComponent.setStory()** - Story preview in replies
- [x] **Settings.hideStories()** - Toggle to hide stories
- [x] **String resources** - All story-related strings added

## In Progress

### Phase 4: Story Posting UI
- [x] **StoryComposeController.java** - Entry point for posting stories
  - Shows camera/gallery options
  - Uses TdApi.PostStory to send
  - Checks TdApi.CanPostStory permission
- [x] Add "Add Story" button to StoryBarView
  - AddStoryItemView with plus icon and gradient ring
  - setCanPostStory() to control visibility
  - checkCanPostStory() in ChatsController
  - Opens StoryComposeController on click
- [ ] Story editor with text/stickers (not started)

## In Progress

### Phase 5: Story Interactions
- [x] Reply input in StoryViewController
  - StoryReplyInputView with EditText, send, and heart buttons
  - sendReply() via TdApi.SendMessage with MessageReplyToStory
  - Pauses story progress when input is focused
- [x] Reactions (double-tap for heart)
  - sendHeartReaction() via TdApi.SetStoryReaction
  - Double-tap detection in center of screen
- [ ] StoryViewersController.java (who viewed your story) - pending

## Pending

### Phase 7: Polish and Edge Cases
- [ ] Expired story handling
- [ ] Loading states/shimmer
- [ ] Stealth mode (premium)
- [ ] Performance optimizations

## TDLib API Used

**Viewing:**
- `getStory(chatId, storyId)`
- `getChatActiveStories(chatId)`
- `openStory(chatId, storyId)`
- `closeStory(chatId, storyId)`

**Posting:**
- `canPostStory(chatId)`
- `postStory(chatId, content, ...)`

**Content Types:**
- `InputStoryContentPhoto`
- `InputStoryContentVideo`
- `StoryPrivacySettings`

## Git Commits

1. `00b6ea2` - Add Stories feature - viewing, story bar, and avatar rings
