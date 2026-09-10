# Bubble Blast — build & release

Everything below is what you run on your own machine. The project ships a Gradle
wrapper, so no Gradle installation is required.

## 1. Requirements

| Tool | Version |
| --- | --- |
| JDK | 17 (Android Studio's bundled JBR is fine) |
| Android SDK | Platform 35 + Build-Tools 35.0.0 |
| Gradle | not needed — use `./gradlew` |
| Android Studio | Ladybug (2024.2) or newer recommended |

Set `ANDROID_HOME` (or `sdk.dir` in `local.properties`) to your SDK location.

## 2. Open the project

**Android Studio**

1. *File ▸ Open…* and select the folder that contains `settings.gradle.kts`
   (the same folder as this file's parent).
2. Let Gradle sync. The first sync downloads AGP 8.7.3, Kotlin 2.0.21 and the
   AndroidX libraries.
3. Press ▶ to run on a device or emulator (API 24+).

**Command line**

```bash
./gradlew assembleDebug          # debug APK
./gradlew installDebug           # build + install on a connected device
./gradlew test                   # pure-Kotlin engine tests
./gradlew lint                   # Android lint report
```

Result: `app/build/outputs/apk/debug/app-debug.apk`

## 3. Release builds

### 3.1 Create a signing key (once)

```bash
keytool -genkeypair -v \
  -keystore release-keystore.jks \
  -alias bubbleblast \
  -keyalg RSA -keysize 2048 -validity 10950 \
  -storetype JKS
```

Keep `release-keystore.jks` somewhere safe and **outside the repository** — it is
the only way to ship updates to the same listing.

### 3.2 Point the build at it

Create `keystore.properties` in the repository root (already git-ignored):

```properties
storeFile=/absolute/path/to/release-keystore.jks
storePassword=your-store-password
keyAlias=bubbleblast
keyPassword=your-key-password
```

If this file is absent, `assembleRelease` still works: it falls back to the debug
key and produces an installable (but not publishable) APK.

### 3.3 Build the artefacts

```bash
./gradlew clean

# Google Play upload bundle (recommended)
./gradlew bundleRelease
# -> app/build/outputs/bundle/release/app-release.aab

# Universal APK, for direct installs / other stores / sideloading
./gradlew assembleRelease
# -> app/build/outputs/apk/release/app-release.apk
```

Both release tasks run R8 (minify) and resource shrinking; keep rules are in
`app/proguard-rules.pro`. The game keeps no reflection-based dependencies, so the
default rules are enough — the file only protects against R8 stripping the
`BubbleBlastApp` entry point and the custom views inflated from XML.

> If a future release build crashes on a device, temporarily set
> `isMinifyEnabled = false` in `app/build.gradle.kts` to confirm R8 is the cause,
> then file the missing keep rule.

### 3.4 Verify before uploading

```bash
# sanity-check the produced APK
$ANDROID_HOME/build-tools/35.0.0/aapt2 dump badging \
  app/build/outputs/apk/release/app-release.apk | head

# install it on a device and play a level end to end
adb install -r app/build/outputs/apk/release/app-release.apk
```

### 3.5 Play Console checklist

* Version: bump `versionCode` / `versionName` in `app/build.gradle.kts` for every
  upload.
* Content rating: no ads, no user-generated content, no data collection.
* Data safety: declare "no data collected" — the game is fully offline and stores
  everything locally.
* Store listing assets: use `store/icon_512.png` (512×512) and
  `store/feature_graphic.png` (1024×500).
* Privacy policy URL: the bundled text lives in
  `app/src/main/assets/legal/privacy_policy.html`; host the same text publicly
  and paste that URL into the listing.

## 4. Build configuration reference

| Setting | Value |
| --- | --- |
| `applicationId` / `namespace` | `com.infinitehits.bubbleblast` |
| `minSdk` / `targetSdk` / `compileSdk` | 24 / 35 / 35 |
| Kotlin / AGP / Gradle | 2.0.21 / 8.7.3 / 8.9 |
| Java compatibility | 17 |
| BuildConfig flags | `AD_PROVIDER="none"`, `ADS_ENABLED=false` |

Release builds are ~2 MB, since all art is vector/baked-at-runtime and the audio
ships as OGG.

## 5. Troubleshooting

| Symptom | Fix |
| --- | --- |
| `SDK location not found` | Set `ANDROID_HOME` or create `local.properties` with `sdk.dir=…`. |
| `Unsupported class file major version` | You are on JDK 11 or older — switch the Gradle JDK to 17 in *Settings ▸ Build Tools ▸ Gradle*. |
| Gradle sync cannot reach `dl.google.com` | A proxy/firewall is blocking the Android repositories; the dependencies are all on Google Maven and Maven Central. |
| Emulator too slow / no device | Any API 24+ device works; the game targets 60 FPS on low-end phones. |
| `assembleRelease` produces an unsigned APK | `keystore.properties` is missing — see §3.2. |
