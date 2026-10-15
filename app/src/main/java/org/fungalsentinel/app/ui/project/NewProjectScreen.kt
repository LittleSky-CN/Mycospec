package org.fungalsentinel.app.ui.project

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.fungalsentinel.app.R
import org.fungalsentinel.app.ui.components.HelpTopBar
import org.fungalsentinel.app.ui.components.TutorialHost
import org.fungalsentinel.app.ui.theme.AppColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewProjectScreen(
    onProjectCreated: (String, Boolean) -> Unit, // 【修改】增加 Boolean 参数
    onBack: () -> Unit
) {
    var projectName by remember { mutableStateOf("") }
    var hasBlueFilter by remember { mutableStateOf(false) } // 【新增】滤光片状态
    var showError by remember { mutableStateOf(false) }

    val tutorial = TutorialHost(
        stepId = "new_project",
        titleRes = R.string.tutorial_project_title,
        bodyRes = R.string.tutorial_project_body
    )

    Scaffold(
        topBar = {
            HelpTopBar(
                title = stringResource(R.string.new_project_title),
                tutorial = tutorial,
                onBack = onBack
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()) // 防止半屏模式遮挡
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = projectName,
                onValueChange = {
                    projectName = it
                    showError = false
                },
                label = { Text(stringResource(R.string.new_project_name_label)) },
                isError = showError,
                supportingText = {
                    if (showError) Text(stringResource(R.string.new_project_name_empty))
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // 【新增】滤光片开关卡片
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = AppColors.Surface)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Blue Filter Installed", fontWeight = FontWeight.Bold)
                        Text(
                            "Enable G+R fit (B absorbed)",
                            style = MaterialTheme.typography.bodySmall,
                            color = AppColors.TextSecondary
                        )
                    }
                    Switch(
                        checked = hasBlueFilter,
                        onCheckedChange = { hasBlueFilter = it }
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            Button(
                onClick = {
                    if (projectName.isBlank()) {
                        showError = true
                    } else {
                        // 【修改】传递两个参数
                        onProjectCreated(projectName, hasBlueFilter)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    stringResource(R.string.new_project_create),
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}