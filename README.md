# MycoSpec · Pocket Fluorescence Spectral Analyzer (Android)
# MycoSpec · 便携荧光光谱分析仪（Android 应用）

[English](#english) ｜ [中文](#中文)

> **Naming / 命名说明**
> | 名称 | 含义 |
> |---|---|
> | **Fungal Sentinel** | iGEM team project (wet lab + hardware + software) / iGEM 大项目名 |
> | **OptiX** | Hardware project / 硬件项目 |
> | **FSSA v1.3.4** | Python desktop spectral analyzer (reference implementation) / Python 桌面版参考实现 |
> | **MycoSpec** | This Android app / 本 Android 应用 |
>
> The Android application ID remains `org.fungalsentinel.app` so signed upgrades install over older builds. / 应用 ID 保持 `org.fungalsentinel.app`，保证旧版覆盖安装兼容。

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
| 4 · Standard curve | 2–10 concentration groups (Blank + Sample each) | OLS regression (formula, R²), predicted concentration, quality warnings |

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
- Cross-check formal results against Python FSSA v1.3.4 on the same DNG set.

### Versioning
Repository milestone **v2.0.0** (first public Android milestone); app module
`versionName 1.3.8 / versionCode 10`; workflow semantics aligned with Python
FSSA v1.3.4.

### Roadmap
- Live Camera2 RAW capture with manual exposure / ISO / focus / WB
- ZIP export (`manifest.json`, `result.json`, `profiles.csv`, `standards.csv`,
  `spd.csv`, `raw/`)
- Reusable calibration templates
- Tutorial illustrations & animations; accessibility (TalkBack) pass

### License & citation
License: see `LICENSE` (to be added). Developed by the iGEM Fungal Sentinel team
as part of the project software submission.

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
| 4 · 标准曲线 | 2–10 个浓度组（各含 Blank+Sample） | 最小二乘回归（公式、R²）、预测浓度、质量警告 |

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

### 科学有效性与限制
- 正式定量需固定光路、已知 R/G/B 波长、匹配 SPD 的标准光源与规范的空白/标准品；
- 模拟器只能验证界面与流程，不能验证光谱准确性；
- 本里程碑实时 Camera2 RAW 拍摄仍在接入中，分析流程请用相册 / 文件（DNG）导入验证；
- 正式结果应与 Python FSSA v1.3.4 在同一批 DNG 上对照。

### 版本
仓库里程碑 **v2.0.0**（首个公开 Android 里程碑）；应用模块 `versionName 1.3.8 /
versionCode 10`；流程语义与 Python FSSA v1.3.4 对齐。

### 路线图
- 实时 Camera2 RAW 拍摄与手动曝光 / ISO / 焦距 / 白平衡
- ZIP 导出（manifest.json、result.json、profiles.csv、standards.csv、spd.csv、raw/）
- 可复用校准模板
- 教程插图与动画；无障碍（TalkBack）适配