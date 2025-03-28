package com.github.cvzi.screenshottile.utils

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.github.cvzi.screenshottile.activities.TakeScreenshotActivity

/**
 * Central class to manage screenshot operations and throttling
 * across all app components to prevent infinite loops and spam
 */
object ScreenshotManager {
    private const val TAG = "ScreenshotManager"
    
    // Global screenshot state with stronger constraints
    @Volatile private var isScreenshotInProgress = false
    @Volatile private var lastScreenshotAttemptTime = 0L
    private const val MIN_SCREENSHOT_INTERVAL_MS = 5000L // Increased to 5 seconds for safety
    
    // Lock for synchronization
    private val lock = Any()
    
    // Handler for timeouts
    private val handler = Handler(Looper.getMainLooper())
    private var timeoutRunnable: Runnable? = null
    
    // Debug counter - purely for logging
    private var attemptCounter = 0
    
    init {
        Log.d(TAG, "ScreenshotManager initialized")
    }
    
    /**
     * Check if it's safe to take a screenshot now
     */
    fun canTakeScreenshotNow(): Boolean {
        synchronized(lock) {
            val now = System.currentTimeMillis()
            val timeSinceLastAttempt = now - lastScreenshotAttemptTime
            
            if (isScreenshotInProgress) {
                Log.d(TAG, "Screenshot already in progress (attempt #$attemptCounter), cannot take another")
                return false
            }
            
            if (timeSinceLastAttempt < MIN_SCREENSHOT_INTERVAL_MS) {
                Log.d(TAG, "Too soon since last screenshot attempt ($timeSinceLastAttempt ms), minimum interval is $MIN_SCREENSHOT_INTERVAL_MS ms")
                return false
            }
            
            return true
        }
    }
    
    /**
     * Attempt to take a screenshot if it's safe to do so
     * @return true if screenshot was attempted, false if throttled
     */
    fun attemptScreenshot(context: Context, partial: Boolean = false): Boolean {
        synchronized(lock) {
            if (!canTakeScreenshotNow()) {
                return false
            }
            
            // Mark screenshot as in progress and record time
            isScreenshotInProgress = true
            lastScreenshotAttemptTime = System.currentTimeMillis()
            attemptCounter++
            
            Log.d(TAG, "Starting screenshot attempt #$attemptCounter")
            
            // Set a timeout to reset the flag in case something goes wrong
            ensureTimeout()
            
            // Launch TakeScreenshotActivity DIRECTLY - avoid using NoDisplayActivity which could cause loops
            TakeScreenshotActivity.start(context, partial)
            
            return true
        }
    }
    
    /**
     * Called when a screenshot is completed (successfully or not)
     */
    fun onScreenshotComplete() {
        synchronized(lock) {
            if (isScreenshotInProgress) {
                isScreenshotInProgress = false
                cancelTimeout()
                Log.d(TAG, "Screenshot #$attemptCounter completed, status reset")
            } else {
                Log.w(TAG, "onScreenshotComplete called but no screenshot was in progress!")
            }
        }
    }
    
    /**
     * Set a timeout to reset the in-progress flag after 10 seconds
     * This prevents the app from getting stuck if something goes wrong
     */
    private fun ensureTimeout() {
        cancelTimeout()
        
        val currentAttempt = attemptCounter
        
        timeoutRunnable = Runnable {
            synchronized(lock) {
                if (isScreenshotInProgress) {
                    Log.w(TAG, "Screenshot #$currentAttempt state reset due to timeout after 10 seconds")
                    isScreenshotInProgress = false
                }
                timeoutRunnable = null
            }
        }
        
        handler.postDelayed(timeoutRunnable!!, 10000) // 10 second timeout
    }
    
    /**
     * Cancel the timeout runnable
     */
    private fun cancelTimeout() {
        timeoutRunnable?.let {
            handler.removeCallbacks(it)
            timeoutRunnable = null
        }
    }
} 