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
4. Return to the companion and check the detected screen name and screenshot pixel size. OCR can locate visible **Claim**, **Continue**, and **Confirm** labels; calibrate unlabeled menu icons, close buttons, and any controls OCR cannot find by selecting the point and tapping its location in the latest screenshot. Coordinates scale to the captured screenshot size; recalibrate after major layout or orientation changes.
5. Keep **Manual mode** enabled and both automation switches off until OCR, calibration, and the on-screen preview have been checked. The service tracks whether it sees the game, Freebie menu, Prestige menu, or explicit Prestige confirmation screen and only advances the matching flow. The prestige estimator needs at least three observed level-ups after level 20; it is an estimate, not a guarantee of an optimal strategy.

Screenshots are kept in the app's private storage and are not uploaded. Screen text recognition and fixed-coordinate taps may be inaccurate if the game layout, language, display orientation, or version changes. Recalibrate after such changes. Device/emulator testing against the actual game is still required; an APK build alone does not validate OCR or coordinates.

## Cloud Android emulator (experimental)

`cloud-emulator/compose.yaml` runs one Android 11 emulator using [Docker-Android](https://github.com/budtmo/docker-android). This is an optional remote Android environment, not a verified Idle Obelisk Miner setup: game compatibility, Play Store availability, and Google Play services on this image have **not** been verified. The project does not provide the game or bypass its distribution/sign-in requirements. The upstream project's free image list does not promise a Play Store image; installing Play services separately may be unsupported or unavailable. Use a provider/device that legitimately supports the game if it requires Play services or store licensing.

### Prerequisites and start

- An x86-64 Linux VM with Docker Engine and the Docker Compose plugin, adequate CPU/RAM/disk for an Android emulator, and hardware virtualization exposed as `/dev/kvm`. Cloud providers must allow nested virtualization/KVM; check with `test -e /dev/kvm` and `kvm-ok` (from `cpu-checker`) before starting. A VM without KVM may be unusably slow or fail to start.
- Review the upstream Docker-Android image, its maintenance/release tags, and its security posture before use. The example uses its `emulator_11.0` tag; for reproducible deployments, pin an image digest you have reviewed and update it deliberately.

From the repository directory on the VM:

```sh
docker compose -f cloud-emulator/compose.yaml up -d
docker compose -f cloud-emulator/compose.yaml logs -f android
```

The named `android-home` volume persists `/home/androidusr` (including emulator user data) when the container is recreated. `docker compose down` keeps this volume; **`docker compose down -v` deletes it**. Make and test encrypted backups/snapshots before upgrades or other risky changes, with the emulator stopped when possible. A Docker volume is not itself a backup.

### Private browser access

The compose file publishes only the noVNC browser UI on `127.0.0.1:6080` of the VM. It does not publish VNC or ADB ports to the network. The browser UI itself should be treated as unauthenticated and unencrypted; do not change the binding to `0.0.0.0` or expose port 6080 in a cloud firewall. From your trusted workstation, create an SSH tunnel (replace the placeholders):

```sh
ssh -N -L 6080:127.0.0.1:6080 <ssh-user>@<vm-address>
```

Then visit `http://127.0.0.1:6080` locally. Keep the tunnel open while using the emulator. Restrict VM SSH access to your trusted IP/VPN, use SSH keys and normal host updates, and do not expose the Docker daemon or emulator ADB. Anyone who can reach the browser UI can control the emulator.

### Game, account, and data considerations

Once the emulator is running, install Idle Obelisk Miner only through a legitimate source available to you; availability in this image and compatibility with the emulator are unknown. Verify gameplay and the companion's accessibility/OCR behavior yourself before relying on them. If you sign in, understand the game's account and cloud-save/restore behavior first: a local emulator volume does not guarantee game progress is backed up or restorable. Treat the emulator volume and backups as sensitive because they may contain account sessions, app data, screenshots, and other personal information. Restrict host access, encrypt VM disks/backups where your provider supports it, and remove sign-in data before sharing or disposing of the VM.

VM compute time, persistent storage, snapshots, and network egress may incur charges; pricing and availability depend on the cloud provider and VM configuration. Stop or delete resources you no longer need, while taking care not to remove the persistent volume unintentionally.
