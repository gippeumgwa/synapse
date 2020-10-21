import com.nao20010128nao.CryptorageExtras.decodeBase64

val config = (System.getenv("SYNAPSE_CONFIG")?.decodeBase64() ?: "{}").parseJson()

val filebaseBucket: String = config.string("SYNAPSE_BUCKET") ?: ""
val filebaseAccessID: String = config.string("SYNAPSE_ACCESS_ID") ?: ""
val filebaseAccessKey: String = config.string("SYNAPSE_ACCESS_KEY") ?: ""

val pass: String = config.string("SYNAPSE_PASSWORD") ?: ""

val endpoint = when (config.string("SYNAPSE_ENDPOINT")) {
    "gh" -> GH
    "gl" -> GL
    "bb" -> BB
    else -> GH
}

val gitlabUsers: List<String> = config.array("SYNAPSE_GITLAB_USERS") ?: emptyList()
val githubUsers: List<String> = config.array("SYNAPSE_GITHUB_USERS") ?: emptyList()
val bitBucketUsers: List<String> = config.array("SYNAPSE_BITBUCKET_USERS") ?: emptyList()
