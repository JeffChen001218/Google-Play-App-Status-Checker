package repo

import PersisValue
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import model.AppStatus
import tool.CoroutineTool.launchScope

object Repo {

    val appStatusList = PersisValue.create("app_status_list", listOf<AppStatus>())
    val mutex = Mutex()

    fun add(data: AppStatus) {
        launchScope {
            mutex.withLock {
                appStatusList.value = appStatusList.value.filterNot {
                    it == data ||
                            it.packageName == data.packageName ||
                            it.code == data.code
                }.toMutableList().apply {
                    add(data.copy(editTime = System.currentTimeMillis()))
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