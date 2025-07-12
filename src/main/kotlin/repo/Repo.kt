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

    fun add(data: AppStatus) {
        launchScope {
            mutex.withLock {
                var existData: AppStatus? = null
                appStatusList.value = appStatusList.value.filterNot { itemData ->
                    (itemData == data ||
                            itemData.packageName == data.packageName ||
                            itemData.code == data.code).also {
                        if (it) existData = itemData
                    }
                }.toMutableList().apply {
                    add(data.copy(editTime = System.currentTimeMillis()).apply {
                        existData?.enableCheck?.let {
                            enableCheck = it
                        }
                        startCheck()
                    })
                }
            }
        }
    }

    fun update(data: AppStatus) {
        launchScope {
            mutex.withLock {
                var existData: AppStatus? = null
                appStatusList.value = appStatusList.value.filterNot { itemData ->
                    (itemData == data ||
                            itemData.packageName == data.packageName ||
                            itemData.code == data.code).also {
                        if (it) {
                            existData = itemData
                        }
                    }
                }.toMutableList().apply {
                    if (existData != null) {
                        add(data.copy(editTime = System.currentTimeMillis()).apply {
                            existData?.enableCheck?.let {
                                enableCheck = it
                            }
                        })
                    }
                }
            }
        }
    }

    fun delete(data: AppStatus) {
        launchScope {
            mutex.withLock {
                appStatusList.value = appStatusList.value.filterNot {
                    it == data ||
                            it.packageName == data.packageName ||
                            it.code == data.code
                }
            }
        }
    }
}