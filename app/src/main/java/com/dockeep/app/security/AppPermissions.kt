package com.dockeep.app.security

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

object AppPermissions {
    // Camera and file access go through system apps and pickers, so the only runtime permission
    // DocKeep asks for is notifications (Android 13+) for expiry reminders.
    fun required(): Array<String> =
        if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.POST_NOTIFICATIONS) else emptyArray()

    fun missing(context: Context): Array<String> =
        required().filter { permission ->
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()
}
