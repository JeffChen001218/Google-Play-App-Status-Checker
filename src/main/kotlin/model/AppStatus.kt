package model

import repo.Repo
import tool.CheckStatusTool.getOnlineVersionName
import java.io.Serializable

data class AppStatus constructor(
    var name: String,
    var code: String,
    var packageName: String,
    var checkIntervalMinutes: Int,
    var onlineVersion: String? = null,
    var isChecked: Boolean = true,
    var lastCheckTime: Long = 0L,
    var editTime: Long = System.currentTimeMillis(),
) : Serializable {

    fun add() = Repo.add(this)
    fun delete() = Repo.delete(this)

    fun canCheck() = System.currentTimeMillis() - lastCheckTime > checkIntervalMinutes * 60 * 1000

    suspend fun checkOnline() {
        onlineVersion = getOnlineVersionName(packageName)
        add()
    }
}