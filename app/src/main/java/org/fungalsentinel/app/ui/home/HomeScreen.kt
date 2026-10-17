package org.fungalsentinel.app.ui.home

import android.content.res.Configuration
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.fungalsentinel.app.R
import org.fungalsentinel.app.ui.components.HalfScreenLayout
import org.fungalsentinel.app.ui.components.TutorialButton
import org.fungalsentinel.app.ui.components.TutorialHost
import org.fungalsentinel.app.ui.theme.AppColors

@Composable
fun HomeScreen(
    onNewProject: () -> Unit,
    onDngCamera: () -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
    isHalfScreen: Boolean = false,
    coverRatio: Float = 0.5f
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val tutorial = TutorialHost(
        stepId = "home",
        titleRes = R.string.tutorial_home_title,
        bodyRes = R.string.tutorial_home_body
    )

    HalfScreenLayout(forceHalfScreen = isHalfScreen, coverRatio = coverRatio) { innerModifier ->
        Column(
            modifier = innerModifier
                .fillMaxSize()
                .background(AppColors.Bg)
                .verticalScroll(rememberScrollState())
                .padding(if (isLandscape) 14.dp else 20.dp),
            verticalArrangement = Arrangement.spacedBy(if (isLandscape) 14.dp else 18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Image(
                    painter = painterResource(R.mipmap.mycospec_icon_foreground),
                    contentDescription = stringResource(R.string.home_icon_desc),
                    modifier = Modifier.size(54.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.app_name),
                        color = AppColors.TextPrimary,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.home_tagline),
                        color = AppColors.TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                TutorialButton(onClick = tutorial::show)
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AppColors.Surface, RoundedCornerShape(12.dp))
                    .border(1.dp, AppColors.Accent, RoundedCornerShape(12.dp))
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = stringResource(R.string.home_welcome),
                    color = AppColors.TextPrimary,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = stringResource(R.string.home_intro),
                    color = AppColors.TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }

            if (isLandscape) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        HomeCard(Icons.Default.Add, R.string.home_new_project, R.string.home_new_project_desc, onNewProject)
                        HomeCard(Icons.Default.CameraAlt, R.string.home_dng_camera, R.string.home_dng_camera_desc, onDngCamera)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        HomeCard(Icons.Default.History, R.string.home_history, R.string.home_history_desc, onHistory)
                        HomeCard(Icons.Default.Settings, R.string.home_settings, R.string.home_settings_desc, onSettings)
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    HomeCard(Icons.Default.Add, R.string.home_new_project, R.string.home_new_project_desc, onNewProject)
                    HomeCard(Icons.Default.CameraAlt, R.string.home_dng_camera, R.string.home_dng_camera_desc, onDngCamera)
                    HomeCard(Icons.Default.History, R.string.home_history, R.string.home_history_desc, onHistory)
                    HomeCard(Icons.Default.Settings, R.string.home_settings, R.string.home_settings_desc, onSettings)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeCard(
    icon: ImageVector,
    titleRes: Int,
    subtitleRes: Int,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.Outline)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier.size(42.dp).background(AppColors.Accent, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = AppColors.OnAccent, modifier = Modifier.size(22.dp))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(titleRes),
                    color = AppColors.TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(subtitleRes),
                    color = AppColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = AppColors.Accent
            )
        }
    }
}
