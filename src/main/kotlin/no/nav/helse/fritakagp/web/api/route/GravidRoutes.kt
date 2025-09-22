package no.nav.helse.fritakagp.web.api.route

import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.coroutines.runBlocking
import no.nav.hag.utils.bakgrunnsjobb.BakgrunnsjobbService
import no.nav.helse.fritakagp.GravidKravMetrics
import no.nav.helse.fritakagp.GravidSoeknadMetrics
import no.nav.helse.fritakagp.db.GravidKravRepository
import no.nav.helse.fritakagp.db.GravidSoeknadRepository
import no.nav.helse.fritakagp.domain.BeloepBeregning
import no.nav.helse.fritakagp.domain.KravStatus
import no.nav.helse.fritakagp.domain.decodeBase64File
import no.nav.helse.fritakagp.integration.IBrregService
import no.nav.helse.fritakagp.integration.PdlService
import no.nav.helse.fritakagp.integration.gcp.BucketStorage
import no.nav.helse.fritakagp.integration.virusscan.VirusScanner
import no.nav.helse.fritakagp.processing.arbeidsgivernotifikasjon.ArbeidsgiverNotifikasjonProcessor
import no.nav.helse.fritakagp.processing.gravid.krav.GravidKravEndreProcessor
import no.nav.helse.fritakagp.processing.gravid.krav.GravidKravKvitteringProcessor
import no.nav.helse.fritakagp.processing.gravid.krav.GravidKravProcessor
import no.nav.helse.fritakagp.processing.gravid.krav.GravidKravSlettProcessor
import no.nav.helse.fritakagp.processing.gravid.soeknad.GravidSoeknadKvitteringProcessor
import no.nav.helse.fritakagp.processing.gravid.soeknad.GravidSoeknadProcessor
import no.nav.helse.fritakagp.web.api.RequestHandler
import no.nav.helse.fritakagp.web.api.resreq.GravidKravRequest
import no.nav.helse.fritakagp.web.api.resreq.GravidSoknadRequest
import no.nav.helse.fritakagp.web.api.resreq.validation.VirusCheckConstraint
import no.nav.helse.fritakagp.web.api.resreq.validation.extractBase64Del
import no.nav.helse.fritakagp.web.api.resreq.validation.extractFilExtDel
import no.nav.helse.fritakagp.web.auth.AuthService
import no.nav.helse.fritakagp.web.auth.hentFnrFraLoginToken
import no.nav.helsearbeidsgiver.aareg.AaregClient
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.ArbeidsgiverNotifikasjonKlient
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.SakEllerOppgaveFinnesIkkeException
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import org.valiktor.ConstraintViolationException
import org.valiktor.DefaultConstraintViolation
import java.time.LocalDateTime
import java.util.UUID

fun Route.gravidRoutes(
    authService: AuthService,
    brregService: IBrregService,
    pdlService: PdlService,
    belopBeregning: BeloepBeregning,
    aaregClient: AaregClient,
    arbeidsgiverNotifikasjonKlient: ArbeidsgiverNotifikasjonKlient,
    gravidSoeknadRepo: GravidSoeknadRepository,
    gravidKravRepo: GravidKravRepository,
    bakgunnsjobbService: BakgrunnsjobbService,
    virusScanner: VirusScanner,
    bucket: BucketStorage,
    om: ObjectMapper
) {
    val logger = "gravidRoutes".logger()
    val sikkerLogger = sikkerLogger()

    val requestHandler = RequestHandler(
        aapenLogger = logger,
        sikkerLogger = sikkerLogger
    )

    route("/gravid") {
        route("/soeknad") {
            get("/{id}") {
                val soeknadId = requestHandler.lesParameterId(this)

                val innloggetFnr = hentFnrFraLoginToken()
                val soeknad = gravidSoeknadRepo.getById(soeknadId)
                if (soeknad == null || soeknad.identitetsnummer != innloggetFnr) {
                    call.respond(HttpStatusCode.NotFound)
                } else {
                    soeknad.sendtAvNavn = soeknad.sendtAvNavn ?: pdlService.hentNavn(innloggetFnr)
                    soeknad.navn = soeknad.navn ?: pdlService.hentNavn(soeknad.identitetsnummer)

                    call.respond(HttpStatusCode.OK, soeknad)
                }
            }

            post {
                val request = requestHandler.lesRequestBody<GravidSoknadRequest>(this)

                val innloggetFnr = hentFnrFraLoginToken()

                val isVirksomhet = brregService.erOrganisasjon(request.virksomhetsnummer)
                request.validate(isVirksomhet)

                val sendtAvNavn = pdlService.hentNavn(innloggetFnr)
                val navn = pdlService.hentNavn(request.identitetsnummer)

                val soeknad = request.toDomain(innloggetFnr, sendtAvNavn, navn)

                processDocumentForGCPStorage(request.dokumentasjon, virusScanner, bucket, soeknad.id)

                gravidSoeknadRepo.insert(soeknad)
                bakgunnsjobbService.opprettJobb<GravidSoeknadProcessor>(
                    maksAntallForsoek = 10,
                    data = om.writeValueAsString(GravidSoeknadProcessor.JobbData(soeknad.id))
                )
                bakgunnsjobbService.opprettJobb<GravidSoeknadKvitteringProcessor>(
                    maksAntallForsoek = 10,
                    data = om.writeValueAsString(GravidSoeknadKvitteringProcessor.Jobbdata(soeknad.id))
                )

                call.respond(HttpStatusCode.Created, soeknad)
                GravidSoeknadMetrics.tellMottatt()
            }
        }

        route("/krav") {
            get("/{id}") {
                val kravId = requestHandler.lesParameterId(this)

                val innloggetFnr = hentFnrFraLoginToken()
                val krav = gravidKravRepo.getById(kravId)
                val slettet = call.request.queryParameters.contains("slettet")
                if (krav == null) {
                    call.respond(HttpStatusCode.NotFound)
                } else if (!slettet && krav.status == KravStatus.SLETTET) {
                    call.respond(HttpStatusCode.NotFound)
                } else {
                    if (krav.identitetsnummer != innloggetFnr) {
                        authService.validerTilgangTilOrganisasjon(this, krav.virksomhetsnummer)
                    }
                    krav.sendtAvNavn = krav.sendtAvNavn ?: pdlService.hentNavn(innloggetFnr)
                    krav.navn = krav.navn ?: pdlService.hentNavn(krav.identitetsnummer)

                    call.respond(HttpStatusCode.OK, krav)
                }
            }

            post {
                val request = requestHandler.lesRequestBody<GravidKravRequest>(this)
                authService.validerTilgangTilOrganisasjon(this, request.virksomhetsnummer)
                val ansettelsesperioder = aaregClient
                    .hentAnsettelsesperioder(request.identitetsnummer, UUID.randomUUID().toString())
                    .get(Orgnr(request.virksomhetsnummer))
                    .orEmpty()

                request.validate(ansettelsesperioder)

                val innloggetFnr = hentFnrFraLoginToken()
                val sendtAvNavn = pdlService.hentNavn(innloggetFnr)
                val navn = pdlService.hentNavn(request.identitetsnummer)

                val krav = request.toDomain(innloggetFnr, sendtAvNavn, navn)
                belopBeregning.beregnBeloepGravid(krav)

                gravidKravRepo.insert(krav)
                bakgunnsjobbService.opprettJobb<GravidKravProcessor>(
                    maksAntallForsoek = 10,
                    data = om.writeValueAsString(GravidKravProcessor.JobbData(krav.id))
                )
                bakgunnsjobbService.opprettJobb<GravidKravKvitteringProcessor>(
                    maksAntallForsoek = 10,
                    data = om.writeValueAsString(GravidKravKvitteringProcessor.Jobbdata(krav.id))
                )
                bakgunnsjobbService.opprettJobb<ArbeidsgiverNotifikasjonProcessor>(
                    maksAntallForsoek = 10,
                    data = om.writeValueAsString(ArbeidsgiverNotifikasjonProcessor.JobbData(krav.id, ArbeidsgiverNotifikasjonProcessor.JobbData.SkjemaType.GravidKrav))
                )

                call.respond(HttpStatusCode.Created, krav)
                GravidKravMetrics.tellMottatt()
            }

            patch("/{id}") {
                val kravId = requestHandler.lesParameterId(this)
                val request = requestHandler.lesRequestBody<GravidKravRequest>(this)

                authService.validerTilgangTilOrganisasjon(this, request.virksomhetsnummer)

                val innloggetFnr = hentFnrFraLoginToken()
                val sendtAvNavn = pdlService.hentNavn(innloggetFnr)
                val navn = pdlService.hentNavn(request.identitetsnummer)

                val ansettelsesperioder = aaregClient
                    .hentAnsettelsesperioder(request.identitetsnummer, UUID.randomUUID().toString())
                    .get(Orgnr(request.virksomhetsnummer))
                    .orEmpty()

                request.validate(ansettelsesperioder)

                val forrigeKrav = gravidKravRepo.getById(kravId)
                    ?: return@patch call.respond(HttpStatusCode.NotFound)

                if (forrigeKrav.virksomhetsnummer != request.virksomhetsnummer) {
                    return@patch call.respond(HttpStatusCode.Forbidden)
                }

                val kravTilOppdatering = request.toDomain(innloggetFnr, sendtAvNavn, navn)
                belopBeregning.beregnBeloepGravid(kravTilOppdatering)

                if (forrigeKrav.isDuplicate(kravTilOppdatering)) {
                    return@patch call.respond(HttpStatusCode.Conflict)
                }

                forrigeKrav.status = KravStatus.ENDRET
                forrigeKrav.slettetAv = innloggetFnr
                forrigeKrav.slettetAvNavn = sendtAvNavn
                forrigeKrav.endretDato = LocalDateTime.now()
                forrigeKrav.endretTilId = kravTilOppdatering.id

                // Sletter gammelt krav
                forrigeKrav.arbeidsgiverSakId?.let {
                    try {
                        runBlocking { arbeidsgiverNotifikasjonKlient.hardDeleteSak(it) }
                    } catch (_: SakEllerOppgaveFinnesIkkeException) {
                        logger.warn("PATCH | Klarte ikke slette sak med ID ${forrigeKrav.arbeidsgiverSakId} fordi saken finnes ikke.")
                    }
                }

                gravidKravRepo.update(forrigeKrav)
                // Oppretter nytt krav
                gravidKravRepo.insert(kravTilOppdatering)
                bakgunnsjobbService.opprettJobb<GravidKravEndreProcessor>(
                    maksAntallForsoek = 10,
                    data = om.writeValueAsString(GravidKravProcessor.JobbData(forrigeKrav.id))
                )
                bakgunnsjobbService.opprettJobb<GravidKravKvitteringProcessor>(
                    maksAntallForsoek = 10,
                    data = om.writeValueAsString(GravidKravKvitteringProcessor.Jobbdata(kravTilOppdatering.id))
                )
                bakgunnsjobbService.opprettJobb<ArbeidsgiverNotifikasjonProcessor>(
                    maksAntallForsoek = 10,
                    data = om.writeValueAsString(ArbeidsgiverNotifikasjonProcessor.JobbData(kravTilOppdatering.id, ArbeidsgiverNotifikasjonProcessor.JobbData.SkjemaType.GravidKrav))
                )

                call.respond(HttpStatusCode.OK, kravTilOppdatering)
            }

            delete("/{id}") {
                val kravId = requestHandler.lesParameterId(this)

                val innloggetFnr = hentFnrFraLoginToken()
                val slettetAv = pdlService.hentNavn(innloggetFnr)

                val krav = gravidKravRepo.getById(kravId)
                    ?: return@delete call.respond(HttpStatusCode.NotFound)

                authService.validerTilgangTilOrganisasjon(this, krav.virksomhetsnummer)

                krav.arbeidsgiverSakId?.let {
                    try {
                        runBlocking { arbeidsgiverNotifikasjonKlient.hardDeleteSak(it) }
                    } catch (_: SakEllerOppgaveFinnesIkkeException) {
                        logger.warn("DELETE | Klarte ikke slette sak med ID ${krav.arbeidsgiverSakId} fordi saken finnes ikke.")
                    }
                }
                krav.status = KravStatus.SLETTET
                krav.slettetAv = innloggetFnr
                krav.slettetAvNavn = slettetAv
                krav.endretDato = LocalDateTime.now()
                gravidKravRepo.update(krav)
                bakgunnsjobbService.opprettJobb<GravidKravSlettProcessor>(
                    maksAntallForsoek = 10,
                    data = om.writeValueAsString(GravidKravProcessor.JobbData(krav.id))
                )
                call.respond(HttpStatusCode.OK)
            }
        }
    }
}

suspend fun processDocumentForGCPStorage(doc: String?, virusScanner: VirusScanner, bucket: BucketStorage, id: UUID) {
    if (!doc.isNullOrEmpty()) {
        val fileContent = extractBase64Del(doc)
        val fileExt = extractFilExtDel(doc)
        if (!virusScanner.scanDoc(decodeBase64File(fileContent))) {
            throw ConstraintViolationException(
                setOf(
                    DefaultConstraintViolation(
                        "dokumentasjon",
                        constraint = VirusCheckConstraint()
                    )
                )
            )
        }
        bucket.uploadDoc(id, fileContent, fileExt)
    }
}
