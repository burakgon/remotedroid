package com.remotedroid

import com.remotedroid.server.Auth
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthTest {
    @Test
    fun rejectsNullEmptyMismatch() {
        assertFalse(Auth.isValid("secret", null))
        assertFalse(Auth.isValid("secret", ""))
        assertFalse(Auth.isValid("", "secret"))
        assertFalse(Auth.isValid("secret", "nope"))
        assertFalse(Auth.isValid("secret", "secre"))
    }

    @Test
    fun acceptsExactMatch() {
        assertTrue(Auth.isValid("s3cr3t-token-123", "s3cr3t-token-123"))
    }
}
