package com.github.cvzi.screenshottile

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.github.cvzi.screenshottile.services.BasicForegroundService

/**
 * Broadcast receiver for the BOOT_COMPLETED intent.
 */
class BootCompletedReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "BootCompletedReceiver"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            if (BuildConfig.DEBUG) Log.v(TAG, "Received BOOT_COMPLETED")
            val prefManager = App.getInstance().prefManager
            if (prefManager.autoScreenshotEnabled && prefManager.autoScreenshotStartOnBoot) {
                if (BuildConfig.DEBUG) Log.v(TAG, "Starting auto screenshot service")
                // Start the auto screenshot service
                BasicForegroundService.startAutoScreenshots(context)
            }
        }
    }
}