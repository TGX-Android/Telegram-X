# Branch Reconstruction — clean core + per-feature topology (2026-06-13)

Goal (from the user): the documented per-feature workflow, done for real —
**core = `origin/main` + the TDLib upgrade, consistent**; every feature an
**independent branch off that core**; `all-features-combined` = merge of all.
Nothing is pushed until the whole thing builds locally.

## What was wrong

- All ~197 custom commits were piled onto local `main` (an already-merged tangle:
  11 merge commits + 14 duplicate commits). My audit-fix commit landed there too.
- **The TDLib upgrade was never committed.** It lived only in dirty submodule
  working trees:
  - `tdlib`: 649 uncommitted files — custom `TdApi.java` (regen from tdlib/td@cecbf129
    "rich messages API", newer than any official TGX `tdlib` release), `Client.java`,
    recompiled `libtdjni.so` ×4 ABIs, OpenSSL, and the `source/td` pointer.
  - `vkryl/td`: 10 uncommitted regenerated binding files.
  - This is why a clean checkout couldn't build.
- `base/tdlib` was NOT a clean core — it's the whole feature pile with the TDLib
  upgrade stacked on top as the last 2 commits.

## DONE this session (all local, nothing pushed)

### Submodules committed properly (root-cause fix)
- `tdlib` → **`a0fc946f`** on branch `tgx/tdlib-richmessages-d6debbb`
- `vkryl/td` → **`4d92b85`** on branch `tgx/td-richmessages-bindings`

### Clean `core/tdlib` built off `origin/main` — COMPILES GREEN
`compileLatestX64DebugJavaWithJavac` passes. 4 commits:
1. `79d60b98c` pin tdlib + vkryl/td to the committed rich-messages SHAs
2. `d17b0208f` buildSrc Windows file-lock fix (cherry-pick of df2e60b64 — needed for codegen on Windows)
3. `cb9ebf165` TDLib upgrade upstream adaptations (non-conflicting hunks)
4. `fd77c21cf` finish upstream API migration (assert-hash renames, InputPhoto/
   InputMessageReplyToMessage/Message ctor changes, toDraftInputMessageText helper,
   removed InternalLinkType/SettingsSection cases for types absent in new TdApi,
   dropped UpdatePendingTextMessage, de-duped StakeDice, pinned vkryl/core to origin/main)

Method that worked: branch off `origin/main`, pin new submodules, `git apply --reject`
the upgrade's PURE diff (`git diff 1b1978f48 86c2582a7 -- <file>`) so feature-region
hunks reject, then compiler-guided fixes. **Zero feature code in the core** (verified).

### 12 features rebased onto `core/tdlib` (all compile green)
| branch | base it was on | commits | status |
|--------|---------------|---------|--------|
| `feature/playback-speed` | base/tdlib-main | +1 | ✅ compiles |
| `feature/profile-notes` | base/tdlib-main | +1 | ✅ compiles |
| `feature/disposable-voices` | base/tdlib-main | +1 | ✅ compiles |
| `feature/reactions-improvements` | base/tdlib-main | +2 | ✅ compiles |
| `feature/rich-messages` | base/tdlib | +1 | ✅ compiles |
| `feature/saved-tags` | origin/main | +1 | ✅ compiles (stripped RestrictionListener contamination) |
| `feature/voice-transcription` | origin/main | +1 | ✅ compiles (stale-TdApi hunks dropped) |
| `feature/premium-billing` | origin/main | +1 | ✅ compiles (BuyStars deep-link dropped — type gone) |
| `stories-implementation` | origin/main | +1 | ✅ compiles (ChatsAdapter story-bar recovered from main) |
| `feature/community-features` | origin/main | +1 | ✅ compiles (me.vkryl.td→tgx.td, transcoder/icon/listener fixes) |
| `forum-topics-implementation` | origin/main | +1 | ✅ compiles (ForumTopicView from main, messageThreadId→forumTopicId) |
| `feature/quotes` | origin/main | +1 | ✅ compiles (stripped ReX + forum, restored CounterTextPart) |

All 12 above are rebased onto `core/tdlib` and **compile green** individually
(`compileLatestX64DebugJavaWithJavac`). Most use the **net-diff-reject** method:
reset branch to core, `git diff <BASE> <branch> | git apply --reject`, discard the
pure-TdApi-divergence rejects (core has the correct versions), re-apply real feature
hooks, compiler-fix old-API usage + cross-feature contamination, commit. Originals
backed up as `backup/pre-rebase/<branch>` tags.

Key recurring fixes: assert-hash renames are in core; old `Message`/`InputMessagePhoto`/
`InputMessageReplyToMessage` ctors → core's new ones; `me.vkryl.td`→`tgx.td`; strip
contamination (ReX, forum `forumTopic`/`selectedForumTopics`, `RestrictionListener`,
transcoder import); incomplete branches need pieces recovered from `main`
(`ChatsAdapter` story bar, `ForumTopicView`).

## REMAINING (TODO) — 2 hardest features

Both reset to `backup/pre-rebase/*` (untouched, clean). Bases:

| branch | base | commits | why hard |
|--------|------|---------|----------|
| `feature/gifts` | base/tdlib | +17 | Full gift-economy sub-system (auctions/resale/craft/collections). Pile-polluted. Needs main's gift FILES (already migrated) + main's shared-file hooks extracted gift-only: Tdlib gift-auction listener infra (~9171-9235 + update-switch cases at 10972-10977), ProfileController gift entry, ContentPreview gift dedup, TGMessage gift renderer wiring (recipe in git history of this branch's earlier attempt). |
| `feature/mini-apps` | origin/main | +86 | **NOT a clean feature** — it's the OLD all-features integration (forum + stories + premium-billing **were merged into it**, per CLAUDE.md). Must be SLIMMED to web-apps-only (WebAppController/WebAppProxy/web-app UI + the `WebAppUrl`/`ShareUsersWithBot` TdApi adaptations), dropping the forum/stories/premium content (now on their own branches). Applying it whole would duplicate those and break all-features-combined. |

### Two contamination patterns discovered (the reason these are hard)

1. **Pile pollution** (`gifts`, base/tdlib-derived): commits' diff context is laced
   with OTHER features' code. e.g. gifts slice 2's `ids.xml`/`ProfileController`
   changes sit among Stories ids and Profile-Notes handlers. Must extract ONLY the
   gift lines from each conflict. Resolve gift-only; drop the rest.

2. **Cross-feature contamination in shared files** (origin/main features): a feature's
   version of a giant shared file (MessagesController, ShareController, Text) carries
   code from features its branch was developed on top of, even though merge-base = origin/main.
   Examples found:
   - `saved-tags` MessagesController implemented `VoiceVideoButtonView.RestrictionListener`
     (from reactions-improvements) — stripped it.
   - `quotes` MessagesController imported `ni.shikatu.rex.ReXConfig/ReXUtils` (a "hide
     buttons" fork customization — NOT a quotes feature) AND referenced `forumTopic` /
     `selectedForumTopics` (from forum-topics). All must be stripped to extract quotes cleanly.
   - `quotes` Text.java removed the `util.text.counter.CounterTextPart` import (its base
     had the class in a different package) — must restore the import.

### Recommended approach for the remaining (per the contamination)
- For each old-TdApi feature: `git rebase --onto core/tdlib origin/main <branch>`,
  resolve conflicts, then `compileLatestX64DebugJavaWithJavac` and fix:
  (a) new-TdApi adaptations (same kinds as the core migration), and
  (b) strip cross-feature contamination (ReX, forumTopic, RestrictionListener, etc.)
      down to ONLY the feature's own code.
  Amend the rebased commit(s) with the fixes. Verify compile. Move on.
- `gifts`: rebase `--onto core/tdlib base/tdlib feature/gifts`; resolve every conflict
  by keeping gift-only lines.
- `mini-apps` (+86): the biggest. Consider whether to keep granular history or squash
  to a single clean feature commit off core (net feature-only diff) given the effort.

### Step 4 (after all features)
Rebuild `all-features-combined` = fresh branch off `core/tdlib`, merge every feature,
resolve cross-feature conflicts, full local build (Java + native + APK). THEN, only
after it's all green locally, push. Update CLAUDE.md branch table to the real topology.

## Build notes
- `$env:GRADLE_USER_HOME = "F:\DevCache\.gradle"` is mandatory.
- Java compile: `.\gradlew.bat compileLatestX64DebugJavaWithJavac`
- If codegen hits "user-mapped section open" (Windows mmap lock): `.\gradlew.bat --stop` first.
- Submodule working trees are SHARED across parent branches; rebasing onto core keeps
  core's submodule pins (features don't touch submodule pointers).

## Safety
- `backup/main-pre-split-20260613` = full pre-reconstruction `main` (08755dd09).
- `backup/pre-rebase/<branch>` = each feature branch's pre-rebase state.
- Submodule upgrade content is committed on the `tgx/*` submodule branches (recoverable).
