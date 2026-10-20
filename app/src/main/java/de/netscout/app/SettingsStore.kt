package de.netscout.app

import de.netscout.core.MessageKey
import de.netscout.core.SearchMessage
import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import de.netscout.data.ApiSettings
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class SettingsRead(val settings: ApiSettings = ApiSettings(), val warning: SearchMessage? = null)
interface SettingsStore {
    fun load(): SettingsRead
    fun save(settings: ApiSettings)
}

/** The AES key never leaves AndroidKeyStore; backups are disabled in the manifest. */
class EncryptedSettingsStore(context: Context) : SettingsStore {
    private val preferences = context.getSharedPreferences("source-access", Context.MODE_PRIVATE)
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("netscout-source-access", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("netscout-source-access", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }

    override fun load(): SettingsRead {
        val blob = preferences.getString("encrypted", null) ?: return SettingsRead()
        return runCatching {
            val encoded = Base64.decode(blob, Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, encoded.copyOfRange(0, 12)))
            DataInputStream(ByteArrayInputStream(cipher.doFinal(encoded.copyOfRange(12, encoded.size)))).use {
                SettingsRead(ApiSettings(it.readUTF(), it.readUTF(), it.readUTF(), it.readUTF()))
            }
        }.getOrElse { SettingsRead(warning = SearchMessage(MessageKey.ACCESS_LOAD_FAILED)) }
    }

    override fun save(settings: ApiSettings) {
        val bytes = ByteArrayOutputStream().apply {
            DataOutputStream(this).use {
                it.writeUTF(settings.searxngUrl); it.writeUTF(settings.youtubeApiKey)
                it.writeUTF(settings.redditAccessToken); it.writeUTF(settings.githubToken)
            }
        }.toByteArray()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val encoded = Base64.encodeToString(cipher.iv + cipher.doFinal(bytes), Base64.NO_WRAP)
        check(preferences.edit().putString("encrypted", encoded).commit()) { "Unable to persist settings" }
    }
}
