# FoldOpti (`com.fitzy.foldopti`)

Dual-device optimizer: Galaxy Z Fold 6 (mobile) + NVIDIA Shield TV (Android 11). Sideload only.
minSdk 30, targetSdk 35, Kotlin, Jetpack Compose, Compose TV 1.0.0 (`androidx.tv.material3`).
Full design: `SPEC.md`. Backlog and status: `FEATURE_PROGRESS.md` (read it first after any `/clear`).

## Commands
- Debug build: `./gradlew assembleDebug`
- Unit tests: `./gradlew test`
- Verify: `./gradlew check` (also runs from the Stop hook when an SDK is present)

## Layout (single `app` module, package-per-engine)
- `ui/mobile/`  Fold 6 UI (adaptive, dual-pane when unfolded)
- `ui/tv/`      Shield 10-foot UI (D-pad, focus)
- `core/notifications/` channels, PendingIntent builders, action receivers
- `core/optimizer/`     cache clear, safe boost, tweak scanner, backends (Shizuku / Accessibility / StorageStats)
- `core/security/`      malware + permission heuristics, hash matching
- `core/scheduler/`     WorkManager scans, thermal listener, foreground monitor
- `core/update/`        GitHub Releases self-update (launch check + Settings "Check for updates")
- `core/whitelist/`     protected-app store (Room), shared by every engine

## Gotchas
- Declare every notification receiver in `AndroidManifest.xml` with `exported="false"`; PendingIntents are explicit and `FLAG_IMMUTABLE`.
- Receivers: use `goAsync()` only for work under ~10s, otherwise hand off to expedited WorkManager.
- Never hold system-service binders or Shizuku binders across config changes; fetch per call.
- No Shizuku and no Accessibility is a valid state: degrade to read-only/measure features and show a setup prompt.
- Never `pm clear` (wipes user data). Cache clear means cache only.
- Never kill foreground services, media sessions, default launcher/IME/dialer/SMS, or whitelisted apps.
- Prefer standby-bucket / background-restrict over `force-stop`; enforce a per-app cooldown to avoid restart loops.
- Collect StateFlows with `collectAsStateWithLifecycle()` / `repeatOnLifecycle(STARTED)`.
- Foreground service type is `specialUse` (targetSdk 35); declare the justification property.
- The only network use is `core/update` (api.github.com). The security scanner never uploads anything.
- Shield: keep a protected package list (launcher, voice search, GameStream, screensaver).

## UI rules
Path-scoped files in `.claude/rules/`: `android-mobile-ui.md`, `android-tv-ui.md`, `android-design-system.md`
(design system adapted from heydamir/unslop, CC BY 4.0).

## Token hygiene
- Keep this file under 200 lines.
- Offload repo exploration to subagents; ask for short technical output.
- Update `FEATURE_PROGRESS.md` after each stage; `/clear` at about 80% context.
