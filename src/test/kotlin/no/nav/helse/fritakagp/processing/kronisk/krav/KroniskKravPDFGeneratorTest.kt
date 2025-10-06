package no.nav.helse.fritakagp.processing.kronisk.krav

import no.nav.helse.KroniskTestData
import no.nav.helse.fritakagp.domain.AarsakEndring
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.awt.Desktop
import java.nio.file.Files
import java.time.LocalDateTime

class KroniskKravPDFGeneratorTest {

    @Test
    fun testLagPDF() {
        val krav = KroniskTestData.kroniskKrav
        val pdf = KroniskKravPDFGenerator().lagPDF(krav)
        assertThat(pdf).isNotNull

        val pdfText = extractTextFromPdf(pdf)
        val antallSider = numberOfPagesInPDF(pdf)

        assertThat(pdfText).contains(krav.navn)
        assertThat(pdfText).contains(krav.virksomhetsnummer)
        assertThat(antallSider).isEqualTo(1)
    }

    @Test
    fun `test lag krav over flere sider`() {
        val krav = KroniskTestData.kroniskLangtKrav
        val pdf = KroniskKravPDFGenerator().lagPDF(krav)
        assertThat(pdf).isNotNull

        val antallSider = numberOfPagesInPDF(pdf)

        assertThat(antallSider).isEqualTo(2)
    }

    @Test
    fun testLagSlettingPDF() {
        val krav = KroniskTestData.kroniskKrav.copy(journalpostId = "12345", endretDato = LocalDateTime.now())
        val pdf = KroniskKravPDFGenerator().lagSlettingPDF(krav)
        assertThat(pdf).isNotNull

        val pdfText = extractTextFromPdf(pdf)

        assertThat(pdfText).contains(krav.navn)
        assertThat(pdfText).contains(krav.virksomhetsnummer)
        assertThat(pdfText).contains(krav.journalpostId)
    }

    @Test
    fun testLagEndringPDF() {
        val krav = KroniskTestData.kroniskKrav.copy(journalpostId = "12345", endretDato = LocalDateTime.now())
        val endretKrav = krav.copy(journalpostId = "12346", endretDato = LocalDateTime.now(), aarsakEndring = AarsakEndring.TARIFFENDRING.name)
        val pdf = KroniskKravPDFGenerator().lagEndringPdf(krav, endretKrav)
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
        val krav = KroniskTestData.kroniskKrav
        val pdf = KroniskKravPDFGenerator().lagPDF(krav)

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
