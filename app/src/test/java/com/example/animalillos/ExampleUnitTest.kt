package com.example.animalillos

import org.junit.Assert.assertEquals
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun reminderStore_roundTrip() {
        val original = listOf(
            PetReminder(1L, "Firulais", "Medicamento 1, 2 tazas de comida", listOf("08:00", "12:00"))
        )
        val decoded = ReminderStore.decode(ReminderStore.encode(original))
        assertEquals(original, decoded)
    }
}
