import com.nao20010128nao.Cryptorage.FileSource
import com.nao20010128nao.Cryptorage.asFileSource
import com.nao20010128nao.Cryptorage.withV3Encryption
import com.nao20010128nao.CryptorageExtras.indexer.V3Indexer
import java.lang.Thread.currentThread
import java.net.URL
import kotlin.concurrent.thread

object RenewSharding {
    @JvmStatic
    fun main(args: Array<String>) {
        openShardingFilebase().use { fs ->
            doRenew(Sharding(password = pass), 0, fs)
        }
    }
}

object IndexSharding {
    @JvmStatic
    fun main(args: Array<String>) {
        openShardingFilebase().use { fs ->
            val sharding = Sharding(fs, pass)
            val begin = maxOf((sharding.allShardingNumbers().maxOrNull() ?: 0) - 2, 0)
            doRenew(sharding, begin, fs)
        }
    }
}

fun doRenew(sharding: Sharding, beginShard: Int, fs: FileSource) {
    val threads: MutableList<Thread> = mutableListOf()
    for (shardNum in generateSequence(beginShard) { it + 1 }) {
        val begin = calculateFirstEntryNum(shardNum)
        try {
            URL(endpoint.rawFileUrlString("$prefix-$begin", "master", "", begin))
                .asFileSource().withV3Encryption(pass)
        } catch (_: Throwable) {
            println("stopped at $shardNum: nothing in shard")
            break
        }
        println("Booting $shardNum")
        threads += thread {
            val indexer = V3Indexer(pass)
            val threadNum = threads.indexOf(currentThread())
            for (num in begin until calculateFirstEntryNum(shardNum + 1)) {
                try {
                    indexer.addIndex(URL(endpoint.rawFileUrlString("$prefix-$num", "master", "", num)))
                } catch (e: Throwable) {
                    println("Error $num ($threadNum)")
                    break
                }
            }
            synchronized(sharding) {
                sharding.setShard(shardNum, indexer)
            }
        }
    }
    threads.forEach {
        it.join()
    }

    //println("Writing sharding")
    //sharding.saveSharding(fs)
}
