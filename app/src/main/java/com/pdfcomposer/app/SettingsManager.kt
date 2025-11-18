package com.pdfcomposer.app

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsManager(private val context: Context) {
    
    companion object {
        private val STORAGE_LOCATION_KEY = stringPreferencesKey("storage_location")
        private val SAF_DIRECTORY_URI_KEY = stringPreferencesKey("saf_directory_uri")
        private val HAS_COMPLETED_ONBOARDING_KEY = stringPreferencesKey("has_completed_onboarding")
    }
    
    val storageLocationFlow: Flow<StorageLocation> = context.dataStore.data.map { preferences ->
        val locationString = preferences[STORAGE_LOCATION_KEY] ?: StorageLocation.SAF.name
        try {
            StorageLocation.valueOf(locationString)
        } catch (e: IllegalArgumentException) {
            StorageLocation.SAF
        }
    }
    
    val safDirectoryUriFlow: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[SAF_DIRECTORY_URI_KEY]
    }
    
    val hasCompletedOnboardingFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[HAS_COMPLETED_ONBOARDING_KEY]?.toBoolean() ?: false
    }
    
    suspend fun setStorageLocation(location: StorageLocation) {
        context.dataStore.edit { preferences ->
            preferences[STORAGE_LOCATION_KEY] = location.name
        }
    }
    
    suspend fun setSafDirectoryUri(uri: String) {
        context.dataStore.edit { preferences ->
            preferences[SAF_DIRECTORY_URI_KEY] = uri
        }
    }
    
    suspend fun setHasCompletedOnboarding(completed: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[HAS_COMPLETED_ONBOARDING_KEY] = completed.toString()
        }
    }
    
    suspend fun getSafDirectoryUri(): String? {
        var result: String? = null
        context.dataStore.data.collect { preferences ->
            result = preferences[SAF_DIRECTORY_URI_KEY]
            return@collect
        }
        return result
    }
}
