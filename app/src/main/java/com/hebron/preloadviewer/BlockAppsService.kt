package com.hebron.preloadviewer

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

/**
 * Blocks the "Apps" area of the system Settings: as soon as one of its
 * screens opens, the user is sent straight back to the home screen, so the
 * app list / app info pages can't be seen at all.
 *
 * Screen class names differ between manufacturers, so detection uses both
 * known class-name fragments and the exact screen title.
 */
class BlockAppsService : AccessibilityService() {

    private val classFragments = listOf(
        "manageapplications", "installedappdetails", "appinfo", "appandnotification",
        "applicationsettings", "applicationdetails", "appdetails", "runningservices",
        "allapps", "appmanagement"
    )

    private val titles = setOf(
        "אפליקציות", "אפליקציות והתראות", "ניהול אפליקציות", "פרטי אפליקציה",
        "מידע על האפליקציה", "מידע על האפליקציות", "יישומים", "ניהול יישומים",
        "apps", "apps & notifications", "app info", "application manager",
        "manage apps", "app management", "applications"
    )

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return

        // 1) Chosen apps (e.g. the alarm clock): show this app's main screen instead.
        if (pkg != packageName && pkg in Blocks.redirectPackages(this)) {
            openMainScreen()
            return
        }

        // 2) Settings "Apps" screens: show this app's main screen instead.
        if (pkg != "com.android.settings" || !Blocks.hideSettingsApps(this)) return
        val cls = event.className?.toString()?.lowercase().orEmpty()
        val title = event.text.joinToString(" ").trim().lowercase()
        if (classFragments.any { cls.contains(it) } || title in titles) {
            openMainScreen()
        }
    }

    /** Brings up this app's main screen (never the hidden ones). */
    private fun openMainScreen() {
        val main = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        try {
            startActivity(main)
        } catch (e: Exception) {
            // If the system refuses a direct start, leave Settings first, then retry.
            performGlobalAction(GLOBAL_ACTION_HOME)
            try { startActivity(main) } catch (_: Exception) { }
        }
    }

    override fun onInterrupt() = Unit
}
