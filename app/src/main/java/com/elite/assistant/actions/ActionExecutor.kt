package com.elite.assistant.actions

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.BatteryManager
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.elite.assistant.notifications.EliteNotificationListener

/**
 * Turns an [ActionCall] into a real Android Intent or device reading.
 * Never throws: problems come back as [ActionResult.Say] with a sentence to speak.
 */
class ActionExecutor(private val context: Context) {

    private val contacts = ContactFinder(context)
    private val apps = AppFinder(context)

    fun execute(call: ActionCall): ActionResult = when (call.action) {
        EliteAction.OPEN_CAMERA -> ActionResult.AfterSpeech(::openCamera)
        EliteAction.OPEN_GALLERY -> ActionResult.AfterSpeech(::openGallery)
        EliteAction.BATTERY_LEVEL -> ActionResult.Say(batteryReport())
        EliteAction.OPEN_APP -> openApp(call.arg)
        EliteAction.SET_ALARM -> setAlarm(call.parts)
        EliteAction.SET_TIMER -> setTimer(call.parts)
        EliteAction.CALL -> call(call.arg)
        EliteAction.WHATSAPP -> whatsApp(call.parts)
        EliteAction.READ_NOTIFICATIONS -> notifications()
        EliteAction.LOOK -> ActionResult.TakePhoto
    }

    // ---- Camera & gallery ----

    /** Opens the camera app in photo mode. Needs no CAMERA permission: the camera app takes the picture. */
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

    // ---- Battery ----

    private fun batteryReport(): String {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val percent = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        // ACTION_BATTERY_CHANGED is sticky, so a null receiver just reads the last value.
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

    // ---- Apps ----

    private fun openApp(name: String): ActionResult {
        val (label, intent) = apps.launchIntentFor(name)
            ?: return ActionResult.Say("I couldn't find an app called $name, Sir.")
        return ActionResult.AfterSpeech {
            if (launch(intent) == null) null else "I couldn't open $label, Sir."
        }
    }

    // ---- Alarms & timers (handled by the phone's Clock app) ----

    private fun setAlarm(parts: List<String>): ActionResult {
        val time = Regex("""(\d{1,2})[:.](\d{2})""").find(parts.getOrElse(0) { "" })
            ?: return ActionResult.Say("I didn't get the alarm time, Sir.")
        val hour = time.groupValues[1].toInt()
        val minute = time.groupValues[2].toInt()
        if (hour !in 0..23 || minute !in 0..59) return ActionResult.Say("That isn't a valid time, Sir.")
        val intent = Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, hour)
            .putExtra(AlarmClock.EXTRA_MINUTES, minute)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        parts.getOrNull(1)?.takeIf { it.isNotBlank() }?.let { intent.putExtra(AlarmClock.EXTRA_MESSAGE, it) }
        return ActionResult.AfterSpeech {
            if (launch(intent) == null) null else "I couldn't find a clock app to set the alarm, Sir."
        }
    }

    private fun setTimer(parts: List<String>): ActionResult {
        val seconds = parts.getOrElse(0) { "" }.filter { it.isDigit() }.toIntOrNull()
            ?.takeIf { it in 1..86_400 }
            ?: return ActionResult.Say("I didn't get how long the timer should be, Sir.")
        val intent = Intent(AlarmClock.ACTION_SET_TIMER)
            .putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        parts.getOrNull(1)?.takeIf { it.isNotBlank() }?.let { intent.putExtra(AlarmClock.EXTRA_MESSAGE, it) }
        return ActionResult.AfterSpeech {
            if (launch(intent) == null) null else "I couldn't find a clock app to set the timer, Sir."
        }
    }

    // ---- Calls & WhatsApp (the user always presses call/send themselves) ----

    private fun call(who: String): ActionResult {
        val number = numberFor(who) ?: return contactProblem(who)
        // ACTION_DIAL fills in the number; the user taps the green button to call.
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(number)))
        return ActionResult.AfterSpeech { if (launch(intent) == null) null else "I couldn't open the dialer, Sir." }
    }

    private fun whatsApp(parts: List<String>): ActionResult {
        val who = parts.getOrElse(0) { "" }
        val message = parts.drop(1).joinToString(" | ").trim()
        val number = numberFor(who) ?: return contactProblem(who)
        val uri = Uri.parse(
            "https://api.whatsapp.com/send?phone=${ContactFinder.toWhatsAppNumber(number)}" +
                "&text=${Uri.encode(message)}"
        )
        return ActionResult.AfterSpeech {
            // Prefer the WhatsApp app (or WhatsApp Business); fall back to the browser link.
            val opened = listOf("com.whatsapp", "com.whatsapp.w4b").any { pkg ->
                launch(Intent(Intent.ACTION_VIEW, uri).setPackage(pkg)) == null
            } || launch(Intent(Intent.ACTION_VIEW, uri)) == null
            if (opened) null else "I couldn't open WhatsApp, Sir."
        }
    }

    /** A number for [who]: used as-is if it's digits, otherwise looked up in contacts. */
    private fun numberFor(who: String): String? {
        if (ContactFinder.looksLikeNumber(who)) return who
        if (!hasPermission(Manifest.permission.READ_CONTACTS)) return null
        return contacts.find(who)?.number
    }

    private fun contactProblem(who: String): ActionResult =
        if (!ContactFinder.looksLikeNumber(who) && !hasPermission(Manifest.permission.READ_CONTACTS)) {
            ActionResult.NeedPermission(
                Manifest.permission.READ_CONTACTS,
                "I need access to your contacts to find $who, Sir. Please allow it, then ask me again.",
            )
        } else {
            ActionResult.Say("I couldn't find $who in your contacts, Sir.")
        }

    // ---- Notifications ----

    private fun notifications(): ActionResult {
        if (!EliteNotificationListener.isEnabled(context)) {
            return ActionResult.AfterSpeech {
                launch(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                "I need notification access first, Sir. Please switch on Elite in the list I've opened, then ask me again."
            }
        }
        val digest = EliteNotificationListener.currentDigest()
            ?: return ActionResult.Say("I'm still connecting to your notifications, Sir. Try again in a moment.")
        return if (digest.isBlank()) ActionResult.Say("You have no new notifications, Sir.")
        else ActionResult.Notifications(digest)
    }

    // ---- Helpers ----

    private fun hasPermission(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

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
