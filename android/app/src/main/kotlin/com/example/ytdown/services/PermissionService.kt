package com.example.ytdown.services

import android.app.Activity
import android.content.Context
import com.example.ytdown.services.PermissionHelper
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PermissionService @Inject constructor(
    private val helper: PermissionHelper
) {
    fun hasStoragePermission(context: Context): Boolean = helper.hasStoragePermission(context)

    fun hasNotificationPermission(context: Context): Boolean = helper.hasNotificationPermission(context)

    fun requestPermissions(activity: Activity) = helper.requestPermissions(activity)

    fun openAppSettings(context: Context) = helper.openAppSettings(context)
}
