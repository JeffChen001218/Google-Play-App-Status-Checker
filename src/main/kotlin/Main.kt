import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.withLock
import repo.Repo.add
import repo.Repo.appStatusList
import repo.Repo.delete
import repo.Repo.mutex
import java.awt.Dimension

@Composable
@Preview
fun App() {

    val appStatusList by appStatusList.state
    var isFirstLaunch = remember { true }
    LaunchedEffect(Unit) {
        appStatusList.forEach { appStatus -> appStatus.reset() }
        while (true) {
            kotlin.runCatching {
                mutex.withLock { appStatusList.toList() }.forEach { appStatus ->
                    appStatus.startCheck(bypassTimeLimit = isFirstLaunch)
                }
            }
            isFirstLaunch = false
            delay(60 * 1000L)
        }
    }

    MaterialTheme {
        PackageListComponent(
            appStatusList = appStatusList,/*.sortedByDescending { it.editTime }*/
            onAdd = { appStatus ->
                println("add-1")
                add(appStatus)
            },
            onDelete = { appStatus ->
                delete(appStatus)
            },
            onToggleCheck = { appStatus, isChecked ->
                appStatus.apply { enableCheck = isChecked }.update()
            },
        )
    }

}

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Google Play App Status Checker",
        icon = painterResource("icon.png")
    ) {
        LaunchedEffect(Unit) {
            window.minimumSize = Dimension(1080, 600)
        }
        App()
    }
}