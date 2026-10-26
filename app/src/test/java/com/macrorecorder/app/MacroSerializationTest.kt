package com.macrorecorder.app

import com.macrorecorder.app.data.MacroAction
import com.macrorecorder.app.data.MacroActionType
import com.macrorecorder.app.data.MacroBounds
import com.macrorecorder.app.data.MacroWorkflow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MacroSerializationTest {

    @Test
    fun testMacroWorkflowSerializationRoundTrip() {
        val originalWorkflow = MacroWorkflow(
            id = "test-workflow-uuid-1234",
            name = "Test Settings Macro",
            description = "A macro testing serialization integrity",
            createdAt = 1729938000000L,
            durationMs = 4500L,
            videoFilePath = "/sdcard/recordings/test.mp4",
            actions = listOf(
                MacroAction(
                    stepIndex = 1,
                    actionType = MacroActionType.CLICK,
                    packageName = "com.android.settings",
                    resourceId = "com.android.settings:id/search_action_bar",
                    textLabel = "Search settings",
                    contentDescription = "Search",
                    className = "android.widget.TextView",
                    delayFromPreviousMs = 800L,
                    timestamp = 1729938000800L,
                    fallbackBounds = MacroBounds(left = 48, top = 120, right = 1032, bottom = 240)
                ),
                MacroAction(
                    stepIndex = 2,
                    actionType = MacroActionType.SET_TEXT,
                    packageName = "com.android.settings",
                    resourceId = "android:id/search_src_text",
                    textLabel = null,
                    textPayload = "Network & internet",
                    delayFromPreviousMs = 1200L,
                    timestamp = 1729938002000L,
                    fallbackBounds = MacroBounds(left = 120, top = 120, right = 960, bottom = 240)
                )
            )
        )

        // Serialize to JSON
        val json = originalWorkflow.toJson()
        assertNotNull(json)
        assertTrue(json.contains("\"test-workflow-uuid-1234\""))
        assertTrue(json.contains("\"com.android.settings:id/search_action_bar\""))
        assertTrue(json.contains("\"Network & internet\""))

        // Deserialize from JSON
        val deserialized = MacroWorkflow.fromJson(json)

        assertEquals(originalWorkflow.id, deserialized.id)
        assertEquals(originalWorkflow.name, deserialized.name)
        assertEquals(originalWorkflow.durationMs, deserialized.durationMs)
        assertEquals(originalWorkflow.videoFilePath, deserialized.videoFilePath)
        assertEquals(2, deserialized.actions.size)

        val firstAction = deserialized.actions[0]
        assertEquals(MacroActionType.CLICK, firstAction.actionType)
        assertEquals("com.android.settings", firstAction.packageName)
        assertEquals("com.android.settings:id/search_action_bar", firstAction.resourceId)
        assertEquals("Search settings", firstAction.textLabel)
        assertEquals(800L, firstAction.delayFromPreviousMs)
        assertNotNull(firstAction.fallbackBounds)
        assertEquals(48, firstAction.fallbackBounds?.left)
        assertEquals(1032, firstAction.fallbackBounds?.right)

        val secondAction = deserialized.actions[1]
        assertEquals(MacroActionType.SET_TEXT, secondAction.actionType)
        assertEquals("Network & internet", secondAction.textPayload)
    }
}
