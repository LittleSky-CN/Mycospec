package org.fungalsentinel.app.ui.steps

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.fungalsentinel.app.R
import org.fungalsentinel.app.ui.capture.CaptureUploadScreen
import org.fungalsentinel.app.ui.components.*
import org.fungalsentinel.app.viewmodel.ProjectViewModel

enum class Fluorophore(val displayName: String, val bandNm: String) {
    YPET("Ypet", "500-530 nm"),
    EGFP("EGFP", "500-540 nm"),
    MCHERRY("mCherry", "590-630nm"),
    CFP("CFP", "460-490 nm"),
    MTURQUOISE2("mTurquoise2", "460-490 nm")
}

/**
 * Step 3 — concept-mockup layout:
 * header → green note box → white status box →
 * Row [ vertical step indicator | Column( fluorophore row, blank row, sample row, buttons ) ]
 * The indicator owns the left column so controls never collide with it in landscape.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Step3SampleScreen(
    onNext: () -> Unit,
    onBack: () -> Unit,
    onOpenReport: () -> Unit,
    projectViewModel: ProjectViewModel,
    isHalfScreen: Boolean = false,
    blankMode: String = "SINGLE"
) {
    val uiState by projectViewModel.uiState.collectAsState()
    val blankCaptures = uiState.blankCaptures
    val sampleCaptures = uiState.sampleCaptures
    val report = uiState.reportData

    var selectedFluorophore by remember { mutableStateOf(Fluorophore.EGFP) }
    var showBlankCapture by remember { mutableStateOf(false) }
    var showSampleCapture by remember { mutableStateOf(false) }
    var fluorophoreExpanded by remember { mutableStateOf(false) }

    val computed = report.step3Status.startsWith("COMPUTED")

    val status = report.step3Status
    val token = status.substringBefore('|')
    val message = status.substringAfter('|', status)
    val statusColor = when (token) {
        "FAILED", "BLOCKED" -> MaterialTheme.colorScheme.error
        "COMPUTED", "PRESET" -> Color(0xFF5CFF5C)
        "PROCESSING" -> Color(0xFF5CA8FF)
        else -> Color(0xFFF5F5C0)
    }

    val tutorial = TutorialHost(
        stepId = "step3_sample",
        titleRes = R.string.tutorial_step3_title,
        bodyRes = R.string.tutorial_step3_body
    )

    LaunchedEffect(Unit) {
        projectViewModel.setFluorophore(selectedFluorophore.name)
    }

    HalfScreenLayout(forceHalfScreen = isHalfScreen) { innerModifier ->
        if (showBlankCapture) {
            CaptureUploadScreen(
                title = stringResource(R.string.step3_capture_blank_title),
                subtitle = stringResource(R.string.step3_capture_blank_title),
                minCaptures = 1,
                maxCaptures = 5,
                capturedImages = blankCaptures,
                onImagesChanged = { projectViewModel.setBlankCaptures(it) },
                onConfirm = { showBlankCapture = false },
                onBack = { showBlankCapture = false },
                onHelp = { tutorial.show() },
                modifier = innerModifier
            )
        } else if (showSampleCapture) {
            CaptureUploadScreen(
                title = stringResource(R.string.step3_capture_sample_title),
                subtitle = stringResource(R.string.step3_capture_sample_title),
                minCaptures = 1,
                maxCaptures = 5,
                capturedImages = sampleCaptures,
                onImagesChanged = { projectViewModel.setSampleCaptures(it) },
                onConfirm = { showSampleCapture = false },
                onBack = { showSampleCapture = false },
                onHelp = { tutorial.show() },
                modifier = innerModifier
            )
        } else {
            Column(
                modifier = innerModifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 品红标题条直接显示内容（无 Step x 前缀）
                NeonHeader("STEP3:", stringResource(R.string.step3_short), onBack, tutorial)

                NeonOutlineBox(MaterialTheme.colorScheme.secondary) {
                    Text(
                        text = if (blankMode == "FULL") stringResource(R.string.note_blank_full)
                        else stringResource(R.string.note_blank_single),
                        color = Color(0xFFF5F5C0),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                NeonOutlineBox(Color.White) {
                    Text(
                        text = message,
                        color = statusColor,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // ── 概念图结构：左进度条独立列，右控件列，互不干扰 ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    NeonStepIndicator(current = 3)

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Target fluorophore
                        NeonRowBlock {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    stringResource(R.string.lbl_target_fluorophore),
                                    color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 14.sp
                                )
                                ExposedDropdownMenuBox(
                                    expanded = fluorophoreExpanded,
                                    onExpandedChange = { fluorophoreExpanded = !fluorophoreExpanded }
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .background(Color(0xFFF5F5C0), RoundedCornerShape(8.dp))
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                            .menuAnchor()
                                            .clickable { fluorophoreExpanded = true }
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                "${selectedFluorophore.displayName} (${selectedFluorophore.bandNm})",
                                                color = Color.Black, fontWeight = FontWeight.SemiBold, fontSize = 13.sp
                                            )
                                            Icon(Icons.Default.ArrowDropDown, null, tint = Color.Black, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                    DropdownMenu(
                                        expanded = fluorophoreExpanded,
                                        onDismissRequest = { fluorophoreExpanded = false },
                                        modifier = Modifier.background(Color(0xFFF5F5C0))
                                    ) {
                                        Fluorophore.values().forEach { f ->
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        "${f.displayName} (${f.bandNm})",
                                                        color = Color.Black,
                                                        fontWeight = if (f == selectedFluorophore) FontWeight.Bold else FontWeight.Normal,
                                                        fontSize = 13.sp
                                                    )
                                                },
                                                onClick = {
                                                    selectedFluorophore = f
                                                    projectViewModel.setFluorophore(f.name)
                                                    fluorophoreExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Blank captures
                        NeonRowBlock {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(stringResource(R.string.lbl_blank_captures), color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("${blankCaptures.size}/5", color = Color.Black, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Button(
                                    onClick = { showBlankCapture = true },
                                    enabled = blankCaptures.size < 5,
                                    shape = RoundedCornerShape(18.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFF5F5C0), contentColor = Color.Black,
                                        disabledContainerColor = Color(0xFFF5F5C0).copy(alpha = 0.4f)
                                    ),
                                    modifier = Modifier.height(36.dp)
                                ) { Text(stringResource(R.string.act_capture), fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                            }
                        }

                        // Sample captures
                        NeonRowBlock {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(stringResource(R.string.lbl_sample_captures), color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("${sampleCaptures.size}/5", color = Color.Black, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Button(
                                    onClick = { showSampleCapture = true },
                                    enabled = sampleCaptures.size < 5,
                                    shape = RoundedCornerShape(18.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFF5F5C0), contentColor = Color.Black,
                                        disabledContainerColor = Color(0xFFF5F5C0).copy(alpha = 0.4f)
                                    ),
                                    modifier = Modifier.height(36.dp)
                                ) { Text(stringResource(R.string.act_capture), fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                            }
                        }

                        // Common buttons (same right-column group as Step1/2)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            NeonYellowButton(stringResource(R.string.act_view_report), onOpenReport, modifier = Modifier.weight(1f))
                            NeonYellowButton(stringResource(R.string.next_step), onNext, enabled = computed, modifier = Modifier.weight(1f))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun NeonRowBlock(content: @Composable RowScope.() -> Unit) {
    Box {
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = 3.dp, y = 3.dp)
                .background(Color(0xFF8F8F8F), RoundedCornerShape(8.dp))
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.tertiary, RoundedCornerShape(8.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}