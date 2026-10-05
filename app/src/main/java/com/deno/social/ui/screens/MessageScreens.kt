package com.deno.social.ui.screens

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Patterns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.deno.social.data.mock.CountryData
import com.deno.social.data.model.ChatMessage
import com.deno.social.data.model.Conversation
import com.deno.social.data.model.Scholar
import com.deno.social.data.model.ScholarApplication
import com.deno.social.data.model.VerificationStatus
import com.deno.social.data.repository.MockMessagingRepository
import com.deno.social.data.repository.SessionManager
import com.deno.social.data.repository.MediaUploader
import com.deno.social.ui.components.Avatar
import com.deno.social.ui.components.DenoButton
import com.deno.social.ui.components.DenoTextField
import com.deno.social.ui.components.EmptyState
import com.deno.social.ui.components.MessageAttachSheet
import com.deno.social.ui.components.MessageAttachmentPreview
import com.deno.social.ui.components.MessageComposer
import com.deno.social.ui.components.MessageRecordingBar
import com.deno.social.ui.components.VoiceRecorder
import com.deno.social.ui.components.ChatPendingAttachment
import com.deno.social.ui.theme.DenoBlue
import com.deno.social.ui.theme.DenoTheme
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

// ---------------------------------------------------------------------------
// DENO Message & Chat visual palette.
//
// Message surfaces follow the soft-black "premium" direction in the app dark
// theme (near-black panels, ink dividers, white sent bubbles with dark text),
// with a clean equivalent in the light theme. Values stay local to this UI;
// the rest of the app theme is untouched.
// ---------------------------------------------------------------------------

internal val MsgInkPanel = Color(0xFF111111)    // main background
internal val MsgInkRaised = Color(0xFF181818)  // raised surfaces / rows
internal val MsgInkDivider = Color(0xFF292929) // dividers
internal val MsgInkReceived = Color(0xFF202020)// received bubble
internal val MsgInkSent = Color(0xFFFFFFFF)    // sent bubble
internal val MsgInkSentText = Color(0xFF111111)// sent bubble text
internal val MsgInkMuted = Color(0xFF9A9A9A)   // secondary / time
internal val MsgInkField = Color(0xFF1C1C1C)   // composer / search field

internal data class MsgColors(
    val background: Color,
    val surface: Color,
    val divider: Color,
    val received: Color,
    val sent: Color,
    val sentText: Color,
    val muted: Color,
    val field: Color,
    val isDark: Boolean
)

@Composable
internal fun denoMsgColors(): MsgColors {
    val colors = DenoTheme.colors
    return if (colors.isDark) {
        MsgColors(
            background = MsgInkPanel,
            surface = MsgInkRaised,
            divider = MsgInkDivider,
            received = MsgInkReceived,
            sent = MsgInkSent,
            sentText = MsgInkSentText,
            muted = MsgInkMuted,
            field = MsgInkField,
            isDark = true
        )
    } else {
        MsgColors(
            background = colors.background,
            surface = colors.surface,
            divider = colors.border,
            received = Color(0xFFF1F3F5),
            sent = DenoBlue,
            sentText = Color.White,
            muted = colors.textSecondary,
            field = Color(0xFFF3F3F5),
            isDark = false
        )
    }
}

@Composable
fun MessagesScreen(
    onOpenChat: (String) -> Unit,
    onOpenScholar: (String) -> Unit,
    onCreateGroup: () -> Unit,
    onOpenGroup: (String) -> Unit,
    onApplyForVerification: () -> Unit = {},
    onBack: (() -> Unit)? = null
) {
    val colors = DenoTheme.colors
    val msg = denoMsgColors()
    val repo = remember { MockMessagingRepository() }
    var activeTab by rememberSaveable { mutableStateOf(0) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var conversations by remember { mutableStateOf(repo.getConversations()) }

    Column(
        Modifier
            .fillMaxSize()
            .background(msg.background)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, null, tint = colors.textPrimary)
                }
            }
            Text(
                "Messages",
                color = colors.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { searchOpen = !searchOpen }) {
                Icon(
                    if (searchOpen) Icons.Default.Close else Icons.Outlined.Search,
                    contentDescription = "Search",
                    tint = colors.textPrimary
                )
            }
        }

        MessageTabs(activeTab = activeTab, onSelect = { activeTab = it })

        if (searchOpen) {
            Spacer(Modifier.height(6.dp))
            DenoSearchField(
                value = query,
                onValueChange = { query = it },
                placeholder = "Search people, scholars, groups"
            )
            Spacer(Modifier.height(6.dp))
            Divider(color = msg.divider)
        }

        when (activeTab) {
            0 -> ChatsTabContent(
                conversations = conversations,
                query = query,
                onConversationsChanged = { conversations = it },
                onOpenChat = onOpenChat
            )
            1 -> AskAMaslaTab(
                query = query,
                onOpenScholar = onOpenScholar,
                onApplyForVerification = onApplyForVerification
            )
            else -> MessageGroupsTab(
                externalQuery = if (searchOpen) query else null,
                onCreateGroup = onCreateGroup,
                onOpenGroup = onOpenGroup
            )
        }
    }
}

@Composable
private fun MessageTabs(activeTab: Int, onSelect: (Int) -> Unit) {
    val colors = DenoTheme.colors
    val msg = denoMsgColors()
    val tabs = listOf("Chats", "Ask a Masla", "Groups")
    Column {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
            tabs.forEachIndexed { index, label ->
                val selected = index == activeTab
                Column(
                    Modifier
                        .weight(1f)
                        .clickable { onSelect(index) }
                        .padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        label,
                        color = if (selected) colors.textPrimary else msg.muted,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        fontSize = 14.sp,
                        maxLines = 1
                    )
                    Spacer(Modifier.height(5.dp))
                    Box(
                        Modifier
                            .width(26.dp)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (selected) colors.textPrimary else Color.Transparent)
                    )
                }
            }
        }
        Divider(color = msg.divider)
    }
}

@Composable
private fun DenoSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String
) {
    val colors = DenoTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
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
        BasicTextField(
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

// --------------------------- CHATS TAB ---------------------------

@Composable
private fun ChatsTabContent(
    conversations: List<Conversation>,
    query: String,
    onConversationsChanged: (List<Conversation>) -> Unit,
    onOpenChat: (String) -> Unit
) {
    val repo = remember { MockMessagingRepository() }
    val filtered = remember(query, conversations) {
        if (query.isBlank()) conversations
        else conversations.filter {
            it.name.contains(query, true) || it.username.contains(query, true) || it.lastMessage.contains(query, true)
        }
    }

    when {
        filtered.isEmpty() && conversations.isEmpty() ->
            EmptyState(
                Icons.Outlined.ChatBubbleOutline,
                "No messages yet",
                "Start a conversation or ask a masla."
            )
        filtered.isEmpty() ->
            EmptyState(
                Icons.Outlined.SearchOff,
                "No results",
                "No conversations match your search."
            )
        else -> LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 8.dp)
        ) {
            itemsIndexed(filtered) { index, chat ->
                ConversationRow(
                    chat = chat,
                    onClick = {
                        repo.markRead(chat.userId)
                        onConversationsChanged(repo.getConversations())
                        onOpenChat(chat.userId)
                    }
                )
                if (index < filtered.lastIndex) {
                    Divider(
                        color = denoMsgColors().divider,
                        modifier = Modifier.padding(start = 76.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ConversationRow(chat: Conversation, onClick: () -> Unit) {
    val colors = DenoTheme.colors
    val msg = denoMsgColors()
    val unread = chat.unread > 0

    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(56.dp)) {
            Avatar(chat.initial, 56)
            if (chat.isOnline) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF2BB673))
                        .border(2.dp, msg.background, CircleShape)
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    chat.name,
                    color = colors.textPrimary,
                    fontWeight = if (unread) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 15.sp,
                    maxLines = 1,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    chat.time,
                    color = if (unread) colors.textPrimary else msg.muted,
                    fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Normal,
                    fontSize = 11.sp
                )
            }
            Spacer(Modifier.height(3.dp))
            Text(
                chat.lastMessage,
                color = if (unread) colors.textPrimary else msg.muted,
                fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Normal,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (chat.unread > 0) {
            Spacer(Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(if (msg.isDark) Color.White else DenoBlue),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "${chat.unread}",
                    color = if (msg.isDark) Color(0xFF111111) else Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// --------------------------- ASK A MASLA TAB ---------------------------

@Composable
private fun AskAMaslaTab(
    query: String,
    onOpenScholar: (String) -> Unit,
    onApplyForVerification: () -> Unit
) {
    val colors = DenoTheme.colors
    val repo = remember { MockMessagingRepository() }
    val scholars = remember { repo.getScholars() }
    val filtered = remember(query, scholars) {
        if (query.isBlank()) scholars
        else scholars.filter {
            it.name.contains(query, true) || it.category.contains(query, true) ||
                it.region.contains(query, true) || it.country.contains(query, true)
        }
    }

    if (filtered.isEmpty()) {
        EmptyState(
            Icons.Outlined.SearchOff,
            "No scholars found",
            "Try a different name, category or region."
        )
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 8.dp)
        ) {
            item {
                ApplyVerificationCard(onClick = onApplyForVerification)
            }
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(
                        "Ask an Islamic scholar",
                        color = colors.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Scholars like Aalim, Mufti, Hafiz, Qari and teachers. Select one to ask your masla in a private chat.",
                        color = colors.textSecondary,
                        fontSize = 13.sp
                    )
                }
            }
            itemsIndexed(filtered) { index, scholar ->
                ScholarRow(scholar = scholar, onClick = { onOpenScholar(scholar.id) })
                if (index < filtered.lastIndex) {
                    Divider(
                        color = colors.border,
                        modifier = Modifier.padding(start = 76.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ApplyVerificationCard(onClick: () -> Unit) {
    val colors = DenoTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (colors.isDark) DenoBlue.copy(alpha = 0.18f) else DenoBlue.copy(alpha = 0.10f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Outlined.Verified,
            null,
            tint = DenoBlue,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "Apply for Verification",
                color = DenoBlue,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp
            )
            Text(
                "Get a verified badge and appear in the scholars list",
                color = colors.textSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            Icons.Outlined.ChevronRight,
            null,
            tint = DenoBlue,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun ScholarRow(scholar: Scholar, onClick: () -> Unit) {
    val colors = DenoTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(scholar.initial.ifEmpty { scholar.name.first().toString() }, 52)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    scholar.name,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (scholar.isVerified) {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.Filled.Verified,
                        "Verified scholar",
                        tint = DenoBlue,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                scholar.category,
                color = DenoBlue,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            val location = listOf(scholar.region, scholar.country)
                .filter { it.isNotBlank() }
                .joinToString(", ")
            if (location.isNotEmpty()) {
                Text(
                    location,
                    color = colors.textSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Icon(
            Icons.Default.ChevronRight,
            null,
            tint = colors.textSecondary,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

@Composable
fun ScholarProfileScreen(
    scholarId: String,
    onBack: () -> Unit,
    onAskMasla: (String) -> Unit
) {
    val colors = DenoTheme.colors
    val msg = denoMsgColors()
    val repo = remember { MockMessagingRepository() }
    val scholar = remember(scholarId) { repo.getScholar(scholarId) }

    Column(
        Modifier
            .fillMaxSize()
            .background(msg.background)
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
                "Scholar Profile",
                color = colors.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp
            )
        }
        Divider(color = msg.divider)

        if (scholar == null) {
            EmptyState(
                Icons.Outlined.SearchOff,
                "Scholar not found",
                "This scholar profile is unavailable."
            )
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 20.dp)
            ) {
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Avatar(scholar.initial.ifEmpty { scholar.name.first().toString() }, 84)
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            scholar.name,
                            color = colors.textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                        if (scholar.isVerified) {
                            Spacer(Modifier.width(5.dp))
                            Icon(
                                Icons.Filled.Verified,
                                "Verified scholar",
                                tint = DenoBlue,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        scholar.category,
                        color = DenoBlue,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    val location = listOf(scholar.region, scholar.country)
                        .filter { it.isNotBlank() }
                        .joinToString(", ")
                    if (location.isNotEmpty()) {
                        Spacer(Modifier.height(2.dp))
                        Text(location, color = colors.textSecondary, fontSize = 13.sp)
                    }
                }

                if (scholar.bio.isNotBlank()) {
                    Spacer(Modifier.height(24.dp))
                    Text(
                        "About",
                        color = colors.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        scholar.bio,
                        color = colors.textSecondary,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                }

                Spacer(Modifier.weight(1f))
                DenoButton(
                    "Ask a Masla",
                    onClick = {
                        repo.startScholarChat(scholar.id)
                        onAskMasla(scholar.id)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

// --------------------------- CHAT SCREEN ---------------------------

@Composable
fun ChatScreen(userId: String, onBack: () -> Unit) {
    val colors = DenoTheme.colors
    val msg = denoMsgColors()
    val repo = remember { MockMessagingRepository() }
    val conversation = remember(userId) { repo.getConversation(userId) }
    var messages by remember(userId) { mutableStateOf(conversation?.messages.orEmpty()) }
    var text by remember { mutableStateOf("") }
    var replyingTo by remember { mutableStateOf<ChatMessage?>(null) }
    var editing by remember(userId) { mutableStateOf<ChatMessage?>(null) }
    var showAttach by remember { mutableStateOf(false) }
    var attachment by remember { mutableStateOf<ChatPendingAttachment?>(null) }
    var isRecording by remember { mutableStateOf(false) }
    var recordingSeconds by remember { mutableStateOf(0) }
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val blockedIds by SessionManager.blockedIds.collectAsState()
    val blocked = userId in blockedIds

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
                sent = repo.sendMessage(userId, text, replyText, "", attach.uri, attach.durationMs)
            } else {
                sent = repo.sendMessage(userId, text, replyText, attach.uri)
            }
        } else {
            sent = repo.sendMessage(userId, text, replyText, "")
        }
        messages = messages + sent
        text = ""
        replyingTo = null
        attachment = null
    }

    val photoPreviewLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
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
            Avatar(conversation?.initial ?: "?", 40)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    conversation?.name ?: "Chat",
                    color = colors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (conversation?.isOnline == true) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF2BB673))
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "Active now",
                            color = msg.muted,
                            fontSize = 12.sp
                        )
                    }
                } else if (!conversation?.username.isNullOrBlank()) {
                    Text(
                        "@${conversation?.username}",
                        color = msg.muted,
                        fontSize = 12.sp
                    )
                }
            }
        }
        Divider(color = msg.divider)

        if (messages.isEmpty() && !blocked) {
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Outlined.ChatBubbleOutline,
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
                    "Say as-salamu alaykum to ${conversation?.name ?: "them"}.",
                    color = msg.muted,
                    fontSize = 13.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
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
                        modifier = Modifier.padding(
                            top = if (index > 0 && prev?.isMe == message.isMe) 2.dp else 8.dp
                        ),
                        onReply = { replyingTo = it },
                        onEdit = {
                            editing = it
                            text = it.text
                        },
                        onUnsend = {
                            repo.deleteMessage(userId, it.id)
                            messages = messages.filterNot { m -> m.id == it.id }
                            if (editing?.id == it.id) editing = null
                        }
                    )
                }
            }
        }

        if (blocked) {
            // Blocked users: no messaging interaction. Composer is replaced
            // with a compact notice until the user is unblocked.
            Box(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "You've blocked this user. Unblock to send messages.",
                    color = msg.muted,
                    fontSize = 13.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        } else {
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
                            "Replying to ${conversation?.name ?: "message"}",
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
            if (isRecording) {
                MessageRecordingBar(
                    seconds = recordingSeconds,
                    onCancel = {
                        voiceRecorder.cancel()
                        isRecording = false
                        recordingSeconds = 0
                    },
                    onStop = {
                        val recorded = voiceRecorder.stop()
                        if (recorded != null) {
                            attachment = recorded
                        }
                        isRecording = false
                        recordingSeconds = 0
                    }
                )
            }
            attachment?.let { att ->
                MessageAttachmentPreview(
                    attachment = att,
                    onRemove = { attachment = null }
                )
            }
            MessageComposer(
                value = text,
                onValueChange = { text = it },
                onSend = {
                    val editTarget = editing
                    if (editTarget != null) {
                        val updated = repo.editMessage(userId, editTarget.id, text)
                        if (updated != null) {
                            messages = messages.map { if (it.id == updated.id) updated else it }
                        }
                        editing = null
                        text = ""
                    } else {
                        sendMessageWithAttachment(attachment)
                    }
                },
                placeholder = "Message...",
                onAttach = { showAttach = true },
                onMicTap = {
                    recordPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                },
                isRecording = isRecording
            )
            if (showAttach) {
                MessageAttachSheet(
                    onCamera = {
                        showAttach = false
                        cameraLauncher.launch(null)
                    },
                    onPhotos = {
                        showAttach = false
                        photoPreviewLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    onFiles = {
                        showAttach = false
                        filesLauncher.launch(arrayOf("*/*"))
                    },
                    onDismiss = { showAttach = false }
                )
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    msg: ChatMessage,
    senderName: String? = null,
    modifier: Modifier = Modifier,
    onReply: (ChatMessage) -> Unit = {},
    onEdit: (ChatMessage) -> Unit = {},
    onUnsend: (ChatMessage) -> Unit = {}
) {
    val colors = DenoTheme.colors
    val msgCol = denoMsgColors()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var showActions by remember(msg.id) { mutableStateOf(false) }

    val bubbleColor = if (msg.isMe) msgCol.sent else msgCol.received
    val textColor = if (msg.isMe) msgCol.sentText else colors.textPrimary
    val timeColor = if (msg.isMe) msgCol.sentText.copy(alpha = 0.55f) else msgCol.muted

    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = if (msg.isMe) Arrangement.End else Arrangement.Start
    ) {
        Box {
            Column(
                Modifier
                    .widthIn(max = 300.dp)
                    .clip(
                        RoundedCornerShape(
                            topStart = 18.dp,
                            topEnd = 18.dp,
                            bottomStart = if (msg.isMe) 18.dp else 4.dp,
                            bottomEnd = if (msg.isMe) 4.dp else 18.dp
                        )
                    )
                    .background(bubbleColor)
                    .combinedClickable(onClick = {}, onLongClick = { showActions = true })
                    .padding(horizontal = 14.dp, vertical = 9.dp)
            ) {
            if (senderName != null) {
                Text(
                    senderName,
                    color = DenoBlue,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(2.dp))
            }
            if (msg.replyTo.isNotBlank()) {
                val quoteBg = if (msg.isMe) {
                    if (msgCol.isDark) Color(0xFFECECEC) else Color.White.copy(alpha = 0.2f)
                } else {
                    if (msgCol.isDark) Color(0xFF171717) else Color(0xFFE6EAEE)
                }
                val quoteText = if (msg.isMe) {
                    if (msgCol.isDark) Color(0xFF5A5A5A) else Color.White.copy(alpha = 0.85f)
                } else msgCol.muted
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.AutoMirrored.Filled.Reply,
                        "Reply",
                        tint = quoteText,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (msgCol.isDark || !msg.isMe) "You replied to" else "Replied to you",
                        color = quoteText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(Modifier.height(3.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(quoteBg)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        msg.replyTo,
                        color = quoteText,
                        fontSize = 13.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(6.dp))
            }
            if (msg.imageUri.isNotBlank()) {
                val image by produceState<ImageBitmap?>(initialValue = null, msg.imageUri) {
                    value = decodeChatImage(context, msg.imageUri)?.asImageBitmap()
                }
                val photo = image
                if (photo != null) {
                    Image(
                        bitmap = photo,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                    Spacer(Modifier.height(4.dp))
                } else {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Outlined.Description,
                            "Attachment",
                            tint = textColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            fileDisplayName(context, msg.imageUri),
                            color = textColor,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
            if (msg.audioUri.isNotBlank()) {
                AudioMessageContent(msg = msg, textColor = textColor, bubbleColor = bubbleColor)
                Spacer(Modifier.height(4.dp))
            }
            Text(
                msg.text,
                color = textColor,
                fontSize = 15.sp
            )
            Spacer(Modifier.height(2.dp))
            Text(
                msg.time,
                color = timeColor,
                fontSize = 11.sp,
                modifier = Modifier.align(Alignment.End)
            )
        }

        if (showActions) {
            Popup(
                popupPositionProvider = ChatMessageMenuPositionProvider(
                    isMe = msg.isMe,
                    density = LocalDensity.current
                ),
                onDismissRequest = { showActions = false },
                properties = PopupProperties(focusable = true)
            ) {
                MessageActionMenu(
                    msg = msg,
                    onReply = {
                        showActions = false
                        onReply(msg)
                    },
                    onEdit = {
                        showActions = false
                        onEdit(msg)
                    },
                    onCopy = {
                        showActions = false
                        clipboard.setText(AnnotatedString(msg.text))
                        Toast.makeText(context, "Message copied", Toast.LENGTH_SHORT).show()
                    },
                    onUnsend = {
                        showActions = false
                        onUnsend(msg)
                    },
                    onDismiss = { showActions = false }
                )
            }
        }
    }
}
}

@Composable
private fun MessageActionMenu(
    msg: ChatMessage,
    onReply: () -> Unit,
    onEdit: () -> Unit,
    onCopy: () -> Unit,
    onUnsend: () -> Unit,
    onDismiss: () -> Unit
) {
    val msgCol = denoMsgColors()
    val dark = msgCol.isDark
    val surface = if (dark) Color(0xFF202020) else Color.White
    val actionColor = if (dark) Color.White else Color(0xFF111111)
    val cancelColor = if (dark) Color(0xFF9A9A9A) else Color(0xFF757575)

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = surface,
        shadowElevation = 4.dp
    ) {
        Column(
            Modifier
                .width(196.dp)
                .widthIn(min = 0.dp)
        ) {
            ChatMenuRow("Reply", Icons.AutoMirrored.Filled.Reply, actionColor) {
                onReply()
            }
            if (msg.isMe) {
                ChatMenuRow("Edit", Icons.Outlined.Edit, actionColor) {
                    onEdit()
                }
            }
            ChatMenuRow("Copy", Icons.Outlined.ContentCopy, actionColor) {
                onCopy()
            }
            if (msg.isMe) {
                ChatMenuRow("Unsend", Icons.Outlined.Delete, Color(0xFFE53935)) {
                    onUnsend()
                }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onDismiss)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Cancel",
                    color = cancelColor,
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
private fun ChatMenuRow(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, title, tint = tint, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(12.dp))
        Text(
            title,
            color = tint,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

// Anchors the long-press action menu next to the long-pressed message bubble.
// For sent (right) messages the menu opens on the upper-left of the bubble;
// for received (left) messages it opens on the upper-right. It flips below when
// there is no room above and is clamped to the screen edges (and thus above the
// keyboard, which shrinks the available window height).
private class ChatMessageMenuPositionProvider(
    private val isMe: Boolean,
    private val density: Density
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val gap = with(density) { 6.dp.toPx().roundToInt() }
        val margin = with(density) { 8.dp.toPx().roundToInt() }
        val menuWidth = popupContentSize.width
        val menuHeight = popupContentSize.height

        val x = if (isMe) {
            (anchorBounds.left - menuWidth - gap)
                .coerceAtLeast(margin)
                .coerceAtMost((windowSize.width - menuWidth - margin).coerceAtLeast(margin))
        } else {
            (anchorBounds.right + gap)
                .coerceAtLeast(margin)
                .coerceAtMost((windowSize.width - menuWidth - margin).coerceAtLeast(margin))
        }

        val yAtTop = anchorBounds.top - menuHeight - gap
        val y = if (yAtTop >= margin) {
            yAtTop
        } else {
            (anchorBounds.bottom + gap)
                .coerceAtLeast(margin)
                .coerceAtMost((windowSize.height - menuHeight - margin).coerceAtLeast(margin))
        }
        return IntOffset(x, y)
    }
}

// --------------------------- SCHOLAR VERIFICATION ---------------------------

// The 10 scholar verification types. Exactly one is selected; choosing "OTHER"
// reveals the required "Specify your expertise" field.
val VERIFICATION_SCHOLAR_TYPES = listOf(
    "AALIM", "MUFTI", "HAFIZ", "QARI", "ISLAMIC TEACHER",
    "KHATIB", "MUHADDITH", "MUFASSIR", "ARABIC SCHOLAR", "OTHER"
)

private val VERIFICATION_DOC_TYPES = arrayOf(
    "application/pdf",
    "application/msword",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "image/*"
)

@Composable
fun ScholarVerificationScreen(onBack: () -> Unit) {
    val colors = DenoTheme.colors
    val msg = denoMsgColors()
    val app by SessionManager.verificationApp.collectAsState()
    val current = app

    Column(Modifier.fillMaxSize().background(msg.background)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, null, tint = colors.textPrimary)
            }
            Text(
                "Apply for Verification",
                color = colors.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp
            )
        }
        Divider(color = msg.divider)

        if (current != null) {
            VerificationStatusView(current)
        } else {
            VerificationForm()
        }
    }
}

@Composable
private fun VerificationForm() {
    val colors = DenoTheme.colors
    val context = LocalContext.current
    val profile by SessionManager.profile.collectAsState()
    val scrollState = rememberScrollState()

    var fullName by remember { mutableStateOf(profile.fullName) }
    var username by remember { mutableStateOf(profile.username) }
    var country by remember { mutableStateOf("") }
    var region by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var scholarType by remember { mutableStateOf("") }
    var expertise by remember { mutableStateOf("") }
    var institution by remember { mutableStateOf("") }
    var qualification by remember { mutableStateOf("") }
    var specialization by remember { mutableStateOf("") }
    var educationYears by remember { mutableStateOf("") }
    var experienceYears by remember { mutableStateOf("") }
    var introduction by remember { mutableStateOf("") }
    var certificateUri by remember { mutableStateOf("") }
    var certificateName by remember { mutableStateOf("") }
    var supportingUri by remember { mutableStateOf("") }
    var supportingName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var declared by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    fun clearError() {
        error = ""
    }

    val certLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val name = displayNameFromUri(context, uri) ?: "Certificate"
            certificateName = name
            // Upload the document to B2 and store the server-side object key
            submitScope.launch {
                try {
                    val bytes = MediaUploader.readVerificationDocumentBytes(context, uri)
                    val contentType = MediaUploader.getDocumentContentType(context, uri)
                    val stored = MediaUploader.uploadVerificationDocument(bytes, contentType, "certificate")
                    certificateUri = stored.path
                    certificateName = name
                    clearError()
                } catch (e: Exception) {
                    error = "Could not upload certificate: ${e.message}"
                    Log.e("ScholarVerification", "Certificate upload failed", e)
                }
            }
        }
    }
    val supportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val name = displayNameFromUri(context, uri) ?: "Supporting Document"
            supportingName = name
            // Upload the document to B2 and store the server-side object key
            submitScope.launch {
                try {
                    val bytes = MediaUploader.readVerificationDocumentBytes(context, uri)
                    val contentType = MediaUploader.getDocumentContentType(context, uri)
                    val stored = MediaUploader.uploadVerificationDocument(bytes, contentType, "supporting")
                    supportingUri = stored.path
                    supportingName = name
                    clearError()
                } catch (e: Exception) {
                    error = "Could not upload supporting document: ${e.message}"
                    Log.e("ScholarVerification", "Supporting document upload failed", e)
                }
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        VerificationSection("BASIC INFORMATION")
        Spacer(Modifier.height(4.dp))
        DenoTextField(fullName, { fullName = it; clearError() }, "Full Name", required = true)
        Spacer(Modifier.height(6.dp))
        DenoTextField(username, { username = it; clearError() }, "Username", required = true)
        Spacer(Modifier.height(6.dp))
        DenoPickerField(country, "Country", CountryData.COUNTRIES, required = true) { country = it; region = ""; clearError() }
        Spacer(Modifier.height(6.dp))
        DenoPickerField(region, "State / Region", CountryData.regionsFor(country), required = true) { region = it; clearError() }
        Spacer(Modifier.height(6.dp))
        DenoTextField(city, { city = it; clearError() }, "City", required = true)

        Spacer(Modifier.height(12.dp))
        VerificationSection("SCHOLAR TYPE")
        Spacer(Modifier.height(4.dp))
        DenoPickerField(
            scholarType,
            "Scholar Type",
            VERIFICATION_SCHOLAR_TYPES,
            alphabetical = false,
            showSearch = false,
            required = true
        ) { scholarType = it; clearError() }
        if (scholarType == "OTHER") {
            Spacer(Modifier.height(6.dp))
            DenoTextField(expertise, { expertise = it; clearError() }, "Specify your expertise", required = true)
        }

        Spacer(Modifier.height(12.dp))
        VerificationSection("EDUCATION / QUALIFICATION")
        Spacer(Modifier.height(4.dp))
        DenoTextField(institution, { institution = it }, "Institution / Madrasa Name")
        Spacer(Modifier.height(6.dp))
        DenoTextField(qualification, { qualification = it }, "Qualification / Degree")
        Spacer(Modifier.height(6.dp))
        DenoTextField(specialization, { specialization = it }, "Specialization")
        Spacer(Modifier.height(6.dp))
        DenoTextField(educationYears, { educationYears = it }, "Years of Islamic Education")

        Spacer(Modifier.height(12.dp))
        VerificationSection("EXPERIENCE")
        Spacer(Modifier.height(4.dp))
        DenoTextField(experienceYears, { experienceYears = it }, "Years of Islamic / Teaching Experience")
        Spacer(Modifier.height(6.dp))
        DenoTextField(introduction, { introduction = it }, "Short Introduction / About", singleLine = false)

        Spacer(Modifier.height(12.dp))
        VerificationSection("VERIFICATION DOCUMENTS")
        Spacer(Modifier.height(4.dp))
        DocumentPickerRow(
            label = "Certificate / Degree*",
            fileName = certificateName,
            hasFile = certificateUri.isNotEmpty()
        ) { certLauncher.launch(VERIFICATION_DOC_TYPES) }
        Spacer(Modifier.height(6.dp))
        DocumentPickerRow(
            label = "Supporting Document (optional)",
            fileName = supportingName,
            hasFile = supportingUri.isNotEmpty()
        ) { supportLauncher.launch(VERIFICATION_DOC_TYPES) }

        Spacer(Modifier.height(12.dp))
        VerificationSection("CONTACT")
        Spacer(Modifier.height(4.dp))
        DenoTextField(email, { email = it; clearError() }, "Email", required = true)
        Spacer(Modifier.height(6.dp))
        DenoTextField(phone, { phone = it }, "Phone (optional)")

        Spacer(Modifier.height(12.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { declared = !declared; clearError() }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = declared,
                onCheckedChange = { declared = it; clearError() }
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "I confirm that the information provided is accurate and belongs to me.",
                color = colors.textPrimary,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        }

        if (error.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        }

        Spacer(Modifier.height(10.dp))
        DenoButton(
            "Submit Application",
            onClick = {
                error = submitApplication(
                    fullName, username, country, region, city,
                    scholarType, expertise, institution, qualification,
                    specialization, educationYears, experienceYears, introduction,
                    certificateUri, certificateName, supportingUri, supportingName,
                    email, phone, declared
                )
            },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(20.dp))
    }
}

private fun submitApplication(
    fullName: String,
    username: String,
    country: String,
    region: String,
    city: String,
    scholarType: String,
    expertise: String,
    institution: String,
    qualification: String,
    specialization: String,
    educationYears: String,
    experienceYears: String,
    introduction: String,
    certificateUri: String,
    certificateName: String,
    supportingUri: String,
    supportingName: String,
    email: String,
    phone: String,
    declared: Boolean
): String {
    fun required(value: String, label: String): String? =
        if (value.isBlank()) "Fill $label." else null

    listOfNotNull(
        required(fullName, "Full Name"),
        required(username, "Username"),
        required(country, "Country"),
        required(region, "State / Region"),
        required(city, "City"),
        required(scholarType, "Scholar Type"),
        if (scholarType == "OTHER") required(expertise, "Specify your expertise") else null,
        required(email, "Email")
    ).firstOrNull()?.let { return it }

    if (!Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) {
        return "Enter a valid email address."
    }
    if (certificateUri.isBlank()) {
        return "Upload the certificate / degree."
    }
    if (!declared) {
        return "Accept the declaration to submit."
    }

    val app = ScholarApplication(
        id = "va${System.currentTimeMillis()}",
        fullName = fullName.trim(),
        username = username.trim(),
        country = country.trim(),
        region = region.trim(),
        city = city.trim(),
        scholarType = scholarType.trim(),
        expertise = expertise.trim(),
        institution = institution.trim(),
        qualification = qualification.trim(),
        specialization = specialization.trim(),
        educationYears = educationYears.trim(),
        experienceYears = experienceYears.trim(),
        introduction = introduction.trim(),
        certificateUri = certificateUri,
        certificateName = certificateName,
        supportingUri = supportingUri,
        supportingName = supportingName,
        email = email.trim(),
        phone = phone.trim(),
        status = VerificationStatus.PENDING,
        submittedAt = System.currentTimeMillis()
    )
    return if (SessionManager.submitVerificationApplication(app)) ""
    else "An application is already pending. You can submit after it is reviewed."
}

@Composable
private fun DocumentPickerRow(
    label: String,
    fileName: String,
    hasFile: Boolean,
    onClick: () -> Unit
) {
    val colors = DenoTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (colors.isDark) colors.card else Color(0xFFF3F3F5))
            .border(1.dp, colors.border, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (hasFile) Icons.Outlined.CheckCircle else Icons.Outlined.AttachFile,
            null,
            tint = if (hasFile) DenoBlue else colors.textSecondary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                label,
                color = if (hasFile) DenoBlue else colors.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (fileName.isNotEmpty()) {
                Spacer(Modifier.height(1.dp))
                Text(
                    fileName,
                    color = colors.textSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Icon(
            Icons.Outlined.Upload,
            null,
            tint = colors.textSecondary,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun VerificationStatusView(app: ScholarApplication) {
    val colors = DenoTheme.colors
    val rejected = app.status == VerificationStatus.REJECTED
    val statusColor = if (rejected) MaterialTheme.colorScheme.error else DenoBlue
    val title = when (app.status) {
        VerificationStatus.VERIFIED -> "Verified Scholar"
        VerificationStatus.REJECTED -> "Application Not Approved"
        else -> "Application Submitted"
    }
    val note = when (app.status) {
        VerificationStatus.VERIFIED ->
            "Your verified badge and scholar profile now appear in Ask a Masla."
        VerificationStatus.REJECTED ->
            "Your application was not approved and will not appear in the scholars list."
        else ->
            "Your application is pending review. After admin approval, a verified badge and scholar profile will appear in Ask a Masla."
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
    ) {
        Spacer(Modifier.height(28.dp))
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(statusColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    when (app.status) {
                        VerificationStatus.VERIFIED -> Icons.Filled.Verified
                        VerificationStatus.REJECTED -> Icons.Outlined.Cancel
                        else -> Icons.Outlined.HourglassEmpty
                    },
                    null,
                    tint = statusColor,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                title,
                color = colors.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp
            )
            Spacer(Modifier.height(10.dp))
            Text(
                if (app.status == VerificationStatus.PENDING) "PENDING REVIEW" else app.status.name,
                color = when (app.status) {
                    VerificationStatus.VERIFIED, VerificationStatus.PENDING -> DenoBlue
                    else -> MaterialTheme.colorScheme.error
                },
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        when (app.status) {
                            VerificationStatus.VERIFIED, VerificationStatus.PENDING ->
                                DenoBlue.copy(alpha = 0.12f)
                            else -> MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                        }
                    )
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            )
            Spacer(Modifier.height(14.dp))
            when (app.status) {
                VerificationStatus.PENDING -> {
                    Text(
                        "Expected review time:",
                        color = colors.textSecondary,
                        fontSize = 13.sp
                    )
                    Text(
                        "Within 24 hours",
                        color = colors.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "In some cases, verification may take up to 3–5 days.",
                        color = colors.textSecondary,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Submitted: ${formatApplicationDateTime(app.submittedAt)}",
                        color = colors.textSecondary,
                        fontSize = 13.sp
                    )
                }
                else -> {
                    Text(
                        note,
                        color = colors.textSecondary,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(if (colors.isDark) colors.card else Color(0xFFF3F3F5))
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            VerificationStatusRow(
                "Scholar Type",
                app.scholarType + if (app.expertise.isNotBlank()) "  •  ${app.expertise}" else ""
            )
            VerificationStatusRow("Country", app.country)
            VerificationStatusRow("State / Region", app.region)
            VerificationStatusRow("City", app.city)
            VerificationStatusRow("Email", app.email)
            VerificationStatusRow(
                "Submitted",
                if (app.status == VerificationStatus.PENDING) formatApplicationDateTime(app.submittedAt)
                else formatApplicationDate(app.submittedAt)
            )
        }
    }
}

@Composable
fun AudioMessageContent(
    msg: ChatMessage,
    textColor: Color,
    bubbleColor: Color
) {
    val context = LocalContext.current
    val duration = msg.audioDurationMs
    val formattedDuration = formatPlaybackTime(duration)

    var isPlaying by remember(msg.id) { mutableStateOf(false) }
    var progressMs by remember(msg.id) { mutableLongStateOf(0) }
    val playerState by remember(msg.id) { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(key1 = msg.id, effect = {
        onDispose {
            playerState?.run {
                if (isPlaying) stop()
                release()
            }
        }
    })

    val startPlayback = {
        if (isPlaying) return@startPlayback
        val player = MediaPlayer().apply {
            try {
                val source = msg.audioUri
                if (source.startsWith("content://") || source.startsWith("file://")) {
                    setDataSource(context, Uri.parse(source))
                } else {
                    setDataSource(source)
                }
                prepare()
                start()
                isPlaying = true
            } catch (e: Exception) {
                release()
            }
        }
        player.setOnCompletionListener {
            isPlaying = false
            progressMs = 0
        }
    }

    val stopPlayback = {
        if (!isPlaying) return@stopPlayback
        playerState?.run {
            if (isPlaying) {
                pause()
                isPlaying = false
            }
        }
    }

    val togglePlayback = {
        if (isPlaying) stopPlayback() else startPlayback()
    }

    LaunchedEffect(isPlaying) {
        if (!isPlaying) return@LaunchedEffect
        val player = playerState ?: return@LaunchedEffect
        while (isPlaying) {
            delay(200)
            if (!isPlaying) break
            progressMs = player.currentPosition.toLong()
            if (progressMs >= duration) {
                isPlaying = false
                progressMs = 0
                break
            }
        }
    }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(if (textColor == Color.White) Color.White.copy(alpha = 0.2f) else Color(0xFFE6E8EB)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                if (isPlaying) "Pause" else "Play",
                tint = textColor,
                modifier = Modifier.size(18.dp).padding(start = 2.dp)
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "Voice message",
                color = textColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(2.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                androidx.compose.foundation.ProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp),
                    progress = if (duration > 0) (progressMs.toFloat() / duration) else 0f,
                    color = textColor.copy(alpha = 0.7f),
                    trackColor = textColor.copy(alpha = 0.2f)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    formattedDuration,
                    color = textColor.copy(alpha = 0.7f),
                    fontSize = 11.sp
                )
            }
        }
    }
}

private fun formatPlaybackTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}

@Composable
private fun VerificationStatusRow(label: String, value: String) {
    val colors = DenoTheme.colors
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(label, color = colors.textSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(
            value,
            color = colors.textPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun VerificationSection(label: String) {
    val colors = DenoTheme.colors
    Text(label, color = colors.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
}

private fun displayNameFromUri(context: Context, uri: Uri): String? = runCatching {
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
    }
}.getOrNull()

private suspend fun decodeChatImage(context: Context, uriOrPath: String): Bitmap? = withContext(Dispatchers.IO) {
    runCatching {
        if (uriOrPath.startsWith("content://") || uriOrPath.startsWith("file://")) {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(Uri.parse(uriOrPath))?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            val width = bounds.outWidth
            val height = bounds.outHeight
            var sample = 1
            while (width / sample > 1400 || height / sample > 1400) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            resolver.openInputStream(Uri.parse(uriOrPath))?.use {
                BitmapFactory.decodeStream(it, null, opts)
            }
        } else {
            BitmapFactory.decodeFile(uriOrPath)
        }
    }.getOrNull()
}

private fun fileDisplayName(context: Context, uriOrPath: String): String =
    if (uriOrPath.startsWith("content://")) {
        displayNameFromUri(context, Uri.parse(uriOrPath)) ?: "Attachment"
    } else {
        val name = uriOrPath.substringAfterLast('/')
        if (name.isNotBlank() && uriOrPath.contains('/')) name else "Attachment"
    }

private fun formatApplicationDate(timestamp: Long): String =
    SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(timestamp))

private fun formatApplicationDateTime(timestamp: Long): String =
    SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault()).format(Date(timestamp))