package com.deno.social.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.deno.social.R
import com.deno.social.data.mock.CountryData
import com.deno.social.data.model.IslamicProfile
import com.deno.social.data.repository.SessionManager
import com.deno.social.ui.components.DenoButton
import com.deno.social.ui.components.DenoTextField
import com.deno.social.ui.theme.DenoBlue
import com.deno.social.ui.theme.DenoTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
private fun AuthScreenLayout(
    scrollState: ScrollState,
    topBar: (@Composable () -> Unit)? = null,
    shiftUp: Dp = 0.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = DenoTheme.colors
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        val density = LocalDensity.current
        val imeVisible = WindowInsets.ime.getBottom(density) > 0
        LaunchedEffect(imeVisible) {
            if (!imeVisible) scrollState.scrollTo(0)
        }
        val compact = maxHeight < 700.dp
        val gap = if (compact) 10.dp else 12.dp
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .offset(y = -shiftUp)
                .verticalScroll(scrollState)
                .padding(horizontal = 28.dp, vertical = if (compact) 12.dp else 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(gap)
        ) {
            content()
        }
        if (topBar != null) {
            Box(Modifier.align(Alignment.TopStart)) {
                topBar()
            }
        }
    }
}

@Composable
fun LoginScreen(
    onLogin: () -> Unit,
    onSignup: () -> Unit,
    onForgot: () -> Unit,
    onGoogle: () -> Unit
) {
    val colors = DenoTheme.colors
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()

    AuthScreenLayout(scrollState) {
        Image(
            painter = painterResource(id = R.drawable.login_logo),
            contentDescription = "Logo",
            modifier = Modifier.width(170.dp),
            contentScale = ContentScale.Fit
        )
        Text("Learn · Share · Inspire", color = colors.textSecondary, fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))

        DenoTextField(email, { email = it; error = "" }, "Email or Username")
        DenoTextField(password, { password = it; error = "" }, "Password", isPassword = true, showPasswordToggle = true)
        if (error.isNotEmpty()) {
            Text(error, color = Color.Red, fontSize = 13.sp)
        }
        Box(modifier = Modifier.fillMaxWidth()) {
            Text(
                "Forgot Password?",
                color = DenoBlue,
                fontSize = 13.sp,
                modifier = Modifier.align(Alignment.CenterEnd).clickable { onForgot() }
            )
        }
        DenoButton("Log In", {
            if (email.isBlank() || password.isBlank()) error = "Please fill all fields"
            else {
                SessionManager.setPassword(password)
                SessionManager.login(); onLogin()
            }
        }, Modifier.fillMaxWidth())

        Row(verticalAlignment = Alignment.CenterVertically) {
            Divider(Modifier.weight(1f), color = colors.border)
            Text("  or  ", color = colors.textSecondary, fontSize = 13.sp)
            Divider(Modifier.weight(1f), color = colors.border)
        }

        // Google button - Multicolor Google G Icon
        OutlinedButton(
            onClick = onGoogle,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, colors.border),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.textPrimary)
        ) {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_google_g),
                    contentDescription = "Google",
                    modifier = Modifier.size(22.dp),
                    tint = Color.Unspecified
                )
                Spacer(Modifier.width(12.dp))
                Text("Continue with Google", color = colors.textPrimary, fontWeight = FontWeight.Medium, fontSize = 15.sp)
            }
        }

        Row {
            Text("Don't have an account? ", color = colors.textSecondary, fontSize = 14.sp)
            Text("Sign Up", color = DenoBlue, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                modifier = Modifier.clickable { onSignup() })
        }
    }
}

@Composable
fun SignupScreen(onSignupSuccess: () -> Unit, onBack: () -> Unit) {
    val colors = DenoTheme.colors
    var fullName by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()

    AuthScreenLayout(scrollState) {
        IconButton(onClick = onBack) {
            Icon(Icons.Default.ArrowBack, null, tint = colors.textPrimary)
        }
        Text("Create Account", color = colors.textPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))

        DenoTextField(fullName, { fullName = it }, "Full Name")
        DenoTextField(username, { username = it }, "Username")
        DenoTextField(email, { email = it }, "Email")
        DenoTextField(password, { password = it }, "Password", isPassword = true, showPasswordToggle = true)
        DenoTextField(confirm, { confirm = it }, "Confirm Password", isPassword = true, showPasswordToggle = true)
        if (error.isNotEmpty()) {
            Text(error, color = Color.Red, fontSize = 13.sp)
        }
        DenoButton("Create Account", {
            when {
                fullName.isBlank() || username.isBlank() || email.isBlank() || password.isBlank() ->
                    error = "Please fill all fields"
                password != confirm -> error = "Passwords do not match"
                password.length < 6 -> error = "Password must be at least 6 characters"
                else -> {
                    SessionManager.setPassword(password)
                    SessionManager.login(); onSignupSuccess()
                }
            }
        }, Modifier.fillMaxWidth())
    }
}

@Composable
fun ForgotPasswordScreen(onBack: () -> Unit) {
    val colors = DenoTheme.colors
    var email by remember { mutableStateOf("") }
    var sent by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(scrollState)
            .padding(horizontal = 28.dp, vertical = 16.dp)
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.Default.ArrowBack, null, tint = colors.textPrimary)
        }
        Spacer(Modifier.height(8.dp))
        Text("Forgot Password", color = colors.textPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Enter your email and we'll send a reset link.", color = colors.textSecondary, fontSize = 14.sp)
        Spacer(Modifier.height(28.dp))
        DenoTextField(email, { email = it }, "Email")
        Spacer(Modifier.height(24.dp))
        DenoButton("Send Reset Link", {
            if (email.isNotBlank()) sent = true
        }, Modifier.fillMaxWidth())
        if (sent) {
            Spacer(Modifier.height(20.dp))
            Text("Reset link sent (mock). Check your inbox.", color = DenoBlue, fontSize = 14.sp)
        }
    }
}

// ---------------------------------------------------------------------------
// Multi-step Complete Profile flow
// ---------------------------------------------------------------------------

// Mutable draft shared across all three steps so entered values are preserved
// while moving between screens. Inherits mutableStateOf for reactive updates.
class ProfileDraft {
    var fullName by mutableStateOf("")
    var username by mutableStateOf("")
    var password by mutableStateOf("")
    var confirmPassword by mutableStateOf("")

    var country by mutableStateOf("")
    var region by mutableStateOf("")
    var city by mutableStateOf("")
    var language by mutableStateOf("English")
    var maslak by mutableStateOf("")

    var bio by mutableStateOf("")
    var photoUriString by mutableStateOf("")
    var showMaslak by mutableStateOf(true)
    var showRegion by mutableStateOf(true)
}

private val ProfileDraftSaver = listSaver<ProfileDraft, Any?>(
    save = { d ->
        listOf(
            d.fullName, d.username, d.password, d.confirmPassword,
            d.country, d.region, d.city, d.language, d.maslak,
            d.bio, d.photoUriString, d.showMaslak, d.showRegion
        )
    },
    restore = { list ->
        ProfileDraft().apply {
            fullName = list.getOrNull(0) as? String ?: ""
            username = list.getOrNull(1) as? String ?: ""
            password = list.getOrNull(2) as? String ?: ""
            confirmPassword = list.getOrNull(3) as? String ?: ""
            country = list.getOrNull(4) as? String ?: ""
            region = list.getOrNull(5) as? String ?: ""
            city = list.getOrNull(6) as? String ?: ""
            language = list.getOrNull(7) as? String ?: ""
            maslak = list.getOrNull(8) as? String ?: ""
            bio = list.getOrNull(9) as? String ?: ""
            photoUriString = list.getOrNull(10) as? String ?: ""
            showMaslak = list.getOrNull(11) as? Boolean ?: true
            showRegion = list.getOrNull(12) as? Boolean ?: true
        }
    }
)

@Composable
fun CompleteProfileFlow(
    isGoogle: Boolean,
    onComplete: () -> Unit,
    onBack: () -> Unit
) {
    var step by rememberSaveable { mutableStateOf(0) }
    val draft = rememberSaveable(saver = ProfileDraftSaver) { ProfileDraft() }

    BackHandler(enabled = step > 0) { step -= 1 }

    when (step) {
        0 -> BasicProfileStep(
            isGoogle = isGoogle,
            draft = draft,
            onBack = onBack,
            onNext = { step = 1 }
        )
        1 -> IslamicProfileStep(
            draft = draft,
            onBack = { step = 0 },
            onNext = { step = 2 }
        )
        else -> FinalProfileStep(
            draft = draft,
            onBack = { step = 1 },
            onComplete = {
                SessionManager.saveProfileSetup(
                    IslamicProfile(
                        country = draft.country,
                        region = draft.region,
                        city = draft.city,
                        language = draft.language,
                        maslak = draft.maslak,
                        bio = draft.bio,
                        showMaslak = draft.showMaslak,
                        showRegion = draft.showRegion
                    )
                )
                // Email/password signup stores the chosen password so the
                // Settings > Change Password screen can validate "Current".
                if (draft.password.isNotBlank()) SessionManager.setPassword(draft.password)
                SessionManager.login()
                onComplete()
            }
        )
    }
}

@Composable
private fun ProfileBackArrow(onBack: () -> Unit) {
    val colors = DenoTheme.colors
    IconButton(
        onClick = onBack,
        modifier = Modifier.padding(start = 20.dp)
    ) {
        Icon(Icons.Default.ArrowBack, "Back", tint = colors.textPrimary)
    }
}

@Composable
private fun ProfileHeader(title: String, subtitle: String) {
    val colors = DenoTheme.colors
    Spacer(Modifier.height(48.dp))
    Text(title, color = colors.textPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold)
    Text(subtitle, color = colors.textSecondary, fontSize = 14.sp)
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun ProfileError(error: String) {
    if (error.isNotEmpty()) {
        Text(error, color = Color.Red, fontSize = 13.sp)
    }
}

// PAGE 1 — Basic Profile
@Composable
private fun BasicProfileStep(
    isGoogle: Boolean,
    draft: ProfileDraft,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    var error by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()

    AuthScreenLayout(scrollState, topBar = { ProfileBackArrow(onBack) }) {
        ProfileHeader(
            "Complete Profile",
            "Finish setting up your DENO account."
        )

        DenoTextField(draft.fullName, { draft.fullName = it; error = "" }, "Full Name", required = true)
        DenoTextField(draft.username, { draft.username = it; error = "" }, "Username", required = true)
        if (!isGoogle) {
            DenoTextField(draft.password, { draft.password = it; error = "" }, "Password", isPassword = true, showPasswordToggle = true, required = true)
            DenoTextField(draft.confirmPassword, { draft.confirmPassword = it; error = "" }, "Confirm Password", isPassword = true, showPasswordToggle = true, required = true)
        }
        ProfileError(error)
        DenoButton("Next", {
            when {
                draft.fullName.isBlank() -> error = "Please enter your full name"
                draft.username.isBlank() -> error = "Please choose a username"
                !isGoogle && draft.password.isBlank() -> error = "Please enter a password"
                !isGoogle && draft.password != draft.confirmPassword -> error = "Passwords do not match"
                !isGoogle && draft.password.length < 6 -> error = "Password must be at least 6 characters"
                else -> onNext()
            }
        }, Modifier.fillMaxWidth())
    }
}

// --- Clean compact popup selector -------------------------------------------
// Opens a small rounded popup (not a full-screen page) with search + A-Z
// section headers. Outside tap / back closes it; selecting closes it and
// writes the value back into the read-only field.

private fun pickerSortKey(s: String): String =
    java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase(java.util.Locale.ROOT)

// Filters by the active query. If `alphabetical` is true the remaining options
// are sorted A-Z (accent-insensitive); otherwise the original fixed order is kept.
private fun buildPickerEntries(
    options: List<String>,
    query: String,
    alphabetical: Boolean
): List<String> {
    val q = pickerSortKey(query.trim())
    return options
        .distinct()
        .filter { q.isBlank() || pickerSortKey(it).contains(q) }
        .let { list ->
            if (alphabetical) list.sortedWith(compareBy { pickerSortKey(it) }) else list
        }
}

@Composable
fun DenoPickerDialog(
    title: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    alphabetical: Boolean,
    showSearch: Boolean
) {
    // Final clean design: always a white surface with dark text.
    val popupBg = Color.White
    val textColor = Color(0xFF111111)
    val secondaryColor = Color(0xFF8A8A8E)
    val searchBg = Color(0xFFF2F2F4)
    val dividerColor = Color(0xFFECECEE)

    var query by remember(title) { mutableStateOf("") }
    val entries = remember(options, query) {
        buildPickerEntries(options, query, alphabetical)
    }
    val focusRequester = remember { FocusRequester() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false // handled by our own scrim below
        )
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
            contentAlignment = Alignment.Center
        ) {
            // Tall, slightly narrow card: ~78% of the screen height, ~89% width.
            val maxCardHeight = maxHeight * 0.78f
            val listMaxHeight = (maxCardHeight - (if (showSearch) 150.dp else 96.dp)).coerceAtLeast(120.dp)

            // Scrim: tap outside the card closes the popup.
            Box(
                Modifier
                    .matchParentSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss
                    )
            )

            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.89f)
                    .widthIn(max = 460.dp)
                    .heightIn(max = maxCardHeight)
                    // Swallow taps inside the card so they never reach the scrim.
                    .pointerInput(Unit) { detectTapGestures { } },
                shape = RoundedCornerShape(22.dp),
                color = popupBg,
                shadowElevation = 10.dp,
                tonalElevation = 0.dp
            ) {
                Column(Modifier.background(popupBg).clip(RoundedCornerShape(22.dp))) {
                    // Header: "Please Select" with a thin divider below it.
                    Text(
                        text = "Please Select",
                        color = textColor,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 12.dp)
                    )
                    Divider(color = dividerColor, thickness = 1.dp)

                    if (showSearch) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                                .height(42.dp)
                                .clip(RoundedCornerShape(21.dp))
                                .background(searchBg)
                                .padding(horizontal = 14.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Search,
                                    null,
                                    tint = secondaryColor,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                BasicTextField(
                                    value = query,
                                    onValueChange = { query = it },
                                    singleLine = true,
                                    textStyle = TextStyle(
                                        color = textColor,
                                        fontSize = 15.sp
                                    ),
                                    cursorBrush = SolidColor(DenoBlue),
                                    modifier = Modifier
                                        .weight(1f)
                                        .focusRequester(focusRequester),
                                    decorationBox = { innerTextField ->
                                        if (query.isEmpty()) {
                                            Text(
                                                "Search",
                                                color = secondaryColor,
                                                fontSize = 15.sp
                                            )
                                        }
                                        innerTextField()
                                    }
                                )
                            }
                        }
                        LaunchedEffect(Unit) { focusRequester.requestFocus() }
                    }

                    if (entries.isEmpty()) {
                        Text(
                            "No results found",
                            color = secondaryColor,
                            fontSize = 15.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 28.dp)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = listMaxHeight)
                        ) {
                            entries.forEachIndexed { index, entry ->
                                item(key = entry) {
                                    val isSelected = entry == selected
                                    Column {
                                        // Row: 54dp tall, 22dp left padding, thin divider, right check.
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(54.dp)
                                                .clickable {
                                                    onSelect(entry)
                                                    onDismiss()
                                                }
                                                .padding(start = 22.dp, end = 18.dp)
                                        ) {
                                            Text(
                                                text = entry.uppercase(),
                                                color = textColor,
                                                fontSize = 17.sp,
                                                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                                                maxLines = 1,
                                                modifier = Modifier.weight(1f)
                                            )
                                            if (isSelected) {
                                                Icon(
                                                    Icons.Default.Check,
                                                    "Selected",
                                                    tint = DenoBlue,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                            }
                                        }
                                        if (index < entries.lastIndex) {
                                            Divider(
                                                color = dividerColor,
                                                thickness = 0.5.dp,
                                                modifier = Modifier.padding(start = 22.dp, end = 18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Field styled like DenoTextField; tapping it opens the clean popup selector.
@Composable
fun DenoPickerField(
    value: String,
    label: String,
    options: List<String>,
    alphabetical: Boolean = true,
    showSearch: Boolean = true,
    required: Boolean = false,
    onSelect: (String) -> Unit
) {
    val colors = DenoTheme.colors
    var showPicker by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value.uppercase(),
            onValueChange = {},
            readOnly = true,
            label = {
                if (required) {
                    Text(
                        buildAnnotatedString {
                            append(label)
                            append(" *")
                        }
                    )
                } else {
                    Text(label)
                }
            },
            singleLine = true,
            trailingIcon = {
                Icon(
                    Icons.Default.ExpandMore,
                    null,
                    tint = colors.textSecondary,
                    modifier = Modifier.size(22.dp)
                )
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = DenoBlue,
                unfocusedBorderColor = colors.border,
                focusedTextColor = colors.textPrimary,
                unfocusedTextColor = colors.textPrimary,
                focusedLabelColor = DenoBlue,
                unfocusedLabelColor = colors.textSecondary,
                cursorColor = DenoBlue,
                focusedContainerColor = colors.surface,
                unfocusedContainerColor = colors.surface
            )
        )
        Box(
            Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(12.dp))
                .clickable { showPicker = true }
        )
    }

    if (showPicker) {
        DenoPickerDialog(
            title = label,
            options = options,
            selected = value,
            onSelect = onSelect,
            onDismiss = { showPicker = false },
            alphabetical = alphabetical,
            showSearch = showSearch
        )
    }
}

// PAGE 2 — Islamic Profile
val COUNTRIES = CountryData.COUNTRIES

val LANGUAGES = listOf(
    "Afrikaans", "Albanian", "Amharic", "Arabic", "Azerbaijani", "Bengali",
    "Bosnian", "Bulgarian", "Burmese", "Cantonese", "Chinese", "Croatian",
    "Czech", "Danish", "Dutch", "English", "Filipino", "Finnish", "French",
    "Georgian", "German", "Greek", "Gujarati", "Hausa", "Hebrew", "Hindi",
    "Hungarian", "Icelandic", "Indonesian", "Italian", "Japanese", "Javanese",
    "Kazakh", "Korean", "Kurdish", "Kyrgyz", "Malay", "Malayalam", "Maltese",
    "Mandarin", "Marathi", "Mongolian", "Nepali", "Norwegian", "Pashto",
    "Persian (Farsi)", "Polish", "Portuguese", "Punjabi", "Romanian", "Russian",
    "Serbian", "Sinhala", "Slovak", "Slovenian", "Somali", "Spanish", "Swahili",
    "Swedish", "Tagalog", "Tajik", "Tamil", "Telugu", "Thai", "Tibetan",
    "Turkish", "Turkmen", "Ukrainian", "Urdu", "Uzbek", "Vietnamese", "Welsh",
    "Yiddish", "Yoruba", "Zulu", "Other"
)

val MASLAKS = listOf("Hanafi", "Shafi'i", "Maliki", "Hanbali", "Ja'fari", "Other", "Prefer not to say")

@Composable
private fun IslamicProfileStep(
    draft: ProfileDraft,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    var error by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()

    AuthScreenLayout(scrollState, topBar = { ProfileBackArrow(onBack) }) {
        ProfileHeader("Islamic Profile", "Tell us a little about yourself.")

        DenoPickerField(draft.country, "Country", COUNTRIES, required = true) {
            draft.country = it
            draft.region = ""
            error = ""
        }
        DenoPickerField(
            draft.region,
            "Region / State",
            CountryData.regionsFor(draft.country),
            required = true
        ) { draft.region = it; error = "" }
        DenoTextField(draft.city, { draft.city = it; error = "" }, "City", required = true)
        DenoPickerField(draft.language, "Language", LANGUAGES, required = true) { draft.language = it; error = "" }
        DenoPickerField(
            draft.maslak,
            "Maslak / Fiqh (optional)",
            MASLAKS,
            alphabetical = false,
            showSearch = false,
            onSelect = { draft.maslak = it; error = "" }
        )
        Text(
            "Verification badges are assigned by admin in the future.",
            color = DenoTheme.colors.textSecondary,
            fontSize = 12.sp
        )
        ProfileError(error)
        DenoButton("Next", {
            when {
                draft.country.isBlank() -> error = "Please select your country"
                draft.region.isBlank() -> error = "Please select your region"
                draft.city.isBlank() -> error = "Please enter your city"
                draft.language.isBlank() -> error = "Please select a language"
                else -> onNext()
            }
        }, Modifier.fillMaxWidth())
    }
}

// PAGE 3 — Final Complete Profile
private fun scaleDownBitmap(source: Bitmap, maxSize: Int): Bitmap {
    val width = source.width
    val height = source.height
    val ratio = maxSize.toFloat() / maxOf(width, height)
    val newW = (width * ratio).toInt().coerceAtLeast(1)
    val newH = (height * ratio).toInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(source, newW, newH, true)
}

@Composable
private fun FinalProfileStep(
    draft: ProfileDraft,
    onBack: () -> Unit,
    onComplete: () -> Unit
) {
    val colors = DenoTheme.colors
    val scrollState = rememberScrollState()
    val context = LocalContext.current

    // Reuses the exact CircularCropEditor from Edit Profile. Picking a photo
    // opens the same circular editor here; Done writes the cropped PNG and
    // saves its real local path into SessionManager (single source of truth),
    // so Profile and Edit Profile show the same saved photo too.
    var cropSource by remember { mutableStateOf<Bitmap?>(null) }
    val savedPhotoPath by SessionManager.profile.collectAsState()

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) cropSource = decodeSampledBitmap(context, uri, 2048)
    }

    fun openPhotoPicker() {
        photoPicker.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    if (cropSource != null) {
        cropSource?.let { source ->
            CircularCropEditor(
                source = source,
                onDone = { cropped ->
                    SessionManager.setProfilePhoto(writeProfilePhotoFile(context, cropped))
                    cropSource = null
                },
                onCancel = { cropSource = null }
            )
        }
    } else {
        AuthScreenLayout(scrollState, topBar = { ProfileBackArrow(onBack) }, shiftUp = 75.dp) {
            ProfileHeader("Complete Profile", "Add the final details to your profile.")

            CompleteProfilePhotoAvatar(
                photoPath = savedPhotoPath.profilePhotoPath,
                onClick = ::openPhotoPicker
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (savedPhotoPath.profilePhotoPath.isBlank()) "Add Photo" else "Change Photo",
                color = DenoBlue,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { openPhotoPicker() }
            )

            DenoTextField(draft.bio, { draft.bio = it }, "Bio", singleLine = false)

        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Show Maslak on profile", color = colors.textPrimary, fontSize = 14.sp)
                Text(
                    "Display your Islamic school of thought.",
                    color = colors.textSecondary,
                    fontSize = 12.sp
                )
            }
            Switch(
                checked = draft.showMaslak,
                onCheckedChange = { draft.showMaslak = it },
                colors = SwitchDefaults.colors(checkedTrackColor = DenoBlue)
            )
        }
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Show Region & Country on profile", color = colors.textPrimary, fontSize = 14.sp)
                Text(
                    "Visibility control, ready for the future.",
                    color = colors.textSecondary,
                    fontSize = 12.sp
                )
            }
            Switch(
                checked = draft.showRegion,
                onCheckedChange = { draft.showRegion = it },
                colors = SwitchDefaults.colors(checkedTrackColor = DenoBlue)
            )
        }

        DenoButton("Complete Profile", onComplete, Modifier.fillMaxWidth())
        }
    }
}

// Circular avatar for Complete Profile that immediately shows the freshly
// cropped photo from SessionManager (single source of truth). Shows the Add
// Photo placeholder until a photo exists.
@Composable
private fun CompleteProfilePhotoAvatar(photoPath: String, onClick: () -> Unit) {
    val colors = DenoTheme.colors
    val imageBitmap by produceState<ImageBitmap?>(initialValue = null, photoPath) {
        value = if (photoPath.isBlank()) null else runCatching {
            withContext(Dispatchers.IO) {
                BitmapFactory.decodeFile(photoPath, BitmapFactory.Options().apply { inSampleSize = 2 })
                    ?.asImageBitmap()
            }
        }.getOrNull()
    }
    Box(
        Modifier
            .size(100.dp)
            .clip(CircleShape)
            .background(if (colors.isDark) colors.card else Color(0xFFE3F2FD))
            .border(2.dp, DenoBlue, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (imageBitmap != null) {
            Image(
                bitmap = imageBitmap!!,
                contentDescription = "Profile photo",
                modifier = Modifier.fillMaxSize().clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            Icon(Icons.Outlined.AddAPhoto, null, tint = DenoBlue, modifier = Modifier.size(36.dp))
        }
    }
}