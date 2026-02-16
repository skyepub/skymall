package com.skytree.skymall.config

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.*
import javax.crypto.SecretKey

/**
 * JWT 토큰 생성/검증
 */
@Component
class JwtProvider(
    @Value("\${jwt.secret:skymall-secret-key-must-be-at-least-32-bytes-long}")
    private val secret: String,

    @Value("\${jwt.expiration:86400000}")  // 24시간
    private val expirationMs: Long
) {
    private val key: SecretKey by lazy {
        Keys.hmacShaKeyFor(secret.toByteArray())
    }

    fun generateToken(userId: Int, username: String, role: String): String =
        Jwts.builder()
            .subject(userId.toString())
            .claim("username", username)
            .claim("role", role)
            .issuedAt(Date())
            .expiration(Date(System.currentTimeMillis() + expirationMs))
            .signWith(key)
            .compact()

    fun validateToken(token: String): Boolean =
        try {
            Jwts.parser().verifyWith(key).build().parseSignedClaims(token)
            true
        } catch (e: Exception) {
            false
        }

    fun getUserId(token: String): Int =
        Jwts.parser().verifyWith(key).build()
            .parseSignedClaims(token).payload.subject.toInt()

    fun getUsername(token: String): String =
        Jwts.parser().verifyWith(key).build()
            .parseSignedClaims(token).payload["username"] as String

    fun getRole(token: String): String =
        Jwts.parser().verifyWith(key).build()
            .parseSignedClaims(token).payload["role"] as String
}
