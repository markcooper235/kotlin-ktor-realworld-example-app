package io.realworld.app.domain.service

import io.realworld.app.domain.Profile
import io.realworld.app.domain.ProfileStats
import io.realworld.app.domain.repository.ActivityRepository
import io.realworld.app.domain.repository.UserRepository

class ProfileService(
    private val userRepository: UserRepository,
    private val activityRepository: ActivityRepository
) {
    fun getProfile(viewerEmail: String?, username: String): Profile {
        val user = userRepository.findByUsername(username)
            ?: throw IllegalArgumentException("Profile username is required.")
        val following = if (viewerEmail.isNullOrBlank()) {
            false
        } else {
            userRepository.findIsFollowUser(viewerEmail, user.id!!)
        }
        return Profile(user.username, user.bio, user.image, following)
    }

    fun follow(email: String, usernameToFollow: String): Profile {
        val user = userRepository.follow(email, usernameToFollow)
        return Profile(user.username, user.bio, user.image, true)
    }

    fun unfollow(email: String, usernameToUnfollow: String): Profile {
        val user = userRepository.unfollow(email, usernameToUnfollow)
        return Profile(user.username, user.bio, user.image, false)
    }

    fun stats(username: String): ProfileStats {
        require(username.isNotBlank()) { "Profile username is required." }
        userRepository.findByUsername(username) ?: throw io.realworld.app.domain.exceptions.NotFoundException("User not found to find.")
        return ProfileStats(
            articlesCount = activityRepository.countArticlesByUsername(username),
            commentsCount = activityRepository.countCommentsByUsername(username),
            favoritesCount = activityRepository.countFavoritesByUsername(username)
        )
    }
}
