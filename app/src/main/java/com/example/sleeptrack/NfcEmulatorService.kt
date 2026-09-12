package com.example.sleeptrack

import android.content.Context
import android.content.Intent
import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class NfcEmulatorService : HostApduService() {
    
    override fun processCommandApdu(commandApdu: ByteArray?, extras: Bundle?): ByteArray {
        try {
            val prefs = getSharedPreferences("SleepTrackerPrefs", Context.MODE_PRIVATE)
            val lastScanTime = prefs.getLong("last_nfc_scan_time", 0)
            val currentTime = System.currentTimeMillis()
            
            // 2 second cooldown
            if (currentTime - lastScanTime > 2_000) {
                
                // Save this exact moment as the last scan time
                prefs.edit().putLong("last_nfc_scan_time", currentTime).apply()
                
                // Perform the toggle logic right here in the background!
                val isSleeping = prefs.getBoolean("is_sleeping", false)
                
                if (isSleeping) {
                    // Wake up
                    val sleepTime = prefs.getLong("sleep_time", 0)
                    val durationMillis = currentTime - sleepTime
                    val hours = TimeUnit.MILLISECONDS.toHours(durationMillis)
                    val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMillis) % 60
                    
                    val dateFormat = SimpleDateFormat("MMM dd", Locale.getDefault())
                    val dateString = dateFormat.format(Date(currentTime))
                    
                    // Read the mattress movement data recorded overnight
                    val movementDataStr = prefs.getString("current_movement_data", "[]") ?: "[]"
                    val movementArray = JSONArray(movementDataStr)
                    
                    // Calculate Sleep Quality Score
                    var totalMovements = 0
                    for (i in 0 until movementArray.length()) {
                        totalMovements += movementArray.getInt(i)
                    }
                    val sleepQuality = if (movementArray.length() > 0) {
                        val avgMovement = totalMovements / movementArray.length()
                        Math.max(0, 100 - (avgMovement * 2)) // Simple algorithm
                    } else {
                        100
                    }
                    
                    // Read Disruptions & Habits
                    val disruptions = prefs.getInt("current_disruptions", 0)
                    val habitsStr = prefs.getString("current_habits", "") ?: ""
                    
                    val displayStr = "$dateString - ${hours}h ${minutes}m (Quality: $sleepQuality%)\nDisruptions: $disruptions\nHabits: $habitsStr"
                    
                    // Create structured data for analytics
                    val historyEntryObj = org.json.JSONObject()
                    historyEntryObj.put("date", dateString)
                    historyEntryObj.put("hours", hours + (minutes / 60.0))
                    historyEntryObj.put("quality", sleepQuality)
                    historyEntryObj.put("disruptions", disruptions)
                    historyEntryObj.put("habits", habitsStr)
                    historyEntryObj.put("display", displayStr)
                    historyEntryObj.put("movement_data", movementArray)
                    
                    // Update history array
                    val historyJson = prefs.getString("sleep_history", "[]") ?: "[]"
                    val jsonArray = JSONArray(historyJson)
                    val newArray = JSONArray()
                    newArray.put(historyEntryObj) // Add newest to top
                    
                    val limit = if (jsonArray.length() < 30) jsonArray.length() else 29
                    for (i in 0 until limit) {
                        newArray.put(jsonArray.get(i))
                    }
                    
                    prefs.edit()
                        .putBoolean("is_sleeping", false)
                        .putLong("sleep_time", 0)
                        .putString("sleep_history", newArray.toString())
                        .apply()
                        
                    // Stop the mattress tracking service
                    stopService(Intent(this, SleepTrackingService::class.java))
                    
                    showNfcFeedback(true, "You slept for ${hours}h ${minutes}m (Quality: $sleepQuality%)")
                } else {
                    // Go to bed
                    prefs.edit()
                        .putBoolean("is_sleeping", true)
                        .putLong("sleep_time", currentTime)
                        .apply()
                        
                    // Start the mattress tracking service
                    val trackIntent = Intent(this, SleepTrackingService::class.java)
                    try {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            androidx.core.content.ContextCompat.startForegroundService(this, trackIntent)
                        } else {
                            startService(trackIntent)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    
                    showNfcFeedback(false, "Tracking your sleep... Goodnight! 🌙")
                }
                
                // Broadcast a signal to update the UI
                val updateIntent = Intent("com.example.sleeptrack.UPDATE_UI")
                updateIntent.setPackage(packageName)
                sendBroadcast(updateIntent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // If it crashes silently, play an ERROR BEEP so the user knows it's a code crash!
            try {
                val toneGen = android.media.ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION, 100)
                toneGen.startTone(android.media.ToneGenerator.TONE_SUP_ERROR, 500)
            } catch (e2: Exception) {
                e2.printStackTrace()
            }
        }
        
        // Return a standard success response (90 00)
        return byteArrayOf(0x90.toByte(), 0x00.toByte())
    }

    private fun showNfcFeedback(isWakingUp: Boolean, message: String) {
        // 1. Audible Beep Feedback
        try {
            val toneGen = android.media.ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION, 100)
            toneGen.startTone(android.media.ToneGenerator.TONE_PROP_BEEP, 150)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. System Push Notification
        try {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            val channelId = "nfc_alerts"
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val channel = android.app.NotificationChannel(
                    channelId,
                    "SleepTrack Alerts",
                    android.app.NotificationManager.IMPORTANCE_HIGH
                )
                notificationManager.createNotificationChannel(channel)
            }
            
            val title = if (isWakingUp) "Good Morning! ☀️" else "SleepTrack Active"
            
            val builder = androidx.core.app.NotificationCompat.Builder(this, channelId)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                
            notificationManager.notify(1001, builder.build())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDeactivated(reason: Int) {
    }
}
