package no.nav.helse.fritakagp.processing.kronisk.krav

import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.helse.KroniskTestData
import no.nav.helse.fritakagp.customObjectMapper
import no.nav.helse.fritakagp.db.KroniskKravRepository
import no.nav.helse.fritakagp.domain.KravStatus
import no.nav.helse.fritakagp.kafka.DialogMelding
import no.nav.helse.fritakagp.kafka.DialogMeldingType
import no.nav.helse.fritakagp.kafka.DialogSender
import no.nav.helse.fritakagp.processing.BakgrunnsJobbUtils
import no.nav.helsearbeidsgiver.utils.json.toJsonStr
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

internal class KroniskKravKvitteringProcessorTest {

    private val kroniskKravKvitteringSenderMock = mockk<KroniskKravKvitteringSender>(relaxed = true)
    private val repositoryMock = mockk<KroniskKravRepository>(relaxed = true)
    private val objectMapper: ObjectMapper = customObjectMapper()
    private val dialogSenderMock = mockk<DialogSender>(relaxed = true)

    private val processor = KroniskKravKvitteringProcessor(
        kroniskKravKvitteringSender = kroniskKravKvitteringSenderMock,
        db = repositoryMock,
        om = objectMapper,
        dialogSender = dialogSenderMock
    )

    private val testKrav = KroniskTestData.kroniskKrav.copy(status = KravStatus.OPPRETTET)

    private var jobb = BakgrunnsJobbUtils.emptyJob()

    @BeforeEach
    fun setup() {
        every { repositoryMock.getById(testKrav.id) } returns testKrav
        jobb = BakgrunnsJobbUtils.testJob(
            objectMapper.writeValueAsString(KroniskKravKvitteringProcessor.Jobbdata(testKrav.id))
        )
    }

    @Test
    fun `skal sende dialog melding for OPPRETTET krav`() {
        testKrav.status = KravStatus.OPPRETTET

        processor.prosesser(jobb)

        verify(exactly = 1) { dialogSenderMock.sendMessage(any()) }
        verify(exactly = 1) { kroniskKravKvitteringSenderMock.send(testKrav) }
    }

    @Test
    fun `skal sende korrekt dialog melding for OPPRETTET krav`() {
        testKrav.status = KravStatus.OPPRETTET

        processor.prosesser(jobb)

        val expectedMessage = DialogMelding(
            type = DialogMeldingType.KroniskKravOpprettet,
            id = testKrav.id,
            orgnr = Orgnr(testKrav.virksomhetsnummer),
            navn = testKrav.navn!!,
            fnr = testKrav.identitetsnummer
        ).toJsonStr(DialogMelding.serializer())

        verify(exactly = 1) { dialogSenderMock.sendMessage(expectedMessage) }
    }

    @Test
    fun `skal sende dialog melding for OPPDATERT krav`() {
        testKrav.status = KravStatus.OPPDATERT
        val forrigeKravId = UUID.randomUUID()
        jobb = BakgrunnsJobbUtils.testJob(
            objectMapper.writeValueAsString(KroniskKravKvitteringProcessor.Jobbdata(testKrav.id, forrigeKravId))
        )

        processor.prosesser(jobb)

        verify(exactly = 1) { dialogSenderMock.sendMessage(any()) }
        verify(exactly = 1) { kroniskKravKvitteringSenderMock.send(testKrav) }
    }

    @Test
    fun `skal sende korrekt dialog melding for OPPDATERT krav`() {
        testKrav.status = KravStatus.OPPDATERT
        val forrigeKravId = UUID.randomUUID()
        jobb = BakgrunnsJobbUtils.testJob(
            objectMapper.writeValueAsString(KroniskKravKvitteringProcessor.Jobbdata(testKrav.id, forrigeKravId))
        )

        processor.prosesser(jobb)

        val expectedMessage = DialogMelding(
            type = DialogMeldingType.KroniskKravEndret,
            id = testKrav.id,
            orgnr = Orgnr(testKrav.virksomhetsnummer),
            navn = testKrav.navn!!,
            fnr = testKrav.identitetsnummer,
            forrigeKrav = forrigeKravId
        ).toJsonStr(DialogMelding.serializer())

        verify(exactly = 1) { dialogSenderMock.sendMessage(expectedMessage) }
    }

    @Test
    fun `skal kaste exception nar forrigeKrav mangler for OPPDATERT status`() {
        testKrav.status = KravStatus.OPPDATERT
        jobb = BakgrunnsJobbUtils.testJob(
            objectMapper.writeValueAsString(KroniskKravKvitteringProcessor.Jobbdata(testKrav.id, forrigeKrav = null))
        )

        assertThrows<IllegalArgumentException> { processor.prosesser(jobb) }

        verify(exactly = 0) { dialogSenderMock.sendMessage(any()) }
        verify(exactly = 0) { kroniskKravKvitteringSenderMock.send(any()) }
    }

    @Test
    fun `skal sende dialog melding for SLETTET krav`() {
        testKrav.status = KravStatus.SLETTET

        processor.prosesser(jobb)

        verify(exactly = 1) { dialogSenderMock.sendMessage(any()) }
        verify(exactly = 1) { kroniskKravKvitteringSenderMock.send(testKrav) }
    }

    @Test
    fun `skal sende korrekt dialog melding for SLETTET krav`() {
        testKrav.status = KravStatus.SLETTET

        processor.prosesser(jobb)

        val expectedMessage = DialogMelding(
            type = DialogMeldingType.KroniskKravSlettet,
            id = testKrav.id,
            orgnr = Orgnr(testKrav.virksomhetsnummer),
            navn = testKrav.navn!!,
            fnr = testKrav.identitetsnummer
        ).toJsonStr(DialogMelding.serializer())

        verify(exactly = 1) { dialogSenderMock.sendMessage(expectedMessage) }
    }

    @Test
    fun `skal kaste exception nar krav ikke finnes i databasen`() {
        every { repositoryMock.getById(testKrav.id) } returns null

        assertThrows<IllegalArgumentException> { processor.prosesser(jobb) }

        verify(exactly = 0) { dialogSenderMock.sendMessage(any()) }
        verify(exactly = 0) { kroniskKravKvitteringSenderMock.send(any()) }
    }

    @Test
    fun `skal kaste exception ved uventet kravstatus ENDRET`() {
        testKrav.status = KravStatus.ENDRET

        assertThrows<IllegalArgumentException> { processor.prosesser(jobb) }

        verify(exactly = 0) { dialogSenderMock.sendMessage(any()) }
        verify(exactly = 0) { kroniskKravKvitteringSenderMock.send(any()) }
    }

    @Test
    fun `skal bruke Ukjent som navn nar krav navn er null`() {
        val kravUtenNavn = testKrav.copy(navn = null)
        every { repositoryMock.getById(kravUtenNavn.id) } returns kravUtenNavn

        processor.prosesser(jobb)

        val expectedMessage = DialogMelding(
            type = DialogMeldingType.KroniskKravOpprettet,
            id = kravUtenNavn.id,
            orgnr = Orgnr(kravUtenNavn.virksomhetsnummer),
            navn = "Ukjent",
            fnr = kravUtenNavn.identitetsnummer
        ).toJsonStr(DialogMelding.serializer())

        verify(exactly = 1) { dialogSenderMock.sendMessage(expectedMessage) }
    }
}
