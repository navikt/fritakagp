package no.nav.helse.fritakagp.integration.kafka

import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.config.SslConfigs
import org.apache.kafka.common.security.auth.SecurityProtocol
import org.apache.kafka.common.serialization.StringSerializer

private const val BASIC_AUTH_CREDENTIALS_SOURCE = "basic.auth.credentials.source"
private const val BASIC_AUTH_USER_INFO = "basic.auth.user.info"
private const val JAVA_KEYSTORE = "jks"
private const val PKCS12 = "PKCS12"

private fun envOrThrow(envVar: String) = System.getenv()[envVar] ?: throw IllegalStateException("$envVar er påkrevd miljøvariabel")

fun brukernotifikasjonKafkaProps() =
    mapOf(
        BASIC_AUTH_CREDENTIALS_SOURCE to "USER_INFO",
        BASIC_AUTH_USER_INFO to System.getenv("KAFKA_SCHEMA_REGISTRY_USER") + ":" + System.getenv("KAFKA_SCHEMA_REGISTRY_PASSWORD"),
        ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
        ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
    ) + gcpCommonKafkaProps()

fun gcpCommonKafkaProps() =
    mapOf<String, Any>(
        ProducerConfig.ACKS_CONFIG to "all",
        ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to envOrThrow("KAFKA_BROKERS"),
        CommonClientConfigs.SECURITY_PROTOCOL_CONFIG to SecurityProtocol.SSL.name,
        SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG to "", // Disable server host name verification
        SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG to JAVA_KEYSTORE,
        SslConfigs.SSL_KEYSTORE_TYPE_CONFIG to PKCS12,
        SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG to envOrThrow("KAFKA_TRUSTSTORE_PATH"),
        SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG to envOrThrow("KAFKA_CREDSTORE_PASSWORD"),
        SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG to envOrThrow("KAFKA_KEYSTORE_PATH"),
        SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG to envOrThrow("KAFKA_CREDSTORE_PASSWORD"),
        SslConfigs.SSL_KEY_PASSWORD_CONFIG to envOrThrow("KAFKA_CREDSTORE_PASSWORD"),
    )
