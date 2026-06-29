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
import org.jetbrains.exposed.sql.update
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

    fun create(authorEmail: String, article: Article): Article = transaction {
        val author = findUserByEmail(authorEmail)
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

        replaceTags(articleId, article.tagList)
        toDomain(findArticleRowBySlug(slugValue), author.id)
    }

    fun findBy(
        tag: String?,
        author: String?,
        favorited: String?,
        limit: Int,
        offset: Int,
        viewerEmail: String?
    ): Pair<List<Article>, Int> = transaction {
        val viewerId = viewerEmail?.let { email -> findUserByEmail(email).id }
        val favoritedUserId = when {
            favorited.isNullOrBlank() -> null
            else -> Users.select { Users.username eq favorited }
                .map { it[Users.id].value }
                .firstOrNull()
        }
        if (!favorited.isNullOrBlank() && favoritedUserId == null) {
            return@transaction emptyList<Article>() to 0
        }
        val articles = Articles.selectAll()
            .orderBy(Articles.createdAt, SortOrder.DESC)
            .map { row -> toDomain(row, viewerId) }
            .filter { article ->
                (tag.isNullOrBlank() || article.tagList.contains(tag)) &&
                    (author.isNullOrBlank() || article.author?.username == author) &&
                    (favoritedUserId == null || isFavoritedByUser(article.slug.orEmpty(), favoritedUserId))
            }
        val total = articles.size
        articles.drop(offset).take(limit) to total
    }

    fun findFeed(userEmail: String, limit: Int, offset: Int): Pair<List<Article>, Int> = transaction {
        val userId = findUserByEmail(userEmail).id!!
        val followedIds = Follows.select { Follows.follower eq userId }
            .map { it[Follows.user] }
        val articles = if (followedIds.isEmpty()) {
            emptyList()
        } else {
            Articles.select { Articles.authorId inList followedIds }
                .orderBy(Articles.createdAt, SortOrder.DESC)
                .map { row -> toDomain(row, userId) }
        }
        val total = articles.size
        articles.drop(offset).take(limit) to total
    }

    fun search(term: String, limit: Int, offset: Int, viewerEmail: String?): Pair<List<Article>, Int> = transaction {
        val normalizedTerm = term.trim().toLowerCase()
        val viewerId = viewerEmail?.let { email -> findUserByEmail(email).id }
        val articles = Articles.selectAll()
            .orderBy(Articles.createdAt, SortOrder.DESC)
            .map { row -> toDomain(row, viewerId) }
            .filter { article ->
                article.title.orEmpty().toLowerCase().contains(normalizedTerm) ||
                    article.body.toLowerCase().contains(normalizedTerm)
            }
        val total = articles.size
        articles.drop(offset).take(limit) to total
    }

    fun findBySlug(slug: String, viewerEmail: String?): Article = transaction {
        val viewerId = viewerEmail?.let { email -> findUserByEmail(email).id }
        toDomain(findArticleRowBySlug(slug), viewerId)
    }

    fun update(slug: String, article: Article, viewerEmail: String): Article = transaction {
        val existing = findArticleRowBySlug(slug)
        val viewer = findUserByEmail(viewerEmail)
        if (existing[Articles.authorId] != viewer.id) {
            throw NotFoundException("Article not found.")
        }
        val now = Date()
        Articles.update({ Articles.id eq existing[Articles.id] }) { row ->
            article.title?.let { row[title] = it }
            article.description?.let { row[description] = it }
            row[body] = article.body
            row[updatedAt] = now.time
        }
        if (article.tagList.isNotEmpty()) {
            replaceTags(existing[Articles.id].value, article.tagList)
        }
        toDomain(findArticleRowBySlug(slug), viewer.id)
    }

    fun delete(slug: String, viewerEmail: String) = transaction {
        val existing = findArticleRowBySlug(slug)
        val viewer = findUserByEmail(viewerEmail)
        if (existing[Articles.authorId] != viewer.id) {
            throw NotFoundException("Article not found.")
        }
        val articleId = existing[Articles.id].value
        Favorites.deleteWhere { Favorites.articleId eq articleId }
        ArticleTags.deleteWhere { ArticleTags.articleId eq articleId }
        ArticleComments.deleteWhere { ArticleComments.articleId eq articleId }
        Articles.deleteWhere { Articles.id eq articleId }
    }

    fun favorite(userEmail: String, slug: String): Article = transaction {
        val user = findUserByEmail(userEmail)
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
        toDomain(findArticleRowBySlug(slug), user.id)
    }

    fun unfavorite(userEmail: String, slug: String): Article = transaction {
        val user = findUserByEmail(userEmail)
        val articleId = findArticleRowBySlug(slug)[Articles.id].value
        Favorites.deleteWhere {
            Favorites.articleId eq articleId and (Favorites.userId eq user.id!!)
        }
        toDomain(findArticleRowBySlug(slug), user.id)
    }

    private fun replaceTags(articleId: Long, tagNames: List<String>) {
        ArticleTags.deleteWhere { ArticleTags.articleId eq articleId }
        tagNames
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .forEach { tagName ->
                val tagId = Tags.select { Tags.name eq tagName }
                    .map { it[Tags.id].value }
                    .firstOrNull()
                    ?: Tags.insertAndGetId { row -> row[name] = tagName }.value
                ArticleTags.insert { row ->
                    row[ArticleTags.articleId] = articleId
                    row[ArticleTags.tagId] = tagId
                }
            }
    }

    private fun toDomain(row: ResultRow, viewerId: Long?): Article {
        val articleId = row[Articles.id].value
        val author = Users.select { Users.id eq row[Articles.authorId] }
            .map { Users.toDomain(it).copy(password = null) }
            .first()
        val tagIds = ArticleTags.select { ArticleTags.articleId eq articleId }
            .map { it[ArticleTags.tagId] }
        val tags = if (tagIds.isEmpty()) {
            emptyList()
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
            tagList = tags,
            createdAt = Date(row[Articles.createdAt]),
            updatedAt = Date(row[Articles.updatedAt]),
            favorited = favorited,
            favoritesCount = favoritesCount,
            author = author
        )
    }

    private fun isFavoritedByUser(slug: String, userId: Long): Boolean {
        val articleId = findArticleRowBySlug(slug)[Articles.id].value
        return Favorites.select {
            Favorites.articleId eq articleId and (Favorites.userId eq userId)
        }.count() > 0
    }

    private fun findArticleRowBySlug(slug: String): ResultRow {
        return Articles.select { Articles.slug eq slug }
            .firstOrNull()
            ?: throw NotFoundException("Article not found.")
    }

    private fun findUserByEmail(email: String) = Users.select { Users.email eq email }
        .map { Users.toDomain(it) }
        .firstOrNull()
        ?: throw NotFoundException("User not found.")

    private fun findUserByUsername(username: String) = Users.select { Users.username eq username }
        .map { Users.toDomain(it) }
        .firstOrNull()
        ?: throw NotFoundException("User not found.")

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
