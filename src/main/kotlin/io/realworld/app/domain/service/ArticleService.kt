package io.realworld.app.domain.service

import io.realworld.app.domain.Article
import io.realworld.app.domain.ArticlesDTO
import io.realworld.app.domain.repository.ArticleRepository

class ArticleService(private val articleRepository: ArticleRepository) {
    fun create(authorEmail: String, article: Article): Article {
        require(!article.title.isNullOrBlank()) { "Article title is required." }
        require(!article.description.isNullOrBlank()) { "Article description is required." }
        require(article.body.isNotBlank()) { "Article body is required." }
        return articleRepository.create(authorEmail, article)
    }

    fun favorite(userEmail: String, slug: String): Article {
        require(slug.isNotBlank()) { "Article slug is required." }
        return articleRepository.favorite(userEmail, slug)
    }

    fun unfavorite(userEmail: String, slug: String): Article {
        require(slug.isNotBlank()) { "Article slug is required." }
        return articleRepository.unfavorite(userEmail, slug)
    }

    fun findPopular(limit: Int, offset: Int, viewerEmail: String?): ArticlesDTO {
        require(limit >= 0) { "limit must be non-negative." }
        require(offset >= 0) { "offset must be non-negative." }
        val (articles, total) = articleRepository.findPopular(limit, offset, viewerEmail)
        return ArticlesDTO(articles, total)
    }
}
