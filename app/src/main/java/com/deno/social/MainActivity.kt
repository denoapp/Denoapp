package com.deno.social

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.deno.social.data.repository.MockMessagingRepository
import com.deno.social.data.repository.SessionManager
import com.deno.social.ui.components.LocalBottomBarInset
import com.deno.social.ui.screens.*
import com.deno.social.ui.theme.DenoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        SessionManager.init(applicationContext)
        setContent {
            var darkMode by remember { mutableStateOf(SessionManager.darkMode.value) }
            DenoTheme(darkTheme = darkMode) {
                DenoApp(darkMode = darkMode, onDarkModeChange = { darkMode = it; SessionManager.setDarkMode(it) })
            }
        }
    }
}

@Composable
fun DenoApp(darkMode: Boolean, onDarkModeChange: (Boolean) -> Unit) {
    val navController = rememberNavController()

    NavHost(navController, startDestination = "login") {
        composable("login") {
            LoginScreen(
                onLogin = { navController.navigate("main") { popUpTo("login") { inclusive = true } } },
                onSignup = { navController.navigate("signup") },
                onForgot = { navController.navigate("forgot") },
                onGoogle = { navController.navigate("google_profile") }
            )
        }
        composable("signup") {
            CompleteProfileFlow(
                isGoogle = false,
                onComplete = { navController.navigate("main") { popUpTo("login") { inclusive = true } } },
                onBack = { navController.popBackStack() }
            )
        }
        composable("forgot") { ForgotPasswordScreen(onBack = { navController.popBackStack() }) }
        composable("google_profile") {
            CompleteProfileFlow(
                isGoogle = true,
                onComplete = { navController.navigate("main") { popUpTo("login") { inclusive = true } } },
                onBack = { navController.popBackStack() }
            )
        }
        composable("main") {
            MainShell(
                darkMode = darkMode,
                onDarkModeChange = onDarkModeChange,
                onLogout = {
                    SessionManager.logout()
                    navController.navigate("login") { popUpTo("main") { inclusive = true } }
                }
            )
        }
    }
}

@Composable
fun MainShell(darkMode: Boolean, onDarkModeChange: (Boolean) -> Unit, onLogout: () -> Unit) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route ?: "home"

    val hideBottom = route in listOf(
        "create_post", "create_reel", "create_story",
        "chat/{userId}", "scholar/{scholarId}", "group_chat/{groupId}", "create_group",
        "select_participants/{ids}", "group_info/{groupId}", "edit_group/{groupId}",
        "group_add_members/{groupId}"
    )

    val colors = DenoTheme.colors

    var bottomNavVisible by remember { mutableStateOf(true) }
    LaunchedEffect(route) { bottomNavVisible = true }

    val onHomeScrollStateChanged: (Boolean) -> Unit = { scrolling ->
        bottomNavVisible = !scrolling
    }

    Scaffold(
        containerColor = colors.background,
        bottomBar = {
            if (!hideBottom) {
                AnimatedVisibility(
                    visible = bottomNavVisible,
                    enter = slideInVertically(animationSpec = tween(260)) { it },
                    exit = slideOutVertically(animationSpec = tween(260)) { it }
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(colors.surface)
                            .navigationBarsPadding()
                    ) {
                        Row(
                            Modifier.fillMaxWidth().height(56.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            BottomNavItem(Icons.Outlined.Home, "Home") {
                                bottomNavVisible = true
                                navController.navigate("home") { popUpTo("home"); launchSingleTop = true }
                            }
                            BottomNavItem(Icons.Outlined.PlayCircle, "Reels") {
                                bottomNavVisible = true
                                navController.navigate("reels") { launchSingleTop = true }
                            }
                            BottomNavItem(Icons.Outlined.Message, "Message") {
                                bottomNavVisible = true
                                navController.navigate("message") { launchSingleTop = true }
                            }
                            BottomNavItem(Icons.Outlined.Notifications, "Notifications") {
                                bottomNavVisible = true
                                navController.navigate("notifications") { launchSingleTop = true }
                            }
                            BottomNavItem(Icons.Outlined.Person, "Profile") {
                                bottomNavVisible = true
                                navController.navigate("profile") { launchSingleTop = true }
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        CompositionLocalProvider(LocalBottomBarInset provides innerPadding.calculateBottomPadding()) {
            NavHost(navController, "home", Modifier.padding(innerPadding)) {
            composable("home") {
                HomeScreen(
                    onSearch = { navController.navigate("advanced_search") },
                    onNotifications = { navController.navigate("notifications") },
                onCreate = { navController.navigate("create_post") },
                onCreateReel = { navController.navigate("create_reel") },
                onCreateStory = { navController.navigate("create_story") },
                    onComments = { navController.navigate("comments") },
                    onProfile = { navController.navigate("user/$it") },
                    onChat = { navController.navigate("message") { launchSingleTop = true } },
                    onHomeScrollStateChanged = onHomeScrollStateChanged
                )
            }
            composable("message") {
                MessagesScreen(
                    onOpenChat = { navController.navigate("chat/$it") },
                    onOpenScholar = { navController.navigate("scholar/$it") },
                    onCreateGroup = { navController.navigate("create_group") },
                    onOpenGroup = { navController.navigate("group_chat/$it") },
                    onApplyForVerification = { navController.navigate("scholar_verification") }
                )
            }
            composable("scholar_verification") {
                ScholarVerificationScreen(onBack = { navController.popBackStack() })
            }
            composable("search") {
                SearchScreen(
                    onBack = { navController.popBackStack() },
                    onUserClick = { navController.navigate("user/$it") }
                )
            }
            composable("advanced_search") {
                AdvancedSearchScreen(
                    onBack = { navController.popBackStack() },
                    onUserClick = { navController.navigate("user/$it") }
                )
            }
            composable("reels") {
                ReelsScreen(
                    onProfile = { navController.navigate("user/$it") },
                    onComments = { navController.navigate("comments") }
                )
            }
            composable("profile") {
                ProfileScreen(
                    onEdit = { navController.navigate("edit_profile") },
                    onSettings = { navController.navigate("settings") },
                    onFollowers = { navController.navigate("followers") },
                    onFollowing = { navController.navigate("following") }
                )
            }
            composable("followers") {
                FollowersScreen(
                    onBack = { navController.popBackStack() },
                    onUserClick = { navController.navigate("user/$it") }
                )
            }
            composable(
                "followers/{profileUserId}",
                arguments = listOf(navArgument("profileUserId") { type = NavType.StringType })
            ) {
                val profileUserId = it.arguments?.getString("profileUserId")
                FollowersScreen(
                    profileUserId = profileUserId,
                    onBack = { navController.popBackStack() },
                    onUserClick = { navController.navigate("user/$it") }
                )
            }
            composable("following") {
                FollowingScreen(
                    onBack = { navController.popBackStack() },
                    onUserClick = { navController.navigate("user/$it") }
                )
            }
            composable(
                "following/{profileUserId}",
                arguments = listOf(navArgument("profileUserId") { type = NavType.StringType })
            ) {
                val profileUserId = it.arguments?.getString("profileUserId")
                FollowingScreen(
                    profileUserId = profileUserId,
                    onBack = { navController.popBackStack() },
                    onUserClick = { navController.navigate("user/$it") }
                )
            }
            composable("create_post") {
                CreatePostScreen(onBack = { navController.popBackStack() }, onPosted = { navController.popBackStack() })
            }
            composable("create_reel") {
                CreateReelScreen(onBack = { navController.popBackStack() }, onPublished = { navController.popBackStack() })
            }
            composable("create_story") {
                CreateStoryScreen(onBack = { navController.popBackStack() }, onShared = { navController.popBackStack() })
            }
            composable("comments") { CommentsScreen(onBack = { navController.popBackStack() }) }
            composable("notifications") {
                NotificationsScreen(
                    onBack = { navController.popBackStack() },
                    onUserClick = { navController.navigate("user/$it") }
                )
            }
            composable("settings") {
                SettingsScreen(
                    darkMode = darkMode,
                    onDarkModeChange = onDarkModeChange,
                    onBack = { navController.popBackStack() },
                    onLogout = onLogout,
                    onAbout = { navController.navigate("about") },
                    onPrivacy = { navController.navigate("privacy") },
                    onTerms = { navController.navigate("terms") },
                    onHelp = { navController.navigate("help") },
                    onEditProfile = { navController.navigate("edit_profile") },
                    onContentPrivacy = { navController.navigate("content_privacy") },
                    onSaved = { navController.navigate("saved") },
                    onChangePassword = { navController.navigate("change_password") },
                    onBlocked = { navController.navigate("blocked") },
                    onDrafts = { navController.navigate("drafts") },
                    onScheduled = { navController.navigate("scheduled") },
                    onAnalytics = { navController.navigate("analytics") },
                    onGroups = { navController.navigate("groups") },
                    onTrending = { navController.navigate("trending") },
                    onInvite = { navController.navigate("invite") },
                    onRecovery = { navController.navigate("recovery") },
                    onModeration = { navController.navigate("moderation") },
                    onLanguage = { navController.navigate("language") }
                )
            }
            composable("edit_profile") {
                EditProfileScreen(onBack = { navController.popBackStack() }, onSaved = { navController.popBackStack() })
            }
            composable("user/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) {
                val userId = it.arguments?.getString("id")
                ProfileScreen(
                    userId = userId,
                    onBack = { navController.popBackStack() },
                    onFollowers = { userId?.let { id -> navController.navigate("followers/$id") } },
                    onFollowing = { userId?.let { id -> navController.navigate("following/$id") } }
                )
            }
            composable("chat/{userId}", arguments = listOf(navArgument("userId") { type = NavType.StringType })) {
                ChatScreen(
                    userId = it.arguments?.getString("userId") ?: "",
                    onBack = { navController.popBackStack() }
                )
            }
            composable("scholar/{scholarId}", arguments = listOf(navArgument("scholarId") { type = NavType.StringType })) {
                ScholarProfileScreen(
                    scholarId = it.arguments?.getString("scholarId") ?: "",
                    onBack = { navController.popBackStack() },
                    onAskMasla = { scholarId -> navController.navigate("chat/$scholarId") }
                )
            }
            composable("group_chat/{groupId}", arguments = listOf(navArgument("groupId") { type = NavType.StringType })) {
                val groupId = it.arguments?.getString("groupId") ?: ""
                GroupChatScreen(
                    groupId = groupId,
                    onBack = { navController.popBackStack() },
                    onGroupInfo = { navController.navigate("group_info/$groupId") }
                )
            }
            composable("group_info/{groupId}", arguments = listOf(navArgument("groupId") { type = NavType.StringType })) {
                val entry = it
                val groupId = entry.arguments?.getString("groupId") ?: ""
                val addedFlow = entry.savedStateHandle.getStateFlow<String>("added_members", "")
                val addedCsv by addedFlow.collectAsState()
                val addedMemberIds = addedCsv.split(",").filter { s -> s.isNotBlank() }
                GroupInfoScreen(
                    groupId = groupId,
                    addedMemberIds = addedMemberIds,
                    onAddedMembersConsumed = { entry.savedStateHandle["added_members"] = "" },
                    onAddMembers = { navController.navigate("group_add_members/$groupId") },
                    onEditGroup = { navController.navigate("edit_group/$groupId") },
                    onBack = { navController.popBackStack() },
                    onGroupDeleted = {
                        navController.popBackStack("group_chat/{groupId}", inclusive = true)
                    },
                    onLeftGroup = {
                        navController.popBackStack("group_chat/{groupId}", inclusive = true)
                    }
                )
            }
            composable("edit_group/{groupId}", arguments = listOf(navArgument("groupId") { type = NavType.StringType })) {
                EditGroupScreen(
                    groupId = it.arguments?.getString("groupId") ?: "",
                    onBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() }
                )
            }
            composable(
                "group_add_members/{groupId}",
                arguments = listOf(navArgument("groupId") { type = NavType.StringType })
            ) {
                val prevEntry = navController.previousBackStackEntry
                val groupId = it.arguments?.getString("groupId") ?: ""
                val repo = remember { MockMessagingRepository() }
                val existingMembers = remember(groupId) {
                    repo.getGroup(groupId)?.memberIds.orEmpty().toSet()
                }
                val incomingFlow = prevEntry
                    ?.savedStateHandle
                    ?.getStateFlow<String>("added_members", "")
                val incomingState: State<String> = incomingFlow?.let { flow -> flow.collectAsState() }
                    ?: remember { mutableStateOf("") }
                val selectedCsv by incomingState
                val selectedIds = selectedCsv.split(",").filter { s -> s.isNotBlank() }
                SelectParticipantsScreen(
                    selectedIds = selectedIds,
                    onSelectionChanged = { ids ->
                        prevEntry?.savedStateHandle?.set("added_members", ids.joinToString(","))
                    },
                    onBack = { navController.popBackStack() },
                    hiddenIds = existingMembers
                )
            }
            composable("create_group") {
                val backEntry = it
                val selectedFlow = backEntry.savedStateHandle.getStateFlow<String>("selected_participants", "")
                val selectedCsv by selectedFlow.collectAsState()
                val selectedMemberIds = selectedCsv.split(",").filter { s -> s.isNotBlank() }
                CreateGroupScreen(
                    selectedMemberIds = selectedMemberIds,
                    onSelectParticipants = {
                        val arg = if (selectedMemberIds.isEmpty()) "none" else Uri.encode(selectedMemberIds.joinToString(","))
                        navController.navigate("select_participants/$arg")
                    },
                    onBack = { navController.popBackStack() },
                    onCreated = { groupId ->
                        navController.navigate("group_chat/$groupId") {
                            popUpTo("create_group") { inclusive = true }
                        }
                    }
                )
            }
            composable(
                "select_participants/{ids}",
                arguments = listOf(navArgument("ids") { type = NavType.StringType })
            ) {
                val backEntry = it
                val prevEntry = navController.previousBackStackEntry
                val raw = backEntry.arguments?.getString("ids") ?: ""
                val initialCsv = remember(raw) {
                    val decoded = runCatching { Uri.decode(raw) }.getOrDefault(raw)
                    if (decoded.isBlank() || decoded == "none") "" else decoded
                }
                val selectionFlow = prevEntry
                    ?.savedStateHandle
                    ?.getStateFlow<String>("selected_participants", initialCsv)
                val selectionState: State<String> = selectionFlow?.let { flow -> flow.collectAsState() }
                    ?: remember(initialCsv) { mutableStateOf(initialCsv) }
                val selectedCsv by selectionState
                val selectedIds = selectedCsv.split(",").filter { s -> s.isNotBlank() }
                SelectParticipantsScreen(
                    selectedIds = selectedIds,
                    onSelectionChanged = { ids ->
                        prevEntry?.savedStateHandle?.set("selected_participants", ids.joinToString(","))
                    },
                    onBack = { navController.popBackStack() }
                )
            }
            composable("saved") { SavedScreen(onBack = { navController.popBackStack() }) }
            composable("content_privacy") { ContentPrivacyScreen(onBack = { navController.popBackStack() }) }
            composable("change_password") { ChangePasswordScreen(onBack = { navController.popBackStack() }) }
            composable("blocked") { BlockedUsersScreen(onBack = { navController.popBackStack() }) }
            composable("drafts") { DraftsScreen(onBack = { navController.popBackStack() }) }
            composable("scheduled") { ScheduledPostsScreen(onBack = { navController.popBackStack() }) }
            composable("analytics") { AnalyticsScreen(onBack = { navController.popBackStack() }) }
            composable("groups") { GroupsScreen(onBack = { navController.popBackStack() }) }
            composable("trending") { TrendingScreen(onBack = { navController.popBackStack() }) }
            composable("invite") { InviteFriendsScreen(onBack = { navController.popBackStack() }) }
            composable("recovery") { AccountRecoveryScreen(onBack = { navController.popBackStack() }) }
            composable("moderation") { ModerationScreen(onBack = { navController.popBackStack() }) }
            composable("language") { LanguageScreen(onBack = { navController.popBackStack() }) }
            composable("about") {
                SimpleInfoScreen("About DENO", "DENO — Learn · Share · Inspire\n\nFull frontend feature set.\n\nBackend NOT connected.", { navController.popBackStack() })
            }
            composable("privacy") {
                SimpleInfoScreen("Privacy Policy", "Local mock frontend only.", { navController.popBackStack() })
            }
            composable("terms") {
                SimpleInfoScreen("Terms of Service", "Demo frontend. Mock data only.", { navController.popBackStack() })
            }
            composable("help") {
                SimpleInfoScreen("Help & Support", "All major UI screens included.\nBackend later.", { navController.popBackStack() })
            }
            }
        }
    }
}

@Composable
fun RowScope.BottomNavItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, label, tint = Color.Black, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(2.dp))
        Text(label, color = Color.Black, fontSize = 12.sp)
    }
}