package com.elite.assistant.actions

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.provider.MediaStore

/**
 * Turns an [EliteAction] into a real Android Intent or a device reading.
 *
 * Returns a sentence to speak (e.g. the battery level, or an error), or null when
 * there is nothing extra to say.
 */
class ActionExecutor(private val context: Context) {

    fun execute(action: EliteAction): String? = when (action) {
        EliteAction.OPEN_CAMERA -> openCamera()
        EliteAction.OPEN_GALLERY -> openGallery()
        EliteAction.BATTERY_LEVEL -> batteryReport()
    }

    /**
     * Opens the default camera app in photo mode. This intent needs no CAMERA permission
     * because the camera app itself takes the picture.
     */
    private fun openCamera(): String? {
        if (launch(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)) == null) return null
        // Some phones only answer the "capture an image" intent.
        return if (launch(Intent(MediaStore.ACTION_IMAGE_CAPTURE)) == null) null
        else "I couldn't find a camera app, Sir."
    }

    /** Opens the gallery app; falls back to any app that can show the photo library. */
    private fun openGallery(): String? {
        val galleryApp = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_GALLERY)
        if (launch(galleryApp) == null) return null

        val photoLibrary = Intent(Intent.ACTION_VIEW, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        return if (launch(photoLibrary) == null) null else "I couldn't find a gallery app, Sir."
    }

    /** Reads battery percentage and charging state. No permission needed. */
    private fun batteryReport(): String {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val percent = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

        // ACTION_BATTERY_CHANGED is sticky, so passing a null receiver just reads the last value.
        val status = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL

        return buildString {
            append("Battery is at $percent percent")
            append(if (charging) " and charging." else ".")
            if (percent in 0..15 && !charging) append(" I recommend plugging in soon, Sir.")
        }
    }

    /** Starts [intent] from outside an Activity. Returns null on success, or the failure. */
    private fun launch(intent: Intent): Exception? = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        null
    } catch (e: ActivityNotFoundException) {
        e
    } catch (e: SecurityException) {
        e
    }
}
