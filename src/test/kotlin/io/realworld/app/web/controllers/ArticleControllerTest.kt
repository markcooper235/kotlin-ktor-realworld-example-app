package io.realworld.app.web.controllers

import io.realworld.app.domain.Article
import io.realworld.app.domain.ArticleDTO
import io.realworld.app.domain.ArticlesDTO
import io.realworld.app.domain.ProfileDTO
import io.realworld.app.web.ErrorResponse
import io.realworld.app.web.rules.AppRule
import io.realworld.app.web.util.HttpUtil
import org.apache.http.HttpStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ArticleControllerTest {
    @Rule
    @JvmField
    val appRule = AppRule()

    @Test
    fun `get all articles`() {
        appRule.http.createArticle()
        val http = HttpUtil(appRule.port)
        val response = http.get<ArticlesDTO>("/api/articles")

        assertEquals(response.status, HttpStatus.SC_OK)
        assertNotNull(response.body.articles)
        assertEquals(response.body.articles.size, response.body.articlesCount)
    }

    @Test
    fun `get all articles with auth`() {
        appRule.http.createArticle()
        val response = appRule.http.get<ArticlesDTO>("/api/articles")

        assertEquals(response.status, HttpStatus.SC_OK)
        assertNotNull(response.body.articles)
        assertEquals(response.body.articles.size, response.body.articlesCount)
        assertNotNull(response.body.articles.first())
        assertFalse(response.body.articles.first().title.isNullOrBlank())
        assertTrue(response.body.articles.first().tagList.isNotEmpty())
    }

    @Test
    fun `get all articles by author`() {
        val author = "user_name_test"
        val response = appRule.http.get<ArticlesDTO>("/api/articles?author=$author")

        assertEquals(response.status, HttpStatus.SC_OK)
        assertNotNull(response.body.articles)
        assertEquals(response.body.articles.size, response.body.articlesCount)
        response.body.articles.forEach {
            assertEquals(it.author?.username, author)
            assertFalse(it.title.isNullOrBlank())
            assertTrue(it.tagList.isNotEmpty())
        }
    }

    @Test
    fun `get all articles by author with auth`() {
        appRule.http.createArticle()
        val author = "user_name_test"
        val response = appRule.http.get<ArticlesDTO>("/api/articles?author=$author")

        assertEquals(response.status, HttpStatus.SC_OK)
        assertNotNull(response.body.articles)
        assertEquals(response.body.articles.size, response.body.articlesCount)
        response.body.articles.forEach {
            assertEquals(it.author?.username, author)
            assertFalse(it.title.isNullOrBlank())
            assertTrue(it.tagList.isNotEmpty())
        }
    }

    @Test
    fun `get all articles favorited by username`() {
        val responseCreate = appRule.http.createArticle()
        appRule.http.post<ArticleDTO>("/api/articles/${responseCreate.body.article?.slug}/favorite")

        val response = appRule.http.get<ArticlesDTO>("/api/articles?favorited=user_name_test")

        assertEquals(response.status, HttpStatus.SC_OK)
        assertNotNull(response.body.articles)
        assertEquals(response.body.articles.size, response.body.articlesCount)
        assertNotNull(response.body.articles.first())
        assertFalse(response.body.articles.first().title.isNullOrBlank())
        assertTrue(response.body.articles.first().tagList.isNotEmpty())
        assertTrue(response.body.articles.first().favorited)
        assertTrue(response.body.articles.first().favoritesCount > 0)
    }

    @Test
    fun `get all articles favorited by username with auth`() {
        val responseCreate = appRule.http.createArticle()
        appRule.http.post<ArticleDTO>("/api/articles/${responseCreate.body.article?.slug}/favorite")

        val response = appRule.http.get<ArticlesDTO>("/api/articles?favorited=${responseCreate.body.article?.author?.username}")

        assertEquals(response.status, HttpStatus.SC_OK)
        assertNotNull(response.body.articles)
        assertEquals(response.body.articles.size, response.body.articlesCount)
        assertNotNull(response.body.articles.first())
        assertFalse(response.body.articles.first().title.isNullOrBlank())
        assertTrue(response.body.articles.first().tagList.isNotEmpty())
    }

    @Test
    fun `get all articles by tag`() {
        val responseCreate = appRule.http.createArticle()
        val tag = responseCreate.body.article?.tagList?.first()
        val response = appRule.http.get<ArticlesDTO>("/api/articles?tag=${responseCreate.body.article?.tagList?.first()}")

        assertEquals(response.status, HttpStatus.SC_OK)
        assertNotNull(response.body.articles)
        assertEquals(response.body.articles.size, response.body.articlesCount)
        assertTrue(response.body.articles.first().tagList.contains(tag))
    }

    @Test
    fun `search articles by title and body`() {
        appRule.http.createUser("search_articles@valid_email.com", "search_user")
        appRule.http.post<ArticleDTO>("/api/articles", ArticleDTO(
            Article(
                title = "Ktor search title",
                description = "query endpoint",
                body = "This body mentions octopus ink.",
                tagList = listOf("search")
            )
        ))
        appRule.http.post<ArticleDTO>("/api/articles", ArticleDTO(
            Article(
                title = "Completely different",
                description = "query endpoint",
                body = "This body also mentions octopus patterns.",
                tagList = listOf("search")
            )
        ))
        appRule.http.post<ArticleDTO>("/api/articles", ArticleDTO(
            Article(
                title = "No match here",
                description = "query endpoint",
                body = "Nothing related.",
                tagList = listOf("search")
            )
        ))

        val response = appRule.http.get<ArticlesDTO>("/api/articles/search?q=octopus")

        assertEquals(response.status, HttpStatus.SC_OK)
        assertEquals(2, response.body.articlesCount)
        assertEquals(2, response.body.articles.size)
        assertTrue(response.body.articles.all {
            it.title.orEmpty().contains("octopus", ignoreCase = true) ||
                it.body.contains("octopus", ignoreCase = true)
        })
    }

    @Test
    fun `search articles paginates results`() {
        appRule.http.createUser("search_pagination@valid_email.com", "search_pagination")
        repeat(3) { index ->
            appRule.http.post<ArticleDTO>("/api/articles", ArticleDTO(
                Article(
                    title = "Search pagination $index",
                    description = "query endpoint",
                    body = "shared-term article $index",
                    tagList = listOf("search")
                )
            ))
        }

        val response = appRule.http.get<ArticlesDTO>("/api/articles/search?q=shared-term&limit=1&offset=1")

        assertEquals(response.status, HttpStatus.SC_OK)
        assertEquals(3, response.body.articlesCount)
        assertEquals(1, response.body.articles.size)
    }

    @Test
    fun `search articles requires query term`() {
        val response = appRule.http.get<ErrorResponse>("/api/articles/search")

        assertEquals(HttpStatus.SC_UNPROCESSABLE_ENTITY, response.status)
        assertTrue(response.body.errors["body"]?.contains("Search term is required.") == true)
    }

    @Test
    fun `create article`() {
        val article = Article(
            title = "Create How to train your dragon",
            description = "Ever wonder how?",
            body = "Very carefully.",
            tagList = listOf("create_article")
        )
        val response = appRule.http.createArticle(article)
        assertEquals(response.status, HttpStatus.SC_OK)
        assertNotNull(response.body.article)
        assertEquals(response.body.article?.title, article.title)
        assertEquals(response.body.article?.description, article.description)
        assertEquals(response.body.article?.body, article.body)
        assertEquals(response.body.article?.tagList, article.tagList)
    }

    @Test
    fun `get all articles of feed`() {
        appRule.http.createArticle()

        val http = HttpUtil(appRule.port)
        http.createUser("celeb_follow_profile@valid_email.com", "celeb_username")

        http.post<ProfileDTO>("/api/profiles/user_name_test/follow")

        val response = http.get<ArticlesDTO>("/api/articles/feed")

        assertEquals(response.status, HttpStatus.SC_OK)
        assertNotNull(response.body.articles)
        assertEquals(response.body.articles.size, response.body.articlesCount)
        assertNotNull(response.body.articles.first())
        assertFalse(response.body.articles.first().title.isNullOrBlank())
        assertTrue(response.body.articles.first().tagList.isNotEmpty())
    }

    @Test
    fun `get single article by slug`() {
        val responseArticle = appRule.http.createArticle()
        val slug = responseArticle.body.article?.slug
        val response = appRule.http.get<ArticleDTO>("/api/articles/$slug")

        assertEquals(response.status, HttpStatus.SC_OK)
        assertNotNull(response.body.article)
        assertNotNull(response.body.article?.body)
        assertFalse(response.body.article?.title.isNullOrBlank())
        assertNotNull(response.body.article?.description)
        assertTrue(response.body.article?.tagList?.isNotEmpty() ?: false)
    }

    @Test
    fun `update article by slug`() {
        val responseCreated = appRule.http.createArticle()
        val slug = responseCreated.body.article?.slug
        val article = Article(body = "Very carefully.", title = "Teste", description = "Teste Desc")
        val response = appRule.http.put<ArticleDTO>("/api/articles/$slug", ArticleDTO(article))

        assertEquals(response.status, HttpStatus.SC_OK)
        assertNotNull(response.body.article)
        assertEquals(response.body.article?.body, article.body)
        assertNotNull(response.body.article?.body)
        assertFalse(response.body.article?.title.isNullOrBlank())
        assertNotNull(response.body.article?.description)
        assertTrue(response.body.article?.tagList?.isNotEmpty() ?: false)
    }

    @Test
    fun `favorite article by slug`() {
        val article = Article(
            title = "slug test",
            description = "Ever wonder how?",
            body = "Very carefully.",
            tagList = listOf("favorite")
        )
        appRule.http.createArticle(article)
        val slug = "slug-test"
        val response = appRule.http.post<ArticleDTO>("/api/articles/$slug/favorite")

        assertEquals(response.status, HttpStatus.SC_OK)
        assertNotNull(response.body.article)
        assertNotNull(response.body.article?.body)
        assertFalse(response.body.article?.title.isNullOrBlank())
        assertNotNull(response.body.article?.description)
        assertTrue(response.body.article?.tagList?.isNotEmpty() ?: false)
    }

    @Test
    fun `unfavorite article by slug`() {
        val email = "unfavorite_article@valid_email.com"
        val password = "Test"
        appRule.http.registerUser(email, password, "user_name_test")
        appRule.http.loginAndSetTokenHeader(email, password)
        val article = Article(
            title = "slug test 2",
            description = "Ever wonder how?",
            body = "Very carefully.",
            tagList = listOf("unfavorite")
        )
        appRule.http.post<ArticleDTO>("/api/articles", ArticleDTO(article))
        val slug = "slug-test-2"
        val response = appRule.http.deleteWithResponseBody<ArticleDTO>("/api/articles/$slug/favorite")

        assertEquals(response.status, HttpStatus.SC_OK)
        assertNotNull(response.body.article)
        assertNotNull(response.body.article?.body)
        assertFalse(response.body.article?.title.isNullOrBlank())
        assertNotNull(response.body.article?.description)
        assertTrue(response.body.article?.tagList?.isNotEmpty() ?: false)
    }

    @Test
    fun `delete article by slug`() {
        val responseCreate = appRule.http.createArticle()
        val response = appRule.http.delete("/api/articles/${responseCreate.body.article?.slug}")

        assertEquals(response.status, HttpStatus.SC_OK)
    }
}
