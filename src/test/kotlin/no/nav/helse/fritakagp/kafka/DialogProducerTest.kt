package no.nav.helse.fritakagp.kafka

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.TopicPartition
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.util.Properties
import java.util.concurrent.CompletableFuture

class DialogProducerTest {

    @Test
    fun `sender melding til dialog-topic`() {
        val recordSlot = slot<ProducerRecord<String, String>>()
        val producer = mockk<Producer<String, String>>()
        val metadata = RecordMetadata(
            TopicPartition(DEFAULT_DIALOG_TOPIC_NAME, 0),
            0,
            1,
            System.currentTimeMillis(),
            "dialog-1".length,
            "melding".length
        )

        every { producer.send(capture(recordSlot)) } returns CompletableFuture.completedFuture(metadata)

        val dialogProducer = KafkaDialogProducer(
            topicName = DEFAULT_DIALOG_TOPIC_NAME,
            props = Properties()
        ) { producer }

        val actualMetadata = requireNotNull(dialogProducer.sendMessage("dialog-1", "melding"))
        val record = requireNotNull(recordSlot.captured)

        assertNotNull(actualMetadata)
        assertEquals(DEFAULT_DIALOG_TOPIC_NAME, actualMetadata.topic())
        assertEquals(DEFAULT_DIALOG_TOPIC_NAME, record.topic())
        assertEquals("dialog-1", record.key())
        assertEquals("melding", record.value())
    }
}
