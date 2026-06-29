package io.realworld.app.domain.repository

import io.realworld.app.domain.Article
import io.realworld.app.domain.exceptions.NotFoundException
import org.jetbrains.exposed.dao.LongIdTable
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.Date

internal object Articles : LongIdTable() {
    val slug = varchar("slug", 255).uniqueIndex()
    val title = varchar("title", 255)
    val description = varchar("description", 500)
    val body = text("body")
    val authorId = long("author_id")
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")
}

internal object ArticleTags : Table() {
    val articleId = long("article_id")
    val tagId = long("tag_id")
}

internal object Favorites : Table() {
    val articleId = long("article_id")
    val userId = long("user_id")
}

class ArticleRepository {
    init {
        transaction {
            SchemaUtils.create(Articles, ArticleTags, Favorites)
        }
    }

    fun create(authorEmail: String, article: Article): Article {
        return transaction {
            val author = Users.select { Users.email eq authorEmail }
                .map { Users.toDomain(it) }
                .firstOrNull()
                ?: throw NotFoundException("Author not found.")
            val now = Date()
            val slugValue = uniqueSlug(article.title.orEmpty())
            val articleId = Articles.insertAndGetId { row ->
                row[slug] = slugValue
                row[title] = article.title.orEmpty()
                row[description] = article.description.orEmpty()
                row[body] = article.body
                row[authorId] = author.id!!
                row[createdAt] = now.time
                row[updatedAt] = now.time
            }.value

            article.tagList
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .forEach { tagName ->
                    val tagId = Tags.select { Tags.name eq tagName }
                        .map { it[Tags.id].value }
                        .firstOrNull()
                        ?: Tags.insertAndGetId { row -> row[name] = tagName }.value
                    val exists = ArticleTags.select {
                        ArticleTags.articleId eq articleId and (ArticleTags.tagId eq tagId)
                    }.count() > 0
                    if (!exists) {
                        ArticleTags.insert { row ->
                            row[ArticleTags.articleId] = articleId
                            row[ArticleTags.tagId] = tagId
                        }
                    }
                }

            toDomain(
                Articles.select { Articles.id eq articleId }
                    .first(),
                viewerId = author.id
            )
        }
    }

    fun favorite(userEmail: String, slug: String): Article {
        return transaction {
            val user = Users.select { Users.email eq userEmail }
                .map { Users.toDomain(it) }
                .firstOrNull()
                ?: throw NotFoundException("User not found.")
            val articleId = findArticleRowBySlug(slug)[Articles.id].value
            val alreadyFavorited = Favorites.select {
                Favorites.articleId eq articleId and (Favorites.userId eq user.id!!)
            }.count() > 0
            if (!alreadyFavorited) {
                Favorites.insert { row ->
                    row[Favorites.articleId] = articleId
                    row[Favorites.userId] = user.id!!
                }
            }
            toDomain(findArticleRowBySlug(slug), viewerId = user.id)
        }
    }

    fun unfavorite(userEmail: String, slug: String): Article {
        return transaction {
            val user = Users.select { Users.email eq userEmail }
                .map { Users.toDomain(it) }
                .firstOrNull()
                ?: throw NotFoundException("User not found.")
            val articleId = findArticleRowBySlug(slug)[Articles.id].value
            Favorites.deleteWhere {
                Favorites.articleId eq articleId and (Favorites.userId eq user.id!!)
            }
            toDomain(findArticleRowBySlug(slug), viewerId = user.id)
        }
    }

    fun findPopular(limit: Int, offset: Int, viewerEmail: String?): Pair<List<Article>, Int> {
        return transaction {
            val viewerId = viewerEmail?.let { email ->
                Users.select { Users.email eq email }
                    .map { it[Users.id].value }
                    .firstOrNull()
            }
            val articles = Articles.selectAll()
                .orderBy(Articles.createdAt, SortOrder.DESC)
                .map { row -> toDomain(row, viewerId) }
                .sortedWith(
                    compareByDescending<Article> { it.favoritesCount }
                        .thenByDescending { it.createdAt?.time ?: 0L }
                )
            val total = articles.size
            val paged = articles.drop(offset).take(limit)
            paged to total
        }
    }

    private fun toDomain(row: ResultRow, viewerId: Long?): Article {
        val articleId = row[Articles.id].value
        val author = Users.select { Users.id eq row[Articles.authorId] }
            .map { Users.toDomain(it).copy(password = null) }
            .first()
        val tagIds = ArticleTags.select { ArticleTags.articleId eq articleId }
            .map { it[ArticleTags.tagId] }
        val tagList = if (tagIds.isEmpty()) {
            listOf()
        } else {
            Tags.select { Tags.id inList tagIds }
                .map { it[Tags.name] }
        }
        val favoritesCount = Favorites.select { Favorites.articleId eq articleId }.count().toLong()
        val favorited = viewerId != null && Favorites.select {
            Favorites.articleId eq articleId and (Favorites.userId eq viewerId)
        }.count() > 0
        return Article(
            slug = row[Articles.slug],
            title = row[Articles.title],
            description = row[Articles.description],
            body = row[Articles.body],
            tagList = tagList,
            createdAt = Date(row[Articles.createdAt]),
            updatedAt = Date(row[Articles.updatedAt]),
            favorited = favorited,
            favoritesCount = favoritesCount,
            author = author
        )
    }

    private fun findArticleRowBySlug(slug: String): ResultRow {
        return Articles.select { Articles.slug eq slug }
            .firstOrNull()
            ?: throw NotFoundException("Article not found.")
    }

    private fun uniqueSlug(title: String): String {
        val base = title
            .trim()
            .toLowerCase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .ifBlank { "article" }
        var candidate = base
        var suffix = 1
        while (Articles.select { Articles.slug eq candidate }.count() > 0) {
            candidate = "$base-$suffix"
            suffix++
        }
        return candidate
    }
}
