package com.example.sleeptrack

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.TimePicker
import android.widget.ToggleButton
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    // Views
    private lateinit var statusText: TextView
    private lateinit var resultText: TextView
    private lateinit var sleepButton: Button
    private lateinit var overlayWakeButton: Button
    private lateinit var historyText: TextView
    private lateinit var insightText: TextView
    private lateinit var sleepDebtText: TextView
    
    // New Feature Views
    private lateinit var alarmTimePicker: TimePicker
    private lateinit var alarmSwitch: Switch
    private lateinit var btnCoffee: ToggleButton
    private lateinit var btnWorkout: ToggleButton
    private lateinit var btnAlcohol: ToggleButton
    private lateinit var btnScreen: ToggleButton
    
    // Tabs & Overlays
    private lateinit var tabHome: View
    private lateinit var tabTrends: View
    private lateinit var tabSettings: View
    private lateinit var sleepOverlay: FrameLayout
    private lateinit var navHome: LinearLayout
    private lateinit var navTrends: LinearLayout
    private lateinit var navSettings: LinearLayout
    
    // Custom Views
    private lateinit var sleepRingView: SleepRingView
    private lateinit var hypnogramView: HypnogramView
    private lateinit var breathingView: BreathingPulseView

    private lateinit var sharedPreferences: SharedPreferences
    private val PREFS_NAME = "SleepTrackerPrefs"
    private val KEY_SLEEP_TIME = "sleep_time"
    private val KEY_IS_SLEEPING = "is_sleeping"
    private val KEY_HISTORY = "sleep_history"
    
    private var nfcAdapter: NfcAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Request Audio Permissions
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 100)
        }

        // Bind Views
        statusText = findViewById(R.id.statusText)
        resultText = findViewById(R.id.resultText)
        sleepButton = findViewById(R.id.sleepButton)
        overlayWakeButton = findViewById(R.id.overlayWakeButton)
        historyText = findViewById(R.id.historyText)
        insightText = findViewById(R.id.insightText)
        sleepDebtText = findViewById(R.id.sleepDebtText)
        
        alarmTimePicker = findViewById(R.id.alarmTimePicker)
        alarmTimePicker.setIs24HourView(true)
        alarmSwitch = findViewById(R.id.alarmSwitch)
        
        btnCoffee = findViewById(R.id.btnCoffee)
        btnWorkout = findViewById(R.id.btnWorkout)
        btnAlcohol = findViewById(R.id.btnAlcohol)
        btnScreen = findViewById(R.id.btnScreen)
        
        tabHome = findViewById(R.id.tabHome)
        tabTrends = findViewById(R.id.tabTrends)
        tabSettings = findViewById(R.id.tabSettings)
        sleepOverlay = findViewById(R.id.sleepOverlay)
        navHome = findViewById(R.id.navHome)
        navTrends = findViewById(R.id.navTrends)
        navSettings = findViewById(R.id.navSettings)
        
        sleepRingView = findViewById(R.id.sleepRingView)
        hypnogramView = findViewById(R.id.hypnogramView)
        breathingView = findViewById(R.id.breathingView)

        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // Load saved Alarm
        alarmSwitch.isChecked = sharedPreferences.getBoolean("alarm_enabled", false)
        alarmTimePicker.hour = sharedPreferences.getInt("alarm_hour", 7)
        alarmTimePicker.minute = sharedPreferences.getInt("alarm_minute", 0)

        // Navigation Listeners
        navHome.setOnClickListener { switchTab(0) }
        navTrends.setOnClickListener { switchTab(1) }
        navSettings.setOnClickListener { switchTab(2) }

        // Action Listeners
        sleepButton.setOnClickListener { toggleSleepState() }
        overlayWakeButton.setOnClickListener { toggleSleepState() }
        
        // Auto-save listeners so NFC background service always has the latest UI values
        val saveUIState = { _: View? ->
            val habitsList = mutableListOf<String>()
            if (btnCoffee.isChecked) habitsList.add("☕")
            if (btnWorkout.isChecked) habitsList.add("🏃")
            if (btnAlcohol.isChecked) habitsList.add("🍷")
            if (btnScreen.isChecked) habitsList.add("📱")
            
            sharedPreferences.edit()
                .putBoolean("alarm_enabled", alarmSwitch.isChecked)
                .putInt("alarm_hour", alarmTimePicker.hour)
                .putInt("alarm_minute", alarmTimePicker.minute)
                .putString("current_habits", habitsList.joinToString(" "))
                .apply()
        }
        
        alarmSwitch.setOnCheckedChangeListener { _, _ -> saveUIState(null) }
        alarmTimePicker.setOnTimeChangedListener { _, _, _ -> saveUIState(null) }
        btnCoffee.setOnClickListener(saveUIState)
        btnWorkout.setOnClickListener(saveUIState)
        btnAlcohol.setOnClickListener(saveUIState)
        btnScreen.setOnClickListener(saveUIState)

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        
        updateUI()
        updateHistoryUI()
        handleNfcIntent(intent)
    }

    private fun switchTab(tabIndex: Int) {
        tabHome.visibility = if (tabIndex == 0) View.VISIBLE else View.GONE
        tabTrends.visibility = if (tabIndex == 1) View.VISIBLE else View.GONE
        tabSettings.visibility = if (tabIndex == 2) View.VISIBLE else View.GONE
        
        val homeText = navHome.getChildAt(1) as TextView
        val trendsText = navTrends.getChildAt(1) as TextView
        val settingsText = navSettings.getChildAt(1) as TextView
        
        // Reset all
        homeText.setTextColor(android.graphics.Color.parseColor("#B3FFFFFF"))
        homeText.setTypeface(null, android.graphics.Typeface.NORMAL)
        trendsText.setTextColor(android.graphics.Color.parseColor("#B3FFFFFF"))
        trendsText.setTypeface(null, android.graphics.Typeface.NORMAL)
        settingsText.setTextColor(android.graphics.Color.parseColor("#B3FFFFFF"))
        settingsText.setTypeface(null, android.graphics.Typeface.NORMAL)
        
        // Highlight active
        when (tabIndex) {
            0 -> {
                homeText.setTextColor(android.graphics.Color.WHITE)
                homeText.setTypeface(null, android.graphics.Typeface.BOLD)
            }
            1 -> {
                trendsText.setTextColor(android.graphics.Color.WHITE)
                trendsText.setTypeface(null, android.graphics.Typeface.BOLD)
            }
            2 -> {
                settingsText.setTextColor(android.graphics.Color.WHITE)
                settingsText.setTypeface(null, android.graphics.Typeface.BOLD)
            }
        }
    }

    private val nfcReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.example.sleeptrack.UPDATE_UI") {
                updateUI()
                updateHistoryUI()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val filter = android.content.IntentFilter("com.example.sleeptrack.UPDATE_UI")
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(nfcReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(nfcReceiver, filter)
        }
        
        // Setup both HCE routing (for iPhone) and Foreground Dispatch (for Bank Cards / Stickers)
        try {
            val nfcAdapter = NfcAdapter.getDefaultAdapter(this)
            if (nfcAdapter != null) {
                // Route iPhone taps to background service
                val componentName = android.content.ComponentName(this, NfcEmulatorService::class.java)
                android.nfc.cardemulation.CardEmulation.getInstance(nfcAdapter).setPreferredService(this, componentName)
                
                // Intercept physical tags tapped against this phone
                val pendingIntent = android.app.PendingIntent.getActivity(
                    this, 0, Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    android.app.PendingIntent.FLAG_MUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
                )
                nfcAdapter.enableForegroundDispatch(this, pendingIntent, null, null)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        updateUI()
    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(nfcReceiver)
            val nfcAdapter = NfcAdapter.getDefaultAdapter(this)
            if (nfcAdapter != null) {
                android.nfc.cardemulation.CardEmulation.getInstance(nfcAdapter).unsetPreferredService(this)
                nfcAdapter.disableForegroundDispatch(this)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNfcIntent(intent)
    }

    private fun handleNfcIntent(intent: Intent?) {
        if (intent?.action == NfcAdapter.ACTION_TECH_DISCOVERED || 
            intent?.action == NfcAdapter.ACTION_TAG_DISCOVERED ||
            intent?.action == NfcAdapter.ACTION_NDEF_DISCOVERED) {
            
            // This is the fallback! If the user taps a bank card or sticker to the Android phone.
            val lastScanTime = sharedPreferences.getLong("last_tag_scan", 0)
            val currentTime = System.currentTimeMillis()
            
            if (currentTime - lastScanTime > 2000) { // 2 sec cooldown
                sharedPreferences.edit().putLong("last_tag_scan", currentTime).apply()
                
                // Actually trigger the sleep tracking!
                toggleSleepState()
                
                // Play a loud beep so the user knows the fallback worked
                try {
                    val toneGen = android.media.ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION, 100)
                    toneGen.startTone(android.media.ToneGenerator.TONE_PROP_BEEP, 150)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun toggleSleepState() {
        val isSleeping = sharedPreferences.getBoolean(KEY_IS_SLEEPING, false)
        if (isSleeping) {
            // Waking Up
            val sleepTime = sharedPreferences.getLong(KEY_SLEEP_TIME, 0)
            val wakeTime = System.currentTimeMillis()
            val durationMillis = wakeTime - sleepTime
            
            val hours = TimeUnit.MILLISECONDS.toHours(durationMillis)
            val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMillis) % 60
            
            val dateFormat = SimpleDateFormat("MMM dd", Locale.getDefault())
            val dateString = dateFormat.format(Date(wakeTime))
            
            val movementDataStr = sharedPreferences.getString("current_movement_data", "[]")
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
            
            // Read Disruptions & Habits
            val disruptions = sharedPreferences.getInt("current_disruptions", 0)
            val habitsStr = sharedPreferences.getString("current_habits", "") ?: ""
            
            val displayStr = "$dateString - ${hours}h ${minutes}m (Quality: $sleepQuality%)\n" +
                             "Disruptions: $disruptions\nHabits: $habitsStr"
            
            val historyEntryObj = JSONObject()
            historyEntryObj.put("date", dateString)
            historyEntryObj.put("hours", hours + (minutes / 60.0))
            historyEntryObj.put("quality", sleepQuality)
            historyEntryObj.put("disruptions", disruptions)
            historyEntryObj.put("habits", habitsStr)
            historyEntryObj.put("display", displayStr)
            historyEntryObj.put("movement_data", movementArray)

            val historyJson = sharedPreferences.getString(KEY_HISTORY, "[]")
            val jsonArray = JSONArray(historyJson)
            val newArray = JSONArray()
            newArray.put(historyEntryObj) 
            
            val limit = if (jsonArray.length() < 30) jsonArray.length() else 29
            for (i in 0 until limit) {
                newArray.put(jsonArray.get(i))
            }
            
            sharedPreferences.edit()
                .putBoolean(KEY_IS_SLEEPING, false)
                .putLong(KEY_SLEEP_TIME, 0)
                .putString(KEY_HISTORY, newArray.toString())
                .apply()
                
            stopService(Intent(this, SleepTrackingService::class.java))
        } else {
            // Going to Sleep
            val habitsList = mutableListOf<String>()
            if (btnCoffee.isChecked) habitsList.add("☕")
            if (btnWorkout.isChecked) habitsList.add("🏃")
            if (btnAlcohol.isChecked) habitsList.add("🍷")
            if (btnScreen.isChecked) habitsList.add("📱")
            
            sharedPreferences.edit()
                .putBoolean(KEY_IS_SLEEPING, true)
                .putLong(KEY_SLEEP_TIME, System.currentTimeMillis())
                .putBoolean("alarm_enabled", alarmSwitch.isChecked)
                .putInt("alarm_hour", alarmTimePicker.hour)
                .putInt("alarm_minute", alarmTimePicker.minute)
                .putString("current_habits", habitsList.joinToString(" "))
                .apply()
                
            val trackIntent = Intent(this, SleepTrackingService::class.java)
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    ContextCompat.startForegroundService(this, trackIntent)
                } else {
                    startService(trackIntent)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        updateUI()
        updateHistoryUI()
    }

    private fun updateUI() {
        val isSleeping = sharedPreferences.getBoolean(KEY_IS_SLEEPING, false)
        if (isSleeping) {
            statusText.text = "Sleeping"
            sleepButton.text = "Wake Up"
            resultText.text = "Tracking active..."
            
            sleepOverlay.visibility = View.VISIBLE
            breathingView.startBreathing()
            sleepRingView.setProgress(0f)
            
            // Disable UI while sleeping
            alarmSwitch.isEnabled = false
            alarmTimePicker.isEnabled = false
            btnCoffee.isEnabled = false
            btnWorkout.isEnabled = false
            btnAlcohol.isEnabled = false
            btnScreen.isEnabled = false
            
        } else {
            statusText.text = "Awake"
            sleepButton.text = "Go to Bed"
            
            sleepOverlay.visibility = View.GONE
            breathingView.stopBreathing()
            
            alarmSwitch.isEnabled = true
            alarmTimePicker.isEnabled = true
            btnCoffee.isEnabled = true
            btnWorkout.isEnabled = true
            btnAlcohol.isEnabled = true
            btnScreen.isEnabled = true
            
            val historyJson = sharedPreferences.getString(KEY_HISTORY, "[]")
            try {
                val jsonArray = JSONArray(historyJson)
                if (jsonArray.length() > 0) {
                    val lastItem = jsonArray.getJSONObject(0)
                    resultText.text = "Latest: " + lastItem.optString("display", "")
                    
                    val hoursSlept = lastItem.optDouble("hours", 0.0)
                    val percentage = ((hoursSlept / 8.0) * 100).toFloat().coerceAtMost(100f)
                    sleepRingView.setProgress(percentage)
                } else {
                    resultText.text = "No sleep recorded yet"
                }
            } catch (e: Exception) {
                resultText.text = ""
            }
        }
    }
    
    private fun updateHistoryUI() {
        val historyJson = sharedPreferences.getString(KEY_HISTORY, "[]")
        val jsonArray = JSONArray(historyJson)
        
        if (jsonArray.length() == 0) {
            historyText.text = "No logs yet. Sleep tonight to see data!"
            insightText.text = "Sleep tonight to generate your first AI insight."
            sleepDebtText.text = "No data yet."
            sleepDebtText.setTextColor(android.graphics.Color.WHITE)
            hypnogramView.setData(emptyList())
            return
        }
        
        val stringBuilder = StringBuilder()
        var totalQuality = 0
        var totalHours = 0.0
        var recentDebtHours = 0.0
        
        val daysToCheck = Math.min(jsonArray.length(), 7)
        for (i in 0 until daysToCheck) {
            recentDebtHours += jsonArray.getJSONObject(i).optDouble("hours", 0.0)
        }
        
        // Calculate Sleep Debt
        val targetHours = daysToCheck * 8.0
        if (recentDebtHours < targetHours) {
            val debt = targetHours - recentDebtHours
            val dHours = debt.toInt()
            val dMins = ((debt - dHours) * 60).toInt()
            sleepDebtText.text = "${dHours}h ${dMins}m in debt"
            sleepDebtText.setTextColor(android.graphics.Color.parseColor("#FF5252")) // Red
        } else {
            sleepDebtText.text = "No debt! Fully recovered."
            sleepDebtText.setTextColor(android.graphics.Color.parseColor("#00E5FF")) // Cyan
        }
        
        // Render List
        for (i in 0 until jsonArray.length()) {
            val item = jsonArray.getJSONObject(i)
            stringBuilder.append("• ").append(item.optString("display", "")).append("\n\n")
            totalQuality += item.optInt("quality", 80)
            totalHours += item.optDouble("hours", 0.0)
        }
        historyText.text = stringBuilder.toString()
        
        // Generate AI Insight with Habits Analysis
        val avgQuality = if (jsonArray.length() > 0) totalQuality / jsonArray.length() else 0
        
        // Look at the most recent night's habits
        val lastItem = jsonArray.optJSONObject(0)
        val habits = lastItem?.optString("habits", "") ?: ""
        val disruptions = lastItem?.optInt("disruptions", 0) ?: 0
        
        if (disruptions > 10) {
            insightText.text = "⚠️ We detected $disruptions noise disruptions last night. Consider using earplugs or a white noise machine to improve deep sleep."
        } else if (habits.contains("🍷") && lastItem?.optInt("quality", 100) ?: 100 < 80) {
            insightText.text = "📉 We noticed your sleep quality dropped below 80% on a night you drank alcohol. Try stopping 3 hours before bed."
        } else if (habits.contains("☕") && lastItem?.optInt("quality", 100) ?: 100 < 80) {
            insightText.text = "📉 Caffeine seems to have impacted your resting heart rate last night resulting in lower quality."
        } else if (habits.contains("🏃")) {
            insightText.text = "✨ Great job working out! Physical activity is strongly correlated with an increase in your REM sleep cycles."
        } else if (avgQuality > 90) {
            insightText.text = "🔥 Excellent! Your sleep quality is exceptionally high. Keep maintaining your current routine."
        } else {
            insightText.text = "✨ Good consistency! You're getting solid rest, though room for a bit more deep sleep."
        }

        // Draw Hypnogram from actual movement data
        val movementDataArray = lastItem?.optJSONArray("movement_data")
        val realMovementData = mutableListOf<Int>()
        if (movementDataArray != null) {
            for (i in 0 until movementDataArray.length()) {
                realMovementData.add(movementDataArray.getInt(i))
            }
        }
        hypnogramView.setData(realMovementData)
    }
}
