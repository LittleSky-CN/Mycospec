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
import org.fungalsentinel.app.ui.theme.AppColors

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

/**
 * 引导栏宿主组件。
 * 【v2.2.4 修复】改为“立即标记”策略：
 * 一旦检测到该页面从未显示过引导，立即写入“已显示”状态，然后再弹出对话框。
 * 这样无论用户点 Skip、点外部关闭还是点 Got it，都不会再次自动弹出。
 */
@Composable
fun TutorialHost(
    stepId: String,
    titleRes: Int,
    bodyRes: Int,
    imageRes: Int? = null,
    autoShowOnFirstLaunch: Boolean = true
): TutorialController {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showDialog by remember { mutableStateOf(false) }

    // 收集是否已显示的状态，初始设为 true 防止首次加载闪烁
    val alreadyShown by TutorialPrefs.isShown(context, stepId).collectAsState(initial = true)

    // 【核心修复】一旦检测到未显示，立即标记并弹出
    LaunchedEffect(alreadyShown) {
        if (autoShowOnFirstLaunch && !alreadyShown) {
            // 1. 立即标记为已显示（写入 DataStore）
            TutorialPrefs.markShown(context, stepId)
            // 2. 等待首帧渲染，避免动画卡顿
            kotlinx.coroutines.delay(400)
            // 3. 弹出对话框
            showDialog = true
        }
    }

    val controller = remember { TutorialController() }
    // 手动点击 Help 按钮时触发
    controller.onShow = { showDialog = true }

    if (showDialog) {
        Dialog(
            onDismissRequest = { showDialog = false }, // 仅关闭对话框，不重置状态
            properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)
        ) {
            Card(
                Modifier.fillMaxWidth().padding(16.dp),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(8.dp)
            ) {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        IconButton(onClick = { showDialog = false }) { Icon(Icons.Default.Close, "Close") }
                    }
                    Icon(Icons.Default.Info, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        stringResource(titleRes),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(bodyRes),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = AppColors.TextSecondary
                    )
                    Spacer(Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        // Skip 按钮：只关闭，不重置状态
                        OutlinedButton(onClick = { showDialog = false }, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.tutorial_skip))
                        }
                        // Got it 按钮：只关闭，状态已在 LaunchedEffect 中标记
                        Button(onClick = { showDialog = false }, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.tutorial_got_it))
                        }
                    }
                }
            }
        }
    }
    return controller
}

@Composable
fun TutorialOverlay(stepId: String, titleRes: Int, bodyRes: Int, imageRes: Int? = null) {
    TutorialHost(stepId, titleRes, bodyRes, imageRes)
}