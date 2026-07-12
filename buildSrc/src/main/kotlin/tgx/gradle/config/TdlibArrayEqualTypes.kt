package tgx.gradle.config

import tgx.gradle.data.TdlibType

fun tdlibArrayEqualTypes(): Array<TdlibType> = arrayOf(
  TdlibType("Document"),
  TdlibType("Sticker"),
  TdlibType("CallServer"),
  TdlibType("GroupCallRecentSpeaker"),
  TdlibType("MessageSender"),
  TdlibType("GroupCallVideoSourceGroup"),
  TdlibType("LinkPreviewAlbumMedia"),
  TdlibType("ClosedVectorPath"),
  TdlibType("VectorPathCommand"),
  TdlibType("KeyboardButton"),
  TdlibType("Array<KeyboardButton>"),
  TdlibType("Thumbnail"),
  TdlibType("InlineKeyboardButton"),
  TdlibType("Array<InlineKeyboardButton>"),
  TdlibType("RichText"),
  TdlibType("PageBlock"),
  TdlibType("PageBlockListItem"),
  TdlibType("PageBlockRelatedArticle"),
  TdlibType("PageBlockTableCell"),
  TdlibType("Array<PageBlockTableCell>"),
)