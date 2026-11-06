package info.pulipuli.app.androidmcpremotecontrol

import android.content.Context
import io.github.rhythmcache.adb.AdbClient
import io.github.rhythmcache.adb.FileKeyProvider
import java.io.File
import kotlinx.coroutines.withTimeout

class AdbController(context: Context) {
    private val appContext = context.applicationContext
    private val keys: FileKeyProvider by lazy {
        val dir = File(appContext.filesDir, "adb-identity").apply { mkdirs() }
        FileKeyProvider(File(dir, "adbkey"), File(dir, "adbkey.pub"), "android-mcp-remote-control")
    }

    suspend fun pair(host: String, port: Int, pairingCode: String): String = withTimeout(45_000) {
        require(host.isNotBlank()) { "請輸入平板 IP" }
        require(port in 1..65535) { "配對埠號必須介於 1 到 65535" }
        require(pairingCode.matches(Regex("\\d{6}"))) { "配對碼必須是 6 位數" }
        val result = AdbClient.pairTls(host.trim(), port, pairingCode, keys)
        result.fold(
            onSuccess = { "ADB 配對成功；接著輸入無線偵錯畫面上的連線埠並測試連線。" },
            onFailure = { throw it },
        )
    }

    suspend fun verify(host: String, port: Int): String = withTimeout(30_000) {
        require(host.isNotBlank()) { "請輸入平板 IP" }
        require(port in 1..65535) { "連線埠必須介於 1 到 65535" }
        AdbClient.connectTls(host.trim(), port, keys).use { client ->
            val model = client.shell("getprop ro.product.model")
            if (!model.isSuccess) throw IllegalStateException(model.stderrText.ifBlank { "ADB shell 測試失敗" })
            val android = client.shell("getprop ro.build.version.release").stdoutText.trim()
            "ADB 已連線：${model.stdoutText.trim()} / Android $android"
        }
    }

    suspend fun tap(host: String, port: Int, x: Int, y: Int): String = withTimeout(20_000) {
        require(x > 0 && y > 0) { "尚未取得測試區座標" }
        AdbClient.connectTls(host, port, keys).use { client ->
            val result = client.shell("input tap $x $y")
            if (!result.isSuccess) throw IllegalStateException(result.stderrText.ifBlank { "ADB tap 失敗" })
            "已透過本機 ADB 點擊測試區 ($x, $y)"
        }
    }
}
