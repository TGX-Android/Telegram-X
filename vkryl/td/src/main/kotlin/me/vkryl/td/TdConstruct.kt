@file:JvmName("Td")
@file:JvmMultifileClass

package me.vkryl.td

import org.drinkless.td.libcore.telegram.TdApi.*

fun constructSearchMessagesFilter (@SearchMessagesFilter.Constructors constructor: Int): SearchMessagesFilter {
  return when (constructor) {
    SearchMessagesFilterEmpty.CONSTRUCTOR -> SearchMessagesFilterEmpty()
    SearchMessagesFilterAnimation.CONSTRUCTOR -> SearchMessagesFilterAnimation()
    SearchMessagesFilterAudio.CONSTRUCTOR -> SearchMessagesFilterAudio()
    SearchMessagesFilterDocument.CONSTRUCTOR -> SearchMessagesFilterDocument()
    SearchMessagesFilterPhoto.CONSTRUCTOR -> SearchMessagesFilterPhoto()
    SearchMessagesFilterVideo.CONSTRUCTOR -> SearchMessagesFilterVideo()
    SearchMessagesFilterVoiceNote.CONSTRUCTOR -> SearchMessagesFilterVoiceNote()
    SearchMessagesFilterPhotoAndVideo.CONSTRUCTOR -> SearchMessagesFilterPhotoAndVideo()
    SearchMessagesFilterUrl.CONSTRUCTOR -> SearchMessagesFilterUrl()
    SearchMessagesFilterChatPhoto.CONSTRUCTOR -> SearchMessagesFilterChatPhoto()
    SearchMessagesFilterCall.CONSTRUCTOR -> SearchMessagesFilterCall()
    SearchMessagesFilterMissedCall.CONSTRUCTOR -> SearchMessagesFilterMissedCall()
    SearchMessagesFilterVideoNote.CONSTRUCTOR -> SearchMessagesFilterVideoNote()
    SearchMessagesFilterVoiceAndVideoNote.CONSTRUCTOR -> SearchMessagesFilterVoiceAndVideoNote()
    SearchMessagesFilterMention.CONSTRUCTOR -> SearchMessagesFilterMention()
    SearchMessagesFilterUnreadMention.CONSTRUCTOR -> SearchMessagesFilterUnreadMention()
    SearchMessagesFilterFailedToSend.CONSTRUCTOR -> SearchMessagesFilterFailedToSend()
    else -> error(constructor.toString())
  }
}

fun constructNotificationSettingsScope (@NotificationSettingsScope.Constructors constructor: Int): NotificationSettingsScope {
  return when (constructor) {
    NotificationSettingsScopePrivateChats.CONSTRUCTOR -> NotificationSettingsScopePrivateChats()
    NotificationSettingsScopeGroupChats.CONSTRUCTOR -> NotificationSettingsScopeGroupChats()
    NotificationSettingsScopeChannelChats.CONSTRUCTOR -> NotificationSettingsScopeChannelChats()
    else -> error(constructor.toString())
  }
}

fun constructChatAction (@ChatAction.Constructors constructor: Int): ChatAction? {
  return when (constructor) {
    ChatActionCancel.CONSTRUCTOR -> ChatActionCancel()
    ChatActionTyping.CONSTRUCTOR -> ChatActionTyping()
    ChatActionRecordingVoiceNote.CONSTRUCTOR -> ChatActionRecordingVoiceNote()
    ChatActionRecordingVideoNote.CONSTRUCTOR -> ChatActionRecordingVideoNote()
    ChatActionRecordingVideo.CONSTRUCTOR -> ChatActionRecordingVideo()
    ChatActionChoosingContact.CONSTRUCTOR -> ChatActionChoosingContact()
    ChatActionChoosingLocation.CONSTRUCTOR -> ChatActionChoosingLocation()
    ChatActionStartPlayingGame.CONSTRUCTOR -> ChatActionStartPlayingGame()
    
    ChatActionUploadingDocument.CONSTRUCTOR,
    ChatActionUploadingPhoto.CONSTRUCTOR,
    ChatActionUploadingVideo.CONSTRUCTOR,
    ChatActionUploadingVideoNote.CONSTRUCTOR,
    ChatActionUploadingVoiceNote.CONSTRUCTOR -> null

    else -> null
  }
}