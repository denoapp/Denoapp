package com.deno.social.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deno.social.data.repository.MockPostRepository
import com.deno.social.data.repository.MockReelRepository
import com.deno.social.data.repository.SessionManager
import com.deno.social.ui.components.EmptyState
import com.deno.social.ui.theme.DenoTheme

// ---------- SAVED ----------
// Live grid of the user's bookmarked posts + reels. Reads the same persisted
// Saved state as the Feed/Reels bookmark buttons, so saves survive restarts.
@Composable
fun SavedScreen(onBack: () -> Unit) {
    val colors = DenoTheme.colors
    val savedPostIds by SessionManager.savedPostIds.collectAsState()
    val savedReelIds by SessionManager.savedReelIds.collectAsState()
    val savedPosts = remember(savedPostIds) { MockPostRepository().getPosts().filter { it.id in savedPostIds } }
    val savedReels = remember(savedReelIds) { MockReelRepository().getReels().filter { it.id in savedReelIds } }
    val total = savedPosts.size + savedReels.size

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
            Text("Saved", color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
        Divider(color = colors.border)
        if (total == 0) {
            EmptyState(Icons.Outlined.BookmarkBorder, "No saved posts", "Bookmark posts and reels to see them here")
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 16.dp),
                contentPadding = PaddingValues(1.dp)
            ) {
                items(savedPosts, key = { it.id }) { p ->
                    BookmarkCell(Icons.Outlined.Image, p.text, colors.textSecondary)
                }
                items(savedReels, key = { it.id }) { r ->
                    BookmarkCell(Icons.Outlined.PlayCircle, r.caption, colors.textSecondary)
                }
            }
        }
    }
}

@Composable
private fun BookmarkCell(icon: ImageVector, label: String, tint: Color) {
    Box(
        Modifier.aspectRatio(1f).padding(1.dp).background(tint.copy(0.12f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(28.dp))
    }
}