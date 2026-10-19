package no.nav.helse.fritakagp.processing.kronisk.krav

import no.nav.helse.fritakagp.domain.DATE_FORMAT
import no.nav.helse.fritakagp.domain.KroniskKrav
import no.nav.helse.fritakagp.domain.TIMESTAMP_FORMAT
import no.nav.helse.fritakagp.domain.tilProsent
import no.nav.helse.fritakagp.processing.PdfConstants
import org.apache.commons.text.WordUtils
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.font.PDType0Font
import java.io.ByteArrayOutputStream
import java.time.LocalDateTime
import kotlin.math.roundToInt

class KroniskKravPDFGenerator {
    fun lagNySide(
        doc: PDDocument,
        font: PDType0Font,
    ): PDPageContentStream {
        val page = PDPage()
        doc.addPage(page)
        val content = PDPageContentStream(doc, page)
        content.beginText()
        val mediaBox = page.mediaBox
        val startX = mediaBox.lowerLeftX + PdfConstants.MARGIN_X
        val startY = mediaBox.upperRightY - PdfConstants.MARGIN_Y
        content.newLineAtOffset(startX, startY)
        content.setFont(font, PdfConstants.FONT_SIZE)
        return content
    }

    fun leggTilKrav(
        doc: PDDocument,
        krav: KroniskKrav,
        tittel: String,
    ) {
        val font =
            PDType0Font.load(
                doc,
                this::class.java.classLoader
                    .getResource(PdfConstants.FONT_NAME)
                    .openStream(),
            )
        var content = lagNySide(doc, font)
        content.setFont(font, PdfConstants.FONT_SIZE + 4)
        content.showText(tittel)
        content.setFont(font, PdfConstants.FONT_SIZE)

        krav.aarsakEndring?.let { content.writeTextWrapped(text = "Årsak til endring: $it", spacing = 4) }
        content.writeTextWrapped("Mottatt: ${krav.opprettet.format(TIMESTAMP_FORMAT)}")
        content.writeTextWrapped("Referansenummer: ${krav.referansenummer}")
        content.writeTextWrapped("Sendt av: ${krav.sendtAvNavn}")
        content.writeTextWrapped("Person navn: ${krav.navn}")
        content.writeTextWrapped("Arbeidsgiver oppgitt i krav: ${krav.virksomhetsnavn} (${krav.virksomhetsnummer})")
        content.writeTextWrapped("Antall lønnsdager: ${krav.antallDager}")
        content.writeTextWrapped("Perioder", 2)

        krav
            .perioder
            .sortedBy { it.fom }
            .withIndex()
            .forEach { (index, periode) ->
                // For hvert 4 nye krav, lag ny side
                if (index != 0 && index % 4 == 0) {
                    content.endText()
                    content.close()
                    content = lagNySide(doc, font)
                }
                with(content) {
                    writeTextWrapped("FOM: ${periode.fom.format(DATE_FORMAT)}")
                    writeTextWrapped("TOM: ${periode.tom.format(DATE_FORMAT)}")
                    writeTextWrapped("Sykmeldingsgrad: ${periode.gradering.tilProsent()}")
                    writeTextWrapped("Antall dager det kreves refusjon for: ${periode.antallDagerMedRefusjon}")
                    writeTextWrapped("Beregnet månedsinntekt (NOK): ${periode.månedsinntekt.roundToInt()}")
                    writeTextWrapped("Dagsats (NOK): ${periode.dagsats.roundToInt()}")
                    writeTextWrapped("Beløp (NOK): ${periode.belop.roundToInt()}")
                    writeTextWrapped("")
                }
            }
        content.endText()
        content.close()
    }

    fun lagPDF(krav: KroniskKrav): ByteArray {
        val doc = PDDocument()
        leggTilKrav(doc, krav, KroniskKrav.TITTEL)
        val out = ByteArrayOutputStream()
        doc.save(out)
        val ba = out.toByteArray()
        doc.close()
        return ba
    }

    fun lagEndringPdf(
        oppdatertKrav: KroniskKrav,
        endretKrav: KroniskKrav,
    ): ByteArray {
        val doc = PDDocument()
        leggTilKrav(doc, oppdatertKrav, "Endring ${KroniskKrav.TITTEL}")

        leggTilKrav(doc, endretKrav, "Tidligere ${KroniskKrav.TITTEL}")
        val out = ByteArrayOutputStream()
        doc.save(out)
        val ba = out.toByteArray()
        doc.close()
        return ba
    }

    fun lagSlettingPDF(krav: KroniskKrav): ByteArray {
        val doc = PDDocument()
        val font =
            PDType0Font.load(
                doc,
                this::class.java.classLoader
                    .getResource(PdfConstants.FONT_NAME)
                    .openStream(),
            )
        var content = lagNySide(doc, font)
        content.setFont(font, PdfConstants.FONT_SIZE + 4)
        content.showText("Annuller ${KroniskKrav.TITTEL}")
        content.setFont(font, PdfConstants.FONT_SIZE)

        content.writeTextWrapped("Annullering Mottatt: ${TIMESTAMP_FORMAT.format(krav.endretDato ?: LocalDateTime.now())}", 4)
        content.writeTextWrapped("Tidligere krav med JournalpostID: ${krav.journalpostId}")
        content.writeTextWrapped("Sendt av: ${krav.sendtAvNavn}")
        content.writeTextWrapped("Person navn: ${krav.navn}")
        content.writeTextWrapped("Arbeidsgiver oppgitt i krav: ${krav.virksomhetsnavn} (${krav.virksomhetsnummer})")
        content.writeTextWrapped("Perioder", 2)

        krav.perioder.withIndex().forEach { (index, periode) ->
            // For hvert 4 nye krav, lag ny side
            if (index != 0 && index % 4 == 0) {
                content.close()
                content = lagNySide(doc, font)
            }
            with(content) {
                writeTextWrapped("FOM: ${periode.fom.format(DATE_FORMAT)}")
                writeTextWrapped("TOM: ${periode.tom.format(DATE_FORMAT)}")
                writeTextWrapped("Sykmeldingsgrad: ${periode.gradering.tilProsent()}")
                writeTextWrapped("Antall dager det kreves refusjon for: ${periode.antallDagerMedRefusjon}")
                writeTextWrapped("Beregnet månedsinntekt (NOK): ${periode.månedsinntekt.roundToInt()}")
                writeTextWrapped("Dagsats (NOK): ${periode.dagsats.roundToInt()}")
                writeTextWrapped("Beløp (NOK): ${periode.belop.roundToInt()}")
                writeTextWrapped("")
            }
        }

        content.endText()
        content.close()
        val out = ByteArrayOutputStream()
        doc.save(out)
        val ba = out.toByteArray()
        doc.close()
        return ba
    }

    private fun PDPageContentStream.writeTextWrapped(
        text: String,
        spacing: Int = 1,
    ) {
        WordUtils.wrap(text, 100).split('\n').forEach {
            this.newLineAtOffset(0F, -PdfConstants.LINE_HEIGHT * spacing)
            this.showText(it)
        }
    }
}
