package info.pulipuli.app.androidmcpremotecontrol

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import go.mobiletunnel.Mobiletunnel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TunnelForegroundService : LifecycleService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val settings by lazy { SecureSettings(this) }
    private val adb by lazy { AdbController(this) }
    private var bridge: LocalAdbRpcServer? = null
    private var runtimeJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP) {
            serviceScope.launch { stopRuntime() }
            return START_NOT_STICKY
        }
        if (runtimeJob?.isActive != true) startRuntime()
        return START_STICKY
    }

    private fun startRuntime() {
        startForegroundCompat(buildNotification("準備啟動"))
        runtimeJob = serviceScope.launch {
            val tunnelId = settings.tunnelId()
            val apiKey = settings.runtimeKey()
            if (tunnelId.isBlank() || apiKey.isBlank()) {
                settings.setTunnelStatus("請先輸入 Tunnel ID 與 Runtime API key")
                stopSelf()
                return@launch
            }
            if (!settings.adbVerified()) {
                settings.setTunnelStatus("請先完成 ADB 配對與本機連線測試")
                stopSelf()
                return@launch
            }
            try {
                val localBridge = LocalAdbRpcServer(adb, settings, serviceScope)
                bridge = localBridge
                localBridge.start()
                withContext(Dispatchers.Main) {
                    Mobiletunnel.start(tunnelId, apiKey, localBridge.port, localBridge.token)
                }
                while (isActive) {
                    val status = withContext(Dispatchers.Main) { Mobiletunnel.status() }
                    settings.setTunnelStatus(status)
                    updateNotification(status)
                    delay(1_000)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                val detail = error.message?.take(160) ?: error.javaClass.simpleName
                settings.setTunnelStatus("啟動失敗：$detail")
                updateNotification("啟動失敗")
            } finally {
                bridge?.close()
                bridge = null
            }
        }
    }

    private suspend fun stopRuntime() {
        runCatching { withContext(Dispatchers.Main) { Mobiletunnel.stop() } }
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline) {
            val status = runCatching { withContext(Dispatchers.Main) { Mobiletunnel.status() } }.getOrDefault("stopped")
            if (status == "stopped") break
            delay(150)
        }
        bridge?.close()
        bridge = null
        settings.setTunnelStatus("已停止")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        serviceScope.cancel()
        runCatching { Mobiletunnel.stop() }
        bridge?.close()
        super.onDestroy()
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(status: String): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, TunnelForegroundService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setContentTitle("Android MCP Tunnel")
            .setContentText(status.take(80))
            .setContentIntent(openApp)
            .setOngoing(status != "已停止")
            .addAction(0, "停止", stop)
            .build()
    }

    private fun updateNotification(status: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(status))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Android MCP Tunnel", NotificationManager.IMPORTANCE_LOW),
            )
        }
    }

    companion object {
        const val ACTION_START = "info.pulipuli.app.androidmcpremotecontrol.START_TUNNEL"
        const val ACTION_STOP = "info.pulipuli.app.androidmcpremotecontrol.STOP_TUNNEL"
        private const val CHANNEL_ID = "android_mcp_tunnel"
        private const val NOTIFICATION_ID = 1701
    }
}
