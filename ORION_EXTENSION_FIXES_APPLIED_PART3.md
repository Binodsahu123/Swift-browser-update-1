# Orion Extension Fixes Applied - Part 3

Date: 2026-08-31

## Applied source fixes

1. Content-script activation now respects the canonical ExtensionRegistry state. Disabled/unregistered extensions are excluded from native content-script registration and manual matching.
2. Native content-script handlers can now be refreshed when the enabled extension set changes.
3. Bound WebViews are tracked weakly by ExtensionEngineImpl so destroyed WebViews are not retained by the extension layer.
4. WebViews register persistent extension content scripts during extension bridge setup, before the next navigation.
5. Installed extensions are restored with their persisted enabled/disabled state instead of always being registered as enabled.
6. Manifest content scripts are registered into the content-script registry when an enabled extension is loaded/installed/enabled, so document-start registration can exist before navigation.
7. Extension install, enable/disable and uninstall now refresh/remove native script handlers.
8. Disabling/uninstalling an extension removes its content-script definitions from the active registry.
9. The previous fixed 2-second extension bootstrap delay was removed; extension state initialization remains off the main thread while allowing earlier registration.
10. On WebView versions exposing JS_INJECTION_IN_FRAME_AND_WORLD, document_start now uses addJavaScriptOnEvent with an explicit PAGE or isolated execution world.
11. Older WebView versions continue to use addDocumentStartJavaScript as a documented fallback without pretending to provide an execution-world selection.
12. Content-script origin-rule conversion was corrected for Chrome-style *:// host patterns so they are represented by explicit http and https rules instead of incorrectly widening to the global '*' rule.

## Verification performed in this environment

- Bracket/syntax-balance sanity check for modified Kotlin files: PASS.
- Duplicate engine class scan inside extension-engine: no duplicate TabEngine/DownloadEngine/PermissionEngine/CookieEngine/StorageEngine/HistoryEngine/BookmarkEngine class declarations found.
- Android Gradle build: NOT RUN; the supplied project archive does not include a Gradle wrapper and this environment does not expose a Gradle executable.
- Device/emulator runtime extension tests: NOT RUN.

## Important limitation

These changes improve the WebView-based extension runtime and lifecycle, but they do not and cannot guarantee 100% compatibility with every Chrome extension. Android WebView does not expose the full Chrome/Chromium extension subsystem. Full validation still requires Android device/emulator testing with real MV2/MV3 extensions.
