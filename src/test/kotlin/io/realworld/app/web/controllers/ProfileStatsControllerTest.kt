package io.realworld.app.web.controllers

import io.realworld.app.domain.ProfileStatsDTO
import io.realworld.app.web.ErrorResponse
import io.realworld.app.web.rules.AppRule
import io.realworld.app.web.util.HttpUtil
import io.realworld.app.domain.repository.ActivityRepository
import org.apache.http.HttpStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ProfileStatsControllerTest {
    @Rule
    @JvmField
    val appRule = AppRule()

    @Test
    fun `get profile stats for existing user`() {
        val username = "stats_user"
        val authorEmail = "stats_user@valid.com"
        val fanEmail = "fan_stats@valid.com"
        val commenterEmail = "commenter_stats@valid.com"

        appRule.http.registerUser(authorEmail, "Test", username)
        appRule.http.registerUser(fanEmail, "Test", "fan_stats")
        appRule.http.registerUser(commenterEmail, "Test", "commenter_stats")

        val activityRepository = ActivityRepository()
        val articleOne = activityRepository.createArticle(authorEmail, "stats-one")
        val articleTwo = activityRepository.createArticle(authorEmail, "stats-two")
        activityRepository.addComment(authorEmail, articleOne, "author-comment")
        activityRepository.addComment(commenterEmail, articleOne, "external-comment")
        activityRepository.favorite(authorEmail, articleOne)
        activityRepository.favorite(authorEmail, articleTwo)
        activityRepository.favorite(fanEmail, articleOne)

        val response = HttpUtil(appRule.port).get<ProfileStatsDTO>("/api/profiles/$username/stats")

        assertEquals(HttpStatus.SC_OK, response.status)
        assertEquals(2, response.body.stats.articlesCount)
        assertEquals(1, response.body.stats.commentsCount)
        assertEquals(2, response.body.stats.favoritesCount)
    }

    @Test
    fun `get profile stats returns zero counts for user without activity`() {
        appRule.http.registerUser("inactive@valid.com", "Test", "inactive")

        val response = HttpUtil(appRule.port).get<ProfileStatsDTO>("/api/profiles/inactive/stats")

        assertEquals(HttpStatus.SC_OK, response.status)
        assertEquals(0, response.body.stats.articlesCount)
        assertEquals(0, response.body.stats.commentsCount)
        assertEquals(0, response.body.stats.favoritesCount)
    }

    @Test
    fun `get profile stats returns not found for missing user`() {
        val response = HttpUtil(appRule.port).get<ErrorResponse>("/api/profiles/missing-user/stats")

        assertEquals(HttpStatus.SC_NOT_FOUND, response.status)
        assertTrue(response.body.errors["body"]!!.contains("User not found to find."))
    }
}
