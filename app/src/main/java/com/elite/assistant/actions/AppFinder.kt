package com.elite.assistant.actions

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/** Finds an installed app by its spoken name ("YouTube", "phone pe"). */
class AppFinder(private val context: Context) {

    /** Returns a launch intent for the best match, or null if nothing fits. */
    fun launchIntentFor(spokenName: String): Pair<String, Intent>? {
        val wanted = normalise(spokenName)
        if (wanted.isEmpty()) return null
        val pm = context.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(launcher, PackageManager.MATCH_ALL)
            .map { it.loadLabel(pm).toString() to it.activityInfo.packageName }
            .filter { it.second != context.packageName }

        val best = apps.firstOrNull { normalise(it.first) == wanted }
            ?: apps.firstOrNull { normalise(it.first).startsWith(wanted) }
            ?: apps.firstOrNull { normalise(it.first).contains(wanted) }
            ?: apps.firstOrNull { wanted.contains(normalise(it.first)) && normalise(it.first).length >= 3 }
            ?: return null

        val intent = pm.getLaunchIntentForPackage(best.second) ?: return null
        return best.first to intent
    }

    /** Lowercase, letters and digits only: "Phone Pe" == "phonepe". */
    private fun normalise(s: String) = s.lowercase().filter { it.isLetterOrDigit() }
}
