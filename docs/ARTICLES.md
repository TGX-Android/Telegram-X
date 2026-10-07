# Native article messages

This feature adds TDLib rich messages to Telegram X's existing message, composer,
draft and media flows. It is independent of the forum-topic contribution: no
forum controllers, product branding, private configuration or native changes are
added by the feature diff.

## User-facing behavior

- Articles render in message history using native page blocks, with text styles,
  links, headings, lists, quotes, tables, formulas, buttons and media. Incomplete
  rich messages can load their full content; a failed load can be retried.
- The ordinary composer offers expansion after more than two nonempty visual
  lines. Posting respects chat permissions and TDLib's `rich_message_posting`
  option, including the premium-only mode. Secret chats are excluded.
- One inline editor supports creating articles, restoring rich drafts and
  editing eligible existing messages. Its contextual tools act on the selected
  text or block, including nested lists and cross-block selection.
- The editor supports block reordering, split/join operations, undo/redo,
  preview, attachment pickers and a table/formula editor. Imported local files
  are copied into private storage instead of depending on a temporary picker
  permission.
- AI composition/correction uses TDLib requests initiated by the user. There is
  no third-party AI endpoint or separate provider key. Server availability and
  permissions remain authoritative; AI results are previewed before applying.

## Implementation map

| Area | Main entry points |
|---|---|
| Message display and full-content loading | `TGMessageArticle`, `ArticleBodyView`, `PageBlock*` |
| Composer, edit, send and draft integration | `MessagesController`, `ArticleComposer`, `ArticleSendTracker` |
| Editor, preview and contextual controls | `ArticleEditorController`, `ArticlePreviewController`, `ArticleDocumentView`, `ArticleTextInput` |
| Document transformations and history | `ArticleEditorTree`, `ArticleRichText`, `ArticleTableGrid`, `ArticleHistory` |
| Durable drafts and typed conversion | `ArticleDraftStore`, `ArticleDraftFiles`, `ArticleCodec`, `ArticleMapper`, `ArticleValidator` |
| Media and AI result handling | `ArticleFiles`, `ArticleMediaFiles`, `ArticleEditorMedia`, `ArticleAiResult`, `ArticleAiEditor` |

`scripts/generate-article-codec.py` generates the bounded typed codec from the
pinned TDLib schema. Runtime persistence does not use reflection or Java object
serialization. Tests cover round trips, invalid input, transformations, draft
comparison, composer promotion, AI diff normalization and send-state handling.

Formula rendering uses the Gradle dependency `ru.noties:jlatexmath-android`.
Its reflective parser needs the explicit R8 rules included in this change.
Malformed formulas have a text fallback; rendering size/cache bounds are kept.

## Resources and design

The feature uses Telegram X controllers, theme helpers, media components and
translation keys. English strings are in `values/strings.xml`; new translations
need the normal translation-platform import rather than locale-specific source
files. Editor icons have 24x24 viewports and `_24` size suffixes.

The original Telegram editor informed the interaction design. Asset and adapted
layout attribution, including the bundled font license, is recorded in
[ARTICLE_EDITOR_ASSETS.md](../app/ARTICLE_EDITOR_ASSETS.md).

## Review and validation boundaries

This is a new design submitted for discussion. Device checks cover the named
scenarios below; they do not establish every role, device or server outcome.
The implementation and reproduced regression fixes are ready for code review.
This is not a claim of complete device/server coverage or maintainer approval.
The specific limits below remain relevant to merge decisions.

The contribution contains only cropped captures of deliberately created test
articles. Private chat content, account identifiers, credentials, local build
harnesses and APKs are not included.

Review should focus on native message/layout integration, editor UX and
accessibility, draft recovery/lifecycle behavior, server rejection handling,
and unchanged ordinary chat/Instant View behavior.

## Validation snapshot — 2026-10-07

Tested feature source: `7c53686923b792d5f4527b9709169f05babce373`, including
upstream `92f13cbe5eeac21ae06cecb58d92d2c0a676cc53`. Device APK source:
product integration `e7a01a4285210e168dab6d72629f3b5a0eddbffa`. Subsequent
publication changes only documentation.

- **62/62 article JVM tests passed** in seven suites, including attachment
  preservation, equivalent cloud/local drafts, deduplicated file aliases,
  restart-safe media references and cancelled pending sends.
- **594/594 product JVM tests passed**, with no failures, errors or skipped tests.
  ARM64 Debug and R8 Release builds passed with the current upstream native pins.
  Release signature, production flags and 16 KiB ZIP/ELF alignment passed.
- **24/24 isolated Android component checks passed on this feature revision**,
  including 40-paragraph layout, channel text/media widths, nested lists,
  selection formatting, hardware selection navigation and AI result rendering.
  These checks do not initialize accounts or exercise the network.
- Production-source Debug and Release lint reported no new issues; 17 existing
  warnings were filtered by the unchanged baseline. Test-source UAST is excluded
  by the local Windows harness because of an analysis stall; executable JVM
  tests are included. The harness is not part of this contribution.
- Both APKs were updated in place on a **Pixel 6, Android 17 (API 37)**. Existing
  app identities/data were preserved and installed APK hashes were verified.
- Debug restored a draft with text, a file, audio and video after process
  termination, then successfully sent it. Release created a fresh local-file
  draft, reopened it, terminated/restarted the app and restored it without a
  false conflict. Both builds preserved surrounding paragraphs and media when
  Ctrl+A was followed by an arrow and typing.
- Concurrent cloud-draft changes made in the other client still produced the
  intended conflict dialog; choosing the cloud version preserved its text and
  file. A separate concurrent published-message edit produced the intended
  warning; cancelling retained the unsaved local text. Deleting that test
  message in the other client left the local editor recoverable.
- A pending send survived about 2.5 minutes offline and process termination,
  then delivered after reconnecting without a manual resend. Deleting another
  pending send released the editor's send lock and preserved its media/text.
- Server-backed AI tests covered translation preview/apply/undo and previews
  for all seven built-in style modes. Audio and video picker imports survived
  draft recovery and sending; the receiving Release rendered their previews.
- A 5,979-character, 20-paragraph article rendered through its final marker/link
  in Release with networking disabled after receipt. A bold link survived
  publication and reopening Edit. Public-channel article text, links and album
  rendered at channel width in the final Release. No new application crashes
  were recorded in the test window.

Draft fixes compare normalized content and actual file identities instead of
treating TDLib's local numeric file IDs as durable identifiers. Received media
uses remote file references for persistence and sending. Private imported files
can match deduplicated TDLib files by content. Old drafts written before this
fix with session-specific numeric IDs can still require an explicit version
choice; unknown identities are not silently treated as equal.

The previous device round at `9ca7f45b` verified the three earlier attachment/
checklist fixes in both builds, including caption editing, pixel-stable checkbox
text and reception in official Telegram 12.10.6. It also exercised formula/link/
table examples and album navigation. Those results are historical, not a fresh
run of every earlier scenario on the latest APK.

### Coverage limits

- The server returned the long article in full. A genuinely partial response,
  full-load failure/retry and a delayed response racing a close/update were not
  reproduced on the live server; source lifecycle guards and component layout
  checks do not substitute for those network scenarios.
- No controlled premium transition, posting-role/media-ban change, expired
  server file reference, AI rejection or large-file upload interruption was
  induced. The offline test covers a pending send, not every upload state.
- A 150% font-scale editor/formula smoke and TalkBack service/node smoke passed.
  Spoken output and complete focus order need human accessibility acceptance.
  The RTL block command was exercised and undone; complete RTL-language UI
  acceptance is not claimed. Nested button combinations were not exhaustively
  exercised on the server.
- Device coverage is Pixel 6, Android 17, ARM64. Maintainer CI and review remain
  separate from these local results; no upstream CI result is claimed.

### Device examples

These are pixel-preserving crops of actual ADB screenshots from the earlier
Release build `9ca7f45b`, captured on 2026-10-07. Only purpose-made test articles
are shown.
Surrounding chats, pinned messages and system bars were cropped out; no UI was
redrawn or composited. The installed application's Russian UI is shown as tested;
upstream translation resources still follow the normal translation process.

Published articles with formatted text, a link, formula, checklist, table and file:

![Article messages on Pixel 6](images/articles/article-native-blocks.png)

Selected text changes the editor's contextual formatting tools:

![Article editor selection on Pixel 6](images/articles/article-inline-editor.png)

Reopening a server-received attachment with no caption:

![File without caption remains visible in Edit](images/articles/article-attachment-no-caption.png)

Editor with its ordinary block toolbar:

![Article editor on Pixel 6](images/articles/article-editor-full.png)
