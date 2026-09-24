package com.deno.social.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.deno.social.data.model.Post
import com.deno.social.data.repository.MockPostRepository
import com.deno.social.data.repository.SessionManager
import com.deno.social.R
import com.deno.social.ui.components.Avatar
import com.deno.social.ui.components.formatCount
import com.deno.social.ui.components.postShareLink
import com.deno.social.ui.components.shareContent
import com.deno.social.ui.theme.DenoBlue
import com.deno.social.ui.theme.DenoTheme
import kotlinx.coroutines.flow.distinctUntilChanged

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onSearch: () -> Unit,
    onNotifications: () -> Unit,
    onCreate: () -> Unit,
    onCreateReel: () -> Unit = {},
    onCreateStory: () -> Unit = {},
    onComments: (String) -> Unit,
    onProfile: (String) -> Unit,
    onChat: () -> Unit = {},
    onHomeScrollStateChanged: (Boolean) -> Unit = {}
) {
    val colors = DenoTheme.colors
    val repo = remember { MockPostRepository() }
    var posts by remember { mutableStateOf(repo.getPosts()) }
    val blockedIds by SessionManager.blockedIds.collectAsState()
    // Blocked users' posts are hidden from the feed whenever the block list
    // changes (and also on fresh repository reads).
    val visiblePosts = remember(posts, blockedIds) { posts.filter { it.user.id !in blockedIds } }
    var showCreateMenu by remember { mutableStateOf(false) }
    var uploadMessage by remember { mutableStateOf("") }

    val feedState = rememberLazyListState()
    LaunchedEffect(feedState) {
        snapshotFlow { feedState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { scrolling -> onHomeScrollStateChanged(scrolling) }
    }

    Box(Modifier.fillMaxSize().background(colors.background)) {
        LazyColumn(
            state = feedState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(0.dp, 0.dp, 0.dp, 16.dp)
        ) {
            item(key = "top_section") {
                Column {
                    // Top bar - compact and natural below status bar
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.home_logo),
                            contentDescription = "Logo",
                            modifier = Modifier.height(28.dp),
                            contentScale = ContentScale.Fit
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            // Plus Create icon button next to search
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(DenoBlue.copy(alpha = 0.12f))
                                    .clickable { showCreateMenu = true },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Add, "Create", tint = DenoBlue, modifier = Modifier.size(20.dp))
                            }
                            Icon(Icons.Outlined.Search, "Search", tint = colors.textPrimary,
                                modifier = Modifier.size(26.dp).clickable { onSearch() })
                            Icon(Icons.Outlined.ChatBubbleOutline, "Chat", tint = colors.textPrimary,
                                modifier = Modifier.size(26.dp).clickable { onChat() })
                            Icon(Icons.Outlined.Notifications, "Notifications", tint = colors.textPrimary,
                                modifier = Modifier.size(26.dp).clickable { onNotifications() })
                        }
                    }

                    Divider(color = colors.border, thickness = 0.5.dp)

                    // Swipeable Stories Row
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                    ) {
                        StoriesRow(
                            onAddStory = {
                                uploadMessage = "Story upload system opened!"
                                onCreate()
                            },
                            onStoryClick = { id ->
                                onProfile(id)
                            }
                        )
                    }

                    Divider(color = colors.border, thickness = 0.5.dp)
                }
            }
            items(visiblePosts, key = { it.id }) { post ->
                PostCard(
                    post = post,
                    onLike = {
                        repo.toggleLike(post.id)
                        posts = repo.getPosts()
                    },
                    onComment = { onComments(post.id) },
                    onProfile = { onProfile(post.user.id) }
                )
            }
        }

        // Facebook-style small popup dialog ("chota dabba") for Create (Post, Reel, Story) with upload system
        if (showCreateMenu) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .clickable { showCreateMenu = false },
                contentAlignment = Alignment.TopEnd
            ) {
                Surface(
                    modifier = Modifier
                        .padding(top = 60.dp, end = 16.dp)
                        .width(220.dp)
                        .clip(RoundedCornerShape(16.dp)),
                    color = colors.card,
                    shadowElevation = 8.dp
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            "Create New",
                            color = colors.textSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                        Divider(color = colors.border, thickness = 0.5.dp)
                        
                        DropdownMenuItem(
                            text = { Text("Create Post", color = colors.textPrimary, fontWeight = FontWeight.Medium) },
                            leadingIcon = { Icon(Icons.Outlined.Article, null, tint = DenoBlue, modifier = Modifier.size(20.dp)) },
                            onClick = {
                                showCreateMenu = false
                                onCreate()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Create Reel", color = colors.textPrimary, fontWeight = FontWeight.Medium) },
                            leadingIcon = { Icon(Icons.Outlined.PlayCircle, null, tint = DenoBlue, modifier = Modifier.size(20.dp)) },
                            onClick = {
                                showCreateMenu = false
                                onCreateReel()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Add Story", color = colors.textPrimary, fontWeight = FontWeight.Medium) },
                            leadingIcon = { Icon(Icons.Outlined.AddPhotoAlternate, null, tint = DenoBlue, modifier = Modifier.size(20.dp)) },
                            onClick = {
                                showCreateMenu = false
                                onCreateStory()
                            }
                        )
                    }
                }
            }
        }

        if (uploadMessage.isNotEmpty()) {
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                action = {
                    TextButton(onClick = { uploadMessage = "" }) {
                        Text("OK", color = Color.White)
                    }
                }
            ) {
                Text(uploadMessage)
            }
        }
    }
}

@Composable
fun PostCard(post: Post, onLike: () -> Unit, onComment: () -> Unit, onProfile: () -> Unit) {
    val colors = DenoTheme.colors
    val context = LocalContext.current
    var showMore by remember { mutableStateOf(false) }
    // Bookmark state + privacy counts come from the shared persisted state so
    // the Feed and the Saved screen stay in sync and restarts preserve saves.
    val savedIds by SessionManager.savedPostIds.collectAsState()
    val privacy by SessionManager.contentPrivacy.collectAsState()
    val isSaved = post.id in savedIds

    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp, 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Avatar(post.user.avatarInitial, 42) { onProfile() }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(post.user.fullName, color = colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text("@${post.user.username} · ${post.timeAgo}", color = colors.textSecondary, fontSize = 12.sp)
            }
            Box {
                Icon(Icons.Default.MoreVert, null, tint = colors.textSecondary,
                    modifier = Modifier.size(24.dp).clickable { showMore = true })
                if (showMore) {
                    Popup(
                        popupPositionProvider = PostMenuPositionProvider,
                        onDismissRequest = { showMore = false },
                        properties = PopupProperties(focusable = true)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.White,
                            shadowElevation = 3.dp,
                            modifier = Modifier.width(140.dp)
                        ) {
                            Column {
                                listOf("Report", "Block user", "Hide", "Copy link", "Share")
                                    .forEach { item ->
                                        Text(
                                            item,
                                            color = Color(0xFF111111),
                                            fontSize = 13.sp,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    showMore = false
                                                    if (item == "Share") {
                                                        shareContent(context, "${post.text}\n${postShareLink(post.id)}")
                                                    }
                                                }
                                                .padding(horizontal = 12.dp, vertical = 10.dp)
                                        )
                                    }
                            }
                        }
                    }
                }
            }
        }

        Text(post.text, color = colors.textPrimary, fontSize = 15.sp,
            modifier = Modifier.padding(horizontal = 16.dp))

        if (post.hasMedia) {
            Spacer(Modifier.height(10.dp))
            var showHeart by remember { mutableStateOf(false) }
            val heartScale by animateFloatAsState(if (showHeart) 1.2f else 0f, label = "h")
            Box(
                Modifier.fillMaxWidth().aspectRatio(4f / 5f).background(colors.card)
                    .pointerInput(Unit) {
                        detectTapGestures(onDoubleTap = {
                            if (!post.isLiked) onLike()
                            showHeart = true
                        })
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Image, null, tint = colors.textSecondary, modifier = Modifier.size(48.dp))
                if (showHeart) {
                    Icon(Icons.Filled.Favorite, null, tint = Color.White,
                        modifier = Modifier.size(72.dp).scale(heartScale))
                    LaunchedEffect(showHeart) {
                        kotlinx.coroutines.delay(500)
                        showHeart = false
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(16.dp, 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onLike() }) {
                    Icon(
                        if (post.isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        null, tint = if (post.isLiked) Color.Red else colors.textPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                    if (privacy.showLikeCounts) {
                        Spacer(Modifier.width(6.dp))
                        Text(formatCount(post.likes), color = colors.textSecondary, fontSize = 13.sp)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onComment() }) {
                    Icon(Icons.Outlined.ChatBubbleOutline, null, tint = colors.textPrimary, modifier = Modifier.size(24.dp))
                    if (privacy.showCommentCounts) {
                        Spacer(Modifier.width(6.dp))
                        Text(formatCount(post.comments), color = colors.textSecondary, fontSize = 13.sp)
                    }
                }
                Icon(Icons.Outlined.Share, null, tint = colors.textPrimary, modifier = Modifier.size(24.dp).clickable {
                    shareContent(context, "${post.text}\n${postShareLink(post.id)}")
                })
            }
            Icon(
                if (isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                null, tint = colors.textPrimary,
                modifier = Modifier.size(24.dp).clickable { SessionManager.toggleSavedPost(post.id) }
            )
        }
        Divider(color = colors.border, thickness = 0.5.dp)
    }
}

private object PostMenuPositionProvider : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val menuWidth = popupContentSize.width
        val menuHeight = popupContentSize.height
        val x = (anchorBounds.right - menuWidth)
            .coerceAtLeast(0)
            .coerceAtMost((windowSize.width - menuWidth).coerceAtLeast(0))
        val fitsBelow = anchorBounds.bottom + menuHeight <= windowSize.height - 4
        val y = if (fitsBelow) {
            anchorBounds.bottom + 4
        } else {
            (anchorBounds.top - menuHeight - 4).coerceAtLeast(4)
        }
        return IntOffset(x, y)
    }
}
