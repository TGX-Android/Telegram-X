@file:JvmName("Td")
@file:JvmMultifileClass

package me.vkryl.td

import me.vkryl.core.equalsOrBothEmpty
import org.drinkless.td.libcore.telegram.TdApi.*
import java.util.*
import kotlin.contracts.ExperimentalContracts

fun ChatList?.equalsTo(b: ChatList?): Boolean {
  return when {
    this === b -> true
    this === null || b === null || this.constructor != b.constructor -> false
    else -> when (this.constructor) {
      ChatListMain.CONSTRUCTOR, ChatListArchive.CONSTRUCTOR -> true
      ChatListFilter.CONSTRUCTOR -> (this as ChatListFilter).chatFilterId == (b as ChatListFilter).chatFilterId
      else -> error(this.toString())
    }
  }
}

fun ChatMemberStatus.equalsTo(b: ChatMemberStatus): Boolean {
  return when {
    this === b -> true
    this.constructor != b.constructor -> false
    else -> when (this.constructor) {
      ChatMemberStatusMember.CONSTRUCTOR,
      ChatMemberStatusLeft.CONSTRUCTOR -> true

      ChatMemberStatusCreator.CONSTRUCTOR -> {
        require(this is ChatMemberStatusCreator && b is ChatMemberStatusCreator)
        this.isMember == b.isMember && this.customTitle.equalsOrBothEmpty(b.customTitle)
      }

      ChatMemberStatusBanned.CONSTRUCTOR -> {
        require(this is ChatMemberStatusBanned && b is ChatMemberStatusBanned)
        this.bannedUntilDate == b.bannedUntilDate
      }

      ChatMemberStatusAdministrator.CONSTRUCTOR -> {
        require(this is ChatMemberStatusAdministrator && b is ChatMemberStatusAdministrator)
        this.canChangeInfo == b.canChangeInfo &&
        this.canPostMessages == b.canPostMessages &&
        this.canEditMessages == b.canEditMessages &&
        this.canDeleteMessages == b.canDeleteMessages &&
        this.canInviteUsers == b.canInviteUsers &&
        this.canRestrictMembers == b.canRestrictMembers &&
        this.canPinMessages == b.canPinMessages &&
        this.canPromoteMembers == b.canPromoteMembers &&
        this.isAnonymous == b.isAnonymous &&
        this.canManageChat == b.canManageChat &&
        this.canManageVideoChats == b.canManageVideoChats &&
        this.customTitle.equalsOrBothEmpty(b.customTitle)
        // ignored: this.canBeEdited == b.canBeEdited
      }

      ChatMemberStatusRestricted.CONSTRUCTOR -> {
        require(this is ChatMemberStatusRestricted && b is ChatMemberStatusRestricted)
        this.isMember == b.isMember &&
          this.restrictedUntilDate == b.restrictedUntilDate &&
          this.permissions.equalsTo(b.permissions)
      }

      else -> error(this.toString())
    }
  }
}

fun ChatPermissions.equalsTo(b: ChatPermissions): Boolean {
  return (this === b) || (
    this.canSendMessages == b.canSendMessages &&
    this.canSendMediaMessages == b.canSendMediaMessages &&
    this.canSendOtherMessages == b.canSendOtherMessages &&
    this.canAddWebPagePreviews == b.canAddWebPagePreviews &&
    this.canSendPolls == b.canSendPolls &&
    this.canInviteUsers == b.canInviteUsers &&
    this.canPinMessages == b.canPinMessages &&
    this.canChangeInfo == b.canChangeInfo
  )
}

fun ChatPermissions.equalsTo(old: ChatPermissions, defaultPermissions: ChatPermissions): Boolean {
  return (this === old) || (
    (this.canSendMessages == old.canSendMessages || !this.canSendMessages && !defaultPermissions.canSendMessages) &&
    (this.canSendMediaMessages == old.canSendMediaMessages || !this.canSendMediaMessages && !defaultPermissions.canSendMediaMessages) &&
    (this.canSendOtherMessages == old.canSendOtherMessages || !this.canSendOtherMessages && !defaultPermissions.canSendOtherMessages) &&
    (this.canAddWebPagePreviews == old.canAddWebPagePreviews || !this.canAddWebPagePreviews && !defaultPermissions.canAddWebPagePreviews) &&
    (this.canSendPolls == old.canSendPolls || !this.canSendPolls && !defaultPermissions.canSendPolls) &&
    (this.canInviteUsers == old.canInviteUsers || !this.canInviteUsers && !defaultPermissions.canInviteUsers) &&
    (this.canPinMessages == old.canPinMessages || !this.canPinMessages && !defaultPermissions.canPinMessages) &&
    (this.canChangeInfo == old.canChangeInfo || !this.canChangeInfo && !defaultPermissions.canChangeInfo)
  )
}

fun ChatActionBar?.equalsTo(b: ChatActionBar?): Boolean {
  return when {
    this === b -> true
    this === null || b === null || this.constructor != b.constructor -> false
    else -> when (this.constructor) {
      ChatActionBarReportSpam.CONSTRUCTOR -> {
        require(this is ChatActionBarReportSpam && b is ChatActionBarReportSpam)
        this.canUnarchive == b.canUnarchive
      }
      ChatActionBarReportAddBlock.CONSTRUCTOR -> {
        require(this is ChatActionBarReportAddBlock && b is ChatActionBarReportAddBlock)
        this.canUnarchive == b.canUnarchive && this.distance == b.distance
      }
      ChatActionBarReportUnrelatedLocation.CONSTRUCTOR,
      ChatActionBarAddContact.CONSTRUCTOR,
      ChatActionBarSharePhoneNumber.CONSTRUCTOR -> true
      else -> error(this.toString())
    }
  }
}


@ExperimentalContracts
@JvmOverloads
fun FormattedText?.equalsTo(b: FormattedText?, ignoreDefaultEntities: Boolean = false): Boolean {
  return when {
    this === b -> true
    this.isEmpty() -> b.isEmpty()
    else -> {
      !b.isEmpty() &&
      this.text.equalsOrBothEmpty(b.text) &&
      this.entities.equalsTo(b.entities, ignoreDefaultEntities)
    }
  }
}

@JvmOverloads
fun Array<TextEntity>?.equalsTo(b: Array<TextEntity>?, ignoreDefaultEntities: Boolean = false): Boolean {
  if (this === b) return true
  if ((this?.size ?: 0) != (b?.size ?: 0)) return false
  if (this != null && b != null) {
    if (ignoreDefaultEntities) {
      return this.findEssential().equalsTo(b.findEssential())
    } else {
      for (i in this.indices) {
        if (!this[i].equalsTo(b[i])) {
          return false
        }
      }
    }
  }
  return true
}

fun TextEntity?.equalsTo(b: TextEntity?): Boolean {
  return (this === b) || (
    this !== null && b !== null &&
    this.offset == b.offset &&
    this.length == b.length && this.type.equalsTo(b.type)
  )
}

fun TextEntityType?.equalsTo(b: TextEntityType?): Boolean {
  return when {
    this === b -> true
    this == null || b == null -> false
    this.constructor != b.constructor -> false
    else -> when (this.constructor) {
      TextEntityTypeMentionName.CONSTRUCTOR -> {
        require(this is TextEntityTypeMentionName && b is TextEntityTypeMentionName)
        this.userId == b.userId
      }
      TextEntityTypeTextUrl.CONSTRUCTOR -> {
        require(this is TextEntityTypeTextUrl && b is TextEntityTypeTextUrl)
        this.url.equalsOrBothEmpty(b.url)
      }
      TextEntityTypePreCode.CONSTRUCTOR -> {
        require(this is TextEntityTypePreCode && b is TextEntityTypePreCode)
        this.language.equalsOrBothEmpty(b.language)
      }
      TextEntityTypeMediaTimestamp.CONSTRUCTOR -> {
        require(this is TextEntityTypeMediaTimestamp && b is TextEntityTypeMediaTimestamp)
        this.mediaTimestamp == b.mediaTimestamp
      }
      TextEntityTypeBankCardNumber.CONSTRUCTOR,
      TextEntityTypeBold.CONSTRUCTOR,
      TextEntityTypeSpoiler.CONSTRUCTOR,
      TextEntityTypeBotCommand.CONSTRUCTOR,
      TextEntityTypeCashtag.CONSTRUCTOR,
      TextEntityTypeCode.CONSTRUCTOR,
      TextEntityTypeEmailAddress.CONSTRUCTOR,
      TextEntityTypeHashtag.CONSTRUCTOR,
      TextEntityTypeItalic.CONSTRUCTOR,
      TextEntityTypeMention.CONSTRUCTOR,
      TextEntityTypePhoneNumber.CONSTRUCTOR,
      TextEntityTypePre.CONSTRUCTOR,
      TextEntityTypeStrikethrough.CONSTRUCTOR,
      TextEntityTypeUnderline.CONSTRUCTOR,
      TextEntityTypeUrl.CONSTRUCTOR -> true
      else -> TODO(this.toString())
    }
  }
}

fun ChatAction.equalsTo(b: ChatAction): Boolean {
  return when {
    this === b -> true
    this.constructor != b.constructor -> false
    else -> when (this.constructor) {
      ChatActionTyping.CONSTRUCTOR,
      ChatActionRecordingVideo.CONSTRUCTOR,
      ChatActionRecordingVoiceNote.CONSTRUCTOR,
      ChatActionChoosingLocation.CONSTRUCTOR,
      ChatActionChoosingContact.CONSTRUCTOR,
      ChatActionChoosingSticker.CONSTRUCTOR,
      ChatActionStartPlayingGame.CONSTRUCTOR,
      ChatActionRecordingVideoNote.CONSTRUCTOR,
      ChatActionCancel.CONSTRUCTOR -> true
      ChatActionUploadingVideo.CONSTRUCTOR -> {
        require(this is ChatActionUploadingVideo && b is ChatActionUploadingVideo)
        this.progress == b.progress
      }
      ChatActionWatchingAnimations.CONSTRUCTOR -> {
        require(this is ChatActionWatchingAnimations && b is ChatActionWatchingAnimations)
        this.emoji == b.emoji
      }
      ChatActionUploadingVoiceNote.CONSTRUCTOR -> {
        require(this is ChatActionUploadingVoiceNote && b is ChatActionUploadingVoiceNote)
        this.progress == b.progress
      }
      ChatActionUploadingPhoto.CONSTRUCTOR -> {
        require(this is ChatActionUploadingPhoto && b is ChatActionUploadingPhoto)
        this.progress == b.progress
      }
      ChatActionUploadingDocument.CONSTRUCTOR -> {
        require(this is ChatActionUploadingDocument && b is ChatActionUploadingDocument)
        this.progress == b.progress
      }
      ChatActionUploadingVideoNote.CONSTRUCTOR -> {
        require(this is ChatActionUploadingVideoNote && b is ChatActionUploadingVideoNote)
        this.progress == b.progress
      }
      else -> TODO(this.toString())
    }
  }
}

fun ChatSource?.equalsTo(b: ChatSource?): Boolean {
  return when {
    this === b -> true
    this === null || b === null || this.constructor != b.constructor -> false
    else -> when (this.constructor) {
      ChatSourceMtprotoProxy.CONSTRUCTOR -> true
      ChatSourcePublicServiceAnnouncement.CONSTRUCTOR -> {
        require(this is ChatSourcePublicServiceAnnouncement && b is ChatSourcePublicServiceAnnouncement)
        this.type.equalsOrBothEmpty(b.type) && this.text.equalsOrBothEmpty(b.text)
      }
      else -> error(this.toString())
    }
  }
}

fun UserStatus?.equalsTo(b: UserStatus?): Boolean {
  return when {
    this === b -> true
    this === null || b === null || this.constructor != b.constructor -> false
    else -> when (this.constructor) {
      UserStatusEmpty.CONSTRUCTOR,
      UserStatusLastMonth.CONSTRUCTOR,
      UserStatusLastWeek.CONSTRUCTOR,
      UserStatusRecently.CONSTRUCTOR -> true
      UserStatusOnline.CONSTRUCTOR -> {
        require(this is UserStatusOnline && b is UserStatusOnline)
        this.expires == b.expires
      }
      UserStatusOffline.CONSTRUCTOR -> {
        require(this is UserStatusOffline && b is UserStatusOffline)
        this.wasOnline == b.wasOnline
      }
      else -> error(this.toString())
    }
  }
}

@JvmOverloads fun File?.equalsTo(b: File?, onlyIdentifier: Boolean = true): Boolean {
  return when {
    this === b -> true
    this === null || b === null -> false
    onlyIdentifier -> this.id == b.id
    else -> {
      this.id == b.id &&
      this.size == b.size &&
      this.expectedSize == b.expectedSize &&
      this.local.equalsTo(b.local) &&
      this.remote.equalsTo(b.remote)
    }
  }
}

fun LocalFile?.equalsTo(b: LocalFile?): Boolean {
  return when {
    this === b -> true
    this === null || b === null -> false
    else -> {
      this.downloadedSize == b.downloadedSize &&
      this.downloadOffset == b.downloadOffset &&
      this.downloadedPrefixSize == b.downloadedPrefixSize &&
      this.isDownloadingActive == b.isDownloadingActive &&
      this.isDownloadingCompleted == b.isDownloadingCompleted &&
      this.canBeDeleted == b.canBeDeleted &&
      this.canBeDownloaded == b.canBeDownloaded &&
      this.path.equalsOrBothEmpty(b.path)
    }
  }
}

fun RemoteFile?.equalsTo(b: RemoteFile?): Boolean {
  return when {
    this === b -> true
    this === null || b === null -> false
    else -> {
      this.uploadedSize == b.uploadedSize &&
      this.isUploadingActive == b.isUploadingActive &&
      this.isUploadingCompleted == b.isUploadingCompleted &&
      this.id.equalsOrBothEmpty(b.id) &&
      this.uniqueId.equalsOrBothEmpty(b.uniqueId)
    }
  }
}

fun ChatEventLogFilters?.equalsTo(b: ChatEventLogFilters?): Boolean {
  return (
    (this?.messageEdits ?: false) == (b?.messageEdits ?: false) &&
    (this?.messageDeletions ?: false) == (b?.messageDeletions ?: false) &&
    (this?.messagePins ?: false) == (b?.messagePins ?: false) &&
    (this?.memberJoins ?: false) == (b?.memberJoins ?: false) &&
    (this?.memberLeaves ?: false) == (b?.memberLeaves ?: false) &&
    (this?.memberInvites ?: false) == (b?.memberInvites ?: false) &&
    (this?.memberPromotions ?: false) == (b?.memberPromotions ?: false) &&
    (this?.memberRestrictions ?: false) == (b?.memberRestrictions ?: false) &&
    (this?.infoChanges ?: false) == (b?.infoChanges ?: false) &&
    (this?.settingChanges ?: false) == (b?.settingChanges ?: false) &&
    (this?.inviteLinkChanges ?: false) == (b?.inviteLinkChanges ?: false) &&
    (this?.videoChatChanges ?: false) == (b?.videoChatChanges ?: false)
  )
}

fun UserPrivacySettingRule.equalsTo(b: UserPrivacySettingRule): Boolean {
  return when {
    this === b -> true
    this.constructor != b.constructor -> false
    else -> when (this.constructor) {
      UserPrivacySettingRuleRestrictChatMembers.CONSTRUCTOR -> {
        require(this is UserPrivacySettingRuleRestrictChatMembers && b is UserPrivacySettingRuleRestrictChatMembers)
        Arrays.equals(this.chatIds, b.chatIds)
      }
      UserPrivacySettingRuleAllowChatMembers.CONSTRUCTOR -> {
        require(this is UserPrivacySettingRuleAllowChatMembers && b is UserPrivacySettingRuleAllowChatMembers)
        Arrays.equals(this.chatIds, b.chatIds)
      }
      UserPrivacySettingRuleAllowUsers.CONSTRUCTOR -> {
        require(this is UserPrivacySettingRuleAllowUsers && b is UserPrivacySettingRuleAllowUsers)
        Arrays.equals(this.userIds, b.userIds)
      }
      UserPrivacySettingRuleRestrictUsers.CONSTRUCTOR -> {
        require(this is UserPrivacySettingRuleRestrictUsers && b is UserPrivacySettingRuleRestrictUsers)
        Arrays.equals(this.userIds, b.userIds)
      }
      UserPrivacySettingRuleAllowAll.CONSTRUCTOR,
      UserPrivacySettingRuleAllowContacts.CONSTRUCTOR,
      UserPrivacySettingRuleRestrictAll.CONSTRUCTOR,
      UserPrivacySettingRuleRestrictContacts.CONSTRUCTOR -> true
      else -> error(this.toString())
    }
  }
}

fun InlineKeyboardButton.equalsTo(b: InlineKeyboardButton): Boolean {
  return (this === b) || (this.text == b.text && this.type.equalsTo(b.type));
}

fun InlineKeyboardButtonType.equalsTo(b: InlineKeyboardButtonType): Boolean {
  return when {
    this === b -> true
    this.constructor != b.constructor -> false
    else -> when (this.constructor) {
      InlineKeyboardButtonTypeUrl.CONSTRUCTOR -> {
        require(this is InlineKeyboardButtonTypeUrl && b is InlineKeyboardButtonTypeUrl)
        this.url == b.url
      }
      InlineKeyboardButtonTypeLoginUrl.CONSTRUCTOR -> {
        require(this is InlineKeyboardButtonTypeLoginUrl && b is InlineKeyboardButtonTypeLoginUrl)
        this.id == b.id && this.url == b.url && this.forwardText == b.forwardText
      }
      InlineKeyboardButtonTypeCallback.CONSTRUCTOR -> {
        require(this is InlineKeyboardButtonTypeCallback && b is InlineKeyboardButtonTypeCallback)
        Arrays.equals(this.data, b.data)
      }
      InlineKeyboardButtonTypeCallbackWithPassword.CONSTRUCTOR -> {
        require(this is InlineKeyboardButtonTypeCallbackWithPassword && b is InlineKeyboardButtonTypeCallbackWithPassword)
        Arrays.equals(this.data, b.data)
      }
      InlineKeyboardButtonTypeSwitchInline.CONSTRUCTOR -> {
        require(this is InlineKeyboardButtonTypeSwitchInline && b is InlineKeyboardButtonTypeSwitchInline)
        this.inCurrentChat == b.inCurrentChat && this.query == b.query
      }
      InlineKeyboardButtonTypeCallbackGame.CONSTRUCTOR,
      InlineKeyboardButtonTypeBuy.CONSTRUCTOR -> true
      else -> error(this.toString())
    }
  }
}

fun KeyboardButton.equalsTo(b: KeyboardButton): Boolean {
  return (this === b) || (this.text == b.text && this.type.equalsTo(b.type))
}

fun KeyboardButtonType.equalsTo(b: KeyboardButtonType): Boolean {
  return when {
    this === b -> true
    this.constructor != b.constructor -> false
    else -> when (this.constructor) {
      KeyboardButtonTypeRequestPoll.CONSTRUCTOR -> {
        require(this is KeyboardButtonTypeRequestPoll && b is KeyboardButtonTypeRequestPoll)
        this.forceQuiz == b.forceQuiz && this.forceRegular == b.forceRegular
      }
      KeyboardButtonTypeText.CONSTRUCTOR,
      KeyboardButtonTypeRequestPhoneNumber.CONSTRUCTOR,
      KeyboardButtonTypeRequestLocation.CONSTRUCTOR -> true
      else -> error(this.toString())
    }
  }
}

fun ReplyMarkup?.equalsTo(b: ReplyMarkup?): Boolean {
  return when {
    this === b -> true
    this == null || b == null -> false
    this.constructor != b.constructor -> false
    else -> when (this.constructor) {
      ReplyMarkupRemoveKeyboard.CONSTRUCTOR -> {
        require(this is ReplyMarkupRemoveKeyboard && b is ReplyMarkupRemoveKeyboard)
        this.isPersonal == b.isPersonal
      }
      ReplyMarkupForceReply.CONSTRUCTOR -> {
        require(this is ReplyMarkupForceReply && b is ReplyMarkupForceReply)
        this.isPersonal == b.isPersonal
      }
      ReplyMarkupShowKeyboard.CONSTRUCTOR -> {
        require(this is ReplyMarkupShowKeyboard && b is ReplyMarkupShowKeyboard)
        if (this.isPersonal != b.isPersonal ||
          this.oneTime != b.oneTime ||
          this.resizeKeyboard != b.resizeKeyboard ||
          (this.rows?.size ?: 0) != (b.rows?.size)) {
          return false
        }
        this.rows?.let {
          for (i in this.rows.indices) {
            if ((this.rows[i]?.size ?: 0) != (b.rows[i]?.size ?: 0)) {
              return false
            }
            this.rows[i]?.let {
              for (j in this.rows[i].indices) {
                if (!this.rows[i][j].equalsTo(b.rows[i][j])) {
                  return false
                }
              }
            }
          }
        }
        true
      }
      ReplyMarkupInlineKeyboard.CONSTRUCTOR -> {
        require(this is ReplyMarkupInlineKeyboard && b is ReplyMarkupInlineKeyboard)
        if ((this.rows?.size ?: 0) != (b.rows?.size ?: 0)) {
          return false
        }
        this.rows?.let {
          for (i in this.rows.indices) {
            if ((this.rows[i]?.size ?: 0) != (b.rows[i]?.size ?: 0)) {
              return false
            }
            this.rows[i]?.let {
              for (j in this.rows[i].indices) {
                if (!this.rows[i][j].equalsTo(b.rows[i][j])) {
                  return false
                }
              }
            }
          }
        }
        true
      }
      else -> error(this.toString())
    }
  }
}

fun PhotoSize?.equalsTo(b: PhotoSize?): Boolean {
  return when {
    this === b -> true
    this == null || b == null -> false
    else -> {
      this.width == b.width &&
      this.height == b.height &&
      this.type == b.type &&
      this.photo.equalsTo(b.photo)
    }
  }
}

@JvmOverloads fun Minithumbnail?.equalsTo(b: Minithumbnail?, checkJpegBytes: Boolean = false): Boolean {
  return when {
    this === b -> true
    this == null || b == null -> false
    else -> {
      this.width == b.width &&
      this.height == b.height &&
      (!checkJpegBytes || Arrays.equals(this.data, b.data))
    }
  }
}

fun Thumbnail?.equalsTo(b: Thumbnail?): Boolean {
  return when {
    this === b -> true
    this == null || b == null -> false
    else -> {
      this.width == b.width &&
      this.height == b.height &&
      this.file.equalsTo(b.file) &&
      this.format.equalsTo(b.format)
    }
  }
}

fun ThumbnailFormat.equalsTo(b: ThumbnailFormat): Boolean {
  return this.constructor == b.constructor
}

fun Array<PhotoSize>?.equalsTo(b: Array<PhotoSize>?): Boolean {
  return when {
    this === b -> true
    this.isNullOrEmpty() || b.isNullOrEmpty() -> this.isNullOrEmpty() == b.isNullOrEmpty()
    else -> {
      val minArray = if (this.size <= b.size) this else b
      val maxArray = if (this.size <= b.size) b else this
      val map = HashMap<String, PhotoSize>(minArray.size)
      for (size in minArray) {
        map[size.type] = size
      }
      for (size in maxArray) {
        val other = map[size.type]
        if (other != null && !other.equalsTo(size)) {
          return false
        }
      }
      true
    }
  }
}

@JvmOverloads fun VoiceNote?.equalsTo(b: VoiceNote?, checkWaveformBytes: Boolean = false): Boolean {
  return when {
    this === b -> true
    this == null || b == null -> false
    else -> {
      this.duration == b.duration &&
      this.mimeType.equalsOrBothEmpty(b.mimeType) &&
      this.voice.equalsTo(b.voice) &&
      if (checkWaveformBytes) Arrays.equals(this.waveform, b.waveform) else (this.waveform?.size ?: 0) == (b.waveform?.size ?: 0)
    }
  }
}

fun VideoNote?.equalsTo(b: VideoNote?): Boolean {
  return when {
    this === b -> true
    this == null || b == null -> false
    else -> {
      this.duration == b.duration &&
      this.length == b.length &&
      this.video.equalsTo(b.video) &&
      this.thumbnail.equalsTo(b.thumbnail) &&
      this.minithumbnail.equalsTo(b.minithumbnail)
    }
  }
}

fun Video?.equalsTo(b: Video?): Boolean {
  return when {
    this === b -> true
    this == null || b == null -> false
    else -> {
      this.width == b.width &&
      this.height == b.height &&
      this.duration == b.duration &&
      this.supportsStreaming == b.supportsStreaming &&
      this.hasStickers == b.hasStickers &&
      this.video.equalsTo(b.video) &&
      this.fileName.equalsOrBothEmpty(b.fileName) &&
      this.mimeType.equalsOrBothEmpty(b.mimeType) &&
      this.thumbnail.equalsTo(b.thumbnail) &&
      this.minithumbnail.equalsTo(b.minithumbnail)
    }
  }
}

fun Audio?.equalsTo(b: Audio?): Boolean {
  return when {
    this === b -> true
    this == null || b == null -> false
    else -> {
      this.duration == b.duration &&
      this.title.equalsOrBothEmpty(b.title) &&
      this.performer.equalsOrBothEmpty(b.performer) &&
      this.mimeType.equalsOrBothEmpty(b.mimeType) &&
      this.fileName.equalsOrBothEmpty(b.fileName) &&
      this.audio.equalsTo(b.audio) &&
      this.albumCoverThumbnail.equalsTo(b.albumCoverThumbnail) &&
      this.albumCoverMinithumbnail.equalsTo(b.albumCoverMinithumbnail)
    }
  }
}

fun Photo?.equalsTo(b: Photo?): Boolean {
  return when {
    this === b -> true
    this == null || b == null -> false
    else -> {
      this.hasStickers == b.hasStickers &&
      this.minithumbnail.equalsTo(b.minithumbnail) &&
      this.sizes.equalsTo(b.sizes)
    }
  }
}

fun Animation?.equalsTo(b: Animation?): Boolean {
  return when {
    this === b -> true
    this == null || b == null -> false
    else -> {
      this.width == b.width &&
      this.height == b.height &&
      this.duration == b.duration &&
      this.hasStickers == b.hasStickers &&
      this.animation.equalsTo(b.animation) &&
      this.mimeType.equalsOrBothEmpty(b.mimeType) &&
      this.fileName.equalsOrBothEmpty(b.fileName) &&
      this.minithumbnail.equalsTo(b.minithumbnail) &&
      this.thumbnail.equalsTo(b.thumbnail)
    }
  }
}

fun Document?.equalsTo(b: Document?): Boolean {
  return when {
    this === b -> true
    this == null || b == null -> false
    else -> {
      this.document.equalsTo(b.document) &&
      this.mimeType.equalsOrBothEmpty(b.mimeType) &&
      this.fileName.equalsOrBothEmpty(b.fileName) &&
      this.minithumbnail.equalsTo(b.minithumbnail) &&
      this.thumbnail.equalsTo(b.thumbnail)
    }
  }
}

fun Sticker?.equalsTo(b: Sticker?): Boolean {
  return when {
    this === b -> true
    this == null || b == null -> false
    else -> {
      this.width == b.width &&
      this.height == b.height &&
      this.type.equalsTo(b.type) &&
      this.setId == b.setId &&
      this.emoji.equalsOrBothEmpty(b.emoji) &&
      this.sticker.equalsTo(b.sticker) &&
      this.outline.equalsTo(b.outline) &&
      this.thumbnail.equalsTo(b.thumbnail)
    }
  }
}

fun StickerType?.equalsTo(b: StickerType?): Boolean {
  return when {
    this === b -> true
    this == null || b == null || this.constructor != b.constructor -> false
    else -> when (this.constructor) {
      StickerTypeStatic.CONSTRUCTOR, StickerTypeAnimated.CONSTRUCTOR, StickerTypeVideo.CONSTRUCTOR -> true
      StickerTypeMask.CONSTRUCTOR -> {
        require(this is StickerTypeMask && b is StickerTypeMask)
        this.maskPosition.equalsTo(b.maskPosition)
      }
      else -> TODO(this.toString())
    }
  }
}

fun Array<ClosedVectorPath>?.equalsTo(b: Array<ClosedVectorPath>?): Boolean {
  return when {
    this === b -> true
    this == null || b == null || this.size != b.size -> false
    else -> {
      this.forEachIndexed { index, path ->
        if (!path.equalsTo(b[index])) {
          return false
        }
      }
      true
    }
  }
}

fun ClosedVectorPath?.equalsTo(b: ClosedVectorPath?): Boolean {
  return this === b || this?.commands.equalsTo(b?.commands)
}

fun Array<VectorPathCommand>?.equalsTo(b: Array<VectorPathCommand>?): Boolean {
  return when {
    this === b -> true
    this == null || b == null || this.size != b.size -> false
    else -> {
      this.forEachIndexed { index, command ->
        if (!command.equalsTo(b[index])) {
          return false
        }
      }
      true
    }
  }
}

fun VectorPathCommand?.equalsTo(b: VectorPathCommand?): Boolean {
  return when {
    this === b -> true
    this == null || b == null -> false
    else -> when(this.constructor) {
      VectorPathCommandLine.CONSTRUCTOR -> (this as VectorPathCommandLine).endPoint.equalsTo((b as VectorPathCommandLine).endPoint)
      VectorPathCommandCubicBezierCurve.CONSTRUCTOR -> {
        (this as VectorPathCommandCubicBezierCurve).endPoint.equalsTo((b as VectorPathCommandCubicBezierCurve).endPoint) &&
          this.startControlPoint.equalsTo(b.startControlPoint) &&
          this.endControlPoint.equalsTo(b.endControlPoint)
      }
      else -> TODO(this.toString())
    }
  }
}

fun Point?.equalsTo(b: Point?): Boolean {
  return this === b || this?.x == b?.x && this?.y == b?.y
}

fun MaskPosition?.equalsTo(b: MaskPosition?): Boolean {
  return when {
    this === b -> true
    this == null || b == null -> false
    else -> {
      this.scale == b.scale &&
      this.xShift == b.xShift &&
      this.yShift == b.yShift &&
      this.point.equalsTo(b.point)
    }
  }
}

fun MaskPoint?.equalsTo(b: MaskPoint?): Boolean {
  return when {
    this === b -> true
    this == null || b == null || this.constructor != b.constructor -> false
    else -> when (this.constructor) {
      MaskPointForehead.CONSTRUCTOR,
      MaskPointEyes.CONSTRUCTOR,
      MaskPointMouth.CONSTRUCTOR,
      MaskPointChin.CONSTRUCTOR -> true
      else -> error(this.toString())
    }
  }
}

@ExperimentalContracts
@JvmOverloads fun DraftMessage?.equalsTo(b: DraftMessage?, ignoreDate: Boolean = false): Boolean {
  return when {
    this === b -> true
    this == null || b == null -> false
    else -> {
      (this.date == b.date || ignoreDate) && this.replyToMessageId == b.replyToMessageId && this.inputMessageText.equalsTo(b.inputMessageText)
    }
  }
}

@ExperimentalContracts
fun InputMessageContent?.equalsTo(b: InputMessageContent?): Boolean {
  return when {
    this === b -> true
    this == null || b == null || this.constructor != b.constructor -> false
    else -> when (this.constructor) {
      InputMessageText.CONSTRUCTOR -> {
        (this as InputMessageText).text.equalsTo((b as InputMessageText).text) &&
        this.clearDraft == b.clearDraft && this.disableWebPagePreview == b.disableWebPagePreview
      }
      else -> TODO(this.toString())
    }
  }
}

@ExperimentalContracts
fun WebPage?.equalsTo(b: WebPage?): Boolean {
  return when {
    this === b -> true
    this == null || b == null -> false
    else -> {
      this.instantViewVersion == b.instantViewVersion &&
      this.duration == b.duration &&
      this.embedWidth == b.embedWidth &&
      this.embedHeight == b.embedHeight &&
      this.embedType.equalsOrBothEmpty(b.embedType) &&
      this.embedUrl.equalsOrBothEmpty(b.embedUrl) &&
      this.type.equalsOrBothEmpty(b.type) &&
      this.url.equalsOrBothEmpty(b.url) &&
      this.displayUrl.equalsOrBothEmpty(b.displayUrl) &&
      this.siteName.equalsOrBothEmpty(b.siteName) &&
      this.title.equalsOrBothEmpty(b.title) &&
      this.description.equalsTo(b.description) &&
      this.author.equalsOrBothEmpty(b.author) &&
      this.photo.equalsTo(b.photo) &&
      this.animation.equalsTo(b.animation) &&
      this.audio.equalsTo(b.audio) &&
      this.document.equalsTo(b.document) &&
      this.video.equalsTo(b.video) &&
      this.videoNote.equalsTo(b.videoNote) &&
      this.voiceNote.equalsTo(b.voiceNote) &&
      this.sticker.equalsTo(b.sticker)
    }
  }
}

fun MessageSender?.equalsTo(b: MessageSender?): Boolean {
  return when {
    this === b -> true
    this == null || b == null || this.constructor != b.constructor -> false
    else -> {
      when (this.constructor) {
        MessageSenderChat.CONSTRUCTOR -> (this as MessageSenderChat).chatId == (b as MessageSenderChat).chatId
        MessageSenderUser.CONSTRUCTOR -> (this as MessageSenderUser).userId == (b as MessageSenderUser).userId
        else -> error(this.toString())
      }
    }
  }
}

fun BackgroundType?.equalsTo(b: BackgroundType?, ignoreSettings: Boolean = true): Boolean {
  return when {
    this === b -> true
    this == null || b == null || this.constructor != b.constructor -> false
    else -> {
      when (this.constructor) {
        BackgroundTypeWallpaper.CONSTRUCTOR -> {
          require(this is BackgroundTypeWallpaper && b is BackgroundTypeWallpaper)
          ignoreSettings || (this.isBlurred == b.isBlurred && this.isMoving == b.isMoving)
        }
        BackgroundTypeFill.CONSTRUCTOR -> {
          require(this is BackgroundTypeFill && b is BackgroundTypeFill)
          this.fill.equalsTo(b.fill)
        }
        BackgroundTypePattern.CONSTRUCTOR -> {
          require(this is BackgroundTypePattern && b is BackgroundTypePattern)
          this.fill.equalsTo(b.fill) && (ignoreSettings || (this.intensity == b.intensity && this.isMoving == b.isMoving))
        }
        else -> error(this.toString())
      }
    }
  }
}

fun BackgroundFill?.equalsTo(b: BackgroundFill?): Boolean {
  return when {
    this === b -> true
    this == null || b == null || this.constructor != b.constructor -> false
    else -> {
      when (this.constructor) {
        BackgroundFillSolid.CONSTRUCTOR -> {
          require(this is BackgroundFillSolid && b is BackgroundFillSolid)
          this.color == b.color
        }
        BackgroundFillGradient.CONSTRUCTOR -> {
          require(this is BackgroundFillGradient && b is BackgroundFillGradient)
          this.topColor == b.topColor && this.bottomColor == b.bottomColor && this.rotationAngle == b.rotationAngle
        }
        BackgroundFillFreeformGradient.CONSTRUCTOR -> {
          require(this is BackgroundFillFreeformGradient && b is BackgroundFillFreeformGradient)
          this.colors.contentEquals(b.colors)
        }
        else -> error(this.toString())
      }
    }
  }
}