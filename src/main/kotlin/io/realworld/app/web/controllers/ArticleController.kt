package io.realworld.app.web.controllers

import io.ktor.application.ApplicationCall
import io.ktor.auth.authentication
import io.ktor.http.HttpStatusCode
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

    suspend fun findBy(ctx: ApplicationCall) {
        val tag = ctx.request.queryParameters["tag"]
        val author = ctx.request.queryParameters["author"]
        val favorited = ctx.request.queryParameters["favorited"]
        val limit = ctx.intQueryParam("limit", 20)
        val offset = ctx.intQueryParam("offset", 0)
        ctx.respond(articleService.findBy(tag, author, favorited, limit, offset, ctx.currentUserEmail()))
    }

    suspend fun feed(ctx: ApplicationCall) {
        val email = ctx.currentUserEmail() ?: throw IllegalArgumentException("User not logged.")
        val limit = ctx.intQueryParam("limit", 20)
        val offset = ctx.intQueryParam("offset", 0)
        ctx.respond(articleService.findFeed(email, limit, offset))
    }

    suspend fun search(ctx: ApplicationCall) {
        val term = ctx.request.queryParameters["q"] ?: throw IllegalArgumentException("Search term is required.")
        val limit = ctx.intQueryParam("limit", 20)
        val offset = ctx.intQueryParam("offset", 0)
        ctx.respond(articleService.search(term, limit, offset, ctx.currentUserEmail()))
    }

    suspend fun get(ctx: ApplicationCall) {
        val slug = ctx.parameters["slug"] ?: throw IllegalArgumentException("Article slug is required.")
        ctx.respond(ArticleDTO(articleService.findBySlug(slug, ctx.currentUserEmail())))
    }

    suspend fun create(ctx: ApplicationCall) {
        val email = ctx.currentUserEmail() ?: throw IllegalArgumentException("User not logged.")
        val article = ctx.receive<ArticleDTO>().article ?: throw IllegalArgumentException("Article is invalid.")
        ctx.respond(ArticleDTO(articleService.create(email, article)))
    }

    suspend fun update(ctx: ApplicationCall) {
        val email = ctx.currentUserEmail() ?: throw IllegalArgumentException("User not logged.")
        val slug = ctx.parameters["slug"] ?: throw IllegalArgumentException("Article slug is required.")
        val article = ctx.receive<ArticleDTO>().article ?: throw IllegalArgumentException("Article is invalid.")
        ctx.respond(ArticleDTO(articleService.update(slug, article, email)))
    }

    suspend fun delete(ctx: ApplicationCall) {
        val email = ctx.currentUserEmail() ?: throw IllegalArgumentException("User not logged.")
        val slug = ctx.parameters["slug"] ?: throw IllegalArgumentException("Article slug is required.")
        articleService.delete(slug, email)
        ctx.respond(HttpStatusCode.OK)
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
