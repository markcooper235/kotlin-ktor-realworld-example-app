package io.realworld.app.domain.repository

import io.realworld.app.domain.exceptions.NotFoundException
import org.jetbrains.exposed.dao.LongIdTable
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction

internal object ActivityArticles : LongIdTable() {
    val slug = varchar("slug", 255).uniqueIndex()
    val authorId = long("author_id")
}

internal object ActivityComments : LongIdTable() {
    val articleId = long("article_id")
    val authorId = long("author_id")
    val body = text("body")
}

internal object ActivityFavorites : Table() {
    val articleId = long("article_id")
    val userId = long("user_id")
}

class ActivityRepository {
    init {
        transaction {
            SchemaUtils.create(ActivityArticles, ActivityComments, ActivityFavorites)
        }
    }

    fun createArticle(authorEmail: String, slugBase: String = "article"): Long {
        return transaction {
            val authorId = findUserIdByEmail(authorEmail)
            ActivityArticles.insertAndGetId { row ->
                row[slug] = uniqueSlug(slugBase)
                row[ActivityArticles.authorId] = authorId
            }.value
        }
    }

    fun addComment(authorEmail: String, articleId: Long, body: String = "comment"): Long {
        return transaction {
            findArticle(articleId)
            val authorId = findUserIdByEmail(authorEmail)
            ActivityComments.insertAndGetId { row ->
                row[ActivityComments.articleId] = articleId
                row[ActivityComments.authorId] = authorId
                row[ActivityComments.body] = body
            }.value
        }
    }

    fun favorite(userEmail: String, articleId: Long) {
        transaction {
            findArticle(articleId)
            val userId = findUserIdByEmail(userEmail)
            val exists = ActivityFavorites.select {
                ActivityFavorites.articleId eq articleId and (ActivityFavorites.userId eq userId)
            }.count() > 0
            if (!exists) {
                ActivityFavorites.insert { row ->
                    row[ActivityFavorites.articleId] = articleId
                    row[ActivityFavorites.userId] = userId
                }
            }
        }
    }

    fun countArticlesByUsername(username: String): Int {
        return transaction {
            val userId = findUserIdByUsername(username)
            ActivityArticles.select { ActivityArticles.authorId eq userId }.count().toInt()
        }
    }

    fun countCommentsByUsername(username: String): Int {
        return transaction {
            val userId = findUserIdByUsername(username)
            ActivityComments.select { ActivityComments.authorId eq userId }.count().toInt()
        }
    }

    fun countFavoritesByUsername(username: String): Int {
        return transaction {
            val userId = findUserIdByUsername(username)
            ActivityFavorites.select { ActivityFavorites.userId eq userId }.count().toInt()
        }
    }

    private fun findUserIdByEmail(email: String): Long {
        return Users.select { Users.email eq email }
            .map { it[Users.id].value }
            .firstOrNull()
            ?: throw NotFoundException("User not found.")
    }

    private fun findUserIdByUsername(username: String): Long {
        return Users.select { Users.username eq username }
            .map { it[Users.id].value }
            .firstOrNull()
            ?: throw NotFoundException("User not found.")
    }

    private fun findArticle(articleId: Long): Long {
        return ActivityArticles.select { ActivityArticles.id eq articleId }
            .map { it[ActivityArticles.id].value }
            .firstOrNull()
            ?: throw NotFoundException("Article not found.")
    }

    private fun uniqueSlug(slugBase: String): String {
        val base = slugBase.trim().toLowerCase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifBlank { "article" }
        var suffix = 1
        var candidate = base
        while (ActivityArticles.select { ActivityArticles.slug eq candidate }.count() > 0) {
            candidate = "$base-$suffix"
            suffix++
        }
        return candidate
    }
}
