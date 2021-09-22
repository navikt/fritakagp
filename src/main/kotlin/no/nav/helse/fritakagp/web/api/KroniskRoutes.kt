package no.nav.helse.fritakagp.web.api

import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.application.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import no.nav.helse.arbeidsgiver.bakgrunnsjobb.BakgrunnsjobbService
import no.nav.helse.arbeidsgiver.integrasjoner.aareg.AaregArbeidsforholdClient
import no.nav.helse.arbeidsgiver.web.auth.AltinnAuthorizer
import no.nav.helse.fritakagp.KroniskKravMetrics
import no.nav.helse.fritakagp.KroniskSoeknadMetrics
import no.nav.helse.fritakagp.db.KroniskKravRepository
import no.nav.helse.fritakagp.db.KroniskSoeknadRepository
import no.nav.helse.fritakagp.domain.BeløpBeregning
import no.nav.helse.fritakagp.integration.brreg.BrregClient
import no.nav.helse.fritakagp.integration.gcp.BucketStorage
import no.nav.helse.fritakagp.integration.virusscan.VirusScanner
import no.nav.helse.fritakagp.processing.kronisk.krav.KroniskKravKvitteringProcessor
import no.nav.helse.fritakagp.processing.kronisk.krav.KroniskKravProcessor
import no.nav.helse.fritakagp.processing.kronisk.soeknad.KroniskSoeknadKvitteringProcessor
import no.nav.helse.fritakagp.processing.kronisk.soeknad.KroniskSoeknadProcessor
import no.nav.helse.fritakagp.service.PdlService
import no.nav.helse.fritakagp.web.api.resreq.KroniskKravRequest
import no.nav.helse.fritakagp.web.api.resreq.KroniskSoknadRequest
import no.nav.helse.fritakagp.web.auth.authorize
import no.nav.helse.fritakagp.web.auth.hentIdentitetsnummerFraLoginToken
import java.util.*
import javax.sql.DataSource

fun Route.kroniskRoutes(
    breegClient: BrregClient,
    datasource: DataSource,
    kroniskSoeknadRepo: KroniskSoeknadRepository,
    kroniskKravRepo: KroniskKravRepository,
    bakgunnsjobbService: BakgrunnsjobbService,
    om: ObjectMapper,
    virusScanner: VirusScanner,
    bucket: BucketStorage,
    authorizer: AltinnAuthorizer,
    belopBeregning: BeløpBeregning,
    aaregClient: AaregArbeidsforholdClient,
    pdlService: PdlService
) {
    route("/kronisk") {
        route("/soeknad") {
            get("/{id}") {
                val innloggetFnr = hentIdentitetsnummerFraLoginToken(application.environment.config, call.request)
                val form = kroniskSoeknadRepo.getById(UUID.fromString(call.parameters["id"]))
                if (form == null || form.identitetsnummer != innloggetFnr) {
                    call.respond(HttpStatusCode.NotFound)
                } else {
                    form.sendtAvNavn = form.sendtAvNavn ?: pdlService.finnNavn(innloggetFnr)
                    call.respond(HttpStatusCode.OK, form)
                }
            }

            post {
                val request = call.receive<KroniskSoknadRequest>()
				val isVirksomhet = breegClient.erVirksomhet(request.virksomhetsnummer)

                request.validate(isVirksomhet)
                val innloggetFnr = hentIdentitetsnummerFraLoginToken(application.environment.config, call.request)

                val sendtAvNavn = pdlService.finnNavn(innloggetFnr)
                val soeknad = request.toDomain(innloggetFnr, sendtAvNavn)
                processDocumentForGCPStorage(request.dokumentasjon, virusScanner, bucket, soeknad.id)

                datasource.connection.use { connection ->
                    kroniskSoeknadRepo.insert(soeknad, connection)
                    bakgunnsjobbService.opprettJobb<KroniskSoeknadProcessor>(
                        maksAntallForsoek = 8,
                        data = om.writeValueAsString(KroniskSoeknadProcessor.JobbData(soeknad.id)),
                        connection = connection
                    )
                    bakgunnsjobbService.opprettJobb<KroniskSoeknadKvitteringProcessor>(
                        maksAntallForsoek = 10,
                        data = om.writeValueAsString(KroniskSoeknadKvitteringProcessor.Jobbdata(soeknad.id)),
                        connection = connection
                    )
                }

                call.respond(HttpStatusCode.Created, soeknad)
                KroniskSoeknadMetrics.tellMottatt()
            }
        }

        route("/krav") {
            get("/{id}") {
                val innloggetFnr = hentIdentitetsnummerFraLoginToken(application.environment.config, call.request)
                val form = kroniskKravRepo.getById(UUID.fromString(call.parameters["id"]))
                if (form == null || form.identitetsnummer != innloggetFnr) {
                    call.respond(HttpStatusCode.NotFound)
                } else {
                    form.sendtAvNavn = form.sendtAvNavn ?: pdlService.finnNavn(innloggetFnr)
                    call.respond(HttpStatusCode.OK, form)
                }
            }

            post {
                val request = call.receive<KroniskKravRequest>()
                authorize(authorizer, request.virksomhetsnummer)
                val arbeidsforhold = aaregClient
                    .hentArbeidsforhold(request.identitetsnummer, UUID.randomUUID().toString())
                    .filter { it.arbeidsgiver.organisasjonsnummer == request.virksomhetsnummer }

                request.validate(arbeidsforhold)

                val innloggetFnr = hentIdentitetsnummerFraLoginToken(application.environment.config, call.request)
                val sendtAvNavn = pdlService.finnNavn(innloggetFnr)

                val krav = request.toDomain(innloggetFnr, sendtAvNavn)

                belopBeregning.beregnBeløpKronisk(krav)
                processDocumentForGCPStorage(request.dokumentasjon, virusScanner, bucket, krav.id)

                datasource.connection.use { connection ->
                    kroniskKravRepo.insert(krav, connection)
                    bakgunnsjobbService.opprettJobb<KroniskKravProcessor>(
                        maksAntallForsoek = 8,
                        data = om.writeValueAsString(KroniskKravProcessor.JobbData(krav.id)),
                        connection = connection
                    )
                    bakgunnsjobbService.opprettJobb<KroniskKravKvitteringProcessor>(
                        maksAntallForsoek = 10,
                        data = om.writeValueAsString(KroniskKravKvitteringProcessor.Jobbdata(krav.id)),
                        connection = connection
                    )
                }

                call.respond(HttpStatusCode.Created, krav)
                KroniskKravMetrics.tellMottatt()
            }
        }
    }
}
