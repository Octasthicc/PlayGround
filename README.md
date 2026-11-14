# PlayGround

<!-- omgithub:readme:start -->
## 🚀 Build, play, and remix with OMGithub

**Created using [OMGithub.com](https://go.ondev.store/).**

[![OMGithub](https://img.shields.io/badge/OMGithub-Open%20project-orange?style=for-the-badge)](https://go.ondev.store/Octasthicc/PlayGround)
[![GitHub](https://img.shields.io/badge/GitHub-Source-181717?logo=github&style=for-the-badge)](https://github.com/Octasthicc/PlayGround)

- 🎮 [Open the project](https://go.ondev.store/Octasthicc/PlayGround).
- ✨ [Remix this project](https://go.ondev.store/Octasthicc/PlayGround?remix=1).
- 💻 [Explore the source](https://github.com/Octasthicc/PlayGround).
- 🛠️ [Check build runs](https://github.com/Octasthicc/PlayGround/actions).
- 🐛 [Report an issue](https://github.com/Octasthicc/PlayGround/issues).
- 👤 [Explore the creator's projects](https://go.ondev.store/Octasthicc).
- 🌍 [Create with OMGithub](https://go.ondev.store/).
<!-- omgithub:readme:end -->

## Obelisk Companion for Android

This repository also contains a native Android companion for **Idle Obelisk Miner**. It observes the visible game screen with on-device OCR, optionally collects Freebie Packs, and estimates a prestige window from measured level-up times. It does not replace or modify the game.

### Build and install

- Use JDK 17 or 21 with Gradle; JDK 25 currently causes the Kotlin lint analyzer to fail. Build a debug APK with `gradle assembleDebug`.
- APK output: `app/build/outputs/apk/debug/app-debug.apk`.
- The debug application ID is `com.playground.obeliskcompanion.debug`.
- Copy the APK to an Android 11 (API 30) or newer device and install it. Android may ask you to allow installation from the app used to open the APK.

### First-time setup

1. Open **Obelisk Companion** and tap **Open accessibility settings**.
2. Enable **Obelisk Companion game monitor**. Android will show a sensitive-permission warning because accessibility can inspect the visible screen and perform taps.
3. Return to the app and open Idle Obelisk Miner. The service only captures or acts when the game package is the foreground window.
4. Return to the companion, select a calibration point, and tap the matching location in the latest screenshot. Calibrate all Freebie points before enabling Freebie automation. Prestige automation additionally requires separate points for the prestige menu, its continue action, and the final irreversible confirmation.
5. Keep **Manual mode** enabled and both automation switches off until OCR, calibration, and the on-screen preview have been checked. The prestige estimator needs at least three observed level-ups after level 20; it is an estimate, not a guarantee of an optimal strategy.

Screenshots are kept in the app's private storage and are not uploaded. Screen text recognition and fixed-coordinate taps may be inaccurate if the game layout, language, display orientation, or version changes. Recalibrate after such changes. Device/emulator testing against the actual game is still required; an APK build alone does not validate OCR or coordinates.
