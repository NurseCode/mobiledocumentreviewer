package com.pdfcomposer.app

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

enum class ImageQuality { HIGH, MEDIUM, LOW }
enum class AppTheme { LIGHT, DARK, SYSTEM }
enum class SortOrder { NEWEST_FIRST, OLDEST_FIRST, NAME_A_Z, NAME_Z_A }

class SettingsManager(private val context: Context) {
    
    companion object {
        private val STORAGE_LOCATION_KEY = stringPreferencesKey("storage_location")
        private val SAF_DIRECTORY_URI_KEY = stringPreferencesKey("saf_directory_uri")
        private val HAS_COMPLETED_ONBOARDING_KEY = stringPreferencesKey("has_completed_onboarding")
        private val DEFAULT_CATEGORY_KEY = stringPreferencesKey("default_category")
        private val IMAGE_QUALITY_KEY = stringPreferencesKey("image_quality")
        private val OCR_LANGUAGE_KEY = stringPreferencesKey("ocr_language")
        private val AUTO_NAMING_KEY = booleanPreferencesKey("auto_naming")
        private val THEME_KEY = stringPreferencesKey("theme")
        private val SORT_ORDER_KEY = stringPreferencesKey("sort_order")
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
    
    val defaultCategoryFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[DEFAULT_CATEGORY_KEY] ?: ""
    }
    
    val imageQualityFlow: Flow<ImageQuality> = context.dataStore.data.map { preferences ->
        val qualityString = preferences[IMAGE_QUALITY_KEY] ?: ImageQuality.HIGH.name
        try {
            ImageQuality.valueOf(qualityString)
        } catch (e: IllegalArgumentException) {
            ImageQuality.HIGH
        }
    }
    
    val ocrLanguageFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[OCR_LANGUAGE_KEY] ?: "en"
    }
    
    val autoNamingFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[AUTO_NAMING_KEY] ?: true
    }
    
    val themeFlow: Flow<AppTheme> = context.dataStore.data.map { preferences ->
        val themeString = preferences[THEME_KEY] ?: AppTheme.SYSTEM.name
        try {
            AppTheme.valueOf(themeString)
        } catch (e: IllegalArgumentException) {
            AppTheme.SYSTEM
        }
    }
    
    val sortOrderFlow: Flow<SortOrder> = context.dataStore.data.map { preferences ->
        val sortString = preferences[SORT_ORDER_KEY] ?: SortOrder.NEWEST_FIRST.name
        try {
            SortOrder.valueOf(sortString)
        } catch (e: IllegalArgumentException) {
            SortOrder.NEWEST_FIRST
        }
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
    
    suspend fun setDefaultCategory(category: String) {
        context.dataStore.edit { preferences ->
            preferences[DEFAULT_CATEGORY_KEY] = category
        }
    }
    
    suspend fun setImageQuality(quality: ImageQuality) {
        context.dataStore.edit { preferences ->
            preferences[IMAGE_QUALITY_KEY] = quality.name
        }
    }
    
    suspend fun setOcrLanguage(language: String) {
        context.dataStore.edit { preferences ->
            preferences[OCR_LANGUAGE_KEY] = language
        }
    }
    
    suspend fun setAutoNaming(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[AUTO_NAMING_KEY] = enabled
        }
    }
    
    suspend fun setTheme(theme: AppTheme) {
        context.dataStore.edit { preferences ->
            preferences[THEME_KEY] = theme.name
        }
    }
    
    suspend fun setSortOrder(order: SortOrder) {
        context.dataStore.edit { preferences ->
            preferences[SORT_ORDER_KEY] = order.name
        }
    }
    
    suspend fun getSafDirectoryUri(): String? {
        val preferences = context.dataStore.data.first()
        return preferences[SAF_DIRECTORY_URI_KEY]
    }
}
