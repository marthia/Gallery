package com.dot.gallery.feature_node.domain.model

import android.net.Uri
import android.os.Parcelable
import com.bumptech.glide.load.Key
import kotlinx.parcelize.Parcelize
import java.util.concurrent.TimeUnit

@Parcelize
data class Media(
    val id: Long = 0,
    val label: String,
    val uri: Uri,
    val path: String,
    val albumID: Long,
    val albumLabel: String,
    val timestamp: Long,
    val duration: String? = null,
    var selected: Boolean = false
) : Parcelable {
    fun formatTime(): String {
        val timestamp = duration?.toLong() ?: return ""
        return String.format(
            "%d:%02d",
            TimeUnit.MILLISECONDS.toMinutes(timestamp),
            TimeUnit.MILLISECONDS.toSeconds(timestamp) -
                    TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(timestamp))
        )
    }
}

@Parcelize
data class Album(
    val id: Long = 0,
    val label: String,
    val pathToThumbnail: String,
    val timestamp: Long,
    var count: Long = 0,
    val selected: Boolean = false
) : Parcelable

class InvalidMediaException(message: String) : Exception(message)