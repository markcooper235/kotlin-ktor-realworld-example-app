package io.realworld.app.domain.service

import io.realworld.app.domain.Article
import io.realworld.app.domain.ArticlesDTO
import io.realworld.app.domain.repository.ArticleRepository

class ArticleService(
    private val articleRepository: ArticleRepository
) {
    fun create(authorEmail: String, article: Article): Article {
        require(!article.title.isNullOrBlank()) { "Article title is required." }
        require(!article.description.isNullOrBlank()) { "Article description is required." }
        require(article.body.isNotBlank()) { "Article body is required." }
        return articleRepository.create(authorEmail, article)
    }

    fun findBy(
        tag: String?,
        author: String?,
        favorited: String?,
        limit: Int,
        offset: Int,
        viewerEmail: String?
    ): ArticlesDTO {
        require(limit >= 0) { "limit must be non-negative." }
        require(offset >= 0) { "offset must be non-negative." }
        val (articles, total) = articleRepository.findBy(tag, author, favorited, limit, offset, viewerEmail)
        return ArticlesDTO(articles, total)
    }

    fun findFeed(userEmail: String, limit: Int, offset: Int): ArticlesDTO {
        require(limit >= 0) { "limit must be non-negative." }
        require(offset >= 0) { "offset must be non-negative." }
        val (articles, total) = articleRepository.findFeed(userEmail, limit, offset)
        return ArticlesDTO(articles, total)
    }

    fun search(term: String, limit: Int, offset: Int, viewerEmail: String?): ArticlesDTO {
        require(term.isNotBlank()) { "Search term is required." }
        require(limit >= 0) { "limit must be non-negative." }
        require(offset >= 0) { "offset must be non-negative." }
        val (articles, total) = articleRepository.search(term, limit, offset, viewerEmail)
        return ArticlesDTO(articles, total)
    }

    fun findBySlug(slug: String, viewerEmail: String?): Article {
        require(slug.isNotBlank()) { "Article slug is required." }
        return articleRepository.findBySlug(slug, viewerEmail)
    }

    fun update(slug: String, article: Article, viewerEmail: String): Article {
        require(slug.isNotBlank()) { "Article slug is required." }
        require(article.body.isNotBlank()) { "Article body is required." }
        return articleRepository.update(slug, article, viewerEmail)
    }

    fun delete(slug: String, viewerEmail: String) {
        require(slug.isNotBlank()) { "Article slug is required." }
        articleRepository.delete(slug, viewerEmail)
    }

    fun favorite(userEmail: String, slug: String): Article {
        require(slug.isNotBlank()) { "Article slug is required." }
        return articleRepository.favorite(userEmail, slug)
    }

    fun unfavorite(userEmail: String, slug: String): Article {
        require(slug.isNotBlank()) { "Article slug is required." }
        return articleRepository.unfavorite(userEmail, slug)
    }
}
