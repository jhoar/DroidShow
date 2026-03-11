# DroidShow — Project Skills & Build Knowledge

This file captures practical knowledge learned while building and maintaining DroidShow.

## 1) Product and UX skills
- **Core user flow**: DroidShow is an Android archive image viewer/slideshow app: open an archive (ZIP/RAR/7Z + comic variants), list image entries, decode and display pages, then auto-advance via slideshow controls.
- **Immersive viewing**: Full-screen behavior hides system bars and uses transient swipe behavior so media stays front-and-center.
- **Settings that matter**:
  - Slideshow interval is clamped to a safe range (`1..30` seconds).
  - Display mode toggles between **sequential** and **random traversal**.
- **Resilient status text**: UI status combines current position, total images, resolved archive display name, and any current error.

## 2) Android platform integration skills
- **Intent intake strategy**:
  - Handle launch and `ACTION_VIEW` intents.
  - Support both MIME-based filters and extension fallbacks for providers that mislabel MIME type.
  - Include `content://` and compatibility `file://` path suffix filters for common archive/comic extensions.
- **Storage permission edge cases**:
  - `content://` URIs generally work with persisted URI grants.
  - Legacy `file://` handling requires `READ_EXTERNAL_STORAGE` on older SDKs (up to API 32 in this app logic).
- **Persistable URI permissions**:
  - Only call `takePersistableUriPermission` when persistable grant flags are present.
  - Use `runCatching` around grant persistence because providers can vary.
- **Insets/cutout safety**:
  - Use a policy layer for computing safe top inset from both system bars and cutout values.

## 3) Architecture and state-management skills
- **MVVM with policy extraction**:
  - Keep `MainActivity` focused on view binding and rendering.
  - Move decision logic into policy helpers (`MainActivityRenderPolicy`, `MainActivityInsetsPolicy`, `ViewerStatePolicy`, `ViewerIndexSelector`).
  - This keeps business logic unit-testable without full UI harnesses.
- **State restoration**:
  - Persist key playback/viewer values in `SavedStateHandle` (archive URI, current index, playing flag, interval, display mode).
  - Rehydrate in `init` and conditionally reload archive when URI exists.
- **Reader lifecycle management**:
  - Maintain a single active archive reader keyed by URI.
  - Close and recreate only when archive changes.
  - Always close readers in `onCleared` and on load failures.

## 4) Archive handling skills
- **Type resolution must be redundant**:
  - Detect by both MIME type and filename extension.
  - Build multiple filename candidates: `DISPLAY_NAME`, URI segment, decoded URI suffix, and `DocumentsContract` document ID suffix.
- **Supported archive families**:
  - ZIP/CBZ via commons-compress ZIP flows.
  - RAR/CBR via Junrar.
  - 7Z/CB7 via commons-compress + XZ dependency.
- **Error mapping discipline**:
  - Map technical exceptions into user-facing string resources:
    - Unsupported archive type
    - Unsupported RAR implementation/runtime issue
    - Permission/security failures
    - Corrupt/archive IO failures
    - Generic open failure fallback

## 5) Image listing and ordering skills
- **Entry filtering**:
  - Accept known image extensions (jpg/jpeg/png/webp/gif/bmp/avif/heif/heic).
- **Natural sorting**:
  - Use comparator logic that compares numeric path segments by magnitude (not plain lexicographic order), preserving comic/page sequencing (e.g., `2` before `10`).
- **Random mode correctness**:
  - Build an order permutation and inverse position map.
  - Keep current item anchored when switching modes by repositioning current index in traversal.
  - Rebuild traversal state when shape invariants break.

## 6) Slideshow engine skills
- **Loop behavior**:
  - Playback loop shows next image then delays by current interval.
  - Interval changes while playing should restart loop cleanly.
- **Mode-specific navigation**:
  - Sequential: wraparound modulo count.
  - Random: cycle through shuffled order; rebuild between rounds.
- **Playback safety**:
  - Stop loop when no entries exist or when changing archive during playback.

## 7) Test strategy skills
- **Unit-first with policy classes**:
  - Add isolated tests for index selection, state policies, render policies, insets policy, intent compatibility, and archive resolution.
- **Robolectric for Activity behavior**:
  - Use Robolectric where Android resources/lifecycle behavior is required.
- **Regression focus areas**:
  - Random traversal invariants and edge cases.
  - Archive type fallback behavior (mislabelled MIME or missing MIME).
  - Settings dialog defaults, clamping, and mode persistence.

## 8) Build/CI environment skills
- **Offline-friendly Gradle workflow**:
  - Repository includes scripts to pre-seed wrapper distribution cache and run offline integration checks.
  - In restricted environments, ensure wrapper distribution ZIP is present in `~/.gradle/wrapper/dists/...` before invoking Gradle wrapper.
- **Wrapper expectations**:
  - Wrapper JAR must exist and contain `GradleWrapperMain`.
  - Integration script runs `./gradlew --offline --no-daemon <task>` to enforce deterministic/no-download runs.

## 9) Implementation conventions that worked well
- Prefer small pure helper objects for branching logic.
- Keep user-visible errors in resources, not inline strings.
- Avoid duplicate archive loads by gating `loadArchiveIfNeeded` with URI/loading-state policy.
- Keep long-lived resources (`ArchiveReader`) explicit and closeable.
- Favor defensive parsing (`runCatching`, null-safe fallbacks) around content-provider metadata.

## 10) Known extension points for future work
- Add explicit zoom/pan gestures and page scrubber.
- Add persisted app settings via DataStore (dependency already present).
- Add cached bitmap decode/downsampling for large pages.
- Expand archive/password handling and better RAR feature detection.
- Introduce instrumentation tests for real provider URI scenarios.
