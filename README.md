# ApkSlim: APK size reducer for Android

Kotlin, Jetpack Compose and Material 3. ApkSlim makes an APK smaller **without touching its code**, so performance and behaviour stay the same.

## Realistic savings
| APK type | Typical saving |
|---|---|
| Universal APK with native code for 3–4 CPU types | **30–70%** (a real 117.8 MB Termux APK came out at 34.6 MB, 70.6% smaller) |
| Image-heavy app with no native code | 5–25% |
| App installed from Google Play (already split per device) | 0–5% as installable APK, about 20–35% as a `.slim` archive |

APKs are already zip-compressed, so no tool can honestly promise 50–70% on *every* APK. ApkSlim shows an estimate before it runs.

## Output formats
- **Installable**: a normal `.apk` (or `.apks` bundle for split apps such as Instagram), installed straight from ApkSlim.
- **Smallest `.slim` archive**: every file is stored uncompressed inside one solid XZ/LZMA2 stream. App code (DEX) comes out about 33% smaller than the zip compression used inside APKs. A `.slim` can't be installed directly: open it in ApkSlim and tap **Restore & install**.

## What it does
1. **Removes CPU code (native libraries) the device can't use**, for example x86 and armv7 on an arm64 phone. This is the biggest win.
2. **Re-encodes PNG/JPEG as WebP.** Lossless mode checks every pixel and keeps the original if anything differs. Lossy mode lets you pick the quality. 9-patch, 16-bit, ICC-profiled and animated PNGs are skipped.
3. **Recompresses at deflate level 9.** Entries stored uncompressed (resources.arsc, page-aligned .so) stay uncompressed.
4. **Strips build leftovers** (META-INF/*.version, Maven files). Service registrations and licences are kept.
5. **Aligns** (4 bytes, and 16 KB for .so) and **re-signs** with apksig (v2/v3, plus v1 when minSdk is below 24), then verifies the signature.

What it doesn't do: shrink DEX code, guess "unused" resources or languages, or keep Play Store updates working.

## Features
- Pick an APK file, open or share one *into* ApkSlim, or choose an installed app (search, sort by size, system filter)
- Size breakdown donut, ABI list and largest files
- Presets (Safe, Balanced, Maximum) plus full custom options, with a live savings estimate
- Progress screen with steps and cancel
- Result screen: before/after per category, signature check, Install, Share, Save
- History with total saved, reinstall, share and delete
- Signing: a generated ApkSlim key (exportable .p12) or your own imported .p12/.bks keystore
- Light and dark theme, splash screen, adaptive icon

## Important: signing
A re-signed APK **cannot update an installed app signed with a different key**. You must uninstall the original first (its data is lost), or import that app's original keystore. Re-signed apps don't get Play Store updates.

## Build
Requirements: Android Studio Ladybug or newer (or JDK 17 + Android SDK 35).

```
./gradlew assembleRelease          # app/build/outputs/apk/release/app-release.apk
./gradlew :core:test               # engine tests
```
Or open the folder in Android Studio and press Run.

No PC? Push this folder to a GitHub repo. The included workflow (`.github/workflows/build.yml`) builds the APK and attaches it as a downloadable artifact under the Actions tab.

The release build is signed with the debug key so it installs right away. Add your own signingConfig before publishing.

## Project layout
- `core/` is the pure Kotlin engine: analyzer, optimizer, aligner, signer, self-signed certificate. It's unit-tested.
- `app/` is the Android UI (Compose) plus the platform image codec, key management and installer.

## Security and reliability

ApkSlim runs fully offline: it has **no internet permission**, no account, no server and no database, so there are no API keys, passwords or user data to leak. What applies to an offline app is covered:

- **Input checks**: every opened file is identified by its first bytes (not its name), capped at 4 GB, and refused if the phone lacks space. `.slim` and `.apks` contents are validated (counts, sizes, entry names), so a damaged or hostile file cannot write outside the app's own folder.
- **Central error handling**: one place turns failures into clear messages (out of storage, damaged file, out of memory).
- **Crash reports**: if the app closes unexpectedly, a report is saved on the phone and the next launch offers to share it. Nothing is sent automatically.
- **Tests**: unit tests cover optimizing, signing, archive round trips and hostile inputs.
- **Staging before release**: CI installs the exact APK on an Android 14 emulator and opens every tab; the download link is updated only if that passes.
- **Dependencies**: Dependabot checks libraries and CI actions weekly.
- **Signing keys stay out of the code**: release builds are signed with a private key from GitHub Actions secrets (`RELEASE_KEYSTORE_B64`, `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`). Without them CI falls back to a throwaway debug key that changes every build, so each update needs an uninstall first.
