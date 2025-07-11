import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.withLock
import repo.Repo.add
import repo.Repo.appStatusList
import repo.Repo.delete
import repo.Repo.mutex

@Composable
@Preview
fun App() {

    val appStatusList by appStatusList.state

    MaterialTheme {
        PackageListComponent(
            appStatusList = appStatusList.sortedByDescending { it.editTime },
            onAdd = { appStatus ->
                add(appStatus)
            },
            onDelete = { appStatus ->
                delete(appStatus)
            },
            onToggleCheck = { appStatus ->
                add(appStatus.copy().apply { isChecked = !isChecked })
            },
        )
    }

    LaunchedEffect(Unit) {
        while (true) {
            mutex.withLock {
                appStatusList.forEach { appStatus ->
                    async { appStatus.checkOnline() }
                }
            }
            delay(10 * 1000L)
        }
    }

}

fun main() = application {
    Window(onCloseRequest = ::exitApplication) {
        App()
    }
}