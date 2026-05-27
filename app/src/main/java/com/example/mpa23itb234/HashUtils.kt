package com.example.mpa23itb234

import java.security.MessageDigest

object HashUtils {
    /**
     * Mã hóa mật khẩu sử dụng thuật toán SHA-256
     */
    fun sha256(input: String): String {
        val bytes = input.toByteArray()
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.fold("") { str, it -> str + "%02x".format(it) }
    }
}
