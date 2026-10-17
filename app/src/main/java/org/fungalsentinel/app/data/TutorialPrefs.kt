package org.fungalsentinel.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.tutorialDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "tutorial_prefs"
)

object TutorialPrefs {
    private fun key(stepId: String) = booleanPreferencesKey("tutorial_shown_$stepId")

    fun isShown(context: Context, stepId: String): Flow<Boolean> =
        context.tutorialDataStore.data.map { it[key(stepId)] ?: false }

    suspend fun markShown(context: Context, stepId: String) {
        context.tutorialDataStore.edit { it[key(stepId)] = true }
    }

    suspend fun resetAll(context: Context) {
        context.tutorialDataStore.edit { it.clear() }
    }
}