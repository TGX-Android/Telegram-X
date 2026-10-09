# Forum UI examples

## Application screenshots

These four screenshots were supplied by the maintainer on October 10, 2026,
from the development fork. They show the public **The Forum** group, not the
private test forum. The images are preserved as supplied, including the
existing chat-rail avatar redactions. They are UI illustrations, not new
runtime verification of the feature-only PR APK or before/after comparisons.

### Forum preview in the chat list

The group name, recent topic icons/names, latest sender/message preview and
unread count appear in one chat row.

<img src="images/forum/04-chat-list-topic-preview.png" width="640" alt="Forum chat row with recent topics and an unread badge">

### Topic list and chat navigation rail

The full-width group header sits above the topic list and the narrow chat rail.
Topic icons and right-aligned status indicators stay inside their rows.

<img src="images/forum/05-topic-list-chat-rail.png" width="320" alt="Topic list with a full-width header and left chat rail">

### Topic profile and shared files

The profile exposes Messages and Unmute, a visible topic link with a copy
action, and topic-scoped shared-media filters with the Files tab selected.
Available actions depend on the user's permissions and topic state.

<img src="images/forum/06-topic-profile-shared-files.png" width="320" alt="Topic profile with actions, a visible link and shared-file filters">

### Topic profile actions

The overflow menu offers notification settings, all-message/topic-list views
and leaving the group for the captured permissions and state.

<img src="images/forum/07-topic-profile-actions.png" width="320" alt="Topic profile overflow menu">

## Synthetic component examples

These PNGs were rendered on Android by `ForumUpstreamDemo` using the production `ForumTopicView` and `ForumTopicsTabsView`. All names, messages, times and counters are invented in-memory fixtures. The test target has no account, application components or network permission.

These are **component crops**, not full-screen application screenshots, before/after comparisons, server tests or frame-time measurements. Topic rows use the isolated presentation-field fixture rather than account-dependent binding. Topic selectors use the real `setTopics` and RecyclerView layout paths. Default topic icons are shown; custom emoji are not downloaded.

### Topic rows (light theme)

Two-line rows reserve the right-hand area for time, pin/mute/closed/delivery indicators and counters. Drafts replace the message preview. The title line includes the topic icon.

![Synthetic topic rows in the light theme](images/forum/01-topic-rows-light.png)

### Horizontal selector (dark theme)

The compact selector includes All, ordered topic tabs, unread indicators, selection and a fixed placement control. The crop uses a wide viewport to show several tabs at once; narrower viewports scroll.

![Synthetic horizontal topic selector in the dark theme](images/forum/02-selector-horizontal-dark.png)

### Side selector (dark theme, RTL, 180% font scale)

This deliberately narrow, tall crop shows every synthetic item. Labels ellipsize at the larger font scale; the complete localized text remains in the accessibility description. The viewport normally scrolls independently of the fixed placement control.

![Synthetic side topic selector with RTL and enlarged text](images/forum/03-selector-side-dark-rtl-font180.png)

See `FORUM_ACCEPTANCE.md` for the broader, separately recorded device/server acceptance matrix and the account-isolated test target.

## Legacy Android behavior

On API 16–17, forum navigation uses the same-progress fade instead of avatar morphing because view clip bounds require API 18. API 18+ keeps the morph transition. Relative layout and live-region APIs are guarded; API 16 uses the platform's left-to-right layout with physical padding/margins. Labeled accessibility actions are registered on API 21+, while older versions retain node descriptions and standard click behavior.

These compatibility paths need separate old-device verification; the component images above were not captured on legacy Android.
