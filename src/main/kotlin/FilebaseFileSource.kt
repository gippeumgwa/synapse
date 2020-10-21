import com.google.common.io.ByteSink
import com.google.common.io.ByteSource
import com.nao20010128nao.Cryptorage.FileSource
import com.nao20010128nao.CryptorageExtras.sink
import com.nao20010128nao.CryptorageExtras.source
import java.io.ByteArrayOutputStream

class FilebaseFileSource(
    private val accessId: String = filebaseAccessID,
    private val accessKey: String = filebaseAccessKey,
    private val bucket: String = filebaseBucket
) : FileSource {
    private val objects by backgroundInit {
        listFilesInFilebase(accessId, accessKey, bucket)
            .associateBy { it.key() }
            .toSortedMap()
    }

    override val isReadOnly: Boolean = false

    override fun close() {
    }

    override fun commit() {
    }

    override fun delete(name: String) = deleteFileFromFilebase(name, accessId, accessKey, bucket)

    override fun lastModified(name: String): Long = objects[name]?.lastModified()?.toEpochMilli() ?: -1

    override fun list(): List<String> = objects.keys.toList()

    override fun open(name: String, offset: Int): ByteSource = source {
        fileStreamFromFilebase(name, accessId, accessKey, bucket).also {
            it.skip(offset.toLong())
        }
    }

    override fun put(name: String): ByteSink = sink {
        object : ByteArrayOutputStream() {
            override fun close() {
                probable(1000) {
                    uploadFileToFilebase(name, accessId, accessKey, bucket) {
                        write(toByteArray())
                    }
                }!!
            }
        }
    }

    override fun size(name: String): Long = objects[name]?.size() ?: -1
    override fun has(name: String): Boolean = try {
        open(name).openStream().close()
        true
    } catch (e: Throwable) {
        false
    }
}
