@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.deno.social.ui.screens

import android.Manifest
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.Image
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.deno.social.data.mock.MockData
import com.deno.social.data.model.Reel
import com.deno.social.data.repository.MockCommentRepository
import com.deno.social.data.repository.MockPostRepository
import com.deno.social.data.repository.MockReelRepository
import com.deno.social.data.repository.SessionManager
import com.deno.social.ui.components.Avatar
import com.deno.social.ui.components.DenoButton
import com.deno.social.ui.components.EmptyState
import com.deno.social.ui.components.MessageComposer
import com.deno.social.ui.components.formatCount
import com.deno.social.ui.components.reelShareLink
import com.deno.social.ui.components.shareContent
import com.deno.social.ui.theme.DenoBlue
import com.deno.social.ui.theme.DenoColors
import com.deno.social.ui.theme.DenoTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "DenoReels"

@Composable
fun ReelsScreen(onProfile: (String) -> Unit, onComments: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { MockReelRepository() }
    var reels by remember { mutableStateOf(repo.getReels()) }
    val blockedIds by SessionManager.blockedIds.collectAsState()
    // Blocked users' reels are hidden from the pager whenever the block list
    // changes (and also on fresh repository reads).
    val visibleReels = remember(reels, blockedIds) { reels.filter { it.user.id !in blockedIds } }
    val pagerState = rememberPagerState(pageCount = { visibleReels.size })

    // Single shared Media3 player serves the currently visible Reel only; no
    // per-Reel players are created. Default volume is 0 (existing default muted).
    // HTTP timeouts are short so an unreachable remote source falls back to the
    // codec-generated local sample quickly instead of stalling behind a spinner.
    val player = remember {
        val httpFactory = DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(5_000)
            .setReadTimeoutMs(10_000)
            .setAllowCrossProtocolRedirects(true)
        ExoPlayer.Builder(context, DefaultMediaSourceFactory(context).setDataSourceFactory(httpFactory))
            .build()
            .apply {
                repeatMode = Player.REPEAT_MODE_ALL // restart at end, never advance to next reel
                volume = 0f
            }
    }
    // Release the player + media resources when the Reels screen leaves composition.
    DisposableEffect(player) {
        onDispose {
            player.stop()
            player.release()
        }
    }

    // Hoisted playback/UI state (rendered by the current page's item only).
    var muted by remember { mutableStateOf(true) } // keep current default: muted
    var isBuffering by remember { mutableStateOf(true) }
    var playbackError by remember { mutableStateOf(false) }
    var activeReelId by remember { mutableStateOf<String?>(null) }
    // Once a remote Reel fails to load, its id is recorded and the codec-generated
    // local sample plays instead, so actual playback is always visible offline.
    var fallbackReelId by remember { mutableStateOf<String?>(null) }
    var localVideoFile by remember { mutableStateOf<File?>(null) }

    val currentReel = visibleReels.getOrNull(pagerState.currentPage)
    // Keep listener closures fresh without re-registering the listener.
    val currentReelState by rememberUpdatedState(currentReel)
    val localFileState by rememberUpdatedState(localVideoFile)

    // Generate (once) a real, codec-valid local MP4 for offline playback.
    LaunchedEffect(context) {
        localVideoFile = withContext(Dispatchers.IO) { LocalReelVideoGenerator.ensure(context) }
    }

    // Real player volume follows the existing mute icon state.
    LaunchedEffect(muted, player) { player.volume = if (muted) 0f else 1f }

    // Loading / loop-restart / error observation on the shared player.
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                // Backstop for end-of-video: restart from 0 and keep playing.
                if (playbackState == Player.STATE_ENDED) {
                    player.seekTo(0)
                    player.playWhenReady = true
                }
            }
            override fun onPlayerError(error: PlaybackException) {
                // Log-safe diagnostics: exact error code/type/cause for root cause.
                Log.e(TAG, "Reel playback error: code=${error.errorCodeName} " +
                    "cause=${error.cause?.javaClass?.simpleName} msg=${error.message}")
                val reelId = currentReelState?.id
                if (reelId != null && fallbackReelId != reelId && localFileState != null) {
                    // Remote source unreachable -> transparently use local sample.
                    fallbackReelId = reelId
                    isBuffering = true
                } else {
                    playbackError = true
                    isBuffering = false
                }
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    // Pause playback when the app is backgrounded; resume on return if it was playing.
    val lifecycleOwner = LocalLifecycleOwner.current
    var wasPlaying by remember { mutableStateOf(false) }
    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    wasPlaying = player.playWhenReady
                    player.playWhenReady = false
                }
                Lifecycle.Event.ON_START -> player.playWhenReady = wasPlaying
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // When the visible Reel changes: release the previous Reel's media, prepare
    // + auto-play the newly visible Reel. The remote Reel.videoUrl is tried
    // first; reels that previously errored fall back to the local sample.
    LaunchedEffect(currentReel?.id, localVideoFile, fallbackReelId, player) {
        activeReelId = null
        val reel = currentReel
        if (reel == null) {
            player.stop()
            return@LaunchedEffect
        }
        playbackError = false
        isBuffering = true
        val localUri = localVideoFile?.let { Uri.fromFile(it) }
        val uri = if (fallbackReelId == reel.id && localUri != null) localUri else Uri.parse(reel.videoUrl)
        player.stop()
        player.setMediaItem(MediaItem.fromUri(uri))
        player.volume = if (muted) 0f else 1f
        player.prepare()
        player.playWhenReady = true
        activeReelId = reel.id
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        VerticalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            val reel = visibleReels[page]
            ReelItem(
                reel = reel,
                player = player,
                videoReady = activeReelId == reel.id,
                isPlaying = pagerState.currentPage == page,
                isBuffering = isBuffering,
                playbackError = playbackError,
                muted = muted,
                onMuteToggle = { muted = !muted },
                onLike = {
                    repo.toggleLike(reel.id)
                    reels = repo.getReels()
                },
                onComment = onComments,
                onProfile = { onProfile(reel.user.id) }
            )
        }
    }
}

@Composable
fun ReelItem(
    reel: Reel,
    player: Player,
    videoReady: Boolean,
    isPlaying: Boolean,
    isBuffering: Boolean,
    playbackError: Boolean,
    muted: Boolean,
    onMuteToggle: () -> Unit,
    onLike: () -> Unit,
    onComment: () -> Unit,
    onProfile: () -> Unit
) {
    val context = LocalContext.current
    var isLiked by remember(reel.id) { mutableStateOf(reel.isLiked) }
    var likes by remember(reel.id) { mutableStateOf(reel.likes) }
    var showHeart by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (showHeart) 1.3f else 0f, label = "heart")
    val savedIds by SessionManager.savedReelIds.collectAsState()
    val privacy by SessionManager.contentPrivacy.collectAsState()
    val isSaved = reel.id in savedIds
    // Existing SessionManager follow system (no new state/repository): reactive
    // Follow/Following button next to the username on non-own reels.
    val followingIds by SessionManager.followingIds.collectAsState()
    val isOwnReel = reel.user.isCurrentUser || reel.user.id == MockData.currentUser.id
    val isBlockedCreator = SessionManager.isBlocked(reel.user.id)
    val isFollowingCreator = reel.user.id in followingIds

    BoxWithConstraints(
        Modifier.fillMaxSize().background(Color(0xFF111111)).pointerInput(isPlaying, player) {
            detectTapGestures(
                // Tap video -> pause/play (reuses the existing tap handler; double
                // tap below still means like).
                onTap = { if (isPlaying) player.playWhenReady = !player.playWhenReady },
                onDoubleTap = {
                    if (!isLiked) { isLiked = true; likes++; onLike() }
                    showHeart = true
                }
            )
        }
    ) {
        val contentH = maxHeight
        // Real video via Media3 ExoPlayer — the shared player is attached only to
        // the currently visible Reel (center-crop, Media3 controls hidden).
        if (isPlaying && videoReady && !playbackError) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { PlayerView(it).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                } },
                update = { it.player = player }
            )
        }
        // Compact loading indicator while the video prepares/buffers.
        if (isPlaying && isBuffering && !playbackError) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center).size(36.dp),
                color = Color.White,
                strokeWidth = 3.dp
            )
        }
        // Compact playback-error state; Reel metadata/actions stay visible.
        if (isPlaying && playbackError) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.Warning, null, tint = Color.White.copy(0.7f), modifier = Modifier.size(32.dp))
                Spacer(Modifier.height(6.dp))
                Text("Video unavailable", color = Color.White.copy(0.7f), fontSize = 13.sp)
            }
        }
        // Mute button — controls the real player volume via onMuteToggle.
        Icon(
            if (muted) Icons.Outlined.VolumeOff else Icons.Outlined.VolumeUp,
            null, tint = Color.White,
            modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 14.dp, end = 16.dp).size(28.dp)
                .clickable { onMuteToggle() }
        )

        if (showHeart) {
            Icon(Icons.Filled.Favorite, null, tint = Color.White,
                modifier = Modifier.align(Alignment.Center).size(90.dp).scale(scale))
            LaunchedEffect(Unit) { delay(600); showHeart = false }
        }

        // Right actions - middle/lower area
        Column(
            Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .padding(top = contentH * 0.40f, bottom = contentH * 0.10f, end = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        null, tint = if (isLiked) Color.Red else Color.White,
                        modifier = Modifier.size(30.dp).clickable {
                            isLiked = !isLiked; likes += if (isLiked) 1 else -1; onLike()
                        }
                    )
                    if (privacy.showLikeCounts) Text(formatCount(likes), color = Color.White, fontSize = 12.sp)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.ChatBubbleOutline, null, tint = Color.White,
                        modifier = Modifier.size(28.dp).clickable { onComment() })
                    if (privacy.showCommentCounts) Text(formatCount(reel.comments), color = Color.White, fontSize = 12.sp)
                }
                Icon(Icons.Outlined.Share, null, tint = Color.White, modifier = Modifier.size(26.dp).clickable {
                    shareContent(context, "${reel.caption}\n${reelShareLink(reel.id)}")
                })
                Icon(
                    if (isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                    null, tint = Color.White,
                    modifier = Modifier.size(26.dp).clickable { SessionManager.toggleSavedReel(reel.id) }
                )
                Icon(Icons.Default.MoreVert, null, tint = Color.White, modifier = Modifier.size(26.dp))
        }

        // Caption with avatar, bottom-left
        Column(Modifier.align(Alignment.BottomStart).padding(16.dp, 0.dp, 100.dp, 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(reel.user.avatarInitial, 44) { onProfile() }
                Spacer(Modifier.width(10.dp))
                Text("@${reel.user.username}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                if (!isOwnReel && !isBlockedCreator) {
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (isFollowingCreator) SessionManager.unfollowUser(reel.user.id)
                            else SessionManager.followUser(reel.user.id)
                        },
                        modifier = Modifier.height(26.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isFollowingCreator) Color.White.copy(alpha = 0.25f) else Color.White,
                            contentColor = if (isFollowingCreator) Color.White else Color.Black
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                    ) {
                        Text(
                            if (isFollowingCreator) "Following" else "Follow",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                reel.caption,
                color = Color.White.copy(0.9f),
                fontSize = 14.sp,
                modifier = Modifier.padding(start = 54.dp)
            )
        }
    }
}

@Composable
fun CreatePostScreen(onBack: () -> Unit, onPosted: () -> Unit) {
    val colors = DenoTheme.colors
    val repo = remember { MockPostRepository() }
    var text by remember { mutableStateOf("") }
    var media by remember { mutableStateOf<List<GalleryMedia>>(emptyList()) }
    var picker by remember { mutableStateOf<GalleryTab?>(null) }
    var scheduleDialog by remember { mutableStateOf(false) }

    val pickerType = picker
    if (pickerType != null) {
        PostMediaPicker(
            type = pickerType,
            selected = media,
            onToggle = { item ->
                media = if (media.any { it.uri == item.uri }) {
                    media.filterNot { it.uri == item.uri }
                } else {
                    media + item
                }
            },
            onDone = { picker = null },
            onCancel = { picker = null }
        )
        return
    }

    val canPost = text.isNotBlank() || media.isNotEmpty()
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .imePadding()
    ) {
        Row(
            Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = onBack,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) { Text("Cancel", color = colors.textSecondary, fontSize = 14.sp) }
            Text(
                "Create Post",
                color = colors.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                modifier = Modifier.weight(2f),
                textAlign = TextAlign.Center
            )
            TextButton(
                onClick = {
                    repo.addPost(text)
                    onPosted()
                },
                enabled = canPost,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    "Post",
                    color = if (canPost) DenoBlue else colors.textSecondary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
        Divider(color = colors.border, thickness = 0.5.dp)

        Row(
            Modifier.fillMaxWidth().weight(1f).padding(start = 16.dp, end = 16.dp, top = 16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Avatar("A", 44)
            Spacer(Modifier.width(12.dp))
            Box(Modifier.weight(1f).fillMaxHeight()) {
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    textStyle = TextStyle(color = colors.textPrimary, fontSize = 16.sp, lineHeight = 22.sp),
                    cursorBrush = SolidColor(DenoBlue),
                    modifier = Modifier.fillMaxSize(),
                    decorationBox = { innerTextField ->
                        if (text.isEmpty()) {
                            Text(
                                "What's on your mind?",
                                color = colors.textSecondary,
                                fontSize = 16.sp,
                                lineHeight = 22.sp
                            )
                        }
                        innerTextField()
                    }
                )
            }
        }

        if (media.isNotEmpty()) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 240.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(media, key = { it.uri.toString() }) { item ->
                    AddToPostPreview(item) {
                        media = media.filterNot { it.uri == item.uri }
                    }
                }
            }
            Divider(color = colors.border, thickness = 0.5.dp)
        }

        Column(Modifier.navigationBarsPadding().padding(vertical = 4.dp)) {
            AddToPostRow(
                icon = Icons.Outlined.Image,
                label = "Photo",
                trailingCount = media.count { !it.isVideo },
                onClick = { picker = GalleryTab.PHOTO }
            )
            AddToPostRow(
                icon = Icons.Outlined.Videocam,
                label = "Video",
                trailingCount = media.count { it.isVideo },
                onClick = { picker = GalleryTab.VIDEO }
            )
            AddToPostRow(icon = Icons.Outlined.LocationOn, label = "Location", onClick = {})
            AddToPostRow(icon = Icons.Outlined.Tag, label = "Tag People", onClick = {})
            AddToPostRow(icon = Icons.Outlined.Public, label = "Audience", onClick = {})
            AddToPostRow(icon = Icons.Outlined.ChatBubbleOutline, label = "Comments", onClick = {})
            AddToPostRow(icon = Icons.Outlined.Drafts, label = "Save Draft", onClick = {
                if (canPost) SessionManager.addDraft(text, media.size)
                onBack()
            })
            AddToPostRow(icon = Icons.Outlined.Schedule, label = "Schedule", onClick = {
                if (canPost) scheduleDialog = true
            })
        }
    }

    if (scheduleDialog) {
        AlertDialog(
            onDismissRequest = { scheduleDialog = false },
            containerColor = colors.surface,
            title = { Text("Schedule post", color = colors.textPrimary) },
            text = {
                Column {
                    listOf(
                        "In 1 hour" to 3600_000L,
                        "In 3 hours" to 3 * 3600_000L,
                        "In 6 hours" to 6 * 3600_000L,
                        "In 1 day" to 24 * 3600_000L
                    ).forEach { (label, offset) ->
                        Text(
                            label,
                            color = DenoBlue,
                            fontSize = 15.sp,
                            modifier = Modifier.fillMaxWidth().clickable {
                                SessionManager.addScheduledPost(text, media.size, System.currentTimeMillis() + offset)
                                scheduleDialog = false
                                onBack()
                            }.padding(vertical = 12.dp)
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { scheduleDialog = false }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            }
        )
    }
}

@Composable
private fun AddToPostRow(
    icon: ImageVector,
    label: String,
    trailingCount: Int = 0,
    onClick: () -> Unit
) {
    val colors = DenoTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = DenoBlue, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, color = colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.weight(1f))
        if (trailingCount > 0) {
            Text(
                trailingCount.toString(),
                color = DenoBlue,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun AddToPostPreview(media: GalleryMedia, onRemove: () -> Unit) {
    Box(
        Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(4.dp))
            .clickable { onRemove() }
    ) {
        MediaThumb(media, Modifier.fillMaxSize())
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(18.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Close, null, tint = Color.White, modifier = Modifier.size(12.dp))
        }
    }
}

@Composable
private fun PostMediaPicker(
    type: GalleryTab,
    selected: List<GalleryMedia>,
    onToggle: (GalleryMedia) -> Unit,
    onDone: () -> Unit,
    onCancel: () -> Unit
) {
    val colors = DenoTheme.colors
    val context = LocalContext.current
    val mediaType = if (type == GalleryTab.PHOTO) MediaType.PHOTO else MediaType.VIDEO
    val neededTypes = setOf(mediaType)
    val permissionState = MediaPermission.accessState(context, neededTypes)
    val hasAccess = permissionState == MediaPermissionState.GRANTED ||
        permissionState == MediaPermissionState.PARTIAL

    var reloadKey by remember(type) { mutableStateOf(0) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        reloadKey++
    }

    val items by produceState(initialValue = emptyList<GalleryMedia>(), reloadKey, permissionState) {
        value = if (MediaPermission.hasAccess(context, neededTypes)) {
            withContext(Dispatchers.IO) { queryGallery(context.contentResolver, neededTypes) }
        } else {
            emptyList()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        Row(
            Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) { Text("Cancel", color = colors.textSecondary, fontSize = 14.sp) }
            Text(
                if (type == GalleryTab.PHOTO) "Add Photo" else "Add Video",
                color = colors.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                modifier = Modifier.weight(2f),
                textAlign = TextAlign.Center
            )
            TextButton(
                onClick = onDone,
                enabled = selected.isNotEmpty(),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    "Done",
                    color = if (selected.isNotEmpty()) DenoBlue else colors.textSecondary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
        Divider(color = colors.border, thickness = 0.5.dp)
        when {
            !hasAccess -> MediaPermissionPrompt(
                state = permissionState,
                explanation = if (type == GalleryTab.PHOTO)
                    "Allow access to your photos so they can be added to your post."
                else
                    "Allow access to your videos so they can be added to your post.",
                onRequest = {
                    MediaPermission.markRequestStarted(context, MediaPermission.permissionRequest(neededTypes))
                    permissionLauncher.launch(MediaPermission.permissionRequest(neededTypes))
                },
                onOpenSettings = { MediaPermission.openSettings(context) }
            )
            items.isEmpty() -> EmptyState(
                Icons.Outlined.Image,
                if (type == GalleryTab.PHOTO) "No photos" else "No videos",
                if (type == GalleryTab.PHOTO)
                    "Photos on this device will appear here."
                else
                    "Videos on this device will appear here."
            )
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(items, key = { it.uri.toString() }) { item ->
                    val order = selected.indexOfFirst { it.uri == item.uri }
                    GalleryGridItem(
                        media = item,
                        selected = order >= 0,
                        selectionOrder = if (order >= 0) order + 1 else null,
                        onClick = { onToggle(item) }
                    )
                }
            }
        }
    }
}

@Composable
fun CreateReelScreen(onBack: () -> Unit, onPublished: () -> Unit) {
    val context = LocalContext.current
    var step by remember { mutableStateOf(0) }
    var tab by remember { mutableStateOf(GalleryTab.GALLERY) }
    var selected by remember { mutableStateOf<GalleryMedia?>(null) }
    var caption by remember { mutableStateOf("") }
    var reloadKey by remember { mutableStateOf(0) }

    val neededTypes = setOf(MediaType.PHOTO, MediaType.VIDEO)
    val permissionState = MediaPermission.accessState(context, neededTypes)

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        reloadKey++
    }

    val items by produceState(initialValue = emptyList<GalleryMedia>(), reloadKey, permissionState) {
        value = if (MediaPermission.hasAccess(context, neededTypes)) {
            withContext(Dispatchers.IO) { queryGallery(context.contentResolver) }
        } else {
            emptyList()
        }
    }

    when (step) {
        0 -> SelectMediaStep(
            permissionState = permissionState,
            items = items,
            selected = selected,
            tab = tab,
            onTabChange = { newTab ->
                tab = newTab
                if (selected != null && !matchesTab(selected!!, newTab)) selected = null
            },
            onSelect = { item -> selected = if (selected == item) null else item },
            onRequestPermission = {
                MediaPermission.markRequestStarted(context, MediaPermission.permissionRequest(neededTypes))
                permissionLauncher.launch(MediaPermission.permissionRequest(neededTypes))
            },
            onOpenSettings = { MediaPermission.openSettings(context) },
            onNext = { step = 1 },
            onCancel = onBack
        )
        else -> PreviewStep(
            media = selected,
            caption = caption,
            onCaptionChange = { caption = it },
            onBack = { step = 0 },
            onPost = onPublished
        )
    }
}

private data class GalleryMedia(
    val uri: Uri,
    val isVideo: Boolean,
    val durationMs: Long,
    val dateAdded: Long
)

private enum class GalleryTab { GALLERY, PHOTO, VIDEO }

private fun matchesTab(media: GalleryMedia, tab: GalleryTab): Boolean =
    when (tab) {
        GalleryTab.GALLERY -> true
        GalleryTab.PHOTO -> !media.isVideo
        GalleryTab.VIDEO -> media.isVideo
    }

private fun queryGallery(
    contentResolver: ContentResolver,
    types: Set<MediaType> = setOf(MediaType.PHOTO, MediaType.VIDEO)
): List<GalleryMedia> {
    val result = mutableListOf<GalleryMedia>()

    if (MediaType.PHOTO in types) {
        try {
            val imageProjection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DATE_ADDED
            )
            contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                imageProjection,
                null,
                null,
                "${MediaStore.Images.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val dateIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                while (cursor.moveToNext()) {
                    result += GalleryMedia(
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
    }

    if (MediaType.VIDEO in types) {
        try {
            val videoProjection = arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DATE_ADDED,
                MediaStore.Video.Media.DURATION
            )
            contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                videoProjection,
                null,
                null,
                "${MediaStore.Video.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val dateIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                val durationIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                while (cursor.moveToNext()) {
                    result += GalleryMedia(
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
    }

    return result.sortedByDescending { it.dateAdded }
}

private fun decodeSampledBitmap(resolver: ContentResolver, uri: Uri, reqWidth: Int, reqHeight: Int): Bitmap? {
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

private fun videoFrame(context: Context, uri: Uri): Bitmap? {
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

private fun loadThumbnail(context: Context, media: GalleryMedia): Bitmap? =
    if (media.isVideo) {
        videoFrame(context, media.uri)?.let { scaleDown(it, 512) }
    } else {
        decodeSampledBitmap(context.contentResolver, media.uri, 512, 512)
    }

private fun loadPreview(context: Context, media: GalleryMedia): Bitmap? =
    if (media.isVideo) {
        videoFrame(context, media.uri)
    } else {
        decodeSampledBitmap(context.contentResolver, media.uri, 1600, 1600)
    }

private fun scaleDown(bitmap: Bitmap, maxSize: Int): Bitmap {
    val width = bitmap.width
    val height = bitmap.height
    if (width <= maxSize && height <= maxSize) return bitmap
    val ratio = maxOf(width, height).toFloat() / maxSize
    val scaledWidth = (width / ratio).toInt().coerceAtLeast(1)
    val scaledHeight = (height / ratio).toInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)
}

private fun formatDuration(durationMs: Long): String =
    if (durationMs <= 0) {
        "0:00"
    } else {
        val seconds = durationMs / 1000
        "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
    }

@Composable
private fun SelectMediaStep(
    permissionState: MediaPermissionState,
    items: List<GalleryMedia>,
    selected: GalleryMedia?,
    tab: GalleryTab,
    onTabChange: (GalleryTab) -> Unit,
    onSelect: (GalleryMedia) -> Unit,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onNext: () -> Unit,
    onCancel: () -> Unit
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
                onClick = onCancel,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
            ) { Text("Cancel", color = colors.textSecondary, fontSize = 14.sp) }
            Text(
                "Create Reel",
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
        GalleryTabRow(tab = tab, onTabChange = onTabChange)
        Divider(color = colors.border, thickness = 0.5.dp)

        val visibleItems = items.filter { matchesTab(it, tab) }
        when {
            permissionState == MediaPermissionState.REQUESTABLE ||
                permissionState == MediaPermissionState.PERMANENTLY_DENIED ->
                MediaPermissionPrompt(
                    state = permissionState,
                    explanation = "Allow access to your photos and videos so they can be used in your reel.",
                    onRequest = onRequestPermission,
                    onOpenSettings = onOpenSettings
                )
            items.isEmpty() -> EmptyState(
                Icons.Outlined.Image,
                "No photos or videos",
                "Photos and videos on this device will appear here."
            )
            visibleItems.isEmpty() -> EmptyState(
                if (tab == GalleryTab.VIDEO) Icons.Outlined.Videocam else Icons.Outlined.Image,
                if (tab == GalleryTab.VIDEO) "No videos" else "No photos",
                "No ${if (tab == GalleryTab.VIDEO) "videos" else "photos"} on this device yet."
            )
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(visibleItems, key = { it.uri.toString() }) { item ->
                    GalleryGridItem(
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
private fun GalleryTabRow(
    tab: GalleryTab,
    onTabChange: (GalleryTab) -> Unit
) {
    val colors = DenoTheme.colors
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        GalleryTabItem("Gallery", tab == GalleryTab.GALLERY, colors) { onTabChange(GalleryTab.GALLERY) }
        GalleryTabItem("Photo", tab == GalleryTab.PHOTO, colors) { onTabChange(GalleryTab.PHOTO) }
        GalleryTabItem("Video", tab == GalleryTab.VIDEO, colors) { onTabChange(GalleryTab.VIDEO) }
    }
}

@Composable
private fun GalleryTabItem(
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
private fun GalleryGridItem(
    media: GalleryMedia,
    selected: Boolean,
    selectionOrder: Int? = null,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(4.dp))
            .clickable { onClick() }
    ) {
        MediaThumb(media, Modifier.fillMaxSize())
        if (selected) {
            Box(
                Modifier
                    .matchParentSize()
                    .background(DenoBlue.copy(alpha = 0.20f))
                    .border(3.dp, DenoBlue, RoundedCornerShape(4.dp))
            )
            val order = selectionOrder
            if (order != null) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(DenoBlue),
                    contentAlignment = Alignment.Center
                ) {
                    Text(order.toString(), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                Icon(
                    Icons.Filled.CheckCircle,
                    null,
                    tint = Color.White,
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(20.dp)
                )
            }
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
                Text(formatDuration(media.durationMs), color = Color.White, fontSize = 9.sp)
            }
        }
    }
}

@Composable
private fun MediaThumb(media: GalleryMedia, modifier: Modifier = Modifier) {
    val colors = DenoTheme.colors
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, media.uri, media.isVideo) {
        value = withContext(Dispatchers.IO) { loadThumbnail(context, media) }
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
private fun PreviewStep(
    media: GalleryMedia?,
    caption: String,
    onCaptionChange: (String) -> Unit,
    onBack: () -> Unit,
    onPost: () -> Unit
) {
    val colors = DenoTheme.colors
    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .imePadding()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, null, tint = Color.White)
            }
            Text(
                "Create Reel",
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
            PreviewMedia(media)
        }
        Row(
            Modifier
                .fillMaxWidth()
                .background(colors.background)
                .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
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
                onClick = onPost,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text("Post Reel", color = DenoBlue, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }
}

@Composable
private fun PreviewMedia(media: GalleryMedia?) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, media?.uri) {
        value = if (media == null) null else withContext(Dispatchers.IO) { loadPreview(context, media) }
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
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    if (media.isVideo) Icons.Outlined.PlayCircle else Icons.Outlined.Image,
                    null,
                    tint = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(64.dp)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    if (media.isVideo) "Video preview" else "Image preview",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 13.sp
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentsScreen(onBack: () -> Unit) {
    val colors = DenoTheme.colors
    val repo = remember { MockCommentRepository() }
    var comments by remember { mutableStateOf(repo.getComments()) }
    var text by remember { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(8.dp, 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, null, tint = colors.textPrimary)
            }
            Text("Comments", color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
        Divider(color = colors.border)
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            items(comments.size) { i ->
                val c = comments[i]
                Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.Top) {
                    Avatar(c.user.avatarInitial, 36)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Row {
                            Text(c.user.username, color = colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Spacer(Modifier.width(8.dp))
                            Text(c.timeAgo, color = colors.textSecondary, fontSize = 12.sp)
                        }
                        Text(c.text, color = colors.textPrimary, fontSize = 14.sp)
                    }
                }
            }
        }
        Divider(color = colors.border)
        MessageComposer(
            value = text,
            onValueChange = { text = it },
            onSend = {
                if (text.isNotBlank()) {
                    repo.addComment(text)
                    comments = repo.getComments()
                    text = ""
                }
            },
            placeholder = "Add a comment..."
        )
    }
}
// One-time offline test source: generates a real, codec-valid MP4 (H.264 +
// MP4 muxed by the Android platform's MediaCodec/MediaMuxer) so Reels playback
// stays visible even when the mock remote Reel.videoUrl hosts are unreachable
// (e.g., no network). It is used only as an automatic fallback — the remote
// Reel.videoUrl remains the authoritative source and still works online; the
// Reel model/videoUrl architecture is unchanged for future backend URLs.
private object LocalReelVideoGenerator {
    private const val WIDTH = 320
    private const val HEIGHT = 320
    private const val FRAME_RATE = 24
    private const val FRAME_COUNT = 48 // ~2s so end-of-video looping is visible
    private val lock = Any()

    fun ensure(context: Context): File? {
        val file = File(context.cacheDir, "deno_reel_sample.mp4")
        synchronized(lock) {
            if (file.exists() && file.length() > 0L) return file
            return runCatching { generate(file) }
                .onFailure { Log.e(TAG, "Local sample generation failed", it) }
                .getOrNull()
                ?.takeIf { it.exists() && it.length() > 0L }
        }
    }

    private fun generate(file: File): File {
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, WIDTH, HEIGHT).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            setInteger(MediaFormat.KEY_BIT_RATE, 500_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        try {
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        } catch (t: Throwable) {
            codec.release()
            throw t
        }
        codec.start()
        val muxer = MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val info = MediaCodec.BufferInfo()
        var trackIndex = -1
        var muxerStarted = false
        var frameIndex = 0
        var producing = true
        var eosReceived = false
        try {
            while (!eosReceived) {
                if (producing) {
                    val inIdx = codec.dequeueInputBuffer(10_000)
                    if (inIdx >= 0) {
                        if (frameIndex >= FRAME_COUNT) {
                            codec.queueInputBuffer(
                                inIdx, 0, 0, frameIndex * 1_000_000L / FRAME_RATE,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            producing = false
                        } else {
                            val image = codec.getInputImage(inIdx) ?: continue
                            fillFrame(image, frameIndex)
                            codec.queueInputBuffer(inIdx, 0, 0, frameIndex * 1_000_000L / FRAME_RATE, 0)
                            frameIndex++
                        }
                    }
                }
                val outIdx = codec.dequeueOutputBuffer(info, 10_000)
                if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    trackIndex = muxer.addTrack(codec.outputFormat)
                    muxer.start()
                    muxerStarted = true
                } else if (outIdx >= 0) {
                    val buf = codec.getOutputBuffer(outIdx)
                    if (buf != null && info.size > 0 && muxerStarted) {
                        muxer.writeSampleData(trackIndex, buf, info)
                    }
                    codec.releaseOutputBuffer(outIdx, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) eosReceived = true
                }
            }
        } finally {
            codec.stop()
            codec.release()
            if (muxerStarted) muxer.stop()
            muxer.release()
        }
        return file
    }

    // Moving bright bar on dark-gray field so "real video frames" are obvious.
    private fun fillFrame(image: Image, frameIndex: Int) {
        val w = image.width
        val h = image.height
        val barWidth = Math.max(16, w / 8)
        val barStart = ((frameIndex * (w + barWidth)) / FRAME_COUNT) - barWidth
        val yPlane = image.planes[0]
        val yBuf = yPlane.buffer
        for (row in 0 until h) {
            val base = row * yPlane.rowStride
            for (col in 0 until w) {
                val inBar = col >= barStart && col < barStart + barWidth
                yBuf.put(base + col, if (inBar) 220.toByte() else 90.toByte())
            }
        }
        for (p in 1 until image.planes.size) {
            val plane = image.planes[p]
            val buf = plane.buffer
            for (row in 0 until (h / 2)) {
                val base = row * plane.rowStride
                for (col in 0 until (w / 2)) {
                    buf.put(base + col, 128.toByte())
                }
            }
        }
    }
}
