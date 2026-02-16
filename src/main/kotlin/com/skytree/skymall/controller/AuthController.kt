package com.skytree.skymall.controller

import com.skytree.skymall.dto.LoginRequest
import com.skytree.skymall.dto.RegisterRequest
import com.skytree.skymall.dto.TokenResponse
import com.skytree.skymall.service.AuthService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authService: AuthService
) {
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    fun register(@RequestBody req: RegisterRequest): TokenResponse =
        authService.register(req)

    @PostMapping("/login")
    fun login(@RequestBody req: LoginRequest): TokenResponse =
        authService.login(req)
}
