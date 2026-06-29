package io.realworld.app.web.controllers

import io.realworld.app.domain.Comment
import io.realworld.app.domain.CommentDTO
import io.realworld.app.domain.CommentsDTO
import io.realworld.app.web.rules.AppRule
import com.mashape.unirest.http.Unirest
import org.apache.http.HttpStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CommentControllerTest {
    companion object {
        private val iso8601WithTimezone = Regex("\"(createdAt|updatedAt)\":\"\\d{4}-\\d{2}-\\d{2}T[^\"\\\\]+(?:Z|[+-]\\d{2}:\\d{2})\"")
    }

    @Rule
    @JvmField
    val appRule = AppRule()

    @Test
    fun `add comment for article by slug`() {
        val responseArticle = appRule.http.createArticle()

        val comment = Comment(body = "Very carefully.")
        val response = appRule.http.post<CommentDTO>(
            "/api/articles/${responseArticle.body.article?.slug}/comments",
            CommentDTO(comment)
        )

        assertEquals(response.status, HttpStatus.SC_OK)
        assertEquals(response.body.comment?.body, comment.body)
    }

    @Test
    fun `get all comments for article by slug`() {
        val responseArticle = appRule.http.createArticle()

        val slug = responseArticle.body.article?.slug

        val comment = Comment(body = "Very carefully.")
        appRule.http.post<CommentDTO>(
            "/api/articles/$slug/comments",
            CommentDTO(comment)
        )

        val response = appRule.http.get<CommentsDTO>("/api/articles/$slug/comments")

        assertEquals(response.status, HttpStatus.SC_OK)
        assertTrue(response.body.comments.isNotEmpty())
        assertEquals(response.body.comments.first().body, comment.body)
    }

    @Test
    fun `delete comment for article by slug`() {
        val responseArticle = appRule.http.createArticle()

        val slug = responseArticle.body.article?.slug

        val comment = Comment(body = "Very carefully.")
        val responseAddComment = appRule.http.post<CommentDTO>(
            "/api/articles/$slug/comments",
            CommentDTO(comment)
        )

        val response = appRule.http.delete("/api/articles/$slug/comments/${responseAddComment.body.comment?.id}")

        assertEquals(response.status, HttpStatus.SC_OK)
    }

    @Test
    fun `comment responses use spec compliant timestamps`() {
        val responseArticle = appRule.http.createArticle()
        val slug = responseArticle.body.article?.slug

        appRule.http.post<CommentDTO>(
            "/api/articles/$slug/comments",
            CommentDTO(Comment(body = "Very carefully."))
        )

        val response = Unirest.get("${appRule.http.origin}/api/articles/$slug/comments")
            .headers(appRule.http.headers)
            .asString()

        assertEquals(HttpStatus.SC_OK, response.status)
        assertTrue(iso8601WithTimezone.containsMatchIn(response.body))
    }
}
