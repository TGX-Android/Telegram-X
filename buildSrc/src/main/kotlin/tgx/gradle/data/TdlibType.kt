package tgx.gradle.data

data class TdlibType(
  val name: String,
  val ignoredFields: Set<String> = emptySet(),
  val isExperimental: Boolean = false,
  val array: ArrayType = ArrayType.NONE
)

enum class ArrayType {
  NONE,
  REGULAR,
  TWO_DIMENSIONAL
}