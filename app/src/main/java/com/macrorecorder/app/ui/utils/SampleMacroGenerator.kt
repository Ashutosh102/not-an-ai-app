package com.macrorecorder.app.ui.utils

import com.macrorecorder.app.data.MacroAction
import com.macrorecorder.app.data.MacroActionType
import com.macrorecorder.app.data.MacroBounds
import com.macrorecorder.app.data.MacroWorkflow
import java.util.UUID

object SampleMacroGenerator {

    /**
     * Generates a sample macro demonstrating structured actions targeting Android Settings.
     */
    fun createSettingsSearchSample(): MacroWorkflow {
        val actions = listOf(
            MacroAction(
                stepIndex = 1,
                actionType = MacroActionType.CLICK,
                packageName = "com.android.settings",
                resourceId = "com.android.settings:id/search_action_bar",
                textLabel = "Search settings",
                contentDescription = "Search settings",
                className = "android.widget.TextView",
                delayFromPreviousMs = 800L,
                fallbackBounds = MacroBounds(48, 120, 1032, 230)
            ),
            MacroAction(
                stepIndex = 2,
                actionType = MacroActionType.SET_TEXT,
                packageName = "com.android.settings",
                resourceId = "android:id/search_src_text",
                textLabel = null,
                contentDescription = null,
                className = "android.widget.EditText",
                textPayload = "Display & brightness",
                delayFromPreviousMs = 1200L,
                fallbackBounds = MacroBounds(120, 120, 960, 230)
            ),
            MacroAction(
                stepIndex = 3,
                actionType = MacroActionType.CLICK,
                packageName = "com.android.settings",
                resourceId = "android:id/title",
                textLabel = "Display",
                contentDescription = "Display settings",
                className = "android.widget.TextView",
                delayFromPreviousMs = 1500L,
                fallbackBounds = MacroBounds(48, 280, 1032, 420)
            ),
            MacroAction(
                stepIndex = 4,
                actionType = MacroActionType.SCROLL_FORWARD,
                packageName = "com.android.settings",
                resourceId = "com.android.settings:id/main_content",
                textLabel = null,
                delayFromPreviousMs = 1000L
            ),
            MacroAction(
                stepIndex = 5,
                actionType = MacroActionType.KEY_BACK,
                packageName = "com.android.settings",
                delayFromPreviousMs = 1200L
            )
        )

        return MacroWorkflow(
            id = UUID.randomUUID().toString(),
            name = "Android Settings Display Flow",
            description = "Sample workflow demonstrating search and navigation via structural IDs and ACTION_SET_TEXT",
            createdAt = System.currentTimeMillis() - 3600_000L,
            durationMs = 5700L,
            videoFilePath = null,
            actions = actions
        )
    }

    /**
     * Generates a sample macro demonstrating mathematical input on Android Calculator.
     */
    fun createCalculatorSample(): MacroWorkflow {
        val actions = listOf(
            MacroAction(
                stepIndex = 1,
                actionType = MacroActionType.CLICK,
                packageName = "com.google.android.calculator",
                resourceId = "com.google.android.calculator:id/digit_7",
                textLabel = "7",
                contentDescription = "7",
                delayFromPreviousMs = 600L,
                fallbackBounds = MacroBounds(100, 800, 300, 1000)
            ),
            MacroAction(
                stepIndex = 2,
                actionType = MacroActionType.CLICK,
                packageName = "com.google.android.calculator",
                resourceId = "com.google.android.calculator:id/op_mul",
                textLabel = "×",
                contentDescription = "multiply",
                delayFromPreviousMs = 600L,
                fallbackBounds = MacroBounds(750, 800, 950, 1000)
            ),
            MacroAction(
                stepIndex = 3,
                actionType = MacroActionType.CLICK,
                packageName = "com.google.android.calculator",
                resourceId = "com.google.android.calculator:id/digit_8",
                textLabel = "8",
                contentDescription = "8",
                delayFromPreviousMs = 600L,
                fallbackBounds = MacroBounds(400, 800, 600, 1000)
            ),
            MacroAction(
                stepIndex = 4,
                actionType = MacroActionType.CLICK,
                packageName = "com.google.android.calculator",
                resourceId = "com.google.android.calculator:id/eq",
                textLabel = "=",
                contentDescription = "equals",
                delayFromPreviousMs = 800L,
                fallbackBounds = MacroBounds(750, 1400, 950, 1600)
            )
        )

        return MacroWorkflow(
            id = UUID.randomUUID().toString(),
            name = "Calculator 7 × 8 Calculation",
            description = "Demonstrates automated dispatchGesture() sequential tapping on calculator nodes",
            createdAt = System.currentTimeMillis() - 7200_000L,
            durationMs = 2600L,
            videoFilePath = null,
            actions = actions
        )
    }
}
