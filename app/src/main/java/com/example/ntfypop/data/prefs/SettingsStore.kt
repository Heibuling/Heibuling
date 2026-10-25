package com.example.ntfypop.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ntfypop.core.Constants
import com.example.ntfypop.domain.EnvelopeCodec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "ntfypop")

data class Identity(
    val uid: String,
    val name: String,
    val topic: String,
    val serverUrl: String,
)

class SettingsStore(private val context: Context) {

    private object K {
        val UID = stringPreferencesKey("self_uid")
        val NAME = stringPreferencesKey("self_name")
        val TOPIC = stringPreferencesKey("dev_topic")
        val SERVER = stringPreferencesKey("server_url")
        val WIZARD = androidx.datastore.preferences.core.booleanPreferencesKey("wizard_done")
        val BALL_Y = androidx.datastore.preferences.core.floatPreferencesKey("ball_y")
        val NICK_SET = androidx.datastore.preferences.core.booleanPreferencesKey("nickname_set")
        val ROM_BRAND = stringPreferencesKey("rom_brand")
        val ROM_READ = androidx.datastore.preferences.core.booleanPreferencesKey("rom_guide_read")
        val LAST_UPDATE = androidx.datastore.preferences.core.longPreferencesKey("last_update_check")
    }

    val identity: Flow<Identity> = context.dataStore.data.map { it.toIdentity() }

    /** 首次启动生成 uid / 昵称 / 开发用 topic。 */
    suspend fun ensureInitialized(): Identity {
        val cur = context.dataStore.data.first()
        if (cur[K.UID].isNullOrBlank()) {
            context.dataStore.edit { p ->
                p[K.UID] = EnvelopeCodec.newUid()
                // 关键：只在为空时写默认值，绝不覆盖用户已经设置过的昵称
                if (p[K.NAME].isNullOrBlank()) p[K.NAME] = "我"
                if (p[K.TOPIC].isNullOrBlank()) {
                    p[K.TOPIC] = Constants.DEV_TOPIC_PREFIX + EnvelopeCodec.randomCrockford(Constants.TOPIC_RANDOM_LEN)
                }
                if (p[K.SERVER].isNullOrBlank()) p[K.SERVER] = Constants.DEFAULT_SERVER
            }
        }
        return context.dataStore.data.first().toIdentity()
    }

    val wizardDone: Flow<Boolean> = context.dataStore.data.map { it[K.WIZARD] ?: false }

    suspend fun setWizardDone(done: Boolean) {
        context.dataStore.edit { it[K.WIZARD] = done }
    }

    suspend fun setServerUrl(url: String) {
        if (url.isBlank()) return
        context.dataStore.edit { it[K.SERVER] = url }
    }

    val lastUpdateCheck: Flow<Long> = context.dataStore.data.map { it[K.LAST_UPDATE] ?: 0L }

    suspend fun setLastUpdateCheck(ts: Long) {
        context.dataStore.edit { it[K.LAST_UPDATE] = ts }
    }

    val romBrand: Flow<String> = context.dataStore.data.map { it[K.ROM_BRAND] ?: "" }

    val romGuideRead: Flow<Boolean> = context.dataStore.data.map { it[K.ROM_READ] ?: false }

    suspend fun setRomBrand(brand: String) {
        context.dataStore.edit { it[K.ROM_BRAND] = brand }
    }

    suspend fun setRomGuideRead(read: Boolean) {
        context.dataStore.edit { it[K.ROM_READ] = read }
    }

    val nicknameSet: Flow<Boolean> = context.dataStore.data.map { it[K.NICK_SET] ?: false }

    suspend fun setNicknameSet(done: Boolean) {
        context.dataStore.edit { it[K.NICK_SET] = done }
    }

    val ballY: Flow<Float> = context.dataStore.data.map { it[K.BALL_Y] ?: 0.65f }

    suspend fun setBallY(ratio: Float) {
        context.dataStore.edit { it[K.BALL_Y] = ratio }
    }

    suspend fun setName(name: String) {
        context.dataStore.edit { it[K.NAME] = name }
    }

    private fun Preferences.toIdentity() = Identity(
        uid = this[K.UID].orEmpty(),
        name = this[K.NAME].orEmpty().ifBlank { "我" },
        topic = this[K.TOPIC].orEmpty(),
        serverUrl = this[K.SERVER]?.ifBlank { null } ?: Constants.DEFAULT_SERVER,
    )
}
