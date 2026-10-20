package org.fungalsentinel.app.ui.capture

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.RawOn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import org.fungalsentinel.app.R
import org.fungalsentinel.app.ui.components.NeonBackPill
import org.fungalsentinel.app.ui.components.NeonButtonGroup
import org.fungalsentinel.app.ui.components.NeonConfirmButton
import org.fungalsentinel.app.ui.components.NeonHelpCircle
import org.fungalsentinel.app.ui.components.NeonYellowButton
import org.fungalsentinel.app.ui.theme.AppColors
import java.io.File

@Composable
fun CaptureUploadScreen(
    title: String,
    subtitle: String = "",
    minCaptures: Int = 1,
    maxCaptures: Int = 5,
    capturedImages: List<Uri>,
    onLaunchCamera: () -> Unit = {},
    onImagesChanged: (List<Uri>) -> Unit,
    onConfirm: () -> Unit,
    onBack: () -> Unit,
    onHelp: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showCamera by remember { mutableStateOf(false) }

    val addUris: (List<Uri>) -> Unit = { incoming ->
        if (incoming.isNotEmpty()) {
            val remaining = maxCaptures - capturedImages.size
            if (remaining > 0) onImagesChanged(capturedImages + incoming.take(remaining))
        }
    }
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris -> addUris(uris) }
    val filesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> addUris(uris.mapNotNull { CaptureImporter.import(context, it) }) }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                NeonBackPill(onBack)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(MaterialTheme.colorScheme.secondary, RoundedCornerShape(50.dp))
                        .padding(vertical = 8.dp, horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = title,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }
                Text(
                    text = "${capturedImages.size} / $maxCaptures",
                    color = AppColors.TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (onHelp != null) NeonHelpCircle(onHelp)
            }

            if (subtitle.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = subtitle,
                    color = AppColors.TextPrimary,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            if (capturedImages.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    itemsIndexed(capturedImages) { index, uri ->
                        NeonThumbnail(uri = uri, index = index, onRemove = { onImagesChanged(capturedImages - uri) })
                    }
                }
                Spacer(modifier = Modifier.height(18.dp))
            }

            BoxWithConstraints {
                val vertical = maxWidth < 480.dp
                NeonButtonGroup(vertical) { m ->
                    NeonYellowButton(
                        text = stringResource(R.string.capture_photo),
                        onClick = { showCamera = true },
                        enabled = capturedImages.size < maxCaptures,
                        icon = Icons.Default.CameraAlt,
                        modifier = m
                    )
                    NeonYellowButton(
                        text = stringResource(R.string.upload_gallery),
                        onClick = { galleryLauncher.launch("image/*") },
                        enabled = capturedImages.size < maxCaptures,
                        icon = Icons.Default.PhotoLibrary,
                        modifier = m
                    )
                    NeonYellowButton(
                        text = stringResource(R.string.upload_files),
                        onClick = {
                            filesLauncher.launch(
                                arrayOf("image/*", "image/x-adobe-dng", "image/dng", "application/octet-stream")
                            )
                        },
                        enabled = capturedImages.size < maxCaptures,
                        icon = Icons.Default.FolderOpen,
                        modifier = m
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = stringResource(R.string.capture_hint, minCaptures, maxCaptures),
                color = AppColors.TextPrimary,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                NeonConfirmButton(
                    text = stringResource(R.string.confirm_captures, capturedImages.size),
                    onClick = onConfirm,
                    enabled = capturedImages.size >= minCaptures
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (showCamera) {
            InlineCameraOverlay(
                onCaptured = { uri ->
                    addUris(listOf(uri))
                    showCamera = false
                },
                onBack = { showCamera = false }
            )
        }
    }
}

@Composable
private fun NeonThumbnail(uri: Uri, index: Int, onRemove: () -> Unit) {
    val isDng = uri.toString().endsWith(".dng", true) || uri.path?.endsWith(".dng", true) == true
    Box(
        modifier = Modifier
            .size(96.dp)
            .background(Color.Black, RoundedCornerShape(14.dp))
            .border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp))
    ) {
        if (isDng) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.RawOn, null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp)
                    )
                    Text("DNG", color = AppColors.Accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        } else {
            AsyncImage(
                model = uri,
                contentDescription = stringResource(R.string.cd_capture_index, index + 1),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(11.dp))
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(3.dp)
                .size(26.dp)
                .background(Color.Black, CircleShape)
                .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                .clip(CircleShape)
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Close, stringResource(R.string.cd_remove),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(12.dp)
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(topEnd = 12.dp))
                .padding(horizontal = 7.dp, vertical = 3.dp)
        ) {
            Text("${index + 1}", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
    }
}

object CaptureImporter {
    fun import(context: Context, source: Uri): Uri? = runCatching {
        val resolver = context.contentResolver
        runCatching { resolver.takePersistableUriPermission(source, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        val mime = resolver.getType(source).orEmpty()
        val name = source.lastPathSegment.orEmpty()
        val isDng = name.endsWith(".dng", true) || mime.contains("dng", true)
        val ext = when {
            isDng -> "dng"
            mime.contains("png", true) -> "png"
            else -> "jpg"
        }
        val dir = File(context.filesDir, "captures").apply { mkdirs() }
        val target = File(dir, "import_${System.currentTimeMillis()}.$ext")
        resolver.openInputStream(source)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: return null
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", target)
    }.getOrNull()
}