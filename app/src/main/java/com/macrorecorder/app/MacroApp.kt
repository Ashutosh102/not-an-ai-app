package com.macrorecorder.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.macrorecorder.app.data.MacroRepository
import com.macrorecorder.app.ui.utils.SampleMacroGenerator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MacroApp : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        preloadDefaultMacrosIfEmpty()
    }

    private fun preloadDefaultMacrosIfEmpty() {
        CoroutineScope(Dispatchers.IO).launch {
            val repository = MacroRepository.getInstance(this@MacroApp)
            if (repository.loadAllMacros().isEmpty()) {
                repository.saveMacro(SampleMacroGenerator.createSettingsSearchSample())
                repository.saveMacro(SampleMacroGenerator.createCalculatorSample())
            }
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "screen_record_channel",
                getString(R.string.notification_channel_record_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_record_desc)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }
}
