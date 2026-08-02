package com.example.kakaomiddleware

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeliveryMechanismTest {
    @Test
    fun acceptsOnlyCurrentOutboundWakeSchema() {
        assertTrue(
            PushWakePayload.isSupported(
                mapOf(
                    "type" to "outbound_available",
                    "schemaVersion" to "1",
                    "reason" to "enqueue"
                )
            )
        )
        assertFalse(
            PushWakePayload.isSupported(
                mapOf("type" to "outbound_available", "schemaVersion" to "2")
            )
        )
        assertFalse(
            PushWakePayload.isSupported(
                mapOf("type" to "unrelated", "schemaVersion" to "1")
            )
        )
    }

    @Test
    fun journalPruningDropsExpiredEntries() {
        val now = 1_000_000L
        val entries = mapOf(
            "fresh" to now,
            "boundary" to now - 100L,
            "expired" to now - 101L
        )

        val pruned = DeliveryJournal.pruneEntries(entries, now, ttlMs = 100L)

        assertEquals(listOf("fresh", "boundary"), pruned.keys.toList())
    }

    @Test
    fun journalPruningKeepsOnlyNewestEntries() {
        val entries = (1L..205L).associate { "message-$it" to it }

        val pruned = DeliveryJournal.pruneEntries(
            entries = entries,
            now = 205L,
            ttlMs = Long.MAX_VALUE,
            maxEntries = 200
        )

        assertEquals(200, pruned.size)
        assertTrue(pruned.containsKey("message-205"))
        assertFalse(pruned.containsKey("message-5"))
    }

    @Test
    fun deviceApiClassifiesPermanentAndTransientFailures() {
        assertFalse(DeviceApiException(401, "unauthorized").isRetryable)
        assertFalse(DeviceApiException(422, "bad payload").isRetryable)
        assertTrue(DeviceApiException(429, "busy").isRetryable)
        assertTrue(DeviceApiException(503, "down").isRetryable)
        assertTrue(DeviceApiException(null, "network").isRetryable)
    }
}
