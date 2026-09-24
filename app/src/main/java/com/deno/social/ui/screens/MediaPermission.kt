package com.deno.social.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.deno.social.ui.components.DenoButton
import com.deno.social.ui.theme.DenoTheme

enum class MediaType { PHOTO, VIDEO }

enum class MediaPermissionState {
    GRANTED,
    PARTIAL,
    REQUESTABLE,
    PERMANENTLY_DENIED
}

object MediaPermission {

    private const val PREFS_NAME = "media_permission_state"
    private const val KEY_REQUESTED = "requested_permissions"

    private val requested = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    @Volatile
    private var loadedFromPrefs = false

    private fun ensureLoaded(context: Context) {
        if (loadedFromPrefs) return
        synchronized(this) {
            if (loadedFromPrefs) return
            val prefs = context.applicationContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.getStringSet(KEY_REQUESTED, null)?.let { requested.addAll(it) }
            loadedFromPrefs = true
        }
    }

    fun accessState(context: Context, types: Set<MediaType>): MediaPermissionState {
        ensureLoaded(context)
        val needed = types.toSet()
        if (needed.isEmpty()) return MediaPermissionState.GRANTED

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                context.isGranted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
            ) {
                return MediaPermissionState.PARTIAL
            }

            val required = buildList {
                if (MediaType.PHOTO in needed) add(Manifest.permission.READ_MEDIA_IMAGES)
                if (MediaType.VIDEO in needed) add(Manifest.permission.READ_MEDIA_VIDEO)
            }

            if (required.all { context.isGranted(it) }) return MediaPermissionState.GRANTED

            val ungranted = required.filterNot { context.isGranted(it) }
            val permanentlyDenied = ungranted.isNotEmpty() &&
                ungranted.all { it in requested && !context.shouldShowRationale(it) }
            return if (permanentlyDenied) MediaPermissionState.PERMANENTLY_DENIED else MediaPermissionState.REQUESTABLE
        }

        return if (context.isGranted(Manifest.permission.READ_EXTERNAL_STORAGE)) {
            MediaPermissionState.GRANTED
        } else if (Manifest.permission.READ_EXTERNAL_STORAGE in requested &&
            !context.shouldShowRationale(Manifest.permission.READ_EXTERNAL_STORAGE)
        ) {
            MediaPermissionState.PERMANENTLY_DENIED
        } else {
            MediaPermissionState.REQUESTABLE
        }
    }

    fun hasAccess(context: Context, types: Set<MediaType>): Boolean =
        accessState(context, types) == MediaPermissionState.GRANTED ||
            accessState(context, types) == MediaPermissionState.PARTIAL

    fun permissionRequest(types: Set<MediaType>): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            buildList {
                if (MediaType.PHOTO in types) add(Manifest.permission.READ_MEDIA_IMAGES)
                if (MediaType.VIDEO in types) add(Manifest.permission.READ_MEDIA_VIDEO)
            }.toTypedArray()
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

    fun markRequestStarted(context: Context, permissions: Array<String>) {
        ensureLoaded(context)
        requested.addAll(permissions)
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY_REQUESTED, requested.toSet())
            .apply()
    }

    fun openSettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null)
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
        }
    }

    private fun Context.isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun Context.shouldShowRationale(permission: String): Boolean =
        (this as? Activity)?.shouldShowRequestPermissionRationale(permission) ?: false
}

@Composable
fun MediaPermissionPrompt(
    state: MediaPermissionState,
    explanation: String,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val colors = DenoTheme.colors
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Outlined.PhotoLibrary, null, tint = colors.textSecondary, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(16.dp))
        Text(
            if (state == MediaPermissionState.PERMANENTLY_DENIED) "Permission is off" else "Access your gallery",
            color = colors.textPrimary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp
        )
        Spacer(Modifier.height(8.dp))
        Text(
            explanation,
            color = colors.textSecondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        if (state == MediaPermissionState.PERMANENTLY_DENIED) {
            DenoButton(text = "Open Settings", onClick = onOpenSettings)
        } else {
            DenoButton(text = "Give access", onClick = onRequest)
        }
    }
}