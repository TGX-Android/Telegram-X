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
| Durable drafts and typed conversion | `ArticleDraftStore`, `ArticleCodec`, `ArticleMapper`, `ArticleValidator` |
| Media and AI result handling | `ArticleMediaFiles`, `ArticleEditorMedia`, `ArticleAiResult`, `ArticleAiEditor` |

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

This is a new design and is submitted for discussion, not as a claim that every
device, role or server operation has been tested. The PR description records
the exact tested revisions, current checks and remaining matrix. In particular:

- Rendering/editor checks with synthetic data do not prove server-side
  send/edit permissions, premium transitions or all AI outcomes.
- Product APK checks do not substitute for runtime testing of the standalone
  feature branch. The product additionally contains forum integration.
- Legacy static lint diagnostics and device/ABI coverage must be reported
  separately from successful modern compilation and unit tests.
- No private chat captures, account identifiers, credentials, local build
  harnesses or APKs are part of this contribution.

Review should focus on native message/layout integration, editor UX and
accessibility, draft recovery/lifecycle behavior, server rejection handling,
and unchanged ordinary chat/Instant View behavior.

## Validation snapshot — 2026-10-07

Tested feature source: `cf7ce04fac65e363c78a7f891add39b1f02316cc`, including
upstream `7e3e3a3b1d658addd10514e2efd97de6e3ba1a42`.

- **53/53 JVM tests passed** in seven suites, with no failures or errors.
- Modern ARM64 production-source Debug lint reported no new issues; 17
  existing upstream warnings were filtered by the unchanged baseline.
- **23/23 synthetic on-device rendering/editor checks passed**, including
  recycled layouts, widths, list/table editing, partial and cross-block
  selection, formatting state, block reordering and AI result presentation.
- Regenerating the typed codec from the pinned schema produced identical
  output. New editor vector assets were checked for 24x24 viewports and the
  size suffix required by the contribution guide.

These checks used a local Windows worktree adapter. It resolves junction paths
for build metadata and reuses exact-pin native outputs from the product build;
the adapter is not part of this PR. Production lint excludes test-source UAST
because of a local analysis stall; the executable JVM tests are not excluded.
This is not an independent Linux/native build of the feature branch.

The instrumentation target is an isolated, component-free application with no
network access or account initialization. It exercises actual feature widgets
but is not the normal Telegram X application. Fresh legacy/all-ABI coverage,
end-to-end server send/edit/AI permissions, lifecycle recovery and accessibility
acceptance remain separate work before marking the contribution ready to merge.

### Synthetic examples

The following images were exported directly from the tested native widgets.
They contain generated test text only, not real chats. Component renders do
not include the normal application's navigation or keyboard.

Native article blocks:

![Native article blocks](images/articles/article-native-blocks.png)

Inline document editing:

![Inline article document](images/articles/article-inline-editor.png)

Editor layout with contextual tools and undo/redo:

![Article editor](images/articles/article-editor-full.png)
