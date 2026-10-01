package org.fungalsentinel.app.ui.project

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.fungalsentinel.app.R
import org.fungalsentinel.app.ui.components.TutorialOverlay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewProjectScreen(
    onProjectCreated: (String) -> Unit,
    onBack: () -> Unit
) {
    var projectName by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }

    TutorialOverlay(
        stepId = "new_project",
        titleRes = R.string.tutorial_project_title,
        bodyRes = R.string.tutorial_project_body
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.new_project_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
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

            Spacer(Modifier.weight(1f))

            Button(
                onClick = {
                    if (projectName.isBlank()) {
                        showError = true
                    } else {
                        onProjectCreated(projectName)
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
        }
    }
}