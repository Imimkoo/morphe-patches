package com.ibrahim.av1mix.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.ibrahim.av1mix.data.StreamCandidate
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.NextRenderersFactory

@OptIn(UnstableApi::class)
object Av1PlayerFactory {
    fun create(context: Context, stream: StreamCandidate): ExoPlayer {
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("AV1Mix/0.1 (Android)")
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(mapOf("Referer" to stream.referer))
        val renderers = NextRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
        val mediaSourceFactory = DefaultMediaSourceFactory(context).setDataSourceFactory(httpFactory)
        val trackSelector = DefaultTrackSelector(context).apply {
            parameters = buildUponParameters()
                .setPreferredVideoMimeTypes(MimeTypes.VIDEO_AV1, MimeTypes.VIDEO_VP9, MimeTypes.VIDEO_H264)
                .setMaxVideoSize(1280, 1280)
                .build()
        }
        return ExoPlayer.Builder(context)
            .setTrackSelector(trackSelector)
            .setRenderersFactory(renderers)
            .setMediaSourceFactory(mediaSourceFactory)
            .build().apply {
                setMediaItem(MediaItem.fromUri(stream.url))
                prepare()
                playWhenReady = true
            }
    }
}
