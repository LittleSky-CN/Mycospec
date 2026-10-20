# MycoSpec · Pocket Fluorescence Spectral Analyzer (Android)

> **MycoSpec — v2.3.0** (Android frontend of the Fungal Sentinel software stack)
>
> **Naming**
> | Name | Version | Meaning |
> |---|---|---|
> | **Fungal Sentinel** | — | iGEM team project (wet lab + hardware + software) |
> | **OptiX** | — | Hardware project |
> | **FSSA** | v1.5.0 | Python desktop spectral analyzer (reference implementation) |
> | **MycoSpec** | v2.3.0 | This Android app |
>
> The Android app was originally shipped under the same name **FSSA** during its
> 1.x releases. It was renamed **MycoSpec** from v2.0.0 onwards so that the
> desktop reference implementation and the mobile client could be told apart in
> papers, reports and the wiki. The two are distinct deliverables: FSSA is the
> Python code base, MycoSpec is this Kotlin/Android code base.
>
> The Android application ID remains `org.fungalsentinel.app` so signed upgrades install over older builds.

---

### What it is
MycoSpec turns an Android phone with Camera2 RAW support into a **portable
fluorescence spectrometer** when combined with a fixed slit–grating–sample-holder
rig. It is fully **offline**: every calibration, correction and regression runs on
the device. It is **not** a general camera app and **not** an AI image classifier.

### Who it is for

- **iGEM teams and teaching labs** that have built (or are building) the OptiX
  slit–grating–sample-holder rig and want the analysis to run on a phone they already
  own — in the field, with no network and no cloud round-trip.
- **Field users** who need a concentration *now*. A plate reader or a benchtop
  spectrofluorometer is not portable; this turns a phone into one.
- **Anyone holding DNG/RAW frames** from a RAW-capable camera. Even when the in-app
  camera cannot start on a given device, the frames can be imported and the full
  pipeline runs unchanged (see the troubleshooting section below).
- **Developers of the Python FSSA.** MycoSpec and FSSA are expected to agree numerically
  on the same input; canonical fluorophore IDs and preset payloads are shared between
  them on purpose.

It is **not** a general camera app and **not** an AI image classifier — it never guesses
from a photograph, it computes from a calibrated spectrum — and it is **not** for
clinical, diagnostic or regulated measurement.

### Workflow
| Step | Purpose | Report output |
|---|---|---|
| 1 · Wavelength calibration | 1–5 R/G/B positioning frames | Pixel↔nm mapping (B+R two-point fit), auto X-ROI, G-point validation, quality grade |
| 2 · Spectral response | 1–5 frames of a characterized source | Exposure-normalized R/G/B response coefficients |
| 3 · Sample analysis | 1–5 Blank + 1–5 Sample frames | Blank-subtracted integrated area in the fluorophore band, replicate SD, corrected spectrum |
| 4 · Standard curve | 2–10 concentration groups (Blank + Sample each), or a saved curve preset | OLS regression (formula, R²), predicted concentration, quality warnings |

A four-page **Analysis Report** (the *View Report* button on every step) collects
all figures and parameters: positioning preview with ROI overlay, 1-D channel
profiles, two-point fit with third-point validation, response profiles,
blank-corrected spectrum, and the standard curve.

### Features
- Guided four-step workflow with first-run bilingual tutorial overlays
- Reusable capture/upload component: gallery images **and DNG/RAW files** via the
  system file picker (imported into app-private storage for stable re-analysis)
- Instant on-device computation after every batch change (add or remove)
- Quality gates: G-error ≤ 2.5 nm `PASS` / ≤ 10 nm `WARNING` / > 10 nm `FAILED`;
  ROI saturation ≥ 1 % rejects the capture; calibration protection blocks
  downstream steps on `FAILED` unless explicitly overridden in Settings
- Half-screen mode for the light-shield cover: landscape-only, light-sensor
  triggered, cover ratio adjustable with a large preview in Settings
- Standalone DNG camera (RAW saved to gallery), History records, Settings for
  positioning wavelengths / SPD source / blank mode / calibration protection
- Save/load Step 4 curve presets locally; reference spectra are stored on a
  wavelength (nm) axis, with the curve and SPD calibration, so a saved curve can
  be applied to new Step 3 samples without reusing sensor pixel coordinates
- No network permission and no uploads; results live in the local Room database
  and DataStore

### Install
1. Download the latest APK from
   [Releases](https://github.com/LittleSky-CN/Mycospec/releases).
2. Allow "install unknown apps" when prompted.
3. Open MycoSpec → New Project → follow Steps 1–4 → View Report.

### The app starts but the built-in camera does not work

This is the most common first-run problem, and on many phones it is **not a bug in
MycoSpec**.

**Why it happens.** The in-app camera is not an ordinary viewfinder. MycoSpec needs **RAW
frames**, so before opening the camera it walks every camera ID on the device and keeps
the first one that advertises Camera2's `REQUEST_AVAILABLE_CAPABILITIES_RAW`
(`CameraManager.kt` → `findRawCapableCamera()`); it then asks that camera for a
`RAW_SENSOR` output size. If no camera advertises RAW capability — or it does but
publishes no RAW output size — the camera cannot be opened, the failure is raised to the
UI as a short toast, and the rest of the app carries on working.

A large number of phones never expose Camera2 RAW. **Huawei devices in particular do not
open the Camera2 RAW path**, and there is no app-level workaround for that: the capability
is not offered by the device's camera HAL, so no permission, setting or third-party shim
can make it appear.

**What to do**

1. **Check the camera permission first.** Settings → Apps → MycoSpec → Permissions →
   Camera → *Allow*. When the permission has not been granted, the capture screen shows a
   button that re-requests it.
2. **Read the toast.** A message such as *"No RAW-capable camera found"* or *"No RAW
   sizes"* means point 3 applies — this device cannot feed the in-app camera.
3. **Use Gallery / Files (DNG) instead.** This is the supported path and it runs exactly
   the same analysis pipeline:
   - Capture the frames on something that does write DNG — a RAW-capable phone, a system
     camera in Pro/RAW mode, or a dedicated camera.
   - In MycoSpec choose **Gallery / Files (DNG)** and pick them. Imported frames are
     copied into app-private storage, so the same batch stays re-analysable and
     reproducible.
   - Then run Steps 1–4 as usual.
4. **Do not judge spectral accuracy on an emulator.** Emulators exercise the UI and the
   flow only; they have no real optics.

### Build from source
Requires Android Studio (JDK 17+) and Android SDK 37.

```powershell
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat assembleDebug     # -> app/build/outputs/apk/debug/app-debug.apk
.\gradlew.bat assembleRelease   # -> app/build/outputs/apk/release/app-release.apk
```

Release packaging intentionally fails without a private `keystore.properties`
(created from `keystore.properties.example`). **Never commit keystores or
passwords.**

### Repository layout
```
app/src/main/java/org/fungalsentinel/app/
├── analysis/    # FrameProfileExtractor (bitmap + minimal DNG/TIFF reader),
│                # WavelengthCalibrator, SpdCalibrator, SampleAnalyzer,
│                # ConcentrationRegressor
├── camera/      # CameraManager (Camera2 RAW), RawCaptureProcessor, preview
├── data/        # Room entities/DAO/database, DataStore settings, tutorial prefs
├── navigation/  # NavHost routes
├── ui/          # Compose screens: home, steps 1–4, report, result, dng,
│                # history, settings + shared components
└── viewmodel/   # ProjectViewModel (state + analysis orchestration),
                 # SettingsViewModel
```

### Scientific validity & limitations
- Reliable quantification requires fixed optics, known R/G/B wavelengths, a
  characterized source with matching SPD, and proper blanks/standards.
- Emulators validate UI and flow only — never spectral accuracy.
- Live Camera2 RAW capture needs a device that exposes the RAW capability through
  Camera2; on phones that do not (see the troubleshooting section above), use
  **Gallery / Files (DNG)** upload — it runs the same analysis pipeline. Manual
  exposure / ISO / focus / WB controls at capture time are still on the roadmap.
- Cross-check formal results against Python FSSA v1.5.0 on the same DNG set.

### Versioning
Current version **v2.3.0** — the single authoritative version number across this
repository, the app module (`versionName 2.3.0 / versionCode 229`), in-app text
and every cross-reference to this build. Earlier public milestones (v2.0.0,
v2.0.1, v2.2.8) are retained only as Git history / tags.

#### What's new in v2.3.0 (`versionCode 229`)

- **Unified the duplicated `Fluorophore` enum.** Two copies existed — one in the
  data layer (`data/model/Entities.kt`) and one in the UI layer
  (`Step3SampleScreen.kt`). Only the data-layer declaration remains; the UI copy
  has been deleted and the UI now imports the single canonical enum. This removes
  the class of bug where the two enums drifted apart and a fluorophore valid in
  one layer was unknown in the other.
- **Added `FLUORESCEIN` to the data-layer enum**, with the matching
  `fluorophoreBand()` mapping to its **500–540 nm** emission band. Fluorescein
  can now be selected and quantified end-to-end instead of being silently
  rejected or mapped to an undefined band.
- **Added `canonicalFluorophore()`**, a normalising resolver that maps
  external/imported fluorophore names (including aliases and case/whitespace
  variants) onto the canonical enum. Names arriving from Python FSSA exports or
  hand-edited presets now resolve to the same canonical IDs used by the Python
  side, so the same sample is identified identically on phone and desktop.
- **Step 2 results are indexed by wavelength, not by pixel.** The response,
  position, corrected, weight and fused curves are computed per pixel and then
  resampled once onto the canonical axis (380–780 nm, 1 nm step, 401 points),
  so a stored SPD preset no longer depends on the sensor width or on the
  pixel↔nm mapping it was recorded with. Presets are therefore portable between
  phones and across an app update; the JSON payload carries its own axis.
- **SPD presets can be exported and imported as files** (SAF, `application/json`)
  so a calibration made on one phone can be used on another. A preset written
  by a pre-v2.3.0 build is upgraded onto the wavelength grid on load instead of
  being rejected.

Analysis semantics are aligned with Python **FSSA v1.5.0**; in particular the
Step 2 channel fusion uses response-proportional normalisation on both sides
(`w_c = resp_c / Σ resp_c`, `Σ w_c = 1`), so the same DNG gives the same result
on phone and desktop.

### Reproducing the main results

The unit-test suite is the part of this repository that is reproducible by anyone, with
no device, no DNG files and no network: every test runs on **synthetic input**.

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

On v2.3.0 that runs **118 tests across 16 classes, 0 failures**:

| Test class | What it pins |
|---|---|
| `AlgorithmParityTest` | Numerical behaviour of the analysis chain — e.g. replicate clipping, corrected area and SD — against hand-computed expected values |
| `PresetReuseParityTest` | Reusing an SPD / CURVE preset reproduces the numbers of the original calibration |
| `SpdGridNumericalParityTest` | Resampling onto the 1 nm grid keeps the response and the Step 3 result within 1 % of the pixel-domain reference |
| `Step4ParityTest` | Standard-curve regression matches the Python FSSA fit on the same standards |
| `SpdPresetSchemaV3Test`, `LegacySpdPayloadUpgradeTest` | The SPD payload round-trips through schema v3, and a pre-v3 pixel-indexed preset is upgraded onto the grid rather than rejected |
| `FluorophoreCanonicalTest` | Canonical fluorophore IDs resolve identically to the Python FSSA side |
| `ReportDataJsonTest`, `CurvePresetPayloadTest`, `PresetRoundTripTest` | Report and preset serialisation round-trips |
| `FinishProjectRaceTest`, `ReopenProvenanceTest`, `ReportProvenanceTest` | Provenance survives project completion and history re-open |
| `LocaleCoverageTest`, `StringResourceFormatTest`, `StringResourceArgCountTest` | Every translatable string exists in all four locales, with matching format specifiers and argument counts |

To reproduce a **measurement** rather than a test, run Steps 1–4 on a real rig and
cross-check against Python FSSA on the same DNG set — MycoSpec and FSSA are expected to
return the same numbers for the same input, and any disagreement is treated as a bug.
Note that this cross-check is a procedure you perform, not something the unit tests assert.

### Roadmap
- Live Camera2 RAW capture with manual exposure / ISO / focus / WB
- ZIP export (`manifest.json`, `result.json`, `profiles.csv`, `standards.csv`,
  `spd.csv`, `raw/`)
- Reusable calibration templates
- Tutorial illustrations & animations; accessibility (TalkBack) pass

### Contributing

**Yes — we are open to contributions**, and we most want the ones that help the next
team get the pipeline running on their own hardware: reporting which phones expose
Camera2 RAW, porting the analysis chain to another platform, adding a fluorophore to
the canonical ID set, translating the UI, or simply telling us that a step did not
reproduce. A reproducible failure reported back is worth more here than another feature.

What we ask: no new permissions beyond camera and gallery import (no `INTERNET`), keep
the committed Gradle lockfiles regenerated when dependencies change, add every new UI
string to every locale, and run **both** the Android and the Python test suites when
you touch a shared algorithm.

Full policy, setup steps, the parity gate and the bug-report template are in
[`CONTRIBUTING.md`](CONTRIBUTING.md). Using an AI assistant? Read
[`.claude/RESPONSIBLE_AI_USE.md`](.claude/RESPONSIBLE_AI_USE.md) first.

### AI use

**The app does not use AI.** MycoSpec ships no model, performs no inference, and calls no
commercial or open-weight model API. Every number it reports comes from deterministic
numerical code — two-point wavelength fits, least-squares regression, and the same
response-proportional channel fusion as the Python side. There is no model to disclose,
no training data and no evaluation set.

**During development**, the team used AI coding assistants (conversation-based code
generation) for refactoring, test authoring, translation strings and documentation
drafts. Their output was reviewed, run and — where it was wrong — rewritten by a human.
No scientific result reported by this app was produced by an AI model, and no
AI-generated figure is presented as measured data.

### License & citation
License: **Apache License 2.0** (see `LICENSE`) — an OSI-approved license chosen for its
explicit patent grant and clean attribution on forks; the same license as the Python FSSA
repository. Copyright 2026 CAU-China (iGEM Fungal Sentinel team).

Prebuilt APKs are published on
[Releases](https://github.com/LittleSky-CN/Mycospec/releases) rather than committed to
the repository: an iGEM software repository is expected to contain source that produces
the output, not compiled binaries.
