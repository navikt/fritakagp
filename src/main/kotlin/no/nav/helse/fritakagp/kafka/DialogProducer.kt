package no.nav.helse.fritakagp.kafka

import no.nav.helsearbeidsgiver.utils.log.logger
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import java.util.Properties

const val DEFAULT_DIALOG_TOPIC_NAME = "helsearbeidsgiver.dialog"

interface DialogSender {
    fun sendMessage(message: String): RecordMetadata?
}

class MockDialogProducer : DialogSender {
    private val logger = this.logger()

    override fun sendMessage(message: String): RecordMetadata? {
        logger.info("Mocked sending av dialogId $message til Kafka-topic $DEFAULT_DIALOG_TOPIC_NAME")
        return null
    }
}

class KafkaDialogProducer(
    private val topicName: String = DEFAULT_DIALOG_TOPIC_NAME,
    props: Properties = createKafkaProducerConfig("dialog-producer"),
    producerFactory: (Properties) -> Producer<String, String> = { KafkaProducer(it) }
) : DialogSender {

    private val kafkaProducer: Producer<String, String> = producerFactory(props)
    private val logger = this.logger()

    override fun sendMessage(message: String): RecordMetadata? {
        val record = ProducerRecord<String, String>(topicName, message)
        val metadata = kafkaProducer.send(record).get()

        logger.info("Dialog: Skrevet til Kafka-topic ${metadata.topic()} med offset ${metadata.offset()}")

        return metadata
    }
}
