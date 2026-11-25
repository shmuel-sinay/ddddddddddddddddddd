package com.hebron.preloadviewer

import android.content.ActivityNotFoundException
import android.content.Intent
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
 * Step 2 of the Activity Launcher: lists every Activity declared in one
 * app's manifest (GET_ACTIVITIES), including ones with no launcher icon,
 * and attempts to start whichever one the user taps via an explicit Intent.
 *
 * Android itself still enforces exported="false": trying to start a
 * non-exported Activity from outside its own app throws SecurityException,
 * and that's shown to the user as a message instead of a crash — this tool
 * does not and cannot bypass that protection.
 */
class ActivityListActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PACKAGE = "extra_package"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_list)

        val packageName = intent.getStringExtra(EXTRA_PACKAGE) ?: run { finish(); return }

        val headerTitle = findViewById<TextView>(R.id.headerTitle)
        val headerCount = findViewById<TextView>(R.id.headerCount)
        val listContainer = findViewById<LinearLayout>(R.id.listContainer)

        val pm = packageManager
        val appLabel = try {
            pm.getApplicationInfo(packageName, 0).loadLabel(pm).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            packageName
        }
        headerTitle.text = appLabel

        val activities = try {
            pm.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES).activities?.toList()
                ?: emptyList()
        } catch (e: PackageManager.NameNotFoundException) {
            emptyList()
        }.sortedBy { it.name }

        headerCount.text = "${activities.size} מסכים"

        if (activities.isEmpty()) {
            val empty = TextView(this).apply {
                text = "לא נמצאו activities מוצהרים לאפליקציה הזו"
                setTextColor(resources.getColor(R.color.empty_text, theme))
                textSize = 15f
                gravity = android.view.Gravity.CENTER
                setPadding(32, 120, 32, 120)
            }
            listContainer.addView(empty)
            return
        }

        val inflater = LayoutInflater.from(this)
        activities.forEachIndexed { index, activityInfo ->
            val row = inflater.inflate(R.layout.item_simple_row, listContainer, false)
            val icon = row.findViewById<ImageView>(R.id.rowIcon)
            val title = row.findViewById<TextView>(R.id.rowTitle)
            val subtitle = row.findViewById<TextView>(R.id.rowSubtitle)

            // Short class name for the title, full name (and export state) as the subtitle.
            val shortName = activityInfo.name.removePrefix(packageName)
            title.text = if (shortName.isEmpty()) activityInfo.name else shortName
            subtitle.text = if (activityInfo.exported) {
                "${activityInfo.name}  ·  exported"
            } else {
                "${activityInfo.name}  ·  לא exported"
            }
            icon.setImageDrawable(
                try { activityInfo.loadIcon(pm) } catch (e: Exception) { null }
                    ?: try { pm.getApplicationInfo(packageName, 0).loadIcon(pm) } catch (e: Exception) { null }
            )

            row.setOnClickListener {
                try {
                    startActivity(
                        Intent().apply {
                            setClassName(packageName, activityInfo.name)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    )
                } catch (e: SecurityException) {
                    Toast.makeText(
                        this,
                        "לא ניתן להפעיל — מוגן ע\"י האפליקציה (exported=false)",
                        Toast.LENGTH_LONG
                    ).show()
                } catch (e: ActivityNotFoundException) {
                    Toast.makeText(this, "המסך הזה לא נמצא / לא ניתן להפעלה ישירה", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "שגיאה בהפעלה: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
            listContainer.addView(row)

            if (index < activities.size - 1) {
                val divider = View(this).apply {
                    setBackgroundColor(resources.getColor(R.color.row_divider, theme))
                }
                listContainer.addView(divider, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1))
            }
        }
    }
}
