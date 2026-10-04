# stackd

Android wallet for Apple Wallet passes (`.pkpass`) and physical loyalty cards.
Physical cards are added by scanning their barcode with the camera, from a
photo, or by typing the number; passes arrive from mail, a browser or a file
manager. Android 16+ only.

## Contents

- [Features](#features)
- [Stack](#stack)
- [Project layout](#project-layout)
- [Build](#build)
  - [Local build in Docker](#local-build-in-docker)
  - [Install on a phone](#install-on-a-phone)
- [Release](#release)
  - [Signing](#signing)
- [Dependency updates](#dependency-updates)
- [Design](#design)
- [License](#license)

## Features

- **Stack home screen** — cards overlap like a deck, ordered by the date they
  were added, the newest in front with its barcode visible. A card has the
  proportions of a real one (ISO/IEC 7810 ID-1). The deck fans out down the
  screen: cards sit tight under the top edge and wide apart at the bottom, so
  scrolling leafs through them; past the ends it stretches like a rubber band
  and springs back. Pinned cards form a second stack of their own below the
  rest. Tabs all / cards / tickets switch by swipe.
- **Cards open in place**, like Apple Wallet — a tapped card rises to the top
  and unfolds its fields and barcode, the rest drop into a pile at the bottom.
  A tap on the card or the pile, a drag down or the (predictive) back gesture
  folds it back into its slot. Under the open card: share, update, edit,
  pin, and details (back fields, dates, delete) in a bottom sheet.
- **Scanner** — live barcode detection only (CameraX + ML Kit, bundled
  model, works offline); it slides up over the app and back down on the back
  gesture.
- **Add (+)** — a sheet with every source: `.pkpass` file, barcode search in
  a gallery image, a photo of the card that becomes its cover, manual entry,
  and the scanner.
- **`.pkpass` import** — opens `application/vnd.apple.pkpass` (and
  `.pkpasses` bundles) from other apps. Parses `pass.json` (as leniently as
  Wallet: trailing commas and comments are accepted), `*.lproj`
  localisation, colours, images and all field sections. Re-importing the same
  pass (`passTypeIdentifier` + `serialNumber`) updates it in place. The Apple
  signature is not verified — passes are only displayed.
- **Pass updates** — a pass that names a web service (`webServiceURL` +
  `authenticationToken`) can be refreshed: the *Update* button under the
  open card, or automatically every 1–24 h (off by default, Settings) with a
  notification listing the changed fields. It is Apple's PassKit web service
  `GET …/v1/passes/{type}/{serial}`, https only; issuers cannot push to
  Android, so the app polls. These requests are the only network traffic.
- **Barcodes** are drawn with ZXing; while a card is open the screen stays on
  and its brightness goes to maximum (configurable). Share sends the original
  `.pkpass` or the number.
- **Languages** — English (default) and Russian. Pick one in Settings or in
  the system *Settings → Apps → Stackd → App language*; both share one value.
- Search, light/dark/system theme.

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
  update/    pass refresh from the issuer, background job, notifications
  data/      Room entity/DAO/database, repository, stack ordering, settings
  ui/        theme + icons, shared components, one package per screen
             (home/Wallet.kt: the deck, scrolling and the in-place open card)
app/src/test/  parser and format tests
```

## Build

The host needs only Docker: JDK, Android SDK and the Gradle distribution live
in the `glowcow/android-sdk` image (recipe in `glowcow/docker`,
`dockerfiles/android-sdk`; tag = `<build-tools>-gradle<gradle>`), so a build
downloads only the app's own dependencies. Keep the tag in step with
`buildToolsVersion` and the Gradle wrapper.
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

# lint + release APKs (unsigned without the ANDROID_* signing variables)
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

## Release

The pipeline is the shared `android` entry point of **g_prjcts/ci-templates**;
`.gitlab-ci.yml` here is the `include:` and two variables. Every push to
`main` runs `android-lint` (`lintRelease`), `android-test` (unit tests) and
`android-build` (signed release APKs per ABI), each straight in the toolchain
image; the jobs stop if the Gradle wrapper or `buildToolsVersion` differ from
what the image carries. Dependencies and the Gradle build cache travel between
the jobs in the runner cache.

Push a `vX.Y.Z` tag to release: `android-build` names the APKs
`stackd-<version>-<abi>-release.apk`, `gitlab-release` uploads them with the R8
`mapping.txt` to the project's generic package registry and creates a GitLab
release linking them, `publish-github` pushes a source snapshot of the tag to
the public showcase [`glowcow/stackd`](https://github.com/glowcow/stackd) and
attaches the APKs to a GitHub release, and `publish-nas` copies the phone APK
to the `Android` dataset of the NAS. Retrace a release stack trace with
`retrace <mapping.txt> <trace.txt>` from the SDK image. `versionCode` is
derived from the tag: `X*10000 + Y*100 + Z`.

### Signing

One release key signs every glowcow Android app. It lives in Infisical
(`CI/CD` → `/android-signing`) and reaches this project as protected CI
variables through a Secret Sync:

| Variable | Content |
|---|---|
| `ANDROID_KEYSTORE_B64` | base64 of the PKCS12 keystore |
| `ANDROID_KEYSTORE_PASSWORD` | keystore password |
| `ANDROID_KEY_ALIAS` | key alias |
| `ANDROID_KEY_PASSWORD` | key password |

The `build` job writes the keystore to a temporary file and Gradle reads the
rest from the environment; it refuses to run without the keystore. A local
`assembleRelease` without these variables produces unsigned APKs.

Certificate: `CN=Anton Sediuk, O=glowcow`, SHA-256
`7E:63:EC:86:6B:13:A8:23:B4:1B:4F:07:05:75:50:13:8F:B6:65:C3:50:B8:0D:41:CF:CF:B2:19:CB:98:C5:67`.
Releases up to v0.3.2 were signed with an earlier key and cannot be updated
in place.

## Dependency updates

Renovate runs for the whole group from the pipeline of `g_prjcts/ci-templates`;
`renovate.json` here extends its shared preset. It covers the Gradle version
catalog and the Gradle wrapper; the toolchain image pin lives in the templates. Only stable releases are
proposed; Kotlin and KSP are grouped.

## Design

Mockups, palette and the launcher icon come from the `stackd-design` bundle.
Palette: light `#FAF9F5` / dark `#1A1A18` backgrounds, accent `#D97757`,
card colours `#6A9BCC #788C5D #CBCADB #E3DACC #BCD1CA #141413`. UI font:
Instrument Sans.

## License

[GNU General Public License v3.0 or later](LICENSE) © Anton Sediuk. A
modified version you distribute has to stay open under the same licence.

As an additional permission under section 7 of the GPL, the app may be
combined and distributed with Google's ML Kit barcode scanning library, which
is not free software.

Instrument Sans is bundled under the SIL Open Font License 1.1 — see
[`licenses/InstrumentSans-OFL.txt`](licenses/InstrumentSans-OFL.txt).
