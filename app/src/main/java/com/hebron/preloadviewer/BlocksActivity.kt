package com.hebron.preloadviewer

import android.app.AlertDialog
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat

/**
 * Hidden "ניהול חסימות" screen (reached from the hidden activity launcher):
 * turn each block on or off, or remove the MDM entirely.
 */
class BlocksActivity : AppCompatActivity() {

    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "ניהול חסימות"

        val dp = resources.displayMetrics.density
        val pad = (16 * dp).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        root.addView(TextView(this).apply {
            text = "ניהול חסימות"
            textSize = 20f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })

        status = TextView(this).apply {
            textSize = 14f
            setPadding(0, pad / 2, 0, pad)
        }
        root.addView(status)

        root.addView(SwitchCompat(this).apply {
            text = "הסתרת \"אפליקציות\" בהגדרות (דורש שירות נגישות)"
            isChecked = Blocks.hideSettingsApps(this@BlocksActivity)
            setOnCheckedChangeListener { _, on -> Blocks.setHideSettingsApps(this@BlocksActivity, on) }
        })

        root.addView(SwitchCompat(this).apply {
            text = "חסימת שינוי אפליקציות (MDM)"
            isChecked = Blocks.appsControl(this@BlocksActivity)
            setPadding(0, pad, 0, pad)
            setOnCheckedChangeListener { _, on ->
                Blocks.setAppsControl(this@BlocksActivity, on)
                if (!AdminReceiver.applyPolicy(this@BlocksActivity)) {
                    Toast.makeText(this@BlocksActivity, "האפליקציה אינה מנהל מכשיר — ההגדרה תיכנס לתוקף כשתהיה", Toast.LENGTH_LONG).show()
                }
                refresh()
            }
        })

        root.addView(Button(this).apply {
            text = "אפליקציות שיפתחו את חברון התקנות"
            setOnClickListener { chooseRedirectApps() }
        })

        root.addView(Button(this).apply {
            text = "בטל MDM לגמרי"
            setOnClickListener {
                AlertDialog.Builder(this@BlocksActivity)
                    .setMessage("לבטל את כל החסימות ולוותר על מעמד מנהל המכשיר?")
                    .setPositiveButton("בטל") { _, _ ->
                        if (AdminReceiver.releasePolicy(this@BlocksActivity)) {
                            Toast.makeText(this@BlocksActivity, "ה-MDM בוטל", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this@BlocksActivity, "האפליקציה אינה מנהל מכשיר", Toast.LENGTH_SHORT).show()
                        }
                        refresh()
                    }
                    .setNegativeButton("חזור", null)
                    .show()
            }
        })

        setContentView(root)
    }

    /** Pick the apps that, when opened, bring up the main screen of this app instead. */
    private fun chooseRedirectApps() {
        val pm = packageManager
        val launcher = android.content.Intent(android.content.Intent.ACTION_MAIN)
            .addCategory(android.content.Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(launcher, 0)
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .filter { it.first != packageName }
            .distinctBy { it.first }
            .sortedBy { it.second.lowercase() }
        val selected = Blocks.redirectPackages(this)
        val checked = BooleanArray(apps.size) { apps[it].first in selected }

        AlertDialog.Builder(this)
            .setTitle("אפליקציות שיפתחו את חברון התקנות")
            .setMultiChoiceItems(apps.map { it.second }.toTypedArray(), checked) { _, i, on ->
                checked[i] = on
            }
            .setPositiveButton("שמור") { _, _ ->
                Blocks.setRedirectPackages(
                    this,
                    apps.indices.filter { checked[it] }.map { apps[it].first }.toSet()
                )
            }
            .setNegativeButton("בטל", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val owner = AdminReceiver.isOwner(this)
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?.contains("$packageName/") == true
        status.text = "מנהל מכשיר (MDM): ${if (owner) "פעיל" else "כבוי"}\n" +
            "שירות נגישות: ${if (enabled) "פעיל" else "כבוי"}"
    }

    // Leaving this screen closes the hidden chain, like the other hidden screens.
    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) finishAffinity()
    }
}
