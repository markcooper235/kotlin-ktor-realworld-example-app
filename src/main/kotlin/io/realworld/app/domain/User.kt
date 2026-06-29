package io.realworld.app.domain

import io.ktor.auth.Principal
import io.realworld.app.ext.isEmailValid

data class UserDTO(val user: User? = null) {
    fun validRegister(): User {
        require(
            user != null &&
                user.email.isEmailValid() &&
                !user.password.isNullOrBlank() &&
                !user.username.isNullOrBlank()
        ) { "User is invalid." }
        return user
    }

    fun validLogin(): User {
        require(
            user != null &&
                user.email.isEmailValid() &&
                !user.password.isNullOrBlank()
        ) { "Email or password is invalid." }
        return user
    }

    fun validToUpdate(): User {
        require(
            user != null &&
                user.email.isEmailValid() &&
                (user.password == null || user.password.isNotBlank()) &&
                (user.username == null || user.username.isNotBlank()) &&
                (user.bio == null || user.bio.isNotBlank()) &&
                (user.image == null || user.image.isNotBlank())
        ) { "User is invalid." }
        return user
    }
}

data class User(
    val id: Long? = null,
    val email: String,
    val token: String? = null,
    val username: String? = null,
    val password: String? = null,
    val bio: String? = null,
    val image: String? = null
) : Principal
