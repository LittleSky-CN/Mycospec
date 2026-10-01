package org.fungalsentinel.app.ui.capture

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import org.fungalsentinel.app.R
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureUploadScreen(
    title: String,
    subtitle: String = "",
    minCaptures: Int = 1,
    maxCaptures: Int = 5,
    capturedImages: List<Uri>,
    onLaunchCamera: () -> Unit = {},   // reserved hook; internal overlay handles capture
    onImagesChanged: (List<Uri>) -> Unit,
    onConfirm: () -> Unit,
    onBack: () -> Unit,
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
    ) { uris ->
        addUris(uris.mapNotNull { CaptureImporter.import(context, it) })
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Column(modifier = Modifier.weight(1f)) {
                    // FIX: named arguments
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    if (subtitle.isNotEmpty()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(
                    text = "${capturedImages.size} / $maxCaptures",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (capturedImages.size >= minCaptures)
                        MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error
                )
            }

            Spacer(Modifier.height(16.dp))

            if (capturedImages.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    itemsIndexed(capturedImages) { index, uri ->
                        val isDng = uri.toString().endsWith(".dng", true) ||
                                uri.path?.endsWith(".dng", true) == true
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(
                                    1.dp, MaterialTheme.colorScheme.outline,
                                    RoundedCornerShape(8.dp)
                                )
                        ) {
                            if (isDng) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            Icons.Default.RawOn,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        // FIX: named arguments
                                        Text(
                                            text = "DNG",
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            } else {
                                AsyncImage(
                                    model = uri,
                                    contentDescription = "Capture ${index + 1}",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            IconButton(
                                onClick = { onImagesChanged(capturedImages - uri) },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(24.dp)
                                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Remove",
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            Text(
                                text = "${index + 1}",
                                color = Color.White,
                                fontSize = 12.sp,
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .background(Color.Black.copy(alpha = 0.5f))
                                    .padding(horizontal = 4.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = { showCamera = true },
                    enabled = capturedImages.size < maxCaptures,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.capture_photo))
                }
                OutlinedButton(
                    onClick = { galleryLauncher.launch("image/*") },
                    enabled = capturedImages.size < maxCaptures,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.upload_gallery))
                }
                OutlinedButton(
                    onClick = {
                        filesLauncher.launch(
                            arrayOf("image/*", "image/x-adobe-dng", "image/dng", "application/octet-stream")
                        )
                    },
                    enabled = capturedImages.size < maxCaptures,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.upload_files))
                }
            }

            Spacer(Modifier.weight(1f))

            if (capturedImages.isEmpty()) {
                Text(
                    text = stringResource(R.string.capture_hint, minCaptures, maxCaptures),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                )
            }

            Button(
                onClick = onConfirm,
                enabled = capturedImages.size >= minCaptures,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.confirm_captures, capturedImages.size),
                    style = MaterialTheme.typography.titleMedium
                )
            }
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

object CaptureImporter {
    fun import(context: Context, source: Uri): Uri? = runCatching {
        val resolver = context.contentResolver
        runCatching {
            resolver.takePersistableUriPermission(source, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val mime = resolver.getType(source).orEmpty()
        val name = source.lastPathSegment.orEmpty()
        val isDng = name.endsWith(".dng", true) || mime.contains("dng", true)
        val ext = when {
            isDng -> "dng"
            mime.contains("png", true) -> "png"
            else -> "jpg"
        }
        val dir = File(context.filesDir, "captures").apply { mkdirs() }
        val target = File(dir, "import_${System.currentTimeMillis()}.${ext}")
        resolver.openInputStream(source)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: return null
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", target)
    }.getOrNull()
}