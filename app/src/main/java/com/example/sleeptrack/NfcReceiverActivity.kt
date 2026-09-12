package com.example.sleeptrack

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class NfcReceiverActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val prefs = getSharedPreferences("SleepTrackerPrefs", Context.MODE_PRIVATE)
        val lastScanTime = prefs.getLong("last_tag_scan", 0)
        val currentTime = System.currentTimeMillis()
        
        if (currentTime - lastScanTime > 2000) {
            prefs.edit().putLong("last_tag_scan", currentTime).apply()
            
            val isSleeping = prefs.getBoolean("is_sleeping", false)
            if (isSleeping) {
                // Wake up
                val sleepTime = prefs.getLong("sleep_time", 0)
                val durationMillis = currentTime - sleepTime
                val hours = TimeUnit.MILLISECONDS.toHours(durationMillis)
                val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMillis) % 60
                
                val dateFormat = SimpleDateFormat("MMM dd", Locale.getDefault())
                val dateString = dateFormat.format(Date(currentTime))
                
                val movementDataStr = prefs.getString("current_movement_data", "[]") ?: "[]"
                val movementArray = JSONArray(movementDataStr)
                
                var totalMovements = 0
                for (i in 0 until movementArray.length()) {
                    totalMovements += movementArray.getInt(i)
                }
                val sleepQuality = if (movementArray.length() > 0) {
                    val avgMovement = totalMovements / movementArray.length()
                    Math.max(0, 100 - (avgMovement * 2))
                } else {
                    100
                }
                
                val disruptions = prefs.getInt("current_disruptions", 0)
                val habitsStr = prefs.getString("current_habits", "") ?: ""
                val displayStr = "$dateString - ${hours}h ${minutes}m (Quality: $sleepQuality%)\nDisruptions: $disruptions\nHabits: $habitsStr"
                
                val historyEntryObj = org.json.JSONObject()
                historyEntryObj.put("date", dateString)
                historyEntryObj.put("hours", hours + (minutes / 60.0))
                historyEntryObj.put("quality", sleepQuality)
                historyEntryObj.put("disruptions", disruptions)
                historyEntryObj.put("habits", habitsStr)
                historyEntryObj.put("display", displayStr)
                historyEntryObj.put("movement_data", movementArray)
                
                val historyJson = prefs.getString("sleep_history", "[]") ?: "[]"
                val jsonArray = JSONArray(historyJson)
                val newArray = JSONArray()
                newArray.put(historyEntryObj)
                
                val limit = if (jsonArray.length() < 30) jsonArray.length() else 29
                for (i in 0 until limit) {
                    newArray.put(jsonArray.get(i))
                }
                
                prefs.edit()
                    .putBoolean("is_sleeping", false)
                    .putLong("sleep_time", 0)
                    .putString("sleep_history", newArray.toString())
                    .apply()
                    
                stopService(Intent(this, SleepTrackingService::class.java))
            } else {
                // Go to bed
                prefs.edit()
                    .putBoolean("is_sleeping", true)
                    .putLong("sleep_time", currentTime)
                    .apply()
                    
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
            }
            
            // Broadcast a signal to update the UI if MainActivity happens to be open in the background
            val updateIntent = Intent("com.example.sleeptrack.UPDATE_UI")
            updateIntent.setPackage(packageName)
            sendBroadcast(updateIntent)
            
            // Play a loud beep so the user knows the fallback worked silently in background
            try {
                val toneGen = android.media.ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION, 100)
                toneGen.startTone(android.media.ToneGenerator.TONE_PROP_BEEP, 150)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        finish() // Instantly close the invisible activity
    }
}
