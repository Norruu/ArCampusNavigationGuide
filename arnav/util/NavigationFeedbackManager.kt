package com.campus.arnav.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NavigationFeedbackManager @Inject constructor(
    @ApplicationContext private val context: Context
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    // --- FIX: Removed space in variable name ---
    private var lastSpokenInstruction: String? = null

    // Vibration Patterns
    private val TURN_VIBRATION = longArrayOf(0, 100, 50, 100) // Double tick
    private val ARRIVAL_VIBRATION = longArrayOf(0, 500, 200, 500) // Long buzz

    init {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.US)
            isTtsReady = result != TextToSpeech.LANG_MISSING_DATA &&
                    result != TextToSpeech.LANG_NOT_SUPPORTED
        }
    }

    fun speak(instruction: String) {
        // 1. CHECK SETTINGS
        val prefs = context.getSharedPreferences("arnav_settings", Context.MODE_PRIVATE)
        val isVoiceEnabled = prefs.getBoolean("voice_enabled", true)

        // Only speak if Enabled AND Ready
        if (isVoiceEnabled && isTtsReady && instruction != lastSpokenInstruction) {
            lastSpokenInstruction = instruction
            tts?.speak(instruction, TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }

    fun vibrateForTurn() {
        vibrate(TURN_VIBRATION)
    }

    fun vibrateForArrival() {
        vibrate(ARRIVAL_VIBRATION)
    }

    private fun vibrate(pattern: LongArray) {
        // 1. CHECK SETTINGS
        val prefs = context.getSharedPreferences("arnav_settings", Context.MODE_PRIVATE)
        val isVibrationEnabled = prefs.getBoolean("vibration_enabled", true)

        // If vibration is disabled in settings, stop here.
        if (!isVibrationEnabled) return

        // 2. PERFORM VIBRATION (Version Safe)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            val vibrator = vibratorManager.defaultVibrator
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION")
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            vibrator.vibrate(pattern, -1)
        }
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
    }
}