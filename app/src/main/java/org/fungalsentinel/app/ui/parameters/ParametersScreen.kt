package org.fungalsentinel.app.ui.parameters

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.fungalsentinel.app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParametersScreen(onBack: () -> Unit) {
    // TODO: 接入 CameraManager 的参数控制
    var exposureMs by remember { mutableStateOf("100") }
    var iso by remember { mutableStateOf("100") }
    var focusD by remember { mutableStateOf("0") }
    var wbTemp by remember { mutableStateOf("Auto") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Parameters") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
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
                value = exposureMs,
                onValueChange = { exposureMs = it },
                label = { Text("Exposure (ms)") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = iso,
                onValueChange = { iso = it },
                label = { Text("ISO") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = focusD,
                onValueChange = { focusD = it },
                label = { Text("Focus (D, 0=∞)") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = wbTemp,
                onValueChange = { wbTemp = it },
                label = { Text("White Balance (K or Auto)") },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}