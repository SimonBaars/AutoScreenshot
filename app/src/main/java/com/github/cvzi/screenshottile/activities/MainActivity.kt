package com.github.cvzi.screenshottile.activities

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Html
import android.text.Spannable
import android.text.SpannableString
import android.text.method.LinkMovementMethod
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.databinding.DataBindingUtil
import com.github.cvzi.screenshottile.App
import com.github.cvzi.screenshottile.BR
import com.github.cvzi.screenshottile.BuildConfig
import com.github.cvzi.screenshottile.R
import com.github.cvzi.screenshottile.ToastType
import com.github.cvzi.screenshottile.activities.HistoryActivity
import com.github.cvzi.screenshottile.activities.LanguageActivity
import com.github.cvzi.screenshottile.activities.SettingsActivity
import com.github.cvzi.screenshottile.databinding.ActivityMainBinding
import com.github.cvzi.screenshottile.interfaces.OnAcquireScreenshotPermissionListener
import com.github.cvzi.screenshottile.services.BasicForegroundService
import com.github.cvzi.screenshottile.utils.formatLocalizedString
import com.github.cvzi.screenshottile.utils.getLocalizedString
import com.github.cvzi.screenshottile.utils.isNewAppInstallation
import com.github.cvzi.screenshottile.utils.makeActivityClickableFromText
import com.github.cvzi.screenshottile.utils.toastMessage
import com.google.android.material.switchmaterial.SwitchMaterial

/**
 * Launcher activity. Controls for automatic screenshots
 */
class MainActivity : BaseAppCompatActivity() {
    companion object {
        const val TAG = "MainActivity.kt"

        /**
         * Start this activity from a service
         */
        fun startNewTask(ctx: Context, args: Bundle? = null) {
            ctx.startActivity(
                Intent(ctx, MainActivity::class.java).apply {
                    putExtra(TransparentContainerActivity.EXTRA_ARGS, args)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
        }
    }

    private lateinit var binding: ActivityMainBinding
    private var askedForStoragePermission = false
    private var showHistoryReminder = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView<ActivityMainBinding>(this, R.layout.activity_main)
        binding.setVariable(BR.strings, App.texts)

        val textDescTranslate = binding.textDescGeneral
        textDescTranslate.movementMethod = LinkMovementMethod()
        textDescTranslate.text = Html.fromHtml(
            getLocalizedString(R.string.setting_auto_screenshot_summary),
            Html.FROM_HTML_SEPARATOR_LINE_BREAK_DIV
        )

        // Ensure we have the necessary screenshot permission
        App.acquireScreenshotPermission(this, object : OnAcquireScreenshotPermissionListener {
            override fun onAcquireScreenshotPermission(isNewPermission: Boolean) {
                Log.d("MainActivity", "Screenshot permission acquired: $isNewPermission")
                // After getting permission, enable auto screenshots if it's set
                if (App.getInstance().prefManager.autoScreenshotEnabled) {
                    BasicForegroundService.startAutoScreenshots(this@MainActivity)
                }
            }
        })

        toggleSwitchOnLabel(R.id.switchAutoScreenshot, R.id.textTitleAutoScreenshot)

        // Setup auto screenshot switch
        val switchAutoScreenshot = binding.switchAutoScreenshot
        switchAutoScreenshot.isChecked = App.getInstance().prefManager.autoScreenshotEnabled
        switchAutoScreenshot.setOnCheckedChangeListener { _, isChecked ->
            App.getInstance().prefManager.autoScreenshotEnabled = isChecked
            if (isChecked) {
                // Ask for storage permission if needed
                if (!askedForStoragePermission && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && packageManager.checkPermission(
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        packageName
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    askedForStoragePermission = true
                    App.requestStoragePermission(this, false)
                }
                
                // Set screenshot interval to 10 seconds
                App.getInstance().prefManager.autoScreenshotInterval = 10
                
                // Start the auto screenshot service
                BasicForegroundService.startAutoScreenshots(this)
                
                // Show a reminder about history
                binding.textHistoryReminder.visibility = View.VISIBLE
                showHistoryReminder = true
            } else {
                // Stop the auto screenshot service
                BasicForegroundService.stopAutoScreenshots(this)
                
                // Hide the history reminder
                binding.textHistoryReminder.visibility = View.GONE
                showHistoryReminder = false
            }
        }

        // Hide the interval editor as we're fixing it to 10 seconds
        binding.editInterval.visibility = View.GONE
        
        // Hide any text view related to interval setting
        val parent = binding.root as ViewGroup
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            if (child is TextView && child.text.contains("interval", ignoreCase = true)) {
                child.visibility = View.GONE
            }
        }
        
        // Set the interval to 10 seconds
        App.getInstance().prefManager.autoScreenshotInterval = 10
        
        // Set up the start on boot switch
        val switchStartOnBoot = binding.switchStartOnBoot
        switchStartOnBoot.isChecked = App.getInstance().prefManager.autoScreenshotStartOnBoot
        switchStartOnBoot.setOnCheckedChangeListener { _, isChecked ->
            App.getInstance().prefManager.autoScreenshotStartOnBoot = isChecked
        }

        // Setup buttons
        binding.buttonSettings.setOnClickListener {
            SettingsActivity.start(this)
        }
        binding.buttonSettings2.setOnClickListener {
            SettingsActivity.start(this)
        }
        binding.buttonFileSettings.setOnClickListener {
            SettingsActivity.start(this)
        }
        binding.buttonHistoryView.setOnClickListener {
            showHistoryReminder = false
            binding.textHistoryReminder.visibility = View.GONE
            startActivity(Intent(this, HistoryActivity::class.java))
        }
        binding.buttonChangeLanguage.setOnClickListener {
            LanguageActivity.start(this)
        }
        
        // Show warning if app is installed on external storage
        try {
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getApplicationInfo(
                    packageName,
                    PackageManager.ApplicationInfoFlags.of(0)
                ).flags
            } else {
                packageManager.getApplicationInfo(packageName, 0).flags
            }
            if (flags and ApplicationInfo.FLAG_EXTERNAL_STORAGE != 0) {
                toastMessage(
                    "App is installed on external storage, this can cause problems after a reboot with the automatic screenshot functionality.",
                    ToastType.ACTIVITY
                )
            }
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e(TAG, e.toString())
        }
        
        // If automatic screenshots are enabled, show the history reminder
        if (App.getInstance().prefManager.autoScreenshotEnabled) {
            binding.textHistoryReminder.visibility = View.VISIBLE
            showHistoryReminder = true
        }
    }

    private fun toggleSwitchOnLabel(switchId: Int, labelId: Int) {
        findViewById<View?>(labelId)?.let { label ->
            label.isClickable = true
            label.setOnClickListener {
                findViewById<SwitchMaterial?>(switchId)?.toggle()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        askedForStoragePermission = true // Don't ask again on resume
        
        // Update UI with current values
        binding.switchAutoScreenshot.isChecked = App.getInstance().prefManager.autoScreenshotEnabled
        binding.editInterval.setText(App.getInstance().prefManager.autoScreenshotInterval.toString())
        binding.switchStartOnBoot.isChecked = App.getInstance().prefManager.autoScreenshotStartOnBoot
        
        // Reset history reminder if we returned from the history activity
        binding.textHistoryReminder.visibility = if (showHistoryReminder) View.VISIBLE else View.GONE
    }

    override fun onPause() {
        super.onPause()
        
        // Save the interval value
        try {
            val intervalValue = binding.editInterval.text.toString().toInt()
            if (intervalValue > 0) {
                App.getInstance().prefManager.autoScreenshotInterval = intervalValue
            }
        } catch (e: NumberFormatException) {
            // If invalid, use default
            App.getInstance().prefManager.autoScreenshotInterval = 10
        }
    }

    private fun makeActivityClickable(textView: TextView, str: String) {
        textView.apply {
            text = makeActivityClickableFromText(str, this@MainActivity).builder
            movementMethod = LinkMovementMethod()
            highlightColor = Color.BLUE
        }
    }
}