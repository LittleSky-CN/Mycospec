package org.fungalsentinel.app.ui.components

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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

@Composable
fun TutorialOverlay(
    stepId: String,
    titleRes: Int,
    bodyRes: Int,
    imageRes: Int? = null,
    onDismiss: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val shownFlow = remember { TutorialPrefs.isShown(context, stepId) }
    val alreadyShown by shownFlow.collectAsState(initial = true)

    var showDialog by remember { mutableStateOf(false) }

    LaunchedEffect(alreadyShown) {
        if (!alreadyShown) {
            showDialog = true
        }
    }

    if (showDialog) {
        Dialog(
            onDismissRequest = {
                showDialog = false
                onDismiss()
            },
            properties = DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = true
            )
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        IconButton(onClick = {
                            showDialog = false
                            onDismiss()
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }

                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = stringResource(titleRes),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = stringResource(bodyRes),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedButton(
                            onClick = {
                                showDialog = false
                                onDismiss()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.tutorial_skip))
                        }
                        Button(
                            onClick = {
                                scope.launch {
                                    TutorialPrefs.markShown(context, stepId)
                                }
                                showDialog = false
                                onDismiss()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.tutorial_got_it))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TutorialButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            Icons.Default.Info,
            contentDescription = stringResource(R.string.tutorial_button_desc),
            tint = MaterialTheme.colorScheme.primary
        )
    }
}