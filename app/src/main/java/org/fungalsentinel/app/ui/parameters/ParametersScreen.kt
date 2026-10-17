package org.fungalsentinel.app.ui.parameters

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.fungalsentinel.app.R
import org.fungalsentinel.app.ui.components.HelpTopBar
import org.fungalsentinel.app.ui.components.TutorialHost

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParametersScreen(onBack: () -> Unit) {
    var exposureMs by remember { mutableStateOf("100") }
    var iso by remember { mutableStateOf("100") }
    var focusD by remember { mutableStateOf("0") }
    var wbTemp by remember { mutableStateOf("Auto") }

    val tutorial = TutorialHost(
        stepId = "parameters",
        titleRes = R.string.tutorial_parameters_title,
        bodyRes = R.string.tutorial_parameters_body
    )

    Scaffold(
        topBar = {
            HelpTopBar(title = "Parameters", tutorial = tutorial, onBack = onBack)
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                exposureMs, { exposureMs = it },
                label = { Text("Exposure (ms, 10–3000)") },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                iso, { iso = it },
                label = { Text("ISO (50–1600)") },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                focusD, { focusD = it },
                label = { Text("Focus (D, 0 = ∞)") },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                wbTemp, { wbTemp = it },
                label = { Text("White balance (K or Auto)") },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            // TODO: push to CameraManager.updateParams() when a capture session is active
        }
    }
}