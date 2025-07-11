import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import model.AppStatus
import kotlin.math.max

@Composable
fun PackageListComponent(
    appStatusList: List<AppStatus>,
    onAdd: (AppStatus) -> Unit,
    onDelete: (AppStatus) -> Unit,
    onToggleCheck: (AppStatus) -> Unit,
) {

    var showAddDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Google Play App Status Checker", fontSize = 20.sp)
            Spacer(modifier = Modifier.weight(1f))
            Button(onClick = {
                showAddDialog = true
            }) {
                Text("添加")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(appStatusList) { item ->
                PackageRow(
                    appStatus = item,
                    onToggleCheck = { onToggleCheck(item) },
                    onDelete = { onDelete(item) }
                )
                Divider(color = Color.LightGray, thickness = 0.5.dp)
            }
        }
    }

    if (showAddDialog) {
        AddDialog(onConfirm = { appStatus ->
            onAdd.invoke(appStatus)
            showAddDialog = false
        }, onDismiss = {
            showAddDialog = false
        })
    }
}

@Composable
fun PackageRow(
    appStatus: AppStatus,
    onToggleCheck: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(appStatus.name, modifier = Modifier.weight(1f))

        Text(appStatus.onlineVersion ?: "Unknown", modifier = Modifier.padding(horizontal = 8.dp))

        if (!appStatus.onlineVersion.isNullOrBlank()) {
            Icon(Icons.Default.Check, contentDescription = "Checked", tint = Color.Green)
        } else {
            Icon(Icons.Default.Warning, contentDescription = "Unchecked", tint = Color.Gray)
        }

        Switch(
            checked = appStatus.isChecked,
            onCheckedChange = {
                onToggleCheck()
            }
        )

        Spacer(modifier = Modifier.width(8.dp))

        Button(onClick = onDelete) {
            Text("删除")
        }
    }
}

@Composable
fun AddDialog(
    onConfirm: (AppStatus) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var id by remember { mutableStateOf("") }
    var packageName by remember { mutableStateOf("") }
    var interval by remember { mutableStateOf("5") }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = {
                onConfirm.invoke(AppStatus(name, id, packageName, interval.toInt()))
            }) {
                Text("确认")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("取消")
            }
        },
        title = { Text("添加信息") },
        text = {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称") },
                    singleLine = true
                )
                OutlinedTextField(value = id, onValueChange = { id = it }, label = { Text("编号") }, singleLine = true)
                OutlinedTextField(
                    value = packageName,
                    onValueChange = { packageName = it },
                    label = { Text("包名") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = interval,
                    onValueChange = {
                        interval = if (it.isNotBlank()) max(5, it.toIntOrNull() ?: 5).toString()
                        else it
                    },
                    label = { Text("检测时间间隔（分钟）") },
                    singleLine = true
                )
            }
        }
    )
}
