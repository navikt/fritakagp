package no.nav.helse.fritakagp.integration.kafka

import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.config.SslConfigs
import org.apache.kafka.common.security.auth.SecurityProtocol
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import java.time.Duration

private const val JAVA_KEYSTORE = "jks"
private const val PKCS12 = "PKCS12"

fun producerConfig() = mutableMapOf(
    ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to System.getenv()["KAFKA_BROKERS"],
    ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java.canonicalName,
    ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java.canonicalName,
    ProducerConfig.ACKS_CONFIG to "1"
) + securityConfig()

fun consumerConfig() = mutableMapOf(
    ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to System.getenv()["KAFKA_BROKERS"],
    ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to false,
    ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java.canonicalName,
    ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java.canonicalName,
    ConsumerConfig.GROUP_ID_CONFIG to "helsearbeidsgiver-group",
    ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG to Duration.ofMinutes(60).toMillis().toInt(),
    ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "latest"
)+ securityConfig()

private fun securityConfig() = mapOf(
    CommonClientConfigs.SECURITY_PROTOCOL_CONFIG to SecurityProtocol.SSL.name,
    SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG to "", //Disable server host name verification
    SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG to JAVA_KEYSTORE,
    SslConfigs.SSL_KEYSTORE_TYPE_CONFIG to PKCS12,
    SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG to System.getenv()["KAFKA_TRUSTSTORE_PATH"],
    SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG to System.getenv()["KAFKA_CREDSTORE_PASSWORD"],
    SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG to System.getenv()["KAFKA_KEYSTORE_PATH"],
    SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG to System.getenv()["KAFKA_CREDSTORE_PASSWORD"],
    SslConfigs.SSL_KEY_PASSWORD_CONFIG to System.getenv()["KAFKA_CREDSTORE_PASSWORD"]
)

fun producerFakeConfig() = mutableMapOf<String, Any>(
    ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to "localhost:9092",
    ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java.canonicalName,
    ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java.canonicalName,
    ProducerConfig.ACKS_CONFIG to "1")

fun consumerFakeConfig() = mutableMapOf<String, Any>(
    ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to "localhost:9092",
    ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG to "30000",
    ConsumerConfig.GROUP_ID_CONFIG to "helsearbeidsgiver-im-varsel-grace-period",
    ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to false,
    ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "latest")