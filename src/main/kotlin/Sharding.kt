import com.beust.klaxon.json
import com.nao20010128nao.Cryptorage.AesKeys
import com.nao20010128nao.Cryptorage.FileSource
import com.nao20010128nao.CryptorageExtras.indexer.V3Indexer
import java.util.*

class Sharding(private val defaultFs: FileSource = EmptyCryptorage, val keys: AesKeys) {
    private val shards: MutableMap<Int, V3Indexer>

    constructor(fs: FileSource = EmptyCryptorage, password: String) : this(fs, populateKeys(password))

    init {
        this.shards = if (defaultFs.has(SHARD_LEADER)) {
            val numbers = defaultFs.open(SHARD_LEADER).decrypt(keys).openStream().reader()
                .parseJson()
                .array<Int>("shards")!!
            val deferredMap = TreeMap<Int, D<V3Indexer>>()
            numbers.forEach { num ->
                deferredMap[num] = { getShardFor(num) }
            }
            DeferredMap(deferredMap, true)
        } else {
            TreeMap<Int, V3Indexer>()
        }
    }

    private fun getShardFor(num: Int): V3Indexer {
        val indexer = V3Indexer(keys)
        indexer.addIndexFiles(indexer.readIndex(defaultFs, "$SHARD_FILE$num"))
        return indexer
    }

    fun saveSharding(fs: FileSource = defaultFs) {
        writeInternal(fs, shards.keys, true)
    }

    private fun writeInternal(fs: FileSource, groups: Collection<Int>, writeLeader: Boolean) {
        groups.forEach { num ->
            val indexer = shards[num]!!
            indexer.serialize().openStream().use { inStrm ->
                fs.put("$SHARD_FILE$num").writeFrom(inStrm)
            }
        }
        if (writeLeader) {
            val json = json {
                obj("shards" to shards.keys.toList())
            }.toJsonString().utf8Bytes().encrypt(keys)
            fs.put(SHARD_LEADER).write(json)
        }
    }

    fun setShard(num: Int, indexer: V3Indexer) {
        val writeLeader = num !in shards
        shards[num] = indexer
        writeInternal(defaultFs, listOf(num), writeLeader)
    }

    fun getShard(num: Int): V3Indexer = shards[num]!!

    fun allShardingNumbers(): Set<Int> = shards.keys.toSet()

    companion object {
        const val SHARD_FILE = "manifest_index_shard_"
        const val SHARD_LEADER = "manifest_index_shard_leader"
    }
}

