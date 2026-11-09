# FoldOpti

Optimizer for the Samsung Galaxy Z Fold 6 and NVIDIA Shield TV. Package `com.fitzy.foldopti`, minSdk 30, targetSdk 35. Sideload only.

- **Interactive notifications** with Boost, Clear cache, Cool down, Protect app, Snooze and Never alert buttons
- **Smart Clean and Safe Boost**: cache clearing and background restriction through Shizuku, with an optional accessibility fallback. No force-stop loops, no `pm clear`
- **Monitoring**: periodic scan (WorkManager), optional live monitor, thermal listener with hysteresis, Fold 6 cover-screen and charging rules
- **Developer tweaks** scanner with apply and revert
- **Unused app manager** (14, 30 and 60 days) with reversible disable, built for Shield
- **Protected apps** list honored by every engine
- **Malware and permission scanner**, fully on-device, flags only
- **Self-update** from GitHub Releases: check on launch, "Check for updates" in Settings, SHA-256 and signing-certificate verification

Without Shizuku the app still measures, scans and alerts (limited mode).

## Build

```
./gradlew assembleDebug     # debug APK
./gradlew test              # unit tests
./gradlew check             # unit tests + lint
```

Needs JDK 17+ and the Android SDK 35 (`local.properties` with `sdk.dir`). If Gradle fails with HTTP 429 from a proxy, add `--max-workers=1 -Dorg.gradle.parallel=false`.

## Releases and updates

The updater reads the latest non-prerelease GitHub Release. Attach the signed APK and a `<name>.apk.sha256` file. Releases must be signed with the same key as the installed app.

## Docs

`SPEC.md` design, `FEATURE_PROGRESS.md` backlog and verification status, `CLAUDE.md` working rules.

Design system adapted from [unslop](https://github.com/heydamir/unslop) by Damir Sokolovsky, CC BY 4.0.
