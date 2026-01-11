# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Git Workflow

**Always push to fork, not origin:**
```bash
git push fork <branch-name>
# NOT: git push origin
```

## Build Commands

```bash
# Debug builds (most common for development)
./gradlew assembleLatestArm64Debug    # ARM64 emulator/device
./gradlew assembleLatestX64Debug      # x86_64 emulator
./gradlew assembleDebug               # All debug variants

# Release builds
./gradlew assembleUniversalRelease    # Universal release APK

# Setup tasks (run after fresh clone)
./scripts/setup.sh                    # Full setup (SDK + native deps)
./scripts/setup.sh --skip-sdk-setup   # Skip SDK, build native only

# Clean builds
./scripts/reset.sh                    # Reset submodules + clean
./scripts/force-clean.sh              # Force clean build files
```

Build outputs: `app/build/outputs/apk/`

## Project Architecture

### Core Package Structure (`app/src/main/java/org/thunderdog/challegram/`)

| Package | Purpose |
|---------|---------|
| `telegram/` | TDLib wrapper (`Tdlib.java`), account management (`TdlibManager.java`), listeners, caching |
| `ui/` | All screen controllers (see ViewController pattern below) |
| `navigation/` | Custom navigation framework (`NavigationController`, `ViewController`, `HeaderView`) |
| `data/` | Data models for messages (`TGMessage*`), chats (`TGChat`), reactions (`TGReaction*`) |
| `component/` | Complex UI components: chat (`/chat/`), dialogs (`/dialogs/`), stickers, popups |
| `widget/` | Reusable custom views (`AvatarView`, `ChatView`, `StoryBarView`) |
| `theme/` | Theme system, color management |
| `tool/` | Static utilities (`Screen`, `Fonts`, `Views`, `Strings`, `Drawables`) |
| `util/` | Helper classes, text rendering (`Text.java`), formatters |

### Submodules

| Module | Path | Purpose |
|--------|------|---------|
| `tdlib` | `/tdlib/` | TDLib native library + JNI bindings |
| `vkryl:core` | `/vkryl/core/` | Core utilities (not Telegram-specific) |
| `vkryl:android` | `/vkryl/android/` | Android utilities, animators |
| `vkryl:leveldb` | `/vkryl/leveldb/` | LevelDB Java bindings |
| `vkryl:td` | `/vkryl/td/` | TDLib utility extensions |

### Navigation System (NOT standard Android)

Telegram X uses a custom navigation framework instead of Activities/Fragments:

- **`BaseActivity`** → Single activity host, manages `NavigationController`
- **`NavigationController`** → Manages navigation stack, transitions
- **`ViewController<T>`** → Base class for all screens

Creating a new screen:
1. Add ID to `app/src/main/res/values/ids.xml` with `controller_` prefix
2. Create class in `ui/` package extending `ViewController<T>` or subclass
3. Navigate via `navigationController.navigateTo(new MyController(context, tdlib))`

Key lifecycle methods:
- `onCreateView()` → Build UI (called once)
- `onFocus()` / `onBlur()` → Visibility changes
- `needAsynchronousAnimation()` → Delay transition until data loads
- `destroy()` → Cleanup

Common controller types:
- `RecyclerViewController<T>` → List screens
- `ViewPagerController<T>` → Tabbed screens
- `EditBaseController<T>` → Edit screens with done button

### Animation System

Use `me.vkryl.android.animator` classes instead of standard Android animators:
- `BoolAnimator` → Animate between two states (preferred)
- `FactorAnimator` → Animate float values
- `ListAnimator<T>` → Animate list changes
- `ReplaceAnimator<T>` → Animate single item replacement

### Theme Colors

Access colors via `Theme.getColor(R.id.theme_color_*)` - optimized for use in `onDraw()`.
Color definitions: `app/src/main/other/themes/colors-and-properties.xml`

### Strings/Translations

- Main strings: `app/src/main/res/values/strings.xml` (English only)
- Use `Lang.getString()`, `Lang.plural()`, `Lang.getRelativeDate()`
- Translations managed via translations.telegram.org (not local files)

## Issue Tracking (MantisBT)

**Use MantisBT for all task/issue tracking.** Project ID: 1 (Telegram X)

### Workflow:
1. **Before starting work:** Check MantisBT for assigned issues or pick from unassigned
2. **When starting:** Update issue status to "assigned" and assign to yourself
3. **During work:** Add notes with progress updates if work spans multiple sessions
4. **When done:** Add implementation summary note with:
   - Files modified
   - Key changes made
   - TDLib functions used (if applicable)
5. **After testing:** Update status to "resolved" with resolution "fixed"

### Categories:
- General, Interface, Purchases, Stories, Topics

**Note:** Categories can only be created via web UI (API limitation). If a bug doesn't fit any existing category, inform the user so they can create a new one.

### Issue Statuses:
- `new` → Unreviewed
- `assigned` → Being worked on
- `resolved` → Fix implemented, awaiting verification
- `closed` → Verified and complete

### Proactive Behavior:
- When user mentions a bug or feature, check if MantisBT issue exists
- Create new issues for discovered bugs during development
- Link commits to issues in notes when applicable

## TDLib Integration

TDLib functions are accessed via the `Tdlib` class:
- Synchronous: `tdlib.client().send(new TdApi.Function(), handler)`
- Async helpers: `tdlib.getChat()`, `tdlib.getUser()`, etc.
- Listeners: Implement interfaces in `telegram/` (e.g., `ChatListener`, `MessageListener`)

Common TDLib patterns:
```java
// Send request with callback
tdlib.client().send(new TdApi.GetChat(chatId), result -> {
  if (result.getConstructor() == TdApi.Chat.CONSTRUCTOR) {
    TdApi.Chat chat = (TdApi.Chat) result;
    // handle chat
  }
});

// Listen for updates
class MyController extends ViewController implements ChatListener {
  @Override public void onChatUpdated(TdApi.Chat chat) { /* ... */ }
}
```

## Key Implementation Patterns

### Message Types
New message types go in `data/TGMessage*.java`. Register in `TGMessage.valueOf()`.

### Custom Views
- Inherit from `View` or `BaseView` (for 3D-touch/preview support)
- Use `Screen.dp()` for dimensions
- Draw directly on canvas in `onDraw()` for performance
- Never allocate objects in drawing methods

### Resources
- IDs: `app/src/main/res/values/ids.xml`
- Strings: `app/src/main/res/values/strings.xml`
- Icons: Vector drawables with 24x24 viewport

## Windows Build Notes

The project includes a workaround for Windows file locking:
- `kotlin.compiler.execution.strategy=in-process` in `gradle.properties`
- Prevents Kotlin daemon memory-mapped file lock issues

## Code Style

- Double whitespace as tab
- Space before method parameter brace: `void method () {`
- Kotlin allowed in `me.vkryl.*` packages only (must interop with Java)
- Vector drawables: 24x24 viewport, size in filename suffix
