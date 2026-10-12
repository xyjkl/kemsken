package com.example.core.security

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import java.security.MessageDigest

private val Context.dataStore by preferencesDataStore(name = "kemsken_security")

class AppLockManager(private val context: Context) {

    private val KEY_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
    private val KEY_PIN_HASH = stringPreferencesKey("pin_hash")
    private val KEY_BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")

    private val _isUnlocked = MutableStateFlow(false)
    val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()

    val isLockEnabledFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_LOCK_ENABLED] ?: false
    }

    val isBiometricEnabledFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_BIOMETRIC_ENABLED] ?: false
    }

    fun unlock() {
        _isUnlocked.value = true
    }

    fun lock() {
        _isUnlocked.value = false
    }

    suspend fun setPin(pin: String, enableBiometric: Boolean = true) {
        val hash = hashPin(pin)
        context.dataStore.edit { prefs ->
            prefs[KEY_LOCK_ENABLED] = true
            prefs[KEY_PIN_HASH] = hash
            prefs[KEY_BIOMETRIC_ENABLED] = enableBiometric
        }
        _isUnlocked.value = true
    }

    suspend fun removeLock() {
        context.dataStore.edit { prefs ->
            prefs[KEY_LOCK_ENABLED] = false
            prefs.remove(KEY_PIN_HASH)
            prefs[KEY_BIOMETRIC_ENABLED] = false
        }
        _isUnlocked.value = true
    }

    suspend fun verifyPin(pin: String): Boolean {
        var isValid = false
        context.dataStore.data.collect { prefs ->
            val storedHash = prefs[KEY_PIN_HASH]
            isValid = storedHash != null && storedHash == hashPin(pin)
            return@collect
        }
        if (isValid) {
            _isUnlocked.value = true
        }
        return isValid
    }

    private fun hashPin(pin: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(("kemsken_salt_" + pin).toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
