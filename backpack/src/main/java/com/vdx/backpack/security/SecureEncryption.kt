package com.vdx.backpack.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import timber.log.Timber
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object SecureEncryption {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "BackpackEncryptionKey"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH = 128

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply {
            load(null)
        }

        keyStore.getKey(KEY_ALIAS, null)?.let {
            return it as SecretKey
        }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )

        val keyGenParameterSpec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(false)
            .build()

        keyGenerator.init(keyGenParameterSpec)
        return keyGenerator.generateKey()
    }

    fun encryptFile(inputFile: File, outputFile: File): Result<Unit> {
        return try {
            val secretKey = getOrCreateKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)

            val iv = cipher.iv

            inputFile.inputStream().use { input ->
                outputFile.outputStream().use { output ->
                    output.write(iv.size)
                    output.write(iv)

                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        val encrypted = cipher.update(buffer, 0, bytesRead)
                        encrypted?.let { output.write(it) }
                    }

                    val finalBlock = cipher.doFinal()
                    output.write(finalBlock)
                }
            }
            Timber.d("File encrypted successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Encryption failed")
            Result.failure(e)
        }
    }

    fun decryptFile(inputFile: File, outputFile: File): Result<Unit> {
        return try {
            val secretKey = getOrCreateKey()

            inputFile.inputStream().use { input ->
                val ivSize = input.read()
                val iv = ByteArray(ivSize)
                input.read(iv)

                val cipher = Cipher.getInstance(TRANSFORMATION)
                val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
                cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

                outputFile.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        val decrypted = cipher.update(buffer, 0, bytesRead)
                        decrypted?.let { output.write(it) }
                    }

                    val finalBlock = cipher.doFinal()
                    output.write(finalBlock)
                }
            }
            Timber.d("File decrypted successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Decryption failed")
            Result.failure(e)
        }
    }
}
