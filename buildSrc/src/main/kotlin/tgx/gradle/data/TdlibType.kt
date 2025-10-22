package tgx.gradle.data

data class TdlibType(
  val name: String,
  val ignoredFields: Set<String> = emptySet(),
  val isExperimental: Boolean = false
)