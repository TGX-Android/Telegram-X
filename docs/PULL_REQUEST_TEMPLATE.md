The guide below provides the flow for creating a perfect pull request to the Telegram X repository. Before submitting your PR, ensure that it complies with the following principles.

## Perfect PRs must be:
- [ ] **Rational**. Changes are explained explicitly in a few short, concise sentences.
- [ ] **Scoped**. Changes solve only a single problem and do not combine multiple ones at once.
- [ ] **Minimal**. Changes are the smallest and simplest solution to the problem: no new abstractions, options or rewrites it doesn't require (a 2-line fix is better than a 40-line one with the same result; extracting repeated code into a function is fine if it results in smaller or clearer code). Existing components from appropriate modules are reused.
- [ ] **Self-explanatory**. Code is clear, easy to understand and easy to change on its own: names are descriptive and not abbreviated (`destinationDir`, not `dstDir`). Documentation and comments describe rules, contracts, or when and why to use the code, but never restate what it does.
- [ ] **Clean**. There are no unnecessary or accidental changes. Code is not duplicated: repeated logic is extracted into a short, well-named function.
- [ ] **Consistent**. Code follows the existing style of the project and `.editorconfig`. Braces with line breaks are always used, even when optional, except for short or single-expression Kotlin functions, which may use `=`.
- [ ] **Designed**. UI changes follow the existing design. If there is none, the PR includes before and after screen recordings for each change.
- [ ] **Compatible**. Changes work down to API 16: classes missing on older versions are never imported and are referenced by their full name only inside version checks, never in fields, method signatures or `catch` clauses outside them. Compatibility dependencies are added only to flavors that need them.
- [ ] **Graceful**. Features unavailable on older versions are hidden or show the required Android version, with the reason explained in the PR.
- [ ] **Modern**. Latest APIs are used where available, with deprecated ones only as a fallback for older versions.
- [ ] **Transparent**. Changes made with the help of LLMs are annotated with `@MadeByLLM` and the exact model used (see its documentation).
- [ ] **Accountable**. All commits are authored by you, without `Co-authored-by` or other attribution to LLMs: the model used is already credited via `@MadeByLLM`.
- [ ] **Traceable**. Commit titles fit into 65 characters, and descriptions briefly explain the reason for the change, unless the title already does. Each `id` added to `@MadeByLLM` or `@MadeByHuman` is also included as a `Context-Id: <id>` trailer of that commit.
- [ ] **Measured**. Optimizations are backed by a benchmark or memory measurement when possible: the PR includes how it was measured and the results before and after.
- [ ] **Completed**. All changes are properly tested (including manually) and ready to be merged.
- [ ] **Up-To-Date**. Your PR is based on the **latest** commit of the `main` branch.

## When fixing bugs, make sure your changes are:
- [ ] **Fundamental**. Changes fix the root cause of an issue, not its symptoms.
- [ ] **Separated**. Different bug fixes are divided into independent PRs.
- [ ] **Linked**. The issue number is in the title, and the description links the issue.
- [ ] **Safe**. The fix doesn't risk breaking other screens or specific devices.

## When adding features, keep them:
- [ ] **Small**. Small, self-contained improvements are welcome in any part of the app.
- [ ] **Isolated**. Larger changes are accepted only in areas most users don't depend on, such as statistics. Large changes to core areas, such as the chat list, chats, messages, calls or notifications, are made only by maintainers. PRs implementing them are closed without review.
- [ ] **Gradual**. Have only one open PR at a time. Prioritize smaller ones first until they are merged.

## Other possible contributions:
- **Improved build speed**. Include before and after build times and the configuration used for measuring them.
- **Windows build fixes**. Building on Windows isn't tested by maintainers and may break after big changes, so fixes are welcome as separate PRs.
- **Dependency update**. Each update is rational and focuses on a single dependency.

## After submitting, expect:
- **Discussion**. Be ready to receive and follow review comments and edit suggestions.
- **Dismissal**. PRs that don't match the quality, stability and style of the app, or harm the UX are declined. Declined features must not be resubmitted unless requested.

Good luck, and thanks for the contribution!