package info.pulipuli.app.androidmcpremotecontrol

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecureSettings(context: Context) {
    private val prefs = context.getSharedPreferences("poc-settings", Context.MODE_PRIVATE)
    private val keyAlias = "android-mcp-runtime-key-v1"

    fun saveTunnel(id: String, apiKey: String) {
        val bytes = apiKey.toByteArray(Charsets.UTF_8)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(bytes)
        prefs.edit()
            .putString("tunnel_id", id.trim())
            .putString("runtime_key_iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString("runtime_key_data", Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .apply()
    }

    fun tunnelId(): String = prefs.getString("tunnel_id", "").orEmpty()

    fun runtimeKey(): String {
        val ivText = prefs.getString("runtime_key_iv", null) ?: return ""
        val dataText = prefs.getString("runtime_key_data", null) ?: return ""
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKey(),
                GCMParameterSpec(128, Base64.decode(ivText, Base64.NO_WRAP)),
            )
            String(cipher.doFinal(Base64.decode(dataText, Base64.NO_WRAP)), Charsets.UTF_8)
        } catch (_: Exception) {
            ""
        }
    }

    fun saveAdbTarget(host: String, port: Int) {
        prefs.edit().putString("adb_host", host.trim()).putInt("adb_port", port).putBoolean("adb_verified", true).apply()
    }

    fun adbHost(): String = prefs.getString("adb_host", "192.168.88.124").orEmpty()
    fun adbPort(): Int = prefs.getInt("adb_port", 38587)
    fun adbVerified(): Boolean = prefs.getBoolean("adb_verified", false)

    fun remoteTapEnabled(): Boolean = prefs.getBoolean("remote_tap_enabled", false)
    fun setRemoteTapEnabled(enabled: Boolean) { prefs.edit().putBoolean("remote_tap_enabled", enabled).apply() }


    fun tunnelStatus(): String = prefs.getString("tunnel_status", "尚未啟動").orEmpty()
    fun setTunnelStatus(value: String) { prefs.edit().putString("tunnel_status", value).apply() }

    private fun getOrCreateKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(keyAlias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }
}
