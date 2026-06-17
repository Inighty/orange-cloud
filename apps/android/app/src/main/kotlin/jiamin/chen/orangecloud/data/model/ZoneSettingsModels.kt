package jiamin.chen.orangecloud.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** GET/PATCH /zones/{id}/settings/{setting} 的 result。value 形态因设置而异（字符串）。 */
@Serializable
data class ZoneSetting(
    val id: String? = null,
    val value: String,
)

@Serializable
data class ZoneSettingUpdate(val value: String)

@Serializable
data class PurgeRequest(
    @SerialName("purge_everything") val purgeEverything: Boolean,
)

@Serializable
data class PurgeResult(val id: String? = null)
