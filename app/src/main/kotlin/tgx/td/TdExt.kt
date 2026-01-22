/*
 * This file is a part of Telegram X
 * Copyright © 2014 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 29/07/2024
 */
@file:JvmName("TdExt")

package tgx.td

import androidx.collection.LongSparseArray
import androidx.collection.set
import org.drinkless.tdlib.TdApi.*
import org.thunderdog.challegram.R
import org.thunderdog.challegram.core.Lang
import org.thunderdog.challegram.data.TD
import org.thunderdog.challegram.data.TGWebPage
import org.thunderdog.challegram.tool.Strings
import tgx.td.data.MessageWithProperties
import kotlin.contracts.ExperimentalContracts

// Use this file only for temporary methods that depend on org.thunderdog.challegram.*
// Or some legacy methods that come from the legacy TD.java file

enum class MediaType {
  PHOTOS, VIDEOS, MIXED;
  companion object {
    @JvmStatic
    fun valueOf (ordinal: Int): MediaType = when (ordinal) {
      PHOTOS.ordinal -> PHOTOS
      VIDEOS.ordinal -> VIDEOS
      MIXED.ordinal -> MIXED
      else -> error("$ordinal")
    }

    @JvmStatic
    fun valueOf (paidMedia: MessagePaidMedia): MediaType {
      var photoCount = 0
      var videoCount = 0
      var unknownCount = 0
      for (media in paidMedia.media) {
        when (media.constructor) {
          PaidMediaPhoto.CONSTRUCTOR -> photoCount++
          PaidMediaVideo.CONSTRUCTOR -> videoCount++
          PaidMediaPreview.CONSTRUCTOR -> {
            require(media is PaidMediaPreview)
            if (media.duration > 0) {
              videoCount++
            } else {
              unknownCount++
            }
          }
          PaidMediaUnsupported.CONSTRUCTOR -> unknownCount++
          else -> {
            assertPaidMedia_a2956719()
            throw unsupported(media)
          }
        }
      }
      return when {
        unknownCount > 0 -> MIXED
        photoCount > 0 && videoCount == 0 -> PHOTOS
        videoCount > 0 && photoCount == 0 -> VIDEOS
        else -> MIXED
      }
    }
  }
}

fun Sticker.isPhotoSticker (): Boolean {
  return !format.isAnimated() && maxOf(width, height) > TGWebPage.STICKER_SIZE_LIMIT
}

@OptIn(ExperimentalContracts::class)
fun LinkPreview?.getRepresentationTitle (): String {
  return this?.let {
    if (it.description.isEmpty()) {
      Strings.any(it.siteName, it.title)
    } else {
      Strings.any(it.title, it.siteName)
    }
  }?.takeIf {
    it.isNotEmpty()
  } ?: this?.type?.let {
    when (it.constructor) {
      LinkPreviewTypePhoto.CONSTRUCTOR -> {
        Lang.getString(R.string.ChatContentPhoto)
      }
      LinkPreviewTypeAnimation.CONSTRUCTOR,
      LinkPreviewTypeEmbeddedAnimationPlayer.CONSTRUCTOR -> {
        Lang.getString(R.string.ChatContentAnimation)
      }
      LinkPreviewTypeSticker.CONSTRUCTOR -> {
        require(it is LinkPreviewTypeSticker)
        if (it.sticker.isPhotoSticker()) {
          Lang.getString(R.string.Photo)
        } else {
          Lang.getString(R.string.Sticker)
        }
      }
      LinkPreviewTypeVideo.CONSTRUCTOR,
      LinkPreviewTypeEmbeddedVideoPlayer.CONSTRUCTOR -> {
        Lang.getString(R.string.Video)
      }
      LinkPreviewTypeDocument.CONSTRUCTOR -> {
        require(it is LinkPreviewTypeDocument)
        it.document.fileName.takeIf { fileName ->
          fileName.isNotBlank()
        } ?: Lang.getString(R.string.File)
      }
      LinkPreviewTypeAudio.CONSTRUCTOR -> {
        require(it is LinkPreviewTypeAudio)
        TD.getTitle(it.audio) + " – " + TD.getSubtitle(it.audio)
      }
      LinkPreviewTypeAlbum.CONSTRUCTOR,
      LinkPreviewTypeApp.CONSTRUCTOR,
      LinkPreviewTypeArticle.CONSTRUCTOR,
      LinkPreviewTypeBackground.CONSTRUCTOR,
      LinkPreviewTypeChannelBoost.CONSTRUCTOR,
      LinkPreviewTypeChat.CONSTRUCTOR,
      LinkPreviewTypeDirectMessagesChat.CONSTRUCTOR,
      LinkPreviewTypeEmbeddedAudioPlayer.CONSTRUCTOR,
      LinkPreviewTypeInvoice.CONSTRUCTOR,
      LinkPreviewTypeMessage.CONSTRUCTOR,
      LinkPreviewTypePremiumGiftCode.CONSTRUCTOR,
      LinkPreviewTypeShareableChatFolder.CONSTRUCTOR,
      LinkPreviewTypeStickerSet.CONSTRUCTOR,
      LinkPreviewTypeStory.CONSTRUCTOR,
      LinkPreviewTypeStoryAlbum.CONSTRUCTOR,
      LinkPreviewTypeSupergroupBoost.CONSTRUCTOR,
      LinkPreviewTypeTheme.CONSTRUCTOR,
      LinkPreviewTypeUnsupported.CONSTRUCTOR,
      LinkPreviewTypeUpgradedGift.CONSTRUCTOR,
      LinkPreviewTypeGiftCollection.CONSTRUCTOR,
      LinkPreviewTypeUser.CONSTRUCTOR,
      LinkPreviewTypeGroupCall.CONSTRUCTOR,
      LinkPreviewTypeVideoChat.CONSTRUCTOR,
      LinkPreviewTypeVideoNote.CONSTRUCTOR,
      LinkPreviewTypeVoiceNote.CONSTRUCTOR,
      LinkPreviewTypeWebApp.CONSTRUCTOR,
      LinkPreviewTypeGiftAuction.CONSTRUCTOR,
      LinkPreviewTypeLiveStory.CONSTRUCTOR,
      LinkPreviewTypeExternalAudio.CONSTRUCTOR,
      LinkPreviewTypeExternalVideo.CONSTRUCTOR -> {
        null
      }
      else -> {
        assertLinkPreviewType_a9a3ffcd()
        throw unsupported(it)
      }
    }
  } ?: Lang.getString(R.string.LinkPreview)
}

fun LinkPreview?.getContentTitle (): String {
  return if (this != null) {
    arrayOf(
      this.title,
      this.type.let {
        when (it.constructor) {
          LinkPreviewTypeDocument.CONSTRUCTOR -> {
            require(it is LinkPreviewTypeDocument)
            it.document.fileName
          }
          LinkPreviewTypeAudio.CONSTRUCTOR -> {
            require(it is LinkPreviewTypeAudio)
            it.audio?.title
          }
          else -> {
            assertLinkPreviewType_a9a3ffcd()
            null
          }
        }
      },
      this.siteName
    ).firstOrNull {
      !it.isNullOrBlank()
    } ?: ""
  } else {
    ""
  }
}

fun LinkPreview?.getMediaFile (): File? = this?.type.getMediaFile()

fun LinkPreviewType?.getMediaFile (): File? {
  if (this == null) return null
  return when (this.constructor) {
    LinkPreviewTypePhoto.CONSTRUCTOR -> {
      require(this is LinkPreviewTypePhoto)
      TD.getFile(this.photo)
    }
    LinkPreviewTypeAnimation.CONSTRUCTOR -> {
      require(this is LinkPreviewTypeAnimation)
      this.animation.animation
    }
    LinkPreviewTypeApp.CONSTRUCTOR -> {
      require(this is LinkPreviewTypeApp)
      TD.getFile(this.photo)
    }
    LinkPreviewTypeArticle.CONSTRUCTOR -> {
      require(this is LinkPreviewTypeArticle)
      TD.getFile(this.photo)
    }
    LinkPreviewTypeAudio.CONSTRUCTOR -> {
      require(this is LinkPreviewTypeAudio)
      this.audio?.audio
    }
    LinkPreviewTypeChannelBoost.CONSTRUCTOR -> {
      require(this is LinkPreviewTypeChannelBoost)
      this.photo?.sizes?.findBiggest()?.photo
    }
    LinkPreviewTypeChat.CONSTRUCTOR -> {
      require(this is LinkPreviewTypeChat)
      this.photo?.sizes?.findBiggest()?.photo
    }
    LinkPreviewTypeDirectMessagesChat.CONSTRUCTOR -> {
      require(this is LinkPreviewTypeDirectMessagesChat)
      this.photo?.sizes?.findBiggest()?.photo
    }
    LinkPreviewTypeDocument.CONSTRUCTOR -> {
      require(this is LinkPreviewTypeDocument)
      this.document.document
    }
    LinkPreviewTypeSticker.CONSTRUCTOR -> {
      require(this is LinkPreviewTypeSticker)
      this.sticker.sticker
    }
    LinkPreviewTypeStickerSet.CONSTRUCTOR -> {
      require(this is LinkPreviewTypeStickerSet)
      this.stickers[0].sticker
    }
    LinkPreviewTypeStory.CONSTRUCTOR -> {
      require(this is LinkPreviewTypeStory)
      null
    }
    LinkPreviewTypeStoryAlbum.CONSTRUCTOR -> {
      require(this is LinkPreviewTypeStoryAlbum)
      null
    }
    LinkPreviewTypeSupergroupBoost.CONSTRUCTOR -> {
      require(this is LinkPreviewTypeSupergroupBoost)
      this.photo?.sizes?.findBiggest()?.photo
    }
    LinkPreviewTypeUser.CONSTRUCTOR -> {
      require(this is LinkPreviewTypeUser)
      this.photo?.sizes?.findBiggest()?.photo
    }
    LinkPreviewTypeVideo.CONSTRUCTOR -> {
      require(this is LinkPreviewTypeVideo)
      this.video?.video
    }
    LinkPreviewTypeVideoNote.CONSTRUCTOR -> {
      require(this is LinkPreviewTypeVideoNote)
      this.videoNote.video
    }
    LinkPreviewTypeVoiceNote.CONSTRUCTOR -> {
      require(this is LinkPreviewTypeVoiceNote)
      this.voiceNote.voice
    }
    // No file returned
    LinkPreviewTypeAlbum.CONSTRUCTOR -> {
      require(this is LinkPreviewTypeAlbum)
      // TODO: pick at least one item?
      null
    }
    LinkPreviewTypeBackground.CONSTRUCTOR,
    LinkPreviewTypeEmbeddedAnimationPlayer.CONSTRUCTOR,
    LinkPreviewTypeEmbeddedAudioPlayer.CONSTRUCTOR,
    LinkPreviewTypeEmbeddedVideoPlayer.CONSTRUCTOR,
    LinkPreviewTypeMessage.CONSTRUCTOR,
    LinkPreviewTypeInvoice.CONSTRUCTOR,
    LinkPreviewTypePremiumGiftCode.CONSTRUCTOR,
    LinkPreviewTypeShareableChatFolder.CONSTRUCTOR,
    LinkPreviewTypeExternalAudio.CONSTRUCTOR,
    LinkPreviewTypeExternalVideo.CONSTRUCTOR,
    LinkPreviewTypeTheme.CONSTRUCTOR,
    LinkPreviewTypeGroupCall.CONSTRUCTOR,
    LinkPreviewTypeVideoChat.CONSTRUCTOR,
    LinkPreviewTypeWebApp.CONSTRUCTOR,
    LinkPreviewTypeUpgradedGift.CONSTRUCTOR,
    LinkPreviewTypeGiftCollection.CONSTRUCTOR,
    LinkPreviewTypeGiftAuction.CONSTRUCTOR,
    LinkPreviewTypeLiveStory.CONSTRUCTOR,
    LinkPreviewTypeUnsupported.CONSTRUCTOR -> null
    else -> {
      assertLinkPreviewType_a9a3ffcd()
      throw unsupported(this)
    }
  }
}

fun Array<MessageWithProperties>?.findUniqueChatId (): Long {
  if (!this.isNullOrEmpty()) {
    val chatId = this[0].message.chatId
    for (message in this) {
      if (message.message.chatId != chatId) {
        return 0
      }
    }
    return chatId
  }
  return 0
}

fun Array<MessageWithProperties>?.findUniqueSenderId (): MessageSender? {
  if (!this.isNullOrEmpty()) {
    val senderId = this[0].message.senderId
    for (message in this) {
      if (!message.message.senderId.equalsTo(senderId)) {
        return null
      }
    }
    return senderId
  }
  return null
}

fun Array<MessageWithProperties>?.toMessageIdsMap (): LongSparseArray<LongArray> {
  if (this.isNullOrEmpty()) {
    return LongSparseArray()
  }
  val result = LongSparseArray<LongArray>()
  var chatId = 0L
  var list = mutableListOf<Long>()

  for (message in this) {
    if (message.message.chatId != chatId || list.isEmpty()) {
      if (list.isNotEmpty()) {
        result[chatId] = list.toLongArray()
      }
      chatId = message.message.chatId
      list = mutableListOf()
    }
    list.add(message.message.id)
  }
  if (list.isNotEmpty()) {
    result[chatId] = list.toLongArray()
  }

  return result
}

fun ReactionType?.isUnsupported (): Boolean = this?.constructor == ReactionTypePaid.CONSTRUCTOR