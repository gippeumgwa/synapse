@file:Suppress("UNCHECKED_CAST")

import com.beust.klaxon.JsonObject
import com.beust.klaxon.Klaxon
import com.google.common.io.ByteSink
import com.google.common.io.ByteSource
import com.google.common.io.ByteStreams
import com.nao20010128nao.Cryptorage.*
import com.nao20010128nao.CryptorageExtras.indexer.V3Indexer
import com.nao20010128nao.CryptorageExtras.sink
import com.nao20010128nao.CryptorageExtras.source
import com.nao20010128nao.CryptorageExtras.withNamePrefixed
import java.io.*
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.security.MessageDigest
import java.util.zip.GZIPOutputStream
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import kotlin.concurrent.thread
import kotlin.reflect.KProperty

const val FILE_LIST_TXT = "file_list_txt"
const val SIZE_LIST_BIN = "size_list_bin"

fun populateKeys(password: String): AesKeys {
    val utf8Bytes1 = password.utf8Bytes()
    val utf8Bytes2 = "$password$password".utf8Bytes()
    return utf8Bytes1.digest().digest().leading(16) to utf8Bytes2.digest().digest().trailing(16)
}


fun String.utf8Bytes(): ByteArray = toByteArray(UTF_8)


fun ByteArray.leading(n: Int): ByteArray = crop(0, n)

fun ByteArray.trailing(n: Int): ByteArray = crop(size - n, n)

fun ByteArray.crop(off: Int, len: Int): ByteArray {
    val result = ByteArray(len)
    System.arraycopy(this, off, result, 0, len)
    return result
}

fun createCipher(keys: AesKeys, mode: Int, algo: String = "AES/CBC/Pkcs5Padding"): Cipher {
    val cipher = Cipher.getInstance(algo)
    val (key, iv) = keys.forCrypto()
    cipher.init(mode, key, iv)
    return cipher
}

fun ByteSource.decrypt(keys: AesKeys) = source {
    CipherInputStream(openStream(), createCipher(keys, Cipher.DECRYPT_MODE))
}

fun ByteSource.encrypt(keys: AesKeys) = source {
    CipherInputStream(openStream(), createCipher(keys, Cipher.ENCRYPT_MODE))
}

fun ByteSink.encrypt(keys: AesKeys) = sink {
    CipherOutputStream(openStream(), createCipher(keys, Cipher.ENCRYPT_MODE))
}

fun ByteArray.encrypt(keys: AesKeys) = ByteSource.wrap(this).encrypt(keys).read()!!

fun ByteSink.gzip() = sink { GZIPOutputStream(openStream()) }

const val SHARD_CHUNK = 50
fun calculateFirstEntryNum(num: Int): Int = num * SHARD_CHUNK + 1

val indexerClass = V3Indexer::class.java
val indexClass = indexerClass.declaredClasses.find { it.simpleName == "Index" }!!

val readIndexMethod: Method = indexerClass.getDeclaredMethod("readIndex", FileSource::class.java, String::class.java)
    .also { it.isAccessible = true }
val finalIndexField: Field = indexerClass.getDeclaredField("finalIndex").also { it.isAccessible = true }
val indexFilesField: Field = indexClass.getDeclaredField("files").also { it.isAccessible = true }

fun V3Indexer.readIndex(fs: FileSource, name: String): Any = readIndexMethod.invoke(this, fs, name)
fun V3Indexer.addIndexFiles(givenIndex: Any) {
    val thisIndex = finalIndexField.get(this)
    val indexFiles = indexFilesField.get(thisIndex) as MutableMap<String, Any>
    val givenFiles = indexFilesField.get(givenIndex) as Map<String, Any>
    indexFiles.putAll(givenFiles)
}

private val defaultFFS: FileSource by lazy { FilebaseFileSource() }

fun openShardingFilebase(prefixName: String = prefix): FileSource = defaultFFS
    .withNamePrefixed("indexed/$prefixName/")
    .withV3Encryption(pass)
    .also {
        it.meta("split_size", "$SPLIT_SIZE")
    }

class MultiTextWriter(private val streams: Iterable<Writer>) : Writer() {
    override fun write(p0: Int) {
        streams.forEach {
            it.write(p0)
        }
    }

    override fun write(cbuf: CharArray, off: Int, len: Int) {
        streams.forEach {
            it.write(cbuf, off, len)
        }
    }

    override fun write(cbuf: CharArray) {
        streams.forEach {
            it.write(cbuf)
        }
    }

    override fun write(str: String) {
        streams.forEach {
            it.write(str)
        }
    }

    override fun write(str: String, off: Int, len: Int) {
        streams.forEach {
            it.write(str, off, len)
        }
    }

    override fun flush() {
        streams.forEach {
            it.flush()
        }
    }

    override fun close() {
        streams.forEach {
            it.close()
        }
    }
}

fun sha256(): MessageDigest = MessageDigest.getInstance("sha-256")
fun ByteArray.digest(md: MessageDigest = sha256()): ByteArray = md.digest(this)

inline fun <T> probable(max: Int = 5, printError: Boolean = true, f: () -> T?): T? {
    var lastError: Throwable? = null
    for (i in (0..max)) {
        return try {
            f()
        } catch (e: InterruptedException) {
            return null
        } catch (e: Throwable) {
            lastError = e
            null
        } ?: continue
    }
    if (printError || !debug)
        lastError?.printStackTrace()
    return null
}

class BundledInputStream<T>(inS: InputStream, private val data: T) : FilterInputStream(inS) {
    override fun close() {
        super.close()
        (data as? Closeable)?.close()
    }
}

object EmptyCryptorage : Cryptorage {
    override val isReadOnly: Boolean = false
    override fun close() = Unit
    override fun commit() = Unit
    override fun delete(name: String) = Unit
    override fun gc() = Unit
    override fun lastModified(name: String): Long = -1
    override fun list(): List<String> = emptyList()
    override fun meta(key: String): String? = null
    override fun meta(key: String, value: String) = Unit
    override fun mv(from: String, to: String) = Unit
    override fun open(name: String, offset: Int): ByteSource = source { ByteArrayInputStream(byteArrayOf()) }
    override fun put(name: String): ByteSink = sink { ByteStreams.nullOutputStream() }
    override fun size(name: String): Long = -1
    override fun has(name: String): Boolean = false
}

val klaxon = Klaxon()

fun parseJson(rdr: Reader): JsonObject = klaxon.parseJsonObject(rdr)
fun parseJson(text: String): JsonObject = parseJson(text.reader())

@JvmName("parseJson2")
fun String.parseJson(): JsonObject = parseJson(this)

@JvmName("parseJson2")
fun Reader.parseJson(): JsonObject = parseJson(this)


private object Uninitialized

fun <T> backgroundInit(f: () -> T): BackgroundInit<T> = BackgroundInit(f)
class BackgroundInit<T>(private val f: () -> T) {
    private var obj: Any? = Uninitialized
    private var initThread: Thread = thread(isDaemon = true) {
        obj = f()
    }

    tailrec operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
        if (obj !== Uninitialized) {
            return obj as T
        }
        initThread.join()
        return getValue(thisRef, property)
    }
}
