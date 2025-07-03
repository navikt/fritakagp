package no.nav.helse.fritakagp.web.api.resreq.validation

import org.valiktor.ConstraintViolation
import org.valiktor.i18n.toMessage
import java.net.URI

fun ConstraintViolation.getContextualMessage(): String {
    return this.toMessage().message
}
open class Problem(
    val type: URI = URI.create("about:blank"),
    val title: String,
    val status: Int? = 500,
    val detail: String? = null,
    val instance: URI = URI.create("about:blank")
)

/**
 * Problem extension for input-validation-feil.
 * Inneholder en liste over properties som feilet validering
 */
class ValidationProblem(
    val violations: Set<ValidationProblemDetail>
) : Problem(
    URI.create("urn:nav:helsearbeidsgiver:validation-error"),
    "Valideringen av input feilet",
    422,
    "Ett eller flere felter har feil."
)

class ValidationProblemDetail(
    val validationType: String,
    val message: String,
    val propertyPath: String,
    val invalidValue: Any?
)
