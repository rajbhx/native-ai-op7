package com.engine.nativeai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ActionParserTest {
    @Test
    fun parsesPlainJson() {
        val a = ActionParser.parse("""{"action": "calculator", "input": "2 + 3"}""")
        assertNotNull(a)
        assertEquals("calculator", a?.name)
        assertEquals("2 + 3", a?.input)
    }

    @Test
    fun parsesJsonInsideProse() {
        val text = "Sure! Let me compute that. {\"action\": \"calculator\", \"input\": \"7 * 6\"} Done."
        val a = ActionParser.parse(text)
        assertNotNull(a)
        assertEquals("calculator", a?.name)
        assertEquals("7 * 6", a?.input)
    }

    @Test
    fun parsesEscapedQuotes() {
        val a = ActionParser.parse("""{"action": "final_answer", "input": "say \"hi\" now"}""")
        assertNotNull(a)
        assertEquals("say \"hi\" now", a?.input)
    }

    @Test
    fun nestedBraceInInput() {
        val a = ActionParser.parse("""{"action": "file_search", "input": "find {config} files"}""")
        assertNotNull(a)
        assertEquals("file_search", a?.name)
        assertEquals("find {config} files", a?.input)
    }

    @Test
    fun rejectsMissingInput() {
        assertNull(ActionParser.parse("""{"action": "calculator"}"""))
    }

    @Test
    fun returnsNullOnNoJson() {
        assertNull(ActionParser.parse("I have no structured actions for you."))
    }
}
