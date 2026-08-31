# Swift Browser — Full Feature Technical Master Specification & Architecture Map

Swift Browser is a high-performance, modular, native Android web browser built on a modern Jetpack Compose interface with robust Kotlin orchestration, custom asynchronous JavaScript webpage probes, and an optimized, high-throughput native C++ execution layer connected via JNI.

This document serves as the central technical reference, file-by-file audit, engine inventory, and development roadmap for the Swift Browser platform.

---

## 1. Overview

Swift Browser is engineered as an offline-first, highly responsive, privacy-preserving, and developer-centric browser environment. It is built directly on top of Android's system `WebView` kernel, heavily customized via low-level interceptors, custom `WebChromeClient` and `WebViewClient` implementations, an advanced JavaScript bridge registry, and native C++ accelerators.

### Design Philosophy
- **Modular Micro-Kernels**: Rather than a monolithic codebase, features are encapsulated as self-contained "engines" that implement common lifecycle interfaces (`SwiftModule`) with strict dependency verification and lazy-loading rules.
- **Asymmetric Execution**: 
  - **Kotlin** orchestrates high-level UI, lifecycle loops, permission state machines, settings dashboards, and SQLite Room databases.
  - **JavaScript** executes within the isolated page-context of the WebView frame, performing lightweight DOM analysis, Online Video stream signature scanning, and interactive reader extraction.
  - **C++** compiles directly to native machine code (`.so`), executing high-frequency mathematical or multi-threaded tasks such as parallel file-chunk assembly, hash verification, metadata parsing, and network optimizer calculations.
- **Trace Master Pipeline Telemetry**: Every state transition, native call, permission decision, and media candidate discovery emits a deterministic `TraceEvent` to a central memory repository. This makes the runtime fully observable in real-time.

---

## 2. Core Architecture

The system utilizes an event-driven, reactive MVVM (Model-View-ViewModel) architecture paired with a centralized Engine Registry and Trace Log pipeline.

```
                  ┌─────────────────────────────────────────────────────────┐
                  │                 Jetpack Compose UI Layer                │
                  │   [BrowserScreen] [DeveloperModeScreen] [MediaCenter]  │
                  └────────────────────────────┬────────────────────────────┘
                                               │ (Observes Flows)
                                               ▼
                  ┌─────────────────────────────────────────────────────────┐
                  │                BrowserViewModel / State                 │
                  │   - Global Health Scorer  - Pipeline Latency States     │
                  └────────────┬───────────────────────────────▲────────────┘
                               │                               │ (Traces)
                               │ (Calls Orchestration)         │
                               ▼                               │
┌─────────────────────────────────────────────────────────┐    │
│            Swift Kotlin Engine Registry / Modules       ├────┤
│   [SwiftBrowserCoreManager] [SwiftPermissionEngine]     │    │
│   [SwiftDownloadEngine]     [SwiftVideoDetectionEngine] │    │
└──────────────┬──────────────────────────────┬───────────┘    │
               │ (JNI Calls)                  │ (JS Injection) │
               ▼                              ▼                │
┌─────────────────────────────┐┌──────────────────────────────┐│
│      Native C++ Layer       ││     Page-Side JavaScript     ││
│  [ChunkAssembler.cpp]       ││  [swift_media_probe.js]      ├┘
│  [NativeMediaEngine.cpp]    ││  [innertube.js]              │ (Bridge Callbacks)
└─────────────────────────────┘└──────────────────────────────┘
```

### Module Lifecycle Contract (`SwiftModule`)
Each major engine registers itself with the `EngineRegistry` and conforms to the lifecycle defined in `SwiftContracts.kt`:

```kotlin
interface SwiftModule {
    val engineId: String
    val isEnabled: Boolean
    fun onInitialize()
    fun onShutdown()
    fun queryState(): EngineStateModel
    fun resetState(): Boolean
}
```

This contract ensures that any sub-system can be dynamic-loaded, disabled, hard-reset, or warm-recovered in the event of an internal state crash or runtime regression.

---

## 3. Engine Inventory

A comprehensive catalog of the custom-built browser engine subsystems active within the Swift codebase:

| Engine ID | Name | Source File(s) | Primary Methods | Implementation Class / Object | Startup Cost (Est.) | Dependency Chain | Status |
|---|---|---|---|---|---|---|---|
| `browser_core` | Browser Core | `BrowserCoreManager.kt`, `SwiftContracts.kt` | `initialize()`, `sleepInactiveTabs()` | `SwiftBrowserCoreManager` | 12ms | None | **Active** |
| `permission_engine` | Permission Engine | `DynamicPermissionEngine.kt`, `SwiftContracts.kt` | `requestPermission()`, `savePermissionDecision()`, `completeRequest()` | `SwiftPermissionEngine` | 4ms | `browser_core` | **Active** |
| `download_engine` | Download Engine | `DownloadManager.kt`, `NativeDownloadEngine.kt` | `nativeCalculateChunks()`, `nativeAssembleChunks()`, `nativeVerifyFileIntegrity()` | `SwiftDownloadEngine` | 15ms | `network_core` | **Active** |
| `video_engine` | Video Engine | `SwiftVideoEngine.kt`, `VideoStateManager.kt` | `matchRules()`, `captureState()`, `restoreState()` | `SwiftVideoDetectionEngine` | 8ms | `browser_core` | **Active** |
| `extension_engine` | Extension Engine | `SwiftExtensionPermissionEngine.kt`, `ExtensionsOverlay.kt` | `getPermissionDecision()`, `setPermissionDecision()`, `resetExtensionPermissions()` | `SwiftExtensionPermissionEngine` | 22ms | `browser_core` | **Active** |
| `desktop_engine` | Desktop Engine | `DesktopModeManager.kt`, `NativeDesktopEngine.kt` | `evaluateDesktopRule()`, `overrideMetrics()` | `DesktopModeManager` | 3ms | None | **Active** |
| `audio_engine` | Audio Engine | `NativeAudioEngine.kt`, `AudioProcessor.kt` | `processAudioStream()`, `applyEqualizer()` | `NativeAudioEngine` | 9ms | `media_engine` | **Active** |
| `trace_engine` | Trace Engine | `TraceRepository.kt`, `TraceModels.kt` | `emit()`, `observeAll()`, `clear()` | `InMemoryTraceRepository` | 1ms | None | **Active** |
| `security_engine` | Security Engine | `SecurityCenterScreen.kt` | `isSecureOrigin()`, `validateSsl()` | `SwiftSecurityManager` | 5ms | `browser_core` | **Active** |

---

## 4. Feature Inventory

The complete breakdown of consumer-facing features organized by browser sub-systems, including their file ownership, native dependencies, and production readiness:

### 4.1 Browser Core Features
- **Ultra-Fast Startup**: Implements asynchronous pre-warming of the WebView kernel and deferred classloader initialization. Served by `BrowserCoreManager.kt`. Status: **Working**.
- **Smart Tab Restore**: Reads persisted SQLite tab snapshots to reconstruct the page backstack. Served by `TabStateManager.kt`. Status: **Working**.
- **Sleeping Tabs**: Suspends background JS execution loops and frees render memory for pages inactive for more than 10 minutes. Served by `BrowserCoreManager.kt`. Status: **Working**.
- **Tab Groups**: Allows users to bucket multiple URLs into named visual cards. Served by `BrowserScreen.kt`. Status: **Partial**.

### 4.2 Download Engine Features
- **Multi-thread Chunk Downloader**: Calculates non-overlapping byte ranges and spawns multiple parallel download workers. Served by `NativeDownloadEngine.kt` and `ChunkAssembler.cpp`. Status: **Working**.
- **Smart Resume**: Saves downloaded chunk progress indexes to survive process termination. Served by `NativeDownloadEngine.kt`. Status: **Working**.
- **Duplicate File Detection**: Queries JNI helper `detectDuplicate` using content hashes prior to disk writing. Status: **Working**.
- **Download Speed Graph**: Real-time throughput graphing powered by state flows in `DownloadCenterScreen.kt`. Status: **Working**.

### 4.3 Media & Video Features
- **Floating Video / Picture-in-Picture (PiP)**: Invokes native Android activity PiP modes upon full-screen HTML video triggers. Served by `FullscreenVideoManager.kt` and `WebAppInterface.java`. Status: **Working**.
- **Background Video Audio Playback**: Spawns a background `ForegroundService` with notification controls to bind active webpage audio nodes. Served by `SwiftVideoEngine.kt` and `NativeAudioEngine.kt`. Status: **Working**.
- **Gesture Control (Brightness & Volume)**: Horizontal and vertical swipes on active full-screen players intercept web motion events and alter local system levels. Served by `BrowserScreen.kt` and `WebAppInterface.java`. Status: **Working**.
- **Adaptive Stream Muxing**: Combines split video-only and audio-only streams into single MP4 formats using C++ Native Media Engine binders. Status: **Partial**.

### 4.4 AI Features (Gemini API)
- **AI Page Summary**: Extracts cleaned page text payloads and transfers them to Gemini models via backend router endpoints. Served by `AIPageAnalyzer.kt` and `AISummaryEngine.kt`. Status: **Working**.
- **AI Interactive Chat**: Responsive bottom-drawer chat allowing immediate page-context prompting. Served by `AIChatPanel.kt`. Status: **Working**.

### 4.5 Developer Mode Dashboard
- **Live Engine Health Inventory**: Renders interactive status cards showing every registered engine's memory cost, latency, errors, and success outputs. Served by `DeveloperModeScreen.kt`. Status: **Working**.
- **Live Web Permission Tracing**: Displays precise, scrolling transaction-log steps detailing website origin, requested permissions, native system responses, and WebView actions. Served by `PermissionDeveloperScreen.kt`. Status: **Working**.
- **Performance Profilers**: Visual graphs of local CPU consumption, memory footprints, startup time millisecond counters, and three-dot menu open latency. Served by `PerformanceDashboardScreen.kt`. Status: **Working**.

---

## 5. File Inventory

An explicit audit of all source files discovered across the repository, tracking their purpose, architectural boundaries, and dependencies:

### 5.1 App Module (Kotlin & Java Source)
- `/app/src/main/java/com/example/browser/SwiftContracts.kt`
  - **Purpose**: Defines core contracts: `SwiftModule`, `TraceEvent`, `SwiftPermissionTraceModel`, and skeletons for `SwiftBrowserCoreManager`, `SwiftPermissionEngine`, and `SwiftDownloadEngine`.
  - **Dependencies**: Android Log, JVM UUID.
  - **Status**: **Working / Fully Integrated**.
- `/app/src/main/java/com/example/browser/TraceRepository.kt`
  - **Purpose**: Unified interface and memory-backed flow manager for system traces.
  - **Status**: **Working**.
- `/app/src/main/java/com/example/browser/TraceModels.kt`
  - **Purpose**: Data structures for `TraceModel`, `PerformanceTraceModel`, and `PermissionTraceModel` utilized by diagnostics UI components.
  - **Status**: **Working**.
- `/app/src/main/java/com/example/browser/EngineRegistry.kt`
  - **Purpose**: Thread-safe global registration vault for engine state metrics.
  - **Status**: **Working**.
- `/app/src/main/java/com/example/browser/BrowserCoreManager.kt`
  - **Purpose**: Manages lifecycle, WebView state memory optimization, and tab suspension.
  - **Status**: **Working**.
- `/app/src/main/java/com/example/browser/BrowserViewModel.kt`
  - **Purpose**: Exposes reactive state, coordinates trace collection, and calculates global browser health scores.
  - **Status**: **Working**.
- `/app/src/main/java/com/example/browser/BrowserScreen.kt`
  - **Purpose**: Jetpack Compose master entry screen. Ties WebView rendering, PiP transitions, gesture layers, and menu routing.
  - **Status**: **Working**.
- `/app/src/main/java/com/example/browser/DeveloperModeScreen.kt`
  - **Purpose**: Main control panel rendering real-time performance stats, scrolling trace lists, and engine inventory states.
  - **Status**: **Working**.
- `/app/src/main/java/com/example/browser/PermissionCenterScreen.kt`
  - **Purpose**: User-facing permission manager showing granted, denied, and pending websites.
  - **Status**: **Working**.
- `/app/src/main/java/com/example/browser/DownloadCenterScreen.kt`
  - **Purpose**: Renders scanning, candidate lists, progress bars, and historical download tasks.
  - **Status**: **Working**.
- `/app/src/main/java/com/example/browser/MediaCenterScreen.kt`
  - **Purpose**: Handles media candidate discovery, stream selection (video, audio, subtitle tracks), and thumbnail extraction.
  - **Status**: **Working**.
- `/app/src/main/java/com/example/browser/DynamicPermissionEngine.kt`
  - **Purpose**: Implements runtime Android system check triggers and saves cache policies for individual web origins.
  - **Status**: **Working**.
- `/app/src/main/java/com/example/browser/WebAppInterface.java`
  - **Purpose**: Exposed JAVASCRIPT-to-JAVA boundary loaded in target webpages. Exposes media download pipelines, volume/brightness parameters, and telemetry feeds.
  - **Status**: **Working**.

### 5.2 Native C++ Module
- `/app/src/main/cpp/CMakeLists.txt`
  - **Purpose**: Native compilation instructions linking C++ files into `libnative_media_bridge.so`.
  - **Status**: **Working**.
- `/app/src/main/cpp/native-media-bridge.cpp`
  - **Purpose**: JNI entry points connecting Java classes with native parsing, chunk calculations, file validation, and metadata indexing.
  - **Status**: **Working**.
- `/app/src/main/cpp/native_hardware_core.cpp`
  - **Purpose**: Executes performance benchmarking and reads system temperature metrics directly from local system logs.
  - **Status**: **Working**.

### 5.3 Assets & Page-Side Javascript
- `/assets/swift_media_probe.js`
  - **Purpose**: Page-injected scraper that analyzes active DOM schemas to discover media candidates, subtitle tracks, and video properties.
  - **Status**: **Working**.
- `/assets/innertube.js`
  - **Purpose**: Online Video-specific request payload rewriter enabling stream signature decoding and signature validation.
  - **Status**: **Working**.

---

## 6. Runtime Wiring

Swift Browser orchestrates interactions between Javascript page hooks, native system events, custom Kotlin processors, and Native C++ helpers.

### 6.1 Unified Live Permission Request Wiring
```
[Website (Webpage DOM)] 
      │
      ▼ (triggers JS navigator.mediaDevices.getUserMedia)
[WebChromeClientImpl (WebChromeClient.kt)]
      │
      ▼ (intercepted via onPermissionRequest)
[SwiftPermissionEngine (SwiftContracts.kt)]
      │
      ├─► [PermissionSecurityChecker] ──► (Verify Secure HTTPS Origin) ──► Fail? ──► Deny Web
      │
      ├─► [PermissionStore] ──► (Read Cache Allowed / Blocked States) ──► Cached? ─► Apply
      │
      ├─► [AndroidPermissionManager] ──► (Ask Android OS Runtime Dialog if needed)
      │
      └─► [HardwareValidationEngine] ──► (Verify Camera/Mic physically exists via JNI)
      │
      ▼ (All Checks Pass)
[WebView Grant Applied] ──► Trace Emitted (SwiftPermissionTraceModel) ──► Developer Mode Logs
```

### 6.2 High-Throughput Download Pipeline Wiring
```
[User select Candidate (DownloadCenterScreen)]
      │
      ▼ (Triggers download enqueue)
[SwiftDownloadEngine (SwiftContracts.kt)]
      │
      ├─► (Call Native JNI: NativeDownloadBridge.planChunks)
      │         │
      │         ▼ (Calculates chunk offsets in high-speed C++)
      │   [ChunkPlan (ChunkAssembler.cpp)]
      │
      ▼ (Spawns concurrent Kotlin Coroutines mapped to planned byte ranges)
[Segment Downloader (Concurrent HTTP Workers)]
      │
      ▼ (Chunks successfully stored to cache temp files)
[Assemble Call] ──► (Native JNI: NativeDownloadBridge.assembleChunks)
                         │
                         ▼ (Assembles split chunk streams in C++ using memory mapped buffers)
                     [Disk Output (Target Directory)]
                         │
                         ▼ (Native JNI: NativeDownloadBridge.verifyHash)
                     [Success Trace Emitted] ──► Developer Dashboard Status Update
```

---

## 7. Permission System

Swift Browser handles permissions with a zero-trust model across web, Android, hardware, and browser layers.

### Supported Scope Definitions
- `GEOLOCATION` (Android: `ACCESS_FINE_LOCATION`)
- `VIDEO_CAPTURE` (Android: `CAMERA`)
- `AUDIO_CAPTURE` (Android: `RECORD_AUDIO`)
- `NOTIFICATION_SCOPE` (Android: `POST_NOTIFICATIONS`)

### Implementation Specifics
1. **Origin Verification**: When a website invokes a permission, `PermissionSecurityChecker` checks if the origin matches a secure standard (`https://` or `localhost`). Unsecured contexts are rejected immediately (`FAIL_INSECURE_ORIGIN`).
2. **User Gesture Requirement**: Permission prompts are blocked unless the web event loop indicates a recent user gesture. This prevents background "prompt-spamming".
3. **Double-Cache Matching**: Cache results are stored dynamically via `PermissionStore` inside `DynamicPermissionEngine`. If a site is blocked once, it is blocked silently without triggering popups.
4. **Hardware Handshake**: Before requesting `CAMERA` or `RECORD_AUDIO` from the OS, JNI queries standard hardware availability descriptors. This prevents "broken system prompt loops" on devices missing the physical components.

---

## 8. Download System

Swift utilizes a hybrid concurrent scheduler split between Kotlin and C++ Native layers.

### Download Phases & Trace States
1. `PAGE_DETECTED`: Injected webpage script identifies downloadable content candidate.
2. `STREAM_DATA_LOADED`: Cobalt API or scraper resolves clean HTTP payload URL.
3. `ANDROID_BRIDGE_CALLED`: Core JNI boundary is crossed, registering filename and length parameters.
4. `PLANNING_CHUNKS`: C++ creates segment byte boundaries based on size and current thread configurations.
5. `DOWNLOADING`: Active workers pull raw stream payloads from network.
6. `CHUNKS_COMPLETED`: Individual threads close connection, storing temporary index segments.
7. `NATIVE_ASSEMBLING`: C++ reads segments and writes to linear disk spaces sequentially.
8. `VERIFYING_HASH`: Native hashing performs file check before marking completed.
9. `COMPLETED`: Disk handles released, files mapped to user views, trace registered.

---

## 9. Media System

The browser includes a fully integrated Media Capture and Player pipeline.

### Adaptive Discovery Loop
- **Video Detection Engine (`SwiftVideoEngine.kt`)**: Monitors current webpage context via injected `swift_media_probe.js`. If an `HTMLVideoElement` is detected, its sources, resolution attributes, and subtitle nodes are parsed.
- **Picture-in-Picture Engine (`FullscreenVideoManager.kt`)**: Implements standard Android activity canvas layers. When the browser registers that the foreground app is closed while playing video, it draws the customized minimal video view instantly.
- **Background Audio Sync**: Keeps active streams connected through a custom foreground Android service, allowing playback to continue when the screen is locked.

---

## 10. Extension System

Swift exposes support for lightweight extensions (packaged as ZIP configurations containing manifest schemas, options configurations, background scripts, and options pages).

### Extension Security Model
- **Isolated Contexts**: Extension scripts run in isolated script realms. They are unable to manipulate native browser structures directly, except through verified channel message bridges.
- **Permission Manifest Checking**: Extensions must explicitly list host and feature access scopes in their `manifest.json`.
- **Dynamic Policy Controller (`SwiftExtensionPermissionEngine.kt`)**: Enables administrators to review, approve, toggle-on, or permanently revoke an extension's privileges mid-execution.

---

## 11. Developer Mode

The Developer Mode Screen serves as the main monitoring console for developers to inspect browser engines and system operations.

### Metric Layout Architecture
- **Global Health Board**: Displayed at the top of the interface. Integrates real-time stats (Memory MB footprint, active CPU use %, milliseconds since boot, and Menu Latency counters).
- **Engine Status Panel**: Real-time card lists displaying registered engines, execution states (`IDLE`, `INITIALIZING`, `PASS`, `FAIL`), class names, and error traces.
- **Scrolling Log Output Console**: Filters logging trace statements dynamically. Categorizes entries (`SECURITY`, `PERFORMANCE`, `RESOURCE`, `NETWORK`, `UI`, `AUDIO`) and maps visual warning indicators directly beside failures.

---

## 12. Performance and Startup

To combat common WebView application performance problems, Swift uses optimization techniques in its startup and menu loops.

### Delay Root-Causes and Solutions
- **Synchronous WebView Warming**: Historically, creating a WebView blocks the main thread for up to 300ms. 
  - *Solution*: Pre-warmed background instantiations created during splash animation render.
- **Synchronous Permission Databases**: Initializing site databases blocks startup threads.
  - *Solution*: Moved permission lookups to lazy Coroutine contexts.
- **Menu Inflation Lag**: Drawing complex Material 3 settings dialogs causes noticeable UI lag.
  - *Solution*: Defer menu rendering structures, pre-compile layout states, and cache active configurations locally.

---

## 13. Native C++ Acceleration

Native machine code speeds up performance-sensitive parsing, file I/O, and mathematical algorithms.

### JNI Map Specification
The native bridge (`NativeBridge.kt` and `native-media-bridge.cpp`) binds these native entry points:

- `Java_com_example_nativedownloadengine_NativeDownloadEngine_nativeCalculateChunks`: Inputs URL size and thread numbers; outputs split boundaries.
- `Java_com_example_nativedownloadengine_NativeDownloadEngine_nativeAssembleChunks`: Inputs fragment files and final output target; writes streams sequentially.
- `Java_com_example_nativedownloadengine_NativeDownloadEngine_nativeVerifyFileIntegrity`: Inputs path and expected hash; executes checksum verification.
- `Java_com_example_nativemediaengine_NativeMediaEngine_nativeParseMetadata`: Fast JSON metadata metadata parser.

---

## 14. JavaScript Integration

The bridge API (`WebAppInterface.java` and `swift_media_probe.js`) enables secure, high-speed page-side communication.

```javascript
// Exposes custom page extraction functions
window.swiftMediaProbe = {
  getPageType() {
    return location.pathname === "/watch" ? "watch" : "shorts";
  },
  getVideoId() {
    return new URL(location.href).searchParams.get("v") || "";
  },
  getThumbnailCandidates() {
    return [{ url: document.querySelector('meta[property="og:image"]')?.content, label: "OG" }];
  }
};

// Transmits discovered state to Android host
window.swiftBridge = {
  sendTrace(payload) {
     if (window.Android?.recordTrace) {
        window.Android.recordTrace(JSON.stringify(payload));
     }
  }
};
```

---

## 15. UI and Compose Screens

Swift features a clean, responsive Material 3 UI.

### Navigation Hierarchy
- **Primary Browser Screen (`BrowserScreen.kt`)**: Standard address panel, search bars, WebView window viewport, and tab switch overlay.
- **Developer mode screen (`DeveloperModeScreen.kt`)**: Scrollable debug graphs and log feeds.
- **Media Center overlay (`MediaCenterScreen.kt`)**: Displays candidates with detailed resolution tags, stream details, and download buttons.

---

## 16. Settings and Reset Paths

Users can manage settings and reset browser systems from the Settings interface.

- **Dynamic Module Reset**: Every modular engine implements a custom reset path (`resetState()`). If an engine malfunctions, users can reset its state to default.
- **Database Cleansing**: Clear historical records, permissions cache entries, and cookies in one tap.

---

## 17. Recovery and Regression Handling

Swift monitors system stability and handles runtime failures gracefully.

- **Warm Recovery System**: The browser core implements dynamic restarts (`recoverModule`). If an engine encounters an unhandled exception, the core re-initializes it without closing active web sessions.
- **Regression Detection Monitor**: Compares historical execution benchmarks with the current state. If a module's startup time exceeds the historical baseline by 30%, it emits an `UPDATED_BROKEN` warning to the trace log.

---

## 18. Diagnostics and Trace Models

Traces follow a unified format to ensure easy debugging.

```kotlin
data class TraceEvent(
    val id: String,
    val timestamp: Long,
    val engineId: String,
    val category: String,
    val eventName: String,
    val tracePayload: String
)
```

System trace categories include:
- `SECURITY`: Origin verifications and SSL handshakes.
- `PERFORMANCE`: Process startup timings and UI latency benchmarks.
- `RESOURCE`: Web asset downloads and asset loading metrics.
- `NETWORK`: API connectivity state changes.

---

## 19. Known Gaps

This section tracks planned improvements and known limitations:

- **Adaptive Audio-Video Muxing**: Direct C++ integration for stream muxing is under development. Splitting formats remains the default.
- **External Extension Injection**: Currently restricted to static package configurations. Dynamic third-party extension injection is planned.
- **Advanced CSS Isolation**: Dynamic stylesheet overrides on foreign domains can occasionally cause layout shifts.

---

## 20. Upgrade Roadmap

Planned development milestones for Swift Browser:

```
┌────────────────────────────────┐
│            Phase 1             │
│ Core Stability & Core Tracing  ├─► Complete contracts, centralize registry and trace repository
└────────────────────────────────┘
                               ▲
┌──────────────────────────────┴─┐
│            Phase 2             │
│ Security & Permission Controls ├─► Secure origin matching and background prompt suppression
└────────────────────────────────┘
                               ▲
┌──────────────────────────────┴─┐
│            Phase 3             │
│ Native Engine Acceleration     ├─► Implement C++ chunk assembly and metadata parser integration
└────────────────────────────────┘
                               ▲
┌──────────────────────────────┴─┐
│            Phase 4             │
│ Unified Developer Mode Screen  ├─► Build global health board, scrollable logs, and profiling graphs
└────────────────────────────────┘
```

---

## 21. Testing Checklist

Use these test suites to verify system functionality:

- **Robolectric Unit Tests**: Run `:app:testDebugUnitTest` to test permissions, state flows, and engine registration logic.
- **System Integrity Check**: Run `./gradlew compileDebugKotlin` to verify codebase compilation and catch syntax errors.
- **Trace Propagation Verification**: Open Developer Mode and verify that webpage interactions generate corresponding trace entries.

---

## 22. Acceptance Criteria

Milestones are considered complete when they meet these criteria:

1. Codebase compiles successfully via Gradle without warnings or errors.
2. The core lifecycle systems implement the standard `SwiftModule` contract.
3. Every system check, download segment step, and website permission request emits a trace to `TraceRepository`.
4. Page interactions generate live trace logs on the Developer Mode Screen.
5. Critical performance metrics (CPU, Memory, Startup time) display on the Developer Dashboard.

---

## 23. Troubleshooting

Solutions for common development issues:

- **Issue: "Library not found" during native JNI load**
  - *Fix*: Check that CMake compiles `native_media_bridge` and verify that `System.loadLibrary` matches the CMake configuration.
- **Issue: Missing traces on the Developer Mode Screen**
  - *Fix*: Ensure the target engine class calls `TraceRepository.emit()` and verify that the UI observes the trace Flow collection correctly.
- **Issue: JNI crashes due to string conversions**
  - *Fix*: Release string resources in C++ using `ReleaseStringUTFChars` before returning to prevent JNI memory leaks.

---

## 24. Appendix: File Ownership Table

This table maps browser modules to their corresponding codebase files:

| Module / System | File Path | Responsibility | Primary Class / Object |
|---|---|---|---|
| Core Contracts | `/app/.../browser/SwiftContracts.kt` | Core Module, Trace, and Download contracts | `SwiftModule`, `SwiftBrowserCoreManager` |
| Trace Core | `/app/.../browser/TraceRepository.kt` | Trace collection and Flow emissions | `TraceRepository`, `InMemoryTraceRepository` |
| State Models | `/app/.../browser/TraceModels.kt` | Telemetry structures | `TraceModel`, `PermissionTraceModel` |
| Engine Registry | `/app/.../browser/EngineRegistry.kt` | Monitors global engine metrics | `EngineRegistry`, `EngineRegistryImpl` |
| Developer UI | `/app/.../browser/DeveloperModeScreen.kt` | Performance graphs and log monitors | `DeveloperModeScreen` |
| Permissions UI | `/app/.../browser/PermissionCenterScreen.kt`| User settings for site permissions | `PermissionCenterScreen` |
| WebView Core | `/app/.../browser/BrowserScreen.kt` | Compose UI and main WebView layer | `BrowserScreen` |
| Native JNI | `/app/src/main/cpp/native-media-bridge.cpp` | C++ logic and JNI bindings | JNI Exporter Functions |
