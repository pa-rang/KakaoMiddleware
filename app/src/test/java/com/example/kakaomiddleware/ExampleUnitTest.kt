package com.example.kakaomiddleware

import org.junit.Test

import org.junit.Assert.*

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun outboundMessageWithDispatchMetadataRequiresAck() {
        val message = CronMessage(
            chatId = "personal_테스트",
            message = "안녕하세요",
            scheduledMessageId = "8e67fa57-1f72-4e13-9a44-fcf4e6c15c08",
            messageSource = CronMessageSource.OUTBOUND,
            deliveryAttempt = 1
        )

        assertTrue(message.requiresDeliveryAck)
    }

    @Test
    fun scheduledMessageDoesNotRequireAck() {
        val message = CronMessage(
            chatId = "personal_테스트",
            message = "안녕하세요",
            scheduledMessageId = "8e67fa57-1f72-4e13-9a44-fcf4e6c15c08",
            messageSource = CronMessageSource.SCHEDULED
        )

        assertFalse(message.requiresDeliveryAck)
    }

    @Test
    fun outboundMessageWithoutAttemptDoesNotRequireAck() {
        val message = CronMessage(
            chatId = "personal_테스트",
            message = "안녕하세요",
            scheduledMessageId = "8e67fa57-1f72-4e13-9a44-fcf4e6c15c08",
            messageSource = CronMessageSource.OUTBOUND
        )

        assertFalse(message.requiresDeliveryAck)
    }
}
