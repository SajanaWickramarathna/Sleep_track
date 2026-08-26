package com.example.sleeptrack

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var resultText: TextView
    private lateinit var sleepButton: Button
    private lateinit var historyText: TextView

    private lateinit var sharedPreferences: SharedPreferences
    private val PREFS_NAME = "SleepTrackerPrefs"
    private val KEY_SLEEP_TIME = "sleep_time"
    private val KEY_IS_SLEEPING = "is_sleeping"
    private val KEY_HISTORY = "sleep_history"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        resultText = findViewById(R.id.resultText)
        sleepButton = findViewById(R.id.sleepButton)
        historyText = findViewById(R.id.historyText)

        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        updateUI()
        updateHistoryUI()

        sleepButton.setOnClickListener {
            val isSleeping = sharedPreferences.getBoolean(KEY_IS_SLEEPING, false)
            if (isSleeping) {
                // Wake up
                val sleepTime = sharedPreferences.getLong(KEY_SLEEP_TIME, 0)
                val wakeTime = System.currentTimeMillis()
                val durationMillis = wakeTime - sleepTime
                
                val hours = TimeUnit.MILLISECONDS.toHours(durationMillis)
                val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMillis) % 60
                
                val sleepSummary = "You slept for $hours hours and $minutes minutes."
                resultText.text = sleepSummary
                
                // Save to history
                val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                val dateString = dateFormat.format(Date(wakeTime))
                val historyEntry = "$dateString - ${hours}h ${minutes}m"
                addHistoryEntry(historyEntry)
                
                sharedPreferences.edit()
                    .putBoolean(KEY_IS_SLEEPING, false)
                    .putLong(KEY_SLEEP_TIME, 0)
                    .apply()
            } else {
                // Go to bed
                sharedPreferences.edit()
                    .putBoolean(KEY_IS_SLEEPING, true)
                    .putLong(KEY_SLEEP_TIME, System.currentTimeMillis())
                    .apply()
                
                resultText.text = ""
            }
            updateUI()
            updateHistoryUI()
        }
    }

    private fun updateUI() {
        val isSleeping = sharedPreferences.getBoolean(KEY_IS_SLEEPING, false)
        if (isSleeping) {
            statusText.text = "You are sleeping..."
            sleepButton.text = "Wake Up"
        } else {
            statusText.text = "Ready to sleep?"
            sleepButton.text = "Go to Bed"
        }
    }
    
    private fun addHistoryEntry(entry: String) {
        val historyJson = sharedPreferences.getString(KEY_HISTORY, "[]")
        val jsonArray = JSONArray(historyJson)
        
        val newArray = JSONArray()
        newArray.put(entry) // Add new entry at the top (most recent first)
        
        // Keep up to 30 entries (about a month)
        val limit = if (jsonArray.length() < 30) jsonArray.length() else 29
        for (i in 0 until limit) {
            newArray.put(jsonArray.getString(i))
        }
        
        sharedPreferences.edit().putString(KEY_HISTORY, newArray.toString()).apply()
    }
    
    private fun updateHistoryUI() {
        val historyJson = sharedPreferences.getString(KEY_HISTORY, "[]")
        val jsonArray = JSONArray(historyJson)
        
        if (jsonArray.length() == 0) {
            historyText.text = "No sleep history yet."
            return
        }
        
        val stringBuilder = StringBuilder()
        for (i in 0 until jsonArray.length()) {
            stringBuilder.append("• ").append(jsonArray.getString(i)).append("\n")
        }
        
        historyText.text = stringBuilder.toString()
    }
}
