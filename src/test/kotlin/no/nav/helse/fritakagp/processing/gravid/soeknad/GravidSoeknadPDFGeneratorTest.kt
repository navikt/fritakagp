package no.nav.helse.fritakagp.processing.gravid.soeknad

import no.nav.helse.GravidTestData
import no.nav.helse.fritakagp.domain.DATE_FORMAT
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.awt.Desktop
import java.nio.file.Files

class GravidSoeknadPDFGeneratorTest {
    @Test
    fun testLagPDF() {
        val soeknad = GravidTestData.soeknadGravid
        val pdf = GravidSoeknadPDFGenerator().lagPDF(soeknad)
        assertThat(pdf).isNotNull

        val pdfText = extractTextFromPdf(pdf)

        assertThat(pdfText).contains(soeknad.tiltakBeskrivelse?.substring(0, 50)) // sjekker bare starten pga wrapping
        assertThat(pdfText).contains(soeknad.navn)
        assertThat(pdfText).contains(soeknad.omplasseringAarsak?.beskrivelse)
        assertThat(pdfText).contains(soeknad.omplassering?.beskrivelse)
        assertThat(pdfText).contains(soeknad.virksomhetsnummer)
        assertThat(pdfText).contains(soeknad.termindato?.format(DATE_FORMAT))

        soeknad.tiltak?.forEach { assertThat((pdfText)?.contains(it.beskrivelse)) }
    }

    @Test
    @Disabled
    fun saveAndShowPdf() {
        // test for å visuelt sjekke ut PDFen
        val soeknad = GravidTestData.soeknadGravid
        val pdf = GravidSoeknadPDFGenerator().lagPDF(soeknad)

        val file = Files.createTempFile(null, ".pdf").toFile()
        file.writeBytes(pdf)

        Desktop.getDesktop().open(file)
    }

    private fun extractTextFromPdf(pdf: ByteArray): String? {
        val pdfReader = PDDocument.load(pdf)
        val pdfStripper = PDFTextStripper()
        val allTextInDocument = pdfStripper.getText(pdfReader)
        pdfReader.close()
        return allTextInDocument
    }
}
