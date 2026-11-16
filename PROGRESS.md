# PROGRESS

## Done (2026-11-16)
- Brainstormed and wrote design spec: `docs/superpowers/specs/2026-11-16-image-manager-design.md`
  — native Android (Kotlin) app for Galaxy phones. Card-swipe main UI (Slidebox-style: up=trash,
  down=move to pile/folder, left/right=navigate, long-press=favorite). Favorites/trash go through
  MediaStore (`IS_FAVORITE`, `createTrashRequest`) so state stays in sync with Samsung Gallery.
  Similar/duplicate photo detection via dHash perceptual hashing + bucketed union-find clustering,
  on-demand (no background scanning).
- Wrote implementation plan: `docs/superpowers/plans/2026-11-16-image-manager-app.md` (13 tasks).
- Implemented all 13 tasks on branch `implement-image-manager-app`, each committed separately:
  scaffold → MediaStore repo → Room cache → dHash → clustering → duplicate scanner → permission
  gate → photo grid → favorites → piles (folders) → card swipe screen → similar-photos screen →
  navigation host wiring everything together.
- 28 unit tests passing (`./gradlew testDebugUnitTest`). `./gradlew assembleDebug` produces a
  working APK at `app/build/outputs/apk/debug/app-debug.apk`.
- Project folder was renamed from `이미지 정리 어플` (Korean) to `ImageManager` — AGP's Windows
  path check rejects non-ASCII project paths. The old Korean-named folder still exists on disk
  (deletion was blocked by the sandbox's path-safety guard) but is now an orphaned copy; safe to
  delete manually.

## Decisions
- **MediaStore is the source of truth** for photos, favorites, and trash — the app never keeps a
  parallel favorite/delete flag. This was a deliberate design choice so the app's state always
  matches Samsung Gallery's own "즐겨찾는 항목" / "최근 삭제".
- **`MANAGE_EXTERNAL_STORAGE`** permission used (not scoped-storage-only) because this is
  explicitly a personal/family sideload app, not a Play Store release — chosen for frictionless
  swipe-to-move-to-folder without a confirmation dialog on every swipe.
- **5-star rating feature is out of scope** for this plan (user requested it but agreed to defer
  it rather than complicate the first build). Schema has room to add a `PhotoRating` table later
  without touching existing tables.
- `local.properties` must use forward slashes (`sdk.dir=C:/Users/...`) — Java `.properties`
  parsing drops backslashes before unrecognized escape chars, silently mangling a
  backslash-escaped Windows path into garbage.
- Room test databases that exercise a `viewModelScope.launch { ... }` fire-and-forget coroutine
  need `.setQueryExecutor(Executor { it.run() })` / `.setTransactionExecutor(...)` (synchronous)
  — otherwise `advanceUntilIdle()` in a `StandardTestDispatcher`-based test won't actually wait
  for the DB write, since Room's suspend DAO calls hop onto their own real executor thread.
- A `StateFlow` built with `.stateIn(scope, SharingStarted.WhileSubscribed(...), ...)` won't
  update `.value` in a test unless something is actively collecting it — tests that read
  `viewModel.someFlow.value` need `viewModel.someFlow.launchIn(backgroundScope)` first.

## Next
- Boot the `Samsung_Galaxy_Z_Fold8_Main_API_34` emulator, run `connectedDebugAndroidTest`
  (instrumented tests have only been compiled, not executed against a real device yet), then
  `adb install` the debug APK and do the manual smoke test listed in the plan's Task 13 Step 6.
- Decide whether to merge `implement-image-manager-app` into `master` (or keep working on the
  branch) once the smoke test passes — see `finishing-a-development-branch` skill.
- Optional cleanup: delete the orphaned `이미지 정리 어플` folder once Windows releases its lock
  on it (was open in some other process — VS Code's Java language server, most likely).

## Open / Blockers
- None currently blocking. Emulator boot was in progress as of last session end — check
  `adb devices` first thing next session.
