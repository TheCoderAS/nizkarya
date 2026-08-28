package com.nizkarya.app

import com.nizkarya.app.logic.QuickAddParser
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

/** Ported from the web app's src/lib/quickAddParser.test.ts. */
class QuickAddParserTest {

    // Fixed reference point: Sunday 2026-05-31, matching the web tests.
    private val now: LocalDate = LocalDate.of(2026, 5, 31)

    @Test
    fun `plain title with no metadata`() {
        val result = QuickAddParser.parse("Buy groceries", now)
        assertEquals("Buy groceries", result.title)
        assertEquals("medium", result.priority)
        assertEquals(emptyList<String>(), result.tags)
        assertEquals(null, result.date)
        assertEquals(null, result.time)
    }

    @Test
    fun `extracts hashtags as tags`() {
        val result = QuickAddParser.parse("Email Sam #work #urgent", now)
        assertEquals("Email Sam", result.title)
        assertEquals(listOf("work", "urgent"), result.tags)
    }

    @Test
    fun `extracts priority tokens`() {
        assertEquals("high", QuickAddParser.parse("Ship release !high", now).priority)
        assertEquals("low", QuickAddParser.parse("Tidy desk !low", now).priority)
        assertEquals("medium", QuickAddParser.parse("Plan week !m", now).priority)
    }

    @Test
    fun `parses 12 hour times`() {
        val result = QuickAddParser.parse("Gym at 6pm", now)
        assertEquals("Gym", result.title)
        assertEquals(LocalTime.of(18, 0), result.time)
    }

    @Test
    fun `parses 24 hour times with minutes`() {
        val result = QuickAddParser.parse("Standup 09:30", now)
        assertEquals(LocalTime.of(9, 30), result.time)
    }

    @Test
    fun `resolves today`() {
        val result = QuickAddParser.parse("Call mum today", now)
        assertEquals("Call mum", result.title)
        assertEquals(LocalDate.of(2026, 5, 31), result.date)
    }

    @Test
    fun `resolves tomorrow with time`() {
        val result = QuickAddParser.parse("Submit report tomorrow at 9am", now)
        assertEquals("Submit report", result.title)
        assertEquals(LocalDate.of(2026, 6, 1), result.date)
        assertEquals(LocalTime.of(9, 0), result.time)
    }

    @Test
    fun `tonight defaults to 8pm`() {
        val result = QuickAddParser.parse("Read tonight", now)
        assertEquals(LocalDate.of(2026, 5, 31), result.date)
        assertEquals(LocalTime.of(20, 0), result.time)
    }

    @Test
    fun `resolves upcoming weekday`() {
        // now is Sunday; the next Tuesday is 2026-06-02.
        val result = QuickAddParser.parse("Dentist tuesday", now)
        assertEquals(LocalDate.of(2026, 6, 2), result.date)
    }

    @Test
    fun `resolves next weekday to following week`() {
        val result = QuickAddParser.parse("Review next tuesday", now)
        assertEquals(LocalDate.of(2026, 6, 9), result.date)
    }

    @Test
    fun `combines tags priority date and time`() {
        val result = QuickAddParser.parse("Pay rent tomorrow at 5pm #finance !high", now)
        assertEquals("Pay rent", result.title)
        assertEquals(listOf("finance"), result.tags)
        assertEquals("high", result.priority)
        assertEquals(LocalDate.of(2026, 6, 1), result.date)
        assertEquals(LocalTime.of(17, 0), result.time)
    }
}
