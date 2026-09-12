package com.example.sleeptrack

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.MediaRecorder
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import org.json.JSONArray
import java.util.Calendar

class SleepTrackingService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var mediaRecorder: MediaRecorder? = null
    private var wakeLock: android.os.PowerManager.WakeLock? = null
    
    private var lastX = 0f
    private var lastY = 0f
    private var lastZ = 0f
    private var isFirstRead = true
    
    private var currentMinuteMovement = 0f
    private val movementList = mutableListOf<Int>()
    private var totalDisruptions = 0
    private var ringtone: Ringtone? = null
    
    private val handler = Handler(Looper.getMainLooper())
    private val saveRunnable = object : Runnable {
        override fun run() {
            movementList.add(currentMinuteMovement.toInt())
            
            val prefs = getSharedPreferences("SleepTrackerPrefs", Context.MODE_PRIVATE)
            
            // Check Audio (Noise Detection)
            try {
                val maxAmplitude = mediaRecorder?.maxAmplitude ?: 0
                if (maxAmplitude > 10000) { // Threshold for loud noise / snore
                    totalDisruptions++
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            
            // Check Smart Alarm
            checkSmartAlarm(prefs, currentMinuteMovement)
            
            currentMinuteMovement = 0f
            
            val jsonArray = JSONArray(movementList)
            prefs.edit()
                .putString("current_movement_data", jsonArray.toString())
                .putInt("current_disruptions", totalDisruptions)
                .apply()
            
            handler.postDelayed(this, 60000) // 1 minute ticks
        }
    }

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        
        val powerManager = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        wakeLock = powerManager.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "SleepTrack::MicWakeLock")
        wakeLock?.acquire()
        
        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, "SleepTrackChannel")
            .setContentTitle("SleepTrack")
            .setContentText("Monitoring sleep and smart alarms...")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
            
        val hasMicPermission = androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
            
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                var serviceType = android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                if (hasMicPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) { // microphone type introduced in R (API 30)
                    serviceType = serviceType or android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                }
                startForeground(1, notification, serviceType)
            } else {
                startForeground(1, notification)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                startForeground(1, notification)
            } catch (e2: Exception) { e2.printStackTrace() }
        }
        
        // Setup Audio Recording for Decibel Spikes ONLY if permission granted
        if (hasMicPermission) {
            try {
                mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    MediaRecorder(this)
                } else {
                    MediaRecorder()
                }
                mediaRecorder?.setAudioSource(MediaRecorder.AudioSource.MIC)
                mediaRecorder?.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                mediaRecorder?.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                mediaRecorder?.setOutputFile(cacheDir.absolutePath + "/dev_null_audio.3gp")
                mediaRecorder?.prepare()
                mediaRecorder?.start()
            } catch (e: Exception) {
                e.printStackTrace()
                mediaRecorder = null
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        movementList.clear()
        totalDisruptions = 0
        getSharedPreferences("SleepTrackerPrefs", Context.MODE_PRIVATE)
            .edit()
            .remove("current_movement_data")
            .remove("current_disruptions")
            .apply()
            
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        handler.postDelayed(saveRunnable, 60000)
        
        return START_STICKY
    }
    
    private fun checkSmartAlarm(prefs: SharedPreferences, movement: Float) {
        val isAlarmEnabled = prefs.getBoolean("alarm_enabled", false)
        if (!isAlarmEnabled) return
        
        val targetHour = prefs.getInt("alarm_hour", 7)
        val targetMinute = prefs.getInt("alarm_minute", 0)
        
        val cal = Calendar.getInstance()
        val currentHour = cal.get(Calendar.HOUR_OF_DAY)
        val currentMinute = cal.get(Calendar.MINUTE)
        
        val currentMinutesTotal = currentHour * 60 + currentMinute
        val targetMinutesTotal = targetHour * 60 + targetMinute
        
        var diff = targetMinutesTotal - currentMinutesTotal
        if (diff < 0) {
            diff += 24 * 60 // Handle midnight crossover
        }
        
        // Is it exactly time?
        if (diff == 0) {
            triggerAlarm()
            return
        }
        
        // Is it within the 30-minute Smart Wake window, AND movement is high (Light Sleep)?
        if (diff in 1..30 && movement > 15f) {
            triggerAlarm()
        }
    }
    
    private fun triggerAlarm() {
        if (ringtone?.isPlaying == true) return
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ringtone = RingtoneManager.getRingtone(applicationContext, uri)
            ringtone?.play()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            
            if (!isFirstRead) {
                val deltaX = Math.abs(x - lastX)
                val deltaY = Math.abs(y - lastY)
                val deltaZ = Math.abs(z - lastZ)
                
                if (deltaX + deltaY + deltaZ > 0.5f) {
                    currentMinuteMovement += (deltaX + deltaY + deltaZ)
                }
            }
            
            lastX = x
            lastY = y
            lastZ = z
            isFirstRead = false
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
        handler.removeCallbacks(saveRunnable)
        ringtone?.stop()
        
        try {
            mediaRecorder?.stop()
            mediaRecorder?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "SleepTrackChannel",
                "Sleep Tracking",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }
}
