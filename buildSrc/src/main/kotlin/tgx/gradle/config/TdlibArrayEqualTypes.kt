package tgx.gradle.config

import tgx.gradle.data.TdlibType

fun tdlibArrayEqualTypes(): Array<TdlibType> = arrayOf(
  TdlibType("Document"),
  TdlibType("Sticker"),
  TdlibType("CallServer"),
  TdlibType("GroupCallRecentSpeaker"),
  TdlibType("LinkPreviewAlbumMedia"),
  TdlibType("ClosedVectorPath"),
  TdlibType("VectorPathCommand"),
  TdlibType("KeyboardButton"),
  TdlibType("Thumbnail"),
  TdlibType("InlineKeyboardButton"),
  TdlibType("Array<KeyboardButton>"),
  TdlibType("Array<InlineKeyboardButton>"),
)