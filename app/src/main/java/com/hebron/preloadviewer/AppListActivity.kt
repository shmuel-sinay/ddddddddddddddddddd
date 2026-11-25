package com.hebron.preloadviewer

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Step 1 of the Activity Launcher: lists every installed app (not just ones
 * with a launcher icon) so the user can drill into ActivityListActivity.
 */
class AppListActivity : AppCompatActivity() {

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

            row.setOnClickListener {
                startActivity(
                    Intent(this, ActivityListActivity::class.java)
                        .putExtra(ActivityListActivity.EXTRA_PACKAGE, appInfo.packageName)
                )
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
