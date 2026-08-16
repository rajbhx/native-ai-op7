package com.engine.nativeai

import org.json.JSONArray
import org.json.JSONObject

/**
 * A skill is lightweight instructions/workflow stored locally (master prompt
 * §11). Skills never modify executable application code.
 */
data class Skill(
    val id: String,
    val purpose: String,
    val tools: List<String> = emptyList(),
    val workflow: List<String> = emptyList(),
    val constraints: String = "",
    val verificationRules: List<String> = emptyList(),
    /** Seeded skills are read-only (they re-seed on empty); user skills are
     *  created/edited/deleted from the SKILLS panel (Phase 10). */
    val builtin: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("purpose", purpose)
        put("tools", JSONArray(tools))
        put("workflow", JSONArray(workflow))
        put("constraints", constraints)
        put("verification_rules", JSONArray(verificationRules))
        put("builtin", builtin)
    }

    companion object {
        fun fromJson(j: JSONObject): Skill = Skill(
            id = j.getString("id"),
            purpose = j.optString("purpose", ""),
            tools = j.optJSONArray("tools")?.let { arr ->
                buildList { for (i in 0 until arr.length()) add(arr.getString(i)) }
            } ?: emptyList(),
            workflow = j.optJSONArray("workflow")?.let { arr ->
                buildList { for (i in 0 until arr.length()) add(arr.getString(i)) }
            } ?: emptyList(),
            constraints = j.optString("constraints", ""),
            verificationRules = j.optJSONArray("verification_rules")?.let { arr ->
                buildList { for (i in 0 until arr.length()) add(arr.getString(i)) }
            } ?: emptyList(),
            builtin = j.optBoolean("builtin", false),
        )
    }
}
