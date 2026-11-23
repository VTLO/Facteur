package com.vtlo.simplesms

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.telephony.SmsManager

/**
 * Sends ordinary, user-visible SMS text messages through the platform
 * [SmsManager] and reports back what the radio did with each message part.
 *
 * This deliberately uses only the public SmsManager API. That API sends
 * normal, stored, notified messages — it offers no way to set a Type-0
 * ("silent") protocol identifier or to force a Class-0 message, and this
 * class does not attempt to reach below it. Every message sent here is a
 * message the recipient can see and the sender explicitly composed.
 */
class SmsSender(private val context: Context) {

    /** A callback with a human-readable status for one message (or message part). */
    fun interface StatusListener {
        fun onStatus(status: String)
    }

    private val smsManager: SmsManager
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }

    /**
     * Sends [message] to [destination]. Long messages are split into the
     * standard multi-part segments. [onStatus] is invoked once the radio
     * reports the send result for each part.
     */
    fun send(destination: String, message: String, onStatus: StatusListener) {
        require(destination.isNotBlank()) { "Destination number is empty" }
        require(message.isNotEmpty()) { "Message is empty" }

        val sm = smsManager
        val parts = sm.divideMessage(message)
        val sentIntents = ArrayList<PendingIntent>(parts.size)

        parts.forEachIndexed { index, _ ->
            val action = "$ACTION_SENT.$index.${System.currentTimeMillis()}"
            registerOneShotReceiver(action, onStatus)
            sentIntents.add(
                PendingIntent.getBroadcast(
                    context,
                    0,
                    Intent(action).setPackage(context.packageName),
                    pendingIntentFlags()
                )
            )
        }

        if (parts.size == 1) {
            sm.sendTextMessage(destination, null, parts[0], sentIntents[0], null)
        } else {
            sm.sendMultipartTextMessage(destination, null, parts, sentIntents, null)
        }
    }

    private fun registerOneShotReceiver(action: String, onStatus: StatusListener) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                onStatus.onStatus(describeResult(resultCode))
                ctx.unregisterReceiver(this)
            }
        }
        val filter = IntentFilter(action)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }
    }

    private fun describeResult(resultCode: Int): String = when (resultCode) {
        android.app.Activity.RESULT_OK -> "Sent"
        SmsManager.RESULT_ERROR_GENERIC_FAILURE -> "Failed: generic failure"
        SmsManager.RESULT_ERROR_NO_SERVICE -> "Failed: no service"
        SmsManager.RESULT_ERROR_NULL_PDU -> "Failed: null PDU"
        SmsManager.RESULT_ERROR_RADIO_OFF -> "Failed: radio off"
        else -> "Failed: code $resultCode"
    }

    private fun pendingIntentFlags(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

    private companion object {
        const val ACTION_SENT = "com.vtlo.simplesms.SMS_SENT"
    }
}
