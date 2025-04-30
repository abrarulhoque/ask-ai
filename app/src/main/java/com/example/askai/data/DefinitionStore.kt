package com.example.askai.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.util.Date

// Extension property for Context to create DataStore
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "definitions")

// Data class for definitions
@Serializable
data class Definition(
    val id: String = System.currentTimeMillis().toString(),
    val query: String,
    val explanation: String,
    val timestamp: Long = Date().time
)

// Class that manages definitions using DataStore
class DefinitionStore(private val context: Context) {
    
    // Key for storing serialized definitions list
    private val definitionsKey = stringPreferencesKey("definitions_list")
    
    // Get all definitions as a Flow
    val allDefinitionsFlow: Flow<List<Definition>> = context.dataStore.data
        .map { preferences ->
            val definitionsJson = preferences[definitionsKey] ?: "[]"
            try {
                Json.decodeFromString<List<Definition>>(definitionsJson)
            } catch (e: Exception) {
                emptyList()
            }
        }
    
    // Add a new definition
    suspend fun saveDefinition(query: String, explanation: String) {
        val definition = Definition(query = query, explanation = explanation)
        context.dataStore.edit { preferences ->
            val currentDefinitionsJson = preferences[definitionsKey] ?: "[]"
            val currentDefinitions = try {
                Json.decodeFromString<List<Definition>>(currentDefinitionsJson)
            } catch (e: Exception) {
                emptyList()
            }
            
            val updatedDefinitions = currentDefinitions + definition
            preferences[definitionsKey] = Json.encodeToString(updatedDefinitions)
        }
    }
    
    // Delete a definition by id
    suspend fun deleteDefinition(id: String) {
        context.dataStore.edit { preferences ->
            val currentDefinitionsJson = preferences[definitionsKey] ?: "[]"
            val currentDefinitions = try {
                Json.decodeFromString<List<Definition>>(currentDefinitionsJson)
            } catch (e: Exception) {
                emptyList()
            }
            
            val updatedDefinitions = currentDefinitions.filter { it.id != id }
            preferences[definitionsKey] = Json.encodeToString(updatedDefinitions)
        }
    }
} 