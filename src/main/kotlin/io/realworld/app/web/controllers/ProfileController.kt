package io.realworld.app.web.controllers

import io.ktor.application.ApplicationCall
import io.ktor.auth.authentication
import io.ktor.response.respond
import io.realworld.app.domain.ProfileDTO
import io.realworld.app.domain.ProfileStatsDTO
import io.realworld.app.domain.User
import io.realworld.app.domain.service.ProfileService

class ProfileController(private val profileService: ProfileService) {
    private fun ApplicationCall.currentUserEmail(): String? = authentication.principal<User>()?.email

    suspend fun get(ctx: ApplicationCall) {
        val username = ctx.parameters["username"] ?: throw IllegalArgumentException("Profile username is required.")
        ctx.respond(ProfileDTO(profileService.getProfile(ctx.currentUserEmail(), username)))
    }

    suspend fun follow(ctx: ApplicationCall) {
        val email = ctx.currentUserEmail() ?: throw IllegalArgumentException("User not logged.")
        val username = ctx.parameters["username"] ?: throw IllegalArgumentException("Profile username is required.")
        ctx.respond(ProfileDTO(profileService.follow(email, username)))
    }

    suspend fun unfollow(ctx: ApplicationCall) {
        val email = ctx.currentUserEmail() ?: throw IllegalArgumentException("User not logged.")
        val username = ctx.parameters["username"] ?: throw IllegalArgumentException("Profile username is required.")
        ctx.respond(ProfileDTO(profileService.unfollow(email, username)))
    }

    suspend fun stats(ctx: ApplicationCall) {
        val username = ctx.parameters["username"] ?: throw IllegalArgumentException("Profile username is required.")
        ctx.respond(ProfileStatsDTO(profileService.stats(username)))
    }
}
