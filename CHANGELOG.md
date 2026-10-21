# Changelog — MycoSpec

The Android client of the Fungal Sentinel spectral pipeline. All dates are
commit dates; every entry is sourced from repository history. Short commit
hashes are given so each claim can be checked against the tree.

Released version: **v2.4.0** (`versionName 2.4.0`, `versionCode 230`, 2026-10-21),
tagged `v2.4.0`. Version references were bumped in `app/build.gradle.kts`, the
Step 2 report title (`report_page2_title`, all five locales), `README.md` and
`CONTRIBUTING.md`.

Two things to know about the numbering before reading further:

- The app shipped under the name **FSSA** through its 1.x releases and was
  renamed **MycoSpec** at v2.0.0. The application ID stays
  `org.fungalsentinel.app` so signed updates install cleanly over older builds.
- Before v2.2.6 the module still declared `versionName 1.3.8 / versionCode 10`
  even though the Git tags read v2.x. Versioning was unified at v2.2.6
  (`136b85c`); the older entries below give the Git tag and the in-app number
  separately where they disagree.

---

## v2.4.0 — build 230 — 2026-10-21

### Added

- **Step 2 is indexed by wavelength, not by pixel** (`c6df061`). Every Step 2
  result is placed once on the canonical **380–780 nm / 1 nm / 401-point grid**,
  so an SPD preset no longer depends on the sensor width or on the pixel↔nm
  mapping it was recorded with.
- **SPD preset payload schema v3**, stored per wavelength with its own axis block
  (`c6df061`).
- **SPD presets can be exported to, and imported from, a JSON file** through the
  system file picker (`c6df061`). A calibration can now move between phones, and
  a preset written before v2.3.0 is resampled onto the grid on load instead of
  being refused (`37e4f3a`).
- **French** (`89542ff`): complete 377-key `values-fr` translation with French
  typography rules applied throughout, plus a `Français` entry in Settings.
- **Full localisation coverage** (`5c33952`): Spanish and Chichewa completed from
  68 to 378 keys each. Every user-facing string is a resource; nothing is
  hardcoded.
- **Three permanent i18n gates** (`c28c3e4`, `e140822`):
  `StringResourceFormatTest` — every string in all five locales must be parsable
  by `java.util.Formatter` (index before flags) and may not drop or renumber
  `%N$` placeholders; `LocaleCoverageTest` — every translatable key in `values/`
  must exist in all four translations, with a reverse check for zombie keys;
  `StringResourceArgCountTest` — a zero-argument `str(R.string.KEY)` /
  `getString(R.string.KEY)` call must refer to a template with no placeholders,
  with the same check carried through the
  `String.format(Locale.X, str(KEY), args…)` idiom. All three include lower-bound
  assertions so a broken resource path cannot turn them silently green.
- **`Step4ParityTest`** (`96ee002`): Step 4 cross-platform parity against Python
  FSSA, with the point tables measured on the Python side pinned as constants.
  Result — EGFP 499–519 unmixing matches to the digits Python reports
  (R² 0.9990410056 vs 0.999041, slope 8.5490385e-4 vs 8.54909e-4); YPET 517–537
  legacy matches to 6.1e-6 (0.2855148672 vs 0.285521). Feeding Python's
  low-scoring point table into Android's regressor reproduces 0.2855, which shows
  the "0.999 vs 0.3" gap lives in the point tables and not in the regression.
  FLUORESCEIN unmixing and YPET unmixing do **not** reconcile — the Python export
  is internally inconsistent there, listing bit-identical point tables with two
  different R² values — so those cases stay `@Ignore`d rather than having their
  tolerances loosened. The test also pins that n=2 collapses R² to exactly 1.0,
  n<2 returns null, the fit is unweighted, and `isLogarithmic` is always false.
- **Repository delivery files** (`1f517ce`, `fc5c5b9`, `37e4f3a`):
  `CONTRIBUTING.md`, committed Gradle dependency lockfiles
  (`app/gradle.lockfile`, `settings-gradle.lockfile`) so a rebuild reproduces the
  same dependency set, a `.gitignore` that keeps `*.apk` / `*.aab` out, and no
  `.idea/` or `local.properties`.

### Changed

- **Licence MIT → Apache License 2.0** (`37e4f3a`).
- **Comments, KDoc, README and build scripts in English** (`ea3af8c`, `37e4f3a`),
  matching the convention `CONTRIBUTING.md` states. Code is untouched by the
  translation.
- The Step 2 buttons (export SPD, import SPD, next) no longer read a localised
  status string (`c4155bf`) — see Fixed.

### Fixed

- **Crash on uploading a Step 1 DNG** (`c07fc8b`):
  `MissingFormatArgumentException: Format specifier '%+1$.2f'`. The template was
  legal; the call passed no arguments. Kotlin's vararg expands an empty array to
  the `getString(int, Object...)` overload, which runs `String.format`
  unconditionally, while the single-argument `getString(int)` does not format.
  Any `String.format(Locale.US, str(R.string.X), args…)` site blew up on the
  inner zero-argument fetch before the outer call supplied its arguments
  (four sites). `str()` now takes the single-argument path when it receives no
  arguments.
- **Step 2 export / import / next buttons stayed disabled outside English**
  (`c4155bf`): the code tested `report.spdStatus.startsWith("Calibrated")`, but
  that field holds localised text (`Calibrado`, `Yakonzedwa`, …), so it was false
  in every other locale — notably after reopening a project restored from Room.
  The check is now the language-independent `spdFrames > 0`.
- **Spanish and Chichewa decimal separators disagreed with what the screen
  actually rendered** (`e05d4a0`): all numbers go through
  `String.format(Locale.US, …)`, so `0,95` and `0,1` now read `0.95` and `0.1`.
- Hardcoded `" (Log-Fit)"` status suffix extracted to `st_log_fit`
  (en / zh / fr) (`5bd4eba`).

### Test baseline

`./gradlew :app:testDebugUnitTest` → **118 tests across 16 classes, 0 failures**
(86 tests / 9 classes at v2.3.0).

---

## v2.3.0 — build 229 — 2026-10-17

### Added

- **`FLUORESCEIN` in the data-layer enum**, with the matching `fluorophoreBand()`
  mapping to its 500–540 nm emission band (`8fb3c8a`). Fluorescein can be
  selected and quantified end-to-end instead of being rejected or mapped to an
  undefined band.
- **`canonicalFluorophore()`** (`8fb3c8a`): a normalising resolver that maps
  external and hand-typed fluorophore names, including aliases and case /
  whitespace variants, onto the canonical enum — the same canonical IDs used by
  the Python side, so one sample is identified identically on phone and desktop.

### Changed

- **The duplicated `Fluorophore` enum is unified** (`8fb3c8a`). Two copies
  existed, one in `data/model/Entities.kt` and one in `ui/steps/Step3SampleScreen.kt`;
  only the data-layer declaration remains. This removes the class of bug where
  the two drifted apart and a fluorophore valid in one layer was unknown in the
  other.
- Step 2 report title shows "MycoSpec v2.3.0" (`8fb3c8a`).

### Test baseline

86 tests, 0 failures.

---

## v2.2.8 — build 228 — 2026-10-17

### Added

- **The report snapshot is persisted** (`3167738`): `ProjectEntity` gains
  `reportDataJson` (Room migration 3 → 4), written on `finishProject()` and on
  every Step 4 regression result.
- Step 1 / 2 / 4 preset provenance (12 fields) is restored when a FINISHED
  project is reopened from History (`3167738`). Numbers are recomputed fresh;
  only the origin is history.

### Fixed

- Lost-update race between the Step 4 snapshot write and Finish that could knock
  a project's status back from FINISHED to IN_PROGRESS (`3167738`).

---

## v2.2.7 — build 227 — 2026-10-17

### Added

- **Step 4 now completes into the project report** instead of dead-ending on the
  result screen (`afccf3d`); back returns to Result, finish returns to Home. A
  "View Full Report" button is available as a fallback entry point.
- **Step 1 / 2 / 4 provenance surfaced in the report UI** (`afccf3d`): source,
  preset name, preset file id, imported-at timestamp and key parameters per page.
- **SPD drift detection** (`afccf3d`): `mappingDriftNm` / `driftLevel`, strict
  JSON parsing, and Mode B recompute against the current mapping.
- `rbSwapped` column with Room migration 2 → 3 (`afccf3d`).
- `spdSkipReason` and metadata parity with the desktop app in `SampleAnalyzer`
  and `FrameProfileExtractor` (`afccf3d`).
- Tests: `ReportProvenanceTest`, `PresetRoundTripTest`, `PresetReuseParityTest`.

### Changed

- **Provenance is truthful** (`afccf3d`): a live calibration clears that step's
  preset origin; loading a Step 1 preset no longer wipes the Step 2 SPD origin; a
  failed SPD apply is no longer reported as Preset; never-run steps report "Not
  run" rather than claiming "Live calibration".

### Test baseline

50 tests, 0 failures; `assembleRelease` and `bundleRelease` signed with the
release key.

---

## v2.2.6 — build 226 — 2026-10-16

### Added

- MIT `LICENSE`, matching the Python repository (`136b85c`). (Since replaced by
  Apache License 2.0 — see Unreleased.)

### Changed

- **Fusion wording now matches the fusion code** (`136b85c`). Step 2 has
  computed response-proportional weights (`w_c = resp_c / Σ resp_c`) since
  v2.2.0, but the surrounding text still described the retired logistic handoff
  relay. `logistic()` and `LOGISTIC_WIDTH_NM` are deleted, `handoffBG/GR`
  renamed `crossingBG/GR` and documented as diagnostics, and the
  `logisticWidth` JSON key dropped (old JSON still loads through a
  `handoffBG/GR` fallback). This also aligns the wording with Python FSSA v1.4.5.
- **Versioning unified** (`136b85c`): `versionName 1.3.8 → 2.2.6`,
  `versionCode 10 → 226`, with README and app text made authoritative.
  The README documents that the app shipped as "FSSA" during 1.x and was renamed
  MycoSpec at v2.0.0.
- Exported RAW frames are named `MycoSpec_RAW_*.dng` (`136b85c`).

### Fixed

- `SpdCalibrationResult.fromJson` did not compile (`75790be`): two `val`
  declarations sat inside the constructor argument list
  (`RETURN_TYPE_MISMATCH` plus a syntax cascade). They now precede the call, with
  `handoffBG/GR` still read for v1 JSON.
- The fusion parity test asserted on `null` (`75790be`): `calibrate()` returns
  null below 10 frames, and the test built a 4-frame profile. It now uses 31
  frames with distinct rising / flat / falling per-channel responses and also
  asserts the partition of unity `Σ w = 1` on every masked wavelength.

### Build

- GitHub Actions workflow runs the unit tests and builds `assembleDebug`; on a
  tag it publishes the APK as a pre-release, on manual runs it uploads it as a
  workflow artifact (`75790be`). The SDK is installed by driving `sdkmanager`
  directly (the `setup-android` action fails on current runners) and the Gradle
  wrapper falls back to `services.gradle.org` when the mirror does not answer
  (`fa9cb45`, `88df796`, `e5741f8`, `b5b49de`).

---

## v2.2.0 — 2026-10-13

Git tag `v2.2.0_debug`; in-app `versionName 1.3.8 / versionCode 10` until v2.2.6.

### Added

**Backend aligned term-for-term with Python FSSA v1.4.4 / v1.4.5** (`f4d1d2c`):

- `FrameProfileExtractor`: DNG parsed first (the embedded JPEG preview is no
  longer captured); TIFF tag 33434 exposure as RATIONAL num/den, tag 50714 black
  level, tag 50706 CFAPattern with DNG/EXIF convention normalisation and
  `{R,G,G,B}` validation (fallback BGGR — the effective phase of
  `cv2.COLOR_BayerRG2RGB`); 10/12/14-bit packed unpacking; column projection as
  the 99.5th percentile over rows; profiles as the ROI column mean; bitmap path
  limited to inverse sRGB.
- `WavelengthCalibrator`: valley-bounded Gaussian-core peak estimator —
  log-parabola vertex on the half-height core with an edge-midpoint fallback,
  unimodality and gauss-vs-edge suspect flags, legacy centroid kept as a
  diagnostic; green-error sign as −residual/slope; peak windows exported.
- `SpectralResponseCalibrator`: masked per-channel response with guarded division
  applied to independent positioning data, cross-channel agreement metric, and
  preset JSON bumped to version 2 with legacy v1 still loadable.
- `SampleAnalyzer`: response looked up **by wavelength** rather than by pixel
  order, fixing the index mismatch that corrupted Peak / FWHM / Centroid;
  features computed on ascending λ (negative-slope safe); FWHM by crossing
  detection with linear interpolation (no more negative FWHM); spectra cropped to
  400–700 nm.
- Step 4 warnings for negative standard or sample areas; Step 2 display arrays
  mask-cropped (no more 130–1067 nm tails).

**Neon UI from the design team's mockups** (`f4d1d2c`): a `NeonWidgets` set
(back pill, parallelogram step tab, magenta title bar with hard offset shadow,
outline boxes, yellow buttons with grey hard shadow, confirm pill, help circle,
vertical step indicator) and a responsive button group that stacks vertically
below 480/520 dp — fixing portrait truncation and half-screen overflow.
Capture screen and inline camera overlay follow the mockups; Steps 1–4 show only
their content in the magenta bar, and button text is 13 sp over two lines.

### Changed

- Step status strings are resources and localised, rather than hardcoded English.

### Also in this window

- **2026-10-11** (`34240b5`): the shared SPD calibration rewrite, mirroring
  `improved SPD calibration` on the Python side.
- **2026-10-11** (`05e1da0`): backend parity with Python FSSA v1.4.3.

---

## v2.1.1 — 2026-10-03

Git tag `v2.1.1_debug`; in-app `versionName 1.3.8 / versionCode 10` until v2.2.6.

### Fixed

- **First capture always failed** (`a44ddab`). `processRawImage()` polled for the
  `TotalCaptureResult` on the same `HandlerThread` that delivers
  `onCaptureCompleted`, so the first capture timed out and the second reused a
  stale result. Camera open / session / callback now live on their own thread,
  with a volatile hand-off and a 2 500 ms wait polled at 25 ms.

### Added

- **Half-screen preview page** (`a44ddab`): forced landscape, live lux and
  Covered / Uncovered readout to check the physical cover, mock-UI split preview,
  and a bottom slider (10–90 %) that moves the boundary and persists
  `coverRatio`. The sensor listener registers only when the setting is on and the
  device is landscape, with 10/30 lux hysteresis; devices with no sensor stay
  fullscreen instead of hanging. The confusing green/grey drag box was removed
  from Settings.
- **Auto-exposure toggle and log-scale manual exposure** (`a44ddab`): AE on by
  default (`CONTROL_AE_MODE_ON`); when off, a logarithmic slider covers 1 ms to
  10 min with fine control at the short end and a live label. DNG metadata now
  reads real exposure and ISO under AE; manual mode keeps the requested values.
  Both are persisted in DataStore and streamed into `CameraManager`.

### Changed

- Settings helpers extracted into `SettingsWidgets.kt`, removing the
  nested-local-function breakage class (`a44ddab`).
- Eight new strings × four locales, no fallback gaps (`a44ddab`).

---

## v2.1.0 — 2026-10-02

Git tag `v2.1.0_debug`; in-app `versionName 1.3.8 / versionCode 10` until v2.2.6.

### Added

The new-generation architecture restored in one coherent commit (`8e94207`),
with no version rollback:

- `FrameProfileExtractor`: hardened JPEG/PNG and uncompressed DNG reading,
  exposure from EXIF, OOM and bounds guards.
- `WavelengthCalibrator`: automatic X-ROI, dynamic peak windows, blue+red
  two-point fit with green third-point validation, PASS / WARNING / FAILED
  grading.
- `SpectralResponseCalibrator` following FSSA v1.4: per-channel response as
  Lamp / SPD_true with a dual mask (430–660 nm ∩ SPD above 10 % of peak), no
  smoothing, correction applied to independent positioning data, per-wavelength
  weights summing to 1 and the fused spectrum — serialised to JSON as a preset,
  so a calibration is done once and reused thereafter.
- `SampleAnalyzer` and `ConcentrationRegressor`: blank-subtracted band
  integration and concentration fitting.
- Auto-save, in-app help and localisation.

---

## v2.0.0 / v2.0.1 — 2026-10-01

First public tags of the renamed client (`0d2cd1a`, `8c76cd1`). 1.x releases
shipped under the name FSSA; the rename to MycoSpec happens here, while the
application ID stays `org.fungalsentinel.app`. No descriptive changelog was
recorded for either tag, so nothing beyond the rename is claimed here.

---

## Older versions
