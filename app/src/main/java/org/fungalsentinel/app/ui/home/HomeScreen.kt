package org.fungalsentinel.app.ui.home

import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.fungalsentinel.app.R
import org.fungalsentinel.app.ui.components.HalfScreenLayout
import org.fungalsentinel.app.ui.components.HelpTopBar
import org.fungalsentinel.app.ui.components.TutorialHost

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNewProject: () -> Unit,
    onDngCamera: () -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
    isHalfScreen: Boolean = false
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // FIX: the controller makes the top-bar ⓘ button actually open the dialog
    val tutorial = TutorialHost(
        stepId = "home",
        titleRes = R.string.tutorial_home_title,
        bodyRes = R.string.tutorial_home_body
    )

    HalfScreenLayout(forceHalfScreen = isHalfScreen) { innerModifier ->
        Scaffold(
            modifier = innerModifier,
            topBar = {
                HelpTopBar(title = "MycoSpec", tutorial = tutorial)
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.home_welcome),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))

                if (isLandscape) {
                    // Landscape: two columns so nothing is clipped
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            HomeCard(
                                icon = Icons.Default.Add,
                                title = stringResource(R.string.home_new_project),
                                subtitle = stringResource(R.string.home_new_project_desc),
                                onClick = onNewProject
                            )
                            HomeCard(
                                icon = Icons.Default.CameraAlt,
                                title = stringResource(R.string.home_dng_camera),
                                subtitle = stringResource(R.string.home_dng_camera_desc),
                                onClick = onDngCamera
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            HomeCard(
                                icon = Icons.Default.History,
                                title = stringResource(R.string.home_history),
                                subtitle = stringResource(R.string.home_history_desc),
                                onClick = onHistory
                            )
                            HomeCard(
                                icon = Icons.Default.Settings,
                                title = stringResource(R.string.home_settings),
                                subtitle = stringResource(R.string.home_settings_desc),
                                onClick = onSettings
                            )
                        }
                    }
                } else {
                    // Portrait: single column
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        HomeCard(
                            icon = Icons.Default.Add,
                            title = stringResource(R.string.home_new_project),
                            subtitle = stringResource(R.string.home_new_project_desc),
                            onClick = onNewProject
                        )
                        HomeCard(
                            icon = Icons.Default.CameraAlt,
                            title = stringResource(R.string.home_dng_camera),
                            subtitle = stringResource(R.string.home_dng_camera_desc),
                            onClick = onDngCamera
                        )
                        HomeCard(
                            icon = Icons.Default.History,
                            title = stringResource(R.string.home_history),
                            subtitle = stringResource(R.string.home_history_desc),
                            onClick = onHistory
                        )
                        HomeCard(
                            icon = Icons.Default.Settings,
                            title = stringResource(R.string.home_settings),
                            subtitle = stringResource(R.string.home_settings_desc),
                            onClick = onSettings
                        )
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeCard(
    icon: ImageVector,
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