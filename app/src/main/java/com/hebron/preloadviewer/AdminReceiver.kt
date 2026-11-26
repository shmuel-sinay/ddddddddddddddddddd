package com.hebron.preloadviewer

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Build
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

        /** Brings the real restrictions in line with the saved choice. False if not device owner. */
        fun applyPolicy(context: Context): Boolean {
            if (!isOwner(context)) return false
            val d = dpm(context)
            val admin = component(context)
            if (Blocks.appsControl(context)) {
                // No changing apps in Settings (disable, force stop, clear data/cache)...
                d.addUserRestriction(admin, UserManager.DISALLOW_APPS_CONTROL)
                // ...and no uninstalling.
                d.addUserRestriction(admin, UserManager.DISALLOW_UNINSTALL_APPS)
                // Block uninstall explicitly for every installed app, one by one
                // (covers the file manager and anything else installed from an APK).
                for (pkg in userPackages(context)) setUninstallBlocked(d, admin, pkg, true)
                // Android 11+: explicitly block force-stop / clear data for every user app.
                if (Build.VERSION.SDK_INT >= 30) {
                    try {
                        d.setUserControlDisabledPackages(admin, userPackages(context))
                    } catch (e: Exception) {
                    }
                }
            } else {
                d.clearUserRestriction(admin, UserManager.DISALLOW_APPS_CONTROL)
                d.clearUserRestriction(admin, UserManager.DISALLOW_UNINSTALL_APPS)
                for (pkg in userPackages(context)) setUninstallBlocked(d, admin, pkg, false)
                if (Build.VERSION.SDK_INT >= 30) {
                    try {
                        d.setUserControlDisabledPackages(admin, emptyList())
                    } catch (e: Exception) {
                    }
                }
            }
            return true
        }

        private fun setUninstallBlocked(d: DevicePolicyManager, admin: ComponentName, pkg: String, blocked: Boolean) {
            try {
                d.setUninstallBlocked(admin, pkg, blocked)
            } catch (e: Exception) {
            }
        }

        /** Every app the user installed, including updated system apps (plus this one). */
        private fun userPackages(context: Context): List<String> {
            val pm = context.packageManager
            return pm.getInstalledApplications(0)
                .filter {
                    (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 ||
                        (it.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                }
                .map { it.packageName }
                .plus(context.packageName)
                .distinct()
        }

        /** Removes every restriction and gives up device-owner status. */
        fun releasePolicy(context: Context): Boolean {
            if (!isOwner(context)) return false
            dpm(context).clearUserRestriction(component(context), UserManager.DISALLOW_APPS_CONTROL)
            dpm(context).clearUserRestriction(component(context), UserManager.DISALLOW_UNINSTALL_APPS)
            for (pkg in userPackages(context)) setUninstallBlocked(dpm(context), component(context), pkg, false)
            if (Build.VERSION.SDK_INT >= 30) {
                try {
                    dpm(context).setUserControlDisabledPackages(component(context), emptyList())
                } catch (e: Exception) {
                }
            }
            @Suppress("DEPRECATION")
            dpm(context).clearDeviceOwnerApp(context.packageName)
            return true
        }
    }
}
