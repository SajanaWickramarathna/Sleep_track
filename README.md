# SleepTracker 🌙

A professional, high-end Android sleep tracking assistant built with an iOS-inspired Glassmorphism UI and deep hardware integrations.

## ✨ Features

- **NFC Ghost Scanning**: Go to sleep instantly. Simply tap an NFC sticker, bank card, or hotel key against the back of your phone to start/stop tracking. Runs silently in the background via a "Ghost Screen" activity without ever opening the app UI.
- **Smart Wake-Up Alarm**: Set your target wake time (e.g., 7:00 AM). The app waits until you enter your lightest sleep phase within a 30-minute window (6:30 AM - 7:00 AM) to gently wake you up, preventing morning grogginess.
- **Mattress Motion Tracking**: Uses high-frequency accelerometer sampling to monitor your movement throughout the night and calculate your overall sleep quality score.
- **Background Snore Detection**: Uses Android `PARTIAL_WAKE_LOCK` to keep the CPU awake with the screen completely off, analyzing microphone decibel spikes to count snoring and environmental disruptions.
- **Sleep Debt Tracker**: Calculates a 7-day rolling window of your sleep history against an 8-hour daily goal to tell you exactly how many hours of "Sleep Debt" you owe your body.
- **Pre-Sleep Habit Tagging**: Quickly tag habits (☕ Caffeine, 🏃 Workout, 🍷 Alcohol, 📱 Screen Time) before bed to track how they influence your sleep quality over time.

## 🛠️ Architecture & Tech Stack

- **Kotlin & Android SDK**
- **NFC Intents & HCE**: Uses `ForegroundDispatch` and `Theme.NoDisplay` invisible activities for aggressive background NFC interception.
- **Foreground Services**: Uses `ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE` and `DATA_SYNC` to bypass Android 14 background execution limits.
- **WakeLocks**: Implements `PowerManager.PARTIAL_WAKE_LOCK` to prevent OEMs (like Samsung) from suspending the microphone array while the screen is off.
- **Custom UI**: Dark mode with blur/glassmorphism aesthetics, using standard Android XML layouts and dynamic programmatic styling.

## 📱 Installation
1. Clone the repository.
2. Open in Android Studio.
3. Build and run `app-debug.apk` on a physical device. *(Note: NFC and background microphone tracking cannot be accurately tested on an emulator).*

## 🔒 Privacy
All sleep data, microphone decibel processing, and accelerometer history are kept strictly local on your device via `SharedPreferences`. No audio files are ever saved or transmitted.
