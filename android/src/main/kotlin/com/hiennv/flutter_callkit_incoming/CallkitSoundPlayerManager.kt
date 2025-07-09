package com.hiennv.flutter_callkit_incoming

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.*
import android.text.TextUtils
import android.util.Log

class CallkitSoundPlayerManager(private val context: Context) {

    companion object {
        private const val TAG = "CallkitSoundPlayerManager"
    }

    private var vibrator: Vibrator? = null
    private var audioManager: AudioManager? = null
    private var mediaPlayer: MediaPlayer? = null

    fun play(data: Bundle) {
        prepare()
        playSound(data)
        playVibrator()
    }

    fun stop() {
        mediaPlayer?.run {
            stop()
            release()
        }
        vibrator?.cancel()
        mediaPlayer = null
        vibrator = null
    }

    fun destroy() {
        stop()
    }

    private fun prepare() {
        mediaPlayer?.run {
            stop()
            release()
        }
        vibrator?.cancel()
    }

    private fun playVibrator() {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager =
                context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (audioManager?.ringerMode != AudioManager.RINGER_MODE_SILENT) {
            vibrator?.let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    it.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 1000, 1000), 0))
                } else {
                    it.vibrate(longArrayOf(0, 1000, 1000), 0)
                }
            }
        }
    }

    private fun playSound(data: Bundle?) {
        val soundPath = data?.getString(
            CallkitConstants.EXTRA_CALLKIT_RINGTONE_PATH,
            ""
        )
        val uri = soundPath?.let { getRingtoneUri(it) }
        if (uri == null) {
            Log.e(TAG, "Failed to get ringtone URI for soundPath=$soundPath")
            return
        }
        try {
            initializeMediaPlayer(uri)
        } catch (e: Exception) {
            Log.e(TAG, "Error playing sound", e)
        }
    }

    private fun initializeMediaPlayer(uri: Uri) {
        mediaPlayer = MediaPlayer().apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val attributes = AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setLegacyStreamType(AudioManager.STREAM_RING)
                    .build()
                setAudioAttributes(attributes)
            } else {
                setAudioStreamType(AudioManager.STREAM_RING)
            }
            setDataSourceFromUri(uri)
            isLooping = true
            prepare()
            start()
        }
    }

    private fun setDataSourceFromUri(uri: Uri) {
        // Try AssetFileDescriptor-based source first
        try {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                mediaPlayer?.setDataSource(
                    afd.fileDescriptor,
                    afd.startOffset,
                    afd.length
                )
                return
            }
        } catch (e: Exception) {
            Log.w(TAG, "AFD data source failed, falling back to URI", e)
        }
        // Fallback to URI-based source
        mediaPlayer?.setDataSource(context, uri)
    }

    private fun getRingtoneUri(fileName: String): Uri? {
        if (fileName.isBlank()) {
            return getDefaultRingtoneUri()
        }
        return if (fileName.equals("system_ringtone_default", ignoreCase = true)) {
            getDefaultRingtoneUri(useSystemDefault = true)
        } else {
            try {
                val resId = context.resources.getIdentifier(fileName, "raw", context.packageName)
                if (resId != 0) {
                    Uri.parse("android.resource://${context.packageName}/$resId")
                } else {
                    getDefaultRingtoneUri()
                }
            } catch (e: Exception) {
                getDefaultRingtoneUri()
            }
        }
    }

    private fun getDefaultRingtoneUri(useSystemDefault: Boolean = false): Uri? {
        return try {
            if (!useSystemDefault) {
                val resId = context.resources.getIdentifier("ringtone_default", "raw", context.packageName)
                if (resId != 0) {
                    Uri.parse("android.resource://${context.packageName}/$resId")
                } else {
                    RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE)
                }
            } else {
                RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE)
            }
        } catch (e: Exception) {
            null
        }
    }
}
