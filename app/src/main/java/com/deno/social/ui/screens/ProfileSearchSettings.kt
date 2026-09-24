package com.deno.social.ui.screens

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Patterns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import androidx.core.content.ContextCompat
import com.deno.social.data.mock.CountryData
import com.deno.social.data.mock.MockData
import com.deno.social.data.model.NotificationItem
import com.deno.social.data.model.ProfileLink
import com.deno.social.data.model.User
import com.deno.social.data.model.UserProfile
import com.deno.social.data.repository.MockUserRepository
import com.deno.social.data.repository.SessionManager
import com.deno.social.ui.components.Avatar
import com.deno.social.ui.components.DenoButton
import com.deno.social.ui.components.DenoTextField
import com.deno.social.ui.components.EmptyState
import com.deno.social.ui.components.formatCount
import com.deno.social.ui.components.profileShareLink
import com.deno.social.ui.components.shareContent
import com.deno.social.ui.theme.DenoBlue
import com.deno.social.ui.theme.DenoTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun ProfileScreen(
    userId: String? = null,
    onEdit: () -> Unit = {},
    onSettings: () -> Unit = {},
    onBack: (() -> Unit)? = null,
    onFollowers: () -> Unit = {},
    onFollowing: () -> Unit = {}
) {
    val colors = DenoTheme.colors
    val context = LocalContext.current
    val repo = remember { MockUserRepository() }
    val isOwn = userId == null || userId == "me"
    val sessionProfile by SessionManager.profile.collectAsState()
    val followerIds by SessionManager.followerIds.collectAsState()
    val followingIds by SessionManager.followingIds.collectAsState()
    val blockedIds by SessionManager.blockedIds.collectAsState()
    val user = if (userId == null || userId == "me") {
        val def = MockData.currentUser
        def.copy(
            fullName = sessionProfile.fullName.ifBlank { def.fullName },
            username = sessionProfile.username.ifBlank { def.username },
            bio = sessionProfile.bio,
            links = sessionProfile.links
        )
    } else {
        repo.getUserById(userId) ?: MockData.currentUser
    }
    var tab by remember { mutableStateOf(0) }
    val savedPostIds by SessionManager.savedPostIds.collectAsState()
    val savedReelIds by SessionManager.savedReelIds.collectAsState()
    val savedTotal = savedPostIds.size + savedReelIds.size
    val displayPhotoPath = if (isOwn) sessionProfile.profilePhotoPath else ""
    // Counts/list data come from the real follow graph: own counts from the
    // shared SessionManager state; viewed users from their own derived lists.
    // Private accounts are only viewable once the current user is approved.
    val canView = isOwn || SessionManager.canViewProfile(user.id)
    val viewedPosts = if (isOwn) emptyList() else MockData.posts.filter { it.user.id == user.id }
    val followers = if (isOwn) followerIds.size else SessionManager.followersOf(user.id).size
    val following = if (isOwn) followingIds.size else SessionManager.followingOf(user.id).size
    val postsCount = if (isOwn) user.postsCount else viewedPosts.size
    val isFollowing = !isOwn && user.isFollowing
    // Block is an additional layer controlled by the current user, entirely
    // separate from the target account's own Private/Follow system.
    val blocked = !isOwn && user.id in blockedIds

    // Compact local popup state (Instagram-style ⋮ menu, confirmations, report).
    var showMenu by remember { mutableStateOf(false) }
    var blockConfirm by remember { mutableStateOf(false) }
    var unblockConfirm by remember { mutableStateOf(false) }
    var reportOpen by remember { mutableStateOf(false) }
    var reportDone by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, null, tint = colors.textPrimary)
                }
            } else Spacer(Modifier.width(48.dp))
            Text(user.username, color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            if (isOwn) {
                IconButton(onClick = onSettings) {
                    Icon(Icons.Default.Settings, null, tint = colors.textPrimary)
                }
            } else {
                // Instagram-style ⋮ menu on other users' profiles only.
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, "More options", tint = colors.textPrimary)
                }
            }
        }

        if (blocked) {
            // Compact blocked-state: hides Posts / Followers / Following content
            // and offers an Unblock action instead of the normal profile body.
            Column(
                Modifier.fillMaxSize().padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Outlined.Block, null, tint = colors.textSecondary, modifier = Modifier.size(44.dp))
                Spacer(Modifier.height(12.dp))
                Text(
                    "You've blocked @${user.username}",
                    color = colors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Their posts, followers, following and messages are hidden from you until you unblock.",
                    color = colors.textSecondary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { unblockConfirm = true },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DenoBlue, contentColor = Color.White),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 0.dp),
                    modifier = Modifier.height(38.dp)
                ) {
                    Text("Unblock", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            }
        } else {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProfilePhoto(user.avatarInitial, 80, displayPhotoPath)
                Spacer(Modifier.width(24.dp))
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceEvenly) {
                    StatCol(formatCount(postsCount), "Posts")
                    Column(Modifier.clickable(enabled = canView) { onFollowers() }) {
                        StatCol(formatCount(followers), "Followers")
                    }
                    Column(Modifier.clickable(enabled = canView) { onFollowing() }) {
                        StatCol(formatCount(following), "Following")
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(user.fullName, color = colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                if (user.followers > 10000 || user.isCurrentUser) {
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Filled.Verified, null, tint = DenoBlue, modifier = Modifier.size(16.dp))
                }
            }
            if (user.bio.isNotEmpty()) {
                Text(user.bio, color = colors.textSecondary, fontSize = 13.sp)
            }
            val profileLinks = if (isOwn) sessionProfile.links else user.links
            val displayLinks = profileLinks.filter { it.url.isNotBlank() }
            if (displayLinks.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    displayLinks.forEach { link ->
                        Row(
                            Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (colors.isDark) colors.card else Color(0xFFF3F3F5))
                                .clickable { openLink(context, link.url) }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Outlined.Link, null, tint = DenoBlue, modifier = Modifier.size(13.dp))
                            Spacer(Modifier.width(5.dp))
                            Text(
                                link.label.ifBlank { "Link" },
                                color = colors.textPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            if (isOwn) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f).height(38.dp), shape = RoundedCornerShape(10.dp), contentPadding = PaddingValues(0.dp)) {
                        Text("Edit Profile", color = colors.textPrimary, fontSize = 13.sp)
                    }
                    OutlinedButton(
                        onClick = {
                            shareContent(context, "Check out ${user.fullName} on DENO\n${profileShareLink(user.username)}")
                        },
                        modifier = Modifier.weight(1f).height(38.dp), shape = RoundedCornerShape(10.dp), contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("Share", color = colors.textPrimary, fontSize = 13.sp)
                    }
                }
            } else {
                Button(
                    onClick = { repo.toggleFollow(user.id) },
                    modifier = Modifier.fillMaxWidth().height(38.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isFollowing) colors.card else DenoBlue,
                        contentColor = if (isFollowing) colors.textPrimary else Color.White
                    ),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(if (isFollowing) "Following" else "Follow", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        TabRow(selectedTabIndex = tab, containerColor = colors.background, contentColor = DenoBlue) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Posts") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Reels") })
            Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("Saved") })
        }
        if (isOwn) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 16.dp),
                contentPadding = PaddingValues(1.dp)
            ) {
                items(if (tab == 2) savedTotal else 12) { i ->
                    Box(
                        Modifier.aspectRatio(1f).padding(1.dp).background(colors.card),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            when (tab) {
                                0 -> Icons.Outlined.Image
                                1 -> Icons.Outlined.PlayCircle
                                else -> Icons.Outlined.BookmarkBorder
                            },
                            null, tint = colors.textSecondary, modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        } else if (!canView) {
            // Blocked private account: no posts/reels/saved content is exposed.
            Box(Modifier.fillMaxSize().padding(bottom = 16.dp)) {
                PrivateAccountNotice()
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 16.dp),
                contentPadding = PaddingValues(1.dp)
            ) {
                when (tab) {
                    0 -> {
                        // That user's own posts; empty falls through to blank
                        // (same as the Saved tab).
                        items(viewedPosts, key = { it.id }) { post ->
                            Box(
                                Modifier.aspectRatio(1f).padding(1.dp).background(colors.card),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Outlined.Image,
                                    null, tint = colors.textSecondary, modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }
                    2 -> items(0) { }
                    else -> items(12) { i ->
                        Box(
                            Modifier.aspectRatio(1f).padding(1.dp).background(colors.card),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (tab == 1) Icons.Outlined.PlayCircle else Icons.Outlined.BookmarkBorder,
                                null, tint = colors.textSecondary, modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
            }
        }
        }

        // Instagram-style compact popups, only ever shown on other users.
        if (!isOwn) {
            if (showMenu) {
                ProfileActionMenu(
                    isBlocked = blocked,
                    onDismiss = { showMenu = false },
                    onBlock = {
                        showMenu = false
                        blockConfirm = true
                    },
                    onUnblock = {
                        showMenu = false
                        unblockConfirm = true
                    },
                    onReport = {
                        showMenu = false
                        reportOpen = true
                    }
                )
            }
            if (blockConfirm) {
                ConfirmBlockDialog(
                    username = user.username,
                    onCancel = { blockConfirm = false },
                    onConfirm = {
                        blockConfirm = false
                        SessionManager.blockUser(user.id)
                    }
                )
            }
            if (unblockConfirm) {
                ConfirmUnblockDialog(
                    username = user.username,
                    onCancel = { unblockConfirm = false },
                    onConfirm = {
                        unblockConfirm = false
                        SessionManager.unblockUser(user.id)
                    }
                )
            }
            if (reportOpen) {
                ReportDialog(
                    onCancel = { reportOpen = false },
                    onReason = { reason ->
                        reportOpen = false
                        SessionManager.reportUser(user.id, reason)
                        reportDone = true
                    }
                )
            }
            if (reportDone) {
                ReportDoneDialog(onOk = { reportDone = false })
            }
        }
    }
}

@Composable
fun StatCol(value: String, label: String) {
    val colors = DenoTheme.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Text(label, color = colors.textSecondary, fontSize = 12.sp)
    }
}

// --------------------------- BLOCK / REPORT POPUPS ---------------------------
// Small centered popups (white, ~280dp wide, rounded, soft shadow). Local only.

private val PopupMenuWidth = 290.dp
private val PopupShape = RoundedCornerShape(16.dp)

// Compact white popup that contains the popup content; centered automatically.
@Composable
private fun CompactPopup(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = PopupShape,
            color = Color.White,
            shadowElevation = 12.dp,
            modifier = Modifier.width(PopupMenuWidth)
        ) {
            Column(content = content)
        }
    }
}

// Instagram-style ⋮ menu for other users' profiles.
@Composable
private fun ProfileActionMenu(
    isBlocked: Boolean,
    onDismiss: () -> Unit,
    onBlock: () -> Unit,
    onUnblock: () -> Unit,
    onReport: () -> Unit
) {
    CompactPopup(onDismiss = onDismiss) {
        if (isBlocked) {
            PopupMenuRow(Icons.Outlined.Block, "Unblock", Color(0xFF111111)) { onUnblock() }
        } else {
            PopupMenuRow(Icons.Outlined.Block, "Block", Color(0xFF111111)) { onBlock() }
            PopupMenuRow(Icons.Outlined.Flag, "Report", Color(0xFF111111)) { onReport() }
        }
        Surface(color = Color(0xFFE8E8E8), modifier = Modifier.fillMaxWidth().height(1.dp)) {}
        PopupMenuRow(Icons.Default.Close, "Cancel", Color(0xFF111111)) { onDismiss() }
    }
}

@Composable
private fun PopupMenuRow(icon: ImageVector, title: String, tint: Color, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(title, color = tint, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

// Compact block confirmation popup. Confirmed via SessionManager (local only).
@Composable
private fun ConfirmBlockDialog(username: String, onCancel: () -> Unit, onConfirm: () -> Unit) {
    CompactPopup(onDismiss = onCancel) {
        Text(
            "Block @$username?",
            color = Color(0xFF111111),
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
        )
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 10.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onCancel) {
                Text("Cancel", color = Color(0xFF111111), fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.width(6.dp))
            Button(
                onClick = onConfirm,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE5212C), contentColor = Color.White),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 0.dp),
                modifier = Modifier.height(38.dp)
            ) {
                Text("Block", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
        }
    }
}

// Compact unblock confirmation popup.
@Composable
private fun ConfirmUnblockDialog(username: String, onCancel: () -> Unit, onConfirm: () -> Unit) {
    CompactPopup(onDismiss = onCancel) {
        Text(
            "Unblock @$username?",
            color = Color(0xFF111111),
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
        )
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 10.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onCancel) {
                Text("Cancel", color = Color(0xFF111111), fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.width(6.dp))
            Button(
                onClick = onConfirm,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DenoBlue, contentColor = Color.White),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 0.dp),
                modifier = Modifier.height(38.dp)
            ) {
                Text("Unblock", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
        }
    }
}

// Compact report reason popup. Local only; nothing is sent anywhere.
@Composable
private fun ReportDialog(onCancel: () -> Unit, onReason: (String) -> Unit) {
    CompactPopup(onDismiss = onCancel) {
        Text(
            "Report",
            color = Color(0xFF111111),
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )
        listOf("Spam", "Harassment or abuse", "Inappropriate content", "Other").forEach { reason ->
            PopupMenuRow(Icons.Outlined.Flag, reason, Color(0xFF111111)) { onReason(reason) }
        }
        Surface(color = Color(0xFFE8E8E8), modifier = Modifier.fillMaxWidth().height(1.dp)) {}
        PopupMenuRow(Icons.Default.Close, "Cancel", Color(0xFF111111)) { onCancel() }
    }
}

// Local-only "Report submitted" confirmation. Never claims anything reached a server.
@Composable
private fun ReportDoneDialog(onOk: () -> Unit) {
    CompactPopup(onDismiss = onOk) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Report submitted",
                color = Color(0xFF111111),
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp
            )
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = onOk,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DenoBlue, contentColor = Color.White),
                contentPadding = PaddingValues(horizontal = 28.dp, vertical = 0.dp),
                modifier = Modifier.height(38.dp)
            ) {
                Text("OK", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
        }
    }
}

// --------------------------- FOLLOWERS / FOLLOWING ---------------------------
// Secondary screens opened from the Profile header counts. Both read the same
// SessionManager follow state, so Follow/Unfollow updates counts everywhere.

@Composable
fun FollowersScreen(profileUserId: String? = null, onBack: () -> Unit, onUserClick: (String) -> Unit) {
    FollowUsersList(title = "Followers", profileUserId = profileUserId, kind = FollowListKind.FOLLOWERS, onBack = onBack, onUserClick = onUserClick)
}

@Composable
fun FollowingScreen(profileUserId: String? = null, onBack: () -> Unit, onUserClick: (String) -> Unit) {
    FollowUsersList(title = "Following", profileUserId = profileUserId, kind = FollowListKind.FOLLOWING, onBack = onBack, onUserClick = onUserClick)
}

private enum class FollowListKind { FOLLOWERS, FOLLOWING }

// Reusable Followers/Following list. `profileUserId == null` (or the current
// user) shows the current user's own lists (existing behavior); otherwise it
// shows the selected user's real Followers/Following lists. A private account
// the current user is not approved to view is blocked at the screen/data level.
@Composable
private fun FollowUsersList(
    title: String,
    profileUserId: String?,
    kind: FollowListKind,
    onBack: () -> Unit,
    onUserClick: (String) -> Unit
) {
    val colors = DenoTheme.colors
    val liveUsers by SessionManager.liveUsers.collectAsState()
    val myFollowing by SessionManager.followingIds.collectAsState()
    val myFollowers by SessionManager.followerIds.collectAsState()

    val isOwnList = profileUserId == null || profileUserId == MockData.currentUser.id
    val targetId = profileUserId ?: MockData.currentUser.id
    val blocked = !isOwnList && !SessionManager.canViewProfile(targetId)

    val users = remember(liveUsers, myFollowing, myFollowers, blocked, targetId, kind) {
        if (blocked) {
            emptyList()
        } else if (isOwnList) {
            val ids = if (kind == FollowListKind.FOLLOWERS) myFollowers else myFollowing
            if (ids.isEmpty()) emptyList()
            else liveUsers.filter { it.id in ids }
        } else {
            if (kind == FollowListKind.FOLLOWERS) SessionManager.followersOf(targetId)
            else SessionManager.followingOf(targetId)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, null, tint = colors.textPrimary)
            }
            Text(title, color = colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
        }
        Divider(color = colors.border)

        when {
            blocked -> {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    PrivateAccountNotice()
                }
            }
            users.isEmpty() -> {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    EmptyState(
                        Icons.Outlined.Person,
                        "No $title yet",
                        "People you follow and who follow you will show up here."
                    )
                }
            }
            else -> {
                LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                    itemsIndexed(users, key = { _, u -> u.id }) { index, user ->
                        FollowUserRow(
                            user = user,
                            onOpen = { onUserClick(user.id) },
                            onToggle = { SessionManager.toggleFollowUser(user.id) }
                        )
                        if (index < users.lastIndex) {
                            Divider(color = colors.border, modifier = Modifier.padding(start = 76.dp))
                        }
                    }
                }
            }
        }
    }
}

// Instagram-style private account gate. Shown instead of a private account's
// Followers/Following lists and grid content while the current user is not an
// approved follower.
@Composable
private fun PrivateAccountNotice() {
    val colors = DenoTheme.colors
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Outlined.Lock, null, tint = colors.textSecondary, modifier = Modifier.size(44.dp))
        Spacer(Modifier.height(12.dp))
        Text(
            "This account is private",
            color = colors.textPrimary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Only people this account approves can see their followers, following and posts.",
            color = colors.textSecondary,
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun FollowUserRow(
    user: User,
    onOpen: () -> Unit,
    onToggle: () -> Unit
) {
    val colors = DenoTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(user.avatarInitial.ifEmpty { user.fullName.first().toString() }, 48)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                user.fullName,
                color = colors.textPrimary,
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "@${user.username}",
                color = colors.textSecondary,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box(
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(if (user.isFollowing) colors.card else DenoBlue)
                .clickable { onToggle() }
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                if (user.isFollowing) "Following" else "Follow",
                color = if (user.isFollowing) colors.textPrimary else Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun EditProfileScreen(onBack: () -> Unit, onSaved: () -> Unit) {
    val colors = DenoTheme.colors
    val context = LocalContext.current

    // Real state: loaded from SessionManager every time the screen opens.
    // Cancel/back pops without calling saveProfile, so nothing is persisted.
    val savedProfile = remember { SessionManager.getProfile() }
    var fullName by remember { mutableStateOf(savedProfile.fullName) }
    var username by remember { mutableStateOf(savedProfile.username) }
    var bio by remember { mutableStateOf(savedProfile.bio) }
    var country by remember { mutableStateOf(savedProfile.country) }
    var region by remember { mutableStateOf(savedProfile.region) }
    var city by remember { mutableStateOf(savedProfile.city) }
    var language by remember { mutableStateOf(savedProfile.language) }
    var maslak by remember { mutableStateOf(savedProfile.maslak) }
    var links by remember { mutableStateOf(savedProfile.links) }
    var pendingPhotoPath by remember { mutableStateOf(savedProfile.profilePhotoPath) }

    val linksValid = links.all { it.url.isBlank() || isValidSocialUrl(it.url) }
    val cityValid = city.isNotBlank()

    // ---------------- Profile photo state (temporary Photo Selection / Crop modes) ----------------
    var photoMode by remember { mutableStateOf(PhotoMode.NONE) }
    var croppingSource by remember { mutableStateOf<Bitmap?>(null) }
    var galleryImages by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var permissionDenied by remember { mutableStateOf(false) }
    var galleryRequested by remember { mutableStateOf(false) }

    val permissionToAsk = remember {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> Manifest.permission.READ_MEDIA_IMAGES
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q -> Manifest.permission.READ_EXTERNAL_STORAGE
            else -> null
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionDenied = !granted
        galleryImages = if (granted) queryGalleryImages(context) else emptyList()
    }

    fun startCrop(uri: Uri) {
        val full = decodeSampledBitmap(context, uri, 2048)
        if (full != null) {
            croppingSource = full
            photoMode = PhotoMode.CROP
        }
    }

    val systemPhotoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) startCrop(uri)
    }

    LaunchedEffect(photoMode, galleryRequested) {
        if (photoMode == PhotoMode.GALLERY && !galleryRequested) {
            galleryRequested = true
            val grantedOrNotNeeded =
                permissionToAsk == null ||
                    ContextCompat.checkSelfPermission(context, permissionToAsk) == PackageManager.PERMISSION_GRANTED
            if (grantedOrNotNeeded) {
                permissionDenied = false
                galleryImages = queryGalleryImages(context)
            } else {
                permissionLauncher.launch(permissionToAsk)
            }
        }
    }

    // System back inside gallery/crop modes returns to the normal form first.
    BackHandler(enabled = photoMode != PhotoMode.NONE) {
        croppingSource = null
        when (photoMode) {
            PhotoMode.CROP -> photoMode = PhotoMode.GALLERY
            else -> {
                galleryRequested = false
                photoMode = PhotoMode.NONE
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(horizontal = 16.dp)
    ) {
        when (photoMode) {
            // Temporary Photo Selection mode: the normal form is hidden and a
            // clean full-width grid fills the available screen area.
            PhotoMode.GALLERY -> {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = {
                        croppingSource = null
                        galleryRequested = false
                        photoMode = PhotoMode.NONE
                    }) { Text("Cancel", color = colors.textSecondary) }
                    Text("Choose Photo", color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Spacer(Modifier.width(48.dp))
                }
                Spacer(Modifier.height(2.dp))
                TextButton(
                    onClick = {
                        systemPhotoPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    Text("Open Photos", color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
                if (permissionDenied) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Photo access is off. Allow photos in settings or use Open Photos.",
                        color = colors.textSecondary,
                        fontSize = 12.sp
                    )
                }
                Spacer(Modifier.height(4.dp))
                GalleryGridFull(
                    images = galleryImages,
                    onPick = { startCrop(it) },
                    modifier = Modifier.weight(1f).fillMaxWidth()
                )
            }

            // Temporary Crop mode: same screen, full circular editor with a
            // clean Cancel | Crop Photo | Done top bar.
            PhotoMode.CROP -> {
                croppingSource?.let { source ->
                    CircularCropEditor(
                        source = source,
                        onDone = { cropped ->
                            val newPath = writeProfilePhotoFile(context, cropped)
                            val savedPath = savedProfile.profilePhotoPath
                            if (pendingPhotoPath.isNotBlank() && pendingPhotoPath != savedPath) {
                                runCatching { File(pendingPhotoPath).delete() }
                            }
                            pendingPhotoPath = newPath
                            croppingSource = null
                            photoMode = PhotoMode.NONE
                        },
                        onCancel = {
                            croppingSource = null
                            photoMode = PhotoMode.GALLERY
                        }
                    )
                }
            }

            // Normal Edit Profile form.
            PhotoMode.NONE -> {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onBack) { Text("Cancel", color = colors.textSecondary) }
                    Text("Edit Profile", color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    TextButton(
                        enabled = linksValid && cityValid,
                        onClick = {
                            if (linksValid && cityValid) {
                                SessionManager.saveProfile(
                                    UserProfile(
                                        fullName = fullName.trim(),
                                        username = username.trim(),
                                        bio = bio.trim(),
                                        country = country,
                                        region = region,
                                        city = city,
                                        language = language,
                                        maslak = maslak,
                                        links = links.mapNotNull { link ->
                                            val url = link.url.trim()
                                            if (url.isBlank()) null
                                            else link.copy(label = link.label.trim(), url = url)
                                        },
                                        profilePhotoPath = pendingPhotoPath
                                    )
                                )
                                onSaved()
                            }
                        }
                    ) {
                        Text("Save", color = if (linksValid && cityValid) DenoBlue else colors.textSecondary, fontWeight = FontWeight.Bold)
                    }
                }
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    Spacer(Modifier.height(24.dp))
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProfilePhoto(MockData.currentUser.avatarInitial, 90, pendingPhotoPath)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Change Photo",
                        color = DenoBlue,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .clickable { photoMode = PhotoMode.GALLERY }
                    )
                    Spacer(Modifier.height(24.dp))
                    DenoTextField(fullName, { fullName = it }, "Full Name")
                    Spacer(Modifier.height(12.dp))
                    DenoTextField(username, { username = it }, "Username")
                    Spacer(Modifier.height(12.dp))
                    DenoTextField(bio, { bio = it }, "Bio", singleLine = false)

                    Spacer(Modifier.height(28.dp))
                    Text("Location", color = colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Spacer(Modifier.height(2.dp))
                    Text("Selecting a country loads that country's real region list.", color = colors.textSecondary, fontSize = 12.sp)
                    Spacer(Modifier.height(14.dp))
                    DenoPickerField(country, "Country", COUNTRIES) {
                        country = it
                        region = ""
                    }
                    Spacer(Modifier.height(12.dp))
                    DenoPickerField(region, "Region / State", CountryData.regionsFor(country)) { region = it }
                    Spacer(Modifier.height(12.dp))
                    DenoTextField(city, { city = it }, "City", required = true)

                    Spacer(Modifier.height(28.dp))
                    Text("Preferences", color = colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Spacer(Modifier.height(2.dp))
                    Text("Verification badges are assigned by admin in the future.", color = colors.textSecondary, fontSize = 12.sp)
                    Spacer(Modifier.height(14.dp))
                    DenoPickerField(language, "Language", LANGUAGES) { language = it }
                    Spacer(Modifier.height(12.dp))
                    DenoPickerField(
                        maslak,
                        "Maslak / Fiqh (optional)",
                        MASLAKS,
                        alphabetical = false,
                        showSearch = false
                    ) { maslak = it }

                    Spacer(Modifier.height(28.dp))
                    Text("Links", color = colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Spacer(Modifier.height(2.dp))
                    Text("Add, edit or remove any number of links. Empty links are not saved.", color = colors.textSecondary, fontSize = 12.sp)
                    Spacer(Modifier.height(14.dp))
                    links.forEachIndexed { index, link ->
                        LinkRow(
                            link = link,
                            onLabelChange = { newLabel ->
                                links = links.mapIndexed { i, l -> if (i == index) l.copy(label = newLabel) else l }
                            },
                            onUrlChange = { newUrl ->
                                links = links.mapIndexed { i, l -> if (i == index) l.copy(url = newUrl) else l }
                            },
                            onRemove = { links = links.filterIndexed { i, _ -> i != index } }
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                    TextButton(
                        onClick = { links = links + ProfileLink("", "") },
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        Icon(Icons.Default.Add, null, tint = DenoBlue, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add Link", color = DenoBlue, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

// ----------------- PROFILE PHOTO (temporary Photo Selection + same-screen circular crop) -----------------

private enum class PhotoMode { NONE, GALLERY, CROP }

@Composable
private fun CircularProfilePhoto(initial: String, size: Int, photoPath: String) {
    val context = LocalContext.current
    val photo by produceState<ImageBitmap?>(initialValue = null, photoPath) {
        value = if (photoPath.isBlank()) null else runCatching {
            withContext(Dispatchers.IO) {
                BitmapFactory.decodeFile(photoPath, BitmapFactory.Options().apply { inSampleSize = 2 })
                    ?.asImageBitmap()
            }
        }.getOrNull()
    }
    if (photo != null) {
        Image(
            bitmap = photo!!,
            contentDescription = "Profile photo",
            modifier = Modifier.size(size.dp).clip(CircleShape),
            contentScale = ContentScale.Crop
        )
    } else {
        Avatar(initial, size)
    }
}

@Composable
private fun GalleryThumb(uri: Uri, modifier: Modifier = Modifier) {
    val colors = DenoTheme.colors
    val context = LocalContext.current
    val photo by produceState<ImageBitmap?>(initialValue = null, uri) {
        value = withContext(Dispatchers.IO) {
            decodeSampledBitmap(context, uri, 256)?.asImageBitmap()
        }
    }
    Box(
        modifier.aspectRatio(1f).background(colors.card),
        contentAlignment = Alignment.Center
    ) {
        if (photo != null) {
            Image(
                bitmap = photo!!,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Icon(Icons.Outlined.Image, null, tint = colors.textSecondary, modifier = Modifier.size(22.dp))
        }
    }
}

// Full-width gallery that fills the available screen area. Only shown inside
// the temporary Photo Selection mode, never inside the Edit Profile form.
@Composable
private fun GalleryGridFull(images: List<Uri>, onPick: (Uri) -> Unit, modifier: Modifier = Modifier) {
    val colors = DenoTheme.colors
    if (images.isEmpty()) {
        Row(
            modifier.fillMaxWidth().padding(vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text("No photos available. Use \"Open Photos\" above.", color = colors.textSecondary, fontSize = 13.sp)
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = modifier.padding(bottom = 4.dp)
        ) {
            items(images) { uri ->
                GalleryThumb(uri, Modifier.clickable { onPick(uri) })
            }
        }
    }
}

// Live circular crop on the current screen. Cancel and Done sit in a clean
// top bar; pan/pinch gestures resize the photo directly below. Minimum zoom
// is locked to the "cover" scale so the circle is never blank. Shared by the
// Edit Profile flow and the Complete Profile (signup) flow.
@Composable
fun CircularCropEditor(
    source: Bitmap,
    onDone: (Bitmap) -> Unit,
    onCancel: () -> Unit
) {
    val colors = DenoTheme.colors
    val imageBitmap = remember(source) { source.asImageBitmap() }
    var viewPx by remember { mutableStateOf(0) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val baseScale = coverScale(source, viewPx)

    fun clampOffset(scaleValue: Float, offsetValue: Offset): Offset {
        val dw = source.width * baseScale * scaleValue
        val dh = source.height * baseScale * scaleValue
        val hx = ((dw - viewPx) / 2f).coerceAtLeast(0f)
        val hy = ((dh - viewPx) / 2f).coerceAtLeast(0f)
        return Offset(offsetValue.x.coerceIn(-hx, hx), offsetValue.y.coerceIn(-hy, hy))
    }

    fun cropSource(scaleValue: Float, offsetValue: Offset): Triple<Int, Int, Int> {
        val dw = source.width * baseScale * scaleValue
        val dh = source.height * baseScale * scaleValue
        val u = (dw / 2f - offsetValue.x) / dw * source.width
        val v = (dh / 2f - offsetValue.y) / dh * source.height
        val side = (viewPx / (baseScale * scaleValue)).coerceAtMost(min(source.width, source.height).toFloat())
        val half = side / 2f
        val x = (u - half).coerceIn(0f, (source.width - side).coerceAtLeast(0f))
        val y = (v - half).coerceIn(0f, (source.height - side).coerceAtLeast(0f))
        return Triple(x.toInt(), y.toInt(), side.toInt())
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onCancel) { Text("Cancel", color = colors.textSecondary) }
                Text("Crop Photo", color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                TextButton(
                    enabled = viewPx > 0,
                    onClick = {
                        if (viewPx > 0) {
                            val (x, y, side) = cropSource(scale, offset)
                            onDone(Bitmap.createBitmap(source, x, y, side, side))
                        }
                    }
                ) {
                    Text("Done", color = if (viewPx > 0) DenoBlue else colors.textSecondary, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .onSizeChanged { viewPx = min(it.width, it.height) }
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 6f)
                                offset = if (scale == 1f) Offset.Zero else clampOffset(scale, offset + pan)
                            }
                        }
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        val s = size.minDimension
                        val dw = source.width * baseScale * scale
                        val dh = source.height * baseScale * scale
                        val cx = s / 2f + offset.x
                        val cy = s / 2f + offset.y
                        drawImage(
                            image = imageBitmap,
                            dstSize = IntSize(dw.roundToInt(), dh.roundToInt()),
                            dstOffset = IntOffset((cx - dw / 2f).roundToInt(), (cy - dh / 2f).roundToInt())
                        )
                        val center = Offset(s / 2f, s / 2f)
                        val radius = s / 2f
                        val circle = Path().apply { addOval(Rect(center, radius)) }
                        clipPath(circle, ClipOp.Difference) {
                            drawRect(color = Color.Black.copy(alpha = 0.5f))
                        }
                        drawCircle(color = Color.White, radius = radius - 2.dp.toPx(), style = Stroke(width = 3.dp.toPx()))
                    }
                }
                CircularLivePreview(
                    imageBitmap = imageBitmap,
                    source = source,
                    baseScale = baseScale,
                    viewPx = viewPx,
                    scale = scale,
                    offset = offset,
                    modifier = Modifier.align(Alignment.TopEnd)
                )
            }
        }
    }
}

@Composable
private fun CircularLivePreview(
    imageBitmap: ImageBitmap,
    source: Bitmap,
    baseScale: Float,
    viewPx: Int,
    scale: Float,
    offset: Offset,
    modifier: Modifier = Modifier
) {
    Box(
        modifier
            .padding(10.dp)
            .size(72.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.35f))
            .border(2.dp, Color.White, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize().padding(3.dp)) {
            if (viewPx > 0 && baseScale > 0f) {
                val dw = source.width * baseScale * scale
                val dh = source.height * baseScale * scale
                val u = (dw / 2f - offset.x) / dw * source.width
                val v = (dh / 2f - offset.y) / dh * source.height
                val side = (viewPx / (baseScale * scale)).coerceAtMost(min(source.width, source.height).toFloat())
                val half = side / 2f
                val x = (u - half).coerceIn(0f, (source.width - side).coerceAtLeast(0f))
                val y = (v - half).coerceIn(0f, (source.height - side).coerceAtLeast(0f))
                val dst = size.minDimension
                drawImage(
                    image = imageBitmap,
                    srcOffset = IntOffset(x.toInt(), y.toInt()),
                    srcSize = IntSize(side.toInt(), side.toInt()),
                    dstSize = IntSize(dst.toInt(), dst.toInt())
                )
            }
        }
    }
}

private fun coverScale(source: Bitmap, viewPx: Int): Float {
    if (viewPx <= 0) return 1f
    return max(viewPx.toFloat() / source.width, viewPx.toFloat() / source.height).coerceAtLeast(0.001f)
}

private fun queryGalleryImages(context: Context): List<Uri> {
    // Read-only list of actual device images. Excludes trashed/pending rows and
    // 0-size placeholders so invalid entries never surface as dead tiles.
    val projection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DATE_ADDED)
    val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

    val selection = buildString {
        var needsAnd = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            append("${MediaStore.Images.Media.IS_TRASHED} = 0 AND ${MediaStore.Images.Media.IS_PENDING} = 0")
            needsAnd = true
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (needsAnd) append(" AND ")
            append("${MediaStore.Images.Media.SIZE} >= 0")
        }
    }.ifEmpty { null }

    // Large practical cap. A hard 60 limit makes real collections appear
    // missing; the cap only guards against pathological stores.
    val maxImages = 1000

    val ids = LinkedHashSet<Long>()
    runCatching {
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            null,
            sortOrder
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            while (cursor.moveToNext() && ids.size < maxImages) {
                if (cursor.isNull(idCol)) continue
                ids.add(cursor.getLong(idCol))
            }
        }
    }
    return ids.map { ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, it) }
}

fun decodeSampledBitmap(context: Context, uri: Uri, maxDimension: Int): Bitmap? {
    return runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (
            (bounds.outWidth / (sample * 2) >= maxDimension) ||
            (bounds.outHeight / (sample * 2) >= maxDimension)
        ) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    }.getOrNull()
}

fun writeProfilePhotoFile(context: Context, cropped: Bitmap): String {
    val dir = File(context.cacheDir, "profile_photos").apply { mkdirs() }
    val file = File(dir, "profile_${System.currentTimeMillis()}.png")
    runCatching {
        file.outputStream().use { cropped.compress(Bitmap.CompressFormat.PNG, 90, it) }
    }
    return file.absolutePath
}

@Composable
private fun LinkRow(
    link: ProfileLink,
    onLabelChange: (String) -> Unit,
    onUrlChange: (String) -> Unit,
    onRemove: () -> Unit
) {
    val colors = DenoTheme.colors
    Row(verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            DenoTextField(link.label, onLabelChange, "Label")
            Spacer(Modifier.height(8.dp))
            SocialLinkField("URL", link.url, onUrlChange)
        }
        Spacer(Modifier.width(8.dp))
        IconButton(onClick = onRemove, modifier = Modifier.padding(top = 4.dp)) {
            Icon(
                Icons.Default.Close,
                "Remove link",
                tint = colors.textSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun SocialLinkField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    val colors = DenoTheme.colors
    val showError = value.isNotBlank() && !isValidSocialUrl(value)
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text("https://example.com", color = colors.textSecondary, fontSize = 13.sp) },
        singleLine = true,
        isError = showError,
        supportingText = {
            if (showError) {
                Text("Enter a valid URL", color = Color.Red, fontSize = 12.sp)
            }
        },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = DenoBlue,
            unfocusedBorderColor = colors.border,
            errorBorderColor = Color.Red,
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

private fun isValidSocialUrl(input: String): Boolean {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return true
    return Patterns.WEB_URL.matcher(trimmed).matches()
}

private fun openLink(context: Context, url: String) {
    val trimmed = url.trim()
    if (trimmed.isEmpty()) return
    val normalized = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "https://$trimmed"
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(normalized)))
    }
}

@Composable
fun SearchScreen(onBack: () -> Unit = {}, onUserClick: (String) -> Unit) {
    val colors = DenoTheme.colors
    val repo = remember { MockUserRepository() }
    var query by remember { mutableStateOf("") }
    val results = remember(query) { repo.searchUsers(query) }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp, 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search users...", color = colors.textSecondary) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = colors.textSecondary) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        Icon(Icons.Default.Close, null, tint = colors.textSecondary,
                            modifier = Modifier.clickable { query = "" })
                    }
                },
                singleLine = true,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = DenoBlue, unfocusedBorderColor = colors.border,
                    focusedTextColor = colors.textPrimary, unfocusedTextColor = colors.textPrimary,
                    cursorColor = DenoBlue
                )
            )
        }
        if (results.isEmpty()) {
            EmptyState(Icons.Outlined.SearchOff, "No results", "Try a different name or username")
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 16.dp)
            ) {
                items(results) { user ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onUserClick(user.id) }.padding(16.dp, 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Avatar(user.avatarInitial, 48)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(user.fullName, color = colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                if (user.followers > 10000 || user.isCurrentUser) {
                                    Spacer(Modifier.width(4.dp))
                                    Icon(Icons.Filled.Verified, null, tint = DenoBlue, modifier = Modifier.size(16.dp))
                                }
                            }
                            Text("@${user.username}", color = colors.textSecondary, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NotificationsScreen(onBack: () -> Unit, onUserClick: (String) -> Unit = {}) {
    val colors = DenoTheme.colors
    val notifications by SessionManager.notifications.collectAsState()
    val followingIds by SessionManager.followingIds.collectAsState()
    val followerIds by SessionManager.followerIds.collectAsState()
    val pushEnabled by SessionManager.pushEnabled.collectAsState()

    // Existing row layout is preserved; rows are fed from real persisted
    // follow notifications instead of static mock data.
    val items = remember(notifications, followingIds, followerIds) {
        notifications.map { n ->
            NotificationItem(
                id = n.id,
                type = "follow",
                user = User(
                    id = n.actorUserId,
                    fullName = n.actorName,
                    username = n.actorUsername,
                    avatarInitial = n.actorName.firstOrNull()?.uppercase().orEmpty(),
                    isCurrentUser = false
                ),
                message = "started following you",
                timeAgo = relativeTime(n.createdAt),
                isRead = n.isRead
            )
        }
    }

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
            Text("Notifications", color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
        Divider(color = colors.border)
        if (!pushEnabled) {
            Row(
                Modifier.fillMaxWidth().padding(16.dp, 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.NotificationsOff, null, tint = DenoBlue, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    "Push notifications are off. Enable them in Settings.",
                    color = colors.textSecondary, fontSize = 13.sp
                )
            }
            Divider(color = colors.border)
        }
        if (items.isEmpty()) {
            EmptyState(Icons.Outlined.NotificationsNone, "No notifications", "You're all caught up")
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 16.dp)
            ) {
                items(items, key = { it.id }) { n ->
                    Row(
                        Modifier.fillMaxWidth()
                            .background(if (!n.isRead) colors.card else colors.background)
                            .clickable {
                                SessionManager.markNotificationRead(n.id)
                                onUserClick(n.user.id)
                            }
                            .padding(16.dp, 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Avatar(n.user.avatarInitial, 46)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row {
                                Text(n.user.username, color = colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Spacer(Modifier.width(4.dp))
                                Text(n.message, color = colors.textSecondary, fontSize = 14.sp)
                            }
                            Text(n.timeAgo, color = colors.textSecondary, fontSize = 12.sp)
                        }
                        NotificationFollowButton(n.user.id, followingIds, followerIds)
                        Spacer(Modifier.width(10.dp))
                        Icon(
                            when (n.type) {
                                "like" -> Icons.Filled.Favorite
                                "follow" -> Icons.Default.PersonAdd
                                else -> Icons.Outlined.ChatBubbleOutline
                            },
                            null, tint = if (n.type == "like") Color.Red else DenoBlue,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

// Follow/Fellow Back wired to the real follow system, so tapping it updates
// the Following state, Followers/Following counts and never duplicates.
@Composable
private fun NotificationFollowButton(
    userId: String,
    followingIds: Set<String>,
    followerIds: Set<String>
) {
    val colors = DenoTheme.colors
    val following = userId in followingIds
    val label = when {
        following -> "Following"
        userId in followerIds -> "Follow Back"
        else -> "Follow"
    }
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (following) colors.card else DenoBlue)
            .clickable { SessionManager.toggleFollowUser(userId) }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            label,
            color = if (following) colors.textPrimary else Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

// Human-readable relative time computed from a timestamp. Shared by the
// Notifications, Drafts and Scheduled Posts screens.
fun relativeTime(timestamp: Long): String {
    if (timestamp <= 0) return "now"
    val minutes = (System.currentTimeMillis() - timestamp) / 60_000L
    return when {
        minutes < 1 -> "now"
        minutes < 60 -> "${minutes}m"
        minutes < 1440 -> "${minutes / 60}h"
        minutes < 10080 -> "${minutes / 1440}d"
        else -> "${minutes / 10080}w"
    }
}

@Composable
fun SettingsScreen(
    darkMode: Boolean,
    onDarkModeChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onAbout: () -> Unit = {},
    onPrivacy: () -> Unit,
    onTerms: () -> Unit,
    onHelp: () -> Unit,
    onEditProfile: () -> Unit = {},
    onContentPrivacy: () -> Unit = {},
    onSaved: () -> Unit = {},
    onChangePassword: () -> Unit = {},
    onBlocked: () -> Unit = {},
    onDrafts: () -> Unit = {},
    onScheduled: () -> Unit = {},
    onAnalytics: () -> Unit = {},
    onGroups: () -> Unit = {},
    onTrending: () -> Unit = {},
    onInvite: () -> Unit = {},
    onRecovery: () -> Unit = {},
    onModeration: () -> Unit = {},
    onLanguage: () -> Unit = {}
) {
    val colors = DenoTheme.colors
    val settingsScrollState = rememberScrollState()
    var deleteAccountConfirm by remember { mutableStateOf(false) }

    val privateAcc by SessionManager.privateAccount.collectAsState()
    val pushEnabled by SessionManager.pushEnabled.collectAsState()

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
            Text("Settings", color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
        Divider(color = colors.border)

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(settingsScrollState)
        ) {
        SettingsSection("ACCOUNT")
        SettingsItem("Edit Profile", Icons.Outlined.Person) { onEditProfile() }
        SettingsItem("Saved", Icons.Outlined.BookmarkBorder) { onSaved() }
        SettingsItem("Change Password", Icons.Outlined.Lock) { onChangePassword() }
        SettingsItem("Drafts", Icons.Outlined.Drafts) { onDrafts() }
        SettingsItem("Scheduled Posts", Icons.Outlined.Schedule) { onScheduled() }
        SettingsItem("Analytics", Icons.Outlined.BarChart) { onAnalytics() }
        SettingsItem("Account Recovery", Icons.Outlined.PhonelinkSetup) { onRecovery() }

        SettingsSection("DISCOVER")
        SettingsItem("Communities", Icons.Outlined.Groups) { onGroups() }
        SettingsItem("Trending", Icons.Outlined.TrendingUp) { onTrending() }
        SettingsItem("Invite Friends", Icons.Outlined.PersonAdd) { onInvite() }

        SettingsSection("PRIVACY")
        Row(
            Modifier.fillMaxWidth().padding(20.dp, 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Security, null, tint = colors.textPrimary)
                Spacer(Modifier.width(14.dp))
                Text("Private Account", color = colors.textPrimary, fontSize = 15.sp)
            }
            Switch(checked = privateAcc, onCheckedChange = { SessionManager.setPrivateAccount(it) },
                colors = SwitchDefaults.colors(checkedTrackColor = DenoBlue))
        }
        SettingsItem("Blocked Users", Icons.Outlined.Block) { onBlocked() }
        SettingsItem("Content Privacy", Icons.Outlined.Visibility) { onContentPrivacy() }

        SettingsSection("NOTIFICATIONS")
        Row(
            Modifier.fillMaxWidth().padding(20.dp, 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Notifications, null, tint = colors.textPrimary)
                Spacer(Modifier.width(14.dp))
                Text("Push Notifications", color = colors.textPrimary, fontSize = 15.sp)
            }
            Switch(checked = pushEnabled, onCheckedChange = { SessionManager.setPushEnabled(it) },
                colors = SwitchDefaults.colors(checkedTrackColor = DenoBlue))
        }

        SettingsSection("APP")
        Row(
            Modifier.fillMaxWidth().padding(20.dp, 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.DarkMode, null, tint = colors.textPrimary)
                Spacer(Modifier.width(14.dp))
                Text("Dark Mode", color = colors.textPrimary, fontSize = 15.sp)
            }
            Switch(checked = darkMode, onCheckedChange = onDarkModeChange, colors = SwitchDefaults.colors(checkedTrackColor = DenoBlue))
        }
        SettingsItem("Language", Icons.Outlined.Language) { onLanguage() }

        SettingsSection("ABOUT")
        SettingsItem("About DENO", Icons.Outlined.Info) { onAbout() }
        SettingsItem("Privacy Policy", Icons.Outlined.Policy) { onPrivacy() }
        SettingsItem("Terms of Service", Icons.Outlined.Description) { onTerms() }
        SettingsItem("Help & Support", Icons.Outlined.HelpOutline) { onHelp() }
        SettingsItem("Moderation", Icons.Outlined.Flag) { onModeration() }

        Divider(color = colors.border, modifier = Modifier.padding(vertical = 8.dp))
        Text(
            "Log Out",
            color = Color.Red,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            modifier = Modifier.fillMaxWidth().clickable {
                SessionManager.logout()
                onLogout()
            }.padding(20.dp)
        )
        Text(
            "Delete Account",
            color = colors.textSecondary,
            fontSize = 14.sp,
            modifier = Modifier.fillMaxWidth().clickable { deleteAccountConfirm = true }.padding(20.dp, 8.dp)
        )
        }

        if (deleteAccountConfirm) {
            AlertDialog(
                onDismissRequest = { deleteAccountConfirm = false },
                containerColor = colors.surface,
                title = { Text("Delete Account?", color = colors.textPrimary) },
                text = {
                    Text(
                        "This permanently removes your local data in this demo app. This action cannot be undone.",
                        color = colors.textSecondary,
                        fontSize = 14.sp
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        deleteAccountConfirm = false
                        SessionManager.deleteAccount()
                        onLogout()
                    }) { Text("Delete", color = Color.Red, fontWeight = FontWeight.SemiBold) }
                },
                dismissButton = {
                    TextButton(onClick = { deleteAccountConfirm = false }) {
                        Text("Cancel", color = colors.textPrimary)
                    }
                }
            )
        }
    }
}

@Composable
fun SettingsSection(title: String) {
    val colors = DenoTheme.colors
    Text(title, color = colors.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(20.dp, 16.dp, 20.dp, 4.dp))
}

@Composable
fun SettingsItem(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    val colors = DenoTheme.colors
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(20.dp, 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = colors.textPrimary)
        Spacer(Modifier.width(14.dp))
        Text(title, color = colors.textPrimary, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Icon(Icons.Default.ChevronRight, null, tint = colors.textSecondary)
    }
}

@Composable
fun SimpleInfoScreen(title: String, body: String, onBack: () -> Unit) {
    val colors = DenoTheme.colors

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
            Text(title, color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
        Divider(color = colors.border)
        Text(body, color = colors.textSecondary, fontSize = 14.sp, modifier = Modifier.padding(20.dp), lineHeight = 22.sp)
    }
}

// Settings > Privacy > Content Privacy. Toggles are backed by the persisted
// SessionManager state and immediately hide/show counts in the feed.
@Composable
fun ContentPrivacyScreen(onBack: () -> Unit) {
    val colors = DenoTheme.colors
    val privacy by SessionManager.contentPrivacy.collectAsState()

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
            Text("Content Privacy", color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
        Divider(color = colors.border)
        Text(
            "Choose what numbers other people can see on your content.",
            color = colors.textSecondary, fontSize = 13.sp,
            modifier = Modifier.padding(20.dp, 12.dp, 20.dp, 4.dp)
        )
        Row(
            Modifier.fillMaxWidth().padding(20.dp, 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Show like counts", color = colors.textPrimary, fontSize = 15.sp)
                Text("Hides the like count on your posts", color = colors.textSecondary, fontSize = 12.sp)
            }
            Switch(
                checked = privacy.showLikeCounts,
                onCheckedChange = { SessionManager.setContentPrivacy(privacy.copy(showLikeCounts = it)) },
                colors = SwitchDefaults.colors(checkedTrackColor = DenoBlue)
            )
        }
        Divider(color = colors.border)
        Row(
            Modifier.fillMaxWidth().padding(20.dp, 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Show comment counts", color = colors.textPrimary, fontSize = 15.sp)
                Text("Hides the comment count on your posts", color = colors.textSecondary, fontSize = 12.sp)
            }
            Switch(
                checked = privacy.showCommentCounts,
                onCheckedChange = { SessionManager.setContentPrivacy(privacy.copy(showCommentCounts = it)) },
                colors = SwitchDefaults.colors(checkedTrackColor = DenoBlue)
            )
        }
    }
}