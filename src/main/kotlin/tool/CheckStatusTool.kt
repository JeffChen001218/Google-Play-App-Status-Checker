package tool

import PersisValue
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.cio.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.*
import kotlinx.serialization.*
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.*
import repo.Repo

object CheckStatusTool {

    // 初始化客户端
    val client = HttpClient(CIO) {
        engine {
            proxy = ProxyBuilder.http("http://${Repo.host.value}:${Repo.port.value}")
        }
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    // 普通 GET 请求获取纯文本
    suspend fun fetchPlainText(url: String): HttpResponse {
        val response: HttpResponse = client.get(url)
        return response
    }

    /**
     * parsing [versionName] via Google PlayStore website
     *      null  ->  fetch/parse failed
     */
    suspend fun getOnlineVersionName(
        packageName: String,
        retryTimes: Int = 2
    ): String? = withContext(Dispatchers.IO) {
        runCatching {
            val response = fetchPlainText("https://play.google.com/store/apps/details?id=${packageName}&hl=en")
            val html = response.bodyAsText()
            if (response.status.isSuccess()) {
                """<script.*${packageName}.*(\["[0-9]+\.[0-9]+(\.[0-9]+)?"\]).*</script>""".toRegex()
                    .find(html)?.value?.let {
                        """\["[0-9]+\.[0-9]+(\.[0-9]+)?"\]""".toRegex().find(it)?.value
                    }
                    ?.replace("[", "")
                    ?.replace("]", "")
                    ?.replace("\"", "")
                    ?.takeIf { it.isNotBlank() } ?: ""
            } else {
                if (html.contains("We're sorry, the requested URL was not found on this server.")) {
                    return@withContext null
                }
                // fetch failed
                if (retryTimes > 0) {
                    // retry
                    getOnlineVersionName(packageName, retryTimes - 1)
                } else null
            }
        }.getOrNull() ?: ""
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