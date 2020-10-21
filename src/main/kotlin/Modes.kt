import com.nao20010128nao.CryptorageExtras.indexer.Indexer
import java.net.URL

val twImage = "^twitter-[^.]+\\.(?:jpe?g|png)$".toRegex()
val twVideo = "^Twitter-[^.]+\\.(?:mp4|webm)$".toRegex()
val anyUtf8Zip = "^(.+)-utf8\\.zip$".toRegex()
val any7z = "^.+\\.(?:7z|zip|rar|iso|(?:t(?:ar\\.)?)?[gx]z)$".toRegex()
val anyApk = "^.+\\.(?:apks?|systag)$".toRegex()
val anySonshi = anyPrefixedBy("sonshi")
val anyNonH = anyPrefixedBy("nonh")
val anyKerasu = anyPrefixedBy("kerasu")
val anyTachibana = anyPrefixedBy("tachibana")
val anyYamaichi = anyPrefixedBy("yamaichi")
val anyGameplay = anyPrefixedBy("gameplay")
val anySide = anyPrefixedBy("side")
val anyMorizono = anyPrefixedBy("morizono")
val anyEhRawGallery = "^(.+) \\[\\d+]\\.(zip|7z)$".toRegex()
val anyExcel = "^.+\\.(?:xlsx?|csv)$".toRegex()
val anyIxy = anyPrefixedBy("ixy")
val anyPdf = "^.+\\.pdf$".toRegex()
val miscImages = "^(?!twitter-).+\\.(png|gif|jpe?g)$".toRegex()

val modes = listOf(
    NoneMode, DataMode,
    object : PlainMode(2, "twimg", twImage) {},
    object : PlainMode(3, "7z", any7z) {},
    object : PlainMode(4, "twvid", twVideo) {},
    object : PlainMode(5, "apk", anyApk, dedicatedPathname = "AppBackup/", shouldEncode = false) {},
    object : PlainMode(6, "sonshi", anySonshi, "sonshi/", shouldEncode = false) {},
    object : PlainMode(7, "nonh", anyNonH, "nonh/") {},
    object : PlainMode(8, "kerasu", anyKerasu, "kerasu/", shouldEncode = false) {},
    object : PlainMode(9, "tachibana", anyTachibana, "tachibana/", shouldEncode = false) {},
    object : PlainMode(10, "yamaichi", anyYamaichi, "yamaichi/", shouldEncode = false) {},
    object : PlainMode(11, "gameplay", anyGameplay, "gameplay/", shouldEncode = false) {},
    object : PlainMode(12, "excel", anyExcel, shouldEncode = false) {},
    object : PlainMode(13, "images", miscImages, shouldEncode = false) {},
    object : PlainMode(14, "side", anySide, "side/") {},
    object : PlainMode(15, "morizono", anyMorizono, "morizono/", shouldEncode = false) {},
    object : PlainMode(16, "ixy", anyIxy, "ixy/", shouldEncode = false) {},
    object : PlainMode(17, "pdf", anyPdf, shouldEncode = false) {},
)

fun <T : Indexer<T>> Indexer<T>.filterByMode(mode: Int) {
    val seq = list().asSequence()
    getModeObject(mode)
        .findUnsuitable(seq)
        .forEach(this::delete)
}

val prefixToMode = modes.asSequence()
    .filter { it.isValid }
    .associateBy({ it.prefixName }, { it.modeId })

fun getFilenamePrefix(mode: Int): String? = getModeObject(mode).fileNamePrefix

fun getFilenamePrefix(prefix: String): String? = getFilenamePrefix(
    prefixToMode[prefix]
        ?: 0
)

fun getModeObject(mode: Int): FrtMode = modes.getOrElse(mode) { NoneMode }

fun <T : Indexer<T>> Indexer<T>.resolveAltNames(mode: Int) {
    // Step1: twitter-2ndLesmi-utf8.zip -> twitter-2ndLesmi.zip
    list().asSequence()
        .mapNotNull { anyUtf8Zip.matchEntire(it) }
        .map { it.groupValues }
        .forEach { (source, fileName) ->
            val newName = "$fileName.zip"
            mv(source, newName)
        }
    // Step2: read correspond.json
    try {
        val correspond =
            URL("https://gistcdn.githack.com/nao20010128nao/4873ce052dea62930020bdacf4d972cc/raw/correspond.json")
                .readText(UTF_8).parseJson().mapValues { "${it.value}" }
        correspond.forEach { (k, v) ->
            if (has(k))
                copy(k, v)
        }
    } catch (e: Throwable) {
        println("correspond.json failed")
        e.printStackTrace()
    }
    // Step3: remove prefixes for specific mode
    getFilenamePrefix(mode)?.also { pf ->
        list().asSequence().filter { it.startsWith(pf) }.forEach {
            mv(it, it.removePrefix(pf))
        }
    }
    // Step4: remove postfixed EH gallery numbers
    list().asSequence()
        .mapNotNull { anyEhRawGallery.matchEntire(it) }
        .map { it.groupValues }
        .forEach { (source, filename, extension) ->
            mv(source, "$filename.$extension")
        }
}

private fun anyPrefixedBy(path: String): Regex = "^${Regex.escape(path)}/(.+)$".toRegex()

interface FrtMode {
    val modeId: Int
    fun findUnsuitable(list: Sequence<String>): Sequence<String>
    val prefixName: String
    val fileNamePrefix: String?
    val dedicatedPathname: String?
    val isValid: Boolean
    val shouldEncode: Boolean
}

object NoneMode : FrtMode {
    override val modeId: Int = 0
    override fun findUnsuitable(list: Sequence<String>): Sequence<String> = emptySequence()
    override val prefixName: String = "data"
    override val fileNamePrefix: String? = null
    override val dedicatedPathname: String? = null
    override val isValid: Boolean = false
    override val shouldEncode: Boolean = false

    override fun toString(): String = "$prefixName ($modeId)"
}

object DataMode : FrtMode {
    override val modeId: Int = 1

    override fun findUnsuitable(list: Sequence<String>): Sequence<String> = modes.asSequence()
        .mapNotNull { it as? PlainMode }
        .flatMap { list.filter(it.regex::matches) }
        .distinct()

    override val prefixName: String = "data"
    override val fileNamePrefix: String? = null
    override val dedicatedPathname: String? = null
    override val isValid: Boolean = true
    override val shouldEncode: Boolean = true

    override fun toString(): String = "$prefixName ($modeId)"
}

abstract class PlainMode(
    override val modeId: Int,
    final override val prefixName: String,
    val regex: Regex,
    override val fileNamePrefix: String? = null,
    override val dedicatedPathname: String? = fileNamePrefix,
    override val shouldEncode: Boolean = true
) : FrtMode {
    override fun findUnsuitable(list: Sequence<String>): Sequence<String> = list.filterNot(regex::matches)
    override val isValid: Boolean = true

    override fun toString(): String = "$prefixName ($modeId)"
}
