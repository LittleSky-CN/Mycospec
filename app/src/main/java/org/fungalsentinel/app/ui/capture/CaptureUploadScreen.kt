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
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.RawOn
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
import org.fungalsentinel.app.ui.components.TutorialButton
import java.io.File

/**
 * Reusable capture/upload component.
 *
 * Three input channels:
 *  1. Capture  -> full-screen inline Camera2 RAW overlay (works on first tap)
 *  2. Gallery  -> decodable images (JPEG/PNG)
 *  3. Files    -> DNG/RAW via SAF, copied into app-private storage
 *
 * @param onHelp optional callback that opens the contextual help dialog
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureUploadScreen(
    title: String,
    subtitle: String = "",
    minCaptures: Int = 1,
    maxCaptures: Int = 5,
    capturedImages: List<Uri>,
    onLaunchCamera: () -> Unit = {},   // reserved external hook; internal overlay is default
    onImagesChanged: (List<Uri>) -> Unit,
    onConfirm: () -> Unit,
    onBack: () -> Unit,
    onHelp: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showCamera by remember { mutableStateOf(false) }

    // Merge helper: never exceed maxCaptures
    val addUris: (List<Uri>) -> Unit = { incoming ->
        if (incoming.isNotEmpty()) {
            val remaining = maxCaptures - capturedImages.size
            if (remaining > 0) {
                onImagesChanged(capturedImages + incoming.take(remaining))
            }
        }
    }

    // Channel 2: gallery picker
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris -> addUris(uris) }

    // Channel 3: SAF document picker (sees DNG/RAW), imported to private storage
    val filesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        addUris(uris.mapNotNull { CaptureImporter.import(context, it) })
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // ── Top bar ──
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Column(modifier = Modifier.weight(1f)) {
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
                    color = if (capturedImages.size >= minCaptures) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                )
                // Contextual help entry (wired by each step screen)
                if (onHelp != null) {
                    TutorialButton(onClick = onHelp)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Thumbnail strip ──
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
                                    1.dp,
                                    MaterialTheme.colorScheme.outline,
                                    RoundedCornerShape(8.dp)
                                )
                        ) {
                            if (isDng) {
                                // Coil cannot decode RAW: show a clear placeholder
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

                            // Delete button
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

                            // Index badge
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
                Spacer(modifier = Modifier.height(16.dp))
            }

            // ── Action buttons: Capture / Gallery / Files(DNG) ──
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
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.capture_photo))
                }
                OutlinedButton(
                    onClick = { galleryLauncher.launch("image/*") },
                    enabled = capturedImages.size < maxCaptures,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.upload_gallery))
                }
                OutlinedButton(
                    onClick = {
                        filesLauncher.launch(
                            arrayOf(
                                "image/*",
                                "image/x-adobe-dng",
                                "image/dng",
                                "application/octet-stream"
                            )
                        )
                    },
                    enabled = capturedImages.size < maxCaptures,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.upload_files))
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // ── Hint ─
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

            // ── Confirm ──
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

        // Full-screen inline camera overlay (no navigation => no state loss)
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

/**
 * Copies an external SAF document into app-private storage and returns a
 * stable FileProvider URI, so later re-analysis never loses permission.
 */
object CaptureImporter {
    fun import(context: Context, source: Uri): Uri? = runCatching {
        val resolver = context.contentResolver

        // Best-effort persistable permission (harmless if unsupported)
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