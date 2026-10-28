package com.betterrail.widget.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import com.betterrail.widget.data.WidgetData
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * Modern DataStore-based widget preferences repository
 * Replaces SharedPreferences with type-safe, coroutine-friendly DataStore
 */
@Singleton
class ModernWidgetPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "ModernWidgetPreferencesRepository"
        private const val DATASTORE_NAME = "widget_preferences"
        
        // DataStore extension
        private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = DATASTORE_NAME)
        
        // Preference keys
        private fun originIdKey(widgetId: Int) = stringPreferencesKey("origin_id_$widgetId")
        private fun destinationIdKey(widgetId: Int) = stringPreferencesKey("destination_id_$widgetId")
        private fun originNameKey(widgetId: Int) = stringPreferencesKey("origin_name_$widgetId")
        private fun destinationNameKey(widgetId: Int) = stringPreferencesKey("destination_name_$widgetId")
        private fun labelKey(widgetId: Int) = stringPreferencesKey("label_$widgetId")
        private fun allowRouteReversalKey(widgetId: Int) = booleanPreferencesKey("allow_route_reversal_$widgetId")
        private fun autoReverseRouteKey(widgetId: Int) = booleanPreferencesKey("auto_reverse_route_$widgetId")
        private fun manualOverrideUntilKey(widgetId: Int) = longPreferencesKey("manual_override_until_$widgetId")
        private fun maxChangesKey(widgetId: Int) = intPreferencesKey("max_changes_$widgetId")
        private fun commuteEnabledKey(widgetId: Int) = booleanPreferencesKey("commute_enabled_$widgetId")
        private fun homeStationIdKey(widgetId: Int) = stringPreferencesKey("home_station_id_$widgetId")
        private fun officeAStationIdKey(widgetId: Int) = stringPreferencesKey("office_a_station_id_$widgetId")
        private fun officeBStationIdKey(widgetId: Int) = stringPreferencesKey("office_b_station_id_$widgetId")
        private fun officeALabelKey(widgetId: Int) = stringPreferencesKey("office_a_label_$widgetId")
        private fun officeBLabelKey(widgetId: Int) = stringPreferencesKey("office_b_label_$widgetId")
        private fun scooterHomeToStationKey(widgetId: Int) = intPreferencesKey("scooter_home_to_station_$widgetId")
        private fun scooterStationToHomeKey(widgetId: Int) = intPreferencesKey("scooter_station_to_home_$widgetId")
        private fun scooterAStationToOfficeKey(widgetId: Int) = intPreferencesKey("scooter_a_station_to_office_$widgetId")
        private fun scooterAOfficeToStationKey(widgetId: Int) = intPreferencesKey("scooter_a_office_to_station_$widgetId")
        private fun scooterBStationToOfficeKey(widgetId: Int) = intPreferencesKey("scooter_b_station_to_office_$widgetId")
        private fun scooterBOfficeToStationKey(widgetId: Int) = intPreferencesKey("scooter_b_office_to_station_$widgetId")
        private fun weekdayOfficesKey(widgetId: Int) = stringPreferencesKey("weekday_offices_$widgetId")
        private fun officeOverrideKey(widgetId: Int) = stringPreferencesKey("office_override_$widgetId")
        private fun officeOverrideDateKey(widgetId: Int) = stringPreferencesKey("office_override_date_$widgetId")
    }

    private fun readWidget(preferences: Preferences, widgetId: Int): WidgetData? {
        val originId = preferences[originIdKey(widgetId)] ?: ""
        val destinationId = preferences[destinationIdKey(widgetId)] ?: ""
        if (originId.isEmpty() || destinationId.isEmpty()) return null
        return WidgetData(
            originId = originId,
            destinationId = destinationId,
            originName = preferences[originNameKey(widgetId)] ?: "",
            destinationName = preferences[destinationNameKey(widgetId)] ?: "",
            label = preferences[labelKey(widgetId)] ?: "",
            allowRouteReversal = preferences[allowRouteReversalKey(widgetId)] ?: false,
            autoReverseRoute = preferences[autoReverseRouteKey(widgetId)] ?: false,
            manualOverrideUntil = preferences[manualOverrideUntilKey(widgetId)] ?: 0,
            maxChanges = preferences[maxChangesKey(widgetId)],
            commuteEnabled = preferences[commuteEnabledKey(widgetId)] ?: false,
            homeStationId = preferences[homeStationIdKey(widgetId)] ?: "",
            officeAStationId = preferences[officeAStationIdKey(widgetId)] ?: "",
            officeBStationId = preferences[officeBStationIdKey(widgetId)] ?: "",
            officeALabel = preferences[officeALabelKey(widgetId)] ?: "",
            officeBLabel = preferences[officeBLabelKey(widgetId)] ?: "",
            scooterHomeToStation = preferences[scooterHomeToStationKey(widgetId)] ?: 0,
            scooterStationToHome = preferences[scooterStationToHomeKey(widgetId)] ?: 0,
            scooterAStationToOffice = preferences[scooterAStationToOfficeKey(widgetId)] ?: 0,
            scooterAOfficeToStation = preferences[scooterAOfficeToStationKey(widgetId)] ?: 0,
            scooterBStationToOffice = preferences[scooterBStationToOfficeKey(widgetId)] ?: 0,
            scooterBOfficeToStation = preferences[scooterBOfficeToStationKey(widgetId)] ?: 0,
            weekdayOffices = preferences[weekdayOfficesKey(widgetId)] ?: "AAAAAAA",
            officeOverride = preferences[officeOverrideKey(widgetId)] ?: "",
            officeOverrideDate = preferences[officeOverrideDateKey(widgetId)] ?: "",
        )
    }

    private fun writeWidget(preferences: MutablePreferences, widgetId: Int, widgetData: WidgetData) {
        preferences[originIdKey(widgetId)] = widgetData.originId
        preferences[destinationIdKey(widgetId)] = widgetData.destinationId
        preferences[originNameKey(widgetId)] = widgetData.originName
        preferences[destinationNameKey(widgetId)] = widgetData.destinationName
        preferences[labelKey(widgetId)] = widgetData.label
        preferences[allowRouteReversalKey(widgetId)] = widgetData.allowRouteReversal
        preferences[autoReverseRouteKey(widgetId)] = widgetData.autoReverseRoute
        preferences[manualOverrideUntilKey(widgetId)] = widgetData.manualOverrideUntil
        if (widgetData.maxChanges != null) {
            preferences[maxChangesKey(widgetId)] = widgetData.maxChanges
        } else {
            preferences.remove(maxChangesKey(widgetId))
        }
        preferences[commuteEnabledKey(widgetId)] = widgetData.commuteEnabled
        preferences[homeStationIdKey(widgetId)] = widgetData.homeStationId
        preferences[officeAStationIdKey(widgetId)] = widgetData.officeAStationId
        preferences[officeBStationIdKey(widgetId)] = widgetData.officeBStationId
        preferences[officeALabelKey(widgetId)] = widgetData.officeALabel
        preferences[officeBLabelKey(widgetId)] = widgetData.officeBLabel
        preferences[scooterHomeToStationKey(widgetId)] = widgetData.scooterHomeToStation
        preferences[scooterStationToHomeKey(widgetId)] = widgetData.scooterStationToHome
        preferences[scooterAStationToOfficeKey(widgetId)] = widgetData.scooterAStationToOffice
        preferences[scooterAOfficeToStationKey(widgetId)] = widgetData.scooterAOfficeToStation
        preferences[scooterBStationToOfficeKey(widgetId)] = widgetData.scooterBStationToOffice
        preferences[scooterBOfficeToStationKey(widgetId)] = widgetData.scooterBOfficeToStation
        preferences[weekdayOfficesKey(widgetId)] = widgetData.weekdayOffices
        preferences[officeOverrideKey(widgetId)] = widgetData.officeOverride
        preferences[officeOverrideDateKey(widgetId)] = widgetData.officeOverrideDate
    }

    private fun removeWidget(preferences: MutablePreferences, widgetId: Int) {
        preferences.remove(originIdKey(widgetId))
        preferences.remove(destinationIdKey(widgetId))
        preferences.remove(originNameKey(widgetId))
        preferences.remove(destinationNameKey(widgetId))
        preferences.remove(labelKey(widgetId))
        preferences.remove(allowRouteReversalKey(widgetId))
        preferences.remove(autoReverseRouteKey(widgetId))
        preferences.remove(manualOverrideUntilKey(widgetId))
        preferences.remove(maxChangesKey(widgetId))
        preferences.remove(commuteEnabledKey(widgetId))
        preferences.remove(homeStationIdKey(widgetId))
        preferences.remove(officeAStationIdKey(widgetId))
        preferences.remove(officeBStationIdKey(widgetId))
        preferences.remove(officeALabelKey(widgetId))
        preferences.remove(officeBLabelKey(widgetId))
        preferences.remove(scooterHomeToStationKey(widgetId))
        preferences.remove(scooterStationToHomeKey(widgetId))
        preferences.remove(scooterAStationToOfficeKey(widgetId))
        preferences.remove(scooterAOfficeToStationKey(widgetId))
        preferences.remove(scooterBStationToOfficeKey(widgetId))
        preferences.remove(scooterBOfficeToStationKey(widgetId))
        preferences.remove(weekdayOfficesKey(widgetId))
        preferences.remove(officeOverrideKey(widgetId))
        preferences.remove(officeOverrideDateKey(widgetId))
    }

    private val dataStore = context.dataStore

    /**
     * Get widget data as Flow for reactive updates
     */
    fun getWidgetDataFlow(widgetId: Int): Flow<WidgetData?> {
        return dataStore.data
            .catch { exception ->
                Log.e(TAG, "Error reading widget data for $widgetId", exception)
                emit(emptyPreferences())
            }
            .map { preferences ->
                try {
                    readWidget(preferences, widgetId)
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing widget data for $widgetId", e)
                    null
                }
            }
    }

    /**
     * Get widget data synchronously (suspending)
     */
    suspend fun getWidgetData(widgetId: Int): WidgetData? {
        return try {
            getWidgetDataFlow(widgetId).first()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting widget data for $widgetId", e)
            null
        }
    }

    /**
     * Save widget configuration
     */
    suspend fun saveWidgetData(widgetId: Int, widgetData: WidgetData) {
        try {
            Log.d(TAG, "Saving widget data for $widgetId: ${widgetData.originName} -> ${widgetData.destinationName}")
            
            dataStore.edit { preferences ->
                writeWidget(preferences, widgetId, widgetData)
            }
            
            Log.d(TAG, "Successfully saved widget data for $widgetId")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving widget data for $widgetId", e)
            throw e
        }
    }

    /**
     * Delete widget configuration
     */
    suspend fun deleteWidgetData(widgetId: Int) {
        try {
            Log.d(TAG, "Deleting widget data for $widgetId")
            
            dataStore.edit { preferences ->
                removeWidget(preferences, widgetId)
            }
            
            Log.d(TAG, "Successfully deleted widget data for $widgetId")
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting widget data for $widgetId", e)
            throw e
        }
    }

    /**
     * Get all configured widget IDs
     */
    suspend fun getAllWidgetIds(): List<Int> {
        return try {
            dataStore.data.first().asMap().keys
                .filter { it.name.startsWith("origin_id_") }
                .mapNotNull { key ->
                    key.name.removePrefix("origin_id_").toIntOrNull()
                }
                .sorted()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting all widget IDs", e)
            emptyList()
        }
    }

    /**
     * Get all configured widgets as Flow
     */
    fun getAllWidgetsFlow(): Flow<List<Pair<Int, WidgetData>>> {
        return dataStore.data
            .catch { exception ->
                Log.e(TAG, "Error reading all widgets", exception)
                emit(emptyPreferences())
            }
            .map { preferences ->
                try {
                    val widgetIds = preferences.asMap().keys
                        .filter { it.name.startsWith("origin_id_") }
                        .mapNotNull { key ->
                            key.name.removePrefix("origin_id_").toIntOrNull()
                        }
                    
                    widgetIds.mapNotNull { widgetId ->
                        readWidget(preferences, widgetId)?.let { widgetId to it }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing all widgets", e)
                    emptyList()
                }
            }
    }

    /**
     * Clean up orphaned widget data
     */
    suspend fun cleanupOrphanedWidgets(validWidgetIds: IntArray) {
        try {
            Log.d(TAG, "Cleaning up orphaned widgets. Valid IDs: ${validWidgetIds.contentToString()}")
            
            val allStoredIds = getAllWidgetIds()
            val validIds = validWidgetIds.toSet()
            
            dataStore.edit { preferences ->
                allStoredIds.forEach { storedId ->
                    if (storedId !in validIds) {
                        Log.d(TAG, "Removing orphaned widget data for ID: $storedId")
                        removeWidget(preferences, storedId)
                    }
                }
            }
            
            Log.d(TAG, "Successfully cleaned up orphaned widgets")
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up orphaned widgets", e)
            throw e
        }
    }

    /**
     * Check if widget is configured
     */
    suspend fun isWidgetConfigured(widgetId: Int): Boolean {
        return getWidgetData(widgetId) != null
    }

    /**
     * Update widget update frequency
     */
    suspend fun updateWidgetFrequency(widgetId: Int, frequencyMinutes: Int) {
        try {
            dataStore.edit { preferences ->
            }
            Log.d(TAG, "Updated frequency for widget $widgetId to ${frequencyMinutes}min")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating frequency for widget $widgetId", e)
            throw e
        }
    }

    /**
     * Clear all widget data (for debugging/reset)
     */
    suspend fun clearAllData() {
        try {
            dataStore.edit { preferences ->
                preferences.clear()
            }
            Log.d(TAG, "Cleared all widget data")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing all data", e)
            throw e
        }
    }
}