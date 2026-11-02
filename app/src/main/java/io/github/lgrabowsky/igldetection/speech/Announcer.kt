package io.github.lgrabowsky.igldetection.speech

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.util.Log
import io.github.lgrabowsky.igl.core.IglStatus
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/** Sprachausgabe bei Statuswechseln, damit man während der Fahrt nicht aufs Display schauen muss. */
class Announcer(context: Context) : TextToSpeech.OnInitListener {

    private val tts = TextToSpeech(context.applicationContext, this)
    private val utteranceCounter = AtomicInteger()

    @Volatile
    private var ready = false

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            Log.w(TAG, "Sprachausgabe nicht verfügbar (Status $status)")
            return
        }
        val language = tts.setLanguage(Locale.forLanguageTag("de-AT"))
        if (language == TextToSpeech.LANG_MISSING_DATA || language == TextToSpeech.LANG_NOT_SUPPORTED) {
            Log.w(TAG, "Deutsche Stimme nicht installiert – verwende Standardstimme")
        }
        tts.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
        ready = true
    }

    fun say(text: String) {
        if (!ready) return
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "igl-${utteranceCounter.incrementAndGet()}")
    }

    fun shutdown() {
        ready = false
        tts.stop()
        tts.shutdown()
    }

    companion object {
        private const val TAG = "Announcer"

        /** Ansage für einen Statuswechsel, oder null, wenn nichts gesagt werden soll. */
        fun announcementFor(old: IglStatus, new: IglStatus): String? = when {
            new is IglStatus.IglActive && old !is IglStatus.IglActive ->
                "I G L aktiv. Tempo ${new.limitKmh} gilt nicht für Elektroautos."

            new is IglStatus.NoIgl && old is IglStatus.IglActive ->
                "I G L aufgehoben. Tempo ${new.limitKmh} gilt auch für Elektroautos."

            else -> null
        }
    }
}
