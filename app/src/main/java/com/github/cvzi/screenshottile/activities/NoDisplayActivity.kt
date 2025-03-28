package com.github.cvzi.screenshottile.activities

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import com.github.cvzi.screenshottile.App
import com.github.cvzi.screenshottile.BuildConfig
import com.github.cvzi.screenshottile.NOTIFICATION_PREFIX
import com.github.cvzi.screenshottile.NotificationActionReceiver
import com.github.cvzi.screenshottile.R
import com.github.cvzi.screenshottile.ToastType
import com.github.cvzi.screenshottile.services.BasicForegroundService
import com.github.cvzi.screenshottile.services.BasicForegroundService.Companion.startForegroundService
import com.github.cvzi.screenshottile.services.ScreenshotAccessibilityService
import com.github.cvzi.screenshottile.services.ScreenshotAccessibilityService.Companion.openAccessibilitySettings
import com.github.cvzi.screenshottile.services.ScreenshotTileService
import com.github.cvzi.screenshottile.utils.screenshot
import com.github.cvzi.screenshottile.utils.screenshotLegacyOnly
import com.github.cvzi.screenshottile.utils.toastMessage
import com.github.cvzi.screenshottile.utils.ScreenshotManager

/**
 * Empty activity that is used to collapse the quick settings panel, finishes itself in onCreate
 */
class NoDisplayActivity : BaseActivity() {
    override fun onNewIntent(intent: Intent) {
        /* If the activity is already open, we need to update the intent,
        otherwise getIntent() returns the old intent in onCreate() */
        setIntent(intent)
        super.onNewIntent(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val intent = intent
        if (intent != null) {
            val action = intent.action
            if (action != null && action.startsWith(NOTIFICATION_PREFIX)) {
                // Trampoline for notification buttons, since BroadcastReceiver can't
                // open activities anymore in Android Tiramisu
                NotificationActionReceiver.handleIntent(this, intent, TAG)
            } else if (intent.getBooleanExtra(EXTRA_PARTIAL, false)) {
                // make sure that a foreground service runs
                val screenshotTileService = ScreenshotTileService.instance
                val basicForegroundService = BasicForegroundService.instance
                if (basicForegroundService != null) {
                    basicForegroundService.foreground()
                } else if (screenshotTileService != null) {
                    screenshotTileService.foreground()
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForegroundService(this)
                }
                
                // Directly launch screenshot via ScreenshotManager to prevent loops
                ScreenshotManager.attemptScreenshot(this, true)
            } else if (intent.getBooleanExtra(
                    EXTRA_SCREENSHOT,
                    false
                ) || action != null && action == EXTRA_SCREENSHOT || intent.getBooleanExtra(
                    EXTRA_LEGACY, false
                )
            ) {
                if (Build.VERSION.SDK_INT < 34) { // UPSIDE_DOWN_CAKE is API 34
                    // make sure that a foreground service runs
                    /*
                    On Android U/14 we need to wait until we have the screenshot
                    permission before we can start the foreground service
                     */
                    val screenshotTileService = ScreenshotTileService.instance
                    val basicForegroundService = BasicForegroundService.instance
                    if (basicForegroundService != null) {
                        basicForegroundService.foreground()
                    } else if (screenshotTileService != null) {
                        screenshotTileService.foreground()
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        startForegroundService(this)
                    }
                }
                
                // Take the screenshot directly via ScreenshotManager
                val isLegacy = intent.getBooleanExtra(EXTRA_LEGACY, false)
                if (isLegacy) {
                    // For legacy screenshots, we directly use TakeScreenshotActivity
                    ScreenshotManager.attemptScreenshot(this, false)
                } else {
                    // Normal screenshots
                    ScreenshotManager.attemptScreenshot(this, false)
                }
            } else if (action != null && action == EXTRA_FLOATING_BUTTON || intent.getBooleanExtra(
                    EXTRA_FLOATING_BUTTON, false
                )
            ) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    // Toggle floating button from shortcuts.xml
                    val screenshotAccessibilityService = ScreenshotAccessibilityService.instance
                    if (App.getInstance().prefManager.floatingButton) {
                        if (screenshotAccessibilityService != null) {
                            App.getInstance().prefManager.floatingButton = false
                            screenshotAccessibilityService.updateFloatingButton(false)
                        } else {
                            openAccessibilitySettings(this, TAG)
                        }
                    } else {
                        App.getInstance().prefManager.floatingButton = true
                        if (screenshotAccessibilityService != null) {
                            screenshotAccessibilityService.updateFloatingButton(false)
                        } else {
                            openAccessibilitySettings(this, TAG)
                        }
                    }
                } else {
                    this.toastMessage(
                        R.string.setting_floating_button_unsupported,
                        ToastType.ERROR,
                        Toast.LENGTH_LONG
                    )
                }
            } else if (action == EXTRA_HIDE_QUICK_SETTINGS_PANEL || intent.getBooleanExtra(
                    EXTRA_FLOATING_BUTTON,
                    false
                )
            ) {
                // no-op
            } else {
                if (BuildConfig.DEBUG) Log.v(TAG, "onCreate() no valid action or EXTRA_* found")
            }
        }
        finish()
    }

    companion object {
        const val TAG = "NoDisplayActivity"
        private const val EXTRA_SCREENSHOT =
            BuildConfig.APPLICATION_ID + ".NoDisplayActivity.EXTRA_SCREENSHOT"
        private const val EXTRA_LEGACY =
            BuildConfig.APPLICATION_ID + ".NoDisplayActivity.EXTRA_LEGACY"
        private const val EXTRA_PARTIAL =
            BuildConfig.APPLICATION_ID + ".NoDisplayActivity.EXTRA_PARTIAL"
        private const val EXTRA_FLOATING_BUTTON =
            BuildConfig.APPLICATION_ID + ".NoDisplayActivity.EXTRA_FLOATING_BUTTON"
        const val EXTRA_HIDE_QUICK_SETTINGS_PANEL =
            BuildConfig.APPLICATION_ID + ".NoDisplayActivity.EXTRA_HIDE_QUICK_SETTINGS_PANEL"

        // Prevent multiple instances from launching too quickly
        private var lastLaunchTime = 0L
        private const val MIN_LAUNCH_INTERVAL_MS = 2000L
        private var isActivityInProgress = false
        
        /**
         * Check if we can launch a new instance of NoDisplayActivity
         */
        private fun canLaunchNow(): Boolean {
            val now = System.currentTimeMillis()
            return !isActivityInProgress && (now - lastLaunchTime) >= MIN_LAUNCH_INTERVAL_MS
        }
        
        /**
         * Launch activity with throttling
         */
        private fun startActivityThrottled(context: Context, intent: Intent) {
            if (!canLaunchNow()) {
                Log.d(TAG, "Skipping activity launch - another one is in progress or too recent")
                return
            }
            
            lastLaunchTime = System.currentTimeMillis()
            isActivityInProgress = true
            
            // Add FLAG_ACTIVITY_NEW_TASK if not called from an Activity
            if (context !is Activity) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            context.startActivity(intent)
        }

        /**
         * Open from service
         *
         * @param context    Context
         * @param screenshot Immediately start taking a screenshot
         */
        fun startNewTask(context: Context, screenshot: Boolean) {
            val intent = newIntent(context, screenshot)
            startActivityThrottled(context, intent)
        }

        /**
         * Open from service
         *
         * @param context Context
         */
        fun startNewTaskPartial(context: Context) {
            val intent = newPartialIntent(context)
            startActivityThrottled(context, intent)
        }

        /**
         * Open from service, take screenshot with legacy method
         *
         * @param context Context
         */
        fun startNewTaskLegacyScreenshot(context: Context) {
            val intent = newLegacyIntent(context)
            startActivityThrottled(context, intent)
        }

        /**
         * New Intent that takes a screenshot immediately if screenshot is true.
         *
         * @param context    Context
         * @param screenshot Immediately start taking a screenshot
         * @return The intent
         */
        @JvmStatic
        fun newIntent(context: Context?, screenshot: Boolean): Intent {
            val intent = Intent(context, NoDisplayActivity::class.java)
            intent.putExtra(EXTRA_SCREENSHOT, screenshot)
            if (screenshot) {
                intent.action = EXTRA_SCREENSHOT
            }
            return intent
        }

        /**
         * New Intent that opens the partial screenshot selector.
         *
         * @param context Context
         * @return The intent
         */
        @JvmStatic
        fun newPartialIntent(context: Context?): Intent {
            val intent = Intent(context, NoDisplayActivity::class.java)
            intent.putExtra(EXTRA_PARTIAL, true)
            return intent
        }

        /**
         * New Intent that takes a screenshot with legacy method
         *
         * @param context Context
         * @return The intent
         */
        fun newLegacyIntent(context: Context?): Intent {
            val intent = Intent(context, NoDisplayActivity::class.java)
            intent.putExtra(EXTRA_LEGACY, true)
            return intent
        }

        /**
         * New Intent that toggles the floating button
         *
         * @param context Context
         * @return The intent
         */
        fun newFloatingButtonIntent(context: Context?): Intent {
            val intent = Intent(context, NoDisplayActivity::class.java)
            intent.putExtra(EXTRA_FLOATING_BUTTON, true)
            return intent
        }
    }
    
    override fun finish() {
        isActivityInProgress = false
        super.finish()
    }
}