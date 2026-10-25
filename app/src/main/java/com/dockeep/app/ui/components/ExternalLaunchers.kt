package com.dockeep.app.ui.components

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.dockeep.app.data.storage.LocalFileStorage
import com.dockeep.app.security.AppLockController
import com.dockeep.app.ui.LocalAppContainer
import java.io.File

/**
 * Starts an activity outside DocKeep (camera, picker, share sheet, system settings) without
 * crashing when nothing on the phone can handle it, and without the app lock kicking in while
 * the user is away for it.
 */
fun AppLockController.launchExternal(
    onError: (String) -> Unit,
    notFoundMessage: String = "No app on this phone can handle this.",
    launch: () -> Unit
) {
    expectExternalActivity()
    try {
        launch()
    } catch (_: ActivityNotFoundException) {
        cancelExternalActivity()
        onError(notFoundMessage)
    } catch (_: SecurityException) {
        cancelExternalActivity()
        onError("Android blocked this action. Check DocKeep’s permissions in system settings.")
    } catch (_: IllegalStateException) {
        cancelExternalActivity()
        onError("Couldn’t open that right now. Try again.")
    }
}

/**
 * Opens the camera app and hands back the captured photo. The pending file path survives the
 * activity being recreated (or DocKeep being killed while the camera is open), so the photo
 * isn't lost on low-memory phones.
 */
@Composable
fun rememberCameraCapture(
    storage: LocalFileStorage,
    onCaptured: (File) -> Unit,
    onError: (String) -> Unit
): () -> Unit {
    val context = LocalContext.current
    val lock = LocalAppContainer.current.appLockController
    val currentOnCaptured by rememberUpdatedState(onCaptured)
    val currentOnError by rememberUpdatedState(onError)
    var pendingPath by rememberSaveable { mutableStateOf<String?>(null) }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { _ ->
        val file = pendingPath?.let(::File)
        pendingPath = null
        // Some camera apps report "cancelled" even after saving, so trust the file instead.
        if (file != null && file.exists() && file.length() > 0L) {
            currentOnCaptured(file)
        } else {
            file?.delete()
        }
    }

    fun openCamera() {
        val file = runCatching { storage.createCaptureFile() }.getOrElse {
            currentOnError("Not enough free space to take a photo.")
            return
        }
        val uri = runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }.getOrElse {
            file.delete()
            currentOnError("Couldn’t prepare the camera. Try again.")
            return
        }
        pendingPath = file.absolutePath
        lock.launchExternal(
            onError = {
                pendingPath = null
                file.delete()
                currentOnError(it)
            },
            notFoundMessage = "No camera app was found on this phone."
        ) {
            takePicture.launch(uri)
        }
    }

    val requestCamera = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            openCamera()
        } else {
            currentOnError("Allow camera access in DocKeep’s settings to take photos.")
        }
    }

    return {
        if (needsCameraPermission(context)) {
            // The permission dialog doesn't send DocKeep to the background, so no lock grace needed.
            requestCamera.launch(Manifest.permission.CAMERA)
        } else {
            openCamera()
        }
    }
}

/**
 * The camera intent needs no permission unless the app declares CAMERA in its manifest; in that
 * case Android throws a SecurityException until the user grants it. DocKeep no longer declares
 * it, but a library could merge it back in, so check what the installed app actually requests.
 */
private fun needsCameraPermission(context: Context): Boolean {
    val declared = runCatching {
        val info = if (Build.VERSION.SDK_INT >= 33) {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        }
        info.requestedPermissions?.contains(Manifest.permission.CAMERA) == true
    }.getOrDefault(true)
    return declared &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
}
