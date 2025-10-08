package no.nav.helse.fritakagp.processing.gravid.krav

import no.nav.helse.GravidTestData
import no.nav.helse.fritakagp.domain.AarsakEndring
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.awt.Desktop
import java.nio.file.Files
import java.time.LocalDateTime
import kotlin.math.roundToInt

class GravidKravPDFGeneratorTest {

    @Test
    fun testLagPDF() {
        val krav = GravidTestData.gravidKrav
        val pdf = GravidKravPDFGenerator().lagPDF(krav)
        assertThat(pdf).isNotNull

        val pdfText = extractTextFromPdf(pdf)
        val antallSider = numberOfPagesInPDF(pdf)

        assertThat(pdfText).contains(krav.navn)
        assertThat(pdfText).contains(krav.virksomhetsnummer)
        assertThat(pdfText).contains(krav.perioder.first().månedsinntekt.roundToInt().toString())
        assertThat(antallSider).isEqualTo(1)
    }

    @Test
    fun `test lag krav over flere sider`() {
        val krav = GravidTestData.gravidLangtKrav
        val pdf = GravidKravPDFGenerator().lagPDF(krav)
        assertThat(pdf).isNotNull

        val antallSider = numberOfPagesInPDF(pdf)

        assertThat(antallSider).isEqualTo(2)
    }

    @Test
    fun testLagSlettingPDF() {
        val krav = GravidTestData.gravidKrav.copy(journalpostId = "12345", endretDato = LocalDateTime.now())
        val pdf = GravidKravPDFGenerator().lagSlettingPDF(krav)
        assertThat(pdf).isNotNull

        val pdfText = extractTextFromPdf(pdf)

        assertThat(pdfText).contains(krav.navn)
        assertThat(pdfText).contains(krav.virksomhetsnummer)
        assertThat(pdfText).contains(krav.journalpostId)
    }

    @Test
    fun testLagEndringPDF() {
        val krav = GravidTestData.gravidKrav.copy(journalpostId = "12345", endretDato = LocalDateTime.now())
        val endretKrav = krav.copy(journalpostId = "12346", endretDato = LocalDateTime.now(), aarsakEndring = AarsakEndring.TARIFFENDRING.name)
        val pdf = GravidKravPDFGenerator().lagEndringPdf(krav, endretKrav)
        assertThat(pdf).isNotNull

        val pdfText = extractTextFromPdf(pdf)

        assertThat(pdfText).contains(endretKrav.navn)
        assertThat(pdfText).contains(endretKrav.virksomhetsnummer)
        assertThat(pdfText).contains(endretKrav.aarsakEndring)
    }

    @Test
    @Disabled
    fun saveAndShowPdf() {
        // test for å visuelt sjekke ut PDFen
        val krav = GravidTestData.gravidKrav
        val pdf = GravidKravPDFGenerator().lagPDF(krav)

        val file = Files.createTempFile(null, ".pdf").toFile()
        file.writeBytes(pdf)

        Desktop.getDesktop().open(file)
    }

    private fun numberOfPagesInPDF(pdf: ByteArray): Int {
        val pdfReader = PDDocument.load(pdf)
        return pdfReader.numberOfPages
    }

    private fun extractTextFromPdf(pdf: ByteArray): String? {
        val pdfReader = PDDocument.load(pdf)
        val pdfStripper = PDFTextStripper()
        val allTextInDocument = pdfStripper.getText(pdfReader)
        pdfReader.close()
        return allTextInDocument
    }
}
