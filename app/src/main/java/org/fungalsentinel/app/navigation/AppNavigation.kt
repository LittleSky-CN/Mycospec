package org.fungalsentinel.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import kotlinx.coroutines.launch
import org.fungalsentinel.app.FssaApplication
import org.fungalsentinel.app.ui.dng.DngCameraScreen
import org.fungalsentinel.app.ui.history.HistoryScreen
import org.fungalsentinel.app.ui.home.HomeScreen
import org.fungalsentinel.app.ui.project.NewProjectScreen
import org.fungalsentinel.app.ui.report.ReportScreen
import org.fungalsentinel.app.ui.result.ResultScreen
import org.fungalsentinel.app.ui.settings.SettingsScreen
import org.fungalsentinel.app.ui.steps.Step1WavelengthScreen
import org.fungalsentinel.app.ui.steps.Step2SpdScreen
import org.fungalsentinel.app.ui.steps.Step3SampleScreen
import org.fungalsentinel.app.ui.steps.Step4StandardCurveScreen
import org.fungalsentinel.app.viewmodel.ProjectViewModel
import org.fungalsentinel.app.viewmodel.SettingsViewModel
import org.fungalsentinel.app.ui.settings.HalfScreenPreviewScreen

object Routes {
    const val HOME = "home"
    const val NEW_PROJECT = "new_project"
    const val STEP1 = "step1"
    const val STEP2 = "step2"
    const val STEP3 = "step3"
    const val STEP4 = "step4"
    const val RESULT = "result"
    const val REPORT = "report"
    const val DNG_CAMERA = "dng_camera"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val HALF_PREVIEW = "half_preview"
}

@Composable
fun AppNavigation(navController: NavHostController) {
    val projectViewModel: ProjectViewModel = viewModel()
    val settingsViewModel: SettingsViewModel = viewModel()
    val settings by settingsViewModel.settings.collectAsState()
    val projectState by projectViewModel.uiState.collectAsState()
    val context = LocalContext.current
    val app = context.applicationContext as FssaApplication
    val dao = app.database.projectDao()
    val projects by dao.getAllProjects().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onNewProject = { navController.navigate(Routes.NEW_PROJECT) },
                onDngCamera = { navController.navigate(Routes.DNG_CAMERA) },
                onHistory = { navController.navigate(Routes.HISTORY) },
                onSettings = { navController.navigate(Routes.SETTINGS) },
                isHalfScreen = settings.halfScreenEnabled
            )
        }

        // 【修复】这里必须匹配 NewProjectScreen 的签名 (String, Boolean) -> Unit
        composable(Routes.NEW_PROJECT) {
            NewProjectScreen(
                onProjectCreated = { name, hasBlueFilter ->
                    projectViewModel.createProject(name, hasBlueFilter)
                    navController.navigate(Routes.STEP1) { popUpTo(Routes.HOME) }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.STEP1) {
            Step1WavelengthScreen(
                onNext = { navController.navigate(Routes.STEP2) },
                onBack = { navController.popBackStack() },
                onOpenReport = { navController.navigate(Routes.REPORT) },
                projectViewModel = projectViewModel,
                isHalfScreen = settings.halfScreenEnabled,
                coverRatio = settings.coverRatio
            )
        }

        composable(Routes.STEP2) {
            Step2SpdScreen(
                onNext = { navController.navigate(Routes.STEP3) },
                onBack = { navController.popBackStack() },
                onOpenReport = { navController.navigate(Routes.REPORT) },
                projectViewModel = projectViewModel,
                isHalfScreen = settings.halfScreenEnabled,
                coverRatio = settings.coverRatio
            )
        }

        composable(Routes.STEP3) {
            Step3SampleScreen(
                onNext = { navController.navigate(Routes.STEP4) },
                onBack = { navController.popBackStack() },
                onOpenReport = { navController.navigate(Routes.REPORT) },
                projectViewModel = projectViewModel,
                isHalfScreen = settings.halfScreenEnabled,
                blankMode = settings.blankMode,
                coverRatio = settings.coverRatio
            )
        }

        composable(Routes.STEP4) {
            Step4StandardCurveScreen(
                onNext = { navController.navigate(Routes.RESULT) },
                onBack = { navController.popBackStack() },
                onOpenReport = { navController.navigate(Routes.REPORT) },
                projectViewModel = projectViewModel,
                isHalfScreen = settings.halfScreenEnabled,
                blankMode = settings.blankMode,
                coverRatio = settings.coverRatio
            )
        }

        composable(Routes.RESULT) {
            ResultScreen(
                regressionFormula = projectState.reportData.regressionFormula,
                rSquared = projectState.reportData.regressionR2.toDoubleOrNull() ?: Double.NaN,
                predictedConcentration = projectState.reportData.predictedConcentration.toDoubleOrNull(),
                onFinish = {
                    projectViewModel.finishProject()
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.HOME) { inclusive = true }
                    }
                },
                onHome = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.HOME) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.REPORT) {
            ReportScreen(
                projectViewModel = projectViewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.DNG_CAMERA) {
            DngCameraScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.HISTORY) {
            HistoryScreen(
                projects = projects,
                projectViewModel = projectViewModel,
                onBack = { navController.popBackStack() },
                onOpenProject = { project ->
                    projectViewModel.openProject(project.id) { step ->
                        val route = when (step) {
                            1 -> Routes.STEP1
                            2 -> Routes.STEP2
                            3 -> Routes.STEP3
                            else -> Routes.STEP4
                        }
                        navController.navigate(route) { popUpTo(Routes.HOME) }
                    }
                },
                onDelete = { project ->
                    scope.launch { dao.deleteProject(project) }
                },
                onExport = { /* TODO: ZIP export */ }
            )
        }

        composable(Routes.HALF_PREVIEW) {
            HalfScreenPreviewScreen(
                coverRatio = settings.coverRatio,
                onRatioChange = { ratio ->
                    settingsViewModel.updateHalfScreen(settings.halfScreenEnabled, ratio)
                },
                onBack = { navController.popBackStack() }
            )
        }

        // 【修复】补全了所有 SettingsScreen 的参数，去掉了注释
        composable(Routes.SETTINGS) {
            SettingsScreen(
                wavelengthR = settings.wavelengthR,
                wavelengthG = settings.wavelengthG,
                wavelengthB = settings.wavelengthB,
                halfScreenEnabled = settings.halfScreenEnabled,
                coverRatio = settings.coverRatio,
                blankMode = settings.blankMode,
                calibrationProtection = settings.calibrationProtection,
                autoExposure = settings.autoExposure,
                exposureMs = settings.exposureMs,
                autoFocus = settings.autoFocus,
                focusDiopter = settings.focusDiopter,
                onAutoFocusChange = { settingsViewModel.updateAutoFocus(it) },
                onFocusDiopterChange = { settingsViewModel.updateFocusDiopter(it) },
                onWavelengthChange = { r, g, b -> settingsViewModel.updateWavelengths(r, g, b) },
                onHalfScreenChange = { enabled, ratio -> settingsViewModel.updateHalfScreen(enabled, ratio) },
                onBlankModeChange = { settingsViewModel.updateBlankMode(it) },
                onCalibrationProtectionChange = { settingsViewModel.updateCalibrationProtection(it) },
                onAutoExposureChange = { settingsViewModel.updateAutoExposure(it) },
                onExposureChange = { settingsViewModel.updateExposureMs(it) },
                onOpenPreview = { navController.navigate(Routes.HALF_PREVIEW) },
                onBack = { navController.popBackStack() }
            )
        }
    }
}