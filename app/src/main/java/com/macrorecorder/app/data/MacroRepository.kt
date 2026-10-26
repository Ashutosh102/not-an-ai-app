package com.macrorecorder.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

class MacroRepository(private val context: Context) {

    private val macrosDir: File by lazy {
        File(context.filesDir, "macros").apply {
            if (!exists()) mkdirs()
        }
    }

    val recordingsDir: File by lazy {
        val dir = context.getExternalFilesDir("recordings") ?: File(context.filesDir, "recordings")
        if (!dir.exists()) dir.mkdirs()
        dir
    }

    private val _macrosFlow = MutableStateFlow<List<MacroWorkflow>>(emptyList())
    val macrosFlow: StateFlow<List<MacroWorkflow>> = _macrosFlow.asStateFlow()

    init {
        refreshMacros()
    }

    fun refreshMacros() {
        val loaded = loadAllMacros()
        _macrosFlow.value = loaded
    }

    fun loadAllMacros(): List<MacroWorkflow> {
        val files = macrosDir.listFiles { file -> file.extension == "json" } ?: return emptyList()
        val list = mutableListOf<MacroWorkflow>()
        for (file in files) {
            try {
                val json = file.readText()
                val workflow = MacroWorkflow.fromJson(json)
                list.add(workflow)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return list.sortedByDescending { it.createdAt }
    }

    suspend fun saveMacro(workflow: MacroWorkflow): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(macrosDir, "macro_${workflow.id}.json")
            val json = workflow.toJson()
            file.writeText(json)
            refreshMacros()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun deleteMacro(id: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(macrosDir, "macro_${id}.json")
            val workflow = if (file.exists()) {
                try { MacroWorkflow.fromJson(file.readText()) } catch (e: Exception) { null }
            } else null

            // Delete JSON file
            val deletedJson = file.delete()

            // Delete associated video file if it exists
            workflow?.videoFilePath?.let { videoPath ->
                val videoFile = File(videoPath)
                if (videoFile.exists()) {
                    videoFile.delete()
                }
            }

            refreshMacros()
            deletedJson
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun getMacroById(id: String): MacroWorkflow? {
        val file = File(macrosDir, "macro_${id}.json")
        if (!file.exists()) return null
        return try {
            MacroWorkflow.fromJson(file.readText())
        } catch (e: Exception) {
            null
        }
    }

    fun createNewVideoFile(macroId: String): File {
        return File(recordingsDir, "recording_${macroId}.mp4")
    }

    companion object {
        @Volatile
        private var instance: MacroRepository? = null

        fun getInstance(context: Context): MacroRepository {
            return instance ?: synchronized(this) {
                instance ?: MacroRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}
