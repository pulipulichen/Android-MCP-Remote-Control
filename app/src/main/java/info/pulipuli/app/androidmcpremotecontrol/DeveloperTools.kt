package info.pulipuli.app.androidmcpremotecontrol

import io.github.rhythmcache.adb.AdbClient
import io.github.rhythmcache.adb.ShellChunk
import java.net.URL
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.UUID
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

internal class DeveloperTools(
    private val adb: AdbController,
    private val settings: SecureSettings,
) {
    private val operation = Mutex()

    suspend fun call(method: String, args: JSONObject): JSONObject =
        withTimeout(if (method == "install_apk") 180_000L else 30_000L) {
            operation.withLock {
                check(settings.adbVerified()) { "請先完成 ADB 配對與連線測試" }
                check(settings.developerToolsEnabled()) { "請先在 App 內開啟「允許遠端開發工具」" }
                adb.withClient(settings.adbHost(), settings.adbPort()) { client ->
                    when (method) {
                        "list_packages" -> shell(client, "pm list packages" + if (args.optBoolean("third_party_only")) " -3" else "")
                        "package_info" -> shell(client, "dumpsys package ${packageName(args)}")
                        "launch_app" -> shell(client, "monkey -p ${packageName(args)} -c android.intent.category.LAUNCHER 1")
                        "logcat" -> shell(client, "logcat -d -t ${args.optInt("lines", 100).also { require(it in 1..1000) }}")
                        "adb_shell" -> shell(client, args.getString("command").also {
                            require(it.isNotBlank() && it.length <= 4096 && '\u0000' !in it) { "Invalid shell command" }
                        })
                        "install_apk" -> install(client, args)
                        "uninstall_app" -> {
                            val name = packageName(args)
                            require(name != APP_PACKAGE) { "Use Android Settings to uninstall the remote control app" }
                            val keepData = if (args.optBoolean("keep_data")) "-k " else ""
                            packageChange(client, "pm uninstall ${keepData}$name")
                        }
                        else -> throw IllegalArgumentException("Unsupported bridge method")
                    }
                }
            }
        }

    private suspend fun install(client: AdbClient, args: JSONObject): JSONObject {
        val url = URL(args.getString("url"))
        require(url.protocol == "https" && url.userInfo == null) { "Use an HTTPS APK URL without embedded credentials" }
        val checksum = args.optString("sha256")
        require(checksum.isEmpty() || checksum.matches(Regex("[0-9a-fA-F]{64}"))) { "Invalid SHA-256" }
        val remotePath = "/data/local/tmp/android-mcp-${UUID.randomUUID()}.apk"
        val connection = url.openConnection() as HttpsURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        val digest = MessageDigest.getInstance("SHA-256")
        try {
            check(connection.responseCode == 200) { "APK download failed (HTTP ${connection.responseCode})" }
            require(connection.contentLengthLong <= MAX_APK_BYTES) { "APK exceeds 256 MiB" }
            DigestInputStream(connection.inputStream, digest).use { input ->
                client.sync.push(input, remotePath, onProgress = {
                    require(it <= MAX_APK_BYTES) { "APK exceeds 256 MiB" }
                })
            }
            val actualChecksum = digest.digest().joinToString("") { "%02x".format(it) }
            require(checksum.isEmpty() || actualChecksum.equals(checksum, ignoreCase = true)) { "APK SHA-256 mismatch" }
            check(settings.developerToolsEnabled()) { "Remote developer tools were disabled" }
            return packageChange(client, "pm install -r $remotePath").put("sha256", actualChecksum)
        } finally {
            connection.disconnect()
            withContext(NonCancellable) {
                runCatching { withTimeout(5_000) { shell(client, "rm -f $remotePath") } }
            }
        }
    }

    private fun packageName(args: JSONObject): String = args.getString("package_name").also {
        require(it.length <= 255 && it.matches(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)*"))) {
            "Invalid Android package name"
        }
    }

    private suspend fun packageChange(client: AdbClient, command: String): JSONObject {
        val result = shell(client, command)
        check(result.getInt("exit_code") == 0 && result.getString("stdout").lineSequence().any { it.trim() == "Success" }) {
            listOf(result.getString("stdout"), result.getString("stderr")).joinToString("\n").trim()
                .ifBlank { "Android package operation failed" }
        }
        return result
    }

    private suspend fun shell(client: AdbClient, command: String): JSONObject {
        val stdout = StringBuilder()
        val stderr = StringBuilder()
        var exitCode: Int? = null
        var truncated = false
        fun append(buffer: StringBuilder, chunk: String) {
            val remaining = MAX_OUTPUT_CHARS - buffer.length
            buffer.append(chunk, 0, minOf(remaining, chunk.length))
            truncated = truncated || chunk.length > remaining
        }
        client.shellFlow(command).collect { chunk ->
            when (chunk) {
                is ShellChunk.Stdout -> append(stdout, chunk.text)
                is ShellChunk.Stderr -> append(stderr, chunk.text)
                is ShellChunk.Exit -> exitCode = chunk.code
            }
        }
        return JSONObject()
            .put("stdout", stdout.toString())
            .put("stderr", stderr.toString())
            .put("exit_code", checkNotNull(exitCode) { "ADB shell closed before returning an exit code" })
            .put("truncated", truncated)
    }

    private companion object {
        const val MAX_OUTPUT_CHARS = 32_768
        const val MAX_APK_BYTES = 256L * 1024 * 1024
        const val APP_PACKAGE = "info.pulipuli.app.androidmcpremotecontrol"
    }
}
