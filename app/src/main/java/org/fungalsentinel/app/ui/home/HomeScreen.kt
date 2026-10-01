package org.fungalsentinel.app.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.fungalsentinel.app.R
import org.fungalsentinel.app.ui.components.HalfScreenLayout
import org.fungalsentinel.app.ui.components.TutorialOverlay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNewProject: () -> Unit,
    onDngCamera: () -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
    isHalfScreen: Boolean = false
) {
    var showTutorial by remember { mutableStateOf(false) }
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    TutorialOverlay(
        stepId = "home",
        titleRes = R.string.tutorial_home_title,
        bodyRes = R.string.tutorial_home_body
    )

    HalfScreenLayout(forceHalfScreen = isHalfScreen) { innerModifier ->
        Scaffold(
            modifier = innerModifier,
            topBar = {
                TopAppBar(
                    title = { Text("MycoSpec", fontWeight = FontWeight.Bold) },
                    actions = {
                        IconButton(onClick = { showTutorial = true }) {
                            Icon(Icons.Default.Info, contentDescription = "Tutorial")
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.home_welcome),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                if (isLandscape) {
                    // 横屏：两列布局
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            HomeCard(Icons.Default.Add, stringResource(R.string.home_new_project),
                                stringResource(R.string.home_new_project_desc), onNewProject)
                            HomeCard(Icons.Default.CameraAlt, stringResource(R.string.home_dng_camera),
                                stringResource(R.string.home_dng_camera_desc), onDngCamera)
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            HomeCard(Icons.Default.History, stringResource(R.string.home_history),
                                stringResource(R.string.home_history_desc), onHistory)
                            HomeCard(Icons.Default.Settings, stringResource(R.string.home_settings),
                                stringResource(R.string.home_settings_desc), onSettings)
                        }
                    }
                } else {
                    // 竖屏：单列布局
                    Column(
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        HomeCard(Icons.Default.Add, stringResource(R.string.home_new_project),
                            stringResource(R.string.home_new_project_desc), onNewProject)
                        HomeCard(Icons.Default.CameraAlt, stringResource(R.string.home_dng_camera),
                            stringResource(R.string.home_dng_camera_desc), onDngCamera)
                        HomeCard(Icons.Default.History, stringResource(R.string.home_history),
                            stringResource(R.string.home_history_desc), onHistory)
                        HomeCard(Icons.Default.Settings, stringResource(R.string.home_settings),
                            stringResource(R.string.home_settings_desc), onSettings)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}