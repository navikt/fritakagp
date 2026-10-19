package no.nav.helse.fritakagp.integration.kafka

import no.nav.helsearbeidsgiver.utils.log.logger
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata

interface BrukernotifikasjonSender {
    fun sendMessage(
        varselId: String,
        varsel: String,
    ): RecordMetadata?
}

class MockBrukernotifikasjonBeskjedSender : BrukernotifikasjonSender {
    private val logger = this.logger()

    override fun sendMessage(
        varselId: String,
        varsel: String,
    ): RecordMetadata? {
        logger.info("Mocked sending av varselId $varselId til Kafka")
        return null
    }
}

class BrukernotifikasjonKafkaProducer(
    props: Map<String, Any>,
    private val topicName: String,
) : BrukernotifikasjonSender {
    private var kafkaProducer: KafkaProducer<String, String> = KafkaProducer(props)
    private val logger = this.logger()

    override fun sendMessage(
        varselId: String,
        varsel: String,
    ): RecordMetadata? {
        val record = ProducerRecord(topicName, varselId, varsel)
        return kafkaProducer.send(record).get().also {
            logger.info("Brukernotifikasjon: Skrevet varselId $varselId til Kafka til topic ${it!!.topic()} med offset ${it.offset()}")
        }
    }
}
