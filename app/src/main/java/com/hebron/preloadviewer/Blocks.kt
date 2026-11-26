package com.hebron.preloadviewer

import android.content.Context

/** User-chosen on/off switches for the blocks, kept in app-private preferences. */
object Blocks {
    private const val PREFS = "blocks"
    private const val KEY_HIDE_APPS = "hide_settings_apps"
    private const val KEY_APPS_CONTROL = "disallow_apps_control"

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Send the user home whenever Settings' "Apps" screens open. */
    fun hideSettingsApps(c: Context) = prefs(c).getBoolean(KEY_HIDE_APPS, true)
    fun setHideSettingsApps(c: Context, on: Boolean) =
        prefs(c).edit().putBoolean(KEY_HIDE_APPS, on).apply()

    /** MDM restriction: no changing apps (uninstall / disable / force stop / clear data). */
    fun appsControl(c: Context) = prefs(c).getBoolean(KEY_APPS_CONTROL, true)
    fun setAppsControl(c: Context, on: Boolean) =
        prefs(c).edit().putBoolean(KEY_APPS_CONTROL, on).apply()
}
