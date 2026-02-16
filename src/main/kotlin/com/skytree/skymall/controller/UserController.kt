package com.skytree.skymall.controller

import com.skytree.skymall.dto.UpdateUserRequest
import com.skytree.skymall.dto.UserProfileResponse
import com.skytree.skymall.dto.UserResponse
import com.skytree.skymall.service.UserService
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/users")
class UserController(
    private val userService: UserService
) {
    @GetMapping
    fun getAllUsers(
        @PageableDefault(size = 20) pageable: Pageable
    ): Page<UserResponse> = userService.getAllUsers(pageable)

    @GetMapping("/{id}")
    fun getUser(@PathVariable id: Int): UserResponse =
        userService.getUser(id)

    @GetMapping("/username/{username}")
    fun getUserByUsername(@PathVariable username: String): UserResponse =
        userService.getUserByUsername(username)

    @GetMapping("/{id}/profile")
    fun getUserProfile(@PathVariable id: Int): UserProfileResponse =
        userService.getUserProfile(id)

    @PatchMapping("/{id}")
    fun updateUser(
        @PathVariable id: Int,
        @RequestBody req: UpdateUserRequest
    ): UserResponse = userService.updateUser(id, req)

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteUser(@PathVariable id: Int) =
        userService.deleteUser(id)
}
