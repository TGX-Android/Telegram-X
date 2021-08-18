@file:JvmName("JSON")

package me.vkryl.td

import me.vkryl.core.isEmpty
import me.vkryl.core.parseInt
import org.drinkless.td.libcore.telegram.Client
import org.drinkless.td.libcore.telegram.TdApi.*

fun parse (json: String?): JsonValue? {
  if (isEmpty(json)) {
    return null
  }
  val result = Client.execute(GetJsonValue(json))
  return if (result is JsonValue) {
    result
  } else {
    null
  }
}

fun toObject (members: List<JsonObjectMember>): JsonValueObject = JsonValueObject(members.toTypedArray())

fun asString (json: JsonValue?): String? = if (json is JsonValueString) json.value else null
fun asInt (json: JsonValue?): Int {
  return when (json) {
    is JsonValueNumber -> json.value.toInt()
    is JsonValueString -> parseInt(json.value)
    else -> 0
  }
}
fun asMap (json: JsonValue?): Map<String, JsonValue>? {
  if (json !is JsonValueObject)
    return null
  val map = HashMap<String, JsonValue>(json.members.size)
  for (member in json.members) {
    map[member.key] = member.value
  }
  return map
}

fun stringify (obj: JsonValue): String? {
  val result = Client.execute(GetJsonString(obj));
  return if (result is Text) {
    result.text
  } else {
    null
  }
}
fun stringify (members: List<JsonObjectMember>): String? = stringify(toObject(members))