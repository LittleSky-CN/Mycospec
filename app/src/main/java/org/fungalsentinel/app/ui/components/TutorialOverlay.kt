package org.fungalsentinel.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import org.fungalsentinel.app.R
import org.fungalsentinel.app.data.TutorialPrefs

class TutorialController internal constructor() {
    internal var onShow: () -> Unit = {}
    fun show() = onShow()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpTopBar(title: String, tutorial: TutorialController, onBack: (() -> Unit)? = null) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = { if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        actions = { TutorialButton(onClick = { tutorial.show() }) }
    )
}

@Composable
fun TutorialButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) { Icon(Icons.Default.Info, stringResource(R.string.tutorial_button_desc), tint = MaterialTheme.colorScheme.primary) }
}

@Composable
fun TutorialHost(stepId: String, titleRes: Int, bodyRes: Int, imageRes: Int? = null, autoShowOnFirstLaunch: Boolean = true): TutorialController {
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    var showDialog by remember { mutableStateOf(false) }
    val alreadyShown by TutorialPrefs.isShown(context, stepId).collectAsState(initial = true)
    LaunchedEffect(alreadyShown) { if (autoShowOnFirstLaunch && !alreadyShown) showDialog = true }
    val controller = remember { TutorialController() }
    controller.onShow = { showDialog = true }
    if (showDialog) {
        Dialog(onDismissRequest = { showDialog = false }, properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)) {
            Card(Modifier.fillMaxWidth().padding(16.dp), shape = RoundedCornerShape(20.dp), elevation = CardDefaults.cardElevation(8.dp)) {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { IconButton(onClick = { showDialog = false }) { Icon(Icons.Default.Close, "Close") } }
                    Icon(Icons.Default.Info, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(titleRes), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(bodyRes), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = { showDialog = false }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.tutorial_skip)) }
                        Button(onClick = { scope.launch { TutorialPrefs.markShown(context, stepId) }; showDialog = false }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.tutorial_got_it)) }
                    }
                }
            }
        }
    }
    return controller
}

@Composable
fun TutorialOverlay(stepId: String, titleRes: Int, bodyRes: Int, imageRes: Int? = null) { TutorialHost(stepId, titleRes, bodyRes, imageRes) }