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
 * Which restrictions are on is controlled from the hidden "ניהול חסימות" screen.
 */
class AdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        applyPolicy(context)
    }

    companion object {
        fun component(context: Context) = ComponentName(context, AdminReceiver::class.java)

        private fun dpm(context: Context) =
            context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

        fun isOwner(context: Context): Boolean = dpm(context).isDeviceOwnerApp(context.packageName)

        /** Brings the real restriction in line with the saved choice. False if not device owner. */
        fun applyPolicy(context: Context): Boolean {
            if (!isOwner(context)) return false
            if (Blocks.appsControl(context)) {
                dpm(context).addUserRestriction(component(context), UserManager.DISALLOW_APPS_CONTROL)
            } else {
                dpm(context).clearUserRestriction(component(context), UserManager.DISALLOW_APPS_CONTROL)
            }
            return true
        }

        /** Removes every restriction and gives up device-owner status. */
        fun releasePolicy(context: Context): Boolean {
            if (!isOwner(context)) return false
            dpm(context).clearUserRestriction(component(context), UserManager.DISALLOW_APPS_CONTROL)
            @Suppress("DEPRECATION")
            dpm(context).clearDeviceOwnerApp(context.packageName)
            return true
        }
    }
}
