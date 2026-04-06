package com.audiobalance.app.data

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class BalanceRepository(private val context: Context) {

    companion object {
        private const val TAG = "BalanceRepository"
    }

    private fun balanceKey(mac: String) = floatPreferencesKey("balance_${mac.replace(":", "_")}")
    private fun autoApplyKey(mac: String) = booleanPreferencesKey("auto_apply_${mac.replace(":", "_")}")
    private fun deviceNameKey(mac: String) = stringPreferencesKey("name_${mac.replace(":", "_")}")

    /** Returns stored balance for MAC, or 0f if unknown device */
    suspend fun getBalance(mac: String): Float {
        return context.dataStore.data
            .map { prefs -> prefs[balanceKey(mac)] ?: 0f }
            .first()
    }

    /** Save balance for a MAC address. balance is -100f to +100f */
    suspend fun saveBalance(mac: String, balance: Float) {
        context.dataStore.edit { prefs ->
            prefs[balanceKey(mac)] = balance
        }
        Log.d(TAG, "Saved balance: mac=$mac balance=$balance")
    }

    /** Flow of all known device balances (for Phase 3 UI) */
    fun getAllBalances(): Flow<Map<String, Float>> = context.dataStore.data.map { prefs ->
        prefs.asMap()
            .filterKeys { it.name.startsWith("balance_") }
            .mapKeys { (key, _) -> key.name.removePrefix("balance_").replace("_", ":") }
            .mapValues { (_, value) -> (value as? Float) ?: 0f }
    }

    suspend fun getAutoApply(mac: String): Boolean {
        return context.dataStore.data
            .map { prefs -> prefs[autoApplyKey(mac)] ?: true }  // default enabled
            .first()
    }

    suspend fun saveAutoApply(mac: String, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[autoApplyKey(mac)] = enabled
        }
        Log.d(TAG, "Saved autoApply: mac=$mac enabled=$enabled")
    }

    suspend fun saveDeviceName(mac: String, name: String) {
        context.dataStore.edit { prefs ->
            prefs[deviceNameKey(mac)] = name
        }
    }

    suspend fun getDeviceName(mac: String): String? {
        return context.dataStore.data
            .map { prefs -> prefs[deviceNameKey(mac)] }
            .first()
    }

    /** Flow of all known devices: each entry is (mac, balance, autoApply) */
    fun getAllDevicesFlow(): Flow<List<Triple<String, Float, Boolean>>> = context.dataStore.data.map { prefs ->
        prefs.asMap()
            .filterKeys { it.name.startsWith("balance_") }
            .map { (key, value) ->
                val mac = key.name.removePrefix("balance_").replace("_", ":")
                val balance = (value as? Float) ?: 0f
                val autoApply = prefs[autoApplyKey(mac)] ?: true
                Triple(mac, balance, autoApply)
            }
    }
}
