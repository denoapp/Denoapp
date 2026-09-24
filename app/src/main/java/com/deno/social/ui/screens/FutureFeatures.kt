package com.deno.social.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deno.social.data.mock.MockData
import com.deno.social.data.repository.MockPostRepository
import com.deno.social.data.repository.MockReelRepository
import com.deno.social.data.repository.SessionManager
import com.deno.social.ui.components.Avatar
import com.deno.social.ui.components.DenoButton
import com.deno.social.ui.components.DenoTextField
import com.deno.social.ui.components.EmptyState
import com.deno.social.ui.components.formatCount
import com.deno.social.ui.components.shareContent
import com.deno.social.ui.theme.DenoBlue
import com.deno.social.ui.theme.DenoTheme

// ---------- GROUPS / COMMUNITIES ----------
data class GroupItem(val id: String, val name: String, val members: Int, val desc: String, val initial: String)

val mockGroups = listOf(
    GroupItem("g1", "Learn Daily", 12500, "Daily learning tips", "L"),
    GroupItem("g2", "Design Hub", 8900, "UI/UX & design", "D"),
    GroupItem("g3", "Code Creators", 15200, "Dev & coding", "C"),
    GroupItem("g4", "Motivation Circle", 6700, "Stay inspired", "M")
)

@Composable
fun GroupsScreen(onBack: () -> Unit, onGroupClick: (String) -> Unit = {}) {
    val colors = DenoTheme.colors
    val joined by SessionManager.joinedCommunities.collectAsState()

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        Row(Modifier.fillMaxWidth().padding(8.dp, 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = colors.textPrimary) }
            Text("Communities", color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
        Divider(color = colors.border)
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 16.dp)
        ) {
            items(mockGroups) { g ->
                val isJoined = g.id in joined
                Row(
                    Modifier.fillMaxWidth().clickable { onGroupClick(g.id) }.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).background(DenoBlue.copy(0.15f)), contentAlignment = Alignment.Center) {
                        Text(g.initial, color = DenoBlue, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(g.name, color = colors.textPrimary, fontWeight = FontWeight.SemiBold)
                        Text("${formatCount(g.members)} members · ${g.desc}", color = colors.textSecondary, fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = { SessionManager.toggleJoinedCommunity(g.id) },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            if (isJoined) "Joined" else "Join",
                            color = if (isJoined) colors.textSecondary else DenoBlue,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}

// ---------- HASHTAGS / TRENDING ----------
@Composable
fun TrendingScreen(onBack: () -> Unit) {
    val colors = DenoTheme.colors
    val trends = listOf(
        "#LearnOnDeno" to "12.4K posts",
        "#DesignTips" to "8.1K posts",
        "#CodeLife" to "15.2K posts",
        "#Motivation" to "22K posts",
        "#ShareKnowledge" to "6.7K posts",
        "#ReelIdeas" to "9.3K posts"
    )
    var openTag by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        Row(Modifier.fillMaxWidth().padding(8.dp, 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = colors.textPrimary) }
            Text("Trending", color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
        Divider(color = colors.border)
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 16.dp)
        ) {
            items(trends.size) { i ->
                val (tag, count) = trends[i]
                Row(Modifier.fillMaxWidth().clickable { openTag = tag }.padding(20.dp, 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${i + 1}", color = colors.textSecondary, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.width(28.dp))
                    Column(Modifier.weight(1f)) {
                        Text(tag, color = colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        Text(count, color = colors.textSecondary, fontSize = 12.sp)
                    }
                    Icon(Icons.Outlined.TrendingUp, null, tint = DenoBlue)
                }
            }
        }
    }

    openTag?.let { tag ->
        val posts = remember(tag) {
            MockPostRepository().getPosts().filter { it.text.contains(tag.trimStart('#'), ignoreCase = true) }
        }
        AlertDialog(
            onDismissRequest = { openTag = null },
            containerColor = colors.surface,
            title = { Text(tag, color = colors.textPrimary) },
            text = {
                if (posts.isEmpty()) {
                    Text("No posts match this topic yet.", color = colors.textSecondary, fontSize = 14.sp)
                } else {
                    LazyColumn(Modifier.heightIn(max = 420.dp)) {
                        items(posts, key = { it.id }) { p ->
                            Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Avatar(p.user.avatarInitial, 34)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(p.user.fullName, color = colors.textPrimary, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                                    Text(p.text, color = colors.textPrimary, fontSize = 13.sp, maxLines = 2)
                                    Text("${formatCount(p.likes)} likes · ${p.timeAgo}", color = colors.textSecondary, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { openTag = null }) { Text("Close", color = DenoBlue) }
            }
        )
    }
}

// ---------- ADVANCED SEARCH ----------
@Composable
fun AdvancedSearchScreen(onBack: () -> Unit, onUserClick: (String) -> Unit) {
    val colors = DenoTheme.colors
    var query by remember { mutableStateOf("") }
    var tab by remember { mutableStateOf(0) } // 0 users 1 posts 2 reels 3 tags
    val tabs = listOf("Users", "Posts", "Reels", "Tags")

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = colors.textPrimary) }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search everything...", color = colors.textSecondary) },
                singleLine = true,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = DenoBlue, unfocusedBorderColor = colors.border,
                    focusedTextColor = colors.textPrimary, unfocusedTextColor = colors.textPrimary, cursorColor = DenoBlue
                )
            )
        }
        TabRow(selectedTabIndex = tab, containerColor = colors.background, contentColor = DenoBlue) {
            tabs.forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) }) }
        }
        when (tab) {
            0 -> {
                val users = MockData.users.filter { !it.isCurrentUser && (query.isBlank() || it.fullName.contains(query, true) || it.username.contains(query, true)) }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 16.dp)
                ) {
                    items(users) { u ->
                        Row(Modifier.fillMaxWidth().clickable { onUserClick(u.id) }.padding(16.dp, 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Avatar(u.avatarInitial, 44)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(u.fullName, color = colors.textPrimary, fontWeight = FontWeight.SemiBold)
                                    if (u.followers > 10000) {
                                        Spacer(Modifier.width(4.dp))
                                        Icon(Icons.Filled.Verified, null, tint = DenoBlue, modifier = Modifier.size(16.dp))
                                    }
                                }
                                Text("@${u.username}", color = colors.textSecondary, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
            1, 2 -> EmptyState(Icons.Outlined.Search, "Search ${tabs[tab].lowercase()}", "Type to find ${tabs[tab].lowercase()}")
            3 -> {
                val tags = listOf("#Learn", "#Design", "#Code", "#Motivation", "#Reels").filter { query.isBlank() || it.contains(query, true) }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 16.dp)
                ) {
                    items(tags) { tag ->
                        Text(tag, color = DenoBlue, fontWeight = FontWeight.SemiBold, fontSize = 16.sp,
                            modifier = Modifier.fillMaxWidth().clickable { }.padding(20.dp, 14.dp))
                    }
                }
            }
        }
    }
}

// ---------- DRAFTS ----------
@Composable
fun DraftsScreen(onBack: () -> Unit) {
    val colors = DenoTheme.colors
    val drafts by SessionManager.drafts.collectAsState()
    var deleteId by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        Row(Modifier.fillMaxWidth().padding(8.dp, 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = colors.textPrimary) }
            Text("Drafts", color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
        Divider(color = colors.border)
        if (drafts.isEmpty()) {
            EmptyState(Icons.Outlined.Drafts, "No drafts", "Save a draft in Create Post to see it here")
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 16.dp)
            ) {
                items(drafts, key = { it.id }) { d ->
                    Row(Modifier.fillMaxWidth().clickable { deleteId = d.id }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Article, null, tint = colors.textSecondary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(d.text.ifBlank { "Media only draft" }, color = colors.textPrimary, fontSize = 14.sp, maxLines = 2)
                            Text(
                                relativeTime(d.createdAt) + if (d.mediaCount > 0) " · ${d.mediaCount} media" else "",
                                color = colors.textSecondary,
                                fontSize = 12.sp
                            )
                        }
                        Icon(Icons.Default.MoreVert, null, tint = colors.textSecondary)
                    }
                }
            }
        }
    }

    deleteId?.let { id ->
        AlertDialog(
            onDismissRequest = { deleteId = null },
            containerColor = colors.surface,
            title = { Text("Delete draft?", color = colors.textPrimary) },
            text = { Text("This draft will be permanently removed.", color = colors.textSecondary, fontSize = 14.sp) },
            confirmButton = {
                TextButton(onClick = {
                    SessionManager.removeDraft(id)
                    deleteId = null
                }) { Text("Delete", color = Color.Red, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { deleteId = null }) { Text("Cancel", color = colors.textPrimary) }
            }
        )
    }
}

// ---------- SCHEDULED POSTS ----------
@Composable
fun ScheduledPostsScreen(onBack: () -> Unit) {
    val colors = DenoTheme.colors
    val scheduled by SessionManager.scheduledPosts.collectAsState()
    var deleteId by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        Row(Modifier.fillMaxWidth().padding(8.dp, 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = colors.textPrimary) }
            Text("Scheduled", color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
        Divider(color = colors.border)
        if (scheduled.isEmpty()) {
            EmptyState(Icons.Outlined.Schedule, "No scheduled posts", "Schedule a post in Create Post")
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 16.dp)
            ) {
                items(scheduled, key = { it.id }) { s ->
                    Row(Modifier.fillMaxWidth().clickable { deleteId = s.id }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Schedule, null, tint = colors.textSecondary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(s.text.ifBlank { "Media only post" }, color = colors.textPrimary, fontSize = 14.sp, maxLines = 2)
                            Text(
                                "Scheduled in ${relativeTime(s.scheduledAt)}" + if (s.mediaCount > 0) " · ${s.mediaCount} media" else "",
                                color = colors.textSecondary,
                                fontSize = 12.sp
                            )
                        }
                        Icon(Icons.Default.MoreVert, null, tint = colors.textSecondary)
                    }
                }
            }
        }
    }

    deleteId?.let { id ->
        AlertDialog(
            onDismissRequest = { deleteId = null },
            containerColor = colors.surface,
            title = { Text("Remove scheduled post?", color = colors.textPrimary) },
            text = { Text("The scheduled post will be cancelled.", color = colors.textSecondary, fontSize = 14.sp) },
            confirmButton = {
                TextButton(onClick = {
                    SessionManager.removeScheduledPost(id)
                    deleteId = null
                }) { Text("Remove", color = Color.Red, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { deleteId = null }) { Text("Cancel", color = colors.textPrimary) }
            }
        )
    }
}

// ---------- ANALYTICS ----------
@Composable
fun AnalyticsScreen(onBack: () -> Unit) {
    val colors = DenoTheme.colors
    // Real, locally derived numbers from the account's own content + follow
    // graph. No hardcoded fake stats.
    val followerIds by SessionManager.followerIds.collectAsState()
    val followingIds by SessionManager.followingIds.collectAsState()
    val myPosts = remember { MockPostRepository().getPosts().filter { it.user.isCurrentUser } }
    val myReels = remember { MockReelRepository().getReels().filter { it.user.isCurrentUser } }
    val totalLikes = remember(myPosts) { myPosts.sumOf { it.likes } }
    val totalComments = remember(myPosts) { myPosts.sumOf { it.comments } }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(horizontal = 16.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = colors.textPrimary) }
            Text("Analytics", color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(formatCount(myPosts.size), "Posts", Modifier.weight(1f))
            StatCard(formatCount(totalLikes), "Likes", Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(formatCount(totalComments), "Comments", Modifier.weight(1f))
            StatCard(formatCount(myReels.size), "Reels", Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(formatCount(followerIds.size), "Followers", Modifier.weight(1f))
            StatCard(formatCount(followingIds.size), "Following", Modifier.weight(1f))
        }
        Spacer(Modifier.height(24.dp))
        Text("Calculated live from your account data", color = colors.textSecondary, fontSize = 13.sp)
    }
}

@Composable
fun StatCard(value: String, label: String, modifier: Modifier = Modifier) {
    val colors = DenoTheme.colors
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).background(colors.card).padding(16.dp)
    ) {
        Text(value, color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 22.sp)
        Text(label, color = colors.textSecondary, fontSize = 13.sp)
    }
}

// ---------- INVITE FRIENDS ----------
@Composable
fun InviteFriendsScreen(onBack: () -> Unit) {
    val colors = DenoTheme.colors
    val context = LocalContext.current
    val link = "https://deno.app/invite/alex_deno"
    var copied by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(horizontal = 16.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = colors.textPrimary) }
            Text("Invite Friends", color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
        Spacer(Modifier.height(24.dp))
        Text("Share DENO with friends", color = colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text("Invite link: $link", color = DenoBlue, fontSize = 14.sp)
        Spacer(Modifier.height(24.dp))
        DenoButton("Copy Invite Link", onClick = {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("DENO invite link", link))
            copied = true
        }, modifier = Modifier.fillMaxWidth())
        if (copied) {
            Spacer(Modifier.height(8.dp))
            Text("Invite link copied to clipboard", color = DenoBlue, fontSize = 13.sp)
        }
        Spacer(Modifier.height(12.dp))
        DenoButton(
            "Share via...",
            onClick = { shareContent(context, "Join me on DENO\n$link") },
            modifier = Modifier.fillMaxWidth(),
            primary = false
        )
    }
}

// ---------- ACCOUNT RECOVERY ----------
@Composable
fun AccountRecoveryScreen(onBack: () -> Unit) {
    val colors = DenoTheme.colors
    val storedEmail by SessionManager.recoveryEmail.collectAsState()
    val storedPhone by SessionManager.recoveryPhone.collectAsState()
    var email by remember { mutableStateOf(storedEmail) }
    var phone by remember { mutableStateOf(storedPhone) }
    var msg by remember { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(horizontal = 16.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = colors.textPrimary) }
            Text("Account Recovery", color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
        Spacer(Modifier.height(16.dp))
        Text("Used to recover your account if you ever lose access.", color = colors.textSecondary, fontSize = 14.sp)
        Spacer(Modifier.height(20.dp))
        DenoTextField(email, { email = it; msg = "" }, "Recovery Email")
        Spacer(Modifier.height(12.dp))
        DenoTextField(phone, { phone = it; msg = "" }, "Phone Number")
        if (msg.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(msg, color = DenoBlue, fontSize = 13.sp)
        }
        Spacer(Modifier.height(24.dp))
        DenoButton("Save Recovery Options", {
            SessionManager.saveRecovery(email, phone)
            msg = "Recovery options saved"
        }, Modifier.fillMaxWidth())
    }
}

// ---------- MODERATION (Report queue UI) ----------
@Composable
fun ModerationScreen(onBack: () -> Unit) {
    val colors = DenoTheme.colors
    // The queue is fed by the reports submitted from any user profile, so it
    // reflects real, locally stored activity instead of static demo rows.
    val reports by SessionManager.reports.collectAsState()
    var reviewing by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        Row(Modifier.fillMaxWidth().padding(8.dp, 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = colors.textPrimary) }
            Text("Moderation", color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
        Divider(color = colors.border)
        if (reports.isEmpty()) {
            EmptyState(Icons.Outlined.Flag, "No reports", "Reports you submit will appear here")
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 16.dp)
            ) {
                items(reports.keys.toList()) { userId ->
                    val reason = reports[userId] ?: ""
                    val username = MockData.users.find { it.id == userId }?.username ?: userId
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Flag, null, tint = Color.Red)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Reported @$username", color = colors.textPrimary, fontWeight = FontWeight.Medium)
                            Text(reason, color = colors.textSecondary, fontSize = 12.sp)
                        }
                        TextButton(onClick = { reviewing = userId }) { Text("Review", color = DenoBlue) }
                    }
                }
            }
        }
    }

    reviewing?.let { userId ->
        val reason = reports[userId] ?: ""
        val username = MockData.users.find { it.id == userId }?.username ?: userId
        AlertDialog(
            onDismissRequest = { reviewing = null },
            containerColor = colors.surface,
            title = { Text("Report review", color = colors.textPrimary) },
            text = {
                Text(
                    "@$username was reported.\nReason: $reason",
                    color = colors.textSecondary,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    SessionManager.removeReport(userId)
                    reviewing = null
                }) { Text("Dismiss", color = Color.Red, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { reviewing = null }) { Text("Close", color = colors.textPrimary) }
            }
        )
    }
}

// ---------- LANGUAGE ----------
@Composable
fun LanguageScreen(onBack: () -> Unit) {
    val colors = DenoTheme.colors
    val languages = listOf("English", "हिन्दी", "اردو", "Español", "Français", "العربية")
    val selected by SessionManager.appLanguage.collectAsState()

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        Row(Modifier.fillMaxWidth().padding(8.dp, 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = colors.textPrimary) }
            Text("Language", color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
        Divider(color = colors.border)
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 16.dp)
        ) {
            items(languages) { lang ->
                Row(
                    Modifier.fillMaxWidth().clickable { SessionManager.setAppLanguage(lang) }.padding(20.dp, 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(lang, color = colors.textPrimary, fontSize = 16.sp)
                    if (selected == lang) Icon(Icons.Default.Check, null, tint = DenoBlue)
                }
            }
        }
        Text("Selected language is saved on this device.", color = colors.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(16.dp))
    }
}