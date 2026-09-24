package com.deno.social.data.mock

import com.deno.social.data.model.*

object MockData {
    val currentUser = User(
        id = "me",
        fullName = "Alex Rivera",
        username = "alex_deno",
        bio = "Learn · Share · Inspire",
        avatarInitial = "A",
        followers = 1280,
        following = 340,
        postsCount = 42,
        isCurrentUser = true
    )

    val users = listOf(
        currentUser,
        User("u1", "Sara Khan", "sara_k", "Creator & learner", "S", 8900, 420, 56),
        User("u2", "Jordan Lee", "jordan_l", "Daily inspiration", "J", 15200, 180, 120),
        User("u3", "Maya Chen", "maya_c", "Design & code", "M", 3400, 560, 28),
        User("u4", "Omar Hassan", "omar_h", "Stories that matter", "O", 6700, 210, 89),
        User("u5", "Priya Sharma", "priya_s", "Learn every day", "P", 4100, 390, 45, isPrivate = true),
        User("u6", "Chris Wong", "chris_w", "Visual stories", "C", 9800, 150, 200, isPrivate = true)
    )

    // Follow graph for the other local users (directional). Used to derive a
    // viewed user's Followers/Following lists; SessionManager layers the live
    // follow state on top for the current user's own lists. The current user
    // ("me") is intentionally not part of these maps.
    val userFollowers = mapOf(
        "u1" to setOf("u2", "u3", "u4", "u5"),
        "u2" to setOf("u1", "u3", "u4", "u5"),
        "u3" to setOf("u1", "u2", "u4", "u6"),
        "u4" to setOf("u1", "u2", "u5", "u6"),
        "u5" to setOf("u1", "u2", "u3"),
        "u6" to setOf("u1", "u3", "u5")
    )

    val userFollowing = mapOf(
        "u1" to setOf("u2", "u3", "u6"),
        "u2" to setOf("u1", "u4", "u5"),
        "u3" to setOf("u1", "u4", "u5", "u6"),
        "u4" to setOf("u1", "u2", "u3"),
        "u5" to setOf("u1", "u2", "u4"),
        "u6" to setOf("u1", "u2", "u4")
    )

    val posts = listOf(
        Post("p1", users[1], "Just finished a new tutorial series. Learning never stops! 📚", "2h", 342, 28, false),
        Post("p2", users[2], "Morning motivation: share what you learn today.", "4h", 891, 56, true),
        Post("p3", users[3], "New design system drop. Clean, minimal, focused.", "6h", 1204, 89, false),
        Post("p4", users[4], "Behind the scenes of my latest reel. Hard work pays off.", "8h", 567, 34, false),
        Post("p5", users[5], "Community tip: ask better questions, get better answers.", "12h", 423, 67, true),
        Post("p6", currentUser, "Excited to share more on DENO. Learn · Share · Inspire.", "1d", 210, 15, false)
    )

    val reels = listOf(
        Reel("r1", users[1], "Quick tip that changed how I learn", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4", 12400, 342),
        Reel("r2", users[2], "60 seconds of pure focus", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4", 8900, 156, true),
        Reel("r3", users[3], "Design process in under a minute", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerFun.mp4", 22100, 890),
        Reel("r4", users[4], "Storytelling that sticks", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerJoyrides.mp4", 15600, 420),
        Reel("r5", users[5], "Learn something new every day", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerMeltdowns.mp4", 9800, 210)
    )

    val comments = listOf(
        Comment("c1", users[2], "This is so helpful!", "1h"),
        Comment("c2", users[3], "Love this approach", "2h"),
        Comment("c3", users[4], "Thanks for sharing", "3h"),
        Comment("c4", users[5], "Exactly what I needed", "5h"),
        Comment("c5", users[1], "Great content as always", "1d")
    )

    val notifications = listOf(
        NotificationItem("n1", "like", users[1], "liked your post", "2m", false),
        NotificationItem("n2", "follow", users[2], "started following you", "15m", false),
        NotificationItem("n3", "comment", users[3], "commented: Amazing work!", "1h", false),
        NotificationItem("n4", "like", users[4], "liked your reel", "3h", true),
        NotificationItem("n5", "follow", users[5], "started following you", "5h", true),
        NotificationItem("n6", "comment", users[6], "commented: Inspiring!", "1d", true)
    )

    // Islamic scholars for "Ask a Masla". `isVerified` is explicit local data;
    // most scholars are unverified until a future admin system approves them.
    val scholars = listOf(
        Scholar(
            "s1", "Mufti Abdul Rahman", "Mufti",
            region = "Riyadh", country = "Saudi Arabia", initial = "A",
            isVerified = true, isOnline = true,
            bio = "Senior Mufti with 20+ years in Islamic jurisprudence and issuing fatwa on daily matters."
        ),
        Scholar(
            "s2", "Maulana Yusuf Ansari", "Aalim",
            region = "Deoband", country = "India", initial = "Y",
            isVerified = true, isOnline = false,
            bio = "Aalim specialising in Quranic sciences and Hadith. Available for detailed questions."
        ),
        Scholar(
            "s3", "Hafiz Bilal Ahmed", "Hafiz",
            region = "Karachi", country = "Pakistan", initial = "B",
            isVerified = false, isOnline = true,
            bio = "Hafiz of the Quran guiding memorisation and revision plans."
        ),
        Scholar(
            "s4", "Qari Suleiman Khan", "Qari",
            region = "Jeddah", country = "Saudi Arabia", initial = "S",
            isVerified = true, isOnline = false,
            bio = "Certified Qari holding Ijazah in Hafs 'an 'Asim. Focus on Tajweed correction."
        ),
        Scholar(
            "s5", "Ustadha Fatima Noor", "Islamic Teacher",
            region = "London", country = "United Kingdom", initial = "F",
            isVerified = false, isOnline = false,
            bio = "Islamic teacher for women covering fardh 'ilm, aqeedah and family matters."
        ),
        Scholar(
            "s6", "Maulana Ibrahim Salim", "Aalim",
            region = "Cairo", country = "Egypt", initial = "I",
            isVerified = true, isOnline = true,
            bio = "Graduate of Al-Azhar University. Lectures on daily fiqh and sirah."
        ),
        Scholar(
            "s7", "Qari Usman Malik", "Qari",
            region = "Lahore", country = "Pakistan", initial = "U",
            isVerified = false, isOnline = false,
            bio = "Reciter and Tajweed teacher for all levels."
        ),
        Scholar(
            "s8", "Mufti Hassan Raza", "Mufti",
            region = "Birmingham", country = "United Kingdom", initial = "H",
            isVerified = true, isOnline = false,
            bio = "Mufti dealing with business transactions and family matters."
        )
    )

    val messageGroups = listOf(
        MessageGroup(
            id = "g1", name = "Quran & Tafsir", initial = "QT",
            description = "Weekly tafsir discussions and reflections on the Quran.",
            memberCount = 1240, topic = "Quran",
            lastActivity = "2h", lastMessage = "JazakAllah khair for the explanation!",
            isJoined = true,
            creatorId = "me", adminIds = listOf("me"), memberIds = listOf("me", "u1", "u4"),
            membersCanSend = true, membersCanAddParticipants = true,
            messages = listOf(
                ChatMessage("g1m1", "Assalamu alaykum everyone, welcome to the group.", false, "08:00", "Maulana Yusuf Ansari"),
                ChatMessage("g1m2", "Today's reflection is on Surah Al-Fatiha.", false, "08:15", "Hafiz Bilal Ahmed"),
                ChatMessage("g1m3", "Beautiful start, BarakAllah feek.", true, "08:20"),
                ChatMessage("g1m4", "JazakAllah khair for the explanation!", false, "08:25", "Qari Usman Malik")
            )
        ),
        MessageGroup(
            id = "g2", name = "Fiqh Q&A", initial = "FQ",
            description = "Ask questions about daily Islamic rulings (fiqh).",
            memberCount = 860, topic = "Fiqh",
            lastActivity = "5h", lastMessage = "Wudu rules explained in detail.",
            isJoined = true,
            creatorId = "me", adminIds = listOf("me"), memberIds = listOf("me", "u2", "u5"),
            membersCanSend = true, membersCanAddParticipants = true,
            messages = listOf(
                ChatMessage("g2m1", "Post your fiqh questions here.", false, "Yesterday", "Mufti Abdul Rahman"),
                ChatMessage("g2m2", "Question: what breaks wudu?", true, "Yesterday"),
                ChatMessage("g2m3", "Wudu rules explained in detail.", false, "Yesterday", "Mufti Abdul Rahman")
            )
        ),
        MessageGroup(
            id = "g3", name = "Tajweed Practice", initial = "TP",
            description = "Practice and improve your Quran recitation with feedback.",
            memberCount = 2100, topic = "Tajweed",
            lastActivity = "1d", lastMessage = "Great recitation, focus on your madd.",
            isJoined = false,
            messages = listOf(
                ChatMessage("g3m1", "Share your recording to get feedback.", false, "1d", "Qari Suleiman Khan"),
                ChatMessage("g3m2", "Great recitation, focus on your madd.", false, "1d", "Qari Usman Malik")
            )
        ),
        MessageGroup(
            id = "g4", name = "Hadith Studies", initial = "HS",
            description = "Read and discuss authentic hadith collections.",
            memberCount = 640, topic = "Hadith",
            lastActivity = "3d", lastMessage = "Ramadan hadith series starting soon.",
            isJoined = false,
            messages = listOf(
                ChatMessage("g4m1", "We start with the 40 Hadith of Imam Nawawi.", false, "3d", "Mufti Hassan Raza")
            )
        ),
        MessageGroup(
            id = "g5", name = "New Muslims Support", initial = "NM",
            description = "A supportive space for new Muslims to ask anything.",
            memberCount = 1520, topic = "General",
            lastActivity = "6h", lastMessage = "Welcome to the group, brother!",
            isJoined = true,
            creatorId = "me", adminIds = listOf("me"), memberIds = listOf("me", "u3", "u6"),
            membersCanSend = true, membersCanAddParticipants = true,
            messages = listOf(
                ChatMessage("g5m1", "No question is too small here.", false, "6h", "Ustadha Fatima Noor"),
                ChatMessage("g5m2", "Welcome to the group, brother!", true, "6h")
            )
        )
    )

    val conversations = listOf(
        Conversation(
            userId = "u1",
            name = "Sara Khan",
            username = "sara_k",
            initial = "S",
            lastMessage = "Hey! Loved your content 🎉",
            time = "2m",
            unread = 2,
            isOnline = true,
            messages = listOf(
                ChatMessage("c1m1", "Hey Alex, great post today!", false, "10:02"),
                ChatMessage("c1m2", "Thanks Sara! 🙌", true, "10:04"),
                ChatMessage("c1m3", "Watched your reel too, so good", false, "10:09"),
                ChatMessage("c1m4", "Hey! Loved your content 🎉", false, "10:12")
            )
        ),
        Conversation(
            userId = "u2",
            name = "Jordan Lee",
            username = "jordan_l",
            initial = "J",
            lastMessage = "Thanks for the follow!",
            time = "1h",
            unread = 0,
            isOnline = false,
            messages = listOf(
                ChatMessage("c2m1", "Hey, followed you!", false, "09:00"),
                ChatMessage("c2m2", "Welcome aboard 👋", true, "09:05"),
                ChatMessage("c2m3", "Thanks for the follow!", false, "09:10")
            )
        ),
        Conversation(
            userId = "u3",
            name = "Maya Chen",
            username = "maya_c",
            initial = "M",
            lastMessage = "Let's collab soon 🔥",
            time = "3h",
            unread = 1,
            isOnline = true,
            messages = listOf(
                ChatMessage("c3m1", "Your designs are inspiring", true, "07:20"),
                ChatMessage("c3m2", "Would love to work together", false, "07:25"),
                ChatMessage("c3m3", "Let's collab soon 🔥", false, "07:30")
            )
        ),
        Conversation(
            userId = "u4",
            name = "Omar Hassan",
            username = "omar_h",
            initial = "O",
            lastMessage = "Great reel man 🔥",
            time = "1d",
            unread = 0,
            isOnline = false,
            messages = listOf(
                ChatMessage("c4m1", "Loved your storytelling", true, "Yesterday"),
                ChatMessage("c4m2", "Appreciate it!", false, "Yesterday"),
                ChatMessage("c4m3", "Great reel man 🔥", false, "Yesterday")
            )
        ),
        Conversation(
            userId = "u5",
            name = "Priya Sharma",
            username = "priya_s",
            initial = "P",
            lastMessage = "See you at the meetup!",
            time = "2d",
            unread = 0,
            isOnline = false,
            messages = listOf(
                ChatMessage("c5m1", "Are you coming on Saturday?", true, "Sun"),
                ChatMessage("c5m2", "Yes, definitely!", false, "Sun"),
                ChatMessage("c5m3", "See you at the meetup!", false, "Sun")
            )
        )
    )
}
