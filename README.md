# MycoSpec · Pocket Fluorescence Spectral Analyzer (Android)
# MycoSpec · 便携荧光光谱分析仪（Android 应用）

[English](#english) ｜ [中文](#中文)

> **MycoSpec — v2.3.0** (Android frontend of the Fungal Sentinel software stack)
>
> **Naming / 命名说明**
> | 名称 | 版本 | 含义 |
> |---|---|---|
> | **Fungal Sentinel** | — | iGEM team project (wet lab + hardware + software) / iGEM 大项目名 |
> | **OptiX** | — | Hardware project / 硬件项目 |
> | **FSSA** | v1.4.5 | Python desktop spectral analyzer (reference implementation) / Python 桌面版参考实现 |
> | **MycoSpec** | v2.3.0 | This Android app / 本 Android 应用 |
>
> The Android app was originally shipped under the same name **FSSA** during its
> 1.x releases. It was renamed **MycoSpec** from v2.0.0 onwards so that the
> desktop reference implementation and the mobile client could be told apart in
> papers, reports and the wiki. The two are distinct deliverables: FSSA is the
> Python code base, MycoSpec is this Kotlin/Android code base.
>
> The Android application ID remains `org.fungalsentinel.app` so signed upgrades install over older builds. / 应用 ID 保持 `org.fungalsentinel.app`，保证旧版覆盖安装兼容。
>
> MycoSpec 在 1.x 时期曾沿用 **FSSA** 之名；自 v2.0.0 起更名为 **MycoSpec**，以便与
> Python 桌面参考实现 FSSA 区分。两者是彼此独立的交付物：FSSA 指 Python 代码库，
> MycoSpec 指本 Kotlin/Android 代码库。

---

<a id="english"></a>
## English

### What it is
MycoSpec turns an Android phone with Camera2 RAW support into a **portable
fluorescence spectrometer** when combined with a fixed slit–grating–sample-holder
rig. It is fully **offline**: every calibration, correction and regression runs on
the device. It is **not** a general camera app and **not** an AI image classifier.

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
- Live Camera2 RAW capture is under integration in this milestone; use
  **Gallery / Files (DNG)** upload to exercise the analysis pipeline.
- Cross-check formal results against Python FSSA v1.4.5 on the same DNG set.

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

Analysis semantics are aligned with Python **FSSA v1.4.5**; in particular the
Step 2 channel fusion uses response-proportional normalisation on both sides
(`w_c = resp_c / Σ resp_c`, `Σ w_c = 1`), so the same DNG gives the same result
on phone and desktop.

### Roadmap
- Live Camera2 RAW capture with manual exposure / ISO / focus / WB
- ZIP export (`manifest.json`, `result.json`, `profiles.csv`, `standards.csv`,
  `spd.csv`, `raw/`)
- Reusable calibration templates
- Tutorial illustrations & animations; accessibility (TalkBack) pass

### License & citation
License: **MIT** (see `LICENSE`) — the same OSI-approved license used by the
Python FSSA repository. Developed by the iGEM Fungal Sentinel team as part of
the project software submission.

---

<a id="中文"></a>
## 中文

### 软件简介
MycoSpec 将支持 Camera2 RAW 的 Android 手机与固定的狭缝–光栅–样品架硬件结合，
变为**便携荧光光谱仪**；全部校准、校正与回归均在手机本地**离线**完成。
它不是普通相机，也不是识别真菌照片的 AI。

### 工作流程
| 步骤 | 目的 | 报告输出 |
|---|---|---|
| 1 · 波长校准 | 1–5 张 R/G/B 定位帧 | 像素↔波长映射（B+R 两点拟合）、自动 X-ROI、G 点验证、质量等级 |
| 2 · 光谱响应 | 1–5 张标准光源帧 | 曝光归一化的 R/G/B 通道响应系数 |
| 3 · 样品分析 | 1–5 张 Blank + 1–5 张 Sample | 荧光波段扣空白积分面积、重复 SD、校正光谱 |
| 4 · 标准曲线 | 2–10 个浓度组（各含 Blank+Sample），或载入已保存曲线预设 | 最小二乘回归（公式、R²）、预测浓度、质量警告 |

每个步骤均提供 **View Report** 按钮，打开四页**分析报告**：定位预览与 ROI
叠加图、三通道 1-D 轮廓、两点拟合 + G 点验证、响应轮廓、扣空白光谱与标准曲线。

### 功能要点
- 四步引导流程，每步首次进入自动弹出双语教程
- 可复用拍摄/上传组件：支持相册图片与 **DNG/RAW 文件**（系统文件选择器导入并
  复制到应用私有目录，保证重复分析稳定）
- 每批图片增删后**立即本地计算**
- 质量门槛：G 误差 ≤2.5 nm `PASS`、≤10 nm `WARNING`、>10 nm `FAILED`；ROI 饱和
  ≥1% 拒绝加入批次；校准保护默认阻断 FAILED 后的下游步骤（可在设置中显式关闭）
- 半屏模式适配遮光盖：仅横屏且光线传感器检测到遮挡时触发；设置中提供大尺寸预览
  调整遮挡比例
- 独立 DNG 相机（RAW 存入相册）、历史记录、设置页（定位波长 / SPD 来源 / 空白模式
  / 校准保护）
- 无网络权限、无自动上传；数据仅存本地 Room 数据库与 DataStore
- 支持本地保存/载入步骤 4 曲线预设；参考发射谱以波长（nm）保存，并一同保存
  标准曲线与 SPD 校准，可应用于新的步骤 3 样品，不依赖传感器像素坐标

### 安装
1. 从 [Releases](https://github.com/LittleSky-CN/Mycospec/releases) 下载最新 APK；
2. 按提示允许"安装未知应用"；
3. 打开 MycoSpec → 新项目 → 按 Step 1–4 操作 → 查看报告。

### 源码构建
需要 Android Studio（JDK 17+）与 Android SDK 37：

```powershell
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat assembleDebug     # 输出 app/build/outputs/apk/debug/app-debug.apk
.\gradlew.bat assembleRelease   # 输出 app/build/outputs/apk/release/app-release.apk
```

缺少私有 `keystore.properties`（由 `keystore.properties.example` 创建）时 Release
打包会故意失败；**切勿提交 keystore 与密码**。

#### 构建技术债：`lintVitalAnalyzeRelease` 曾报分析器崩溃（当前未复现）

**现象**：某一版打包时 `:app:lintVitalAnalyzeRelease` 失败，异常本体是

```
KotlinIllegalArgumentExceptionWithAttachments:
KaFirKotlinPropertyKtParameterBasedSymbol.<init> ... Unexpected owner function: null
```

报错文案自称 "this is a bug in lint"。同一份源码的 `compileReleaseKotlin` 与
`dexBuilderRelease` 均通过 —— 这是 lint 的 Kotlin FIR/UAST 分析器自身的缺陷，
不是本项目的代码问题。

**为什么不建议把跳过固化下来**：撞上时确实可以用 `-x lintVitalAnalyzeRelease`
绕开 —— `lintVital*` 只做静态分析、**不参与打包**，跳过不会改变一个字节的产物内容，
它是"无害"的。但一旦写进 README 或 `app/build.gradle.kts`，它就变成常规命令，
后人照抄后永久失去这道检查，而且届时没人分得清那是配置项还是 bug。

**当前状态（v2.2.8 实测，2026-10-17）**：**不再复现，因此没有跳过任何一个 lint 任务。**

```
./gradlew clean assembleRelease      → BUILD SUCCESSFUL（lintVital 三个任务全执行）
./gradlew bundleRelease              → BUILD SUCCESSFUL
./gradlew lintVitalAnalyzeRelease --rerun-tasks → BUILD SUCCESSFUL（第二次强制重跑）
```

环境：AGP 9.4.1 / Kotlin 2.4.20 / compileSdk 37 / build-tools 37.0.0 / JDK 25。
也就是说：崩溃是一次**观测记录**，不是当前的构建结论 —— 本次 Release 全链路
没有跳过任何 lint 任务，产物是在 lintVital 正常参与下打出来的。

**若将来再次撞上这个崩溃，按下面三步走，别直接把 `-x` 抄进这里：**

1. **先确认不是真的问题**：跑 `./gradlew lintVitalRelease --stacktrace`，看异常是
   `KaFirKotlinPropertyKtParameterBasedSymbol` / "this is a bug in lint" 一类
   来自分析器内部的栈帧，还是某个 `.kt` 文件的位置信息。**只有前者才可以跳过。**
2. **临时用命令行 `-x lintVitalAnalyzeRelease` 出包，但不要把 `-x` 写成默认命令**
   （不要写进 `app/build.gradle.kts` 的 `tasks.named { }`，更不要用
   `android { lint { … } }` 关掉整类检查）—— 一次性命令行跳过是可审计的，
   固化到配置里没人会记得它为什么在那。
3. **验证能否去掉这个跳过**：升级 AGP / Kotlin（这类分析器崩溃通常随版本修复），
   然后 `./gradlew clean lintVitalAnalyzeRelease --rerun-tasks`，连续两到三次干净
   跑通过，才允许把这个小节整段删掉 —— 那才是"债还清了"的证据。删之前请把当时的
   AGP / Kotlin 版本写进 commit message。

### 科学有效性与限制
- 正式定量需固定光路、已知 R/G/B 波长、匹配 SPD 的标准光源与规范的空白/标准品；
- 模拟器只能验证界面与流程，不能验证光谱准确性；
- 本里程碑实时 Camera2 RAW 拍摄仍在接入中，分析流程请用相册 / 文件（DNG）导入验证；
- 正式结果应与 Python FSSA v1.4.5 在同一批 DNG 上对照。

### 版本
当前版本 **v2.3.0** —— 本仓库、应用模块（`versionName 2.3.0 / versionCode 229`）、
应用内文案与所有交叉引用统一使用这一个版本号。更早的公开里程碑（v2.0.0、v2.0.1、
v2.2.8）仅作为 Git 历史/标签保留。

#### v2.3.0（`versionCode 229`）本版内容

- **合并重复的 `Fluorophore` enum。** 此前存在两份声明：数据层
  （`data/model/Entities.kt`）与 UI 层（`Step3SampleScreen.kt`）各一份。现仅保留
  数据层那一份，UI 层副本已删除并改为引用同一份枚举，消除了两份枚举各自漂移、
  同一荧光素在两层中行为不一致的隐患。
- **数据层 enum 新增 `FLUORESCEIN`**，并补齐 `fluorophoreBand()` 的
  **500–540 nm** 波段映射。荧光素现在可被正常选择并完成定量，不再被静默拒绝或
  映射到未定义波段。
- **新增 `canonicalFluorophore()` 归一化解析**，把外部导入的荧光素名（含别名、
  大小写与空白差异）归一到规范枚举。来自 Python FSSA 导出或手工预设的名称，现在
  会解析到与 Python 端一致的 canonical ID，保证同一批样品在手机端与桌面端识别一致。

分析语义与 Python **FSSA v1.4.5** 对齐；尤其步骤 2 的通道融合两端均采用响应比例
归一（`w_c = resp_c / Σ resp_c`，`Σ w_c = 1`），同一批 DNG 在手机与桌面端得到一致结果。

### 路线图
- 实时 Camera2 RAW 拍摄与手动曝光 / ISO / 焦距 / 白平衡
- ZIP 导出（manifest.json、result.json、profiles.csv、standards.csv、spd.csv、raw/）
- 可复用校准模板
- 教程插图与动画；无障碍（TalkBack）适配