package com.kidfocus.timer.ui.learning

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import java.util.Locale

class LearningTtsController(
    private val context: Context,
) : TextToSpeech.OnInitListener {
    private var engine: TextToSpeech? = TextToSpeech(context, this)
    private var ready = false
    private var pending: SpeechRequest? = null

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        pending?.let {
            pending = null
            speak(it)
        }
    }

    fun speak(
        text: String,
        languageTag: String,
        fallbackText: String?,
        fallbackLanguageTag: String,
        rate: Float,
        pitch: Float,
    ) {
        val request = SpeechRequest(
            text = text.take(1_000),
            languageTag = languageTag,
            fallbackText = fallbackText?.take(1_000),
            fallbackLanguageTag = fallbackLanguageTag,
            rate = rate.coerceIn(0.5f, 1.5f),
            pitch = pitch.coerceIn(0.5f, 1.5f),
        )
        if (!ready) {
            pending = request
            return
        }
        speak(request)
    }

    private fun speak(request: SpeechRequest) {
        val tts = engine ?: return
        val desiredLocale = Locale.forLanguageTag(request.languageTag)
        val supported = tts.isLanguageAvailable(desiredLocale) >= TextToSpeech.LANG_AVAILABLE
        val text = if (supported) request.text else request.fallbackText ?: request.text
        val locale = if (supported) desiredLocale else Locale.forLanguageTag(request.fallbackLanguageTag)
        tts.language = locale
        tts.setSpeechRate(request.rate)
        tts.setPitch(request.pitch)
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle(), "learning-${System.nanoTime()}")
    }

    fun stop() {
        pending = null
        engine?.stop()
    }

    fun openVoiceInstaller() {
        runCatching {
            context.startActivity(
                Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    fun close() {
        pending = null
        engine?.stop()
        engine?.shutdown()
        engine = null
        ready = false
    }

    private data class SpeechRequest(
        val text: String,
        val languageTag: String,
        val fallbackText: String?,
        val fallbackLanguageTag: String,
        val rate: Float,
        val pitch: Float,
    )
}
