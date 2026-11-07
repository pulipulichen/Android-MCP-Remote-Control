package info.pulipuli.app.androidmcpremotecontrol

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { Surface(Modifier.fillMaxSize()) { MainScreen(this) } } }
    }

    companion object {
        // Set and read on the main thread while the test button is composed.
        internal var currentTapTarget: (() -> Pair<Int, Int>?)? = null
    }
}

@Composable
private fun MainScreen(context: MainActivity) {
    val settings = remember { SecureSettings(context) }
    val adb = remember { AdbController(context) }
    val scope = rememberCoroutineScope()

    var page by rememberSaveable { mutableStateOf("首頁") }
    var host by remember { mutableStateOf(settings.adbHost()) }
    var pairingPort by remember { mutableStateOf("") }
    var pairingCode by remember { mutableStateOf("") }
    var connectionPort by remember { mutableStateOf(settings.adbPort().toString()) }
    var tunnelId by remember { mutableStateOf(settings.tunnelId()) }
    var apiKey by remember { mutableStateOf(settings.runtimeKey()) }
    var adbStatus by remember { mutableStateOf(if (settings.adbVerified()) "ADB 已配對，尚待重新測試" else "尚未連線") }
    var tunnelStatus by remember { mutableStateOf(settings.tunnelStatus()) }
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var remoteTapEnabled by remember { mutableStateOf(settings.remoteTapEnabled()) }
    var developerToolsEnabled by remember { mutableStateOf(settings.developerToolsEnabled()) }
    var testTapCount by remember { mutableStateOf(0) }
    var testButtonCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    DisposableEffect(context) {
        val target = {
            val coordinates = testButtonCoordinates
            if (!context.hasWindowFocus() || coordinates == null || !coordinates.isAttached) {
                null
            } else {
                val center = Offset(coordinates.size.width / 2f, coordinates.size.height / 2f)
                val clippedBounds = coordinates.boundsInWindow()
                val screen = coordinates.localToScreen(center)
                if (clippedBounds.contains(coordinates.localToWindow(center)) && screen.isSpecified) {
                    screen.x.toInt() to screen.y.toInt()
                } else {
                    null
                }
            }
        }
        MainActivity.currentTapTarget = target
        onDispose { if (MainActivity.currentTapTarget === target) MainActivity.currentTapTarget = null }
    }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        while (true) {
            tunnelStatus = settings.tunnelStatus()
            delay(1_000)
        }
    }

    fun launchWork(action: suspend () -> String) {
        scope.launch {
            busy = true
            message = "執行中…"
            try { message = action() } catch (error: Exception) { message = "失敗：${error.message ?: "未知錯誤"}" }
            busy = false
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 700.dp
        var drawerOpen by rememberSaveable { mutableStateOf(false) }
        Row(Modifier.fillMaxSize()) {
            if (wide || drawerOpen) {
                Column(
                    Modifier.width(if (wide) 220.dp else 190.dp).fillMaxHeight()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("Android MCP", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Remote Control", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(12.dp))
                    listOf("首頁", "ADB", "Tunnel", "設定", "關於").forEach { destination ->
                        NavigationDrawerItem(
                            label = { Text(destination) },
                            selected = page == destination,
                            onClick = { page = destination; drawerOpen = false },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    if (!wide) TextButton(onClick = { drawerOpen = !drawerOpen }) { Text("☰") }
                    Text(page, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.primary)
                    when (page) {
                        "首頁" -> {
                            Text("裝置與連線狀態", style = MaterialTheme.typography.titleLarge)
                            InfoCard("ADB｜本機控制") {
                                StatusLine("ADB", adbStatus)
                                Text("目標：$host:$connectionPort")
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(enabled = !busy, onClick = {
                                        launchWork {
                                            val port = connectionPort.toIntOrNull() ?: 0
                                            val result = adb.verify(host, port)
                                            settings.saveAdbTarget(host, port)
                                            adbStatus = result
                                            result
                                        }
                                    }) { Text("連線／重新連線") }
                                    OutlinedButton(onClick = {
                                        adbStatus = "已停止操作；ADB 系統服務不受此按鈕控制"
                                    }) { Text("停止操作") }
                                }
                                TextButton(onClick = { page = "ADB" }) { Text("ADB 詳細設定") }
                            }
                            InfoCard("Tunnel｜遠端連線") {
                                StatusLine("Tunnel", tunnelStatus)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(enabled = !busy && settings.adbVerified(), onClick = {
                                        if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        launchWork {
                                            require(settings.tunnelId().isNotBlank() && settings.runtimeKey().isNotBlank()) { "請先在 Tunnel 頁面設定金鑰" }
                                            settings.setTunnelStatus("正在啟動…")
                                            ContextCompat.startForegroundService(context,
                                                Intent(context, TunnelForegroundService::class.java)
                                                    .setAction(TunnelForegroundService.ACTION_START))
                                            "已要求啟動 Tunnel"
                                        }
                                    }) { Text("啟動／重新連線") }
                                    OutlinedButton(onClick = {
                                        context.startService(Intent(context, TunnelForegroundService::class.java)
                                            .setAction(TunnelForegroundService.ACTION_STOP))
                                    }) { Text("停止") }
                                }
                                TextButton(onClick = { page = "Tunnel" }) { Text("Tunnel 詳細設定") }
                            }
                            InfoCard("MCP 工具") {
                                Text("開發工具：${if (developerToolsEnabled) "已授權" else "尚未授權"}")
                                Text("工具實際可用性仍須透過 Tunnel 與 ADB 驗證。")
                                TextButton(onClick = { page = "ADB" }) { Text("前往 ADB 操作") }
                            }
                            InfoCard("裝置資訊") {
                                Text("Android ${Build.VERSION.RELEASE} · ${Build.MODEL}")
                            }
                        }
                        "ADB" -> {
        InfoCard("ADB｜配對與本機控制") {
            Text("在開發者選項開啟「無線偵錯」。配對用的 IP、配對埠與 6 位碼，從「使用配對碼配對裝置」取得；主畫面的埠是後續連線埠。")
            TextButton(onClick = { runCatching { context.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) } }) {
                Text("開啟開發者選項")
            }
            OutlinedTextField(host, { host = it }, label = { Text("Y700 IP 位址") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    pairingPort, { pairingPort = it }, label = { Text("配對埠") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    pairingCode, { pairingCode = it.take(6) }, label = { Text("6 位配對碼") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), modifier = Modifier.weight(1f),
                )
            }
            OutlinedTextField(
                connectionPort, { connectionPort = it }, label = { Text("無線偵錯連線埠") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(enabled = !busy, onClick = {
                    val port = pairingPort.toIntOrNull() ?: 0
                    launchWork { adb.pair(host, port, pairingCode) }
                }) { Text("配對這台平板") }
                Button(enabled = !busy, onClick = {
                    val port = connectionPort.toIntOrNull() ?: 0
                    launchWork {
                        val result = adb.verify(host, port)
                        settings.saveAdbTarget(host, port)
                        adbStatus = result
                        result
                    }
                }) { Text("連線並驗證") }
            }
            StatusLine("ADB", adbStatus)
            HorizontalDivider()
            Text("點擊此測試區會增加計數。下方按鈕會透過 App 內 ADB 執行 input tap，驗證裝置能控制自己。")
            Button(
                onClick = { testTapCount++ },
                modifier = Modifier.fillMaxWidth().onGloballyPositioned { testButtonCoordinates = it },
            ) { Text("ADB 點擊測試區｜目前計數 $testTapCount") }
            Text("狀態：${if (settings.adbVerified()) "已保存 ADB 連線資訊" else "尚未通過連線驗證"}", style = MaterialTheme.typography.bodySmall)
            TextButton(enabled = !busy && settings.adbVerified(), onClick = {
                launchWork {
                    val result = adb.tap(settings.adbHost(), settings.adbPort())
                    adbStatus = "ADB tap 測試已送出"
                    result
                }
            }) { Text("透過 ADB 點擊測試區") }
        }


                        }
                        "Tunnel" -> {
        InfoCard("Tunnel｜遠端連線") {
            Text("使用 OpenAI Tunnel Runtime API key（CONTROL_PLANE_API_KEY）。金鑰會加密後存入 Android Keystore 保護的本機儲存。")
            OutlinedTextField(tunnelId, { tunnelId = it }, label = { Text("Tunnel ID") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                apiKey, { apiKey = it }, label = { Text("Runtime API key") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("允許遠端點擊測試區", fontWeight = FontWeight.Medium)
                    Text("開啟後，請讓測試按鈕留在 App 的前景畫面。", style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = remoteTapEnabled, onCheckedChange = {
                    remoteTapEnabled = it
                    settings.setRemoteTapEnabled(it)
                })
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("允許遠端開發工具", fontWeight = FontWeight.Medium)
                    Text("允許 Tunnel 呼叫者讀取 Logcat、安裝或移除 App，並以 ADB shell 權限執行指令。", style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = developerToolsEnabled, onCheckedChange = {
                    developerToolsEnabled = it
                    settings.setDeveloperToolsEnabled(it)
                })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(enabled = !busy && settings.adbVerified(), onClick = {
                    if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    launchWork {
                        require(tunnelId.isNotBlank() && apiKey.isNotBlank()) { "請輸入 Tunnel ID 與 Runtime API key" }
                        settings.saveTunnel(tunnelId, apiKey)
                        settings.setTunnelStatus("正在啟動…")
                        val intent = Intent(context, TunnelForegroundService::class.java).setAction(TunnelForegroundService.ACTION_START)
                        ContextCompat.startForegroundService(context, intent)
                        "已啟動 Tunnel Client；等待 Tunnel 狀態回報。"
                    }
                }) { Text("啟動 Tunnel") }
                TextButton(onClick = {
                    val intent = Intent(context, TunnelForegroundService::class.java).setAction(TunnelForegroundService.ACTION_STOP)
                    context.startService(intent)
                }) { Text("停止") }
            }
            StatusLine("Tunnel", tunnelStatus)
            Spacer(Modifier.height(2.dp))
            Text("開發工具包含套件查詢、APK 安裝、移除、App 啟動、Logcat 與 ADB shell。", style = MaterialTheme.typography.bodySmall)
        }

                        }
                        "設定" -> {
                            InfoCard("自動啟動與背景執行") {
                                Text("首次安裝預設不自動啟動 ADB 或 Tunnel。")
                                Text("後續將加入開機啟動、重試與背景服務的獨立設定。")
                            }
                            InfoCard("MCP 工具與安全") {
                                Text("遠端開發工具權限")
                                Switch(checked = developerToolsEnabled, onCheckedChange = {
                                    developerToolsEnabled = it
                                    settings.setDeveloperToolsEnabled(it)
                                })
                                Text("具有破壞性的遠端操作需要另行設計本機確認流程。")
                            }
                        }
                        "關於" -> {
                            InfoCard("Android MCP Remote Control") {
                                Text("版本：${context.packageManager.getPackageInfo(context.packageName, 0).versionName}")
                                Text("GitHub：pulipulichen/Android-MCP-Remote-Control")
                                Text("Android 裝置自我 ADB 控制與 OpenAI Tunnel 的技術驗證專案。")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun StatusLine(label: String, value: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text("$label：", fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(2.dp))
        Text(value)
    }
}
