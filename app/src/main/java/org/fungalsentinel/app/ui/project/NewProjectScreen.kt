package org.fungalsentinel.app.ui.project

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import org.fungalsentinel.app.R
import org.fungalsentinel.app.ui.components.HalfScreenLayout
import org.fungalsentinel.app.ui.components.NeonHeader
import org.fungalsentinel.app.ui.components.NeonOutlineBox
import org.fungalsentinel.app.ui.components.NeonYellowButton
import org.fungalsentinel.app.ui.components.TutorialHost
import org.fungalsentinel.app.ui.theme.AppColors

@Composable
fun NewProjectScreen(
    onProjectCreated: (String, Boolean) -> Unit,
    onBack: () -> Unit,
    isHalfScreen: Boolean = false,
    coverRatio: Float = 0.5f
) {
    var projectName by remember { mutableStateOf("") }
    var useBlueFilter by remember { mutableStateOf(false) }
    var showError by remember { mutableStateOf(false) }
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val tutorial = TutorialHost(
        stepId = "new_project",
        titleRes = R.string.tutorial_project_title,
        bodyRes = R.string.tutorial_project_body
    )

    HalfScreenLayout(forceHalfScreen = isHalfScreen, coverRatio = coverRatio) { innerModifier ->
        Column(
            modifier = innerModifier
                .fillMaxSize()
                .background(AppColors.Bg)
                .verticalScroll(rememberScrollState())
                .padding(if (isLandscape) 12.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(if (isLandscape) 10.dp else 14.dp)
        ) {
            NeonHeader(
                stepLabel = null,
                title = stringResource(R.string.new_project_title),
                onBack = onBack,
                tutorial = tutorial
            )

            NeonOutlineBox(AppColors.Accent) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.new_project_name_label),
                        color = AppColors.TextPrimary,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    OutlinedTextField(
                        value = projectName,
                        onValueChange = {
                            projectName = it
                            showError = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.new_project_name_hint)) },
                        singleLine = true,
                        isError = showError,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Done),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AppColors.Accent,
                            unfocusedBorderColor = AppColors.Outline,
                            focusedLabelColor = AppColors.Accent,
                            cursorColor = AppColors.Accent
                        )
                    )
                    if (showError) {
                        Text(
                            text = stringResource(R.string.new_project_name_empty),
                            color = AppColors.Error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            NeonOutlineBox(AppColors.Outline) {
                val filterTitle = stringResource(R.string.new_project_filter_title)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.FilterAlt, contentDescription = null, tint = AppColors.Accent)
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = filterTitle,
                            color = AppColors.TextPrimary,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = stringResource(R.string.new_project_filter_desc),
                            color = AppColors.TextSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Switch(
                        checked = useBlueFilter,
                        onCheckedChange = { useBlueFilter = it },
                        modifier = Modifier.semantics { contentDescription = filterTitle },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = AppColors.OnAccent,
                            checkedTrackColor = AppColors.Accent,
                            checkedBorderColor = AppColors.Accent
                        )
                    )
                }
            }

            NeonYellowButton(
                text = stringResource(R.string.new_project_create),
                onClick = {
                    val name = projectName.trim()
                    if (name.isEmpty()) {
                        showError = true
                    } else {
                        onProjectCreated(name, useBlueFilter)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
