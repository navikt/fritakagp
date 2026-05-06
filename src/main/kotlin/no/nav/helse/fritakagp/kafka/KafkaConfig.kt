package no.nav.helse.fritakagp.kafka

import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.config.SslConfigs
import org.apache.kafka.common.security.auth.SecurityProtocol
import org.apache.kafka.common.serialization.StringSerializer
import java.util.Properties

class KafkaConfig {
    val dialogProducerConfig: Properties = createKafkaProducerConfig("dialog-producer")
}

fun createKafkaProducerConfig(producerName: String): Properties {
    val producerKafkaProperties = mapOf(
        ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to resolveKafkaBrokers(),
        ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION to "1",
        ProducerConfig.ACKS_CONFIG to "all",
        ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG to "true",
        ProducerConfig.MAX_BLOCK_MS_CONFIG to "15000",
        ProducerConfig.RETRIES_CONFIG to "2",
        ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java.name,
        ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java.name,
        ProducerConfig.CLIENT_ID_CONFIG to "fritakagp-$producerName"
    )

    return Properties().apply { putAll(producerKafkaProperties + commonKafkaProperties()) }
}

private fun resolveKafkaBrokers(): String =
    System.getenv("KAFKA_BROKERS")
        ?: System.getProperty("KAFKA_BOOTSTRAP_SERVERS")
        ?: "localhost:9092"

private fun commonKafkaProperties(): Map<String, String> {
    val pkcs12 = "PKCS12"
    val javaKeyStore = "jks"

    val truststoreConfig = System.getenv("KAFKA_TRUSTSTORE_PATH")
        ?.let {
            mapOf(
                SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG to it,
                CommonClientConfigs.SECURITY_PROTOCOL_CONFIG to SecurityProtocol.SSL.name,
                SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG to "",
                SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG to javaKeyStore,
                SslConfigs.SSL_KEYSTORE_TYPE_CONFIG to pkcs12
            )
        }.orEmpty()

    val credstoreConfig = System.getenv("KAFKA_CREDSTORE_PASSWORD")
        ?.let {
            mapOf(
                SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG to it,
                SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG to it,
                SslConfigs.SSL_KEY_PASSWORD_CONFIG to it
            )
        }.orEmpty()

    val keystoreConfig = System.getenv("KAFKA_KEYSTORE_PATH")
        ?.let {
            mapOf(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG to it)
        }.orEmpty()

    return truststoreConfig + credstoreConfig + keystoreConfig
}
