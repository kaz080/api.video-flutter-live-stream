package video.api.flutter.livestream.utils

import android.media.AudioFormat
import android.util.Size
import io.github.thibaultbee.streampack.core.elements.encoders.AudioCodecConfig
import io.github.thibaultbee.streampack.core.elements.encoders.VideoCodecConfig

fun Map<String, Any>.toVideoConfig(): VideoCodecConfig {
    // VideoCodecConfig constructor is internal, but uses startBitrate parameter
    @Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
    return VideoCodecConfig(
        startBitrate = this["bitrate"] as Int,
        resolution = (this["resolution"] as String).toResolution(),
        fps = this["fps"] as Int
    )
}

fun Map<String, Any>.toAudioConfig(): AudioCodecConfig {
    val channelConfig = if (this["channel"] == "stereo") {
        AudioFormat.CHANNEL_IN_STEREO
    } else {
        AudioFormat.CHANNEL_IN_MONO
    }

    // AudioCodecConfig constructor does not have bitrate parameter
    @Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
    return AudioCodecConfig(
        sampleRate = this["sampleRate"] as Int,
        channelConfig = channelConfig
    )
}

fun String.toResolution(): Size {
    return when (this) {
        "240p" -> Size(426, 240)
        "360p" -> Size(640, 360)
        "480p" -> Size(854, 480)
        "720p" -> Size(1280, 720)
        "1080p" -> Size(1920, 1080)
        else -> throw IllegalArgumentException("Unknown resolution: $this")
    }
}

/**
 * Add a slash at the end of a [String] only if it is missing.
 *
 * @return the given string with a trailing slash.
 */
fun String.addTrailingSlashIfNeeded(): String {
    return if (this.endsWith("/")) this else "$this/"
}


