package no.nav.helse.fritakagp.integration.gcp

import com.google.cloud.storage.BlobId
import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.Storage
import com.google.cloud.storage.StorageOptions
import java.util.UUID

interface BucketStorage {
    fun uploadDoc(uuid: UUID, filContent: String, filExt: String)
    fun getDocAsString(uuid: UUID): BucketDocument?
    fun deleteDoc(uuid: UUID)
}

data class BucketDocument(val base64Data: String, val extension: String)

class MockBucketStorage : BucketStorage {
    private val items = HashMap<String, BucketDocument>()
    private val default = BucketDocument("default doc", "txt")
    override fun uploadDoc(uuid: UUID, filContent: String, filExt: String) {
        items[uuid.toString()] = BucketDocument(filContent, filExt)
    }

    override fun getDocAsString(uuid: UUID): BucketDocument? {
        return items.getOrDefault(uuid.toString(), default)
    }

    override fun deleteDoc(uuid: UUID) {
        items.remove(uuid.toString())
    }
}

class BucketStorageImpl(
    private val bucketName: String = "helse-arbeidsgiver-fritakagb-bucket",
    gcpPrjID: String
) : BucketStorage {
    private val storage: Storage = StorageOptions.newBuilder().setProjectId(gcpPrjID).build().service

    override fun uploadDoc(uuid: UUID, filContent: String, filExt: String) {
        storage.create(
            createBlob(bucketName, uuid, filExt),
            filContent.toByteArray()
        )
    }

    override fun getDocAsString(uuid: UUID): BucketDocument? {
        val blob = storage[BlobId.of(bucketName, uuid.toString())] ?: return null
        val blobMeta = blob.metadata
        val ext = blobMeta?.get("ext") ?: ""

        return BucketDocument(blob.getContent().decodeToString(), ext)
    }

    override fun deleteDoc(uuid: UUID) {
        storage[BlobId.of(bucketName, uuid.toString())]?.delete()
    }

    private fun createBlob(bucketName: String, blobName: UUID, ext: String): BlobInfo {
        val blobMetadata: MutableMap<String, String?> = HashMap()
        blobMetadata["ext"] = ext
        val blobId = BlobId.of(bucketName, blobName.toString())

        return BlobInfo.newBuilder(blobId).setMetadata(blobMetadata).build()
    }
}
