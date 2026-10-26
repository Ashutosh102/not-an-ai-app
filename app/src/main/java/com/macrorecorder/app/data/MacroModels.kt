package com.macrorecorder.app.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Type of action performed during a recorded workflow.
 */
@Serializable
enum class MacroActionType {
    CLICK,
    SET_TEXT,
    SCROLL_FORWARD,
    SCROLL_BACKWARD,
    KEY_BACK,
    KEY_HOME
}

/**
 * Fallback screen boundaries for gesture dispatching.
 */
@Serializable
data class MacroBounds(
    val left: Int = 0,
    val top: Int = 0,
    val right: Int = 0,
    val bottom: Int = 0
) {
    val centerX: Int get() = (left + right) / 2
    val centerY: Int get() = (top + bottom) / 2
    val width: Int get() = (right - left).coerceAtLeast(0)
    val height: Int get() = (bottom - top).coerceAtLeast(0)

    fun toJsonObject(): JSONObject = JSONObject().apply {
        put("left", left)
        put("top", top)
        put("right", right)
        put("bottom", bottom)
    }

    companion object {
        fun fromJsonObject(obj: JSONObject?): MacroBounds? {
            if (obj == null) return null
            return MacroBounds(
                left = obj.optInt("left", 0),
                top = obj.optInt("top", 0),
                right = obj.optInt("right", 0),
                bottom = obj.optInt("bottom", 0)
            )
        }
    }
}

/**
 * Represents a single structurally recorded action in a workflow sequence.
 */
@Serializable
data class MacroAction(
    val stepIndex: Int,
    val actionType: MacroActionType,
    val packageName: String,
    val resourceId: String? = null,
    val textLabel: String? = null,
    val contentDescription: String? = null,
    val className: String? = null,
    val textPayload: String? = null,
    val delayFromPreviousMs: Long = 500L,
    val timestamp: Long = System.currentTimeMillis(),
    val fallbackBounds: MacroBounds? = null
) {
    fun toJsonObject(): JSONObject = JSONObject().apply {
        put("stepIndex", stepIndex)
        put("actionType", actionType.name)
        put("packageName", packageName)
        put("resourceId", resourceId ?: JSONObject.NULL)
        put("textLabel", textLabel ?: JSONObject.NULL)
        put("contentDescription", contentDescription ?: JSONObject.NULL)
        put("className", className ?: JSONObject.NULL)
        put("textPayload", textPayload ?: JSONObject.NULL)
        put("delayFromPreviousMs", delayFromPreviousMs)
        put("timestamp", timestamp)
        put("fallbackBounds", fallbackBounds?.toJsonObject() ?: JSONObject.NULL)
    }

    companion object {
        fun fromJsonObject(obj: JSONObject): MacroAction {
            return MacroAction(
                stepIndex = obj.getInt("stepIndex"),
                actionType = MacroActionType.valueOf(obj.getString("actionType")),
                packageName = obj.optString("packageName", ""),
                resourceId = if (obj.isNull("resourceId")) null else obj.optString("resourceId"),
                textLabel = if (obj.isNull("textLabel")) null else obj.optString("textLabel"),
                contentDescription = if (obj.isNull("contentDescription")) null else obj.optString("contentDescription"),
                className = if (obj.isNull("className")) null else obj.optString("className"),
                textPayload = if (obj.isNull("textPayload")) null else obj.optString("textPayload"),
                delayFromPreviousMs = obj.optLong("delayFromPreviousMs", 500L),
                timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                fallbackBounds = if (obj.isNull("fallbackBounds")) null else MacroBounds.fromJsonObject(obj.optJSONObject("fallbackBounds"))
            )
        }
    }
}

/**
 * A full macro workflow containing the sequence of structural actions and screen recording reference.
 */
@Serializable
data class MacroWorkflow(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val durationMs: Long = 0L,
    val videoFilePath: String? = null,
    val actions: List<MacroAction> = emptyList()
) {
    /**
     * Serializes this workflow into an indented JSON string.
     */
    fun toJson(indentSpaces: Int = 2): String {
        return try {
            val jsonConfig = Json {
                prettyPrint = true
                ignoreUnknownKeys = true
                encodeDefaults = true
            }
            jsonConfig.encodeToString(this)
        } catch (e: Exception) {
            toJsonObject().toString(indentSpaces)
        }
    }

    fun toJsonObject(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("description", description)
        put("createdAt", createdAt)
        put("durationMs", durationMs)
        put("videoFilePath", videoFilePath ?: JSONObject.NULL)
        val actionsArray = JSONArray()
        actions.forEach { action ->
            actionsArray.put(action.toJsonObject())
        }
        put("actions", actionsArray)
    }

    companion object {
        fun fromJson(jsonString: String): MacroWorkflow {
            return try {
                val jsonConfig = Json {
                    ignoreUnknownKeys = true
                }
                jsonConfig.decodeFromString(serializer(), jsonString)
            } catch (e: Exception) {
                fromJsonObject(JSONObject(jsonString))
            }
        }

        fun fromJsonObject(obj: JSONObject): MacroWorkflow {
            val actionsList = mutableListOf<MacroAction>()
            val actionsArray = obj.optJSONArray("actions")
            if (actionsArray != null) {
                for (i in 0 until actionsArray.length()) {
                    val actionObj = actionsArray.getJSONObject(i)
                    actionsList.add(MacroAction.fromJsonObject(actionObj))
                }
            }

            return MacroWorkflow(
                id = obj.optString("id", UUID.randomUUID().toString()),
                name = obj.optString("name", "Unnamed Macro"),
                description = obj.optString("description", ""),
                createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                durationMs = obj.optLong("durationMs", 0L),
                videoFilePath = if (obj.isNull("videoFilePath")) null else obj.optString("videoFilePath"),
                actions = actionsList
            )
        }
    }
}
