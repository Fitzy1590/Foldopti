# FoldOpti Technical Specification

Package `com.fitzy.foldopti` · minSdk 30 · targetSdk 35 · sideload only · mobile built first, then TV.
Items marked [verify] depend on hidden APIs or OEM behavior and must be tested on a real Fold 6 / Shield before release.

## 0. Capability tiers
| Tier | Condition | What works |
|---|---|---|
| T0 | no Shizuku, no Accessibility | measure cache (StorageStats), scan, alerts, tweak read-only, malware scan, whitelist, updates |
| T1 | Accessibility enabled (opt-in) | foreground-only per-app cache clear and force-stop via Settings UI automation |
| T2 | Shizuku running + permission granted | silent cache clear, standby bucket changes, force-stop, `WRITE_SECURE_SETTINGS` tweaks, disable/uninstall, silent self-update |
The UI always shows the current tier and the one action that raises it.

## 1. Notifications (`core/notifications/`)
**Channels** (created on first launch): `alerts_critical` (IMPORTANCE_HIGH: thermal severe+, runaway process), `alerts_info` (DEFAULT: RAM, junk cache), `monitor` (LOW: persistent monitor), `updates` (LOW), `scan_results` (DEFAULT).
Request `POST_NOTIFICATIONS` on first alert-worthy event, not at install.

**Alert thresholds** (defaults, editable in Settings; all gated by the 1b rate limit below)
| Alert | Trigger | Actions |
|---|---|---|
| High RAM | available RAM below 15% for two consecutive scans | Boost · Whitelist app |
| Junk cache | measured cache at least 1 GB | Clear cache · Whitelist app |
| Overheating | thermal status MODERATE: info heads-up; SEVERE or worse: critical heads-up | Cool down · Snooze |
| Runaway process | one non-foreground app above 25% CPU for 3 scans | Restrict app · Whitelist · Never alert for this app |
| Battery drain | one app above 20% of drain over 3 h | Restrict app · Whitelist |
**Rate limit**: one alert per type per 30 min; identical content updates the existing notification (stable ID per type); quiet hours (1d) and DND suppress non-critical alerts; snooze (1c) stores a `until` timestamp per type/app in DataStore.

**PendingIntent payload builder** (`ActionIntents.kt`): `PendingIntent.getBroadcast(ctx, requestCode, intent, FLAG_IMMUTABLE or FLAG_UPDATE_CURRENT)`. Intent is explicit (`setClass(ctx, ActionReceiver::class.java)`), action constants `com.fitzy.foldopti.action.{BOOST,CLEAR_CACHE,COOL_DOWN,WHITELIST,SNOOZE,NEVER_ALERT}`, extras `pkg`, `alertId`, `schemaVersion`. requestCode = hash(action, pkg) so each button stays distinct.
**Receiver**: `ActionReceiver` (`exported=false`, declared in manifest). Validates action against an allowlist, loads whitelist, then `goAsync()` for work under ~10 s; otherwise enqueues expedited `OneTimeWorkRequest` (`OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST`). Updates the notification with the result ("Cleared 840 MB") and auto-dismisses after 5 s. Whitelisted targets: the action no-ops and says so.

## 2. Cache and process engine (`core/optimizer/`)
Interface `OptimizerBackend { measure(); clearCache(pkg); restrict(pkg); stop(pkg); capability }` with implementations `StatsBackend` (T0), `AccessibilityBackend` (T1), `ShizukuBackend` (T2). `BackendResolver` picks the highest available per call; no binders retained.

**Measure**: `StorageStatsManager.queryStatsForPackage` per installed package (`PACKAGE_USAGE_STATS` special access required). Group into default, hidden (non-standard dirs under `Android/data/<pkg>`, only with T2 file access) and WebView caches (2d).
**Clear (T2)**: Shizuku `ShizukuBinderWrapper` over `IPackageManager.deleteApplicationCacheFiles` per package [verify per OS version]; fallback `pm trim-caches <bytes>` for a global trim. **Never run `pm clear`.**
**Clear (T1)**: `AccessibilityService` navigates App info → Storage → Clear cache. Foreground only (needs the screen), user-initiated only, never from notifications or WorkManager. Cancel on any user touch.
**Safe Boost policy** (`BoostPolicy`), evaluated for each candidate in order; first match excludes it:
1. whitelisted (user list, seeded defaults) 2. default launcher / IME / dialer / SMS / assistant 3. has a foreground service or active media session or active notification with `FLAG_ONGOING_EVENT` 4. system uid and not marked optional 5. stopped within the last 6 h (cooldown) 6. foreground app.
Remaining candidates: prefer **`am set-standby-bucket <pkg> restricted`** and background-restrict (`cmd appops set <pkg> RUN_ANY_IN_BACKGROUND ignore`) over `force-stop`, because they do not trigger restart loops. `force-stop` is used only for runaway processes (alert action) and logged with a 6 h cooldown. A loop guard disables Boost for an app that was restarted by the system 3 times in 24 h and surfaces it in the UI.

## 3. Monitoring, thermal and battery (`core/scheduler/`)
- **Periodic scan** (3a): `PeriodicWorkRequest` every 30 min (default; 15 min–6 h options), constraints `setRequiresBatteryNotLow(true)`, skip when `PowerManager` thermal status is SEVERE or worse. Unique work name `scan`, `ExistingPeriodicWorkPolicy.UPDATE`. Reschedule on boot (`RECEIVE_BOOT_COMPLETED`).
- **Live monitor**: optional foreground service, type `specialUse` (property `special_use_reason`: "On-device device health monitoring"), persistent low-importance notification, started only by user toggle, stops on screen-off after 10 min.
- **Fold 6 rules** (3b): WindowManager `WindowInfoTracker` gives display state (cover vs inner). While on the cover screen and unplugged, scan interval is doubled; while charging at ≥ 80%, heavy scans (full cache measure, malware scan) are scheduled. Doze: no exact alarms; rely on WorkManager and `JobScheduler` windows. Deep link to battery optimization settings if the app is restricted.
- **Shield thermal** (3c): `PowerManager.addThermalStatusListener` registered by the monitor service. MODERATE → info heads-up; SEVERE → critical heads-up + "Cool down" (stop whitelisted-exempt background candidates, pause scans); CRITICAL or worse → persistent alert until status < MODERATE. `getThermalHeadroom(30)` sampled every minute while MODERATE+ to show a trend on the TV UI. Hysteresis: need 2 min below a level before downgrading.
- **Sustained Performance** (3d): `Window.setSustainedPerformanceMode(true)` is per-window and only affects FoldOpti's own activity. Provide it only as an in-app toggle labelled as such (see FEATURE_PROGRESS notes). Check `PowerManager.isSustainedPerformanceModeSupported()`.
- **Battery drain attribution** (3e): `UsageStatsManager` + `BatteryManager` deltas; no per-app mAh claims unless reported by the OS, show "share of active time" instead.

## 4. Developer options tweak scanner (`core/optimizer/tweaks/`)
| Setting | Key / source | Recommended | Apply |
|---|---|---|---|
| Window animation scale | `Settings.Global.WINDOW_ANIMATION_SCALE` | 0.5 | `Settings.Global.putFloat` (T2 or granted `WRITE_SECURE_SETTINGS`) |
| Transition animation scale | `TRANSITION_ANIMATION_SCALE` | 0.5 | same |
| Animator duration scale | `ANIMATOR_DURATION_SCALE` | 0.5 | same |
| Background process limit | ActivityManager process limit [verify] | standard limit | T2 only, otherwise deep link to Developer options |
| Stay awake | `STAY_ON_WHILE_PLUGGED_IN` | 0 on TV idle, flag only | read-only recommendation |
| App standby | `app_standby_enabled` | 1 | read-only |
| Doze state | `dumpsys deviceidle` (T2) | informational | read-only |
| RAM Plus (4c) | Samsung Device care | recommendation text + deep link | no automatic change |
Wi-Fi throttle toggle (4d) is excluded. Every apply stores the previous value to allow one-tap revert.
Grant `WRITE_SECURE_SETTINGS` once via Shizuku (`pm grant`) when the user taps "Enable 1-tap apply".

## 5. TV bloat and inactive apps (`ui/tv/`, `core/optimizer/dormancy/`)
- `UsageStatsManager.queryEvents`/`queryAndAggregateUsageStats` → last foreground time per package; tiers 14/30/60 days (5a). Never-launched apps use `PackageInfo.firstInstallTime`.
- Actions: **Disable** = `pm disable-user --user 0 <pkg>` (T2, default, reversible, with a "Disabled apps" screen to re-enable). **Uninstall** (5b) is a separate confirmed action via `PackageInstaller.uninstall`; user-uninstall only for non-system apps.
- Built-in protected list (5c), always excluded: `com.google.android.tvlauncher`, the current default launcher, `com.google.android.katniss` (voice search), `com.nvidia.shieldtech.*`/GameStream components, `com.google.android.backdrop` (screensaver), `com.google.android.tv.*` core, `com.android.vending`, `com.plexapp.*` / `org.xbmc.kodi` suggested for whitelist.
- Bloat view (5d): services running, standby bucket, last used, background activity; no live kill list.
- Mobile reuse (5e): same engine, shown as "Unused apps" in the mobile Optimize tab.

## 6. Whitelist (`core/whitelist/`)
Room table `protected_app(pkg PK, reason, addedAt, source{USER,DEFAULT,SUGGESTED})`. Seed on first launch: default launcher, IME, dialer, SMS, FoldOpti, Shizuku, and on TV the 5c list. Suggestions (6b): installed Plex, Kodi, WhatsApp, Signal, Telegram, Messages. Export/import (6c) as JSON via the Storage Access Framework. **Every engine calls `WhitelistGuard.assertAllowed(pkg, Action)` before acting; engines must not read the table directly.** Unit-tested for each action type.

## 7. Malware and permission scanner (`core/security/`)
Local only; no APK or package data leaves the device.
**Inputs**: `PackageManager.getInstalledPackages(GET_PERMISSIONS or GET_SIGNING_CERTIFICATES)` (`QUERY_ALL_PACKAGES` is acceptable: sideload only), `getInstallSourceInfo` (API 30+), APK file hash.
**Heuristic score** (0–100, each rule has an id and an explanation shown to the user):
| Rule | Points |
|---|---|
| Accessibility service + overlay (`SYSTEM_ALERT_WINDOW`) | 35 |
| Accessibility + SMS read/send | 40 |
| Device admin active on a non-MDM app | 30 |
| `REQUEST_INSTALL_PACKAGES` + sideloaded + not from a known store | 15 |
| Sideloaded and requests ≥ 3 dangerous permission groups unrelated to category | 15 |
| Signed with a debug certificate | 20 |
| Certificate mismatch against a known-publisher list (7b) | 40 |
| Outdated app: targetSdk ≤ 27 or not updated in 2+ years with dangerous permissions | 10 |
Bands: 0–29 clean, 30–59 review, 60+ suspicious.
**Hash matching (7c)**: SHA-256 of `sourceDir` base APK compared against a bundled sorted hash list in `assets/signatures.bin` (binary search / Bloom filter). **Optional database update (7d)**: user-triggered download of a signed list from GitHub Releases, verified with a public key baked into the app; no uploads.
**Policy (7e)**: results are flagged only. The scanner never uninstalls or disables anything; it offers the system uninstall screen for the user to confirm.

## 8. Self-update (`core/update/`)
Source: GitHub Releases of `fitzy1590/foldopti` (`GET api.github.com/repos/fitzy1590/foldopti/releases/latest`, the only network call in the app besides 7d).
- **Auto-check on launch**: when the app opens, if the last check was more than 6 h ago, fetch the latest release in the background and compare `tag_name` (semver) with `BuildConfig.VERSION_NAME`. A periodic `WorkManager` job (24 h, unmetered network, battery-not-low) also checks. Toggle in Settings: "Check for updates automatically" (default on).
- **Settings → "Check for updates"**: manual check, shows current version, latest version, release notes, and last checked time.
- **Download and verify**: download the `.apk` asset to `cacheDir` over HTTPS; verify SHA-256 against the `.sha256` release asset; verify the downloaded APK's signing certificate matches the installed app's before installing.
- **Install** via `PackageInstaller` session. T2: silent install through Shizuku (`pm install`) and the app restarts after the update. Otherwise: the system confirmation prompt (one tap). Requires `REQUEST_INSTALL_PACKAGES`; Android 12+ `setRequireUserAction(USER_ACTION_NOT_REQUIRED)` may skip the prompt for updates when allowed by the OS [verify].
- "Auto update" therefore means: automatic check and download; installation is unattended only on T2 and otherwise needs one confirmation. Never auto-install a release flagged `prerelease`. Release signing keystore stays out of the repo (see `.claude/settings.json` deny list).

## 9. UI contracts
**Fold 6** (mobile): bottom navigation (Home, Optimize, Security, Settings) when folded/compact; navigation rail plus `ListDetailPaneScaffold` when unfolded. Home: thermal/health gauge (the one rich element), a Smart Clean button, device state row (live dot). Tabletop posture: gauge above hinge, actions below. State survives fold/unfold via ViewModel + `rememberSaveable`.
**Shield** (TV): left `NavigationDrawer` (Home, Clean, Apps, Security, Settings), content rows with `focusRestorer()`. Home shows thermal state, dormant-app count by tier, a single "Clean now" button with initial focus. Focus visuals ≥ 1.03× scale or ≥ 2dp border. Destructive actions use a confirm dialog with focus on Cancel. D-pad only.
**Design system**: see `.claude/rules/android-design-system.md` (adapted from heydamir/unslop, CC BY 4.0).
**Settings screen** (both UIs): backend tier and setup, alert thresholds, quiet hours, scan interval, whitelist, updates (auto-check toggle, "Check for updates", version, last checked), about and licenses (including unslop credit).

## 10. Permissions summary
`POST_NOTIFICATIONS`, `PACKAGE_USAGE_STATS` (special), `QUERY_ALL_PACKAGES`, `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SPECIAL_USE`, `RECEIVE_BOOT_COMPLETED`, `REQUEST_INSTALL_PACKAGES`, `INTERNET` (updates only), `WRITE_SECURE_SETTINGS` (granted via Shizuku, optional), Shizuku API permission, Accessibility service (opt-in).
