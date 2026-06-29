package io.realworld.app.domain.repository

import io.realworld.app.domain.Comment
import io.realworld.app.domain.exceptions.NotFoundException
import org.jetbrains.exposed.dao.LongIdTable
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.Date

internal object ArticleComments : LongIdTable() {
    val articleId = long("article_id")
    val authorId = long("author_id")
    val body = text("body")
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")
}

class CommentRepository {
    init {
        transaction {
            SchemaUtils.create(ArticleComments)
        }
    }

    fun add(slug: String, authorEmail: String, body: String): Comment = transaction {
        val article = Articles.select { Articles.slug eq slug }
            .firstOrNull()
            ?: throw NotFoundException("Article not found.")
        val author = Users.select { Users.email eq authorEmail }
            .map { Users.toDomain(it) }
            .firstOrNull()
            ?: throw NotFoundException("User not found.")
        val now = Date()
        val id = ArticleComments.insertAndGetId { row ->
            row[articleId] = article[Articles.id].value
            row[ArticleComments.authorId] = author.id!!
            row[ArticleComments.body] = body
            row[createdAt] = now.time
            row[updatedAt] = now.time
        }.value
        toDomain(ArticleComments.select { ArticleComments.id eq id }.first())
    }

    fun findBySlug(slug: String): List<Comment> = transaction {
        val article = Articles.select { Articles.slug eq slug }
            .firstOrNull()
            ?: throw NotFoundException("Article not found.")
        ArticleComments.select { ArticleComments.articleId eq article[Articles.id].value }
            .orderBy(ArticleComments.createdAt, SortOrder.ASC)
            .map { toDomain(it) }
    }

    fun delete(id: Long, slug: String) = transaction {
        val article = Articles.select { Articles.slug eq slug }
            .firstOrNull()
            ?: throw NotFoundException("Article not found.")
        val deleted = ArticleComments.deleteWhere {
            ArticleComments.id eq id and (ArticleComments.articleId eq article[Articles.id].value)
        }
        if (deleted == 0) {
            throw NotFoundException("Comment not found.")
        }
    }

    private fun toDomain(row: ResultRow): Comment {
        val author = Users.select { Users.id eq row[ArticleComments.authorId] }
            .map { Users.toDomain(it).copy(password = null) }
            .first()
        return Comment(
            id = row[ArticleComments.id].value,
            createdAt = Date(row[ArticleComments.createdAt]),
            updatedAt = Date(row[ArticleComments.updatedAt]),
            body = row[ArticleComments.body],
            author = author
        )
    }
}
