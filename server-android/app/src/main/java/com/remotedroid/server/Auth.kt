package com.remotedroid.server

/**
 * Token verification. Constant-time comparison (reduces timing leaks).
 * Empty/null tokens are rejected.
 */
object Auth {
    fun isValid(expected: String, provided: String?): Boolean {
        if (expected.isEmpty() || provided.isNullOrEmpty()) return false
        val a = expected.toByteArray(Charsets.UTF_8)
        val b = provided.toByteArray(Charsets.UTF_8)
        if (a.size != b.size) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].toInt() xor b[i].toInt())
        return diff == 0
    }
}
