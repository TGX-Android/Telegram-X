package tgx.gradle.data

import java.net.URI
import java.util.*

data class GitInformation(
  val commitHashShort: String,
  val commitHashLong: String,
  val commitDate: Long,
  val remoteUrl: String,
  val commitAuthor: String
) {
  init {
    if (URI.create(remoteUrl).host != "github.com") {
      // This requirement is only for commitUrl
      error("Unfortunately, currently you must host your code on GitHub")
    }
  }
  val commitUrl: String =
    String.format(Locale.ENGLISH, $$"%1$s/tree/%3$s", remoteUrl, commitHashShort, commitHashLong)
}