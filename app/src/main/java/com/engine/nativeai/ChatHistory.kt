package com.engine.nativeai

/** One rendered chat-history row (display-only, never fabricated). */
data class ChatMessage(
    val id: Long,
    val sessionId: Long,
    val role: String,
    val content: String,
    val created: Long,
)

/** A past agent session with its messages (oldest first). */
data class ChatSession(
    val id: Long,
    val meta: String,
    val startedAt: Long,
    val messages: List<ChatMessage>,
)

/**
 * Loads recent agent conversations for the history UI (Phase 3 gap: runs
 * were persisted but never visible). Function seams keep this testable
 * without the Android SQLite helper.
 */
class ChatHistory(
    private val recentSessions: (Int) -> List<SessionInfo>,
    private val recentMessages: (Long, Int) -> List<Message>,
) {

    fun recent(limit: Int = 8, messagesPerSession: Int = 10): List<ChatSession> =
        recentSessions(limit).map { s ->
            ChatSession(
                id = s.id,
                meta = s.meta,
                startedAt = s.startedAt,
                messages = recentMessages(s.id, messagesPerSession)
                    .asReversed() // store returns newest-first; show oldest-first
                    .map { ChatMessage(it.id, it.sessionId, it.role, it.content, it.created) },
            )
        }
}
