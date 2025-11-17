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
    }
    
    val storageLocationFlow: Flow<StorageLocation> = context.dataStore.data.map { preferences ->
        val locationString = preferences[STORAGE_LOCATION_KEY] ?: StorageLocation.SAF.name
        try {
            StorageLocation.valueOf(locationString)
        } catch (e: IllegalArgumentException) {
            StorageLocation.SAF
        }
    }
    
    suspend fun setStorageLocation(location: StorageLocation) {
        context.dataStore.edit { preferences ->
            preferences[STORAGE_LOCATION_KEY] = location.name
        }
    }
}
