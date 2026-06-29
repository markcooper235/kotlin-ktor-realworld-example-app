package io.realworld.app.web.controllers

import io.ktor.application.ApplicationCall
import io.ktor.auth.authentication
import io.ktor.request.receive
import io.ktor.response.respond
import io.realworld.app.domain.ArticleDTO
import io.realworld.app.domain.ArticlesDTO
import io.realworld.app.domain.User
import io.realworld.app.domain.service.ArticleService

class ArticleController(private val articleService: ArticleService) {
    private fun ApplicationCall.currentUserEmail(): String? {
        return authentication.principal<User>()?.email
    }

    private fun ApplicationCall.intQueryParam(name: String, defaultValue: Int): Int {
        val value = request.queryParameters[name] ?: return defaultValue
        return value.toIntOrNull() ?: throw IllegalArgumentException("$name must be an integer.")
    }

    fun findBy(ctx: ApplicationCall): ArticlesDTO {
        return ArticlesDTO(listOf(), 1)
    }

    fun feed(ctx: ApplicationCall): ArticlesDTO {
        return ArticlesDTO(listOf(), 1)
    }

    suspend fun popular(ctx: ApplicationCall) {
        val limit = ctx.intQueryParam("limit", 20)
        val offset = ctx.intQueryParam("offset", 0)
        ctx.respond(articleService.findPopular(limit, offset, ctx.currentUserEmail()))
    }

    fun get(ctx: ApplicationCall): ArticleDTO {
        ctx.parameters["slug"]
        //                articleService.findBySlug(slug).apply {
//                    ctx.json(ArticleDTO(this))
//                }
        return ArticleDTO(null)
    }

    suspend fun create(ctx: ApplicationCall) {
        val email = ctx.currentUserEmail() ?: throw IllegalArgumentException("User not logged.")
        val article = ctx.receive<ArticleDTO>().article ?: throw IllegalArgumentException("Article is invalid.")
        ctx.respond(ArticleDTO(articleService.create(email, article)))
    }

    suspend fun update(ctx: ApplicationCall): ArticleDTO {
        val slug = ctx.parameters["slug"]
        ctx.receive<ArticleDTO>()
        //            articleService.update(slug, article).apply {
//                ctx.json(ArticleDTO(this))
//            }
        return ArticleDTO(null)
    }

    fun delete(ctx: ApplicationCall) {
        ctx.parameters["slug"]
        //            articleService.delete(slug)
    }

    suspend fun favorite(ctx: ApplicationCall) {
        val email = ctx.currentUserEmail() ?: throw IllegalArgumentException("User not logged.")
        val slug = ctx.parameters["slug"] ?: throw IllegalArgumentException("Article slug is required.")
        ctx.respond(ArticleDTO(articleService.favorite(email, slug)))
    }

    suspend fun unfavorite(ctx: ApplicationCall) {
        val email = ctx.currentUserEmail() ?: throw IllegalArgumentException("User not logged.")
        val slug = ctx.parameters["slug"] ?: throw IllegalArgumentException("Article slug is required.")
        ctx.respond(ArticleDTO(articleService.unfavorite(email, slug)))
    }
}
