package com.deno.social.ui.components

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.deno.social.ui.theme.DenoBlue
import com.deno.social.ui.theme.DenoTheme
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// A pending attachment (image / file / voice note) staged in the chat composer
// before Send. Not sent until the composer's Send button is pressed.
data class ChatPendingAttachment(
    val uri: String,
    val name: String,
    val isImage: Boolean,
    val isAudio: Boolean = false,
    val durationMs: Long = 0L
)

@Composable
fun Avatar(initial: String, size: Int = 40, onClick: (() -> Unit)? = null) {
    val colors = DenoTheme.colors
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(if (colors.isDark) colors.card else Color(0xFFE3F2FD))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initial.uppercase().take(1),
            color = DenoBlue,
            fontWeight = FontWeight.Bold,
            fontSize = (size / 2.5).sp
        )
    }
}

@Composable
fun DenoButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    enabled: Boolean = true
) {
    val colors = DenoTheme.colors
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(50.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (primary) DenoBlue else colors.card,
            contentColor = if (primary) Color.White else colors.textPrimary,
            disabledContainerColor = colors.border
        )
    ) {
        Text(text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }
}

@Composable
fun DenoTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isPassword: Boolean = false,
    singleLine: Boolean = true,
    showPasswordToggle: Boolean = false,
    required: Boolean = false
) {
    val colors = DenoTheme.colors
    var passwordVisible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = {
            if (required) {
                Text(
                    buildAnnotatedString {
                        append(label)
                        append(" *")
                    }
                )
            } else {
                Text(label)
            }
        },
        singleLine = singleLine,
        visualTransformation = if (isPassword && !passwordVisible) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        trailingIcon = if (isPassword && showPasswordToggle) {
            {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        null,
                        tint = colors.textSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        } else null,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = DenoBlue,
            unfocusedBorderColor = colors.border,
            focusedTextColor = colors.textPrimary,
            unfocusedTextColor = colors.textPrimary,
            focusedLabelColor = DenoBlue,
            unfocusedLabelColor = colors.textSecondary,
            cursorColor = DenoBlue,
            focusedContainerColor = colors.surface,
            unfocusedContainerColor = colors.surface
        )
    )
}

@Composable
fun EmptyState(icon: ImageVector, title: String, subtitle: String) {
    val colors = DenoTheme.colors
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, null, tint = colors.textSecondary, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(16.dp))
        Text(title, color = colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
        Spacer(Modifier.height(8.dp))
        Text(subtitle, color = colors.textSecondary, fontSize = 14.sp)
    }
}

@Composable
fun LoadingView() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = DenoBlue)
    }
}

fun formatCount(n: Int): String = when {
    n >= 1_000_000 -> String.format("%.1fM", n / 1_000_000f)
    n >= 1_000 -> String.format("%.1fK", n / 1_000f)
    else -> n.toString()
}

val LocalBottomBarInset = staticCompositionLocalOf { 0.dp }

const val DENO_APP_LINK = "https://deno.app"

fun profileShareLink(username: String) = "$DENO_APP_LINK/u/$username"

fun postShareLink(postId: String) = "$DENO_APP_LINK/p/$postId"

fun reelShareLink(reelId: String) = "$DENO_APP_LINK/r/$reelId"

fun shareContent(context: Context, text: String, chooserTitle: String = "Share") {
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(sendIntent, chooserTitle))
}

val MessageComposerBottomSpacing = 8.dp

// Wraps actual device audio recording so chat screens stay free of MediaRecorder
// bookkeeping. start() begins a real MIC recording into a cache file; stop()
// returns the final file (with measured duration) or null if nothing to stop.
class VoiceRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    fun start(): Boolean {
        stop()
        return runCatching {
            val file = File(context.cacheDir, "deno_voice_${System.currentTimeMillis()}.m4a")
            val rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else MediaRecorder()
            rec.setAudioSource(MediaRecorder.AudioSource.MIC)
            rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            rec.setAudioSamplingRate(44100)
            rec.setAudioEncodingBitRate(96000)
            rec.setOutputFile(file.absolutePath)
            rec.prepare()
            rec.start()
            recorder = rec
            outputFile = file
            true
        }.getOrElse { stop(); false }
    }

    // Returns the recorded attachment preview; null when no recording finished.
    fun stop(): ChatPendingAttachment? {
        val rec = recorder ?: return null
        val file = outputFile
        return try {
            rec.stop()
            rec.release()
            recorder = null
            outputFile = null
            val duration = mediaDuration(file) ?: 0L
            ChatPendingAttachment(
                uri = file.absolutePath,
                name = file.name,
                isImage = false,
                isAudio = true,
                durationMs = duration
            )
        } catch (e: Exception) {
            runCatching { rec.reset(); rec.release() }
            recorder = null
            outputFile = null
            file?.delete()
            null
        }
    }

    fun cancel() {
        val rec = recorder ?: return
        runCatching { rec.stop() }
        runCatching { rec.reset(); rec.release() }
        recorder = null
        outputFile?.delete()
        outputFile = null
    }

    private fun mediaDuration(file: File?): Long? = runCatching {
        if (file == null) return null
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(file.absolutePath)
        val ms = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        retriever.release()
        ms
    }.getOrNull()
}

fun formatPlaybackTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}

// One-line recording indicator shown above the composer while recording.
@Composable
fun MessageRecordingBar(
    seconds: Int,
    onCancel: () -> Unit,
    onStop: () -> Unit
) {
    val colors = DenoTheme.colors
    val dark = colors.isDark
    val surface = if (dark) Color(0xFF1C1C1C) else Color(0xFFF3F3F5)
    val border = if (dark) Color(0xFF3A3A3A) else Color(0xFFD0D0D0)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(surface)
            .border(1.dp, border, RoundedCornerShape(14.dp))
            .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.Mic,
            null,
            tint = Color(0xFFE53935),
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "Recording…",
            color = Color(0xFFE53935),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.width(6.dp))
        Text(
            formatPlaybackTime(seconds.toLong() * 1000),
            color = colors.textSecondary,
            fontSize = 13.sp
        )
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onStop) {
            Icon(
                Icons.Filled.Stop,
                "Stop recording",
                tint = Color(0xFFE53935),
                modifier = Modifier.size(20.dp)
            )
        }
        IconButton(onClick = onCancel) {
            Icon(
                Icons.Default.Close,
                "Cancel recording",
                tint = colors.textSecondary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

// Compact attachment preview staged above the composer (image thumbnail, file
// chip or voice note) with a remove X. Text typed alongside stays untouched;
// only the preview is removed.
@Composable
fun MessageAttachmentPreview(
    attachment: ChatPendingAttachment,
    onRemove: () -> Unit
) {
    val colors = DenoTheme.colors
    val dark = colors.isDark
    val context = LocalContext.current
    val surface = if (dark) Color(0xFF1C1C1C) else Color(0xFFF3F3F5)
    val border = if (dark) Color(0xFF3A3A3A) else Color(0xFFD0D0D0)
    val tint = if (dark) Color.White else Color(0xFF111111)

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(surface)
            .border(1.dp, border, RoundedCornerShape(14.dp))
            .padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val photo by produceState<ImageBitmap?>(null, attachment.uri) {
            value = decodePreviewImage(context, attachment.uri)?.asImageBitmap()
        }
        if (attachment.isAudio) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (dark) Color(0xFF2A2A2A) else Color(0xFFE6E8EB)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.Mic,
                    "Voice message",
                    tint = Color(0xFF2BB673),
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Text("Voice message", color = tint, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.width(6.dp))
            Text(
                formatPlaybackTime(attachment.durationMs),
                color = colors.textSecondary,
                fontSize = 12.sp
            )
        } else if (photo != null) {
            Image(
                bitmap = photo!!,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
            Spacer(Modifier.width(10.dp))
            Text(
                attachment.name,
                color = tint,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        } else {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (dark) Color(0xFF2A2A2A) else Color(0xFFE6E8EB)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.Description,
                    "Attachment",
                    tint = if (dark) Color.White else Color(0xFF555555),
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                attachment.name,
                color = tint,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onRemove) {
            Icon(
                Icons.Default.Close,
                "Remove attachment",
                tint = colors.textSecondary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

private suspend fun decodePreviewImage(context: Context, uriOrPath: String): Bitmap? = withContext(Dispatchers.IO) {
    runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        if (uriOrPath.startsWith("content://") || uriOrPath.startsWith("file://")) {
            val resolver = context.contentResolver
            resolver.openInputStream(Uri.parse(uriOrPath))?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            var sample = 1
            while (bounds.outWidth / sample > 600 || bounds.outHeight / sample > 600) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            resolver.openInputStream(Uri.parse(uriOrPath))?.use {
                BitmapFactory.decodeStream(it, null, opts)
            }
        } else {
            BitmapFactory.decodeFile(uriOrPath)
        }
    }.getOrNull()
}

@Composable
fun MessageComposer(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    placeholder: String,
    onAttach: (() -> Unit)? = null,
    onMicTap: (() -> Unit)? = null,
    isRecording: Boolean = false
) {
    val colors = DenoTheme.colors
    val dark = colors.isDark
    val hasContent = value.isNotBlank()
    val surface = if (dark) Color(0xFF1C1C1C) else Color.White
    val borderColor = if (dark) Color(0xFF3A3A3A) else Color(0xFFD0D0D0)
    val iconColor = if (dark) Color.White else Color(0xFF111111)

    val density = LocalDensity.current
    val imeBottom = with(density) { WindowInsets.ime.getBottom(density).toDp() }
    val navigationBottom = with(density) { WindowInsets.navigationBars.getBottom(density).toDp() }
    // The ChatScreen is already inside MainActivity's Scaffold content padding,
    // so do not add the bottom-nav inset a second time. When the keyboard is open,
    // only the IME area that is not already covered by the system navigation bar
    // needs to lift the composer.
    val bottomPadding = (imeBottom - navigationBottom).coerceAtLeast(0.dp) + MessageComposerBottomSpacing

    val scrollState = rememberScrollState()
    LaunchedEffect(value) {
        // Keep the caret visible once the (bounded) input grows past 120dp.
        scrollState.scrollTo(scrollState.maxValue)
    }

    Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp).padding(bottom = bottomPadding)) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 58.dp, max = 120.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(surface)
                .border(
                    width = 1.dp,
                    color = if (isRecording) Color(0xFFE53935) else borderColor,
                    shape = RoundedCornerShape(22.dp)
                )
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onAttach != null) {
                IconButton(
                    onClick = onAttach,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.Outlined.Add,
                        "Attach",
                        tint = iconColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = false,
                textStyle = TextStyle(color = iconColor, fontSize = 15.sp, lineHeight = 20.sp),
                cursorBrush = SolidColor(DenoBlue),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp, max = 104.dp)
                    .verticalScroll(scrollState)
                    .clip(RoundedCornerShape(16.dp))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty()) {
                            Text(
                                if (isRecording) "Recording…" else placeholder,
                                color = if (dark) Color(0xFF9A9A9A) else Color(0xFF9AA0A6),
                                fontSize = 15.sp,
                                lineHeight = 20.sp,
                                maxLines = 1
                            )
                        }
                        innerTextField()
                    }
                }
            )
            if (hasContent) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(DenoBlue)
                        .clickable(onClick = onSend),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            } else if (onMicTap != null) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (dark) Color(0xFF2A2A2A) else Color(0xFFEFF1F4))
                        .clickable(onClick = onMicTap),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.Mic,
                        null,
                        tint = if (dark) Color.White else Color(0xFF666666),
                        modifier = Modifier.size(20.dp)
                    )
                }
            } else {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (dark) Color(0xFF2A2A2A) else Color(0xFFEFF1F4)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.Mic,
                        null,
                        tint = if (dark) Color(0xFF9A9A9A) else Color(0xFF999999),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

// Anchored attachment popup: a compact white rounded card that opens right above
// the composer's "+" button (never a centered Dialog). Rows are plain, round
// icon chips with no dividers and no row borders.
@Composable
fun MessageAttachSheet(
    onCamera: () -> Unit,
    onPhotos: () -> Unit,
    onFiles: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = DenoTheme.colors
    val dark = colors.isDark
    val cardColor = if (dark) Color(0xFF181818) else Color.White
    val actionColor = if (dark) Color.White else Color(0xFF111111)
    val chipColor = if (dark) Color(0xFF2A2A2A) else Color(0xFFF1F3F5)

    Popup(
        popupPositionProvider = AttachMenuPositionProvider(LocalDensity.current),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        Surface(
            modifier = Modifier.width(300.dp),
            shape = RoundedCornerShape(20.dp),
            color = cardColor,
            shadowElevation = 8.dp
        ) {
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                AttachSheetRow("Camera", Icons.Outlined.PhotoCamera, actionColor, chipColor) { onCamera() }
                AttachSheetRow("Photos", Icons.Outlined.Image, actionColor, chipColor) { onPhotos() }
                AttachSheetRow("Files", Icons.Outlined.AttachFile, actionColor, chipColor) { onFiles() }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onDismiss)
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Cancel",
                        color = if (dark) colors.textSecondary else Color(0xFF757575),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

// Anchors the attachment popup just above the composer, aligned to the left
// where the "+" button lives, clamped to the screen edges.
private class AttachMenuPositionProvider(
    private val density: Density
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val gap = with(density) { 8.dp.toPx().roundToInt() }
        val margin = with(density) { 8.dp.toPx().roundToInt() }
        val x = (anchorBounds.left + with(density) { 14.dp.toPx().roundToInt() })
            .coerceAtLeast(margin)
            .coerceAtMost((windowSize.width - popupContentSize.width - margin).coerceAtLeast(margin))
        val y = (anchorBounds.top - popupContentSize.height - gap).coerceAtLeast(margin)
        return IntOffset(x, y)
    }
}

@Composable
private fun AttachSheetRow(
    title: String,
    icon: ImageVector,
    tint: Color,
    chipColor: Color,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(chipColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, title, tint = tint, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(14.dp))
        Text(title, color = tint, fontWeight = FontWeight.Medium, fontSize = 15.sp)
    }
}
