package com.engine.nativeai

import org.junit.Assert.assertEquals
import org.junit.Test

class TaskClassifierTest {
    @Test
    fun defaultChat() {
        assertEquals(TaskType.CHAT, TaskClassifier.classify("hello, how are you?"))
    }

    @Test
    fun coding() {
        assertEquals(TaskType.CODING, TaskClassifier.classify("write a kotlin function that sorts a list"))
    }

    @Test
    fun debugging() {
        assertEquals(TaskType.DEBUGGING, TaskClassifier.classify("why does my app crash on startup"))
    }

    @Test
    fun reasoning() {
        assertEquals(TaskType.REASONING, TaskClassifier.classify("solve this math puzzle step by step"))
    }

    @Test
    fun summarization() {
        assertEquals(TaskType.SUMMARIZATION, TaskClassifier.classify("summarize these key points"))
    }

    @Test
    fun research() {
        assertEquals(TaskType.RESEARCH, TaskClassifier.classify("search the web for latest AI news"))
    }

    @Test
    fun vision() {
        assertEquals(TaskType.VISION, TaskClassifier.classify("describe this image"))
    }

    @Test
    fun offlineWhenNoNetwork() {
        assertEquals(TaskType.OFFLINE_TASK, TaskClassifier.classify("hello", networkAvailable = false))
    }

    @Test
    fun longContext() {
        val long = "word ".repeat(2000)
        assertEquals(TaskType.LONG_CONTEXT, TaskClassifier.classify(long))
    }
}
