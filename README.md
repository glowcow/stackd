# stackd

Android wallet for Apple Wallet passes (`.pkpass`) and physical loyalty cards.
Physical cards are added by scanning their barcode with the camera, from a
photo, or by typing the number; passes arrive from mail, a browser or a file
manager. Android 16+ only. The interface speaks ten languages: English,
Belarusian, French, German, Hebrew, Italian, Polish, Russian, Serbian and
Spanish.

<p>
  <img src="docs/screenshots/stack.png" width="200" alt="The stack of cards">
  <img src="docs/screenshots/open-card.png" width="200" alt="An open ticket with its barcode">
  <img src="docs/screenshots/add.png" width="200" alt="The sheet with ways to add a card">
  <img src="docs/screenshots/stack-dark.png" width="200" alt="The stack in the dark theme">
</p>

## Contents

- [Features](#features)
- [Stack](#stack)
- [Project layout](#project-layout)
- [Build](#build)
  - [Local build in Docker](#local-build-in-docker)
  - [Install on a phone](#install-on-a-phone)
- [Download](#download)
- [Design](#design)
- [License](#license)

## Features

- **Stack** — cards overlap like a deck, the newest in front; scrolling
  leafs through them, pinned cards form a stack of their own.
- **Open in place** — a tapped card unfolds its fields and barcode, as in
  Apple Wallet; the screen stays on at full brightness while it is open.
- **Add** — a `.pkpass` file, a live barcode scan, a barcode in a gallery
  image, a photo of the card as its cover, or the number typed by hand.
- **`.pkpass`** — opens passes and `.pkpasses` bundles from mail, a browser
  or a file manager, with their colours, images and translations.
- **Pass updates** — refreshes a pass from its issuer on a tap or on a
  schedule, and notifies about what changed. Off by default.
- **Backup** — every card and the settings in one file, encrypted with a
  password if you set one; also part of Android's own backup.
- **App updates** — checks the releases on GitHub and installs a new
  version from inside the app. The weekly check is off by default.
- **Look** — light, dark or system theme, two colour schemes, ten
  languages including right-to-left Hebrew.
- **Private** — no account, no analytics; nothing touches the network
  until you turn on an update.

## Stack

| Area | Choice |
|---|---|
| Language / build | Kotlin 2.4, AGP 9.4 (built-in Kotlin), Gradle 9.8, JDK 25 |
| SDK | `minSdk 36` (Android 16), `compileSdk`/`targetSdk 37` |
| UI | Jetpack Compose (BOM 2026.09), Material 3, Navigation 3 |
| Storage | Room 3 with the bundled SQLite driver, DataStore |
| Camera | CameraX 1.6 (`camera-compose` viewfinder, `camera-mlkit-vision`) |
| Barcodes | ML Kit barcode scanning (bundled) to read, ZXing core to draw |

All versions live in [`gradle/libs.versions.toml`](gradle/libs.versions.toml).

## Project layout

```
app/src/main/kotlin/dev/glowcow/stackd/
  barcode/   formats, ZXing renderer, ML Kit mapping and still-image scan
  pkpass/    .pkpass parser (pure JVM, unit-tested), .strings parser, importer
  update/    pass refresh from the issuer and app self-update, background
             jobs, notifications
  backup/    the backup archive, its password encryption, save and restore
  data/      Room entity/DAO/database, repository, stack ordering, settings
  ui/        theme + icons, shared components, one package per screen
             (home/Wallet.kt: the deck, scrolling and the in-place open card)
app/src/test/  parser, update and backup tests
```

## Build

The host needs only Docker: JDK, Android SDK and the Gradle distribution live
in the `glowcow/android-sdk` image on Docker Hub (tag =
`<build-tools>-gradle<gradle>`), so a build downloads only the app's own
dependencies. Keep the tag in step with `buildToolsVersion` and the Gradle
wrapper.
The build runs as `linux/amd64` (Google ships `aapt2` for x86-64 Linux only).
On Apple Silicon turn on *Use Rosetta for x86_64/amd64 emulation* in Docker
Desktop and give the VM ~12 GB of memory: a warm debug build then takes
~1 min and a release (R8) build ~3.5 min, against ~35 min under QEMU.

### Local build in Docker

Run Gradle in the toolchain image with the sources mounted and a persistent
dependency cache:

```sh
IMAGE=glowcow/android-sdk:37.0.0-gradle9.8.0
RUN="docker run --rm --platform linux/amd64 -v $PWD:/src -v stackd-gradle:/root/.gradle/caches -v stackd-keys:/root/.android $IMAGE"

# debug APKs + unit tests
$RUN ./gradlew testDebugUnitTest assembleDebug

# lint + release APKs, unsigned; -PappVersion sets the version
$RUN ./gradlew -PappVersion=0.1.0 lintRelease assembleRelease
```

The APKs land in `app/build/outputs/apk/<type>/`, one per ABI:
`app-arm64-v8a-<type>.apk` for phones and `app-x86_64-<type>.apk` for the
emulator. The `stackd-keys` volume keeps the debug keystore, so a rebuilt
debug APK installs over the previous one.

### Install on a phone

Enable *Developer options → Wireless debugging* on the phone, then use `adb`
from the same image (the volume keeps the adb pairing keys):

```sh
docker run --rm -it --platform linux/amd64 -v stackd-adb:/root/.android -v "$PWD/app/build/outputs/apk":/apk \
  glowcow/android-sdk:37.0.0-gradle9.8.0 sh -c 'adb pair <ip>:<pair-port> && adb connect <ip>:<port> && adb install -r /apk/debug/app-arm64-v8a-debug.apk'
```

Or copy the APK to the phone and open it.

The same container reads crashes (`adb logcat -b crash -d`) and frame stats
(`adb shell dumpsys gfxinfo <package>`). Judge animations on a release (R8)
build: debug Compose is several times slower.

## Download

Signed APKs are attached to every
[release](https://github.com/glowcow/stackd/releases). Phones take
`stackd-<version>-arm64-v8a-release.apk`; the `x86_64` one is for the
emulator. There is no store listing: allow your browser or file manager to
install apps, then open the file.

Releases are signed with the certificate `CN=Anton Sediuk, O=glowcow`,
SHA-256
`7E:63:EC:86:6B:13:A8:23:B4:1B:4F:07:05:75:50:13:8F:B6:65:C3:50:B8:0D:41:CF:CF:B2:19:CB:98:C5:67`.
Check a downloaded file with `apksigner verify --print-certs <file>.apk`.
Versions up to 0.3.2 were signed with an earlier key and have to be
uninstalled before installing a newer one.

## Design

Mockups and the launcher icon come from the `stackd-design` bundle. The
default *classic* scheme is neutral grey — light `#FAFAFA` / dark `#161616`
backgrounds, accent `#1F6FEB`; the *warm* scheme of the original design has
`#FAF9F5` / `#1A1A18` and accent `#D97757`. Card colours start from
`#6A9BCC #788C5D #CBCADB #E3DACC #BCD1CA #141413`. UI font: Arimo, which
covers every language of the app. The header and the tab bar are frosted
glass: the page scrolls under them and shows through, blurred.

## License

[GNU General Public License v3.0 or later](LICENSE) © Anton Sediuk. A
modified version you distribute has to stay open under the same licence.

As an additional permission under section 7 of the GPL, the app may be
combined and distributed with Google's ML Kit barcode scanning library, which
is not free software.

Arimo is bundled under the SIL Open Font License 1.1 — see
[`licenses/Arimo-OFL.txt`](licenses/Arimo-OFL.txt).
