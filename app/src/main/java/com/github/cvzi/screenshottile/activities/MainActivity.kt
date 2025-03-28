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
import android.text.Html
import android.text.method.LinkMovementMethod
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.databinding.DataBindingUtil
import com.github.cvzi.screenshottile.App
import com.github.cvzi.screenshottile.BR
import com.github.cvzi.screenshottile.BuildConfig
import com.github.cvzi.screenshottile.R
import com.github.cvzi.screenshottile.ToastType
import com.github.cvzi.screenshottile.databinding.ActivityMainBinding
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
                
                // Start the auto screenshot service
                BasicForegroundService.startAutoScreenshots(this)
            } else {
                // Stop the auto screenshot service
                BasicForegroundService.stopAutoScreenshots(this)
            }
        }

        // Set up the interval editor
        val editInterval = binding.editInterval
        editInterval.setText(App.getInstance().prefManager.autoScreenshotInterval.toString())
        
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