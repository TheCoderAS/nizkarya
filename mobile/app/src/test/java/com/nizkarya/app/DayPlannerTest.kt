package com.nizkarya.app

import com.nizkarya.app.logic.DayPlanner
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class DayPlannerTest {

    @Test
    fun `next half hour rounds up`() {
        assertEquals(
            LocalDateTime.of(2026, 5, 31, 9, 30),
            DayPlanner.nextHalfHour(LocalDateTime.of(2026, 5, 31, 9, 5))
        )
        assertEquals(
            LocalDateTime.of(2026, 5, 31, 10, 0),
            DayPlanner.nextHalfHour(LocalDateTime.of(2026, 5, 31, 9, 45))
        )
        // Exactly on a boundary moves to the next slot.
        assertEquals(
            LocalDateTime.of(2026, 5, 31, 10, 0),
            DayPlanner.nextHalfHour(LocalDateTime.of(2026, 5, 31, 9, 30))
        )
    }

    @Test
    fun `slots are thirty minutes apart`() {
        val slots = DayPlanner.slots(3, LocalDateTime.of(2026, 5, 31, 9, 5))
        assertEquals(LocalDateTime.of(2026, 5, 31, 9, 30), slots[0])
        assertEquals(LocalDateTime.of(2026, 5, 31, 10, 0), slots[1])
        assertEquals(LocalDateTime.of(2026, 5, 31, 10, 30), slots[2])
    }

    @Test
    fun `slots clamp at ten pm`() {
        val slots = DayPlanner.slots(4, LocalDateTime.of(2026, 5, 31, 21, 20))
        assertEquals(LocalDateTime.of(2026, 5, 31, 21, 30), slots[0])
        assertEquals(LocalDateTime.of(2026, 5, 31, 22, 0), slots[1])
        assertEquals(LocalDateTime.of(2026, 5, 31, 22, 0), slots[2])
        assertEquals(LocalDateTime.of(2026, 5, 31, 22, 0), slots[3])
    }
}
