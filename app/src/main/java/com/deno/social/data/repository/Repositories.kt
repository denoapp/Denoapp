package com.deno.social.data.repository

import com.deno.social.data.mock.MockData
import com.deno.social.data.model.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

// Interfaces for future backend swap
interface UserRepository {
    fun getCurrentUser(): User
    fun getUsers(): List<User>
    fun searchUsers(query: String): List<User>
    fun getUserById(id: String): User?
    fun toggleFollow(userId: String): Boolean
}

interface PostRepository {
    fun getPosts(): List<Post>
    fun toggleLike(postId: String): Post?
    fun addPost(text: String): Post
}

interface ReelRepository {
    fun getReels(): List<Reel>
    fun toggleLike(reelId: String): Reel?
}

interface CommentRepository {
    fun getComments(): List<Comment>
    fun addComment(text: String): Comment
}

interface NotificationRepository {
    fun getNotifications(): List<NotificationItem>
    fun markRead(id: String)
}

interface MessagingRepository {
    fun getConversations(): List<Conversation>
    fun getConversation(userId: String): Conversation?
    fun markRead(userId: String)
    fun sendMessage(
        userId: String,
        text: String,
        replyTo: String = "",
        imageUri: String = "",
        audioUri: String = "",
        audioDurationMs: Long = 0L
    ): ChatMessage
    fun editMessage(userId: String, messageId: String, newText: String): ChatMessage?
    fun deleteMessage(userId: String, messageId: String): Boolean
    fun getScholars(): List<Scholar>
    fun getScholar(id: String): Scholar?
    fun startScholarChat(scholarId: String): Conversation?
    fun getGroups(): List<MessageGroup>
    fun getGroup(groupId: String): MessageGroup?
    fun joinGroup(groupId: String): Boolean
    fun sendGroupMessage(
        groupId: String,
        text: String,
        replyTo: String = "",
        imageUri: String = "",
        audioUri: String = "",
        audioDurationMs: Long = 0L
    ): ChatMessage
    fun editGroupMessage(groupId: String, messageId: String, newText: String): ChatMessage?
    fun deleteGroupMessage(groupId: String, messageId: String): Boolean
    fun createGroup(
        name: String,
        description: String,
        topic: String,
        photoUri: String = "",
        creatorId: String = "",
        memberIds: List<String> = emptyList()
    ): MessageGroup

    // Group admin controls (functional in the local store).
    fun makeAdmin(groupId: String, userId: String): Boolean

    fun removeMember(groupId: String, userId: String): Boolean

    fun addMembers(groupId: String, newMemberIds: List<String>): Boolean

    fun updateGroup(
        groupId: String,
        name: String,
        description: String,
        photoUri: String
    ): Boolean

    fun updateGroupSettings(
        groupId: String,
        membersCanSend: Boolean,
        membersCanAddParticipants: Boolean
    ): Boolean

    fun deleteGroup(groupId: String): Boolean

    fun leaveGroup(groupId: String): Boolean
}

// Shared in-memory store so every screen holding a MockMessagingRepository
// observes the same local state (sent messages, created groups, join state).
// Swappable for a real backend later without UI changes.
object MessengerStore {
    private val conversations = MockData.conversations.toMutableList()
    private val groups = MockData.messageGroups.toMutableList()

    fun getConversations(): List<Conversation> =
        conversations.filter { !SessionManager.isBlocked(it.userId) }.toList()

    fun getConversation(userId: String): Conversation? =
        conversations.find { it.userId == userId }

    fun markRead(userId: String) {
        if (SessionManager.isBlocked(userId)) return
        val idx = conversations.indexOfFirst { it.userId == userId }
        if (idx >= 0) conversations[idx] = conversations[idx].copy(unread = 0)
    }

    fun sendMessage(
        userId: String,
        text: String,
        replyTo: String = "",
        imageUri: String = "",
        audioUri: String = "",
        audioDurationMs: Long = 0L
    ): ChatMessage {
        if (SessionManager.isBlocked(userId)) {
            // Blocked users cannot receive messages; the send is a no-op that
            // does not mutate any stored conversation.
            return ChatMessage(
                "", text.trim(), true, "now",
                replyTo = replyTo, imageUri = imageUri, audioUri = audioUri, audioDurationMs = audioDurationMs
            )
        }
        val msg = ChatMessage(
            "m${System.currentTimeMillis()}", text.trim(), true, "now",
            replyTo = replyTo, imageUri = imageUri, audioUri = audioUri, audioDurationMs = audioDurationMs
        )
        val idx = conversations.indexOfFirst { it.userId == userId }
        if (idx >= 0) {
            val c = conversations[idx]
            conversations[idx] = c.copy(
                lastMessage = previewOf(msg),
                time = "now",
                unread = 0,
                messages = c.messages + msg
            )
        }
        return msg
    }

    fun editMessage(userId: String, messageId: String, newText: String): ChatMessage? {
        val idx = conversations.indexOfFirst { it.userId == userId }
        if (idx < 0) return null
        val c = conversations[idx]
        val mi = c.messages.indexOfFirst { it.id == messageId }
        if (mi < 0) return null
        val old = c.messages[mi]
        val updated = old.copy(text = newText.trim())
        val msgs = c.messages.toMutableList().apply { this[mi] = updated }
        conversations[idx] = c.copy(
            lastMessage = previewOf(msgs.lastOrNull() ?: old),
            messages = msgs
        )
        return updated
    }

    fun deleteMessage(userId: String, messageId: String): Boolean {
        if (SessionManager.isBlocked(userId)) return false
        val idx = conversations.indexOfFirst { it.userId == userId }
        if (idx < 0) return false
        val c = conversations[idx]
        val rest = c.messages.filterNot { it.id == messageId }
        if (rest.size == c.messages.size) return false
        val last = rest.lastOrNull()
        conversations[idx] = c.copy(
            messages = rest,
            lastMessage = last?.let { previewOf(it) } ?: "",
            time = last?.time ?: ""
        )
        return true
    }

    fun getScholars(): List<Scholar> = MockData.scholars + SessionManager.verifiedAsScholars()

    fun getScholar(id: String): Scholar? = getScholars().find { it.id == id }

    // Ensures a private 1-to-1 conversation exists with the chosen scholar.
    // Reuses the existing conversation when one was already started.
    fun startScholarChat(scholarId: String): Conversation? {
        getConversation(scholarId)?.let { return it }
        val scholar = getScholar(scholarId) ?: return null
        val conversation = Conversation(
            userId = scholar.id,
            name = scholar.name,
            username = scholar.id,
            initial = scholar.initial.ifEmpty { scholar.name.first().toString() },
            lastMessage = "",
            time = "",
            unread = 0,
            isOnline = scholar.isOnline,
            messages = emptyList()
        )
        conversations.add(0, conversation)
        return conversation
    }

    fun getGroups(): List<MessageGroup> = groups.toList()

    fun getGroup(groupId: String): MessageGroup? = groups.find { it.id == groupId }

    fun joinGroup(groupId: String): Boolean {
        val idx = groups.indexOfFirst { it.id == groupId }
        if (idx >= 0) {
            val g = groups[idx]
            val joined = !g.isJoined
            val members = if (joined) (g.memberIds + currentUserId).distinct() else g.memberIds
            groups[idx] = g.copy(
                isJoined = joined,
                memberIds = members,
                memberCount = if (joined) (members.size).coerceAtLeast(1) else (g.memberCount - 1).coerceAtLeast(1)
            )
            return groups[idx].isJoined
        }
        return false
    }

    fun sendGroupMessage(
        groupId: String,
        text: String,
        replyTo: String = "",
        imageUri: String = "",
        audioUri: String = "",
        audioDurationMs: Long = 0L
    ): ChatMessage {
        val msg = ChatMessage(
            "gm${System.currentTimeMillis()}", text.trim(), true, "now",
            replyTo = replyTo, imageUri = imageUri, audioUri = audioUri, audioDurationMs = audioDurationMs
        )
        val idx = groups.indexOfFirst { it.id == groupId }
        if (idx >= 0) {
            val g = groups[idx]
            groups[idx] = g.copy(
                lastMessage = previewOf(msg),
                lastActivity = "now",
                messages = g.messages + msg
            )
        }
        return msg
    }

    fun editGroupMessage(groupId: String, messageId: String, newText: String): ChatMessage? {
        val idx = groups.indexOfFirst { it.id == groupId }
        if (idx < 0) return null
        val g = groups[idx]
        val mi = g.messages.indexOfFirst { it.id == messageId }
        if (mi < 0) return null
        val old = g.messages[mi]
        val updated = old.copy(text = newText.trim())
        val msgs = g.messages.toMutableList().apply { this[mi] = updated }
        groups[idx] = g.copy(
            lastMessage = previewOf(msgs.lastOrNull() ?: old),
            lastActivity = g.lastActivity,
            messages = msgs
        )
        return updated
    }

    fun deleteGroupMessage(groupId: String, messageId: String): Boolean {
        val idx = groups.indexOfFirst { it.id == groupId }
        if (idx < 0) return false
        val g = groups[idx]
        val rest = g.messages.filterNot { it.id == messageId }
        if (rest.size == g.messages.size) return false
        val last = rest.lastOrNull()
        groups[idx] = g.copy(
            messages = rest,
            lastMessage = last?.let { previewOf(it) } ?: "",
            lastActivity = last?.time ?: ""
        )
        return true
    }

    // Shared last-message preview: shows text, or "Photo"/"Voice message" for
    // media-only sends.
    private fun previewOf(msg: ChatMessage): String =
        msg.text.ifBlank {
            when {
                msg.audioUri.isNotBlank() -> "Voice message"
                msg.imageUri.isNotBlank() -> "Photo"
                else -> ""
            }
        }

    fun createGroup(
        name: String,
        description: String,
        topic: String,
        photoUri: String = "",
        creatorId: String = "",
        memberIds: List<String> = emptyList()
    ): MessageGroup {
        // Creator is always a member/admin; the selected participants join as members.
        val members = (listOfNotNull(creatorId.ifBlank { null }).toSet() + memberIds).distinct()
        val group = MessageGroup(
            id = "g${System.currentTimeMillis()}",
            name = name.trim(),
            description = description.trim(),
            initial = name.trim().take(2).uppercase(),
            memberCount = members.size,
            topic = topic,
            photoUri = photoUri,
            creatorId = creatorId,
            memberIds = members,
            adminIds = listOfNotNull(creatorId.ifBlank { null }),
            createdAt = "now",
            lastActivity = "now",
            lastMessage = "",
            isJoined = true,
            messages = emptyList()
        )
        groups.add(0, group)
        return group
    }

    // ---- Group admin controls (all state lives in this shared store) ----

    fun makeAdmin(groupId: String, userId: String): Boolean {
        val idx = groups.indexOfFirst { it.id == groupId }
        if (idx < 0 || userId.isBlank()) return false
        val g = groups[idx]
        if (userId !in g.memberIds || userId in g.adminIds) return false
        groups[idx] = g.copy(adminIds = g.adminIds + userId)
        return true
    }

    fun removeMember(groupId: String, userId: String): Boolean {
        val idx = groups.indexOfFirst { it.id == groupId }
        if (idx < 0 || userId.isBlank()) return false
        val g = groups[idx]
        // The creator can never be removed, and an admin cannot remove themself.
        if (userId == g.creatorId || userId == currentUserId) return false
        if (userId !in g.memberIds) return false
        groups[idx] = g.copy(
            memberIds = g.memberIds - userId,
            adminIds = g.adminIds - userId,
            memberCount = (g.memberCount - 1).coerceAtLeast(1)
        )
        return true
    }

    fun addMembers(groupId: String, newMemberIds: List<String>): Boolean {
        val idx = groups.indexOfFirst { it.id == groupId }
        if (idx < 0) return false
        val g = groups[idx]
        val added = newMemberIds.filter { it.isNotBlank() && it !in g.memberIds }.distinct()
        if (added.isEmpty()) return false
        val members = (g.memberIds + added).distinct()
        groups[idx] = g.copy(
            memberIds = members,
            memberCount = members.size
        )
        return true
    }

    fun updateGroup(groupId: String, name: String, description: String, photoUri: String): Boolean {
        val idx = groups.indexOfFirst { it.id == groupId }
        if (idx < 0) return false
        val g = groups[idx]
        groups[idx] = g.copy(
            name = name.trim().ifEmpty { g.name },
            description = description.trim(),
            photoUri = photoUri,
            initial = g.initial.ifEmpty { name.trim().take(2).uppercase() }
        )
        return true
    }

    fun updateGroupSettings(
        groupId: String,
        membersCanSend: Boolean,
        membersCanAddParticipants: Boolean
    ): Boolean {
        val idx = groups.indexOfFirst { it.id == groupId }
        if (idx < 0) return false
        val g = groups[idx]
        groups[idx] = g.copy(
            membersCanSend = membersCanSend,
            membersCanAddParticipants = membersCanAddParticipants
        )
        return true
    }

    fun deleteGroup(groupId: String): Boolean {
        val idx = groups.indexOfFirst { it.id == groupId }
        if (idx < 0) return false
        groups.removeAt(idx)
        return true
    }

    fun leaveGroup(groupId: String): Boolean {
        val idx = groups.indexOfFirst { it.id == groupId }
        if (idx < 0) return false
        val g = groups[idx]
        // The creator cannot leave; they must delete the group instead.
        if (g.creatorId == currentUserId) return false
        groups[idx] = g.copy(
            isJoined = false,
            memberIds = g.memberIds - currentUserId,
            adminIds = g.adminIds - currentUserId,
            memberCount = (g.memberCount - 1).coerceAtLeast(1)
        )
        return true
    }

    private val currentUserId: String
        get() = MockData.currentUser.id
}

class MockMessagingRepository : MessagingRepository {
    override fun getConversations() = MessengerStore.getConversations()

    override fun getConversation(userId: String) = MessengerStore.getConversation(userId)

    override fun markRead(userId: String) = MessengerStore.markRead(userId)

    override fun sendMessage(
        userId: String,
        text: String,
        replyTo: String,
        imageUri: String,
        audioUri: String,
        audioDurationMs: Long
    ) = MessengerStore.sendMessage(userId, text, replyTo, imageUri, audioUri, audioDurationMs)

    override fun editMessage(userId: String, messageId: String, newText: String) = MessengerStore.editMessage(userId, messageId, newText)

    override fun deleteMessage(userId: String, messageId: String) = MessengerStore.deleteMessage(userId, messageId)

    override fun getScholars() = MessengerStore.getScholars()

    override fun getScholar(id: String) = MessengerStore.getScholar(id)

    override fun startScholarChat(scholarId: String) = MessengerStore.startScholarChat(scholarId)

    override fun getGroups() = MessengerStore.getGroups()

    override fun getGroup(groupId: String) = MessengerStore.getGroup(groupId)

    override fun joinGroup(groupId: String) = MessengerStore.joinGroup(groupId)

    override fun sendGroupMessage(
        groupId: String,
        text: String,
        replyTo: String,
        imageUri: String,
        audioUri: String,
        audioDurationMs: Long
    ) = MessengerStore.sendGroupMessage(groupId, text, replyTo, imageUri, audioUri, audioDurationMs)

    override fun editGroupMessage(groupId: String, messageId: String, newText: String) = MessengerStore.editGroupMessage(groupId, messageId, newText)

    override fun deleteGroupMessage(groupId: String, messageId: String) = MessengerStore.deleteGroupMessage(groupId, messageId)

    override fun createGroup(
        name: String,
        description: String,
        topic: String,
        photoUri: String,
        creatorId: String,
        memberIds: List<String>
    ) = MessengerStore.createGroup(name, description, topic, photoUri, creatorId, memberIds)

    override fun makeAdmin(groupId: String, userId: String) = MessengerStore.makeAdmin(groupId, userId)

    override fun removeMember(groupId: String, userId: String) = MessengerStore.removeMember(groupId, userId)

    override fun addMembers(groupId: String, newMemberIds: List<String>) = MessengerStore.addMembers(groupId, newMemberIds)

    override fun updateGroup(groupId: String, name: String, description: String, photoUri: String) =
        MessengerStore.updateGroup(groupId, name, description, photoUri)

    override fun updateGroupSettings(groupId: String, membersCanSend: Boolean, membersCanAddParticipants: Boolean) =
        MessengerStore.updateGroupSettings(groupId, membersCanSend, membersCanAddParticipants)

    override fun deleteGroup(groupId: String) = MessengerStore.deleteGroup(groupId)

    override fun leaveGroup(groupId: String) = MessengerStore.leaveGroup(groupId)
}

// Mock implementations
class MockUserRepository : UserRepository {
    private val users = MockData.users.toMutableList()
    override fun getCurrentUser() = MockData.currentUser
    override fun getUsers() = users.filter { !it.isCurrentUser }.map { SessionManager.applyFollowState(it) }
    override fun searchUsers(query: String) =
        users.filter { !it.isCurrentUser && (it.fullName.contains(query, true) || it.username.contains(query, true)) }
            .map { SessionManager.applyFollowState(it) }
    // Follow state merges live from SessionManager (single source of truth),
    // so every screen reflects the same isFollowing/followers values.
    override fun getUserById(id: String) = users.find { it.id == id }?.let { SessionManager.applyFollowState(it) }
    override fun toggleFollow(userId: String): Boolean = SessionManager.toggleFollowUser(userId)
}

class MockPostRepository : PostRepository {
    private val posts = MockData.posts.toMutableList()
    override fun getPosts() = posts.filter { !SessionManager.isBlocked(it.user.id) }.toList()
    override fun toggleLike(postId: String): Post? {
        val idx = posts.indexOfFirst { it.id == postId }
        if (idx >= 0) {
            val p = posts[idx]
            posts[idx] = p.copy(isLiked = !p.isLiked, likes = if (p.isLiked) p.likes - 1 else p.likes + 1)
            return posts[idx]
        }
        return null
    }
    override fun addPost(text: String): Post {
        val post = Post("p${System.currentTimeMillis()}", MockData.currentUser, text, "now", 0, 0, false, false)
        posts.add(0, post)
        return post
    }
}

class MockReelRepository : ReelRepository {
    private val reels = MockData.reels.toMutableList()
    override fun getReels() = reels.filter { !SessionManager.isBlocked(it.user.id) }.toList()
    override fun toggleLike(reelId: String): Reel? {
        val idx = reels.indexOfFirst { it.id == reelId }
        if (idx >= 0) {
            val r = reels[idx]
            reels[idx] = r.copy(isLiked = !r.isLiked, likes = if (r.isLiked) r.likes - 1 else r.likes + 1)
            return reels[idx]
        }
        return null
    }
}

class MockCommentRepository : CommentRepository {
    private val comments = MockData.comments.toMutableList()
    override fun getComments() = comments.toList()
    override fun addComment(text: String): Comment {
        val c = Comment("c${System.currentTimeMillis()}", MockData.currentUser, text, "now")
        comments.add(0, c)
        return c
    }
}

class MockNotificationRepository : NotificationRepository {
    private val items = MockData.notifications.toMutableList()
    override fun getNotifications() = items.toList()
    override fun markRead(id: String) {
        val idx = items.indexOfFirst { it.id == id }
        if (idx >= 0) items[idx] = items[idx].copy(isRead = true)
    }
}

// Session manager (local only). Owns the single source of truth for the
// current user's editable profile and persists it to SharedPreferences so
// saved data survives process restarts. Swappable for a real backend later
// without UI changes.
object SessionManager {
    private const val PREFS_NAME = "deno_profile"
    private const val KEY_PROFILE = "profile_json"
    private const val KEY_FOLLOWING = "following_ids"
    private const val KEY_FOLLOWERS = "follower_ids"
    private const val KEY_NOTIFICATIONS = "notifications_json"
    private const val KEY_VERIFICATION = "verification_json"
    private const val KEY_BLOCKED = "blocked_ids"
    private const val KEY_REPORTS = "reports_json"
    private const val KEY_PASSWORD = "password"
    private const val KEY_DARK_MODE = "dark_mode"
    private const val KEY_PRIVATE = "private_account"
    private const val KEY_PUSH = "push_enabled"
    private const val KEY_LANGUAGE = "app_language"
    private const val KEY_RECOVERY = "recovery_json"
    private const val KEY_SAVED_POSTS = "saved_post_ids"
    private const val KEY_SAVED_REELS = "saved_reel_ids"
    private const val KEY_DRAFTS = "drafts_json"
    private const val KEY_SCHEDULED = "scheduled_json"
    private const val KEY_JOINED_COMMUNITIES = "joined_communities"
    private const val KEY_CONTENT_PRIVACY = "content_privacy_json"

    private lateinit var appContext: Context

    private val _session = MutableStateFlow(Session())
    val session: StateFlow<Session> = _session.asStateFlow()

    // Editable profile state. Seeded from MockData + the signup islamic
    // profile, then updated only when the user actually saves in Edit Profile.
    private val _profile = MutableStateFlow(
        UserProfile(
            fullName = MockData.currentUser.fullName,
            username = MockData.currentUser.username,
            bio = MockData.currentUser.bio
        )
    )
    val profile: StateFlow<UserProfile> = _profile.asStateFlow()

    // Follow relationships (single source of truth). Ids of users the current
    // user follows (followingIds) and users who follow them (followerIds).
    // Persisted locally so follow state and the Profile counts survive
    // app restarts; shared by Profile, Followers, Following and user profiles.
    private val _followingIds = MutableStateFlow<Set<String>>(emptySet())
    val followingIds: StateFlow<Set<String>> = _followingIds.asStateFlow()

    private val _followerIds = MutableStateFlow<Set<String>>(emptySet())
    val followerIds: StateFlow<Set<String>> = _followerIds.asStateFlow()

    // Local block list (single source of truth). The current user's blocked
    // ids persist in SharedPreferences so blocks survive app restarts. Blocking
    // is a separate layer on top of the existing Private/Follow system: it is
    // controlled by the current user and restricts interaction/content between
    // the two users without touching the account owner's privacy setting.
    private val _blockedIds = MutableStateFlow<Set<String>>(emptySet())
    val blockedIds: StateFlow<Set<String>> = _blockedIds.asStateFlow()

    // Local-only report state (userId -> reason). Stored purely for the local
    // demo; nothing is ever sent to a backend/server.
    private val _reports = MutableStateFlow<Map<String, String>>(emptyMap())
    val reports: StateFlow<Map<String, String>> = _reports.asStateFlow()

    // ---------------------------- SETTINGS STATE ----------------------------
    // Every toggled/preference value in Settings lives here and persists so
    // user choices survive app restarts. Single source of truth for the whole
    // app; no screen keeps its own copy of these booleans.

    // App theme. Read at startup so the current session opens in the chosen
    // mode and updated from the Settings Dark Mode switch.
    private val _darkMode = MutableStateFlow(false)
    val darkMode: StateFlow<Boolean> = _darkMode.asStateFlow()

    // Private Account mirrors the account owner's privacy intent. It is a
    // separate layer from Block and overrides the mock default when set, so
    // the account owner controls their own visibility.
    private val _privateAccount = MutableStateFlow(false)
    val privateAccount: StateFlow<Boolean> = _privateAccount.asStateFlow()

    // In-app push notifications toggle. Off shows a muted banner on the
    // Notifications screen but never deletes the persisted follow items.
    private val _pushEnabled = MutableStateFlow(true)
    val pushEnabled: StateFlow<Boolean> = _pushEnabled.asStateFlow()

    // Selected app language from Settings > Language.
    private val _appLanguage = MutableStateFlow("English")
    val appLanguage: StateFlow<String> = _appLanguage.asStateFlow()

    // Account recovery contact entered in Settings > Account Recovery.
    private val _recoveryEmail = MutableStateFlow("")
    val recoveryEmail: StateFlow<String> = _recoveryEmail.asStateFlow()
    private val _recoveryPhone = MutableStateFlow("")
    val recoveryPhone: StateFlow<String> = _recoveryPhone.asStateFlow()

    // Bookmarked posts + reels (single source of truth, persisted). The
    // bookmark toggle in Post/Reel cards and the Saved screens all read this
    // same set so saved items survive restarts.
    private val _savedPostIds = MutableStateFlow<Set<String>>(emptySet())
    val savedPostIds: StateFlow<Set<String>> = _savedPostIds.asStateFlow()
    private val _savedReelIds = MutableStateFlow<Set<String>>(emptySet())
    val savedReelIds: StateFlow<Set<String>> = _savedReelIds.asStateFlow()

    // Post drafts created in Create Post. Drafts screen is a live view of this.
    private val _drafts = MutableStateFlow<List<DraftPost>>(emptyList())
    val drafts: StateFlow<List<DraftPost>> = _drafts.asStateFlow()

    // Posts scheduled from Create Post. Scheduled Posts screen is a live view.
    private val _scheduledPosts = MutableStateFlow<List<ScheduledPost>>(emptyList())
    val scheduledPosts: StateFlow<List<ScheduledPost>> = _scheduledPosts.asStateFlow()

    // Communities the current user joined in Settings > Communities.
    private val _joinedCommunities = MutableStateFlow<Set<String>>(emptySet())
    val joinedCommunities: StateFlow<Set<String>> = _joinedCommunities.asStateFlow()

    // Content-privacy switches (show/hide like + comment counts app-wide).
    private val _contentPrivacy = MutableStateFlow(ContentPrivacy())
    val contentPrivacy: StateFlow<ContentPrivacy> = _contentPrivacy.asStateFlow()

    // Locally stored password used by Change Password validation. Empty when
    // the account was created without one (e.g. Google sign-in path).
    private val _password = MutableStateFlow("")
    val password: StateFlow<String> = _password.asStateFlow()

    // Live user catalog with follow state already merged, built in one place
    // and refreshed only when follow state changes. List screens (Followers,
    // Following) filter this instead of re-deriving the full list on open.
    private val _liveUsers = MutableStateFlow(buildLiveUsers())
    val liveUsers: StateFlow<List<User>> = _liveUsers.asStateFlow()

    private fun buildLiveUsers(): List<User> =
        MockData.users.filter { !it.isCurrentUser }.map { applyFollowState(it) }

    private fun refreshLiveUsers() {
        _liveUsers.value = buildLiveUsers()
    }

    // Real follow notifications (single source of truth), newest first.
    // Persisted so unread/read state survives app restarts.
    private val _notifications = MutableStateFlow<List<FollowNotification>>(emptyList())
    val notifications: StateFlow<List<FollowNotification>> = _notifications.asStateFlow()

    // Scholar verification application (single source of truth). The user may
    // hold at most one live application: while a PENDING or VERIFIED
    // application exists, resubmission is blocked; a REJECTED application may
    // be replaced by a new one. Persisted locally and swappable for a backend.
    private val _verificationApp = MutableStateFlow<ScholarApplication?>(null)
    val verificationApp: StateFlow<ScholarApplication?> = _verificationApp.asStateFlow()

    // Only VERIFIED applications surface in the Ask a Masla scholar list.
    // PENDING/REJECTED must never appear until a future admin approves.
    fun verifiedAsScholars(): List<Scholar> {
        val app = _verificationApp.value ?: return emptyList()
        if (app.status != VerificationStatus.VERIFIED) return emptyList()
        return listOf(
            Scholar(
                id = "verified_${app.id}",
                name = app.fullName,
                category = app.scholarType,
                region = if (app.expertise.isNotBlank()) app.expertise else app.region,
                country = app.country,
                initial = app.fullName.trim().firstOrNull()?.toString() ?: "",
                isVerified = true,
                isOnline = false,
                bio = app.introduction,
            )
        )
    }

    // Returns true when a new application is accepted. Blocked while a
    // PENDING or VERIFIED application already exists (no duplicates).
    fun submitVerificationApplication(app: ScholarApplication): Boolean {
        val existing = _verificationApp.value
        if (existing != null && existing.status != VerificationStatus.REJECTED) return false
        _verificationApp.value = app
        persistVerificationApp()
        return true
    }

    // Must be called once from the Application/Activity before any screen reads state.
    fun init(context: Context) {
        appContext = context.applicationContext
        _profile.value = loadProfile()
        loadFollowState()
        loadNotifications()
        loadVerificationApp()
        loadBlockedState()
        loadReports()
        loadSettingsState()
    }

    // Seed a sensible start set on first run only; afterwards always read the
    // persisted sets so restart preserves the user's follow state.
    private fun loadFollowState() {
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val followingRaw = prefs.getString(KEY_FOLLOWING, null)
        val followersRaw = prefs.getString(KEY_FOLLOWERS, null)
        if (followingRaw == null || followersRaw == null) {
            _followingIds.value = setOf("u2", "u3", "u4")
            _followerIds.value = setOf("u1", "u2", "u5")
            persistFollowState()
        } else {
            _followingIds.value = parseIdSet(followingRaw)
            _followerIds.value = parseIdSet(followersRaw)
        }
        refreshLiveUsers()
    }

    private fun parseIdSet(raw: String): Set<String> = runCatching {
        JSONArray(raw).let { arr -> (0 until arr.length()).map { arr.getString(it) }.toSet() }
    }.getOrDefault(emptySet())

    private fun persistFollowState() {
        if (!::appContext.isInitialized) return
        val following = JSONArray().apply { _followingIds.value.forEach { put(it) } }
        val followers = JSONArray().apply { _followerIds.value.forEach { put(it) } }
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_FOLLOWING, following.toString())
            .putString(KEY_FOLLOWERS, followers.toString())
            .apply()
    }

    fun isFollowing(userId: String): Boolean = userId in _followingIds.value

    fun followUser(userId: String) {
        if (userId == MockData.currentUser.id || isBlocked(userId)) return
        val wasFollowing = userId in _followingIds.value
        _followingIds.value = _followingIds.value + userId
        // Local single-device demo: following a local user makes them follow
        // you back. This is the real event that (a) keeps Followers/Following
        // synchronized and (b) emits one follow notification per actor.
        if (!wasFollowing) {
            _followerIds.value = _followerIds.value + userId
            addFollowNotification(userId)
        }
        refreshLiveUsers()
        persistFollowState()
        persistNotifications()
    }

    fun unfollowUser(userId: String) {
        if (userId == MockData.currentUser.id) return
        _followingIds.value = _followingIds.value - userId
        _followerIds.value = _followerIds.value - userId
        refreshLiveUsers()
        persistFollowState()
        // Unfollowing never creates a notification.
    }

    fun toggleFollowUser(userId: String): Boolean {
        if (userId == MockData.currentUser.id || isBlocked(userId)) return false
        return if (isFollowing(userId)) {
            unfollowUser(userId)
            false
        } else {
            followUser(userId)
            true
        }
    }

    // ------------------------------ BLOCK SYSTEM ------------------------------

    fun isBlocked(userId: String): Boolean = userId in _blockedIds.value

    // Blocks a user locally against the current account. Also unfollows them
    // so Follow/Unfollow and Followers/Following interaction with that user is
    // unavailable while blocked. Accepts duplicate-free ids only.
    fun blockUser(userId: String) {
        if (userId == MockData.currentUser.id || userId.isBlank()) return
        if (isBlocked(userId)) return
        _blockedIds.value = _blockedIds.value + userId
        if (isFollowing(userId)) unfollowUser(userId)
        persistBlockedState()
    }

    // Removes the block restriction. Follow/message/content restrictions caused
    // by the block are lifted; the existing Private Account rules still apply.
    fun unblockUser(userId: String) {
        if (userId.isBlank()) return
        if (!isBlocked(userId)) return
        _blockedIds.value = _blockedIds.value - userId
        persistBlockedState()
    }

    // Local-only report. Stored against the current session; never sent to a
    // backend. Re-reporting a user simply updates the stored reason.
    fun reportUser(userId: String, reason: String) {
        if (userId.isBlank()) return
        _reports.value = _reports.value + (userId to reason)
        persistReports()
    }

    private fun loadBlockedState() {
        val raw = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_BLOCKED, null)
        _blockedIds.value = if (raw == null) emptySet() else parseIdSet(raw)
    }

    private fun persistBlockedState() {
        if (!::appContext.isInitialized) return
        val arr = JSONArray().apply { _blockedIds.value.forEach { put(it) } }
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_BLOCKED, arr.toString())
            .apply()
    }

    private fun loadReports() {
        val raw = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_REPORTS, null) ?: return
        _reports.value = runCatching {
            JSONObject(raw).let { o -> o.keys().asSequence().associateWith { o.getString(it) } }
        }.getOrDefault(emptyMap())
    }

    private fun persistReports() {
        if (!::appContext.isInitialized) return
        val o = JSONObject()
        _reports.value.forEach { (id, reason) -> o.put(id, reason) }
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_REPORTS, o.toString())
            .apply()
    }

    // Removes a stored report (used by Moderation "Dismiss").
    fun removeReport(userId: String) {
        if (userId.isBlank() || userId !in _reports.value) return
        _reports.value = _reports.value - userId
        persistReports()
    }

    // ------------------------- SETTINGS PERSISTENCE -----------------------

    private fun loadSettingsState() {
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _darkMode.value = prefs.getBoolean(KEY_DARK_MODE, false)
        _privateAccount.value = prefs.getBoolean(KEY_PRIVATE, false)
        _pushEnabled.value = prefs.getBoolean(KEY_PUSH, true)
        _appLanguage.value = prefs.getString(KEY_LANGUAGE, _profile.value.language.ifBlank { "English" })
            ?: "English"
        _recoveryEmail.value = prefs.getString("${KEY_RECOVERY}_email", "") ?: ""
        _recoveryPhone.value = prefs.getString("${KEY_RECOVERY}_phone", "") ?: ""
        _savedPostIds.value = parseIdSet(prefs.getString(KEY_SAVED_POSTS, null) ?: "[]")
        _savedReelIds.value = parseIdSet(prefs.getString(KEY_SAVED_REELS, null) ?: "[]")
        _joinedCommunities.value = parseIdSet(prefs.getString(KEY_JOINED_COMMUNITIES, null) ?: "[]")
        _drafts.value = parseDrafts(prefs.getString(KEY_DRAFTS, null))
        _scheduledPosts.value = parseScheduled(prefs.getString(KEY_SCHEDULED, null))
        _contentPrivacy.value = runCatching {
            val o = JSONObject(prefs.getString(KEY_CONTENT_PRIVACY, null) ?: "{}")
            ContentPrivacy(
                showLikeCounts = o.optBoolean("showLikeCounts", true),
                showCommentCounts = o.optBoolean("showCommentCounts", true)
            )
        }.getOrDefault(ContentPrivacy())
        _password.value = prefs.getString(KEY_PASSWORD, "") ?: ""
    }

    private fun persistValue(action: (android.content.SharedPreferences.Editor) -> Unit) {
        if (!::appContext.isInitialized) return
        val editor = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        action(editor)
        editor.apply()
    }

    fun setDarkMode(enabled: Boolean) {
        _darkMode.value = enabled
        persistValue { it.putBoolean(KEY_DARK_MODE, enabled) }
    }

    fun setPrivateAccount(enabled: Boolean) {
        _privateAccount.value = enabled
        persistValue { it.putBoolean(KEY_PRIVATE, enabled) }
    }

    fun setPushEnabled(enabled: Boolean) {
        _pushEnabled.value = enabled
        persistValue { it.putBoolean(KEY_PUSH, enabled) }
    }

    fun setAppLanguage(lang: String) {
        if (lang.isBlank()) return
        _appLanguage.value = lang
        persistValue { it.putString(KEY_LANGUAGE, lang) }
    }

    fun saveRecovery(email: String, phone: String) {
        _recoveryEmail.value = email.trim()
        _recoveryPhone.value = phone.trim()
        persistValue {
            it.putString("${KEY_RECOVERY}_email", email.trim())
                .putString("${KEY_RECOVERY}_phone", phone.trim())
        }
    }

    // --------------------------- SAVED CONTENT ---------------------------

    fun isPostSaved(postId: String): Boolean = postId in _savedPostIds.value

    fun isReelSaved(reelId: String): Boolean = reelId in _savedReelIds.value

    fun toggleSavedPost(postId: String) {
        if (postId.isBlank()) return
        _savedPostIds.value = if (isPostSaved(postId)) _savedPostIds.value - postId
        else _savedPostIds.value + postId
        persistStringSet(KEY_SAVED_POSTS, _savedPostIds.value)
    }

    fun toggleSavedReel(reelId: String) {
        if (reelId.isBlank()) return
        _savedReelIds.value = if (isReelSaved(reelId)) _savedReelIds.value - reelId
        else _savedReelIds.value + reelId
        persistStringSet(KEY_SAVED_REELS, _savedReelIds.value)
    }

    private fun persistStringSet(key: String, values: Set<String>) {
        persistValue {
            val arr = JSONArray().apply { values.forEach { put(it) } }
            it.putString(key, arr.toString())
        }
    }

    // ------------------------------ DRAFTS ------------------------------

    fun addDraft(text: String, mediaCount: Int) {
        if (text.isBlank() && mediaCount == 0) return
        val draft = DraftPost("d${System.currentTimeMillis()}", text, mediaCount, System.currentTimeMillis())
        _drafts.value = listOf(draft) + _drafts.value
        persistDrafts()
    }

    fun removeDraft(id: String) {
        _drafts.value = _drafts.value.filterNot { it.id == id }
        persistDrafts()
    }

    private fun persistDrafts() {
        persistValue {
            val arr = JSONArray()
            _drafts.value.forEach { d ->
                arr.put(JSONObject().put("id", d.id).put("text", d.text)
                    .put("mediaCount", d.mediaCount).put("createdAt", d.createdAt))
            }
            it.putString(KEY_DRAFTS, arr.toString())
        }
    }

    private fun parseDrafts(raw: String?): List<DraftPost> = runCatching {
        JSONArray(raw).let { arr ->
            (0 until arr.length()).mapNotNull { i ->
                val e = arr.getJSONObject(i)
                DraftPost(e.optString("id"), e.optString("text"), e.optInt("mediaCount"), e.optLong("createdAt"))
            }
        }
    }.getOrDefault(emptyList())

    // --------------------------- SCHEDULED ------------------------------

    fun addScheduledPost(text: String, mediaCount: Int, scheduledAt: Long) {
        if ((text.isBlank() && mediaCount == 0) || scheduledAt <= 0) return
        val post = ScheduledPost("s${System.currentTimeMillis()}", text, mediaCount, scheduledAt, System.currentTimeMillis())
        _scheduledPosts.value = (listOf(post) + _scheduledPosts.value).sortedBy { it.scheduledAt }
        persistScheduled()
    }

    fun removeScheduledPost(id: String) {
        _scheduledPosts.value = _scheduledPosts.value.filterNot { it.id == id }
        persistScheduled()
    }

    private fun persistScheduled() {
        persistValue {
            val arr = JSONArray()
            _scheduledPosts.value.forEach { s ->
                arr.put(JSONObject().put("id", s.id).put("text", s.text)
                    .put("mediaCount", s.mediaCount).put("scheduledAt", s.scheduledAt)
                    .put("createdAt", s.createdAt))
            }
            it.putString(KEY_SCHEDULED, arr.toString())
        }
    }

    private fun parseScheduled(raw: String?): List<ScheduledPost> = runCatching {
        JSONArray(raw).let { arr ->
            (0 until arr.length()).mapNotNull { i ->
                val e = arr.getJSONObject(i)
                ScheduledPost(e.optString("id"), e.optString("text"), e.optInt("mediaCount"), e.optLong("scheduledAt"), e.optLong("createdAt"))
            }
        }
    }.getOrDefault(emptyList())

    // --------------------------- COMMUNITIES ----------------------------

    fun isJoinedCommunity(groupId: String): Boolean = groupId in _joinedCommunities.value

    fun toggleJoinedCommunity(groupId: String) {
        if (groupId.isBlank()) return
        _joinedCommunities.value = if (isJoinedCommunity(groupId)) _joinedCommunities.value - groupId
        else _joinedCommunities.value + groupId
        persistStringSet(KEY_JOINED_COMMUNITIES, _joinedCommunities.value)
    }

    // ------------------------- CONTENT PRIVACY --------------------------

    fun setContentPrivacy(privacy: ContentPrivacy) {
        _contentPrivacy.value = privacy
        persistValue {
            it.putString(KEY_CONTENT_PRIVACY, JSONObject()
                .put("showLikeCounts", privacy.showLikeCounts)
                .put("showCommentCounts", privacy.showCommentCounts).toString())
        }
    }

    // ----------------------------- PASSWORD -----------------------------

    // Stores the password the user last signed in with (email/password flows
    // only). Used as the "Current Password" source for Change Password.
    fun setPassword(password: String) {
        if (password.isBlank()) return
        _password.value = password
        persistValue { it.putString(KEY_PASSWORD, password) }
    }

    // Validates the entered current password against the stored one and, on
    // success, persists the new password. Returns human-readable error or null.
    fun changePassword(current: String, newPassword: String): String? {
        val stored = _password.value
        // Accounts created without a password (Google path) have nothing to
        // compare yet, so any non-blank current password is accepted.
        if (stored.isNotEmpty() && current != stored) return "Current password is incorrect"
        if (newPassword.length < 6) return "Password must be at least 6 characters"
        _password.value = newPassword
        persistValue { it.putString(KEY_PASSWORD, newPassword) }
        return null
    }

    // -------------------------- ACCOUNT ACTIONS -------------------------

    // Wipes every persisted local value and resets in-memory settings to their
    // defaults. Called from the Settings Delete Account action; the caller is
    // expected to route the user back to the login screen afterwards.
    fun deleteAccount() {
        if (::appContext.isInitialized) {
            appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .apply()
        }
        _profile.value = UserProfile(
            fullName = MockData.currentUser.fullName,
            username = MockData.currentUser.username,
            bio = MockData.currentUser.bio
        )
        _followingIds.value = emptySet()
        _followerIds.value = emptySet()
        _notifications.value = emptyList()
        _verificationApp.value = null
        _blockedIds.value = emptySet()
        _reports.value = emptyMap()
        _savedPostIds.value = emptySet()
        _savedReelIds.value = emptySet()
        _drafts.value = emptyList()
        _scheduledPosts.value = emptyList()
        _joinedCommunities.value = emptySet()
        _contentPrivacy.value = ContentPrivacy()
        _darkMode.value = false
        _privateAccount.value = false
        _pushEnabled.value = true
        _appLanguage.value = _profile.value.language.ifBlank { "English" }
        _recoveryEmail.value = ""
        _recoveryPhone.value = ""
        _password.value = ""
        refreshLiveUsers()
        _session.value = Session()
    }

    fun isPrivateUser(userId: String): Boolean {
        // The account owner's own privacy setting overrides the mock default so
        // toggling Private Account in Settings is honored everywhere.
        if (userId == MockData.currentUser.id) return _privateAccount.value
        return MockData.users.find { it.id == userId }?.isPrivate ?: false
    }

    // Access rule for a viewed profile: public accounts are always viewable;
    // private accounts only when the current user is an approved follower.
    // Locally, following a private account (auto-approved in the mutual-follow
    // demo) is what grants approval. Blocking hides the profile regardless of
    // the targeted account's own privacy setting (Block is a separate layer
    // on top of the private-account system).
    fun canViewProfile(userId: String): Boolean =
        !isBlocked(userId) && (!isPrivateUser(userId) || isFollowing(userId))

    // Followers/Following lists for a viewed user, derived from the live user
    // catalog (follow state already merged) plus the mock per-user follow
    // graph. The current user's own lists use the persisted SessionManager
    // sets, so the existing behavior is preserved. No per-open catalog work.
    fun followersOf(userId: String): List<User> {
        val set = if (userId == MockData.currentUser.id) _followerIds.value
        else MockData.userFollowers[userId].orEmpty()
        return liveUsers.value.filter { it.id in set }
    }

    fun followingOf(userId: String): List<User> {
        val set = if (userId == MockData.currentUser.id) _followingIds.value
        else MockData.userFollowing[userId].orEmpty()
        return liveUsers.value.filter { it.id in set }
    }

    // ------------------------- FOLLOW NOTIFICATIONS -------------------------

    private fun loadNotifications() {
        val raw = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_NOTIFICATIONS, null)
        if (raw == null) {
            seedNotifications()
        } else {
            _notifications.value = parseNotifications(raw)
        }
    }

    // First run only: derive real notifications from the seed followers so the
    // screen shows genuine state (not static mock rows). Newest unread on top.
    private fun seedNotifications() {
        val now = System.currentTimeMillis()
        _notifications.value = listOfNotNull(
            buildFollowNotification("u1", now - 5 * 3600_000L, true),
            buildFollowNotification("u2", now - 2 * 3600_000L, true),
            buildFollowNotification("u5", now - 15 * 60_000L, false)
        ).filter { it.actorUserId in _followerIds.value }
        persistNotifications()
    }

    private fun parseNotifications(raw: String): List<FollowNotification> = runCatching {
        JSONArray(raw).let { arr ->
            (0 until arr.length()).mapNotNull { i ->
                val e = arr.getJSONObject(i)
                FollowNotification(
                    id = e.optString("id"),
                    actorUserId = e.optString("actorUserId"),
                    actorName = e.optString("actorName"),
                    actorUsername = e.optString("actorUsername"),
                    actorProfilePhotoPath = e.optString("actorProfilePhotoPath"),
                    createdAt = e.optLong("createdAt"),
                    isRead = e.optBoolean("isRead")
                )
            }
        }
    }.getOrDefault(emptyList())

    private fun persistNotifications() {
        if (!::appContext.isInitialized) return
        val arr = JSONArray()
        _notifications.value.forEach { n ->
            arr.put(
                JSONObject()
                    .put("id", n.id)
                    .put("actorUserId", n.actorUserId)
                    .put("actorName", n.actorName)
                    .put("actorUsername", n.actorUsername)
                    .put("actorProfilePhotoPath", n.actorProfilePhotoPath)
                    .put("createdAt", n.createdAt)
                    .put("isRead", n.isRead)
            )
        }
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_NOTIFICATIONS, arr.toString())
            .apply()
    }

    private fun buildFollowNotification(userId: String, createdAt: Long, isRead: Boolean): FollowNotification? {
        val actor = MockData.users.find { it.id == userId } ?: return null
        return FollowNotification(
            id = "n_${userId}_$createdAt",
            actorUserId = actor.id,
            actorName = actor.fullName,
            actorUsername = actor.username,
            actorProfilePhotoPath = "",
            createdAt = createdAt,
            isRead = isRead
        )
    }

    // Emits an unread follow notification for the actor, deduplicated per user.
    fun addFollowNotification(userId: String) {
        if (_notifications.value.any { it.actorUserId == userId }) return
        val n = buildFollowNotification(userId, System.currentTimeMillis(), false) ?: return
        _notifications.value = listOf(n) + _notifications.value
        persistNotifications()
    }

    // Marks only the opened notification read; opening the screen alone never
    // marks everything read.
    fun markNotificationRead(id: String) {
        if (_notifications.value.none { it.id == id && !it.isRead }) return
        _notifications.value = _notifications.value.map { if (it.id == id) it.copy(isRead = true) else it }
        persistNotifications()
    }

    private fun loadVerificationApp() {
        val raw = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_VERIFICATION, null) ?: return
        _verificationApp.value = parseVerificationApp(raw)
    }

    private fun parseVerificationApp(raw: String): ScholarApplication? = runCatching {
        val o = JSONObject(raw)
        ScholarApplication(
            id = o.optString("id"),
            fullName = o.optString("fullName"),
            username = o.optString("username"),
            country = o.optString("country"),
            region = o.optString("region"),
            city = o.optString("city"),
            scholarType = o.optString("scholarType"),
            expertise = o.optString("expertise"),
            institution = o.optString("institution"),
            qualification = o.optString("qualification"),
            specialization = o.optString("specialization"),
            educationYears = o.optString("educationYears"),
            experienceYears = o.optString("experienceYears"),
            introduction = o.optString("introduction"),
            certificateUri = o.optString("certificateUri"),
            certificateName = o.optString("certificateName"),
            supportingUri = o.optString("supportingUri"),
            supportingName = o.optString("supportingName"),
            email = o.optString("email"),
            phone = o.optString("phone"),
            status = runCatching { VerificationStatus.valueOf(o.optString("status")) }
                .getOrDefault(VerificationStatus.PENDING),
            submittedAt = o.optLong("submittedAt")
        )
    }.getOrNull()

    private fun persistVerificationApp() {
        if (!::appContext.isInitialized) return
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val app = _verificationApp.value
        if (app == null) {
            prefs.edit().remove(KEY_VERIFICATION).apply()
            return
        }
        prefs.edit()
            .putString(
                KEY_VERIFICATION,
                JSONObject()
                    .put("id", app.id)
                    .put("fullName", app.fullName)
                    .put("username", app.username)
                    .put("country", app.country)
                    .put("region", app.region)
                    .put("city", app.city)
                    .put("scholarType", app.scholarType)
                    .put("expertise", app.expertise)
                    .put("institution", app.institution)
                    .put("qualification", app.qualification)
                    .put("specialization", app.specialization)
                    .put("educationYears", app.educationYears)
                    .put("experienceYears", app.experienceYears)
                    .put("introduction", app.introduction)
                    .put("certificateUri", app.certificateUri)
                    .put("certificateName", app.certificateName)
                    .put("supportingUri", app.supportingUri)
                    .put("supportingName", app.supportingName)
                    .put("email", app.email)
                    .put("phone", app.phone)
                    .put("status", app.status.name)
                    .put("submittedAt", app.submittedAt)
                    .toString()
            )
            .apply()
    }

    // Merges live follow state onto a user so every screen shares the same
    // truth: isFollowing + the followers count reflect the current follow set.
    fun applyFollowState(user: User): User {
        if (user.isCurrentUser) return user
        val following = isFollowing(user.id)
        return user.copy(
            isFollowing = following,
            followers = user.followers + if (following) 1 else 0
        )
    }

    private fun loadProfile(): UserProfile {
        val raw = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_PROFILE, null)
        if (!raw.isNullOrBlank()) {
            runCatching { return parseProfile(raw) }
        }
        return seedFromSetup()
    }

    private fun seedFromSetup(): UserProfile {
        val setup = _session.value.islamicProfile ?: IslamicProfile()
        return UserProfile(
            fullName = MockData.currentUser.fullName,
            username = MockData.currentUser.username,
            bio = MockData.currentUser.bio.ifBlank { setup.bio },
            country = setup.country,
            region = setup.region,
            city = setup.city,
            language = setup.language,
            maslak = setup.maslak,
            islamicCategory = setup.category
        )
    }

    private fun persist(profile: UserProfile) {
        if (!::appContext.isInitialized) return
        val links = JSONArray()
        profile.links.forEach { link ->
            links.put(JSONObject().put("label", link.label).put("url", link.url))
        }
        val json = JSONObject()
            .put("fullName", profile.fullName)
            .put("username", profile.username)
            .put("bio", profile.bio)
            .put("country", profile.country)
            .put("region", profile.region)
            .put("city", profile.city)
            .put("language", profile.language)
            .put("maslak", profile.maslak)
            .put("category", profile.islamicCategory)
            .put("links", links)
            .put("profilePhotoPath", profile.profilePhotoPath)
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PROFILE, json.toString())
            .apply()
    }

    private fun parseProfile(raw: String): UserProfile {
        val o = JSONObject(raw)
        val links = o.optJSONArray("links")?.let { arr ->
            (0 until arr.length()).map { i ->
                val e = arr.getJSONObject(i)
                ProfileLink(e.optString("label"), e.optString("url"))
            }
        } ?: emptyList()
        return UserProfile(
            fullName = o.optString("fullName"),
            username = o.optString("username"),
            bio = o.optString("bio"),
            country = o.optString("country"),
            region = o.optString("region"),
            city = o.optString("city"),
            language = o.optString("language"),
            maslak = o.optString("maslak"),
            islamicCategory = o.optString("category"),
            links = links,
            profilePhotoPath = o.optString("profilePhotoPath")
        )
    }

    // The actual save entry point used by Edit Profile. Updates in-memory
    // state, persists locally, and mirrors the values onto the current user.
    fun saveProfile(profile: UserProfile) {
        _profile.value = profile
        persist(profile)
        val updatedUser = MockData.currentUser.copy(
            fullName = profile.fullName.ifBlank { MockData.currentUser.fullName },
            username = profile.username.ifBlank { MockData.currentUser.username },
            bio = profile.bio,
            links = profile.links
        )
        _session.value = _session.value.copy(currentUser = updatedUser)
    }

    fun getProfile(): UserProfile = _profile.value

    // Persists a freshly cropped profile photo path. Used by the Complete
    // Profile (signup) flow; Edit Profile keeps its pending path and commits
    // through saveProfile so Cancel can discard unsaved changes.
    fun setProfilePhoto(path: String) {
        val updated = _profile.value.copy(profilePhotoPath = path)
        _profile.value = updated
        persist(updated)
    }

    fun login(user: User = MockData.currentUser) {
        // Seed any not-yet-saved profile fields from the signup setup.
        val setup = _session.value.islamicProfile ?: IslamicProfile()
        val seeded = _profile.value.copy(
            fullName = _profile.value.fullName.ifBlank { user.fullName },
            username = _profile.value.username.ifBlank { user.username },
            bio = _profile.value.bio.ifBlank { setup.bio },
            country = _profile.value.country.ifBlank { setup.country },
            region = _profile.value.region.ifBlank { setup.region },
            city = _profile.value.city.ifBlank { setup.city },
            language = _profile.value.language.ifBlank { setup.language },
            maslak = _profile.value.maslak.ifBlank { setup.maslak },
            islamicCategory = _profile.value.islamicCategory.ifBlank { setup.category }
        )
        _profile.value = seeded
        persist(seeded)
        _session.value = Session(isLoggedIn = true, currentUser = user)
    }

    // Holds the completed multi-step profile setup so entered data is
    // preserved and ready to be pushed to Firebase/API when the backend is connected.
    fun saveProfileSetup(profile: IslamicProfile) {
        _session.value = _session.value.copy(islamicProfile = profile)
        val current = _profile.value
        val merged = current.copy(
            country = profile.country.ifBlank { current.country },
            region = profile.region.ifBlank { current.region },
            city = profile.city.ifBlank { current.city },
            language = profile.language.ifBlank { current.language },
            maslak = profile.maslak.ifBlank { current.maslak },
            islamicCategory = profile.category.ifBlank { current.islamicCategory },
            bio = profile.bio.ifBlank { current.bio }
        )
        _profile.value = merged
        if (::appContext.isInitialized) persist(merged)
    }

    fun logout() {
        _session.value = Session()
    }

    fun isLoggedIn() = _session.value.isLoggedIn
}
