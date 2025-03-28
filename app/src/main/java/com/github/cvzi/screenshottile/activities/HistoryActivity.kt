package com.github.cvzi.screenshottile.activities


import android.annotation.SuppressLint
import android.content.Intent
import android.icu.text.DateFormat
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.burhanrashid52.photoediting.EditImageActivity
import com.github.cvzi.screenshottile.App
import com.github.cvzi.screenshottile.BR
import com.github.cvzi.screenshottile.R
import com.github.cvzi.screenshottile.ToastType
import com.github.cvzi.screenshottile.databinding.ActivityHistoryBinding
import com.github.cvzi.screenshottile.databinding.ActivityMainBinding
import com.github.cvzi.screenshottile.utils.ScreenshotHistoryAdapter
import com.github.cvzi.screenshottile.utils.SingleImage
import com.github.cvzi.screenshottile.utils.cleanUpAppData
import com.github.cvzi.screenshottile.utils.compressionPreference
import com.github.cvzi.screenshottile.utils.formatLocalizedString
import com.github.cvzi.screenshottile.utils.getLocalizedString
import com.github.cvzi.screenshottile.utils.screenshot
import com.github.cvzi.screenshottile.utils.toastMessage
import java.io.File
import java.util.Date
import java.util.Locale


/**
 * View recent screenshots especially files in /Android/data folder
 */
class HistoryActivity : BaseAppCompatActivity() {
    companion object {
        const val TAG = "HistoryActivity.kt"
    }

    private lateinit var binding: ActivityHistoryBinding
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView<ActivityHistoryBinding>(this, R.layout.activity_history)
        binding.setVariable(BR.strings, App.texts)

        binding.buttonClear.setOnClickListener {
            clear()
        }

        binding.switchKeepHistory.setOnCheckedChangeListener { v, isChecked ->
            val prefManager = App.getInstance().prefManager
            if (isChecked != prefManager.keepScreenshotHistory) {
                v.setText(if (isChecked) R.string.notification_settings_on else R.string.notification_settings_off)
                prefManager.keepScreenshotHistory = isChecked
                if (!isChecked) {
                    clear()
                }
            }
        }
        
        // Set up the FAB to take a screenshot
        binding.fabTakeScreenshot.setOnClickListener {
            takeScreenshotAndRefresh()
        }
    }
    
    private fun takeScreenshotAndRefresh() {
        // First make sure history is enabled
        if (!App.getInstance().prefManager.keepScreenshotHistory) {
            App.getInstance().prefManager.keepScreenshotHistory = true
            binding.switchKeepHistory.isChecked = true
            toastMessage("Screenshot history is now enabled", ToastType.SUCCESS)
        }
        
        // Take the screenshot
        screenshot(this)
        
        // Wait a moment for the screenshot to be saved then refresh the UI
        Handler(Looper.getMainLooper()).postDelayed({
            refreshHistory()
        }, 1500)
    }
    
    @SuppressLint("NotifyDataSetChanged")
    private fun refreshHistory() {
        val data = loadImageList()
        val adapter = binding.recyclerView.adapter as? ScreenshotHistoryAdapter
        adapter?.run {
            dataSet = data
            notifyDataSetChanged()
        }
        updateStatistics(data)
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun clear() {
        val folder = getExternalFilesDir(Environment.DIRECTORY_PICTURES)?.toString()
            ?: "Android/data/com.github.cvzi.screenshottile/..."
        AlertDialog.Builder(this).apply {
            title = getLocalizedString(R.string.button_clear)
            setMessage(formatLocalizedString(R.string.button_clear_confirm, folder))
        }.setPositiveButton(android.R.string.ok) { dialog, _ ->
            dialog.dismiss()
            cleanUpAppData(this@HistoryActivity, 0) {
                val adapter =
                    binding.recyclerView.adapter as? ScreenshotHistoryAdapter
                adapter?.run {
                    dataSet = loadImageList()
                    notifyDataSetChanged()
                }
                updateStatistics(ArrayList())
            }
        }.setNegativeButton(android.R.string.cancel) { dialog, _ ->
            dialog.dismiss()
        }.show()
    }

    override fun onResume() {
        super.onResume()

        val recyclerView: RecyclerView = binding.recyclerView

        val layoutManager = LinearLayoutManager(this)
        layoutManager.orientation = LinearLayoutManager.VERTICAL
        recyclerView.layoutManager = layoutManager

        val data = loadImageList()

        val adapter = ScreenshotHistoryAdapter(this, data) { record, view ->
            Log.v(TAG, "Click on history item: $record $view")
        }
        recyclerView.adapter = adapter

        binding.switchKeepHistory.isChecked = App.getInstance().prefManager.keepScreenshotHistory
        
        // Update the statistics display
        updateStatistics(data)
    }
    
    private fun updateStatistics(data: ArrayList<SingleImage>) {
        val screenshotCount = App.getInstance().prefManager.screenshotCount
        binding.textTotalScreenshots.text = screenshotCount.toString()
        
        // Find the most recent screenshot date
        val mostRecentDate = data.mapNotNull { it.lastModified }.maxByOrNull { it }
        val dateFormat = DateFormat.getDateTimeInstance(
            DateFormat.MEDIUM,
            DateFormat.SHORT,
            Locale.getDefault()
        )
        binding.textLastScreenshotTime.text = if (mostRecentDate != null) {
            dateFormat.format(mostRecentDate)
        } else {
            "Never"
        }
        
        // Get the current file format setting
        val compressionOptions = compressionPreference(this)
        binding.textScreenshotFormat.text = compressionOptions.fileExtension.uppercase()
    }

    private fun loadImageList(): ArrayList<SingleImage> {
        // Add files from /Android/data folder
        val allFiles = HashMap<File, Boolean>()
        val allUris = HashMap<Uri, Boolean>()
        val folder = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        val fileList =
            folder?.listFiles { _, string ->
                string.endsWith("jpg", true)
                        || string.endsWith("jpeg", true)
                        || string.endsWith("png", true)
                        || string.endsWith("webp", true)
            }?.map { file ->
                SingleImage(
                    FileProvider.getUriForFile(
                        this,
                        EditImageActivity.FILE_PROVIDER_AUTHORITY,
                        file
                    ),
                    file,
                    folder.absolutePath,
                    lastModified = file?.lastModified()?.run { Date(this) },
                    isAppData = true
                )
            }?.toMutableList() ?: listOf()

        var data = ArrayList(fileList)

        // Add files from history
        for (item in App.getInstance().prefManager.screenshotHistory) {
            if (item.uri !in allUris && (item.file == null || item.file !in allFiles)) {
                data.add(SingleImage(item.uri, item.file, lastModified = item.date))
                allUris[item.uri] = true
                item.file?.let { allFiles[it] = true }
            }
        }

        // Sort by date, most recent first
        data.sortByDescending { it.lastModified }
        return data
    }
}


