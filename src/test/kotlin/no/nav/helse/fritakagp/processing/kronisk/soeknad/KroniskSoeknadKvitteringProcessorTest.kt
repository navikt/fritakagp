package no.nav.helse.fritakagp.processing.kronisk.soeknad

import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.helse.KroniskTestData
import no.nav.helse.fritakagp.customObjectMapper
import no.nav.helse.fritakagp.db.KroniskSoeknadRepository
import no.nav.helse.fritakagp.kafka.DialogMelding
import no.nav.helse.fritakagp.kafka.DialogSender
import no.nav.helse.fritakagp.processing.BakgrunnsJobbUtils
import no.nav.helsearbeidsgiver.utils.json.toJsonStr
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class KroniskSoeknadKvitteringProcessorTest {

    private val repositoryMock = mockk<KroniskSoeknadRepository>(relaxed = true)
    private val objectMapper: ObjectMapper = customObjectMapper()
    private val dialogSenderMock = mockk<DialogSender>(relaxed = true)

    private val processor = KroniskSoeknadKvitteringProcessor(
        db = repositoryMock,
        om = objectMapper,
        dialogSender = dialogSenderMock
    )

    private val testSoeknad = KroniskTestData.soeknadKronisk.copy()

    private var jobb = BakgrunnsJobbUtils.emptyJob()

    @BeforeEach
    fun setup() {
        every { repositoryMock.getById(testSoeknad.id) } returns testSoeknad
        jobb = BakgrunnsJobbUtils.testJob(
            objectMapper.writeValueAsString(KroniskSoeknadKvitteringProcessor.Jobbdata(testSoeknad.id))
        )
    }

    @Test
    fun `skal sende dialog melding og kvittering`() {
        processor.prosesser(jobb)
        val expectedMessage = DialogMelding(
            type = DialogMelding.Type.KroniskSoeknadOpprettet,
            id = testSoeknad.id,
            orgnr = Orgnr(testSoeknad.virksomhetsnummer),
            navn = testSoeknad.navn!!,
            fnr = testSoeknad.identitetsnummer
        ).toJsonStr(DialogMelding.serializer())
        verify(exactly = 1) { dialogSenderMock.sendMessage(expectedMessage) }
    }

    @Test
    fun `skal kaste exception nar soeknad ikke finnes i databasen`() {
        every { repositoryMock.getById(testSoeknad.id) } returns null

        assertThrows<IllegalArgumentException> { processor.prosesser(jobb) }

        verify(exactly = 0) { dialogSenderMock.sendMessage(any()) }
    }
}
