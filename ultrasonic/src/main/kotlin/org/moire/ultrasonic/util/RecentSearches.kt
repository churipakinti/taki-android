package org.moire.ultrasonic.util

import android.content.Context
import org.json.JSONArray

class RecentSearches(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun get(): List<String> = runCatching {
        val values = JSONArray(preferences.getString(KEY_QUERIES, "[]"))
        List(values.length()) { index -> values.getString(index) }
    }.getOrDefault(emptyList())

    fun save(rawQuery: String): List<String> {
        val query = rawQuery.trim()
        if (query.isEmpty()) return get()
        val updated = buildList {
            add(query)
            addAll(get().filterNot { it.equals(query, ignoreCase = true) })
        }.take(MAX_QUERIES)
        write(updated)
        return updated
    }

    fun remove(query: String): List<String> {
        val updated = get().filterNot { it.equals(query, ignoreCase = true) }
        write(updated)
        return updated
    }

    fun clear() {
        preferences.edit().remove(KEY_QUERIES).apply()
    }

    private fun write(queries: List<String>) {
        preferences.edit().putString(KEY_QUERIES, JSONArray(queries).toString()).apply()
    }

    companion object {
        private const val PREFERENCES_NAME = "taki_recent_searches"
        private const val KEY_QUERIES = "queries"
        private const val MAX_QUERIES = 10
    }
}
