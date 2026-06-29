package io.realworld.app.domain.service

import io.realworld.app.domain.Profile

class ProfileService(private val userService: UserService) {
    fun getProfile(viewerEmail: String?, username: String): Profile {
        require(username.isNotBlank()) { "Profile username is required." }
        val user = userService.findByUsername(username)
        val following = if (viewerEmail.isNullOrBlank()) {
            false
        } else {
            userService.getProfileByUsername(viewerEmail, username).following
        }
        return Profile(user.username, user.bio, user.image, following)
    }

    fun follow(email: String, usernameToFollow: String): Profile {
        require(usernameToFollow.isNotBlank()) { "Profile username is required." }
        return userService.follow(email, usernameToFollow)
    }

    fun unfollow(email: String, usernameToUnfollow: String): Profile {
        require(usernameToUnfollow.isNotBlank()) { "Profile username is required." }
        return userService.unfollow(email, usernameToUnfollow)
    }
}
