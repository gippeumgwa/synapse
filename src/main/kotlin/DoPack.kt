import com.nao20010128nao.CryptorageExtras.indexer.V3Indexer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.internal.closeQuietly
import java.io.DataOutputStream
import java.io.File
import java.io.Writer
import java.util.concurrent.Executors
import kotlin.system.exitProcess

@Suppress("BlockingMethodInNonBlockingContext")
@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
fun main() = runBlocking<Unit> {
    val request = modes.asSequence().map { it.modeId }.toSet()
    println("Opening")
    val fs = openShardingFilebase("data")
    val sharding = Sharding(fs, pass)
    val allIndexer = V3Indexer(pass)
    val numbers = sharding.allShardingNumbers()
    val pool = Executors.newCachedThreadPool().asCoroutineDispatcher()
    val loaders = mutableListOf<Flow<Pair<Int, V3Indexer>>>()
    for (k in numbers) {
        loaders += flow {
            delay(1L)
            emit(k to sharding.getShard(k))
        }.flowOn(pool)
    }
    var count = 0
    val pending = mutableMapOf<Int, V3Indexer>()
    suspend fun follow() {
        while (true) {
            if (count !in numbers) {
                if (count > numbers.maxOrNull()!!) {
                    break
                }
                count++
                continue
            }
            if (count !in pending) {
                break
            }
            println("Merging shard number $count")
            withContext(Dispatchers.Default) {
                allIndexer.merge(pending[count]!!)
                pending.remove(count)
            }
            count++
        }
    }
    loaders.merge().collect { (k, index) ->
        println("Loaded shard number $k")
        pending[k] = index
        follow()
    }
    follow()
    println("Pre-processing files")
    allIndexer.joinSplits()

    val uploadMutex = Mutex()
    val processJob = mutableListOf<Job>()
    for (mode in request) {
        processJob += launch(pool) {
            println("$mode: Mode: $mode")
            val prefix = modes[mode].prefixName
            val localIndexer = V3Indexer(pass)
            localIndexer.merge(allIndexer)
            println("$mode: Processing files")
            localIndexer.joinSplits()
            localIndexer.filterByMode(mode)
            localIndexer.resolveAltNames(mode)
            println("$mode: Current file count: ${localIndexer.list().size}")
            uploadMutex.withLock {
                openShardingFilebase(prefix).use { fs2 ->
                    println("$mode: Writing index down")
                    val list = localIndexer.list().sorted()
                    val writers = mutableListOf<Writer>()
                    localIndexer.writeTo(fs2)
                    if (debug) {
                        writers += File("/tmp/$prefix.txt").writer()
                    }
                    writers += fs2.put(FILE_LIST_TXT).encrypt(sharding.keys).gzip().asCharSink(UTF_8)
                        .openBufferedStream()
                    val sizes = fs2.put(SIZE_LIST_BIN).encrypt(sharding.keys).gzip().openBufferedStream()
                    MultiTextWriter(writers).use { writer ->
                        DataOutputStream(sizes).use { size ->
                            size.writeInt(list.size)
                            list.forEach {
                                writer.write("$it\n")
                                size.writeUTF(it)
                                size.writeLong(localIndexer.size(it))
                            }
                        }
                    }
                }
            }
        }
    }
    processJob.joinAll()
    pool.closeQuietly()
    exitProcess(0)
}
