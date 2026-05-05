package com.solodev.fleet.shared.utils

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.MGF1ParameterSpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource

/**
 * Unit tests for [RsaDecryptor].
 *
 * These tests verify that the backend's RSA-OAEP (SHA-256/MGF1-SHA-256) decryption
 * is compatible with what the browser's SubtleCrypto API produces.
 *
 * We simulate the browser's encryption using the same JVM cipher config to ensure
 * round-trip correctness: encrypt(plaintext) → decrypt → original plaintext.
 */
class RsaDecryptorTest {
    private lateinit var publicKey: PublicKey
    private lateinit var privateKey: PrivateKey
    private lateinit var publicKeyPem: String

    private val oaepParams =
        OAEPParameterSpec(
            "SHA-256",
            "MGF1",
            MGF1ParameterSpec.SHA256,
            PSource.PSpecified.DEFAULT,
        )

    @BeforeEach
    fun setUp() {
        // Generate a fresh 2048-bit RSA key pair for each test
        val gen = KeyPairGenerator.getInstance("RSA")
        gen.initialize(2048)
        val kp = gen.generateKeyPair()
        publicKey = kp.public
        privateKey = kp.private

        // Encode public key to PEM for loadPrivateKey-style pem testing
        val b64 = Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(publicKey.encoded)
        publicKeyPem = "-----BEGIN PUBLIC KEY-----\n$b64\n-----END PUBLIC KEY-----"
    }

    /** Simulates the browser's SubtleCrypto RSA-OAEP + SHA-256 encryption. */
    private fun simulateBrowserEncrypt(plainText: String): String {
        val cipher = Cipher.getInstance("RSA/ECB/OAEPPadding")
        cipher.init(Cipher.ENCRYPT_MODE, publicKey, oaepParams)
        return Base64.getEncoder().encodeToString(cipher.doFinal(plainText.toByteArray(Charsets.UTF_8)))
    }

    // ── RsaDecryptor.decrypt() ──────────────────────────────────────────────────

    @Test
    fun `decrypt should return original plaintext for a simple string`() {
        val plainText = "hello@fleet.ph"
        val cipherText = simulateBrowserEncrypt(plainText)

        val result = RsaDecryptor.decrypt(cipherText, privateKey)

        assertThat(result).isEqualTo(plainText)
    }

    @Test
    fun `decrypt should correctly decrypt an email address`() {
        val email = "renz.rellus@fleet.com"
        val cipherText = simulateBrowserEncrypt(email)

        val result = RsaDecryptor.decrypt(cipherText, privateKey)

        assertThat(result).isEqualTo(email)
    }

    @Test
    fun `decrypt should correctly decrypt a password with special characters`() {
        val password = "P@ssw0rd!#Fleet2025"
        val cipherText = simulateBrowserEncrypt(password)

        val result = RsaDecryptor.decrypt(cipherText, privateKey)

        assertThat(result).isEqualTo(password)
    }

    @Test
    fun `decrypt should handle ciphertext with escaped newlines from env formatting`() {
        val plainText = "sanitize@test.com"
        val cleanCipherText = simulateBrowserEncrypt(plainText)
        // Simulate the \n injection that happens when PowerShell writes to .env
        val dirtyCipherText = cleanCipherText.take(10) + "\\n" + cleanCipherText.drop(10)

        val result = RsaDecryptor.decrypt(dirtyCipherText, privateKey)

        assertThat(result).isEqualTo(plainText)
    }

    @Test
    fun `decrypt should throw for tampered or invalid ciphertext`() {
        val invalidCipherText = Base64.getEncoder().encodeToString("this is not valid RSA ciphertext".toByteArray())

        assertThatThrownBy { RsaDecryptor.decrypt(invalidCipherText, privateKey) }
            .isInstanceOf(Exception::class.java)
    }

    // ── RsaDecryptor.loadPrivateKey() ──────────────────────────────────────────

    @Test
    fun `loadPrivateKey should load a clean PKCS8 PEM key`() {
        val b64 = Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(privateKey.encoded)
        val pem = "-----BEGIN PRIVATE KEY-----\n$b64\n-----END PRIVATE KEY-----"

        val loaded = RsaDecryptor.loadPrivateKey(pem)

        assertThat(loaded.algorithm).isEqualTo("RSA")
        assertThat(loaded.encoded).isEqualTo(privateKey.encoded)
    }

    @Test
    fun `loadPrivateKey should handle escaped newlines from PowerShell env injection`() {
        val b64 = Base64.getEncoder().encodeToString(privateKey.encoded)
        // Simulate what PowerShell writes to .env: literal \n instead of real newlines
        val dirtyPem = "-----BEGIN PRIVATE KEY-----\\n$b64\\n-----END PRIVATE KEY-----"

        val loaded = RsaDecryptor.loadPrivateKey(dirtyPem)

        assertThat(loaded.algorithm).isEqualTo("RSA")
        assertThat(loaded.encoded).isEqualTo(privateKey.encoded)
    }

    // ── Full Round-Trip ─────────────────────────────────────────────────────────

    @Test
    fun `full round-trip encrypt then decrypt should return original credentials`() {
        val email = "admin@fleetdrive.com"
        val password = "SecureP@ss123!"

        val encryptedEmail = simulateBrowserEncrypt(email)
        val encryptedPassword = simulateBrowserEncrypt(password)

        val decryptedEmail = RsaDecryptor.decrypt(encryptedEmail, privateKey)
        val decryptedPassword = RsaDecryptor.decrypt(encryptedPassword, privateKey)

        assertThat(decryptedEmail).isEqualTo(email)
        assertThat(decryptedPassword).isEqualTo(password)
    }

    @Test
    fun `each encryption of same plaintext should produce different ciphertext (OAEP randomness)`() {
        val plainText = "same@input.com"

        val cipher1 = simulateBrowserEncrypt(plainText)
        val cipher2 = simulateBrowserEncrypt(plainText)

        // OAEP is randomized so each encryption is unique
        assertThat(cipher1).isNotEqualTo(cipher2)
        // But both should decrypt to the same value
        assertThat(RsaDecryptor.decrypt(cipher1, privateKey)).isEqualTo(plainText)
        assertThat(RsaDecryptor.decrypt(cipher2, privateKey)).isEqualTo(plainText)
    }
}
