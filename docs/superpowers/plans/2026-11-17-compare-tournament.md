# Compare Tournament Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans (inline) to implement this plan task-by-task.

**Goal:** Replace the keep-and-trash comparison screen with a 2-photo "tournament" screen (main vs challenger, synced zoom, batch trash at the end).

**Architecture:** A pure-Kotlin immutable `CompareSession<T>` holds the rules (delete / hold / promote / undo). `CompareViewModel` exposes it as `StateFlow` so rotation keeps progress. `ComparisonScreen` renders 2 panes (row in landscape, column in portrait) sharing one zoom/pan state, and a summary page that sends the deleted photos to the trash in one `createTrashRequest`.

**Tech Stack:** Kotlin, Jetpack Compose, MediaStore `createTrashRequest`, JUnit4.

## Global Constraints
- Spec: `docs/superpowers/specs/2026-11-17-compare-tournament-design.md`.
- Nothing is trashed until the summary page's button is pressed (one system confirmation).
- Demoted main photo goes to `held` (never deleted automatically).
- First main photo = first photo in the selection order.
- Zoom range 1x..6x; pan is clamped so the image can't leave the pane.

---

### Task 1: CompareSession (pure rules)

**Files:**
- Create: `app/src/main/java/com/imagemanager/app/ui/compare/CompareSession.kt`
- Test: `app/src/test/java/com/imagemanager/app/ui/compare/CompareSessionTest.kt`

**Interfaces:**
- Produces: `CompareSession.start(items: List<T>): CompareSession<T>` (requires size >= 2);
  properties `main: T`, `challenger: T?`, `queue: List<T>`, `deleted: List<T>`, `held: List<T>`,
  `total: Int` (number of challengers), `isFinished`, `canUndo`, `position` (1-based index of current challenger);
  functions `delete()`, `hold()`, `promote()`, `undo()` each returning a new session.

- [ ] **Step 1: Write the failing tests** (see test file: start state, delete, hold, promote, finish, undo chain, <2 items rejected)
- [ ] **Step 2: Run `./gradlew testDebugUnitTest --tests "*CompareSessionTest"` and confirm FAIL (class missing)**
- [ ] **Step 3: Implement `CompareSession` (immutable, `previous` pointer for undo)**
- [ ] **Step 4: Run tests, confirm PASS**
- [ ] **Step 5: Commit** `feat: add CompareSession rules for tournament comparison`

### Task 2: ViewModel + screen + navigation

**Files:**
- Create: `app/src/main/java/com/imagemanager/app/ui/compare/CompareViewModel.kt`
- Modify: `app/src/main/java/com/imagemanager/app/ui/compare/ComparisonScreen.kt` (rewrite)
- Modify: `app/src/main/java/com/imagemanager/app/ui/navigation/AppNavHost.kt` (compare route)

**Interfaces:**
- Consumes: `CompareSession<Photo>` from Task 1.
- Produces: `CompareViewModel(photos: List<Photo>)` with `session: StateFlow<CompareSession<Photo>>` and
  `delete()`, `hold()`, `promote()`, `undo()`;
  `ComparisonScreen(viewModel, requestTrash: (List<Long>) -> IntentSender, onFinished: () -> Unit)`.

- [ ] **Step 1: ViewModel** wrapping the session.
- [ ] **Step 2: Screen** — `BoxWithConstraints` picks Row (width > height) or Column; both panes share `zoom`/`pan` state reset when the challenger changes; challenger pane has 삭제/보류/우선 buttons; header has progress + "이전으로"; summary page when `isFinished`.
- [ ] **Step 3: Nav** — reorder `listByIds` result by selection order; if fewer than 2 photos show a message; create `CompareViewModel` via `viewModelFactory { initializer { ... } }` keyed by the ids.
- [ ] **Step 4: Run** `./gradlew testDebugUnitTest assembleDebug compileDebugAndroidTestKotlin`, confirm BUILD SUCCESSFUL.
- [ ] **Step 5: Commit** `feat: tournament-style comparison screen with synced zoom`

### Task 3: Release

- [ ] Update `PROGRESS.md`, push branch, publish `v0.8.0` debug APK on GitHub Releases (user downloads from there).
