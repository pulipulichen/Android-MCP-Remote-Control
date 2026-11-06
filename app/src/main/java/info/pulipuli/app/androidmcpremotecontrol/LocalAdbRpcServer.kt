package info.pulipuli.app.androidmcpremotecontrol

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.security.SecureRandom
import android.util.Base64
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class LocalAdbRpcServer(
    private val adb: AdbController,
    private val settings: SecureSettings,
    private val scope: CoroutineScope,
) : AutoCloseable {
    private val server = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
    private var acceptJob: Job? = null
    val port: Int get() = server.localPort
    val token: String = ByteArray(32).also { SecureRandom().nextBytes(it) }.let { Base64.encodeToString(it, Base64.NO_WRAP) }

    fun start() {
        acceptJob = scope.launch(Dispatchers.IO) {
            while (isActive && !server.isClosed) {
                val socket = try { server.accept() } catch (_: Exception) { break }
                launch(Dispatchers.IO) { handle(socket) }
            }
        }
    }

    private suspend fun handle(socket: Socket) = withContext(Dispatchers.IO) {
        socket.use { peer ->
            peer.soTimeout = 12_000
            val reader = BufferedReader(InputStreamReader(peer.getInputStream(), Charsets.UTF_8))
            val writer = OutputStreamWriter(peer.getOutputStream(), Charsets.UTF_8)
            val line = reader.readLine() ?: return@withContext
            val response = try {
                val request = JSONObject(line)
                val provided = request.optString("token").toByteArray(Charsets.UTF_8)
                val expected = token.toByteArray(Charsets.UTF_8)
                if (!MessageDigest.isEqual(provided, expected)) throw SecurityException("Invalid local bridge token")
                val result = when (request.optString("method")) {
                    "device_info" -> deviceInfo()
                    "tap_test_zone" -> tapTestZone()
                    else -> throw IllegalArgumentException("Unsupported bridge method")
                }
                JSONObject().put("ok", true).put("result", result)
            } catch (error: Exception) {
                JSONObject().put("ok", false).put("error", error.message ?: "ADB operation failed")
            }
            writer.write(response.toString())
            writer.write("\n")
            writer.flush()
        }
    }

    private suspend fun deviceInfo(): JSONObject {
        check(settings.adbVerified()) { "請先在 App 內完成 ADB 配對與連線測試" }
        val result = adb.verify(settings.adbHost(), settings.adbPort())
        return JSONObject().put("device", result)
    }

    private suspend fun tapTestZone(): JSONObject {
        check(settings.remoteTapEnabled()) { "遠端點擊尚未由使用者啟用" }
        check(settings.adbVerified()) { "請先完成 ADB 配對與連線測試" }
        val result = adb.tap(settings.adbHost(), settings.adbPort(), settings.tapX(), settings.tapY())
        return JSONObject().put("result", result)
    }

    override fun close() {
        acceptJob?.cancel()
        runCatching { server.close() }
    }
}
