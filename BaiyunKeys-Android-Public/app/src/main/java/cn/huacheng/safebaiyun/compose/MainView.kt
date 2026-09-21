package cn.huacheng.safebaiyun.compose

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import cn.huacheng.safebaiyun.unlock.DataRepo
import cn.huacheng.safebaiyun.unlock.UnlockRepo
import cn.huacheng.safebaiyun.unlock.UnlockUiState
import cn.huacheng.safebaiyun.util.showToast

private val PageBackground = Color.White
private val CardBackground = Color(0xFFFFFFFF)
private val PrimaryGreen = Color(0xFF12C77D)
private val DeepText = Color(0xFF111827)
private val SecondaryText = Color(0xFF667085)
private val StatusBlue = Color(0xFF3478F6)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainView(navController: NavHostController) {
    val context = LocalContext.current
    val showEditDialog = remember { mutableStateOf(!DataRepo.isConfigured()) }
    var configRevision by remember { mutableIntStateOf(0) }
    val config = remember(configRevision) { DataRepo.readData() }
    val configured = DataRepo.validate(config.first, config.second) == null
    val unlockState by UnlockRepo.uiState.collectAsState()
    val logs by UnlockRepo.logFlow.collectAsState()

    var hasPermission by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val requestPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        UnlockRepo.addLog(if (granted) "[权限] 蓝牙授权状态正常" else "[权限] 蓝牙授权被拒绝")
    }

    Scaffold(
        containerColor = PageBackground,
        topBar = { AppHeader() },
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                NavigationBarItem(
                    selected = true,
                    onClick = {},
                    icon = { Icon(Icons.Default.Home, contentDescription = null) },
                    label = { Text("首页") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { showEditDialog.value = true },
                    icon = { Icon(Icons.Default.Edit, contentDescription = null) },
                    label = { Text("配置") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { navController.navigate("helper") },
                    icon = { Icon(Icons.Default.MoreVert, contentDescription = null) },
                    label = { Text("帮助") }
                )
            }
        }
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            UnlockPanel(
                configured = configured,
                hasPermission = hasPermission,
                state = unlockState,
                onUnlock = {
                    when {
                        !configured -> showEditDialog.value = true
                        !hasPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                            UnlockRepo.addLog("检查系统权限状态")
                            requestPermission.launch(Manifest.permission.BLUETOOTH_CONNECT)
                        }
                        else -> UnlockRepo.unlock()
                    }
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
            LogCard(
                logs = logs,
                status = unlockState.message,
                modifier = Modifier.weight(1f)
            )
        }
    }

    if (showEditDialog.value) {
        EditDialog(
            state = showEditDialog,
            initData = { DataRepo.readData() },
            onSaved = {
                configRevision++
                UnlockRepo.addLog("门禁配置已加密保存")
            }
        )
    }
}

@Composable
private fun AppHeader() {
    Surface(color = Color.White, shadowElevation = 1.dp) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "BaiyunKeys",
                color = DeepText,
                fontSize = 25.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun UnlockPanel(
    configured: Boolean,
    hasPermission: Boolean,
    state: UnlockUiState,
    onUnlock: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(76.dp),
        contentAlignment = Alignment.Center
    ) {
            Button(
                onClick = onUnlock,
                enabled = !state.isBusy,
                modifier = Modifier
                    .fillMaxWidth(0.62f)
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryGreen,
                    disabledContainerColor = PrimaryGreen.copy(alpha = 0.62f)
                )
            ) {
                if (state.isBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.size(10.dp))
                    Text("执行中…", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                } else {
                    val label = when {
                        !configured -> "配置门禁"
                        !hasPermission -> "授权并开门"
                        else -> "一键开门"
                    }
                    Text(label, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
            }
    }
}

@Composable
private fun LogCard(logs: List<String>, status: String, modifier: Modifier = Modifier) {
    val clipboard = LocalClipboardManager.current
    val listState = rememberLazyListState()
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) listState.animateScrollToItem(logs.lastIndex)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 240.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("实时日志", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = DeepText)
                Text(status, fontSize = 14.sp, color = StatusBlue, maxLines = 1)
            }
            Spacer(modifier = Modifier.height(12.dp))

            if (logs.isEmpty()) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text("点击开门后，这里会实时显示蓝牙流程", color = SecondaryText)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    items(logs) { line ->
                        Text(line, fontSize = 14.sp, lineHeight = 20.sp, color = SecondaryText)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                OutlinedButton(onClick = { UnlockRepo.clearLogs() }) {
                    Text("清空日志", color = StatusBlue, fontWeight = FontWeight.Bold)
                }
                OutlinedButton(onClick = {
                    clipboard.setText(AnnotatedString(logs.joinToString("\n")))
                    showToast("日志已复制")
                }) {
                    Text("复制日志", color = Color(0xFF049669), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
