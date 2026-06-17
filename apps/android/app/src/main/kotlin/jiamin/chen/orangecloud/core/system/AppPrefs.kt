package jiamin.chen.orangecloud.core.system

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** 外观模式（对应 iOS AppAppearance）。 */
enum class AppAppearance(val value: Int) {
    SYSTEM(0), LIGHT(1), DARK(2);

    companion object {
        fun from(value: Int): AppAppearance = entries.firstOrNull { it.value == value } ?: SYSTEM
    }
}

/** App 偏好（外观 + 通知开关），存共享 DataStore。 */
@Singleton
class AppPrefs @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val appearance: Flow<AppAppearance> = dataStore.data.map { AppAppearance.from(it[KEY_APPEARANCE] ?: 0) }
    val notificationsEnabled: Flow<Boolean> = dataStore.data.map { it[KEY_NOTIF_MASTER] ?: false }
    val notifyZoneStatus: Flow<Boolean> = dataStore.data.map { it[KEY_NOTIF_ZONE] ?: true }
    val notifyWorkerErrors: Flow<Boolean> = dataStore.data.map { it[KEY_NOTIF_WORKER] ?: true }

    suspend fun setAppearance(appearance: AppAppearance) {
        dataStore.edit { it[KEY_APPEARANCE] = appearance.value }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_NOTIF_MASTER] = enabled }
    }

    suspend fun setNotifyZoneStatus(enabled: Boolean) {
        dataStore.edit { it[KEY_NOTIF_ZONE] = enabled }
    }

    suspend fun setNotifyWorkerErrors(enabled: Boolean) {
        dataStore.edit { it[KEY_NOTIF_WORKER] = enabled }
    }

    private companion object {
        val KEY_APPEARANCE = intPreferencesKey("pref_appearance")
        val KEY_NOTIF_MASTER = booleanPreferencesKey("pref_notif_master")
        val KEY_NOTIF_ZONE = booleanPreferencesKey("pref_notif_zone_status")
        val KEY_NOTIF_WORKER = booleanPreferencesKey("pref_notif_worker_errors")
    }
}
