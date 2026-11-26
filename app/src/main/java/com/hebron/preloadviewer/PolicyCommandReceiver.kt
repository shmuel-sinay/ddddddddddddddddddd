package com.hebron.preloadviewer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Lets adb re-apply or fully release the policy. Guarded by the DUMP
 * permission, which only the shell / system hold, so other apps cannot
 * send these.
 *
 *   adb shell am broadcast -n com.hebron.preloadviewer/.PolicyCommandReceiver -a com.hebron.preloadviewer.APPLY
 *   adb shell am broadcast -n com.hebron.preloadviewer/.PolicyCommandReceiver -a com.hebron.preloadviewer.RELEASE
 */
class PolicyCommandReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            "com.hebron.preloadviewer.APPLY" -> AdminReceiver.applyPolicy(context)
            "com.hebron.preloadviewer.RELEASE" -> AdminReceiver.releasePolicy(context)
        }
    }
}
