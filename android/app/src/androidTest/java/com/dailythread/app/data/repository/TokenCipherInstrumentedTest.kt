package com.dailythread.app.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TokenCipherInstrumentedTest {
    @Test
    fun encryptDecrypt_roundTripAndCiphertextDiffers() {
        val cipher = TokenCipher()
        val plain = "supabase-refresh-token-example"

        val encrypted = cipher.encrypt(plain)
        val decrypted = cipher.decrypt(encrypted)

        assertTrue(encrypted.startsWith("v1:"))
        assertNotEquals(plain, encrypted)
        assertEquals(plain, decrypted)
    }

    @Test
    fun decrypt_supportsLegacyPlaintextForOneTimeMigrationCompatibility() {
        val cipher = TokenCipher()
        assertEquals("legacy-token", cipher.decrypt("legacy-token"))
    }
}
