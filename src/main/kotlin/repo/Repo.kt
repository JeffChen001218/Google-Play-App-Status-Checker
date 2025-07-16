package repo

import PersisValue
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import model.AppStatus
import tool.CoroutineTool.launchScope

object Repo {

    val appStatusList = PersisValue.create(
        "app_status_list", listOf(), ListSerializer(AppStatus.serializer())
    )
    val mutex = Mutex()

    val host = PersisValue.string("host", "127.0.0.1")
    val port = PersisValue.int("port", 7890)

    fun add(data: AppStatus) {
        println("add: $data")
        launchScope {
            mutex.withLock {
                var existData: AppStatus? = null
                val existIndex = appStatusList.value.indexOfFirst { itemData ->
                    (itemData == data ||
                            itemData.packageName == data.packageName ||
                            itemData.code == data.code).also {
                        if (it) existData = itemData
                    }
                }
                appStatusList.value = appStatusList.value.toMutableList().apply {
                    if (existIndex >= 0) {
                        removeAt(existIndex)
                        add(existIndex, data.copy(editTime = System.currentTimeMillis()))
                    } else {
                        add(0, data.copy(editTime = System.currentTimeMillis()))
                    }
                    if (existData?.enableCheck == false && data.enableCheck) {
                        data.startCheck(bypassTimeLimit = true)
                    }
                }
            }
        }
    }

    fun update(data: AppStatus) {
        println("update: $data")
        launchScope {
            mutex.withLock {
                var existData: AppStatus? = null
                val existIndex = appStatusList.value.indexOfFirst { itemData ->
                    (itemData == data ||
                            itemData.packageName == data.packageName ||
                            itemData.code == data.code).also {
                        if (it) existData = itemData
                    }
                }
                appStatusList.value = appStatusList.value.toMutableList().apply {
                    if (existIndex >= 0) {
                        removeAt(existIndex)
                        add(existIndex, data.copy(editTime = System.currentTimeMillis()))
                        if (existData?.enableCheck == false && data.enableCheck) {
                            data.startCheck(bypassTimeLimit = true)
                        }
                    }
                }
            }
        }
    }

    fun delete(data: AppStatus) {
        launchScope {
            mutex.withLock {
                appStatusList.value.also {
                    println("delete-old size: ${it.size}")
                }
                appStatusList.value = appStatusList.value.filterNot {
                    it == data ||
                            it.packageName == data.packageName ||
                            it.code == data.code
                }.also {
                    println("delete-new size: ${it.size}")
                }
            }
        }
    }
}