package com.audiobalance.app.data

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class BalanceRepository(private val context: Context) {

    companion object {
        private const val TAG = "BalanceRepository"
    }

    private fun balanceKey(mac: String) = floatPreferencesKey("balance_${mac.replace(":", "_")}")

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
}
