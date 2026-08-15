package com.engine.nativeai

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatHistoryTest {

    private val now = 1_800_000_000_000L

    @Test
    fun recentLoadsSessionsOldestFirst() {
        val history = ChatHistory(
            recentSessions = { limit ->
                assertEquals(8, limit)
                listOf(
                    SessionInfo(2, now - 60_000, now - 10_000, "second run"),
                    SessionInfo(1, now - 120_000, now - 60_000, "first run"),
                )
            },
            recentMessages = { sessionId, _ ->
                if (sessionId == 1L) {
                    listOf(
                        Message(2, 1, "agent", "answer one", now - 61_000),
                        Message(1, 1, "user", "prompt one", now - 62_000),
                    )
                } else {
                    listOf(
                        Message(4, 2, "agent", "answer two", now - 11_000),
                        Message(3, 2, "user", "prompt two", now - 12_000),
                    )
                }
            },
        )
        val sessions = history.recent()
        assertEquals(listOf(2L, 1L), sessions.map { it.id }) // store returns newest-first
        assertEquals(listOf("user", "agent"), sessions[0].messages.map { it.role })
        assertEquals("prompt two", sessions[0].messages[0].content) // sessions[0] = most recent
        assertEquals("prompt one", sessions[1].messages[0].content)
    }

    @Test
    fun recentBoundsMessagesPerSession() {
        var calls = 0
        val history = ChatHistory(
            recentSessions = { listOf(SessionInfo(9, now, null, "solo")) },
            recentMessages = { _, limit ->
                calls++
                assertEquals(3, limit)
                List(3) { Message(it.toLong(), 9, "user", "m$it", now) }
            },
        )
        val sessions = history.recent(messagesPerSession = 3)
        assertEquals(1, sessions.size)
        assertEquals(3, sessions[0].messages.size)
        assertEquals(1, calls)
    }

    @Test
    fun emptyHistoryIsEmptyList() {
        val history = ChatHistory(
            recentSessions = { emptyList() },
            recentMessages = { _, _ -> emptyList() },
        )
        assertEquals(emptyList<ChatSession>(), history.recent())
    }
}
