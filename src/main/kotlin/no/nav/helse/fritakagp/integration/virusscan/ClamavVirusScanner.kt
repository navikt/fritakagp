package no.nav.helse.fritakagp.integration.virusscan

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.append
import io.ktor.client.request.forms.formData
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.utils.io.core.writeFully

interface VirusScanner {
    suspend fun scanDoc(vedlagt: ByteArray): Boolean
}

class MockVirusScanner : VirusScanner {
    override suspend fun scanDoc(vedlagt: ByteArray): Boolean = true
}

class ClamavVirusScannerImp(
    private val httpClient: HttpClient,
    private val scanUrl: String,
) : VirusScanner {
    data class ScanResult(
        val Filename: String,
        val Result: Result,
    )

    enum class Result {
        FOUND,
        OK,
        ERROR,
    }

    override suspend fun scanDoc(vedlagt: ByteArray): Boolean {
        val scanResult: List<ScanResult> =
            httpClient
                .post {
                    url(scanUrl)
                    setBody(
                        MultiPartFormDataContent(
                            formData {
                                append(
                                    "file1",
                                    "vedlagt",
                                    ContentType.parse("application/octet-stream"),
                                    vedlagt.size.toLong(),
                                ) {
                                    writeFully(vedlagt)
                                }
                            },
                        ),
                    )
                }.body()
        return when (scanResult[0].Result) {
            Result.OK -> true
            Result.FOUND, Result.ERROR -> false
        }
    }
}
