interface EndpointDirection {
    fun rawFileUrlString(repo: String, ref: String, path: String, num: Int): String
    fun gitRemote(repo: String, num: Int): String
}

abstract class EDCommon : EndpointDirection {
    // endpoint, repo, ref, path
    val rawFileFormatString: String = "%s/%s/raw/%s/%s"
    override fun rawFileUrlString(repo: String, ref: String, path: String, num: Int): String =
        rawFileFormatString.format(endpoint(num), repo, ref, path).removeSuffix("/")

    // endpoint, repo
    val gitRemoteFormatString: String = "%s/%s.git"
    override fun gitRemote(repo: String, num: Int): String = gitRemoteFormatString.format(endpoint(num), repo)

    abstract fun endpoint(num: Int): String
}

object GL : EDCommon() {
    override fun endpoint(num: Int): String = "https://gitlab.com/${gitlabUsers[(num - 1) / 400]}"
}

object GH : EDCommon() {
    override fun endpoint(num: Int): String = "https://github.com/${githubUsers[(num - 1) / 500]}"
}

object BB : EDCommon() {
    override fun endpoint(num: Int): String = "https://bitbucket.org/${bitBucketUsers[(num - 1) / 300]}"
}
