@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.deno.social.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deno.social.data.mock.MockData
import com.deno.social.data.repository.MockUserRepository
import com.deno.social.data.repository.SessionManager
import com.deno.social.ui.components.Avatar
import com.deno.social.ui.components.DenoButton
import com.deno.social.ui.theme.DenoBlue
import com.deno.social.ui.theme.DenoTheme

@Composable
fun StoriesRow(onAddStory: () -> Unit = {}, onStoryClick: (String) -> Unit = {}) {
    val colors = DenoTheme.colors
    val users = MockData.users.filter { !it.isCurrentUser }.take(8)

    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box {
                    Box(
                        Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(colors.card)
                            .border(2.dp, colors.border, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Avatar(MockData.currentUser.avatarInitial, 56)
                    }
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(DenoBlue)
                            .clickable { onAddStory() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Add, null, tint = Color.White, modifier = Modifier.size(14.dp))
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text("Your story", color = colors.textSecondary, fontSize = 11.sp)
            }
        }
        items(users) { user ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable { onStoryClick(user.id) }
            ) {
                Box(
                    Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(listOf(DenoBlue, Color(0xFF00C6FF)))
                        )
                        .padding(3.dp)
                ) {
                    Box(
                        Modifier.fillMaxSize().clip(CircleShape).background(colors.background),
                        contentAlignment = Alignment.Center
                    ) {
                        Avatar(user.avatarInitial, 54)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(user.username.take(10), color = colors.textSecondary, fontSize = 11.sp, maxLines = 1)
            }
        }
    }
}

@Composable
fun ChangePasswordScreen(onBack: () -> Unit) {
    val colors = DenoTheme.colors
    var current by remember { mutableStateOf("") }
    var newPass by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(horizontal = 16.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, null, tint = colors.textPrimary)
            }
            Text("Change Password", color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
        Spacer(Modifier.height(24.dp))
        com.deno.social.ui.components.DenoTextField(current, { current = it }, "Current Password", isPassword = true)
        Spacer(Modifier.height(12.dp))
        com.deno.social.ui.components.DenoTextField(newPass, { newPass = it }, "New Password", isPassword = true)
        Spacer(Modifier.height(12.dp))
        com.deno.social.ui.components.DenoTextField(confirm, { confirm = it }, "Confirm New Password", isPassword = true)
        if (message.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(message, color = if (message.contains("success")) DenoBlue else Color.Red, fontSize = 13.sp)
        }
        Spacer(Modifier.height(28.dp))
        DenoButton("Update Password", {
            when {
                current.isBlank() || newPass.isBlank() -> message = "Please fill all fields"
                newPass != confirm -> message = "Passwords do not match"
                else -> {
                    // Validates "Current" against the stored password and
                    // persists the new one. Returns an error string or null.
                    val error = SessionManager.changePassword(current, newPass)
                    message = error ?: "Password updated successfully"
                }
            }
        }, Modifier.fillMaxWidth())
    }
}

@Composable
fun BlockedUsersScreen(onBack: () -> Unit) {
    val colors = DenoTheme.colors
    val blockedIds by SessionManager.blockedIds.collectAsState()
    val blocked = remember(blockedIds) {
        MockUserRepository().getUsers().filter { it.id in blockedIds }
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
            Text("Blocked Users", color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
        Divider(color = colors.border)
        if (blocked.isEmpty()) {
            com.deno.social.ui.components.EmptyState(
                Icons.Outlined.Block, "No blocked users", "Users you block will appear here"
            )
        } else {
            androidx.compose.foundation.lazy.LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 16.dp)
            ) {
                items(blocked.size) { i ->
                    val u = blocked[i]
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp, 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Avatar(u.avatarInitial, 44)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(u.fullName, color = colors.textPrimary, fontWeight = FontWeight.SemiBold)
                            Text("@${u.username}", color = colors.textSecondary, fontSize = 13.sp)
                        }
                        OutlinedButton(
                            onClick = { SessionManager.unblockUser(u.id) },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Unblock", color = colors.textPrimary, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}