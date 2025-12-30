# Telegram-X Project Instructions

## Task Tracking

**IMPORTANT:** Always keep track of work progress in `TASKS.md` file at the project root.

When working on features or bug fixes:
1. Add new tasks to `TASKS.md` under appropriate section
2. Update task status as work progresses
3. Document files created/modified
4. Include TDLib functions used for API-related changes
5. Keep the bug fixes section updated with root cause and solution

## Project Structure

- Main app code: `app/src/main/java/org/thunderdog/challegram/`
- Resources: `app/src/main/res/`
- TDLib bindings: `tdlib/` and `vkryl/td/`
- Build output: `app/build/outputs/apk/`

## Build Commands

```bash
# Debug ARM64 build
./gradlew assembleLatestArm64Debug

# Debug x64 build
./gradlew assembleLatestX64Debug

# All debug variants
./gradlew assembleDebug
```

## Key Files

- `TASKS.md` - Feature implementation tracking and bug fixes documentation
- `app/src/main/res/values/strings.xml` - String resources
- `app/src/main/res/values/ids.xml` - View and controller IDs
