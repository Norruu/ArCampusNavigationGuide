package com.campus.arnav.util

import android.content.Context
import android.content.SharedPreferences
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
    private var lastSpokenInstruction: String? = null  // FIXED: was "last spokenInstruction"

    private val prefs: SharedPreferences =
        context.getSharedPreferences("arnav_settings", Context.MODE_PRIVATE)

    private val TURN_VIBRATION    = longArrayOf(0, 100, 50, 100)
    private val ARRIVAL_VIBRATION = longArrayOf(0, 500, 200, 500)

    private val isVoiceEnabled   get() = prefs.getBoolean("voice_enabled",     true)
    private val isVibrationEnabled get() = prefs.getBoolean("vibration_enabled", true)

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
        if (!isVoiceEnabled) return
        if (!isTtsReady) return
        if (instruction == lastSpokenInstruction) return  // don't repeat

        lastSpokenInstruction = instruction
        tts?.speak(instruction, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    fun vibrateForTurn() {
        if (isVibrationEnabled) vibrate(TURN_VIBRATION)
    }

    fun vibrateForArrival() {
        if (isVibrationEnabled) vibrate(ARRIVAL_VIBRATION)
    }

    private fun vibrate(pattern: LongArray) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val mgr = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                mgr.defaultVibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                val v = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                @Suppress("DEPRECATION")
                v.vibrate(pattern, -1)
            }
        } catch (e: Exception) {
            // Some devices (emulators) throw here — swallow silently
        }
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isTtsReady = false
    }
}