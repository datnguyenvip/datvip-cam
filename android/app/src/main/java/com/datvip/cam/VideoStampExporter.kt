package com.datvip.cam

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.google.common.collect.ImmutableList
import java.io.File

data class VideoStampFrame(val timeUs: Long, val stamp: Stamp)

/** Burns the recorded stamp timeline into the MP4 before publishing it to MediaStore. */
@UnstableApi
class VideoStampExporter {
    private var transformer: Transformer? = null

    fun start(
        context: Context,
        input: File,
        output: File,
        landscape: Boolean,
        frames: List<VideoStampFrame>,
        draw: (Bitmap, Stamp) -> Unit,
        completed: () -> Unit,
        failed: (String) -> Unit
    ) {
        require(frames.isNotEmpty())
        output.delete()
        val overlay = object : BitmapOverlay() {
            private var bitmap: Bitmap? = null
            private var index = 0
            private var drawnIndex = -1

            override fun configure(videoSize: Size) {
                bitmap?.recycle()
                bitmap = Bitmap.createBitmap(videoSize.width, videoSize.height, Bitmap.Config.ARGB_8888)
                drawnIndex = -1
            }

            override fun getBitmap(presentationTimeUs: Long): Bitmap {
                while (index + 1 < frames.size && frames[index + 1].timeUs <= presentationTimeUs) index++
                val result = checkNotNull(bitmap)
                if (drawnIndex != index) {
                    result.eraseColor(Color.TRANSPARENT)
                    draw(result, frames[index].stamp)
                    drawnIndex = index
                }
                return result
            }

            override fun release() {
                super.release()
                bitmap?.recycle()
                bitmap = null
            }
        }
        val crop = Presentation.createForAspectRatio(
            if (landscape) 4f / 3f else 3f / 4f, Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP
        )
        val item = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(input)))
            .setEffects(Effects(emptyList(), listOf(crop, OverlayEffect(ImmutableList.of(overlay)))))
            .build()
        val instance = Transformer.Builder(context)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    transformer = null
                    completed()
                }

                override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                    transformer = null
                    failed(exportException.localizedMessage ?: "Không xử lý được video")
                }
            }).build()
        transformer = instance
        try {
            instance.start(item, output.absolutePath)
        } catch (e: Exception) {
            transformer = null
            instance.cancel()
            failed(e.localizedMessage ?: "Không bắt đầu được xử lý video")
        }
    }

    fun cancel() {
        transformer?.cancel()
        transformer = null
    }
}
