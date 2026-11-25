package com.hebron.preloadviewer

import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.File

/**
 * Phase 1: read-only viewer.
 * Scans a folder for .apk files and shows name / version / icon for each one.
 * No install / uninstall action exists in this build.
 * The status badge is a fixed "מוגן" label for now — real
 * PackageManager install-state checking is planned for phase 2.
 */
class MainActivity : AppCompatActivity() {

    // Primary target. On a normal (non-system, non-rooted) install this will
    // usually fail with a permission error, which is expected and handled below.
    private val primaryFolder = File("/system/preload")

    // Fallback folder anyone can use without special permissions: put APKs here
    // from a file manager or `adb push` to test the screen end to end.
    private val fallbackFolder: File
        get() = File(getExternalFilesDir(null), "HebronInstall").apply { mkdirs() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val headerTitle = findViewById<TextView>(R.id.headerTitle)
        val headerCount = findViewById<TextView>(R.id.headerCount)
        val permissionBanner = findViewById<TextView>(R.id.permissionBanner)
        val listContainer = findViewById<LinearLayout>(R.id.listContainer)

        // Hidden entry point to the activity launcher — intentionally not a
        // visible button. Long-press the header title to open it.
        headerTitle.setOnLongClickListener {
            startActivity(android.content.Intent(this, AppListActivity::class.java))
            true
        }

        val (folder, usedFallback) = resolveReadableFolder()
        permissionBanner.visibility = if (usedFallback) View.VISIBLE else View.GONE

        // No sorting on purpose: the original PreloadManagerActivity uses
        // File.listFiles() as-is with no comparator, so its row order is
        // whatever order the filesystem returns — we match that exactly
        // instead of imposing our own (e.g. alphabetical) order.
        val apks = folder.listFiles { f -> f.isFile && f.name.lowercase().endsWith(".apk") }
            ?.toList()
            ?: emptyList()

        headerCount.text = getString(R.string.header_title).let {
            "${apks.size} אפליקציות"
        }

        if (apks.isEmpty()) {
            val empty = TextView(this).apply {
                text = getString(R.string.empty_state)
                setTextColor(resources.getColor(R.color.empty_text, theme))
                textSize = 15f
                gravity = android.view.Gravity.CENTER
                setPadding(32, 120, 32, 120)
            }
            listContainer.addView(empty)
            return
        }

        val inflater = LayoutInflater.from(this)
        val pm = packageManager

        apks.forEachIndexed { index, file ->
            val row = inflater.inflate(R.layout.item_app_row, listContainer, false)

            val iconView = row.findViewById<ImageView>(R.id.appIcon)
            val nameView = row.findViewById<TextView>(R.id.appName)
            val versionView = row.findViewById<TextView>(R.id.appVersion)
            // appStatus already defaults to the fixed "מוגן" string from the layout.

            val info = pm.getPackageArchiveInfo(file.absolutePath, 0)
            if (info != null && info.applicationInfo != null) {
                info.applicationInfo!!.sourceDir = file.absolutePath
                info.applicationInfo!!.publicSourceDir = file.absolutePath

                val label = try {
                    info.applicationInfo!!.loadLabel(pm).toString()
                } catch (e: Exception) {
                    file.nameWithoutExtension
                }
                val icon: Drawable? = try {
                    info.applicationInfo!!.loadIcon(pm)
                } catch (e: Exception) {
                    null
                }

                nameView.text = label
                versionView.text = if (!info.versionName.isNullOrEmpty()) {
                    "v${info.versionName}"
                } else {
                    file.name
                }
                if (icon != null) iconView.setImageDrawable(icon)
            } else {
                // Not a parseable APK (or permission blocked reading it) —
                // still list the file so nothing is silently hidden.
                nameView.text = file.nameWithoutExtension
                versionView.text = file.name
            }

            listContainer.addView(row)

            if (index < apks.size - 1) {
                val divider = View(this).apply {
                    setBackgroundColor(resources.getColor(R.color.row_divider, theme))
                }
                listContainer.addView(
                    divider,
                    LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
                )
            }
        }
    }

    /** Tries /system/preload first; falls back to an app-private folder if it can't be read. */
    private fun resolveReadableFolder(): Pair<File, Boolean> {
        return try {
            if (primaryFolder.canRead() && primaryFolder.listFiles() != null) {
                primaryFolder to false
            } else {
                fallbackFolder to true
            }
        } catch (e: SecurityException) {
            fallbackFolder to true
        }
    }
}
