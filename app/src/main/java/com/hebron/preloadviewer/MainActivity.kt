package com.hebron.preloadviewer

import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File

/**
 * Phase 1: read-only viewer.
 * Scans a folder for .apk files and shows name / version / icon for each one.
 * No install / uninstall action exists in this build.
 *
 * The list has two sections: local apps (gray header) and, separately,
 * the file manager.
 */
class MainActivity : AppCompatActivity() {

    private class Entry(val file: File, val info: PackageInfo?)

    // Primary target. On a normal (non-system, non-rooted) install this will
    // usually fail with a permission error, which is expected and handled below.
    private val primaryFolder = File("/system/preload")

    // Fallback folder anyone can use without special permissions.
    private val fallbackFolder: File
        get() = File(getExternalFilesDir(null), "HebronInstall").apply { mkdirs() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Hidden entry point to the activity launcher — long-press the header title.
        findViewById<TextView>(R.id.headerTitle).setOnLongClickListener {
            startActivity(Intent(this, AppListActivity::class.java))
            true
        }

        findViewById<View>(R.id.refreshButton).setOnClickListener { load() }

        // Visual parity only — this build stays view-only, nothing is installed.
        findViewById<Button>(R.id.installAllButton).setOnClickListener {
            Toast.makeText(this, "תצוגה בלבד בגרסה הזו — אין פעולת התקנה", Toast.LENGTH_SHORT).show()
        }

        load()
    }

    private fun load() {
        val headerCount = findViewById<TextView>(R.id.headerCount)
        val permissionBanner = findViewById<TextView>(R.id.permissionBanner)
        val listContainer = findViewById<LinearLayout>(R.id.listContainer)
        listContainer.removeAllViews()

        val (folder, usedFallback) = resolveReadableFolder()
        permissionBanner.visibility = if (usedFallback) View.VISIBLE else View.GONE

        val pm = packageManager

        // No sorting on purpose: row order is whatever the filesystem returns.
        val entries = (folder.listFiles { f -> f.isFile && f.name.lowercase().endsWith(".apk") }
            ?.toList() ?: emptyList())
            .map { Entry(it, pm.getPackageArchiveInfo(it.absolutePath, 0)) }

        val installedCount = entries.count { isInstalled(it.info) }
        headerCount.text = "${entries.size} אפליקציות · $installedCount מותקנות"

        if (entries.isEmpty()) {
            val empty = TextView(this).apply {
                text = getString(R.string.empty_state)
                setTextColor(resources.getColor(R.color.empty_text, theme))
                textSize = 15f
                gravity = Gravity.CENTER
                setPadding(32, 120, 32, 120)
            }
            listContainer.addView(empty)
            return
        }

        val (tools, apps) = entries.partition { isFileManager(it, pm) }

        addSection(listContainer, R.string.section_local_apps, apps, pm, extraTop = false)
        addSection(listContainer, R.string.section_tools, tools, pm, extraTop = apps.isNotEmpty())
    }

    private fun addSection(
        container: LinearLayout,
        titleRes: Int,
        items: List<Entry>,
        pm: PackageManager,
        extraTop: Boolean
    ) {
        if (items.isEmpty()) return
        val inflater = LayoutInflater.from(this)
        val dp = resources.displayMetrics.density

        val header = TextView(this).apply {
            setText(titleRes)
            setTextColor(resources.getColor(R.color.section_text, theme))
            setTypeface(typeface, Typeface.BOLD)
            textSize = 15f
            setPadding(
                (16 * dp).toInt(),
                ((if (extraTop) 24 else 16) * dp).toInt(),
                (16 * dp).toInt(),
                (8 * dp).toInt()
            )
        }
        container.addView(header)

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(resources.getColor(R.color.row_bg, theme))
        }
        container.addView(
            card,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        )

        items.forEachIndexed { index, entry ->
            card.addView(buildRow(inflater, card, entry, pm))
            if (index < items.size - 1) {
                val divider = View(this).apply {
                    setBackgroundColor(resources.getColor(R.color.row_divider, theme))
                }
                card.addView(divider, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1))
            }
        }
    }

    private fun buildRow(inflater: LayoutInflater, parent: LinearLayout, entry: Entry, pm: PackageManager): View {
        val row = inflater.inflate(R.layout.item_app_row, parent, false)
        val iconView = row.findViewById<ImageView>(R.id.appIcon)
        val nameView = row.findViewById<TextView>(R.id.appName)
        val versionView = row.findViewById<TextView>(R.id.appVersion)
        val installedView = row.findViewById<TextView>(R.id.appInstalled)

        val file = entry.file
        val info = entry.info
        val appInfo = info?.applicationInfo
        if (info != null && appInfo != null) {
            appInfo.sourceDir = file.absolutePath
            appInfo.publicSourceDir = file.absolutePath

            nameView.text = try {
                appInfo.loadLabel(pm).toString()
            } catch (e: Exception) {
                file.nameWithoutExtension
            }
            val icon: Drawable? = try {
                appInfo.loadIcon(pm)
            } catch (e: Exception) {
                null
            }
            versionView.text = if (!info.versionName.isNullOrEmpty()) "v${info.versionName}" else file.name
            if (icon != null) iconView.setImageDrawable(icon)
        } else {
            // Not a parseable APK — still list the file so nothing is silently hidden.
            nameView.text = file.nameWithoutExtension
            versionView.text = file.name
        }
        installedView.visibility = if (isInstalled(info)) View.VISIBLE else View.GONE
        return row
    }

    private fun isInstalled(info: PackageInfo?): Boolean {
        if (info == null) return false
        return try {
            packageManager.getPackageInfo(info.packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun isFileManager(entry: Entry, pm: PackageManager): Boolean {
        val pkg = entry.info?.packageName?.lowercase() ?: ""
        val appInfo = entry.info?.applicationInfo
        val label = try {
            appInfo?.also {
                it.sourceDir = entry.file.absolutePath
                it.publicSourceDir = entry.file.absolutePath
            }?.loadLabel(pm)?.toString()
        } catch (e: Exception) {
            null
        } ?: entry.file.nameWithoutExtension
        val l = label.lowercase()
        return pkg.contains("filemanager") || pkg.contains("fileexplorer") ||
            pkg.contains("documentsui") || pkg.endsWith(".files") ||
            l.contains("קבצים") || l.contains("file manager")
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
