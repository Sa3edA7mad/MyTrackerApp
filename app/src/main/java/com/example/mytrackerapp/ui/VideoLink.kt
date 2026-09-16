package com.example.mytrackerapp.ui

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri

/**
 * Opens a form video externally.
 *
 * Wrapped in runCatching because a device with no browser and no YouTube app would
 * otherwise throw ActivityNotFoundException straight out of a tap handler.
 *
 * @return true if something handled the intent.
 */
fun openVideo(context: Context, url: String): Boolean = runCatching {
    context.startActivity(
        Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
    true
}.getOrDefault(false)
