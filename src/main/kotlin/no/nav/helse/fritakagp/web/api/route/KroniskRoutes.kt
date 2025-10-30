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
import no.nav.helse.fritakagp.KroniskKravMetrics
import no.nav.helse.fritakagp.KroniskSoeknadMetrics
import no.nav.helse.fritakagp.Log
import no.nav.helse.fritakagp.db.KroniskKravRepository
import no.nav.helse.fritakagp.db.KroniskSoeknadRepository
import no.nav.helse.fritakagp.domain.BeloepBeregning
import no.nav.helse.fritakagp.domain.KravStatus
import no.nav.helse.fritakagp.integration.IBrregService
import no.nav.helse.fritakagp.integration.PdlService
import no.nav.helse.fritakagp.integration.gcp.BucketStorage
import no.nav.helse.fritakagp.integration.virusscan.VirusScanner
import no.nav.helse.fritakagp.processing.arbeidsgivernotifikasjon.ArbeidsgiverNotifikasjonProcessor
import no.nav.helse.fritakagp.processing.kronisk.krav.KroniskKravEndreProcessor
import no.nav.helse.fritakagp.processing.kronisk.krav.KroniskKravKvitteringProcessor
import no.nav.helse.fritakagp.processing.kronisk.krav.KroniskKravProcessor
import no.nav.helse.fritakagp.processing.kronisk.krav.KroniskKravSlettProcessor
import no.nav.helse.fritakagp.processing.kronisk.soeknad.KroniskSoeknadKvitteringProcessor
import no.nav.helse.fritakagp.processing.kronisk.soeknad.KroniskSoeknadProcessor
import no.nav.helse.fritakagp.web.api.RequestHandler
import no.nav.helse.fritakagp.web.api.resreq.KroniskKravRequest
import no.nav.helse.fritakagp.web.api.resreq.KroniskSoknadRequest
import no.nav.helse.fritakagp.web.auth.AuthService
import no.nav.helse.fritakagp.web.auth.hentFnrFraLoginToken
import no.nav.helsearbeidsgiver.aareg.AaregClient
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.ArbeidsgiverNotifikasjonKlient
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.SakEllerOppgaveFinnesIkkeException
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import java.time.LocalDateTime
import java.util.UUID

fun Route.kroniskRoutes(
    authService: AuthService,
    brregService: IBrregService,
    pdlService: PdlService,
    belopBeregning: BeloepBeregning,
    aaregClient: AaregClient,
    arbeidsgiverNotifikasjonKlient: ArbeidsgiverNotifikasjonKlient,
    kroniskSoeknadRepo: KroniskSoeknadRepository,
    kroniskKravRepo: KroniskKravRepository,
    bakgunnsjobbService: BakgrunnsjobbService,
    virusScanner: VirusScanner,
    bucket: BucketStorage,
    om: ObjectMapper
) {
    val logger = "kroniskRoutes".logger()
    val sikkerLogger = sikkerLogger()

    val requestHandler = RequestHandler(
        aapenLogger = logger,
        sikkerLogger = sikkerLogger
    )

    fun slettSak(sakId: String) {
        try {
            runBlocking { arbeidsgiverNotifikasjonKlient.hardDeleteSak(sakId) }
        } catch (_: SakEllerOppgaveFinnesIkkeException) {
            logger.warn("Klarte ikke slette sak med ID '$sakId' fordi saken finnes ikke.")
        }
    }

    route("/kronisk") {
        route("/soeknad") {
            get("/{id}") {
                val soeknadId = requestHandler.lesParameterId(this)

                MdcUtils.withLogFields(
                    Log.apiRoute("GET /kronisk/soeknad/{id}"),
                    Log.soeknadId(soeknadId),
                    Log.kontekstId(UUID.randomUUID())
                ) {
                    logger.info("Hent kronisk søknad.")

                    val innloggetFnr = hentFnrFraLoginToken()

                    logger.info("Hent kronisk søknad fra database.")
                    val soeknad = kroniskSoeknadRepo.getById(soeknadId)

                    if (soeknad == null || soeknad.identitetsnummer != innloggetFnr) {
                        logger.warn("Kronisk søknad ikke funnet eller matcher ikke innlogget fnr.")
                        call.respond(HttpStatusCode.NotFound)
                    } else {
                        logger.info("Hent personinfo fra PDL.")
                        soeknad.sendtAvNavn = soeknad.sendtAvNavn ?: pdlService.hentNavn(innloggetFnr)
                        soeknad.navn = soeknad.navn ?: pdlService.hentNavn(soeknad.identitetsnummer)

                        logger.info("Kronisk søknad hentet OK.")
                        call.respond(HttpStatusCode.OK, soeknad)
                    }
                }
            }

            post {
                MdcUtils.withLogFields(
                    Log.apiRoute("POST /kronisk/soeknad"),
                    Log.kontekstId(UUID.randomUUID())
                ) {
                    logger.info("Motta kronisk søknad.")

                    val request = requestHandler.lesRequestBody<KroniskSoknadRequest>(this)

                    logger.info("Hent virksomhet fra brreg.")
                    val isVirksomhet = brregService.erOrganisasjon(request.virksomhetsnummer)

                    logger.info("Valider request.")
                    request.validate(isVirksomhet)

                    val innloggetFnr = hentFnrFraLoginToken()

                    logger.info("Hent personinfo fra PDL.")
                    val sendtAvNavn = pdlService.hentNavn(innloggetFnr)
                    val navn = pdlService.hentNavn(request.identitetsnummer)

                    val soeknad = request.toDomain(innloggetFnr, sendtAvNavn, navn)

                    logger.info("Prosesser dokument for GCP-lagring.")
                    processDocumentForGCPStorage(request.dokumentasjon, virusScanner, bucket, soeknad.id)

                    logger.info("Lagre kronisk søknad i database.")
                    kroniskSoeknadRepo.insert(soeknad)
                    bakgunnsjobbService.opprettJobb<KroniskSoeknadProcessor>(
                        maksAntallForsoek = 10,
                        data = om.writeValueAsString(KroniskSoeknadProcessor.JobbData(soeknad.id))
                    )
                    bakgunnsjobbService.opprettJobb<KroniskSoeknadKvitteringProcessor>(
                        maksAntallForsoek = 10,
                        data = om.writeValueAsString(KroniskSoeknadKvitteringProcessor.Jobbdata(soeknad.id))
                    )

                    logger.info("Kronisk søknad mottatt OK.")
                    call.respond(HttpStatusCode.Created, soeknad)
                    KroniskSoeknadMetrics.tellMottatt()
                }
            }
        }

        route("/krav") {
            get("/{id}") {
                val kravId = requestHandler.lesParameterId(this)
                val erSlettet = call.request.queryParameters.contains("slettet")

                MdcUtils.withLogFields(
                    Log.apiRoute("GET /kronisk/krav/{id}"),
                    Log.kravId(kravId),
                    Log.kontekstId(UUID.randomUUID())
                ) {
                    logger.info("Hent kronisk krav.")

                    val innloggetFnr = hentFnrFraLoginToken()

                    logger.info("Hent kronisk krav fra database.")
                    val krav = kroniskKravRepo.getById(kravId)

                    if (krav == null) {
                        logger.warn("Kronisk krav ikke funnet.")
                        call.respond(HttpStatusCode.NotFound)
                    } else if (!erSlettet && krav.status == KravStatus.SLETTET) {
                        logger.warn("Kronisk krav er slettet.")
                        call.respond(HttpStatusCode.NotFound)
                    } else {
                        if (krav.identitetsnummer != innloggetFnr) {
                            logger.info("Fnr på kronisk krav matcher ikke innlogget fnr.")
                            authService.validerTilgangTilOrganisasjon(this, krav.virksomhetsnummer)
                        }

                        logger.info("Hent personinfo fra PDL.")
                        krav.sendtAvNavn = krav.sendtAvNavn ?: pdlService.hentNavn(innloggetFnr)
                        krav.navn = krav.navn ?: pdlService.hentNavn(krav.identitetsnummer)

                        logger.info("Kronisk krav hentet OK.")
                        call.respond(HttpStatusCode.OK, krav)
                    }
                }
            }

            post {
                MdcUtils.withLogFields(
                    Log.apiRoute("POST /kronisk/krav"),
                    Log.kontekstId(UUID.randomUUID())
                ) {
                    logger.info("Motta kronisk krav.")

                    val request = requestHandler.lesRequestBody<KroniskKravRequest>(this)

                    authService.validerTilgangTilOrganisasjon(this, request.virksomhetsnummer)

                    val callId = UUID.randomUUID().toString()
                    logger.info("Hent ansettelsesperioder fra aareg, callId: $callId")
                    val ansettelsesperioder = aaregClient
                        .hentAnsettelsesperioder(request.identitetsnummer, callId)
                        .get(Orgnr(request.virksomhetsnummer))
                        .orEmpty()

                    logger.info("Valider request.")
                    request.validate(ansettelsesperioder)

                    val innloggetFnr = hentFnrFraLoginToken()

                    logger.info("Hent personinfo fra PDL.")
                    val sendtAvNavn = pdlService.hentNavn(innloggetFnr)
                    val navn = pdlService.hentNavn(request.identitetsnummer)

                    val krav = request.toDomain(innloggetFnr, sendtAvNavn, navn)

                    logger.info("Hent grunnbeløp.")
                    belopBeregning.beregnBeloepKronisk(krav)

                    logger.info("Legg til kronisk krav i database.")
                    kroniskKravRepo.insert(krav)
                    bakgunnsjobbService.opprettJobb<KroniskKravProcessor>(
                        maksAntallForsoek = 10,
                        data = om.writeValueAsString(KroniskKravProcessor.JobbData(krav.id))
                    )
                    bakgunnsjobbService.opprettJobb<KroniskKravKvitteringProcessor>(
                        maksAntallForsoek = 10,
                        data = om.writeValueAsString(KroniskKravKvitteringProcessor.Jobbdata(krav.id))
                    )
                    bakgunnsjobbService.opprettJobb<ArbeidsgiverNotifikasjonProcessor>(
                        maksAntallForsoek = 10,
                        data = om.writeValueAsString(ArbeidsgiverNotifikasjonProcessor.JobbData(krav.id, ArbeidsgiverNotifikasjonProcessor.JobbData.SkjemaType.KroniskKrav))
                    )

                    logger.info("Kronisk krav mottatt OK.")
                    call.respond(HttpStatusCode.Created, krav)
                    KroniskKravMetrics.tellMottatt()
                }
            }

            patch("/{id}") {
                val kravId = requestHandler.lesParameterId(this)

                MdcUtils.withLogFields(
                    Log.apiRoute("PATCH /kronisk/krav/{id}"),
                    Log.kravId(kravId),
                    Log.kontekstId(UUID.randomUUID())
                ) {
                    logger.info("Oppdater kronisk krav.")

                    val request = requestHandler.lesRequestBody<KroniskKravRequest>(this)

                    authService.validerTilgangTilOrganisasjon(this, request.virksomhetsnummer)

                    val innloggetFnr = hentFnrFraLoginToken()

                    logger.info("Hent personinfo fra PDL.")
                    val sendtAvNavn = pdlService.hentNavn(innloggetFnr)
                    val navn = pdlService.hentNavn(request.identitetsnummer)

                    logger.info("Hent ansettelsesperioder fra aareg.")
                    val ansettelsesperioder = aaregClient
                        .hentAnsettelsesperioder(request.identitetsnummer, UUID.randomUUID().toString())
                        .get(Orgnr(request.virksomhetsnummer))
                        .orEmpty()

                    logger.info("Valider request.")
                    request.validate(ansettelsesperioder)

                    logger.info("Hent gammelt kronisk krav fra database.")
                    val forrigeKrav = kroniskKravRepo.getById(kravId)

                    if (forrigeKrav == null) {
                        logger.warn("Kronisk krav ikke funnet.")
                        return@patch call.respond(HttpStatusCode.NotFound)
                    } else if (forrigeKrav.virksomhetsnummer != request.virksomhetsnummer) {
                        logger.warn("Orgnr på forrige kronisk krav matcher ikke nytt krav.")
                        return@patch call.respond(HttpStatusCode.Forbidden)
                    }

                    val kravTilOppdatering = request.toDomain(innloggetFnr, sendtAvNavn, navn)

                    logger.info("Hent grunnbeløp.")
                    belopBeregning.beregnBeloepKronisk(kravTilOppdatering)

                    if (forrigeKrav.isDuplicate(kravTilOppdatering)) {
                        logger.warn("Nytt kronisk krav er duplikat.")
                        return@patch call.respond(HttpStatusCode.Conflict)
                    }

                    kravTilOppdatering.status = KravStatus.OPPDATERT

                    forrigeKrav.status = KravStatus.ENDRET
                    forrigeKrav.slettetAv = innloggetFnr
                    forrigeKrav.slettetAvNavn = sendtAvNavn
                    forrigeKrav.endretDato = LocalDateTime.now()
                    forrigeKrav.endretTilId = kravTilOppdatering.id

                    // Sletter gammelt krav
                    val arbeidsgiverSakId = forrigeKrav.arbeidsgiverSakId
                    if (arbeidsgiverSakId != null) {
                        logger.info("Slett sak for gammelt kronisk krav i arbeidsgivernotifikasjon.")
                        slettSak(arbeidsgiverSakId)
                    }

                    logger.info("Oppdater gammelt kronisk krav til status '${KravStatus.ENDRET}' i database.")
                    kroniskKravRepo.update(forrigeKrav)

                    // Oppretter nytt krav
                    logger.info("Legg til nytt kronisk krav med status '${KravStatus.OPPDATERT}' i database.")
                    kroniskKravRepo.insert(kravTilOppdatering)
                    bakgunnsjobbService.opprettJobb<KroniskKravEndreProcessor>(
                        maksAntallForsoek = 10,
                        data = om.writeValueAsString(KroniskKravProcessor.JobbData(forrigeKrav.id))
                    )
                    bakgunnsjobbService.opprettJobb<KroniskKravKvitteringProcessor>(
                        maksAntallForsoek = 10,
                        data = om.writeValueAsString(KroniskKravKvitteringProcessor.Jobbdata(kravTilOppdatering.id))
                    )
                    bakgunnsjobbService.opprettJobb<ArbeidsgiverNotifikasjonProcessor>(
                        maksAntallForsoek = 10,
                        data = om.writeValueAsString(ArbeidsgiverNotifikasjonProcessor.JobbData(kravTilOppdatering.id, ArbeidsgiverNotifikasjonProcessor.JobbData.SkjemaType.KroniskKrav))
                    )

                    logger.info("Kronisk krav oppdatert OK.")
                    call.respond(HttpStatusCode.OK, kravTilOppdatering)
                }
            }

            delete("/{id}") {
                val kravId = requestHandler.lesParameterId(this)

                MdcUtils.withLogFields(
                    Log.apiRoute("DELETE /kronisk/krav/{id}"),
                    Log.kravId(kravId),
                    Log.kontekstId(UUID.randomUUID())
                ) {
                    logger.info("Slett kronisk krav.")

                    val innloggetFnr = hentFnrFraLoginToken()

                    logger.info("Hent personinfo fra PDL.")
                    val slettetAv = pdlService.hentNavn(innloggetFnr)

                    val krav = kroniskKravRepo.getById(kravId)
                    if (krav == null) {
                        logger.warn("Kronisk krav ikke funnet.")
                        return@delete call.respond(HttpStatusCode.NotFound)
                    }

                    authService.validerTilgangTilOrganisasjon(this, krav.virksomhetsnummer)

                    val arbeidsgiverSakId = krav.arbeidsgiverSakId
                    if (arbeidsgiverSakId != null) {
                        logger.info("Slett sak for kronisk krav i arbeidsgivernotifikasjon.")
                        slettSak(arbeidsgiverSakId)
                    }

                    krav.status = KravStatus.SLETTET
                    krav.slettetAv = innloggetFnr
                    krav.slettetAvNavn = slettetAv
                    krav.endretDato = LocalDateTime.now()

                    logger.info("Oppdater kronisk krav til slettet i database.")
                    kroniskKravRepo.update(krav)
                    bakgunnsjobbService.opprettJobb<KroniskKravSlettProcessor>(
                        maksAntallForsoek = 10,
                        data = om.writeValueAsString(KroniskKravProcessor.JobbData(krav.id))
                    )
                    bakgunnsjobbService.opprettJobb<KroniskKravKvitteringProcessor>(
                        maksAntallForsoek = 10,
                        data = om.writeValueAsString(KroniskKravKvitteringProcessor.Jobbdata(krav.id))
                    )

                    logger.info("Kronisk krav slettet OK.")
                    call.respond(HttpStatusCode.OK)
                }
            }
        }
    }
}
