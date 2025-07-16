package model

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import repo.Repo
import tool.CheckStatusTool.getOnlineVersionName
import tool.CoroutineTool.launchScope

@Serializable
data class AppStatus constructor(
    var name: String,
    var code: String,
    var packageName: String,
    var checkIntervalMinutes: Int,
    var onlineVersion: String? = "",
    var theLastOnlineVersion: String? = null,
    var enableCheck: Boolean = true,
    var lastCheckTime: Long = 0L,
    var editTime: Long = System.currentTimeMillis(),
) {

    enum class Status {
        Checking,
        Online,
        Offline,
        Unknown,
    }


    fun add() = Repo.add(this)
    fun update() = Repo.update(this)
    fun delete() = Repo.delete(this)

    fun reset() {
        onlineVersion = ""
        println("add-3")
        add()
    }

    private var checking by mutableStateOf(false)

    val status: Status by derivedStateOf {
        when {
            checking -> Status.Checking
            isOnline() -> Status.Online
            isOffline() -> Status.Offline
            else -> Status.Unknown
        }
    }


    private var checkJob: Job? = null
    fun startCheck(bypassTimeLimit: Boolean = false) {
        launchScope {
            if (isChecking()) return@launchScope
            if (canCheck(bypassTimeLimit)) {
                checkJob?.cancelAndJoin()
                checkJob = launch {
                    async {
                        checking = true
                        onlineVersion = getOnlineVersionName(packageName).also {
                            if (!it.isNullOrBlank()) {
                                theLastOnlineVersion = it
                            }
                        }
                        checking = false
                        println("add-2")
                        add()
                    }
                }
            }
        }
    }

    fun cancelCheck() {
        checkJob?.cancel()
    }


    fun isChecking(): Boolean = checking
    fun isOffline(): Boolean = onlineVersion == null
    fun isOnline(): Boolean = !onlineVersion.isNullOrBlank()


    fun canCheck(bypassTimeLimit: Boolean = false) = enableCheck
            // && !isOffline()
            && (bypassTimeLimit || System.currentTimeMillis() - lastCheckTime > checkIntervalMinutes * 60 * 1000)
}