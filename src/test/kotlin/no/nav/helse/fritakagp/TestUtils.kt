package no.nav.helse.fritakagp

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode

fun String.readToObjectNode(objectMapper: ObjectMapper): ObjectNode = objectMapper.readTree(this) as ObjectNode

fun String.jsonEquals(
    objectMapper: ObjectMapper,
    other: String,
    vararg excluding: String,
): Boolean {
    val excludingList = excluding.toList()
    return this.readToObjectNode(objectMapper).remove(excludingList).equals(
        other.readToObjectNode(objectMapper).remove(excludingList),
    )
}
