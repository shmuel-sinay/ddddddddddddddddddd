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
        "allapps", "appmanagement", "uninstall"
    )

    // Whole phrases only: findAccessibilityNodeInfosByText matches substrings.
    private val uninstallTexts = listOf(
        "הסרת התקנה", "הסר את התקנת", "הסרת עדכונים", "הסרת האפליקציה",
        "uninstall", "uninstall updates",
        // Settings opens every sub-page inside the generic "SubSettings" screen, so the
        // app-info page is also recognised by its other buttons.
        "אילוץ עצירה", "עצירה בכוח", "force stop"
    )

    private val titles = setOf(
        "אפליקציות", "אפליקציות והתראות", "ניהול אפליקציות", "פרטי אפליקציה",
        "מידע על האפליקציה", "מידע על האפליקציות", "יישומים", "ניהול יישומים",
        "apps", "apps & notifications", "app info", "application manager",
        "manage apps", "app management", "applications"
    )

    // Protect apps installed later too: re-apply the policy whenever a package is added.
    private val packageAdded = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context, intent: Intent) {
            AdminReceiver.applyPolicy(context)
        }
    }

    // Cached so no event ever triggers a preferences read or a PackageManager query.
    @Volatile private var redirect: Set<String> = emptySet()
    private var lastScan = 0L

    private val prefsListener =
        android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> refreshConfig() }

    /** Packages that handle the system "uninstall app" screen on this device. */
    private fun installerPackages(): Set<String> {
        val found = try {
            packageManager.queryIntentActivities(
                Intent(Intent.ACTION_DELETE, android.net.Uri.parse("package:com.example.none")), 0
            ).map { it.activityInfo.packageName }
        } catch (e: Exception) {
            emptyList()
        }
        return found.toSet() + setOf("com.android.packageinstaller", "com.google.android.packageinstaller")
    }

    /**
     * Only events from the few packages we actually care about are delivered
     * to this service at all (Settings, the uninstall screen, and the apps
     * chosen for redirect). Every other app costs nothing.
     */
    private fun refreshConfig() {
        redirect = Blocks.redirectPackages(this)
        val info = serviceInfo ?: return
        info.packageNames = (setOf("com.android.settings") + installerPackages() + redirect).toTypedArray()
        serviceInfo = info
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        refreshConfig()
        getSharedPreferences("blocks", android.content.Context.MODE_PRIVATE)
            .registerOnSharedPreferenceChangeListener(prefsListener)
        registerReceiver(
            packageAdded,
            android.content.IntentFilter(Intent.ACTION_PACKAGE_ADDED).apply { addDataScheme("package") }
        )
        AdminReceiver.applyPolicy(this)
    }

    override fun onDestroy() {
        getSharedPreferences("blocks", android.content.Context.MODE_PRIVATE)
            .unregisterOnSharedPreferenceChangeListener(prefsListener)
        try { unregisterReceiver(packageAdded) } catch (_: Exception) { }
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val pkg = event.packageName?.toString() ?: return

        // Content changes are only used to spot an uninstall option inside Settings.
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            if (pkg == "com.android.settings" && Blocks.appsControl(this) && Blocks.textBlock(this)) {
                val now = android.os.SystemClock.uptimeMillis()
                if (now - lastScan >= 500) {
                    lastScan = now
                    if (showsUninstall()) openSettingsMain()
                }
            }
            return
        }
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        // 1) Chosen apps (e.g. the alarm clock): show this app's main screen instead.
        if (pkg != packageName && pkg in redirect) {
            openMainScreen()
            return
        }

        // Backup for devices that don't enforce the uninstall restriction themselves:
        // cancel the system "uninstall this app?" dialog as soon as it appears.
        if (Blocks.appsControl(this) && pkg.contains("packageinstaller", ignoreCase = true)) {
            val installerCls = event.className?.toString()?.lowercase().orEmpty()
            if (installerCls.contains("uninstall")) {
                performGlobalAction(GLOBAL_ACTION_BACK)
                return
            }
        }

        // 2) Settings "Apps" screens: show the main screen of Settings instead.
        if (pkg != "com.android.settings" || !Blocks.hideSettingsApps(this)) return
        val cls = event.className?.toString()?.lowercase().orEmpty()
        val title = event.text.joinToString(" ").trim().lowercase()
        if (classFragments.any { cls.contains(it) } || title in titles ||
            (Blocks.appsControl(this) && Blocks.textBlock(this) && showsUninstall())
        ) {
            openSettingsMain()
        }
    }

    /** True if the Settings screen currently shown offers to uninstall / remove an app. */
    private fun showsUninstall(): Boolean {
        val root = rootInActiveWindow ?: return false
        return try {
            uninstallTexts.any { root.findAccessibilityNodeInfosByText(it).isNotEmpty() }
        } finally {
            root.recycle()
        }
    }

    /** Resets Settings to its own main screen, dropping the Apps screens it was showing. */
    private fun openSettingsMain() {
        val settings = Intent(android.provider.Settings.ACTION_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        try {
            startActivity(settings)
        } catch (e: Exception) {
            // If the system refuses a direct start, leave Settings first, then retry.
            performGlobalAction(GLOBAL_ACTION_HOME)
            try { startActivity(settings) } catch (_: Exception) { }
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
