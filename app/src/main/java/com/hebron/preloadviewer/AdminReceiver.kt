package com.hebron.preloadviewer

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.UserManager

/**
 * Device-owner receiver. Becomes active only via adb, on a device with no
 * accounts set up:
 *
 *   adb shell dpm set-device-owner com.hebron.preloadviewer/.AdminReceiver
 *
 * The single policy applied is DISALLOW_APPS_CONTROL: the user can no longer
 * change apps from Settings (uninstall / disable / force stop / clear data).
 */
class AdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        applyPolicy(context)
    }

    companion object {
        fun component(context: Context) = ComponentName(context, AdminReceiver::class.java)

        fun isOwner(context: Context): Boolean {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            return dpm.isDeviceOwnerApp(context.packageName)
        }

        /** Applies the restriction. Returns false if this app is not the device owner. */
        fun applyPolicy(context: Context): Boolean {
            if (!isOwner(context)) return false
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            dpm.addUserRestriction(component(context), UserManager.DISALLOW_APPS_CONTROL)
            return true
        }

        /** Removes the restriction and gives up device-owner status (so the app can be removed). */
        fun releasePolicy(context: Context): Boolean {
            if (!isOwner(context)) return false
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            dpm.clearUserRestriction(component(context), UserManager.DISALLOW_APPS_CONTROL)
            @Suppress("DEPRECATION")
            dpm.clearDeviceOwnerApp(context.packageName)
            return true
        }
    }
}
