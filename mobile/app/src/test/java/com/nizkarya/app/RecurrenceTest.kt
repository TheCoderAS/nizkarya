package com.nizkarya.app

import com.nizkarya.app.logic.Recurrence
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class RecurrenceTest {

    private val base = LocalDateTime.of(2026, 5, 31, 17, 0)
        .toInstant(ZoneOffset.UTC)

    private fun localOf(instant: java.time.Instant): LocalDateTime =
        LocalDateTime.ofInstant(instant, ZoneOffset.UTC)

    @Test
    fun `daily advances one day`() {
        val next = Recurrence.next(base, "daily", ZoneOffset.UTC)
        assertEquals(LocalDateTime.of(2026, 6, 1, 17, 0), localOf(next))
    }

    @Test
    fun `weekly advances seven days`() {
        val next = Recurrence.next(base, "weekly", ZoneOffset.UTC)
        assertEquals(LocalDateTime.of(2026, 6, 7, 17, 0), localOf(next))
    }

    @Test
    fun `monthly advances one month`() {
        val next = Recurrence.next(base, "monthly", ZoneOffset.UTC)
        assertEquals(LocalDateTime.of(2026, 6, 30, 17, 0), localOf(next))
    }

    @Test
    fun `unknown recurrence is unchanged`() {
        assertEquals(base, Recurrence.next(base, "none", ZoneOffset.UTC))
    }
}
