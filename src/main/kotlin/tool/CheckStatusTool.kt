package tool

import PersisValue
import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.serialization.*
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.*

object CheckStatusTool {

    private const val nodeInspectionUrl = "http://127.0.0.1:18765/api/app-inspect"
    private val client = HttpClient(CIO)
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class NodeInspectionResponse(
        val success: Boolean = false,
        val data: NodeInspectionData? = null,
    )

    @Serializable
    private data class NodeInspectionData(
        val online: Boolean? = null,
        val version: String? = null,
    )

    private suspend fun requestNodeInspection(packageName: String): NodeInspectionData? {
        val response = client.get(nodeInspectionUrl) {
            parameter("id", packageName)
            parameter("country", "US")
        }
        if (!response.status.isSuccess()) return null
        val payload = json.decodeFromString<NodeInspectionResponse>(response.bodyAsText())
        return payload.data?.takeIf { payload.success }
    }

    /**
     * 通过本地 Node 解析服务读取 Google Play 上架状态和版本名。
     * 返回：null=目标地区明确未上架；空字符串=服务不可用或无法解析。
     */
    suspend fun getOnlineVersionName(
        packageName: String,
        retryTimes: Int = 2
    ): String? = withContext(Dispatchers.IO) {
        repeat(retryTimes.coerceAtLeast(0) + 1) {
            val result = runCatching { requestNodeInspection(packageName) }.getOrNull() ?: return@repeat
            return@withContext when (result.online) {
                // 直接保留 Node 解析器的版本字段；字段缺失才视为未知。
                true -> result.version.orEmpty()
                false -> null
                null -> ""
            }
        }
        ""
    }

    private var releasedVersionNameList by PersisValue.create(
        "released_version_name_list",
        listOf(),
        ListSerializer(String.serializer())
    )

    suspend fun isAppReleased(
        packageName: String,
        versionName: String,
        retryTimes: Int = 2
    ): Boolean? {
        versionName ?: return null
        if (releasedVersionNameList.contains(versionName)) {
            return true
        }
        val onlineVersionName = getOnlineVersionName(packageName, retryTimes).also {
            println("online version name: ${it}")
        } ?: return null
        return (onlineVersionName.isBiggerOrEqualVersionName(versionName)).also { released ->
            if (released) {
                releasedVersionNameList = releasedVersionNameList.toMutableList().apply { add(versionName) }
            }
        }
    }

    fun String.isBiggerOrEqualVersionName(that: String): Boolean {
        val thisVersionNumList = this.split('.').map { it.toIntOrNull() ?: -1 }
        val thatVersionNumList = that.split('.').map { it.toIntOrNull() ?: -1 }

        (0 until kotlin.math.max(thisVersionNumList.size, thatVersionNumList.size)).forEach { index ->
            val thisNum = thisVersionNumList.getOrNull(index) ?: -1
            val thatNum = thatVersionNumList.getOrNull(index) ?: -1
            if (thisNum < thatNum) {
                return false
            }
        }
        return true
    }
}
