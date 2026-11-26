package com.hebron.preloadviewer

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * Step 1 of the Activity Launcher: lists every installed app (not just ones
 * with a launcher icon) so the user can drill into ActivityListActivity.
 */
class AppListActivity : AppCompatActivity() {

    // True while moving to our own sub-screen (so we must stay alive).
    private var navigatingInside = false

    /**
     * The app's "main screen" even if it has no launcher icon:
     * 1. the normal launcher activity, else
     * 2. any activity with action MAIN (the launcher category may have been removed), else
     * 3. the first activity another app is allowed to start.
     */
    private fun findMainIntent(pkg: String): Intent? {
        packageManager.getLaunchIntentForPackage(pkg)?.let { return it }

        val main = packageManager.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).setPackage(pkg), 0
        ).firstOrNull { it.activityInfo.exported && it.activityInfo.enabled }
        if (main != null) {
            return Intent(Intent.ACTION_MAIN).setClassName(pkg, main.activityInfo.name)
        }

        val first = try {
            packageManager.getPackageInfo(pkg, PackageManager.GET_ACTIVITIES).activities
                ?.firstOrNull { it.exported && it.enabled && it.permission == null }
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
        return first?.let { Intent().setClassName(pkg, it.name) }
    }

    override fun onStart() {
        super.onStart()
        navigatingInside = false
    }

    /**
     * Leaving to another app / home / recents closes this hidden screen, so
     * re-entering the app lands on the main screen instead of this one.
     */
    override fun onStop() {
        super.onStop()
        if (!navigatingInside && !isChangingConfigurations) finishAffinity()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_list)

        val headerTitle = findViewById<TextView>(R.id.headerTitle)
        val headerCount = findViewById<TextView>(R.id.headerCount)
        val listContainer = findViewById<LinearLayout>(R.id.listContainer)
        headerTitle.text = "מפעיל פעילויות"

        val pm = packageManager
        val packages = pm.getInstalledApplications(0)
            // Only user-installed apps — system apps aren't interesting here
            // and would otherwise flood the list with hundreds of entries.
            // An app that was updated (e.g. a system app replaced by a
            // Play Store update) still counts as "system" here on purpose.
            .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
            .sortedBy { it.loadLabel(pm).toString().lowercase() }

        headerCount.text = "${packages.size} אפליקציות"

        val inflater = LayoutInflater.from(this)
        packages.forEachIndexed { index, appInfo: ApplicationInfo ->
            val row = inflater.inflate(R.layout.item_simple_row, listContainer, false)
            val icon = row.findViewById<ImageView>(R.id.rowIcon)
            val title = row.findViewById<TextView>(R.id.rowTitle)
            val subtitle = row.findViewById<TextView>(R.id.rowSubtitle)

            title.text = appInfo.loadLabel(pm)
            subtitle.text = appInfo.packageName
            icon.setImageDrawable(appInfo.loadIcon(pm))

            // Short press: open the app's main screen.
            row.setOnClickListener {
                val launch = findMainIntent(appInfo.packageName)
                if (launch != null) {
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    try {
                        startActivity(launch)
                    } catch (e: Exception) {
                        Toast.makeText(this, "שגיאה בהפעלה: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                } else {
                    Toast.makeText(this, "לא נמצא מסך שניתן לפתוח באפליקציה הזו", Toast.LENGTH_LONG).show()
                }
            }
            // Long press: list all of the app's openable sub-screens.
            row.setOnLongClickListener {
                navigatingInside = true
                startActivity(
                    Intent(this, ActivityListActivity::class.java)
                        .putExtra(ActivityListActivity.EXTRA_PACKAGE, appInfo.packageName)
                )
                true
            }
            listContainer.addView(row)

            if (index < packages.size - 1) {
                val divider = View(this).apply {
                    setBackgroundColor(resources.getColor(R.color.row_divider, theme))
                }
                listContainer.addView(divider, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1))
            }
        }
    }
}
