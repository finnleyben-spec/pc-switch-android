package dev.finnley.pcswitch.state

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.switchDataStore by preferencesDataStore(name = "switches")

class SwitchManager(private val context: Context) {

    private val store = context.switchDataStore
    private val switchesKey = stringPreferencesKey("switches")

    /** Persisted JSON array of switch configs. */
    fun switchesFlow(): Flow<List<SwitchConfig>> = store.data.map { prefs ->
        val json = prefs[switchesKey] ?: "[]"
        parseSwitches(json)
    }

    suspend fun getSwitches(): List<SwitchConfig> {
        val prefs = store.data.first()
        val json = prefs[switchesKey] ?: "[]"
        return parseSwitches(json)
    }

    suspend fun saveSwitches(switches: List<SwitchConfig>) {
        store.edit { prefs ->
            prefs[switchesKey] = serializeSwitches(switches)
        }
    }

    /** Add a new switch. Returns its id. */
    suspend fun addSwitch(name: String, type: String, primaryEntity: String, secondaryEntity: String = ""): String {
        val switches = getSwitches().toMutableList()
        val id = "switch_${System.currentTimeMillis()}"
        switches.add(SwitchConfig.newSwitch(id, name, type, primaryEntity, secondaryEntity))
        saveSwitches(switches)
        return id
    }

    suspend fun updateSwitch(switchConfig: SwitchConfig) {
        val switches = getSwitches().toMutableList()
        val idx = switches.indexOfFirst { it.id == switchConfig.id }
        if (idx >= 0) {
            switches[idx] = switchConfig
            saveSwitches(switches)
        }
    }

    suspend fun removeSwitch(id: String) {
        val switches = getSwitches().toMutableList()
        val filtered = switches.filter { it.id != id }
        saveSwitches(filtered)
    }

    /** Reorder switches (move from oldIndex to newIndex). */
    suspend fun reorderSwitches(fromIndex: Int, toIndex: Int) {
        val switches = getSwitches().toMutableList()
        if (fromIndex < 0 || fromIndex >= switches.size || toIndex < 0 || toIndex >= switches.size) return
        val item = switches.removeAt(fromIndex)
        switches.add(toIndex, item)
        saveSwitches(switches)
    }

    private fun parseSwitches(json: String): List<SwitchConfig> {
        return try {
            val arr = JSONArray(json)
            val result = mutableListOf<SwitchConfig>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                result.add(SwitchConfig(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    type = obj.optString("type", "dual"),
                    primaryEntity = obj.getString("primaryEntity"),
                    secondaryEntity = obj.optString("secondaryEntity", "")
                ))
            }
            result
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun serializeSwitches(switches: List<SwitchConfig>): String {
        val arr = JSONArray()
        for (sw in switches) {
            val obj = JSONObject()
            obj.put("id", sw.id)
            obj.put("name", sw.name)
            obj.put("type", sw.type)
            obj.put("primaryEntity", sw.primaryEntity)
            obj.put("secondaryEntity", sw.secondaryEntity)
            arr.put(obj)
        }
        return arr.toString()
    }
}