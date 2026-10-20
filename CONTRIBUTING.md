# Contributing to MycoSpec

MycoSpec is the Android implementation of the Fungal Sentinel measurement
pipeline — an offline pocket spectrometer that reads RAW frames through Camera2.
It is written by **team CAU-China (iGEM 2026)** and published for reuse: every
item here exists so that **another team can pick the pipeline up and run it on
their own hardware.**

- Repository: <https://gitlab.igem.org/2026/software/cau-china/mycospec>
  (mirror: <https://github.com/LittleSky-CN/Mycospec>)
- Python reference implementation: **FSSA** — <https://gitlab.igem.org/2026/software/cau-china/fungal-sentinel-spectral-analyzer>
- Latest build: **versionCode 229 / versionName 2.3.0** · License: **Apache-2.0**
  (see [LICENSE](LICENSE))

This document answers three questions: *what do we accept*, *how do you get set
up*, and *what has to be true before a change lands*.

---

## 1. We are open to contributions

Bug reports, patches, device reports and documentation are welcome. Ranked by how
much they help the next team:

| Contribution | Why it matters here |
|---|---|
| **A reproducible failure** on your phone or your dataset | Highest value: it tells the next team where the procedure breaks — see §6. |
| Device reports: which phones expose Camera2 RAW and which do not | The single most common blocker for a new team; see the camera note in §6. |
| Porting the pipeline to another language or platform | The pipeline is specification-first; every port strengthens the parity gate. |
| New canonical fluorophore IDs / emission windows | Must land on **both** sides — see §5. |
| Translations of the UI (`values-<lc>` strings) | English, 简体中文, español and Chichewa are shipped; another locale is a self-contained change. |
| Troubleshooting notes, cleaner workflows, accessibility fixes | iGEM's Contribution criterion explicitly rewards documented troubleshooting others can reuse. |

### What we ask before you start

1. Open an issue or merge request **before** starting anything large, so we can
   say whether it belongs here or on the Python side.
2. State which implementation(s) the change touches. Shared algorithms are gated
   by §5.
3. Keep the repository **source-only**: no APKs, `.aab`, `.dex`, keystores or
   build output. Signed APKs belong on GitHub Releases, not in git — iGEM expects
   a repository another team can carry (and our CI checks it, see
   `.gitlab-ci.yml`).
4. **Commit history is part of the deliverable.** Descriptive English commit
   messages; iGEM asks us to leave a history another team can resume from.
5. **Every number you write must be reproducible.** State the version and commit
   you ran. Do not quote a figure you did not produce.

---

## 2. Ground rules for anything that lands

- **Camera and storage. Nothing else.** The manifest requests `CAMERA` plus the
  storage reads needed to import DNG frames — and no `INTERNET` permission. No
  analytics, no crash reporting SDK, no cloud upload, no remote model call. For a
  project that handles biological samples this removes the third-party-processing
  question rather than answering it with a policy.
- **Imported frames are copied into app-private storage,** so re-analysis stays
  reproducible even if the user later edits or deletes the gallery item. Do not
  "optimise" that away.
- **English-only source.** KDoc, comments, identifiers and docs are English. UI
  text lives in `app/src/main/res/values*/strings.xml` and is translated there —
  currently `values` (English), `values-zh`, `values-es`, `values-ny`. **Every new
  UI string must be added to every locale**; a string present only in English is
  a bug.
- **Dependencies are locked.** `app/gradle.lockfile` and
  `settings-gradle.lockfile` are committed and verified by CI. If you add or bump
  a dependency, regenerate and commit both:

  ```bash
  ./gradlew dependencies --write-locks
  ```

- **Update `README.md`** whenever behaviour, permissions, requirements or
  reference numbers change. It is the part judges read.

---

## 3. Development setup

Requires **Android Studio / Gradle with a JDK** and the Android SDK; the wrapper
pins **Gradle 9.7.1** (`gradle/wrapper/gradle-wrapper.properties`). Build target:
`compileSdk 37`, `minSdk 24`, `targetSdk 36`, Java 11 source level.

```bash
git clone https://gitlab.igem.org/2026/software/cau-china/mycospec.git
cd mycospec

./gradlew :app:assembleDebug          # Windows: gradlew.bat :app:assembleDebug
./gradlew :app:testDebugUnitTest      # Windows: gradlew.bat :app:testDebugUnitTest
```

> The wrapper currently downloads Gradle from a mirror. If that host is not
> reachable from your network, change `distributionUrl` in
> `gradle/wrapper/gradle-wrapper.properties` to `https://services.gradle.org/distributions/gradle-9.7.1-bin.zip`
> — do not commit that change in a pull request.

---

## 4. Running the tests

```bash
./gradlew :app:testDebugUnitTest        # Windows: gradlew.bat :app:testDebugUnitTest
```

Expected result on v2.3.0: **86 tests across 9 classes, 0 failures**, reproducible
by anyone — no device, no DNG files, no network, **every test runs on synthetic
input**:

| Test class | What it pins |
|---|---|
| `AlgorithmParityTest` | Numerical behaviour of the analysis chain — replicate clipping, corrected area, SD — against hand-computed values |
| `PresetReuseParityTest` | Reusing an SPD / CURVE preset reproduces the numbers of the original calibration |
| `FluorophoreCanonicalTest` | Canonical fluorophore IDs resolve identically to the Python FSSA side |
| `ReportDataJsonTest`, `CurvePresetPayloadTest`, `PresetRoundTripTest` | Report and preset serialisation round-trips |
| `FinishProjectRaceTest`, `ReopenProvenanceTest`, `ReportProvenanceTest` | Provenance survives project completion and history re-open |

---

## 5. The parity gate — read this before touching a shared algorithm

The reusable idea is not either program. It is that the pipeline is **written
down once in physical units, implemented twice** (Kotlin here, Python in FSSA)
and tested for agreement. Where the two implementations overlap, their tests
carry identical expectations:

| Expectation | Meaning |
|---|---|
| Spectral unmixing coefficients are recovered to a tight tolerance | Reference taken as *(highest standard − its blank)*, blank and constant offset fitted out by least squares. |
| Response calibration does not smooth an isolated spike | Per-channel absolute response stays unsmoothed. |
| A broad Gaussian peak survives spike removal, an isolated one does not | Local median-absolute-deviation test with a 2 % amplitude floor. |
| Fusion weights equal the response proportions and sum to one | `w_c(λ) = resp_c(λ) / Σ_c resp_c(λ)`, `Σ_c w_c(λ) = 1` pointwise. |

**Running both suites is the gate for any change to a shared algorithm.**

```bash
# Android side (this repository)
./gradlew :app:testDebugUnitTest        # ≥ 86 tests across 9 classes

# Python side
cd /path/to/fungal-sentinel-spectral-analyzer
python -m pytest tests -q               # ≥ 279 passed
```

A merge request that changes a shared algorithm and reports only one side will
not be merged. If the numbers legitimately have to change, change the expectation
in **both** suites in the same MR and say why.

---

## 6. Reporting something that does not reproduce

A reproducible failure reported back is worth more to the next team than another
feature. Use this template:

```
Version / commit:      MycoSpec 2.3.0 (versionCode 229) @ <commit sha>
Device:                make, model, Android version
Camera:                does Camera2 work here? does it expose RAW? (see note below)
Input:                 DNG source (gallery import / capture) or synthetic fixture
Steps 1-4 inputs:      positioning frames, characterised source + its SPD CSV, blanks, standards
Expected:              number from the Python FSSA run on the same input
Observed:              what MycoSpec reported
What you ruled out:    e.g. "same result after reinstall", "other app reads RAW fine"
```

**Camera note.** Some devices (several Huawei-class phones among them) do not
open Camera2 RAW at all, so the app starts but its built-in camera cannot be
used. There is no software workaround — the OS simply does not expose the
frames. Import DNG frames from the gallery instead; the analysis pipeline is
identical. This is documented in `README.md §"The app starts but the built-in
camera does not work"`, and **reports of additional affected or working models
are a contribution in their own right.**

---

## 7. Using AI assistants

This repository ships configuration for AI coding assistants. If you use one,
read **[.claude/RESPONSIBLE_AI_USE.md](.claude/RESPONSIBLE_AI_USE.md)** first. In
short: never fabricate data, measurements, citations or attributions; keep a human
in the loop who reviewed and ran every line; commit no secrets or keystores; and
say in the merge request description which parts were AI-drafted.

---

## 8. Repository layout

```
app/src/main/java/org/fungalsentinel/app/
    analysis/     the numerics shared with FSSA — FrameProfileExtractor
                  (CFA decode incl. the red/blue label-swap self-check),
                  WavelengthCalibrator, SpectralResponseCalibrator
                  (response coefficients and the fusion weights),
                  SpectralUnmixer, SampleAnalyzer, ConcentrationRegressor
    camera/       Camera2 setup (CameraManager), preview view, RAW capture
    data/         Room database + DAOs, entities, presets, preferences
    ui/           Compose screens, including the six-part report
    viewmodel/    per-step state machines; ProjectViewModel drives Steps 1-4
    util/         LanguageManager (locale handling)
app/src/test/     synthetic-input unit tests (the parity suite)
```

Copyright 2026 CAU-China (iGEM Fungal Sentinel team). Contributions are accepted
under the same **Apache License 2.0** as the rest of the repository.
