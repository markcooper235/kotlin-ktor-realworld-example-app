package io.realworld.app.web.controllers

import io.realworld.app.domain.Article
import io.realworld.app.domain.ArticleDTO
import io.realworld.app.domain.ArticlesDTO
import io.realworld.app.web.ErrorResponse
import io.realworld.app.web.rules.AppRule
import io.realworld.app.web.util.HttpUtil
import org.apache.http.HttpStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PopularArticleFeedControllerTest {
    @Rule
    @JvmField
    val appRule = AppRule()

    @Test
    fun `get popular feed returns empty page when offset exceeds article count`() {
        seedPopularArticles()
        val response = HttpUtil(appRule.port).get<ArticlesDTO>("/api/articles/feed/popular?offset=99")

        assertEquals(HttpStatus.SC_OK, response.status)
        assertEquals(3, response.body.articlesCount)
        assertTrue(response.body.articles.isEmpty())
    }

    @Test
    fun `get popular feed sorted by favorites with pagination`() {
        val authorOne = HttpUtil(appRule.port)
        authorOne.createUser("author1@valid.com", "author1")
        val articleOne = authorOne.post<ArticleDTO>(
            "/api/articles",
            ArticleDTO(
                Article(
                    title = "Article One",
                    description = "Desc One",
                    body = "Body One"
                )
            )
        )

        val authorTwo = HttpUtil(appRule.port)
        authorTwo.createUser("author2@valid.com", "author2")
        val articleTwo = authorTwo.post<ArticleDTO>(
            "/api/articles",
            ArticleDTO(
                Article(
                    title = "Article Two",
                    description = "Desc Two",
                    body = "Body Two"
                )
            )
        )

        val authorThree = HttpUtil(appRule.port)
        authorThree.createUser("author3@valid.com", "author3")
        authorThree.post<ArticleDTO>(
            "/api/articles",
            ArticleDTO(
                Article(
                    title = "Article Three",
                    description = "Desc Three",
                    body = "Body Three"
                )
            )
        )

        val fanOne = HttpUtil(appRule.port)
        fanOne.createUser("fan1@valid.com", "fan1")
        fanOne.post<ArticleDTO>("/api/articles/${articleOne.body.article?.slug}/favorite")
        fanOne.post<ArticleDTO>("/api/articles/${articleTwo.body.article?.slug}/favorite")

        val fanTwo = HttpUtil(appRule.port)
        fanTwo.createUser("fan2@valid.com", "fan2")
        fanTwo.post<ArticleDTO>("/api/articles/${articleOne.body.article?.slug}/favorite")

        val response = HttpUtil(appRule.port).get<ArticlesDTO>("/api/articles/feed/popular?limit=2&offset=0")

        assertEquals(HttpStatus.SC_OK, response.status)
        assertEquals(3, response.body.articlesCount)
        assertEquals(2, response.body.articles.size)
        assertEquals(articleOne.body.article?.slug, response.body.articles[0].slug)
        assertEquals(2L, response.body.articles[0].favoritesCount)
        assertFalse(response.body.articles[0].favorited)
        assertEquals(articleTwo.body.article?.slug, response.body.articles[1].slug)
        assertEquals(1L, response.body.articles[1].favoritesCount)
    }

    @Test
    fun `get popular feed with offset returns later page`() {
        val response = seedPopularArticles()

        val paged = HttpUtil(appRule.port).get<ArticlesDTO>("/api/articles/feed/popular?limit=1&offset=1")

        assertEquals(HttpStatus.SC_OK, paged.status)
        assertEquals(response.second.body.article?.slug, paged.body.articles.single().slug)
        assertEquals(3, paged.body.articlesCount)
    }

    @Test
    fun `get popular feed marks article favorited for authenticated viewer`() {
        val seeded = seedPopularArticles()
        val viewer = HttpUtil(appRule.port)
        viewer.createUser("viewer@valid.com", "viewer")
        viewer.post<ArticleDTO>("/api/articles/${seeded.second.body.article?.slug}/favorite")

        val response = viewer.get<ArticlesDTO>("/api/articles/feed/popular")
        val viewerFavorite = response.body.articles.first { it.slug == seeded.second.body.article?.slug }

        assertEquals(HttpStatus.SC_OK, response.status)
        assertTrue(viewerFavorite.favorited)
        assertEquals(2L, response.body.articles.first().favoritesCount)
    }

    @Test
    fun `get popular feed rejects invalid limit`() {
        val response = HttpUtil(appRule.port).get<ErrorResponse>("/api/articles/feed/popular?limit=nope")

        assertEquals(HttpStatus.SC_UNPROCESSABLE_ENTITY, response.status)
        assertTrue(response.body.errors["body"]!!.contains("limit must be an integer."))
    }

    @Test
    fun `get popular feed rejects invalid negative offset`() {
        val response = HttpUtil(appRule.port).get<ErrorResponse>("/api/articles/feed/popular?offset=-1")

        assertEquals(HttpStatus.SC_UNPROCESSABLE_ENTITY, response.status)
        assertTrue(response.body.errors["body"]!!.contains("offset must be non-negative."))
    }

    @Test
    fun `favorite endpoint is idempotent for ranking count`() {
        val author = HttpUtil(appRule.port)
        val suffix = uniqueSuffix()
        author.createUser("idempotent-author-$suffix@valid.com", "idempotent-author-$suffix")
        val article = author.post<ArticleDTO>(
            "/api/articles",
            ArticleDTO(Article(title = "Stable Favorite", description = "Desc", body = "Body"))
        )

        val fan = HttpUtil(appRule.port)
        fan.createUser("stable-fan-$suffix@valid.com", "stable-fan-$suffix")
        fan.post<ArticleDTO>("/api/articles/${article.body.article?.slug}/favorite")
        fan.post<ArticleDTO>("/api/articles/${article.body.article?.slug}/favorite")

        val response = HttpUtil(appRule.port).get<ArticlesDTO>("/api/articles/feed/popular")
        val savedArticle = response.body.articles.first { it.slug == article.body.article?.slug }

        assertEquals(HttpStatus.SC_OK, response.status)
        assertEquals(1L, savedArticle.favoritesCount)
    }

    @Test
    fun `favorite endpoint returns not found for missing article`() {
        val user = HttpUtil(appRule.port)
        val suffix = uniqueSuffix()
        user.createUser("missing-favorite-$suffix@valid.com", "missing-favorite-$suffix")

        val response = user.post<ErrorResponse>("/api/articles/does-not-exist/favorite")

        assertEquals(HttpStatus.SC_NOT_FOUND, response.status)
        assertTrue(response.body.errors["body"]!!.contains("Article not found."))
    }

    @Test
    fun `create article validates required fields`() {
        val user = HttpUtil(appRule.port)
        val suffix = uniqueSuffix()
        user.createUser("invalid-article-$suffix@valid.com", "invalid-article-$suffix")

        val response = user.post<ErrorResponse>(
            "/api/articles",
            ArticleDTO(Article(title = null, description = "Desc", body = "Body"))
        )

        assertEquals(HttpStatus.SC_UNPROCESSABLE_ENTITY, response.status)
        assertTrue(response.body.errors["body"]!!.contains("Article title is required."))
    }

    private fun seedPopularArticles(): Pair<com.mashape.unirest.http.HttpResponse<ArticleDTO>, com.mashape.unirest.http.HttpResponse<ArticleDTO>> {
        val suffix = uniqueSuffix()
        val authorOne = HttpUtil(appRule.port)
        authorOne.createUser("seed-author1-$suffix@valid.com", "seed-author1-$suffix")
        val articleOne = authorOne.post<ArticleDTO>(
            "/api/articles",
            ArticleDTO(Article(title = "Seed One", description = "Desc One", body = "Body One"))
        )

        val authorTwo = HttpUtil(appRule.port)
        authorTwo.createUser("seed-author2-$suffix@valid.com", "seed-author2-$suffix")
        val articleTwo = authorTwo.post<ArticleDTO>(
            "/api/articles",
            ArticleDTO(Article(title = "Seed Two", description = "Desc Two", body = "Body Two"))
        )

        val authorThree = HttpUtil(appRule.port)
        authorThree.createUser("seed-author3-$suffix@valid.com", "seed-author3-$suffix")
        authorThree.post<ArticleDTO>(
            "/api/articles",
            ArticleDTO(Article(title = "Seed Three", description = "Desc Three", body = "Body Three"))
        )

        val fanOne = HttpUtil(appRule.port)
        fanOne.createUser("seed-fan1-$suffix@valid.com", "seed-fan1-$suffix")
        fanOne.post<ArticleDTO>("/api/articles/${articleOne.body.article?.slug}/favorite")
        fanOne.post<ArticleDTO>("/api/articles/${articleTwo.body.article?.slug}/favorite")

        val fanTwo = HttpUtil(appRule.port)
        fanTwo.createUser("seed-fan2-$suffix@valid.com", "seed-fan2-$suffix")
        fanTwo.post<ArticleDTO>("/api/articles/${articleOne.body.article?.slug}/favorite")

        return articleOne to articleTwo
    }

    private fun uniqueSuffix(): String = System.nanoTime().toString()
}
