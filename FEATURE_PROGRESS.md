# FEATURE_PROGRESS

Resume point after `/clear`: read `CLAUDE.md`, this file, then the relevant `SPEC.md` section.
Status legend: [ ] todo · [~] implemented, `./gradlew check` and unit tests pass, **device verification pending** · [x] verified on a device.
Nothing has been run on a Fold 6 or Shield yet.
Voted scope: all features except **4d**. Build order: mobile first, then TV. Sideload only, minSdk 30, targetSdk 35. Shizuku optional.

## Stages
| # | Stage | Contents | Status |
|---|---|---|---|
| 0 | Repo guardrails | CLAUDE.md, settings, rules, SPEC, this file | [x] |
| 1 | Project scaffold | Gradle (Kotlin DSL, version catalog), `app` module, theme, design tokens, navigation shell, Hilt (chosen), Room, DataStore deps | [x] `assembleDebug`, `test`, `check` pass |
| 2 | Core services | whitelist (6) + `WhitelistGuard`, capability tiers, `OptimizerBackend` + StatsBackend | [~] |
| 3 | Notifications | channels, PendingIntent builders, receivers, rate limit, alert thresholds | [~] |
| 4 | Optimizer | Shizuku backend, Accessibility backend, Safe Boost policy, loop guard | [~] |
| 5 | Monitor | WorkManager scan, foreground monitor, thermal listener, Fold 6 rules | [~] |
| 6 | Tweak scanner | read, recommend, apply, revert | [~] |
| 7 | Security | heuristics, hash matching, DB update | [~] |
| 8 | Self-update | launch check, Settings button, download, verify, install | [~] |
| 9 | Mobile UI | Home gauge, Optimize, Security, Settings, adaptive fold layouts | [~] |
| 10 | TV UI | drawer, Home, Clean, Apps, Security, Settings, focus polish | [~] |
| 11 | Hardening | tests, release signing, Fold 6 and Shield device passes | [ ] |

## Backlog and verification
| ID | Feature | Stage | Verification | Status |
|---|---|---|---|---|
| 1a | Shade cards: Boost, Clear Cache, Cool Down, Whitelist | 3 | instrumented: each action fires receiver and updates notification; whitelisted target no-ops | [~] |
| 1b | Heads-up thermal/RAM alerts, rate limited | 3 | unit: threshold + 30 min dedupe; manual: forced thermal state | [~] |
| 1c | Snooze / never-alert-for-app | 3 | unit: snooze expiry stored and honored | [~] |
| 1d | Quiet hours and DND suppression | 3 | unit: non-critical suppressed, critical not | [~] |
| 2a | Shizuku backend | 4 | device (T2): cache cleared, no `pm clear` call anywhere (grep test) | [~] |
| 2b | Accessibility fallback | 4 | device: foreground-only, aborts on touch, not callable from receiver/worker | [~] |
| 2c | Safe Boost policy and cooldown | 4 | unit: policy matrix of all 6 exclusions; loop guard after 3 restarts | [~] |
| 2d | Hidden-cache finder | 4 | unit on a fake file tree; device on T2 | [~] |
| 3a | WorkManager periodic scan | 5 | unit: constraints set; boot reschedule | [~] |
| 3b | Fold 6 display/charging awareness | 5 | unit: interval rules; device: cover vs inner | [~] |
| 3c | Shield thermal listener | 5 | unit: hysteresis; device: Shield | [~] |
| 3d | Sustained performance (in-app toggle only) | 5 | device: toggle works only while FoldOpti is foreground | [~] |
| 3e | Battery-drain attribution | 5 | unit: shares sum to ~100% | [~] |
| 4a | Developer options read-only scan | 6 | unit: parsing and recommendation table | [~] |
| 4b | 1-tap apply with revert | 6 | device: values change and revert | [~] |
| 4c | RAM Plus recommendation (deep link) | 6 | device (Samsung): link opens | [~] |
| 4d | Wi-Fi throttle toggle | n/a | excluded by vote | excluded |
| 5a | Dormancy tiers + disable (14/30/60 d) | 5/10 | unit: tiers; device: disable and re-enable | [~] |
| 5b | Separate uninstall action | 10 | device: confirm dialog, system uninstall | [~] |
| 5c | Shield protected package list | 10 | unit: all protected packages refused | [~] |
| 5d | Background-bloat view | 10 | device: matches `dumpsys` | [~] |
| 5e | Dormant-app view on mobile | 9 | UI test | [~] |
| 6a | Lock list with auto-seeded defaults | 2 | unit: seeding; `WhitelistGuard` for every action | [~] |
| 6b | Suggested apps (Plex, Kodi, messaging) | 2 | unit | [~] |
| 6c | JSON import/export | 2 | unit: round trip | [~] |
| 7a | Permission-heuristic scoring | 7 | unit: fixtures per rule and band | [~] |
| 7b | Installer-source and signature checks | 7 | unit with fake `PackageInfo` | [~] |
| 7c | Local SHA-256 hash matching | 7 | unit: known hash hit/miss; no network | [~] |
| 7d | Optional signature DB download | 7 | unit: signature verification rejects tampering | [~] |
| 7e | Flag only, never auto-delete | 7 | unit: no delete/disable calls from scanner | [~] |
| U1 | Auto-check for updates on app launch | 8 | unit: 6 h throttle, semver compare; device: launch triggers check | [~] |
| U2 | Settings "Check for updates" button | 8 | UI test: shows current, latest, last checked | [~] |
| U3 | Verified download and install | 8 | unit: hash mismatch rejected; device: update from vN to vN+1 (silent on T2) | [~] |
| D1 | UI follows unslop-based design system | 9/10 | review against the checklist in `android-design-system.md`; no gradients, emoji, uppercase labels | [~] |

## Release state
v1.0.0 code complete on branch `ccr-4c4a9111-lmn2f5`. 21 unit tests, lint and R8 release build pass. Creating GitHub Releases is blocked in the cloud session, so a release APK must be built and published locally or by CI.

## 1.0.1 fixes (from first device report)
- Smart Clean and Optimize gave no progress or result, and the app crashed after Smart Clean. Added live progress (`OptimizerEngine.progress`), a result summary with the first shell error, 4-way parallel cache clearing, 20 s shell timeout, error capture in every ViewModel action, and Settings > Diagnostics (last crash and errors, copy button). Crash root cause not yet identified: reproduce and paste Diagnostics.
- POST_NOTIFICATIONS was never requested on Android 13+, so no alert ever showed. Now requested at launch.

## Open items
- Device pass (Stage 11): every [~] row above. Hidden-behavior risks: `pm clear --cache-only` (Android 12+ only, so unavailable on Shield Android 11), `dumpsys` parsers on Samsung/Shield builds, accessibility labels (English only), Samsung Device care package name, Shizuku `newProcess` reflection.
- `assets/publishers.json` is empty (cert-mismatch rule inactive) and `signatures.bin` holds only the EICAR test hash. A real signature feed and key (`SIGNATURE_PUBKEY_B64`) are needed for 7c/7d to be useful.
- Verify hidden-API paths on a real Fold 6 and Shield: `deleteApplicationCacheFiles`, process limit, standby bucket commands.
- Bundle Geist / IBM Plex Sans + Mono font files (system fonts used for now). Replace placeholder launcher icon and TV banner.
- Build note: Maven Central rate-limits parallel Gradle in this sandbox; use `--max-workers=1 -Dorg.gradle.parallel=false`.
- Create release signing key outside the repo; publish first GitHub Release so U1–U3 can be tested.
- Keep unslop CC BY 4.0 credit in the in-app licenses screen.
