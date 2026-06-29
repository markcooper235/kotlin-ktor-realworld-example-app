package io.realworld.app.domain.service

import io.realworld.app.domain.Comment
import io.realworld.app.domain.repository.CommentRepository

class CommentService(private val commentRepository: CommentRepository) {
    fun add(slug: String, authorEmail: String, comment: Comment): Comment {
        require(slug.isNotBlank()) { "Article slug is required." }
        require(comment.body.isNotBlank()) { "Comment body is required." }
        return commentRepository.add(slug, authorEmail, comment.body)
    }

    fun findBySlug(slug: String): List<Comment> {
        require(slug.isNotBlank()) { "Article slug is required." }
        return commentRepository.findBySlug(slug)
    }

    fun delete(id: Long, slug: String) {
        require(slug.isNotBlank()) { "Article slug is required." }
        commentRepository.delete(id, slug)
    }
}
