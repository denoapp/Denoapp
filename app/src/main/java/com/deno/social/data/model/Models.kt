package com.deno.social.data.model

data class User(
    val id: String,
    val fullName: String,
    val username: String,
    val bio: String = "",
    val avatarInitial: String = "",
    val followers: Int = 0,
    val following: Int = 0,
    val postsCount: Int = 0,
    val isFollowing: Boolean = false,
    val isCurrentUser: Boolean = false,
    // Private accounts hide their Followers/Following lists and posts from
    // everyone the account owner has not approved as a follower.
    val isPrivate: Boolean = false,
    // Optional user-provided profile links. Display/clicking is driven
    // purely by these values (no fake defaults).
    val links: List<ProfileLink> = emptyList()
)

// A single user-provided link shown on the profile. `label` is the display
// name (e.g. "Instagram"); `url` must be a real URL before it is saved.
data class ProfileLink(
    val label: String = "",
    val url: String = ""
)

// The editable profile state owned by SessionManager. This is the single
// source of truth for what is shown on the Profile screen and edited in
// Edit Profile. Kept as a plain data class so it can be pushed to
// Firebase/API later without UI changes.
data class UserProfile(
    val fullName: String = "",
    val username: String = "",
    val bio: String = "",
    val country: String = "",
    val region: String = "",
    val city: String = "",
    val language: String = "",
    val maslak: String = "",
    val islamicCategory: String = "",
    val links: List<ProfileLink> = emptyList(),
    // Absolute path of the cropped profile photo saved to the app cache.
    // Empty when no photo is set.
    val profilePhotoPath: String = ""
)

data class Post(
    val id: String,
    val user: User,
    val text: String,
    val timeAgo: String,
    val likes: Int,
    val comments: Int,
    val isLiked: Boolean = false,
    val hasMedia: Boolean = true
)

data class Reel(
    val id: String,
    val user: User,
    val caption: String,
    val videoUrl: String,
    val likes: Int,
    val comments: Int,
    val isLiked: Boolean = false
)

data class Comment(
    val id: String,
    val user: User,
    val text: String,
    val timeAgo: String
)

data class NotificationItem(
    val id: String,
    val type: String, // like, comment, follow
    val user: User,
    val message: String,
    val timeAgo: String,
    val isRead: Boolean = false
)

// Real, persisted follow notification emitted by SessionManager whenever a
// local user follows the current user. Single source of truth for the
// Notifications screen; NotificationItem rows are derived from these.
data class FollowNotification(
    val id: String,
    val actorUserId: String,
    val actorName: String,
    val actorUsername: String,
    val actorProfilePhotoPath: String = "",
    val createdAt: Long,
    val isRead: Boolean = false
)

// A post (text + optional media) the user saved for later publishing in the
// Create Post screen. Owned + persisted by SessionManager so the Drafts
// screen shows the user's real, persisted draft state.
data class DraftPost(
    val id: String,
    val text: String,
    val mediaCount: Int,
    val createdAt: Long
)

// A post the user chose to publish at a later time from the Create Post
// screen. Owned + persisted by SessionManager; shown in Scheduled Posts.
data class ScheduledPost(
    val id: String,
    val text: String,
    val mediaCount: Int,
    val scheduledAt: Long,
    val createdAt: Long
)

// Content-privacy switches owned + persisted by SessionManager. When a count
// is hidden the app does not render that number anywhere in the feed.
data class ContentPrivacy(
    val showLikeCounts: Boolean = true,
    val showCommentCounts: Boolean = true
)

data class Session(
    val isLoggedIn: Boolean = false,
    val currentUser: User? = null,
    val islamicProfile: IslamicProfile? = null
)

// Islamic profile data collected during the multi-step Complete Profile flow.
// Kept as a plain data class so it can be persisted to Firebase/API later without UI changes.
// Verification (Aalim/Mufti/Hafiz badge) is NOT derived from category selection; it will come
// from a future backend/admin verification system.
data class IslamicProfile(
    val country: String = "",
    val region: String = "",
    val city: String = "",
    val language: String = "",
    val maslak: String = "",
    val category: String = "",
    val bio: String = "",
    val showMaslak: Boolean = true,
    val showRegion: Boolean = true
)

data class ChatMessage(
    val id: String,
    val text: String,
    val isMe: Boolean,
    val time: String,
    // Sender display name used for group chats; empty for 1-to-1 chats.
    val sender: String = "",
    // Text of the message this one replies to; empty when not a reply.
    val replyTo: String = "",
    // Content Uri (or cache file Uri) of an attached image; empty when none.
    val imageUri: String = "",
    // Absolute local path of a recorded voice note; empty when not a voice message.
    val audioUri: String = "",
    // Real duration of the voice note in milliseconds (measured at record time).
    val audioDurationMs: Long = 0L
)

data class Conversation(
    val userId: String,
    val name: String,
    val username: String,
    val initial: String,
    val lastMessage: String,
    val time: String,
    val unread: Int,
    val isOnline: Boolean,
    val messages: List<ChatMessage> = emptyList()
)

// An Islamic scholar available under the "Ask a Masla" section.
// `isVerified` is an explicit field sourced from the local repository only —
// it is never derived automatically from category selection.
data class Scholar(
    val id: String,
    val name: String,
    val category: String, // Aalim, Mufti, Hafiz, Qari, Islamic Teacher
    val region: String = "",
    val country: String = "",
    val initial: String = "",
    val isVerified: Boolean = false,
    val isOnline: Boolean = false,
    val bio: String = ""
)

// An Islamic discussion group shown under the "Groups" section.
data class MessageGroup(
    val id: String,
    val name: String,
    val description: String = "",
    val initial: String = "",
    val memberCount: Int = 1,
    val topic: String = "",
    val lastActivity: String = "",
    val lastMessage: String = "",
    val isJoined: Boolean = false,
    val messages: List<ChatMessage> = emptyList(),
    // Reference to the selected group photo (local content Uri) when present.
    val photoUri: String = "",
    // Id of the user who created the group; locally this is MockData.currentUser.id.
    val creatorId: String = "",
    // Initial member ids (creator + selected participants).
    val memberIds: List<String> = emptyList(),
    // Ids of all admins. The creator is always the first admin.
    val adminIds: List<String> = emptyList(),
    // Group settings (admin-controlled, functional in the local store).
    val membersCanSend: Boolean = true,
    val membersCanAddParticipants: Boolean = true,
    val createdAt: String = ""
)

// Lifecycle of a scholar verification application. Locally it is always
// PENDING; VERIFIED/REJECTED are set by a future admin/backend approval
// system. Only VERIFIED applications are exposed in the public scholar list.
enum class VerificationStatus { PENDING, VERIFIED, REJECTED }

// A verification application submitted by the current user. Persisted in
// SharedPreferences via SessionManager; kept as a plain data class so it can
// be pushed to Firebase/API later without UI changes.
data class ScholarApplication(
    val id: String,
    val fullName: String,
    val username: String,
    val country: String,
    val region: String,
    val city: String,
    // One of the 10 scholar types; when "OTHER", `expertise` is required.
    val scholarType: String,
    val expertise: String = "",
    val institution: String = "",
    val qualification: String = "",
    val specialization: String = "",
    val educationYears: String = "",
    val experienceYears: String = "",
    val introduction: String = "",
    // Real local URIs (and display names) from the device document/photo picker.
    val certificateUri: String = "",
    val certificateName: String = "",
    val supportingUri: String = "",
    val supportingName: String = "",
    val email: String,
    val phone: String = "",
    val status: VerificationStatus = VerificationStatus.PENDING,
    val submittedAt: Long = System.currentTimeMillis()
)
