package com.github.cvzi.screenshottile.services

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.github.cvzi.screenshottile.App
import com.github.cvzi.screenshottile.BuildConfig
import com.github.cvzi.screenshottile.activities.TakeScreenshotActivity
import com.github.cvzi.screenshottile.activities.NoDisplayActivity
import com.github.cvzi.screenshottile.utils.foregroundNotification
import com.github.cvzi.screenshottile.utils.ScreenshotManager

/**
 * Foreground service for MediaProjection and automatic screenshots
 */
class BasicForegroundService : Service() {
    companion object {
        private const val TAG = "BasicForegroundService"
        private const val FOREGROUND_SERVICE_ID = 7594
        const val FOREGROUND_NOTIFICATION_ID = 8140
        private const val FOREGROUND_ON_START =
            BuildConfig.APPLICATION_ID + "BasicForegroundService.FOREGROUND_ON_START"
        private const val RESUME_SCREENSHOT =
            BuildConfig.APPLICATION_ID + "BasicForegroundService.RESUME_SCREENSHOT"
        private const val START_AUTO_SCREENSHOTS =
            BuildConfig.APPLICATION_ID + "BasicForegroundService.START_AUTO_SCREENSHOTS"
        private const val STOP_AUTO_SCREENSHOTS =
            BuildConfig.APPLICATION_ID + "BasicForegroundService.STOP_AUTO_SCREENSHOTS"
        private const val MIN_SCREENSHOT_INTERVAL_MS = 3000L // Minimum 3 seconds between screenshots
        var instance: BasicForegroundService? = null

        // Keep track of the last time a screenshot was taken
        private var lastScreenshotTime = 0L
        
        /**
         * Check if we can take a screenshot now (to prevent spam)
         */
        private fun canTakeScreenshotNow(): Boolean {
            val now = System.currentTimeMillis()
            val timeSinceLastScreenshot = now - lastScreenshotTime
            return timeSinceLastScreenshot > MIN_SCREENSHOT_INTERVAL_MS
        }

        /**
         * Start this service in the foreground
         */
        fun startForegroundService(context: Context): ComponentName? {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                return null
            }
            val serviceIntent = Intent(context, BasicForegroundService::class.java)
            serviceIntent.action = FOREGROUND_ON_START
            return context.startForegroundService(serviceIntent)
        }

        /**
         * Start this service in the foreground, request screenshot permission with a
         * callback to TakeScreenshotActivity
         */
        fun resumeScreenshot(context: Context): ComponentName? {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                return null
            }
            val serviceIntent = Intent(context, BasicForegroundService::class.java)
            serviceIntent.action = RESUME_SCREENSHOT
            return context.startForegroundService(serviceIntent)
        }

        /**
         * Start automatic screenshots
         */
        fun startAutoScreenshots(context: Context): ComponentName? {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                return null
            }
            val serviceIntent = Intent(context, BasicForegroundService::class.java)
            serviceIntent.action = START_AUTO_SCREENSHOTS
            return context.startForegroundService(serviceIntent)
        }

        /**
         * Stop automatic screenshots
         */
        fun stopAutoScreenshots(context: Context): ComponentName? {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                return null
            }
            val serviceIntent = Intent(context, BasicForegroundService::class.java)
            serviceIntent.action = STOP_AUTO_SCREENSHOTS
            return context.startForegroundService(serviceIntent)
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var isAutoScreenshotRunning = false
    private var autoScreenshotRunnable: Runnable? = null

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        instance = this
        when(intent?.action) {
            FOREGROUND_ON_START -> {
                foreground()
            }
            RESUME_SCREENSHOT -> {
                foreground()
                TakeScreenshotActivity.instance?.let {
                    App.acquireScreenshotPermission(this, it)
                }
            }
            START_AUTO_SCREENSHOTS -> {
                foreground()
                startAutoScreenshotLoop()
            }
            STOP_AUTO_SCREENSHOTS -> {
                stopAutoScreenshotLoop()
                if (!isAutoScreenshotRunning) {
                    background()
                }
            }
        }

        return START_STICKY
    }

    /**
     * Start foreground with sticky notification, necessary for MediaProjection
     */
    fun foreground() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return
        }

        startForeground(
            FOREGROUND_SERVICE_ID,
            foregroundNotification(this, FOREGROUND_NOTIFICATION_ID).build(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        )
    }

    /**
     * Stop foreground and remove sticky notification
     */
    fun background() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    /**
     * Start the automatic screenshot loop
     */
    private fun startAutoScreenshotLoop() {
        if (isAutoScreenshotRunning) {
            Log.d(TAG, "startAutoScreenshotLoop: already running")
            return
        }

        if (App.getScreenshotPermission() == null) {
            // Ask for permission first
            Log.d(TAG, "startAutoScreenshotLoop: no permission, requesting...")
            App.acquireScreenshotPermission(this, object : com.github.cvzi.screenshottile.interfaces.OnAcquireScreenshotPermissionListener {
                override fun onAcquireScreenshotPermission(isNewPermission: Boolean) {
                    // Now that we have permission, start the loop
                    Log.d(TAG, "onAcquireScreenshotPermission: got permission, starting loop")
                    startAutoScreenshotLoopInternal()
                }
            })
        } else {
            Log.d(TAG, "startAutoScreenshotLoop: have permission, starting loop directly")
            startAutoScreenshotLoopInternal()
        }
    }

    private fun startAutoScreenshotLoopInternal() {
        if (isAutoScreenshotRunning) {
            Log.d(TAG, "startAutoScreenshotLoopInternal: already running")
            return
        }
        
        Log.d(TAG, "startAutoScreenshotLoopInternal: starting auto screenshot loop")
        isAutoScreenshotRunning = true
        
        // Create and start the runnable that takes screenshots at intervals
        autoScreenshotRunnable = object : Runnable {
            override fun run() {
                if (!isAutoScreenshotRunning) {
                    Log.d(TAG, "autoScreenshotRunnable: stopped")
                    return
                }
                
                try {
                    // Check service-level throttling 
                    val now = System.currentTimeMillis()
                    val timeSinceLastScreenshot = now - lastScreenshotTime
                    
                    if (timeSinceLastScreenshot < MIN_SCREENSHOT_INTERVAL_MS) {
                        Log.d(TAG, "Auto screenshot: skipping - too soon ($timeSinceLastScreenshot ms < $MIN_SCREENSHOT_INTERVAL_MS ms)")
                    } else {
                        Log.d(TAG, "Auto screenshot: attempting to take screenshot")
                        
                        // Let the ScreenshotManager decide if we can take a screenshot
                        if (ScreenshotManager.attemptScreenshot(this@BasicForegroundService, false)) {
                            lastScreenshotTime = now
                            Log.d(TAG, "Auto screenshot: successfully initiated")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Auto screenshot: error taking screenshot", e)
                } finally {
                    // Always schedule the next screenshot based on the interval
                    val intervalSeconds = App.getInstance().prefManager.autoScreenshotInterval
                    Log.d(TAG, "Auto screenshot: scheduling next attempt in $intervalSeconds seconds")
                    handler.postDelayed(this, intervalSeconds * 1000L)
                }
            }
        }
        
        // Start the loop immediately
        Log.d(TAG, "startAutoScreenshotLoopInternal: posting first runnable")
        autoScreenshotRunnable?.let { handler.post(it) }
    }

    /**
     * Stop the automatic screenshot loop
     */
    private fun stopAutoScreenshotLoop() {
        isAutoScreenshotRunning = false
        autoScreenshotRunnable?.let { handler.removeCallbacks(it) }
        autoScreenshotRunnable = null
    }

    override fun onDestroy() {
        stopAutoScreenshotLoop()
        instance = null
        super.onDestroy()
    }
}