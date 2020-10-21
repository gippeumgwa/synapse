import software.amazon.awssdk.auth.credentials.AwsCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.*
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.net.URI

fun uploadFileToFilebase(
    filename: String,
    accessId: String = filebaseAccessID,
    accessKey: String = filebaseAccessKey,
    bucket: String = filebaseBucket,
    stream: OutputStream.() -> Unit
) {
    s3client(accessId, accessKey).use { s3 ->
        s3.putObject(
            PutObjectRequest.builder()
                .bucket(bucket)
                .key(filename)
                .build(),
            RequestBody.fromBytes(ByteArrayOutputStream().also(stream).toByteArray())
        )
    }
}

fun fileStreamFromFilebase(
    filename: String,
    accessId: String = filebaseAccessID,
    accessKey: String = filebaseAccessKey,
    bucket: String = filebaseBucket
): BundledInputStream<S3Client> = s3client(accessId, accessKey).let { s3 ->
    val data = s3.getObject(GetObjectRequest.builder().bucket(bucket).key(filename).build())
    BundledInputStream(data, s3)
}

fun listFilesInFilebase(
    accessId: String = filebaseAccessID,
    accessKey: String = filebaseAccessKey,
    bucket: String = filebaseBucket
): Sequence<S3Object> = sequence {
    s3client(accessId, accessKey).use { s3 ->
        var listObjectsReqManual = ListObjectsV2Request.builder()
            .bucket(bucket)
            .maxKeys(1000)
            .build()

        while (true) {
            val listObjResponse = s3.listObjectsV2(listObjectsReqManual)
            val contents = listObjResponse.contents()
            if (contents.isEmpty()) {
                break
            }
            yieldAll(contents)
            val next = listObjResponse.nextContinuationToken() ?: break
            listObjectsReqManual = listObjectsReqManual.toBuilder()
                .continuationToken(next)
                .build()
        }
    }
}

fun deleteFileFromFilebase(
    filename: String,
    accessId: String = filebaseAccessID,
    accessKey: String = filebaseAccessKey,
    bucket: String = filebaseBucket
) {
    s3client(accessId, accessKey).use {
        it.deleteObject(
            DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(filename)
                .build()
        )
    }
}

private fun s3client(accessId: String, accessKey: String): S3Client {
    return S3Client.builder()
        .credentialsProvider(StaticCredentialsProvider.create(object : AwsCredentials {
            override fun accessKeyId(): String = accessId
            override fun secretAccessKey(): String = accessKey
        }))
        .endpointOverride(URI("https://s3.filebase.com"))
        .region(Region.US_EAST_1)
        .build()
}
