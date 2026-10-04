package com.nigelspeight.sportscollector.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.Handler
import android.os.Looper
import android.os.SystemClock

/// Background-music playback, mirroring the original game's single-channel
/// model - only one music track ever plays at a time, switched between gameplay
/// loop and win/lose jingles. Gameplay sound effects are short one-shots and go
/// through a `SoundPool` (`playSound`).
object AudioManager {
    private lateinit var context: Context
    private val handler = Handler(Looper.getMainLooper())

    private var musicPlayer: MediaPlayer? = null
    private var pausedByLifecycle = false
    private val activeFades = mutableMapOf<MediaPlayer, Runnable>()

    private lateinit var soundPool: SoundPool
    private val soundIds = mutableMapOf<String, Int>()

    private val effectNames = listOf(
        "BacteriaFall_1sec", "Explosion1", "Explosion2Loud", "PillX2", "PillsFalling1Sec",
        "ShowHint", "Siren1_2secs", "Siren2_2secs", "Squelch_Tile1", "StarryEffect1A_UpBeat",
        "SwapPillError1", "Swap_Pill1A",
    )

    fun init(context: Context) {
        this.context = context.applicationContext
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        soundPool = SoundPool.Builder().setMaxStreams(8).setAudioAttributes(attributes).build()
        for (name in effectNames) {
            this.context.assets.openFd("audio/$name.wav").use { fd ->
                soundIds[name] = soundPool.load(fd, 1)
            }
        }
    }

    fun playSound(name: String) {
        val id = soundIds[name] ?: return
        soundPool.play(id, 1f, 1f, 1, 0, 1f)
    }

    fun playMusic(resourceName: String, loop: Boolean, fadeDuration: Double = 0.4) {
        musicPlayer?.let { outgoing -> fadeVolume(outgoing, from = 1f, to = 0f, fadeDuration) { outgoing.release() } }
        musicPlayer = null
        pausedByLifecycle = false

        val player = try {
            context.assets.openFd("audio/$resourceName.mp3").use { fd ->
                MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build(),
                    )
                    setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
                    isLooping = loop
                    setVolume(0f, 0f)
                    prepare()
                }
            }
        } catch (_: Exception) {
            return
        }
        player.start()
        fadeVolume(player, from = 0f, to = 1f, fadeDuration)
        musicPlayer = player
    }

    fun stopMusic(fadeDuration: Double = 0.3) {
        val player = musicPlayer ?: return
        fadeVolume(player, from = 1f, to = 0f, fadeDuration) { player.release() }
        musicPlayer = null
    }

    /// Called when the app leaves/returns to the foreground, so music doesn't keep
    /// playing in the background the way iOS audio sessions stop it.
    fun onAppBackgrounded() {
        val player = musicPlayer ?: return
        if (player.isPlaying) {
            player.pause()
            pausedByLifecycle = true
        }
    }

    fun onAppForegrounded() {
        if (pausedByLifecycle) {
            musicPlayer?.start()
            pausedByLifecycle = false
        }
    }

    private fun fadeVolume(player: MediaPlayer, from: Float, to: Float, duration: Double, onDone: () -> Unit = {}) {
        val start = SystemClock.uptimeMillis()
        val durationMs = (duration * 1000).toLong().coerceAtLeast(1)
        activeFades.remove(player)?.let(handler::removeCallbacks)
        val step = object : Runnable {
            override fun run() {
                val t = ((SystemClock.uptimeMillis() - start).toFloat() / durationMs).coerceIn(0f, 1f)
                val volume = from + (to - from) * t
                try {
                    player.setVolume(volume, volume)
                } catch (_: IllegalStateException) {
                    activeFades.remove(player)
                    return
                }
                if (t < 1f) {
                    handler.postDelayed(this, 16)
                } else {
                    activeFades.remove(player)
                    onDone()
                }
            }
        }
        activeFades[player] = step
        handler.post(step)
    }
}
