package io.realworld.app.web.controllers

import io.ktor.application.ApplicationCall
import io.ktor.auth.authentication
import io.ktor.http.HttpStatusCode
import io.ktor.request.receive
import io.ktor.response.respond
import io.realworld.app.domain.CommentDTO
import io.realworld.app.domain.CommentsDTO
import io.realworld.app.domain.User
import io.realworld.app.domain.service.CommentService

class CommentController(private val commentService: CommentService) {
    suspend fun add(ctx: ApplicationCall) {
        val slug = ctx.parameters["slug"] ?: throw IllegalArgumentException("Article slug is required.")
        val email = ctx.authentication.principal<User>()?.email ?: throw IllegalArgumentException("User not logged.")
        val comment = ctx.receive<CommentDTO>().comment ?: throw IllegalArgumentException("Comment is invalid.")
        ctx.respond(CommentDTO(commentService.add(slug, email, comment)))
    }

    suspend fun findBySlug(ctx: ApplicationCall) {
        val slug = ctx.parameters["slug"] ?: throw IllegalArgumentException("Article slug is required.")
        ctx.respond(CommentsDTO(commentService.findBySlug(slug)))
    }

    suspend fun delete(ctx: ApplicationCall) {
        val slug = ctx.parameters["slug"] ?: throw IllegalArgumentException("Article slug is required.")
        val id = ctx.parameters["id"]?.toLongOrNull() ?: throw IllegalArgumentException("Comment id is required.")
        commentService.delete(id, slug)
        ctx.respond(HttpStatusCode.OK)
    }
}
