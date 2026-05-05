package com.solodev.fleet.shared.utils

import java.security.KeyFactory
import java.security.PrivateKey
import java.security.spec.MGF1ParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource

/**
 * Utility for RSA decryption on the backend.
 *
 * Uses RSA/ECB/OAEPPadding with explicit OAEPParameterSpec (SHA-256 for both OAEP and MGF1)
 * to match the browser's SubtleCrypto RSA-OAEP + SHA-256 encryption exactly.
 *
 * ⚠️ IMPORTANT: Java's "OAEPWithSHA-256AndMGF1Padding" uses SHA-1 for MGF1 by default,
 * which is INCOMPATIBLE with WebCrypto. We must use OAEPParameterSpec to override this.
 */
object RsaDecryptor {
    private val oaepParams =
        OAEPParameterSpec(
            "SHA-256",
            "MGF1",
            MGF1ParameterSpec.SHA256, // Match WebCrypto: MGF1 with SHA-256
            PSource.PSpecified.DEFAULT,
        )

    /**
     * Decrypts a Base64 encoded RSA-OAEP ciphertext using the provided private key.
     */
    fun decrypt(
        base64Ciphertext: String,
        privateKey: PrivateKey,
    ): String {
        // Sanitize: strip escaped newlines or whitespace from env formatting
        val sanitized =
            base64Ciphertext
                .replace("\\n", "")
                .replace("\\r", "")
                .replace("\\s".toRegex(), "")

        val cipher = Cipher.getInstance("RSA/ECB/OAEPPadding")
        cipher.init(Cipher.DECRYPT_MODE, privateKey, oaepParams)

        val encryptedBytes = Base64.getDecoder().decode(sanitized)
        val decryptedBytes = cipher.doFinal(encryptedBytes)

        return String(decryptedBytes, Charsets.UTF_8)
    }

    /**
     * Loads a PrivateKey from a PEM string (PKCS#8 format).
     * Handles escaped newlines from .env file formatting.
     */
    fun loadPrivateKey(pem: String): PrivateKey {
        val cleanPem =
            pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("\\n", "")
                .replace("\\r", "")
                .replace("\\s".toRegex(), "")

        val decoded = Base64.getDecoder().decode(cleanPem)
        val spec = PKCS8EncodedKeySpec(decoded)
        val kf = KeyFactory.getInstance("RSA")
        return kf.generatePrivate(spec)
    }
}
