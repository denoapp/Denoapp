package com.deno.social.ui.screens

import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.PersonRemove
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deno.social.data.mock.MockData
import com.deno.social.data.model.ChatMessage
import com.deno.social.data.model.MessageGroup
import com.deno.social.data.model.User
import com.deno.social.data.repository.MockMessagingRepository
import com.deno.social.data.repository.MockUserRepository
import com.deno.social.ui.components.Avatar
import com.deno.social.ui.components.DenoButton
import com.deno.social.ui.components.EmptyState
import com.deno.social.ui.components.MessageAttachSheet
import com.deno.social.ui.components.MessageAttachmentPreview
import com.deno.social.ui.components.MessageComposer
import com.deno.social.ui.components.MessageRecordingBar
import com.deno.social.ui.components.VoiceRecorder
import com.deno.social.ui.components.ChatPendingAttachment
import com.deno.social.ui.components.formatCount
import com.deno.social.ui.theme.DenoBlue
import com.deno.social.ui.theme.DenoTheme
import androidx.core.content.FileProvider
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
fun MessageGroupsTab(
    externalQuery: String?,
    onCreateGroup: () -> Unit,
    onOpenGroup: (String) -> Unit
) {
    val colors = DenoTheme.colors
    val repo = remember { MockMessagingRepository() }
    var groups by remember { mutableStateOf(repo.getGroups()) }
    var tabQuery by remember { mutableStateOf("") }

    val effectiveQuery = externalQuery ?: tabQuery
    val filtered = remember(effectiveQuery, groups) {
        if (effectiveQuery.isBlank()) groups
        else groups.filter {
            it.name.contains(effectiveQuery, true) ||
                it.description.contains(effectiveQuery, true) ||
                it.topic.contains(effectiveQuery, true) ||
                it.lastMessage.contains(effectiveQuery, true)
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (externalQuery == null) {
                DenoGroupSearchField(
                    value = tabQuery,
                    onValueChange = { tabQuery = it },
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
            }
            Button(
                onClick = onCreateGroup,
                modifier = Modifier.height(40.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DenoBlue,
                    contentColor = Color.White
                ),
                contentPadding = PaddingValues(horizontal = 16.dp)
            ) {
                Text("Create Group", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        when {
            filtered.isEmpty() && groups.isEmpty() ->
                EmptyState(
                    Icons.Outlined.Groups,
                    "No groups yet",
                    "Create a group to start an Islamic discussion."
                )
            filtered.isEmpty() ->
                EmptyState(
                    Icons.Outlined.SearchOff,
                    "No groups found",
                    "No groups match your search."
                )
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 8.dp)
            ) {
                itemsIndexed(filtered) { index, group ->
                    GroupRow(
                        group = group,
                        onOpen = { onOpenGroup(group.id) },
                        onJoin = {
                            repo.joinGroup(group.id)
                            groups = repo.getGroups()
                        }
                    )
                    if (index < filtered.lastIndex) {
                        Divider(color = denoMsgColors().divider, modifier = Modifier.padding(start = 76.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun DenoGroupSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search groups"
) {
    val colors = DenoTheme.colors
    Row(
        modifier
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (colors.isDark) colors.card else Color(0xFFF3F3F5)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Outlined.Search,
            null,
            tint = colors.textSecondary,
            modifier = Modifier.padding(start = 14.dp).size(18.dp)
        )
        androidx.compose.foundation.text.BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = colors.textPrimary, fontSize = 14.sp),
            cursorBrush = SolidColor(DenoBlue),
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 4.dp),
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    Text(placeholder, color = colors.textSecondary, fontSize = 14.sp, maxLines = 1)
                }
                inner()
            }
        )
        if (value.isNotEmpty()) {
            Icon(
                Icons.Default.Close,
                "Clear",
                tint = colors.textSecondary,
                modifier = Modifier.padding(end = 14.dp).size(16.dp).clickable { onValueChange("") }
            )
        }
    }
}

@Composable
private fun GroupAvatar(initial: String, size: Int, photoUri: String = "") {
    val colors = DenoTheme.colors
    val context = LocalContext.current
    val bitmap = produceState<ImageBitmap?>(initialValue = null, photoUri) {
        value = if (photoUri.isBlank()) null else runCatching {
            withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(Uri.parse(photoUri))?.use {
                    BitmapFactory.decodeStream(it)
                }?.asImageBitmap()
            }
        }.getOrNull()
    }.value

    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(if (colors.isDark) colors.card else Color(0xFFE3F2FD)),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(
                initial.take(2).uppercase(),
                color = DenoBlue,
                fontWeight = FontWeight.Bold,
                fontSize = (size / 2.6).sp
            )
        }
    }
}

@Composable
private fun GroupRow(
    group: MessageGroup,
    onOpen: () -> Unit,
    onJoin: () -> Unit
) {
    val colors = DenoTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onOpen() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GroupAvatar(group.initial.ifEmpty { group.name.first().toString() }, 52, group.photoUri)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    group.name,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    group.lastActivity,
                    color = colors.textSecondary,
                    fontSize = 12.sp
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                group.description,
                color = colors.textSecondary,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Groups,
                    null,
                    tint = colors.textSecondary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(3.dp))
                Text(
                    "${formatCount(group.memberCount)} members",
                    color = colors.textSecondary,
                    fontSize = 12.sp
                )
                if (group.lastMessage.isNotBlank()) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        group.lastMessage,
                        color = colors.textSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
            }
        }
        if (group.isJoined) {
            Spacer(Modifier.width(10.dp))
            Text(
                "Joined",
                color = colors.textSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        } else {
            Text(
                "Join",
                color = DenoBlue,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clickable { onJoin() }
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (colors.isDark) colors.card else Color(0xFFE3F2FD))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            )
        }
    }
}

// --------------------------- GROUP CHAT ---------------------------

@Composable
fun GroupChatScreen(groupId: String, onBack: () -> Unit, onGroupInfo: () -> Unit) {
    val colors = DenoTheme.colors
    val msg = denoMsgColors()
    val repo = remember { MockMessagingRepository() }
    val group = remember(groupId) { repo.getGroup(groupId) }
    var messages by remember(groupId) { mutableStateOf(group?.messages.orEmpty()) }
    var isJoined by remember(groupId) { mutableStateOf(group?.isJoined ?: false) }
    var memberCount by remember(groupId) { mutableStateOf(group?.memberCount ?: 1) }
    var text by remember { mutableStateOf("") }
    var replyingTo by remember { mutableStateOf<ChatMessage?>(null) }
    var editing by remember(groupId) { mutableStateOf<ChatMessage?>(null) }
    var showAttach by remember { mutableStateOf(false) }
    var attachment by remember { mutableStateOf<ChatPendingAttachment?>(null) }
    var isRecording by remember { mutableStateOf(false) }
    var recordingSeconds by remember { mutableStateOf(0) }
    val context = LocalContext.current
    val listState = rememberLazyListState()

    val voiceRecorder = remember { VoiceRecorder(context) }
    val recordPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val started = voiceRecorder.start()
            if (started) {
                isRecording = true
                recordingSeconds = 0
            } else {
                Toast.makeText(context, "Could not start recording", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Microphone permission required", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(isRecording) {
        if (isRecording) {
            while (isRecording) {
                delay(1000)
                if (isRecording) recordingSeconds++
            }
        }
    }

    val sendMessageWithAttachment: (ChatPendingAttachment?) -> Unit = { attach ->
        val replyText = replyingTo?.text.orEmpty()
        val sent: ChatMessage
        if (attach != null) {
            if (attach.isAudio) {
                sent = repo.sendGroupMessage(groupId, text, replyText, "", attach.uri, attach.durationMs)
            } else {
                sent = repo.sendGroupMessage(groupId, text, replyText, attach.uri)
            }
        } else {
            sent = repo.sendGroupMessage(groupId, text, replyText, "")
        }
        messages = messages + sent
        text = ""
        replyingTo = null
        attachment = null
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        bitmap?.let {
            val file = File(context.cacheDir, "deno_attach_${System.currentTimeMillis()}.jpg")
            file.outputStream().use { it.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            attachment = ChatPendingAttachment(
                uri = file.absolutePath,
                name = file.name,
                isImage = true,
                isAudio = false
            )
            showAttach = false
        }
    }

    val photosLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let {
            val name = fileDisplayName(context, it.toString())
            attachment = ChatPendingAttachment(
                uri = it.toString(),
                name = name,
                isImage = true,
                isAudio = false
            )
            showAttach = false
        }
    }

    val filesLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            val name = fileDisplayName(context, it.toString())
            attachment = ChatPendingAttachment(
                uri = it.toString(),
                name = name,
                isImage = false,
                isAudio = false
            )
            showAttach = false
        }
    }

    val groupName = group?.name ?: "Group"
    val initial = group?.initial ?: "G"
    val topic = group?.topic ?: ""
    val isAdmin = group?.adminIds?.contains(MockData.currentUser.id) == true
    val canSend = isAdmin || (group?.membersCanSend ?: true)
    val subtitle = buildString {
        append("${formatCount(memberCount)} members")
        if (isAdmin) append(" · Admin")
        if (topic.isNotBlank()) append(" · $topic")
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.scrollToItem(messages.lastIndex)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(msg.background)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, null, tint = colors.textPrimary)
            }
            GroupAvatar(initial, 40, group?.photoUri.orEmpty())
            Spacer(Modifier.width(10.dp))
            Column(
                Modifier.weight(1f).clickable { onGroupInfo() },
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    groupName,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    subtitle,
                    color = msg.muted,
                    fontSize = 12.sp
                )
            }
            IconButton(onClick = onGroupInfo) {
                Icon(Icons.Outlined.Info, "Group info", tint = msg.muted)
            }
        }

        if (!isJoined) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(msg.surface)
                    .padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "You haven't joined this group yet.",
                    color = msg.muted,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "Join",
                    color = DenoBlue,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clickable {
                            repo.joinGroup(groupId)
                            isJoined = true
                            memberCount += 1
                        }
                        .clip(RoundedCornerShape(16.dp))
                        .background(DenoBlue.copy(alpha = 0.12f))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
        }
        Divider(color = msg.divider)

        if (messages.isEmpty() && isJoined) {
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Outlined.Groups,
                    null,
                    tint = msg.muted,
                    modifier = Modifier.size(44.dp)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Start the conversation",
                    color = colors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Begin the discussion in $groupName.",
                    color = msg.muted,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                contentPadding = PaddingValues(vertical = 10.dp)
            ) {
                itemsIndexed(messages) { index, message ->
                    val prev = messages.getOrNull(index - 1)
                    MessageBubble(
                        msg = message,
                        senderName = message.sender.takeIf { !it.isBlank() && !message.isMe },
                        modifier = Modifier.padding(
                            top = if (index > 0 && prev?.isMe == message.isMe) 2.dp else 8.dp
                        ),
                        onReply = { replyingTo = it },
                        onEdit = {
                            editing = it
                            text = it.text
                        },
                        onUnsend = {
                            repo.deleteGroupMessage(groupId, it.id)
                            messages = messages.filterNot { m -> m.id == it.id }
                            if (editing?.id == it.id) editing = null
                        }
                    )
                }
            }
        }

        when {
            !isJoined -> {
                Box(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Join the group to send messages.",
                        color = msg.muted,
                        fontSize = 13.sp
                    )
                }
            }
            canSend -> {
                replyingTo?.let { target ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(msg.surface)
                            .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Replying to ${target.sender.takeIf { it.isNotBlank() } ?: "message"}",
                                color = colors.textPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                target.text,
                                color = msg.muted,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(onClick = { replyingTo = null }) {
                            Icon(
                                Icons.Default.Close,
                                "Cancel reply",
                                tint = msg.muted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
                if (editing != null) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(msg.surface)
                            .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Editing message",
                                color = colors.textPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                editing?.text.orEmpty(),
                                color = msg.muted,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(onClick = { editing = null; text = "" }) {
                            Icon(
                                Icons.Default.Close,
                                "Cancel edit",
                                tint = msg.muted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
                MessageComposer(
                    value = text,
                    onValueChange = { text = it },
                    onSend = {
                        val editTarget = editing
                        if (editTarget != null) {
                            val updated = repo.editGroupMessage(groupId, editTarget.id, text)
                            if (updated != null) {
                                messages = messages.map { if (it.id == updated.id) updated else it }
                            }
                            editing = null
                            text = ""
                        } else if (isJoined && text.isNotBlank()) {
                            val sent = repo.sendGroupMessage(groupId, text, replyingTo?.text.orEmpty())
                            messages = messages + sent
                            text = ""
                            replyingTo = null
                        }
                    },
                    placeholder = "Message...",
                    onAttach = { showAttach = true }
                )
                if (showAttach) {
                    MessageAttachSheet(
                        onCamera = {
                            showAttach = false
                            cameraLauncher.launch(null)
                        },
                        onPhotos = {
                            showAttach = false
                            photosLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        onFiles = {
                            showAttach = false
                            filesLauncher.launch(arrayOf("*/*"))
                        },
                        onDismiss = { showAttach = false }
                    )
                }
            }
            else -> {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.Info,
                        null,
                        tint = msg.muted,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Only admins can send messages in this group.",
                        color = msg.muted,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

// --------------------------- GROUP INFO ---------------------------

@Composable
fun GroupInfoScreen(
    groupId: String,
    addedMemberIds: List<String>,
    onAddedMembersConsumed: () -> Unit,
    onAddMembers: () -> Unit,
    onEditGroup: () -> Unit,
    onBack: () -> Unit,
    onGroupDeleted: () -> Unit,
    onLeftGroup: () -> Unit
) {
    val colors = DenoTheme.colors
    val repo = remember { MockMessagingRepository() }
    var group by remember(groupId) { mutableStateOf(repo.getGroup(groupId)) }
    val me = MockData.currentUser.id
    val isAdmin = group?.adminIds?.contains(me) == true
    val isCreator = group?.creatorId == me

    var memberActionTarget by remember { mutableStateOf<User?>(null) }
    var settingsExpanded by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf(false) }
    var pendingLeave by remember { mutableStateOf(false) }

    val allUsers = remember {
        listOf(MockData.currentUser) + MockUserRepository().getUsers().sortedBy { it.fullName }
    }
    val members = remember(allUsers, group) {
        allUsers.filter { it.id in (group?.memberIds.orEmpty()) }
    }

    LaunchedEffect(groupId, addedMemberIds) {
        val added = addedMemberIds.filter { it.isNotBlank() }
        if (added.isNotEmpty()) {
            repo.addMembers(groupId, added)
            group = repo.getGroup(groupId)
            onAddedMembersConsumed()
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(denoMsgColors().background),
        contentPadding = PaddingValues(bottom = 20.dp)
    ) {
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, null, tint = colors.textPrimary)
                }
                Text(
                    "Group Info",
                    color = colors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp
                )
            }
            Divider(color = denoMsgColors().divider)
        }

        item {
            Column(
                Modifier.fillMaxWidth().padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                GroupAvatar(group?.initial?.ifEmpty { null } ?: "G", 88, group?.photoUri.orEmpty())
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        group?.name ?: "Group",
                        color = colors.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 20.sp,
                        maxLines = 2,
                        textAlign = TextAlign.Center
                    )
                    if (isAdmin) {
                        Spacer(Modifier.width(8.dp))
                        AdminBadge("Admin")
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "${formatCount(group?.memberCount ?: 1)} members",
                    color = colors.textSecondary,
                    fontSize = 13.sp
                )
                if (!group?.description.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        group?.description.orEmpty(),
                        color = colors.textSecondary,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
            Divider(color = denoMsgColors().divider)
        }

        if (isAdmin) {
            item {
                Spacer(Modifier.height(6.dp))
                InfoActionRow("Edit Group", Icons.Outlined.Edit) { onEditGroup() }
                MenuDivider()
                InfoActionRow("Add Members", Icons.Outlined.PersonAdd) { onAddMembers() }
                MenuDivider()
                InfoActionRow("Group Settings", Icons.Outlined.Settings, expandable = true, expanded = settingsExpanded) {
                    settingsExpanded = !settingsExpanded
                }
                if (settingsExpanded && group != null) {
                    GroupSettingsBlock(
                        membersCanSend = group!!.membersCanSend,
                        membersCanAddParticipants = group!!.membersCanAddParticipants,
                        onSettingsChanged = { mcs, mca ->
                            repo.updateGroupSettings(groupId, mcs, mca)
                            group = repo.getGroup(groupId)
                        }
                    )
                }
                Spacer(Modifier.height(6.dp))
                Divider(color = denoMsgColors().divider)
            }
        }

        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Members",
                    color = colors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "${members.size}",
                    color = colors.textSecondary,
                    fontSize = 13.sp
                )
            }
        }

        items(members, key = { it.id }) { user ->
            val isMemberAdmin = group?.adminIds?.contains(user.id) == true
            val actionable = isAdmin && user.id != me && user.id != group?.creatorId
            MemberInfoRow(
                user = user,
                isAdmin = isMemberAdmin,
                onClick = if (actionable) ({ memberActionTarget = user }) else null
            )
            Divider(color = denoMsgColors().divider, modifier = Modifier.padding(start = 72.dp))
        }

        item { Spacer(Modifier.height(12.dp)) }

        if (isAdmin) {
            item {
                DangerActionRow("Delete Group", Icons.Outlined.Delete) { pendingDelete = true }
            }
        } else if (group != null && !isCreator) {
            item {
                DangerActionRow("Leave Group", Icons.Outlined.Logout) { pendingLeave = true }
            }
        }
    }

    memberActionTarget?.let { user ->
        val isMemberAdmin = group?.adminIds?.contains(user.id) == true
        MemberActionPopup(
            user = user,
            isMemberAdmin = isMemberAdmin,
            onMakeAdmin = {
                repo.makeAdmin(groupId, user.id)
                group = repo.getGroup(groupId)
                memberActionTarget = null
            },
            onRemove = {
                repo.removeMember(groupId, user.id)
                group = repo.getGroup(groupId)
                memberActionTarget = null
            },
            onDismiss = { memberActionTarget = null }
        )
    }

    if (pendingDelete) {
        AlertDialog(
            onDismissRequest = { pendingDelete = false },
            title = { Text("Delete Group?") },
            text = {
                Text("This permanently deletes ${group?.name ?: "this group"} for everyone. This cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = false
                        repo.deleteGroup(groupId)
                        onGroupDeleted()
                    }
                ) { Text("Delete", color = Color.Red) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = false }) { Text("Cancel") }
            },
            containerColor = if (colors.isDark) colors.card else Color.White
        )
    }

    if (pendingLeave) {
        AlertDialog(
            onDismissRequest = { pendingLeave = false },
            title = { Text("Leave Group?") },
            text = { Text("You'll stop receiving messages from ${group?.name ?: "this group"}.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingLeave = false
                        repo.leaveGroup(groupId)
                        onLeftGroup()
                    }
                ) { Text("Leave", color = Color.Red) }
            },
            dismissButton = {
                TextButton(onClick = { pendingLeave = false }) { Text("Cancel") }
            },
            containerColor = if (colors.isDark) colors.card else Color.White
        )
    }
}

// --------------------------- EDIT GROUP ---------------------------

@Composable
fun EditGroupScreen(groupId: String, onBack: () -> Unit, onSaved: () -> Unit) {
    val colors = DenoTheme.colors
    val repo = remember { MockMessagingRepository() }
    val group = remember(groupId) { repo.getGroup(groupId) }
    val context = LocalContext.current
    var name by rememberSaveable(groupId) { mutableStateOf(group?.name ?: "") }
    var description by rememberSaveable(groupId) { mutableStateOf(group?.description ?: "") }
    var photoUri by rememberSaveable(groupId) { mutableStateOf<String?>(group?.photoUri?.takeIf { it.isNotBlank() }) }
    val scrollState = rememberScrollState()

    val photoBitmap = produceState<ImageBitmap?>(initialValue = null, photoUri) {
        val uriString = photoUri
        value = if (uriString == null) null else runCatching {
            withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(Uri.parse(uriString))?.use {
                    BitmapFactory.decodeStream(it)
                }?.asImageBitmap()
            }
        }.getOrNull()
    }.value

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) photoUri = uri.toString()
    }

    val nameOk = name.isNotBlank()

    Column(
        Modifier
            .fillMaxSize()
            .background(denoMsgColors().background)
    ) {
        // --------------------------- TOP BAR ---------------------------
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, null, tint = colors.textPrimary)
            }
            Text(
                "Edit Group",
                color = colors.textPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp
            )
        }
        Divider(color = denoMsgColors().divider)

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp)
        ) {
            // --------------------------- GROUP PHOTO ---------------------------
            Spacer(Modifier.height(28.dp))
            Box(
                Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier
                        .size(110.dp)
                        .clip(CircleShape)
                        .background(if (colors.isDark) colors.card else Color(0xFFE3F2FD))
                        .clickable {
                            photoPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (photoBitmap != null) {
                        Image(
                            bitmap = photoBitmap,
                            contentDescription = "Group photo",
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text(
                            name.take(2).uppercase().ifEmpty { "G" },
                            color = DenoBlue,
                            fontWeight = FontWeight.Bold,
                            fontSize = 40.sp
                        )
                    }
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(DenoBlue)
                            .border(2.dp, colors.background, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Outlined.CameraAlt,
                            null,
                            tint = Color.White,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Change group photo",
                color = DenoBlue,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .clickable {
                        photoPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
            )

            // --------------------------- GROUP NAME ---------------------------
            Spacer(Modifier.height(28.dp))
            GroupInputField(
                value = name,
                onValueChange = { name = it },
                placeholder = "Group name",
                maxLength = 60,
                showCounter = true,
                errorText = if (name.isBlank()) "Group name is required" else ""
            )

            // --------------------------- DESCRIPTION ---------------------------
            Spacer(Modifier.height(20.dp))
            GroupInputField(
                value = description,
                onValueChange = { description = it },
                placeholder = "Group description",
                singleLine = false,
                maxLength = 200
            )
            Spacer(Modifier.height(24.dp))
        }

        // --------------------------- SAVE BUTTON ---------------------------
        Divider(color = denoMsgColors().divider)
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .imePadding()
        ) {
            DenoButton(
                "Save Changes",
                onClick = {
                    if (nameOk) {
                        repo.updateGroup(groupId, name, description, photoUri.orEmpty())
                        onSaved()
                    }
                },
                enabled = nameOk,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// --------------------------- GROUP INFO HELPERS ---------------------------

@Composable
private fun AdminBadge(label: String) {
    Text(
        label,
        color = DenoBlue,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(DenoBlue.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}

@Composable
private fun MenuDivider() {
    Divider(color = DenoTheme.colors.border, modifier = Modifier.padding(start = 60.dp))
}

@Composable
private fun InfoActionRow(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    expandable: Boolean = false,
    expanded: Boolean = false,
    onClick: () -> Unit
) {
    val colors = DenoTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(if (colors.isDark) colors.card else Color(0xFFF3F3F5)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, title, tint = DenoBlue, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(
            title,
            color = colors.textPrimary,
            fontWeight = FontWeight.Medium,
            fontSize = 15.sp,
            modifier = Modifier.weight(1f)
        )
        if (expandable) {
            Icon(
                Icons.Default.ChevronRight,
                null,
                tint = colors.textSecondary,
                modifier = Modifier
                    .size(22.dp)
                    .rotate(if (expanded) 90f else 0f)
            )
        }
    }
}

@Composable
private fun GroupSettingsBlock(
    membersCanSend: Boolean,
    membersCanAddParticipants: Boolean,
    onSettingsChanged: (membersCanSend: Boolean, membersCanAddParticipants: Boolean) -> Unit
) {
    val colors = DenoTheme.colors
    Column(Modifier.fillMaxWidth()) {
        if (colors.isDark) Spacer(Modifier.height(2.dp))
        SettingsRow(
            title = "Members can send messages",
            subtitle = if (membersCanSend) "Anyone can post" else "Only admins can post",
            checked = membersCanSend,
            onCheckedChange = { onSettingsChanged(it, membersCanAddParticipants) }
        )
        MenuDivider()
        SettingsRow(
            title = "Members can add participants",
            subtitle = if (membersCanAddParticipants) "Members can invite" else "Only admins can invite",
            checked = membersCanAddParticipants,
            onCheckedChange = { onSettingsChanged(membersCanSend, it) }
        )
    }
}

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val colors = DenoTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = colors.textPrimary,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp
            )
            Spacer(Modifier.height(2.dp))
            Text(
                subtitle,
                color = colors.textSecondary,
                fontSize = 12.sp
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = DenoBlue,
                uncheckedThumbColor = colors.textSecondary,
                uncheckedTrackColor = if (colors.isDark) colors.border else Color(0xFFE3E3E8)
            )
        )
    }
}

@Composable
private fun MemberInfoRow(
    user: User,
    isAdmin: Boolean,
    onClick: (() -> Unit)?
) {
    val colors = DenoTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(user.avatarInitial.ifEmpty { user.fullName.first().toString() }, 44)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    user.fullName,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (isAdmin) {
                    Spacer(Modifier.width(6.dp))
                    AdminBadge("Admin")
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                if (user.isCurrentUser) "You" else "@${user.username}",
                color = colors.textSecondary,
                fontSize = 13.sp
            )
        }
        if (onClick != null) {
            Icon(
                Icons.Default.ChevronRight,
                null,
                tint = colors.textSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun DangerActionRow(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    val colors = DenoTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color.Red.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, title, tint = Color.Red, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(
            title,
            color = Color.Red,
            fontWeight = FontWeight.Medium,
            fontSize = 15.sp
        )
    }
}

@Composable
private fun DialogActionRow(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color.Black, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Text(
            title,
            color = Color.Black,
            fontWeight = FontWeight.Medium,
            fontSize = 15.sp
        )
    }
}

@Composable
private fun MemberActionPopup(
    user: User,
    isMemberAdmin: Boolean,
    onMakeAdmin: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit
) {
    val name = user.fullName
    val username = if (user.isCurrentUser) "You" else "@${user.username}"
    val dividerColor = Color(0xFFEEEEEE)

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .width(310.dp)
                .clip(RoundedCornerShape(18.dp)),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
        ) {
            Column(Modifier.fillMaxWidth()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Avatar(user.avatarInitial.ifEmpty { user.fullName.first().toString() }, 40)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            name,
                            color = Color.Black,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            username,
                            color = Color(0xFF757575),
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Divider(color = dividerColor)
                if (!isMemberAdmin) {
                    DialogActionRow("Make Admin", Icons.Outlined.Edit) { onMakeAdmin() }
                    Divider(color = dividerColor)
                }
                DialogActionRow("Remove from Group", Icons.Outlined.PersonRemove) { onRemove() }
                Divider(color = dividerColor)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onDismiss)
                        .padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Cancel",
                        color = Color.Black,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

// --------------------------- CREATE GROUP ---------------------------

@Composable
fun CreateGroupScreen(
    selectedMemberIds: List<String>,
    onSelectParticipants: () -> Unit,
    onBack: () -> Unit,
    onCreated: (String) -> Unit
) {
    val colors = DenoTheme.colors
    val repo = remember { MockMessagingRepository() }
    val context = LocalContext.current
    var name by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var photoUri by rememberSaveable { mutableStateOf<String?>(null) }
    val scrollState = rememberScrollState()

    val photoBitmap = produceState<ImageBitmap?>(initialValue = null, photoUri) {
        val uriString = photoUri
        value = if (uriString == null) null else runCatching {
            withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(Uri.parse(uriString))?.use {
                    BitmapFactory.decodeStream(it)
                }?.asImageBitmap()
            }
        }.getOrNull()
    }.value

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) photoUri = uri.toString()
    }

    val nameOk = name.isNotBlank()
    val participantsOk = selectedMemberIds.isNotEmpty()
    val canCreate = nameOk && participantsOk

    Column(
        Modifier
            .fillMaxSize()
            .background(denoMsgColors().background)
    ) {
        // --------------------------- TOP BAR ---------------------------
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, null, tint = colors.textPrimary)
            }
            Text(
                "New Group",
                color = colors.textPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp
            )
        }
        Divider(color = denoMsgColors().divider)

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp)
        ) {
            // --------------------------- GROUP PHOTO ---------------------------
            Spacer(Modifier.height(28.dp))
            Box(
                Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier
                        .size(110.dp)
                        .clip(CircleShape)
                        .background(if (colors.isDark) colors.card else Color(0xFFE3F2FD))
                        .clickable {
                            photoPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (photoBitmap != null) {
                        Image(
                            bitmap = photoBitmap,
                            contentDescription = "Group photo",
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text(
                            name.take(2).uppercase().ifEmpty { "G" },
                            color = DenoBlue,
                            fontWeight = FontWeight.Bold,
                            fontSize = 40.sp
                        )
                    }
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(DenoBlue)
                            .border(2.dp, colors.background, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Outlined.CameraAlt,
                            null,
                            tint = Color.White,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                if (photoUri == null) "Add group photo (optional)" else "Change group photo",
                color = DenoBlue,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .clickable {
                        photoPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
            )

            // --------------------------- GROUP NAME ---------------------------
            Spacer(Modifier.height(28.dp))
            GroupInputField(
                value = name,
                onValueChange = { name = it },
                placeholder = "Group name",
                maxLength = 60,
                showCounter = true,
                errorText = if (name.isBlank()) "Group name is required" else ""
            )

            // --------------------------- DESCRIPTION ---------------------------
            Spacer(Modifier.height(20.dp))
            GroupInputField(
                value = description,
                onValueChange = { description = it },
                placeholder = "Group description",
                singleLine = false,
                maxLength = 200
            )

            // --------------------------- ADD PARTICIPANTS ---------------------------
            Spacer(Modifier.height(28.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onSelectParticipants)
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Add participants",
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Medium,
                        fontSize = 16.sp
                    )
                    if (selectedMemberIds.isEmpty()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "Select at least one participant",
                            color = colors.textSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
                if (selectedMemberIds.isNotEmpty()) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (colors.isDark) colors.card else Color(0xFFE3F2FD)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "${selectedMemberIds.size} selected",
                            color = DenoBlue,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                }
                Icon(
                    Icons.Default.ChevronRight,
                    null,
                    tint = colors.textSecondary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Divider(color = denoMsgColors().divider)

            if (selectedMemberIds.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                SelectedParticipantsPreview(selectedMemberIds)
                Spacer(Modifier.height(8.dp))
            }
        }

        // --------------------------- CREATE BUTTON ---------------------------
        Divider(color = denoMsgColors().divider)
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .imePadding()
        ) {
            DenoButton(
                "Create Group",
                onClick = {
                    if (canCreate) {
                        val group = repo.createGroup(
                            name = name,
                            description = description,
                            topic = "General",
                            photoUri = photoUri.orEmpty(),
                            creatorId = MockData.currentUser.id,
                            memberIds = selectedMemberIds
                        )
                        onCreated(group.id)
                    }
                },
                enabled = canCreate,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun GroupInputField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    maxLength: Int = Int.MAX_VALUE,
    showCounter: Boolean = false,
    errorText: String = ""
) {
    val colors = DenoTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()

    Column(modifier.fillMaxWidth()) {
        BasicTextField(
            value = value,
            onValueChange = { new ->
                if (new.length <= maxLength) onValueChange(new)
            },
            singleLine = singleLine,
            interactionSource = interactionSource,
            textStyle = TextStyle(color = colors.textPrimary, fontSize = 16.sp),
            cursorBrush = SolidColor(DenoBlue),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { innerTextField ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        if (value.isEmpty()) {
                            Text(placeholder, color = colors.textSecondary, fontSize = 16.sp, maxLines = 1)
                        }
                        innerTextField()
                    }
                    if (showCounter && maxLength != Int.MAX_VALUE) {
                        Text(
                            "${value.length}/$maxLength",
                            color = if (value.length >= maxLength) Color.Red else colors.textSecondary,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        )
        Spacer(Modifier.height(5.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(
                    when {
                        errorText.isNotEmpty() -> Color.Red
                        focused -> DenoBlue
                        else -> colors.border
                    }
                )
        )
        if (errorText.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(errorText, color = Color.Red, fontSize = 12.sp)
        }
    }
}

@Composable
private fun SelectedParticipantsPreview(memberIds: List<String>) {
    val colors = DenoTheme.colors
    val users = remember { MockUserRepository().getUsers() }
    val selected = remember(memberIds, users) { users.filter { it.id in memberIds } }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(selected, key = { it.id }) { user ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Avatar(user.avatarInitial.ifEmpty { user.fullName.first().toString() }, 44)
                Spacer(Modifier.height(3.dp))
                Text(
                    user.fullName.split(" ").first(),
                    color = colors.textSecondary,
                    fontSize = 10.sp,
                    maxLines = 1
                )
            }
        }
    }
}

// --------------------------- SELECT PARTICIPANTS ---------------------------

@Composable
fun SelectParticipantsScreen(
    selectedIds: List<String>,
    onSelectionChanged: (List<String>) -> Unit,
    onBack: () -> Unit,
    hiddenIds: Set<String> = emptySet()
) {
    val colors = DenoTheme.colors
    val users = remember(hiddenIds) {
        MockUserRepository().getUsers()
            .sortedBy { it.fullName }
            .filterNot { it.id in hiddenIds }
            .filterNot { com.deno.social.data.repository.SessionManager.isBlocked(it.id) }
    }
    var query by rememberSaveable { mutableStateOf("") }
    val selectedSet = selectedIds.toSet()
    val selectedUsers = remember(selectedSet, users) { users.filter { it.id in selectedSet } }

    val filtered = remember(query, users) {
        if (query.isBlank()) users
        else users.filter {
            it.fullName.contains(query, true) || it.username.contains(query, true)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(denoMsgColors().background)
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
            Text(
                "Add Participants",
                color = colors.textPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp
            )
        }
        Divider(color = denoMsgColors().divider)

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            DenoGroupSearchField(
                value = query,
                onValueChange = { query = it },
                placeholder = "Search participants",
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (selectedUsers.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(selectedUsers, key = { it.id }) { user ->
                    Column(
                        Modifier.clickable {
                            onSelectionChanged(selectedIds.filterNot { it == user.id })
                        },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Avatar(user.avatarInitial.ifEmpty { user.fullName.first().toString() }, 44)
                        Spacer(Modifier.height(3.dp))
                        Text(
                            user.fullName.split(" ").first(),
                            color = colors.textPrimary,
                            fontSize = 10.sp,
                            maxLines = 1
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Divider(color = denoMsgColors().divider)
        }

        if (filtered.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                EmptyState(
                    Icons.Outlined.SearchOff,
                    "No participants found",
                    "No one matches your search."
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                itemsIndexed(filtered) { index, user ->
                    ParticipantRow(
                        user = user,
                        selected = user.id in selectedSet,
                        onToggle = {
                            val next = if (user.id in selectedSet) {
                                selectedIds.filterNot { it == user.id }
                            } else {
                                selectedIds + user.id
                            }
                            onSelectionChanged(next)
                        }
                    )
                    if (index < filtered.lastIndex) {
                        Divider(color = denoMsgColors().divider, modifier = Modifier.padding(start = 76.dp))
                    }
                }
            }
        }

        Divider(color = denoMsgColors().divider)
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .imePadding()
        ) {
            DenoButton(
                "Done",
                onClick = onBack,
                enabled = selectedIds.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            )
            if (selectedIds.isEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Select at least one participant",
                    color = colors.textSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
    }
}

@Composable
private fun ParticipantRow(
    user: User,
    selected: Boolean,
    onToggle: () -> Unit
) {
    val colors = DenoTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
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
        if (selected) {
            Icon(
                Icons.Filled.CheckCircle,
                null,
                tint = DenoBlue,
                modifier = Modifier.size(24.dp)
            )
        } else {
            Box(
                Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .border(2.dp, colors.border, CircleShape)
            )
        }
    }
}