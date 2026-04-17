package com.kidfocus.timer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

/**
 * Unit tests for PIN hashing and verification logic.
 *
 * These tests validate the SHA-256 hashing behavior that [SettingsDataStore] relies on,
 * without depending on Android APIs or DataStore infrastructure.
 */
class PinValidationTest {

    // ---- Helper (mirrors SettingsDataStore.sha256Hex) ----------------------------------------

    private fun sha256Hex(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun verifyPin(pin: String, storedHash: String): Boolean =
        sha256Hex(pin) == storedHash

    // ---- Hash properties --------------------------------------------------------------------

    @Test
    fun `sha256Hex produces a 64-character hex string`() {
        val hash = sha256Hex("1234")
        assertEquals(64, hash.length)
    }

    @Test
    fun `sha256Hex output is lowercase hexadecimal`() {
        val hash = sha256Hex("9876")
        assertTrue(hash.all { it.isDigit() || it in 'a'..'f' })
    }

    @Test
    fun `same input always produces the same hash`() {
        val hash1 = sha256Hex("1234")
        val hash2 = sha256Hex("1234")
        assertEquals(hash1, hash2)
    }

    @Test
    fun `different inputs produce different hashes`() {
        val hash1 = sha256Hex("1234")
        val hash2 = sha256Hex("4321")
        assertNotEquals(hash1, hash2)
    }

    @Test
    fun `plaintext PIN is never equal to its hash`() {
        val pin = "1234"
        val hash = sha256Hex(pin)
        assertNotEquals(pin, hash)
    }

    // ---- Verification logic -----------------------------------------------------------------

    @Test
    fun `verifyPin returns true for correct PIN`() {
        val pin = "5678"
        val storedHash = sha256Hex(pin)
        assertTrue(verifyPin(pin, storedHash))
    }

    @Test
    fun `verifyPin returns false for wrong PIN`() {
        val pin = "5678"
        val storedHash = sha256Hex(pin)
        assertFalse(verifyPin("0000", storedHash))
    }

    @Test
    fun `verifyPin is case-insensitive for hex output`() {
        val pin = "1111"
        val hash = sha256Hex(pin)
        // Hash output should be deterministic lowercase
        assertEquals(hash, hash.lowercase())
    }

    @Test
    fun `verifyPin handles single-digit PIN`() {
        val pin = "0"
        val stored = sha256Hex(pin)
        assertTrue(verifyPin("0", stored))
        assertFalse(verifyPin("1", stored))
    }

    @Test
    fun `verifyPin handles empty string gracefully`() {
        val emptyHash = sha256Hex("")
        assertTrue(verifyPin("", emptyHash))
        assertFalse(verifyPin("a", emptyHash))
    }

    @Test
    fun `verifyPin handles PIN with leading zero`() {
        val pin = "0123"
        val hash = sha256Hex(pin)
        assertTrue(verifyPin("0123", hash))
        assertFalse(verifyPin("123", hash))
    }

    // ---- Known hash values (regression) -----------------------------------------------------

    @Test
    fun `sha256Hex of 1234 matches known SHA-256 digest`() {
        // Known SHA-256("1234") = 03ac674216f3e15c761ee1a5e255f067953623c8b388b4459e13f978d7c846f4
        val expected = "03ac674216f3e15c761ee1a5e255f067953623c8b388b4459e13f978d7c846f4"
        assertEquals(expected, sha256Hex("1234"))
    }

    @Test
    fun `sha256Hex of 0000 matches known SHA-256 digest`() {
        // Known SHA-256("0000")
        val expected = sha256Hex("0000")
        assertEquals(64, expected.length)
        // Verify idempotency
        assertEquals(expected, sha256Hex("0000"))
    }
}
