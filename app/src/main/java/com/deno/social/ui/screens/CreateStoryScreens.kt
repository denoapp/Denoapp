package com.deno.social.ui.screens

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deno.social.ui.components.EmptyState
import com.deno.social.ui.theme.DenoBlue
import com.deno.social.ui.theme.DenoColors
import com.deno.social.ui.theme.DenoTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class StoryGalleryMedia(
    val uri: Uri,
    val isVideo: Boolean,
    val durationMs: Long,
    val dateAdded: Long
)

private enum class StoryStep { SELECT, PREVIEW }

private enum class StoryGalleryTab { GALLERY, PHOTO, VIDEO }

private fun storyMatchesTab(media: StoryGalleryMedia, tab: StoryGalleryTab): Boolean =
    when (tab) {
        StoryGalleryTab.GALLERY -> true
        StoryGalleryTab.PHOTO -> !media.isVideo
        StoryGalleryTab.VIDEO -> media.isVideo
    }

@Composable
fun CreateStoryScreen(onBack: () -> Unit, onShared: () -> Unit) {
    val context = LocalContext.current
    var step by remember { mutableStateOf(StoryStep.SELECT) }
    var tab by remember { mutableStateOf(StoryGalleryTab.GALLERY) }
    var selected by remember { mutableStateOf<StoryGalleryMedia?>(null) }
    var caption by remember { mutableStateOf("") }
    var reloadKey by remember { mutableStateOf(0) }

    val neededTypes = setOf(MediaType.PHOTO, MediaType.VIDEO)
    val permissionState = MediaPermission.accessState(context, neededTypes)

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        reloadKey++
    }

    val items by produceState(initialValue = emptyList<StoryGalleryMedia>(), reloadKey, permissionState) {
        value = if (MediaPermission.hasAccess(context, neededTypes)) {
            withContext(Dispatchers.IO) { storyQueryGallery(context.contentResolver) }
        } else {
            emptyList()
        }
    }

    when (step) {
        StoryStep.SELECT -> StorySelectStep(
            permissionState = permissionState,
            items = items,
            selected = selected,
            tab = tab,
            onTabChange = { newTab ->
                tab = newTab
                if (selected != null && !storyMatchesTab(selected!!, newTab)) selected = null
            },
            onSelect = { item -> selected = if (selected == item) null else item },
            onRequestPermission = {
                MediaPermission.markRequestStarted(context, MediaPermission.permissionRequest(neededTypes))
                permissionLauncher.launch(MediaPermission.permissionRequest(neededTypes))
            },
            onOpenSettings = { MediaPermission.openSettings(context) },
            onNext = { step = StoryStep.PREVIEW },
            onBack = onBack
        )
        StoryStep.PREVIEW -> StoryPreviewStep(
            media = selected,
            caption = caption,
            onCaptionChange = { caption = it },
            onBack = { step = StoryStep.SELECT },
            onShare = onShared
        )
    }
}

private fun storyQueryGallery(contentResolver: ContentResolver): List<StoryGalleryMedia> {
    val result = mutableListOf<StoryGalleryMedia>()

    try {
        contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DATE_ADDED
            ),
            null,
            null,
            "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val dateIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            while (cursor.moveToNext()) {
                result += StoryGalleryMedia(
                    uri = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        cursor.getLong(idIndex)
                    ),
                    isVideo = false,
                    durationMs = 0L,
                    dateAdded = cursor.getLong(dateIndex)
                )
            }
        }
    } catch (_: SecurityException) {
    }

    try {
        contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DATE_ADDED,
                MediaStore.Video.Media.DURATION
            ),
            null,
            null,
            "${MediaStore.Video.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val dateIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
            val durationIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            while (cursor.moveToNext()) {
                result += StoryGalleryMedia(
                    uri = ContentUris.withAppendedId(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        cursor.getLong(idIndex)
                    ),
                    isVideo = true,
                    durationMs = cursor.getLong(durationIndex),
                    dateAdded = cursor.getLong(dateIndex)
                )
            }
        }
    } catch (_: SecurityException) {
    }

    return result.sortedByDescending { it.dateAdded }
}

private fun storyDecodeSampledBitmap(resolver: ContentResolver, uri: Uri, reqWidth: Int, reqHeight: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= reqWidth || bounds.outHeight / (sample * 2) >= reqHeight) {
        sample *= 2
    }
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
}

private fun storyVideoFrame(context: Context, uri: Uri): Bitmap? {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, uri)
        retriever.getFrameAtTime(0) ?: retriever.getFrameAtTime(1_000_000)
    } catch (_: Exception) {
        null
    } finally {
        try { retriever.release() } catch (_: Exception) {}
    }
}

private fun storyLoadThumbnail(context: Context, media: StoryGalleryMedia): Bitmap? =
    if (media.isVideo) {
        storyVideoFrame(context, media.uri)?.let { storyScaleDown(it, 512) }
    } else {
        storyDecodeSampledBitmap(context.contentResolver, media.uri, 512, 512)
    }

private fun storyLoadPreview(context: Context, media: StoryGalleryMedia): Bitmap? =
    if (media.isVideo) {
        storyVideoFrame(context, media.uri)
    } else {
        storyDecodeSampledBitmap(context.contentResolver, media.uri, 1600, 1600)
    }

private fun storyScaleDown(bitmap: Bitmap, maxSize: Int): Bitmap {
    val width = bitmap.width
    val height = bitmap.height
    if (width <= maxSize && height <= maxSize) return bitmap
    val ratio = maxOf(width, height).toFloat() / maxSize
    val scaledWidth = (width / ratio).toInt().coerceAtLeast(1)
    val scaledHeight = (height / ratio).toInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)
}

private fun storyFormatDuration(durationMs: Long): String =
    if (durationMs <= 0) {
        "0:00"
    } else {
        val seconds = durationMs / 1000
        "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
    }

@Composable
private fun StorySelectStep(
    permissionState: MediaPermissionState,
    items: List<StoryGalleryMedia>,
    selected: StoryGalleryMedia?,
    tab: StoryGalleryTab,
    onTabChange: (StoryGalleryTab) -> Unit,
    onSelect: (StoryGalleryMedia) -> Unit,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    val colors = DenoTheme.colors
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = onBack,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
            ) { Text("Back", color = colors.textSecondary, fontSize = 14.sp) }
            Text(
                "Create Story",
                color = colors.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )
            TextButton(
                onClick = onNext,
                enabled = selected != null,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    "Next",
                    color = if (selected != null) DenoBlue else colors.textSecondary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
        Divider(color = colors.border, thickness = 0.5.dp)
        StoryGalleryTabRow(tab = tab, onTabChange = onTabChange)
        Divider(color = colors.border, thickness = 0.5.dp)

        val visibleItems = items.filter { storyMatchesTab(it, tab) }
        val hasAccess = permissionState == MediaPermissionState.GRANTED ||
            permissionState == MediaPermissionState.PARTIAL
        when {
            !hasAccess -> MediaPermissionPrompt(
                state = permissionState,
                explanation = "Allow access to your photos and videos to create a story.",
                onRequest = onRequestPermission,
                onOpenSettings = onOpenSettings
            )
            items.isEmpty() -> EmptyState(
                Icons.Outlined.PhotoLibrary,
                "No photos or videos",
                "Photos and videos on this device will appear here."
            )
            visibleItems.isEmpty() -> EmptyState(
                if (tab == StoryGalleryTab.VIDEO) Icons.Outlined.Videocam else Icons.Outlined.Image,
                if (tab == StoryGalleryTab.VIDEO) "No videos" else "No photos",
                "No ${if (tab == StoryGalleryTab.VIDEO) "videos" else "photos"} on this device yet."
            )
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(visibleItems, key = { it.uri.toString() }) { item ->
                    StoryGalleryGridItem(
                        media = item,
                        selected = item == selected,
                        onClick = { onSelect(item) }
                    )
                }
            }
        }
    }
}

@Composable
private fun StoryGalleryTabRow(
    tab: StoryGalleryTab,
    onTabChange: (StoryGalleryTab) -> Unit
) {
    val colors = DenoTheme.colors
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        StoryGalleryTabItem("Gallery", tab == StoryGalleryTab.GALLERY, colors) { onTabChange(StoryGalleryTab.GALLERY) }
        StoryGalleryTabItem("Photo", tab == StoryGalleryTab.PHOTO, colors) { onTabChange(StoryGalleryTab.PHOTO) }
        StoryGalleryTabItem("Video", tab == StoryGalleryTab.VIDEO, colors) { onTabChange(StoryGalleryTab.VIDEO) }
    }
}

@Composable
private fun StoryGalleryTabItem(
    label: String,
    selected: Boolean,
    colors: DenoColors,
    onClick: () -> Unit
) {
    Column(
        Modifier
            .height(44.dp)
            .clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            label,
            color = if (selected) colors.textPrimary else colors.textSecondary,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            fontSize = 14.sp
        )
        Spacer(Modifier.height(2.dp))
        Box(
            Modifier
                .width(if (selected) 56.dp else 0.dp)
                .height(2.5.dp)
                .clip(RoundedCornerShape(1.5.dp))
                .background(DenoBlue)
        )
    }
}

@Composable
private fun StoryGalleryGridItem(
    media: StoryGalleryMedia,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(4.dp))
            .clickable { onClick() }
    ) {
        StoryMediaThumb(media, Modifier.fillMaxSize())
        if (selected) {
            Box(
                Modifier
                    .matchParentSize()
                    .background(DenoBlue.copy(alpha = 0.20f))
                    .border(3.dp, DenoBlue, RoundedCornerShape(4.dp))
            )
            Icon(
                Icons.Filled.CheckCircle,
                null,
                tint = Color.White,
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(20.dp)
            )
        }
        if (media.isVideo) {
            Row(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(4.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(10.dp))
                Text(storyFormatDuration(media.durationMs), color = Color.White, fontSize = 9.sp)
            }
        }
    }
}

@Composable
private fun StoryMediaThumb(media: StoryGalleryMedia, modifier: Modifier = Modifier) {
    val colors = DenoTheme.colors
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, media.uri, media.isVideo) {
        value = withContext(Dispatchers.IO) { storyLoadThumbnail(context, media) }
    }
    Box(modifier.background(colors.card), contentAlignment = Alignment.Center) {
        val thumbnail = bitmap
        if (thumbnail != null) {
            Image(
                thumbnail.asImageBitmap(),
                null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Icon(
                if (media.isVideo) Icons.Outlined.PlayCircle else Icons.Outlined.Image,
                null,
                tint = colors.textSecondary,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun StoryPreviewStep(
    media: StoryGalleryMedia?,
    caption: String,
    onCaptionChange: (String) -> Unit,
    onBack: () -> Unit,
    onShare: () -> Unit
) {
    val colors = DenoTheme.colors
    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .imePadding()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Outlined.ArrowBack, null, tint = Color.White)
            }
            Text(
                "Preview",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.width(48.dp))
        }
        Box(
            Modifier.fillMaxWidth().weight(1f),
            contentAlignment = Alignment.Center
        ) {
            StoryPreviewMedia(media)
        }
        Row(
            Modifier
                .fillMaxWidth()
                .background(colors.background)
                .padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = caption,
                onValueChange = onCaptionChange,
                singleLine = true,
                textStyle = TextStyle(color = colors.textPrimary, fontSize = 16.sp),
                cursorBrush = SolidColor(DenoBlue),
                modifier = Modifier.weight(1f),
                decorationBox = { innerTextField ->
                    if (caption.isEmpty()) {
                        Text("Add a caption...", color = colors.textSecondary, fontSize = 16.sp)
                    }
                    innerTextField()
                }
            )
            Spacer(Modifier.width(8.dp))
            TextButton(
                onClick = onShare,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Text("Share to Story", color = DenoBlue, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }
}

@Composable
private fun StoryPreviewMedia(media: StoryGalleryMedia?) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, media?.uri) {
        value = if (media == null) null else withContext(Dispatchers.IO) { storyLoadPreview(context, media) }
    }
    val preview = bitmap
    when {
        media == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Select media to preview", color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp)
        }
        preview != null -> Image(
            preview.asImageBitmap(),
            null,
            modifier = Modifier.fillMaxSize().padding(12.dp),
            contentScale = ContentScale.Fit
        )
        else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                Icons.Outlined.PlayCircle,
                null,
                tint = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.size(64.dp)
            )
        }
    }
}