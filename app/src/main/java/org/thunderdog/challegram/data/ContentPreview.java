package org.thunderdog.challegram.data;

import android.util.SparseIntArray;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.Settings;

import java.util.List;
import java.util.concurrent.TimeUnit;

import me.vkryl.core.ArrayUtils;
import me.vkryl.core.CurrencyUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.td.ChatId;
import me.vkryl.td.Td;

public class ContentPreview {
  public static final Emoji
    EMOJI_PHOTO = new Emoji("\uD83D\uDDBC", R.drawable.baseline_camera_alt_16); // "\uD83D\uDCF7"
  public static final Emoji EMOJI_VIDEO = new Emoji("\uD83C\uDFA5", R.drawable.baseline_videocam_16); // "\uD83D\uDCF9"
  public static final Emoji EMOJI_ROUND_VIDEO = new Emoji("\uD83D\uDCF9", R.drawable.deproko_baseline_msg_video_16);
  public static final Emoji EMOJI_SECRET_PHOTO = new Emoji("\uD83D\uDD25", R.drawable.deproko_baseline_whatshot_16);
  public static final Emoji EMOJI_SECRET_VIDEO = new Emoji("\uD83D\uDD25", R.drawable.deproko_baseline_whatshot_16);
  public static final Emoji EMOJI_LINK = new Emoji("\uD83D\uDD17", R.drawable.baseline_link_16);
  public static final Emoji EMOJI_GAME = new Emoji("\uD83C\uDFAE", R.drawable.baseline_videogame_asset_16);
  public static final Emoji EMOJI_GROUP = new Emoji("\uD83D\uDC65", R.drawable.baseline_group_16);
  public static final Emoji EMOJI_GIFT = new Emoji("\uD83C\uDF81", R.drawable.baseline_redeem_16);
  public static final Emoji EMOJI_THEME = new Emoji("\uD83C\uDFA8", R.drawable.baseline_palette_16);
  public static final Emoji EMOJI_GROUP_INVITE = new Emoji("\uD83D\uDC65", R.drawable.baseline_group_add_16);
  public static final Emoji EMOJI_CHANNEL = new Emoji("\uD83D\uDCE2", R.drawable.baseline_bullhorn_16); // "\uD83D\uDCE3"
  public static final Emoji EMOJI_FILE = new Emoji("\uD83D\uDCCE", R.drawable.baseline_insert_drive_file_16);
  public static final Emoji EMOJI_AUDIO = new Emoji("\uD83C\uDFB5", R.drawable.baseline_music_note_16);
  public static final Emoji EMOJI_CONTACT = new Emoji("\uD83D\uDC64", R.drawable.baseline_person_16);
  public static final Emoji EMOJI_POLL = new Emoji("\uD83D\uDCCA", R.drawable.baseline_poll_16);
  public static final Emoji EMOJI_QUIZ = new Emoji("\u2753", R.drawable.baseline_help_16);
  public static final Emoji EMOJI_VOICE = new Emoji("\uD83C\uDFA4", R.drawable.baseline_mic_16);
  public static final Emoji EMOJI_GIF = new Emoji("\uD83D\uDC7E", R.drawable.deproko_baseline_gif_filled_16);
  public static final Emoji EMOJI_LOCATION = new Emoji("\uD83D\uDCCC", R.drawable.baseline_gps_fixed_16);
  public static final Emoji EMOJI_INVOICE = new Emoji("\uD83D\uDCB8", R.drawable.baseline_receipt_16);
  public static final Emoji EMOJI_USER_JOINED = new Emoji("\uD83C\uDF89", R.drawable.baseline_party_popper_16);
  public static final Emoji EMOJI_SCREENSHOT = new Emoji("\uD83D\uDCF8", R.drawable.round_warning_16);
  public static final Emoji EMOJI_PIN = new Emoji("\uD83D\uDCCC", R.drawable.deproko_baseline_pin_16);
  public static final Emoji EMOJI_ALBUM_MEDIA = new Emoji("\uD83D\uDDBC", R.drawable.baseline_collections_16);
  public static final Emoji EMOJI_ALBUM_PHOTOS = new Emoji("\uD83D\uDDBC", R.drawable.baseline_collections_16);
  public static final Emoji EMOJI_ALBUM_AUDIO = new Emoji("\uD83C\uDFB5", R.drawable.ivanliana_baseline_audio_collections_16);
  public static final Emoji EMOJI_ALBUM_FILES = new Emoji("\uD83D\uDCCE", R.drawable.ivanliana_baseline_file_collections_16);
  public static final Emoji EMOJI_ALBUM_VIDEOS = new Emoji("\uD83C\uDFA5", R.drawable.ivanliana_baseline_video_collections_16);
  public static final Emoji EMOJI_FORWARD = new Emoji("\u21A9", R.drawable.baseline_share_arrow_16);
  public static final Emoji EMOJI_ABACUS = new Emoji("\uD83E\uDDEE", R.drawable.baseline_bar_chart_24);
  public static final Emoji EMOJI_DART = new Emoji("\uD83C\uDFAF", R.drawable.baseline_gps_fixed_16);
  public static final Emoji EMOJI_DICE = new Emoji("\uD83C\uDFB2", R.drawable.baseline_casino_16);
  public static final Emoji EMOJI_DICE_1 = new Emoji("\uD83C\uDFB2", R.drawable.belledeboheme_baseline_dice_1_16);
  public static final Emoji EMOJI_DICE_2 = new Emoji("\uD83C\uDFB2", R.drawable.belledeboheme_baseline_dice_2_16);
  public static final Emoji EMOJI_DICE_3 = new Emoji("\uD83C\uDFB2", R.drawable.belledeboheme_baseline_dice_3_16);
  public static final Emoji EMOJI_DICE_4 = new Emoji("\uD83C\uDFB2", R.drawable.belledeboheme_baseline_dice_4_16);
  public static final Emoji EMOJI_DICE_5 = new Emoji("\uD83C\uDFB2", R.drawable.belledeboheme_baseline_dice_5_16);
  public static final Emoji EMOJI_DICE_6 = new Emoji("\uD83C\uDFB2", R.drawable.belledeboheme_baseline_dice_6_16);
  public static final Emoji EMOJI_CALL = new Emoji("\uD83D\uDCDE", R.drawable.baseline_call_16);
  public static final Emoji EMOJI_TIMER = new Emoji("\u23F2", R.drawable.baseline_timer_16);
  public static final Emoji EMOJI_TIMER_OFF = new Emoji("\u23F2", R.drawable.baseline_timer_off_16);
  public static final Emoji EMOJI_CALL_END = new Emoji("\uD83D\uDCDE", R.drawable.baseline_call_end_16);
  public static final Emoji EMOJI_CALL_MISSED = new Emoji("\u260E", R.drawable.baseline_call_missed_18);
  public static final Emoji EMOJI_CALL_DECLINED = new Emoji("\u260E", R.drawable.baseline_call_received_18);
  public static final Emoji EMOJI_WARN = new Emoji("\u26A0", R.drawable.baseline_warning_18);
  public static final Emoji EMOJI_INFO = new Emoji("\u2139", R.drawable.baseline_info_18);
  public static final Emoji EMOJI_ERROR = new Emoji("\u2139", R.drawable.baseline_error_18);
  public static final Emoji EMOJI_LOCK = new Emoji("\uD83D\uDD12", R.drawable.baseline_lock_16)
;
  private static final int ARG_NONE = 0;
  private static final int ARG_TRUE = 1;
  private static final int ARG_POLL_QUIZ = 1;
  private static final int ARG_CALL_DECLINED = -1;
  private static final int ARG_CALL_MISSED = -2;
  private static final int ARG_RECURRING_PAYMENT = -3;
  private static final long ADDITIONAL_MESSAGE_UI_LOAD_TIMEOUT_MS = -1; // Always async
  private static final long ADDITIONAL_MESSAGE_LOAD_TIMEOUT_MS = 0;
  public final @Nullable Emoji emoji, parentEmoji;
  public final @StringRes int placeholderText;
  public final @Nullable TdApi.FormattedText formattedText;
  public final boolean isTranslatable;
  public final boolean hideAuthor;
  public @Nullable TdApi.Message relatedMessage;
  private @Nullable MessageContentBuilder relatedMessageBuilder;

  public ContentPreview (ContentPreview copy, TdApi.FormattedText editedFormattedText) {
    this.emoji = copy.emoji;
    this.parentEmoji = copy.parentEmoji;
    this.placeholderText = copy.placeholderText;
    this.formattedText = editedFormattedText != null ? editedFormattedText : copy.formattedText;
    this.isTranslatable = copy.isTranslatable;
    this.hideAuthor = copy.hideAuthor;
    this.relatedMessage = copy.relatedMessage;
    this.relatedMessageBuilder = copy.relatedMessageBuilder;
    this.refresher = copy.refresher;
    this.isMediaGroup = copy.isMediaGroup;
    this.album = copy.album;
  }

  public ContentPreview (@Nullable Emoji emoji, @StringRes int placeholderTextRes) {
    this(emoji, placeholderTextRes, (TdApi.FormattedText) null);
  }

  public ContentPreview (@Nullable Emoji emoji, @StringRes int placeholderTextRes, @Nullable String text) {
    this(emoji, placeholderTextRes, StringUtils.isEmpty(text) ? null : new TdApi.FormattedText(text, null), false);
  }

  public ContentPreview (@Nullable Emoji emoji, @StringRes int placeholderTextRes, @Nullable TdApi.FormattedText text) {
    this(emoji, placeholderTextRes, text, false);
  }

  public ContentPreview (@Nullable String text, boolean textTranslatable) {
    this(null, 0, text, textTranslatable);
  }

  public ContentPreview (@Nullable TdApi.FormattedText text, boolean textTranslatable) {
    this(null, 0, text, textTranslatable);
  }

  public ContentPreview (@Nullable Emoji emoji, @StringRes int placeholderTextRes, @Nullable TdApi.FormattedText text, boolean textTranslatable) {
    this(emoji, placeholderTextRes, text, textTranslatable, false, null);
  }

  public ContentPreview (@Nullable Emoji emoji, @StringRes int placeholderTextRes, @Nullable String text, boolean textTranslatable) {
    this(emoji, placeholderTextRes, StringUtils.isEmpty(text) ? null : new TdApi.FormattedText(text, null), textTranslatable, false, null);
  }

  public ContentPreview (@Nullable Emoji emoji, ContentPreview copy) {
    this(copy.emoji, copy.placeholderText, copy.formattedText, copy.isTranslatable, copy.hideAuthor, emoji);
  }

  public ContentPreview (@Nullable TdApi.FormattedText text, ContentPreview copy) {
    this(copy.emoji, copy.placeholderText, text != null ? text : copy.formattedText, copy.isTranslatable, copy.hideAuthor, copy.parentEmoji);
  }

  public ContentPreview (@Nullable Emoji emoji, int placeholderText, @Nullable TdApi.FormattedText formattedText, boolean isTranslatable, boolean hideAuthor, @Nullable Emoji parentEmoji) {
    this.emoji = emoji;
    this.placeholderText = placeholderText;
    this.formattedText = formattedText;
    this.isTranslatable = isTranslatable;
    this.hideAuthor = hideAuthor;
    this.parentEmoji = parentEmoji;
  }

  private interface MessageContentBuilder {
    ContentPreview runBuilder (TdApi.Message message);
  }

  public boolean belongsToRelatedMessage (long chatId, long[] messageIds) {
    return relatedMessage != null && relatedMessage.chatId == chatId && ArrayUtils.contains(messageIds, relatedMessage.id);
  }

  private ContentPreview setRelatedMessage (@NonNull TdApi.Message message, @NonNull MessageContentBuilder refresher) {
    this.relatedMessage = message;
    this.relatedMessageBuilder = refresher;
    return this;
  }

  public boolean updateRelatedMessage (long chatId, long messageId, TdApi.MessageContent content, RefreshCallback callback) {
    if (relatedMessage != null && relatedMessage.chatId == chatId && relatedMessage.id == messageId) {
      relatedMessage.content = content;
      if (callback != null) {
        ContentPreview newPreview;
        if (relatedMessageBuilder != null) {
          newPreview = relatedMessageBuilder.runBuilder(relatedMessage);
        } else {
          newPreview = null;
        }
        if (newPreview != null) {
          callback.onContentPreviewChanged(chatId, messageId, newPreview, this);
        } else {
          callback.onContentPreviewNotChanged(chatId, messageId, this);
        }
      }
      return true;
    }
    return false;
  }

  @NonNull
  public static ContentPreview getChatListPreview (Tdlib tdlib, long chatId, TdApi.Message message, boolean checkChatRestrictions) {
    return getContentPreview(tdlib, chatId, message, true, true, checkChatRestrictions);
  }

  @NonNull
  public static ContentPreview getNotificationPreview (Tdlib tdlib, long chatId, TdApi.Message message, boolean allowContent) {
    return getContentPreview(tdlib, chatId, message, allowContent, false, true);
  }

  private static long additionalMessageLoadTimeoutMs () {
    if (UI.inUiThread()) {
      return ADDITIONAL_MESSAGE_UI_LOAD_TIMEOUT_MS;
    } else {
      return ADDITIONAL_MESSAGE_LOAD_TIMEOUT_MS;
    }
  }

  @NonNull
  private static ContentPreview getContentPreview (Tdlib tdlib, long chatId, @Nullable TdApi.Message message, boolean allowContent, boolean isChatList, boolean checkChatRestrictions) {
    if (message == null) {
      return new ContentPreview(EMOJI_ERROR, 0, Lang.getString(R.string.DeletedMessage), false);
    }
    if (Settings.instance().needRestrictContent()) {
      if (!StringUtils.isEmpty(message.restrictionReason)) {
        return new ContentPreview(EMOJI_ERROR, 0, message.restrictionReason, false);
      }
      if (checkChatRestrictions && chatId != 0) { // Otherwise lookup is handled by the caller
        String restrictionReason = tdlib.chatRestrictionReason(chatId);
        if (restrictionReason != null) {
          return new ContentPreview(EMOJI_ERROR, 0, restrictionReason, false);
        }
      }
    }
    @TdApi.MessageContent.Constructors int type = message.content.getConstructor();
    TdApi.FormattedText formattedText;
    if (allowContent) {
      formattedText = Td.textOrCaption(message.content);
      if (message.isOutgoing) {
        TdApi.FormattedText pendingText = tdlib.getPendingFormattedText(message.chatId, message.id);
        if (pendingText != null) {
          formattedText = pendingText;
        }
      }
    } else {
      formattedText = null;
    }
    String alternativeText = null;
    boolean alternativeTextTranslatable = false;
    int arg1 = ARG_NONE;
    int arg2 = ARG_NONE;
    switch (type) {
      case TdApi.MessageText.CONSTRUCTOR: {
        TdApi.MessageText messageText = (TdApi.MessageText) message.content;
        if (!Td.isEmpty(messageText.text) && messageText.text.entities != null) {
          boolean isUrl = false;
          for (TdApi.TextEntity entity : messageText.text.entities) {
            //noinspection SwitchIntDef
            switch (entity.type.getConstructor()) {
              case TdApi.TextEntityTypeTextUrl.CONSTRUCTOR:
              case TdApi.TextEntityTypeUrl.CONSTRUCTOR: {
                if (entity.offset == 0 && (
                  entity.length == messageText.text.text.length() ||
                  !Td.isTextUrl(entity.type) ||
                  !StringUtils.isEmptyOrInvisible(Td.substring(messageText.text.text, entity
                  )))
                ) {
                  isUrl = true;
                  break;
                }
                break;
              }
            }
          }
          if (isUrl) {
            arg1 = ARG_TRUE;
          }
        }
        break;
      }
      case TdApi.MessageAnimatedEmoji.CONSTRUCTOR: {
        TdApi.MessageAnimatedEmoji animatedEmoji = (TdApi.MessageAnimatedEmoji) message.content;
        alternativeText = animatedEmoji.emoji;
        break;
      }
      case TdApi.MessageDocument.CONSTRUCTOR:
        alternativeText = ((TdApi.MessageDocument) message.content).document.fileName;
        break;
      case TdApi.MessageVoiceNote.CONSTRUCTOR: {
        int duration = ((TdApi.MessageVoiceNote) message.content).voiceNote.duration;
        if (duration > 0) {
          alternativeText = Lang.getString(R.string.ChatContentVoiceDuration, Lang.getString(R.string.ChatContentVoice), Strings.buildDuration(duration));
          alternativeTextTranslatable = true;
        }
        break;
      }
      case TdApi.MessageVideoNote.CONSTRUCTOR: {
        int duration = ((TdApi.MessageVideoNote) message.content).videoNote.duration;
        if (duration > 0) {
          alternativeText = Lang.getString(R.string.ChatContentVoiceDuration, Lang.getString(R.string.ChatContentRoundVideo), Strings.buildDuration(duration));
          alternativeTextTranslatable = true;
        }
        break;
      }
      case TdApi.MessageAudio.CONSTRUCTOR:
        TdApi.Audio audio = ((TdApi.MessageAudio) message.content).audio;
        alternativeText = Lang.getString(R.string.ChatContentSong, TD.getTitle(audio), TD.getSubtitle(audio));
        alternativeTextTranslatable = !TD.hasTitle(audio) || !TD.hasSubtitle(audio);
        break;
      case TdApi.MessageContact.CONSTRUCTOR: {
        TdApi.Contact contact = ((TdApi.MessageContact) message.content).contact;
        String name = TD.getUserName(contact.firstName, contact.lastName);
        if (!StringUtils.isEmpty(name))
          alternativeText = name;
        break;
      }
      case TdApi.MessagePoll.CONSTRUCTOR:
        alternativeText = ((TdApi.MessagePoll) message.content).poll.question;
        arg1 = ((TdApi.MessagePoll) message.content).poll.type.getConstructor() == TdApi.PollTypeRegular.CONSTRUCTOR ? ARG_NONE : ARG_POLL_QUIZ;
        break;
      case TdApi.MessageDice.CONSTRUCTOR:
        alternativeText = ((TdApi.MessageDice) message.content).emoji;
        arg1 = ((TdApi.MessageDice) message.content).value;
        break;
      case TdApi.MessageCall.CONSTRUCTOR: {
        switch (((TdApi.MessageCall) message.content).discardReason.getConstructor()) {
          case TdApi.CallDiscardReasonDeclined.CONSTRUCTOR:
            arg1 = ARG_CALL_DECLINED;
            break;
          case TdApi.CallDiscardReasonMissed.CONSTRUCTOR:
            arg1 = ARG_CALL_MISSED;
            break;
          default:
            arg1 = ((TdApi.MessageCall) message.content).duration;
            break;
        }
        break;
      }
      case TdApi.MessageLocation.CONSTRUCTOR: {
        TdApi.MessageLocation location = ((TdApi.MessageLocation) message.content);
        alternativeText = location.livePeriod == 0 || location.expiresIn == 0 ? null : "live";
        break;
      }
      case TdApi.MessageGame.CONSTRUCTOR:
        alternativeText = ((TdApi.MessageGame) message.content).game.title;
        break;
      case TdApi.MessageSticker.CONSTRUCTOR:
        TdApi.Sticker sticker = ((TdApi.MessageSticker) message.content).sticker;
        alternativeText = Td.isAnimated(sticker.format) ? "animated" + sticker.emoji : sticker.emoji;
        break;
      case TdApi.MessageInvoice.CONSTRUCTOR: {
        TdApi.MessageInvoice invoice = (TdApi.MessageInvoice) message.content;
        alternativeText = CurrencyUtils.buildAmount(invoice.currency, invoice.totalAmount);
        break;
      }
      case TdApi.MessagePhoto.CONSTRUCTOR:
        if (((TdApi.MessagePhoto) message.content).isSecret)
          return new ContentPreview(EMOJI_SECRET_PHOTO, R.string.SelfDestructPhoto, formattedText);
        break;
      case TdApi.MessageVideo.CONSTRUCTOR:
        if (((TdApi.MessageVideo) message.content).isSecret)
          return new ContentPreview(EMOJI_SECRET_VIDEO, R.string.SelfDestructVideo, formattedText);
        break;
      case TdApi.MessageAnimation.CONSTRUCTOR:
        // alternativeText = ((TdApi.MessageAnimation) message.content).animation.fileName;
        break;
      case TdApi.MessageStory.CONSTRUCTOR: {
        TdApi.MessageStory story = (TdApi.MessageStory) message.content;
        arg1 = story.viaMention ? ARG_TRUE : ARG_NONE;
        break;
      }
      case TdApi.MessagePinMessage.CONSTRUCTOR: {
        long pinnedMessageId = ((TdApi.MessagePinMessage) message.content).messageId;
        TdApi.Message pinnedMessage;
        long loadTimeoutMs = additionalMessageLoadTimeoutMs();
        if (pinnedMessageId != 0 && loadTimeoutMs >= 0) {
          pinnedMessage = tdlib.getMessageLocally(
            message.chatId, pinnedMessageId, loadTimeoutMs
          );
        } else {
          pinnedMessage = null;
        }
        if (pinnedMessage != null) {
          return new ContentPreview(EMOJI_PIN, getContentPreview(tdlib, chatId, pinnedMessage, allowContent, isChatList, checkChatRestrictions));
        } else {
          return new ContentPreview(EMOJI_PIN, R.string.ChatContentPinned)
            .setRefresher((oldPreview, callback) -> tdlib.getMessage(chatId, pinnedMessageId, remotePinnedMessage -> {
            if (remotePinnedMessage != null) {
              MessageContentBuilder builder = new MessageContentBuilder() {
                @Override
                public ContentPreview runBuilder (TdApi.Message message) {
                  return new ContentPreview(EMOJI_PIN, getContentPreview(tdlib, chatId, message, allowContent, isChatList, checkChatRestrictions))
                    .setRelatedMessage(message, this);
                }
              };
              ContentPreview newPreview = builder.runBuilder(remotePinnedMessage);
              callback.onContentPreviewChanged(chatId, message.id, newPreview, oldPreview);
            } else {
              callback.onContentPreviewNotChanged(chatId, message.id, oldPreview);
            }
          }), false);
        }
      }
      case TdApi.MessageGameScore.CONSTRUCTOR: {
        TdApi.MessageGameScore score = (TdApi.MessageGameScore) message.content;
        long timeoutMs = additionalMessageLoadTimeoutMs();
        TdApi.Message gameMessage = timeoutMs >= 0 ?
          tdlib.getMessageLocally(
            message.chatId, score.gameMessageId,
            timeoutMs
          ) : null;
        String gameTitle = gameMessage != null && Td.isGame(gameMessage.content) ? TD.getGameName(((TdApi.MessageGame) gameMessage.content).game, false) : null;
        if (!StringUtils.isEmpty(gameTitle)) {
          return new ContentPreview(EMOJI_GAME, 0, Lang.plural(message.isOutgoing ? R.string.game_ActionYouScoredInGame : R.string.game_ActionScoredInGame, score.score, gameTitle), true);
        } else {
          return new ContentPreview(EMOJI_GAME, 0, Lang.plural(message.isOutgoing ? R.string.game_ActionYouScored : R.string.game_ActionScored, score.score), true)
            .setRefresher(gameMessage != null ? null :
              (oldPreview, callback) -> tdlib.getMessage(message.chatId, score.gameMessageId, remoteGameMessage -> {
              if (remoteGameMessage != null && Td.isGame(remoteGameMessage.content)) {
                String newGameTitle = TD.getGameName(((TdApi.MessageGame) remoteGameMessage.content).game, false);
                if (!StringUtils.isEmpty(newGameTitle)) {
                  MessageContentBuilder builder = new MessageContentBuilder() {
                    @Override
                    public ContentPreview runBuilder (TdApi.Message updatedMessage) {
                      String newGameTitle = TD.getGameName(((TdApi.MessageGame) updatedMessage.content).game, false);
                      return new ContentPreview(EMOJI_GAME, 0, Lang.plural(message.isOutgoing ? R.string.game_ActionYouScoredInGame : R.string.game_ActionScoredInGame, score.score, newGameTitle), true)
                        .setRelatedMessage(updatedMessage, this);
                    }
                  };
                  ContentPreview newContent = builder.runBuilder(remoteGameMessage);
                  callback.onContentPreviewChanged(message.chatId, message.id, newContent, oldPreview);
                  return;
                }
              }
              callback.onContentPreviewNotChanged(message.chatId, message.id, oldPreview);
          }), false);
        }
      }
      case TdApi.MessageProximityAlertTriggered.CONSTRUCTOR: {
        TdApi.MessageProximityAlertTriggered alert = (TdApi.MessageProximityAlertTriggered) message.content;
        if (tdlib.isSelfSender(alert.travelerId)) {
          return new ContentPreview(EMOJI_LOCATION, 0, Lang.plural(alert.distance >= 1000 ? R.string.ChatContentProximityYouKm : R.string.ChatContentProximityYouM, alert.distance >= 1000 ? alert.distance / 1000 : alert.distance, tdlib.senderName(alert.watcherId, true)), true);
        } else if (tdlib.isSelfSender(alert.watcherId)) {
          return new ContentPreview(EMOJI_LOCATION, 0, Lang.plural(alert.distance >= 1000 ? R.string.ChatContentProximityFromYouKm : R.string.ChatContentProximityFromYouM, alert.distance >= 1000 ? alert.distance / 1000 : alert.distance, tdlib.senderName(alert.travelerId, true)), true);
        } else {
          return new ContentPreview(EMOJI_LOCATION, 0, Lang.plural(alert.distance >= 1000 ? R.string.ChatContentProximityKm : R.string.ChatContentProximityM, alert.distance >= 1000 ? alert.distance / 1000 : alert.distance, tdlib.senderName(alert.travelerId, true), tdlib.senderName(alert.watcherId, true)), true);
        }
      }
      case TdApi.MessageVideoChatStarted.CONSTRUCTOR: {
        if (message.isChannelPost) {
          return new ContentPreview(EMOJI_CALL, message.isOutgoing ? R.string.ChatContentLiveStreamStarted_outgoing : R.string.ChatContentLiveStreamStarted);
        } else {
          return new ContentPreview(EMOJI_CALL, message.isOutgoing ? R.string.ChatContentVoiceChatStarted_outgoing : R.string.ChatContentVoiceChatStarted);
        }
      }
      case TdApi.MessageVideoChatEnded.CONSTRUCTOR: {
        TdApi.MessageVideoChatEnded videoChatOrLiveStream = (TdApi.MessageVideoChatEnded) message.content;
        if (message.isChannelPost) {
          return new ContentPreview(EMOJI_CALL_END, 0, Lang.getString(message.isOutgoing ? R.string.ChatContentLiveStreamFinished_outgoing : R.string.ChatContentLiveStreamFinished, Lang.getCallDuration(videoChatOrLiveStream.duration)), true);
        } else {
          return new ContentPreview(EMOJI_CALL_END, 0, Lang.getString(message.isOutgoing ? R.string.ChatContentVoiceChatFinished_outgoing : R.string.ChatContentVoiceChatFinished, Lang.getCallDuration(videoChatOrLiveStream.duration)), true);
        }
      }
      case TdApi.MessageVideoChatScheduled.CONSTRUCTOR: {
        TdApi.MessageVideoChatScheduled event = (TdApi.MessageVideoChatScheduled) message.content;
        return new ContentPreview(EMOJI_CALL, 0, Lang.getString(message.isChannelPost ? R.string.LiveStreamScheduledOn : R.string.VideoChatScheduledFor, Lang.getMessageTimestamp(event.startDate, TimeUnit.SECONDS)), true);
      }
      case TdApi.MessageInviteVideoChatParticipants.CONSTRUCTOR: {
        TdApi.MessageInviteVideoChatParticipants info = (TdApi.MessageInviteVideoChatParticipants) message.content;
        if (message.isChannelPost) {
          if (info.userIds.length == 1) {
            long userId = info.userIds[0];
            if (tdlib.isSelfUserId(userId)) {
              return new ContentPreview(EMOJI_GROUP_INVITE, R.string.ChatContentLiveStreamInviteYou);
            } else {
              return new ContentPreview(EMOJI_GROUP_INVITE, 0, Lang.getString(message.isOutgoing ? R.string.ChatContentLiveStreamInvite_outgoing : R.string.ChatContentLiveStreamInvite, tdlib.cache().userName(userId)), true);
            }
          } else {
            return new ContentPreview(EMOJI_GROUP_INVITE, 0, Lang.plural(message.isOutgoing ? R.string.ChatContentLiveStreamInviteMulti_outgoing : R.string.ChatContentLiveStreamInviteMulti, info.userIds.length), true);
          }
        } else {
          if (info.userIds.length == 1) {
            long userId = info.userIds[0];
            if (tdlib.isSelfUserId(userId)) {
              return new ContentPreview(EMOJI_GROUP_INVITE, R.string.ChatContentVoiceChatInviteYou);
            } else {
              return new ContentPreview(EMOJI_GROUP_INVITE, 0, Lang.getString(message.isOutgoing ? R.string.ChatContentVoiceChatInvite_outgoing : R.string.ChatContentVoiceChatInvite, tdlib.cache().userName(userId)), true);
            }
          } else {
            return new ContentPreview(EMOJI_GROUP_INVITE, 0, Lang.plural(message.isOutgoing ? R.string.ChatContentVoiceChatInviteMulti_outgoing : R.string.ChatContentVoiceChatInviteMulti, info.userIds.length), true);
          }
        }
      }
      case TdApi.MessageChatAddMembers.CONSTRUCTOR: {
        TdApi.MessageChatAddMembers info = (TdApi.MessageChatAddMembers) message.content;
        if (info.memberUserIds.length == 1) {
          long userId = info.memberUserIds[0];
          if (userId == Td.getSenderUserId(message)) {
            if (ChatId.isSupergroup(message.chatId)) {
              return new ContentPreview(EMOJI_GROUP, message.isOutgoing ? R.string.ChatContentGroupJoinPublic_outgoing : R.string.ChatContentGroupJoinPublic);
            } else { // isReturned
              return new ContentPreview(EMOJI_GROUP, message.isOutgoing ? R.string.ChatContentGroupReturn_outgoing : R.string.ChatContentGroupReturn);
            }
          } else if (tdlib.isSelfUserId(userId)) {
            return new ContentPreview(EMOJI_GROUP, R.string.ChatContentGroupAddYou);
          } else {
            return new ContentPreview(EMOJI_GROUP, 0, Lang.getString(message.isOutgoing ? R.string.ChatContentGroupAdd_outgoing : R.string.ChatContentGroupAdd, tdlib.cache().userName(userId)), true);
          }
        } else {
          return new ContentPreview(EMOJI_GROUP, 0, Lang.plural(message.isOutgoing ? R.string.ChatContentGroupAddMembers_outgoing : R.string.ChatContentGroupAddMembers, info.memberUserIds.length), true);
        }
      }
      case TdApi.MessageChatDeleteMember.CONSTRUCTOR: {
        long userId = ((TdApi.MessageChatDeleteMember) message.content).userId;
        if (userId == Td.getSenderUserId(message)) {
          return new ContentPreview(EMOJI_GROUP, message.isOutgoing ? R.string.ChatContentGroupLeft_outgoing : R.string.ChatContentGroupLeft);
        } else if (tdlib.isSelfUserId(userId)) {
          return new ContentPreview(EMOJI_GROUP, R.string.ChatContentGroupKickYou);
        } else {
          return new ContentPreview(EMOJI_GROUP, 0, Lang.getString(message.isOutgoing ? R.string.ChatContentGroupKick_outgoing : R.string.ChatContentGroupKick, tdlib.cache().userFirstName(userId)), true);
        }
      }
      case TdApi.MessageChatChangeTitle.CONSTRUCTOR:
        alternativeText = ((TdApi.MessageChatChangeTitle) message.content).title;
        break;
      case TdApi.MessageChatSetMessageAutoDeleteTime.CONSTRUCTOR:
        arg1 = ((TdApi.MessageChatSetMessageAutoDeleteTime) message.content).messageAutoDeleteTime;
        break;
      case TdApi.MessageChatSetTheme.CONSTRUCTOR:
        alternativeText = ((TdApi.MessageChatSetTheme) message.content).themeName;
        break;
      case TdApi.MessageGiftedPremium.CONSTRUCTOR: {
        // TODO: R.string.ChatContent*
        TdApi.MessageGiftedPremium giftedPremium = (TdApi.MessageGiftedPremium) message.content;
        CharSequence text;
        if (message.isOutgoing) {
          text = Lang.pluralBold(R.string.YouGiftedPremium, giftedPremium.monthCount, CurrencyUtils.buildAmount(giftedPremium.currency, giftedPremium.amount));
        } else {
          text = Lang.pluralBold(R.string.GiftedPremium, giftedPremium.monthCount, tdlib.senderName(message.senderId, true), CurrencyUtils.buildAmount(giftedPremium.currency, giftedPremium.amount));
        }
        TdApi.FormattedText formatted = TD.toFormattedText(text, false);
        return new ContentPreview(EMOJI_GIFT, 0, formatted, true);
      }
      case TdApi.MessagePremiumGiftCode.CONSTRUCTOR: {
        // TODO: R.string.ChatContent*
        TdApi.MessagePremiumGiftCode giftedPremium = (TdApi.MessagePremiumGiftCode) message.content;
        CharSequence text;
        if (message.isOutgoing) {
          text = Lang.pluralBold(R.string.YouGiftedPremiumCode, giftedPremium.monthCount);
        } else {
          text = Lang.pluralBold(R.string.GiftedPremiumCode, giftedPremium.monthCount, tdlib.senderName(giftedPremium.creatorId, true));
        }
        TdApi.FormattedText formatted = TD.toFormattedText(text, false);
        return new ContentPreview(EMOJI_GIFT, 0, formatted, true);
      }
      case TdApi.MessagePremiumGiveaway.CONSTRUCTOR: {
        TdApi.MessagePremiumGiveaway premiumGiveaway = (TdApi.MessagePremiumGiveaway) message.content;
        String text;
        if (premiumGiveaway.winnerCount > 0) {
          text = Lang.getString(R.string.format_giveawayInfo,
            Lang.getString(R.string.Giveaway),
            Lang.plural(R.string.xFutureWinnersOn, premiumGiveaway.winnerCount, Lang.getDate(premiumGiveaway.parameters.winnersSelectionDate, TimeUnit.SECONDS))
          );
        } else {
          text = Lang.getString(R.string.Giveaway);
        }
        return new ContentPreview(EMOJI_GIFT, 0, text, true);
      }
      case TdApi.MessagePremiumGiveawayWinners.CONSTRUCTOR: {
        TdApi.MessagePremiumGiveawayWinners premiumGiveaway = (TdApi.MessagePremiumGiveawayWinners) message.content;
        String text;
        if (premiumGiveaway.winnerCount > 0) {
          text = Lang.getString(R.string.format_giveawayInfo,
            Lang.getString(R.string.Giveaway),
            Lang.plural(R.string.xPastWinnersOn, premiumGiveaway.winnerCount, Lang.getDate(premiumGiveaway.actualWinnersSelectionDate, TimeUnit.SECONDS))
          );
        } else {
          text = Lang.getString(R.string.Giveaway);
        }
        return new ContentPreview(EMOJI_GIFT, 0, text, true);
      }
      case TdApi.MessagePremiumGiveawayCompleted.CONSTRUCTOR: {
        TdApi.MessagePremiumGiveawayCompleted giveawayCompleted = (TdApi.MessagePremiumGiveawayCompleted) message.content;
        arg1 = giveawayCompleted.winnerCount;
        arg2 = giveawayCompleted.unclaimedPrizeCount;
        break;
      }

      case TdApi.MessageCustomServiceAction.CONSTRUCTOR: {
        TdApi.MessageCustomServiceAction serviceAction = (TdApi.MessageCustomServiceAction) message.content;
        return new ContentPreview(EMOJI_INFO, 0, serviceAction.text);
      }
      case TdApi.MessageBotWriteAccessAllowed.CONSTRUCTOR: {
        TdApi.MessageBotWriteAccessAllowed writeAccessAllowed = (TdApi.MessageBotWriteAccessAllowed) message.content;
        TdApi.BotWriteAccessAllowReason reason = writeAccessAllowed.reason;
        CharSequence text;
        switch (reason.getConstructor()) {
          case TdApi.BotWriteAccessAllowReasonConnectedWebsite.CONSTRUCTOR: {
            TdApi.BotWriteAccessAllowReasonConnectedWebsite connectedWebsite = (TdApi.BotWriteAccessAllowReasonConnectedWebsite) reason;
            text = Lang.getStringBold(R.string.BotWebappAllowed, connectedWebsite.domainName);
            break;
          }
          case TdApi.BotWriteAccessAllowReasonAddedToAttachmentMenu.CONSTRUCTOR: {
            text = Lang.getString(R.string.BotAttachAllowed);
            break;
          }
          case TdApi.BotWriteAccessAllowReasonLaunchedWebApp.CONSTRUCTOR: {
            TdApi.BotWriteAccessAllowReasonLaunchedWebApp launchedWebApp = (TdApi.BotWriteAccessAllowReasonLaunchedWebApp) reason;
            text = Lang.getStringBold(R.string.BotWebappAllowed, launchedWebApp.webApp.title);
            break;
          }
          case TdApi.BotWriteAccessAllowReasonAcceptedRequest.CONSTRUCTOR: {
            text = Lang.getString(R.string.BotAppAllowed);
            break;
          }
          default: {
            Td.assertBotWriteAccessAllowReason_d7597302();
            throw Td.unsupported(reason);
          }
        }
        formattedText = TD.toFormattedText(text, false);
        return new ContentPreview(EMOJI_INFO, 0, formattedText, true);
      }
      case TdApi.MessageWebAppDataSent.CONSTRUCTOR: {
        TdApi.MessageWebAppDataSent webAppDataSent = (TdApi.MessageWebAppDataSent) message.content;
        return new ContentPreview(EMOJI_INFO, 0, Lang.getString(R.string.BotDataSent, webAppDataSent.buttonText), true);
      }
      case TdApi.MessagePaymentSuccessful.CONSTRUCTOR: {
        TdApi.MessagePaymentSuccessful successful = (TdApi.MessagePaymentSuccessful) message.content;
        return new ContentPreview(EMOJI_INVOICE, 0, Lang.getString(R.string.PaymentSuccessfullyPaidNoItem, CurrencyUtils.buildAmount(successful.currency, successful.totalAmount), tdlib.chatTitle(message.chatId)), true);
      }

      // Handled by getSimpleContentPreview
      case TdApi.MessageVenue.CONSTRUCTOR:
      case TdApi.MessageScreenshotTaken.CONSTRUCTOR:
      case TdApi.MessageExpiredPhoto.CONSTRUCTOR:
      case TdApi.MessageExpiredVideo.CONSTRUCTOR:
      case TdApi.MessageContactRegistered.CONSTRUCTOR:

      case TdApi.MessageChatUpgradeFrom.CONSTRUCTOR:
      case TdApi.MessageChatUpgradeTo.CONSTRUCTOR:
      case TdApi.MessageBasicGroupChatCreate.CONSTRUCTOR:
      case TdApi.MessageSupergroupChatCreate.CONSTRUCTOR:
      case TdApi.MessageChatJoinByRequest.CONSTRUCTOR:
      case TdApi.MessageChatJoinByLink.CONSTRUCTOR:
      case TdApi.MessageChatChangePhoto.CONSTRUCTOR:
      case TdApi.MessageChatDeletePhoto.CONSTRUCTOR:
      case TdApi.MessagePremiumGiveawayCreated.CONSTRUCTOR:

      // Handled by getSimpleContentPreview, but unsupported
      case TdApi.MessageUnsupported.CONSTRUCTOR:
      case TdApi.MessageUsersShared.CONSTRUCTOR:
      case TdApi.MessageChatShared.CONSTRUCTOR:
      case TdApi.MessageSuggestProfilePhoto.CONSTRUCTOR:
      case TdApi.MessageForumTopicCreated.CONSTRUCTOR:
      case TdApi.MessageForumTopicEdited.CONSTRUCTOR:
      case TdApi.MessageForumTopicIsClosedToggled.CONSTRUCTOR:
      case TdApi.MessageForumTopicIsHiddenToggled.CONSTRUCTOR:
      case TdApi.MessagePassportDataSent.CONSTRUCTOR:
      case TdApi.MessageChatSetBackground.CONSTRUCTOR:
        break;

      // Bots only. Unused
      case TdApi.MessagePassportDataReceived.CONSTRUCTOR:
      case TdApi.MessagePaymentSuccessfulBot.CONSTRUCTOR:
      case TdApi.MessageWebAppDataReceived.CONSTRUCTOR:
      default:
        Td.assertMessageContent_d40af239();
        throw Td.unsupported(message.content);
    }
    Refresher refresher = null;
    if (message.mediaAlbumId != 0 && TD.getCombineMode(message) != TD.COMBINE_MODE_NONE) {
      refresher = (oldContent, callback) -> tdlib.getAlbum(message, true, null, localAlbum -> {
        if (localAlbum.messages.size() == 1 && !localAlbum.mayHaveMoreItems()) {
          callback.onContentPreviewNotChanged(message.chatId, message.id, oldContent);
        } else {
          ContentPreview newPreview = getAlbumPreview(tdlib, message, localAlbum, allowContent);
          if (localAlbum.messages.size() == 1) {
            if (newPreview.hasRefresher()) {
              newPreview.refreshContent(callback);
            } else {
              callback.onContentPreviewNotChanged(message.chatId, message.id, oldContent);
            }
          } else {
            callback.onContentPreviewChanged(message.chatId, message.id, newPreview, oldContent);
          }
        }
      });
    }
    TdApi.FormattedText argument;
    boolean argumentTranslatable;
    if (Td.isEmpty(formattedText)) {
      argument = new TdApi.FormattedText(alternativeText, null);
      argumentTranslatable = alternativeTextTranslatable;
    } else {
      argument = formattedText;
      argumentTranslatable = false;
    }
    ContentPreview preview = getSimpleContentPreview(message.content.getConstructor(), tdlib, chatId, message.senderId, null, !message.isChannelPost && message.isOutgoing, isChatList, argument, argumentTranslatable, arg1, arg2);
    if (refresher != null) {
      preview.setRefresher(refresher, true);
    }
    return preview;
  }

  public static ContentPreview getAlbumPreview (Tdlib tdlib, TdApi.Message message, Tdlib.Album album, boolean allowContent) {
    SparseIntArray counters = new SparseIntArray();
    for (TdApi.Message m : album.messages) {
      ArrayUtils.increment(counters, m.content.getConstructor());
    }
    int textRes;
    Emoji emoji;
    switch (counters.size() == 1 ? counters.keyAt(0) : 0) {
      case TdApi.MessagePhoto.CONSTRUCTOR:
        textRes = R.string.xPhotos;
        emoji = EMOJI_ALBUM_PHOTOS;
        break;
      case TdApi.MessageVideo.CONSTRUCTOR:
        textRes = R.string.xVideos;
        emoji = EMOJI_ALBUM_VIDEOS;
        break;
      case TdApi.MessageDocument.CONSTRUCTOR:
        textRes = R.string.xFiles;
        emoji = EMOJI_ALBUM_FILES;
        break;
      case TdApi.MessageAudio.CONSTRUCTOR:
        textRes = R.string.xAudios;
        emoji = EMOJI_ALBUM_AUDIO;
        break;
      default:
        textRes = R.string.xMedia;
        emoji = EMOJI_ALBUM_MEDIA;
        break;
    }
    TdApi.Message captionMessage = allowContent ? getAlbumCaptionMessage(tdlib, album.messages) : null;
    TdApi.FormattedText formattedCaption = captionMessage != null ? Td.textOrCaption(captionMessage.content) : null;
    ContentPreview preview = new ContentPreview(emoji, 0, Td.isEmpty(formattedCaption) ? new TdApi.FormattedText(Lang.plural(textRes, album.messages.size()), null) : formattedCaption, Td.isEmpty(formattedCaption));
    preview.album = album;
    if (album.mayHaveMoreItems()) {
      preview.setRefresher((oldPreview, callback) ->
        tdlib.getAlbum(message, false, album, remoteAlbum -> {
          if (remoteAlbum.messages.size() > album.messages.size()) {
            callback.onContentPreviewChanged(message.chatId, message.id, getAlbumPreview(tdlib, message, remoteAlbum, allowContent), oldPreview);
          } else {
            callback.onContentPreviewNotChanged(message.chatId, message.id, oldPreview);
          }
        }), true
      );
    }
    return preview;
  }

  public static TdApi.Message getAlbumCaptionMessage (Tdlib tdlib, List<TdApi.Message> messages) {
    TdApi.Message captionMessage = null;
    for (TdApi.Message message : messages) {
      TdApi.FormattedText currentCaption = tdlib.getFormattedText(message);
      if (!Td.isEmpty(currentCaption)) {
        if (captionMessage != null) {
          captionMessage = null;
          break;
        } else {
          captionMessage = message;
        }
      }
    }
    return captionMessage;
  }

  private static ContentPreview getNotificationPinned(int res, int type, Tdlib tdlib, long chatId, TdApi.MessageSender sender, String senderName, String argument) {
    return getNotificationPinned(res, type, tdlib, chatId, sender, argument, senderName, false, 0, 0);
  }

  private static ContentPreview getNotificationPinned(int res, int type, Tdlib tdlib, long chatId, TdApi.MessageSender sender, String senderName, String argument, boolean argumentTranslatable) {
    return getNotificationPinned(res, type, tdlib, chatId, sender, argument, senderName, argumentTranslatable, 0, 0);
  }

  private static ContentPreview getNotificationPinned(int res, int type, Tdlib tdlib, long chatId, TdApi.MessageSender sender, String senderName, String argument, int arg1, int arg2) {
    return getNotificationPinned(res, type, tdlib, chatId, sender, argument, senderName, false, arg1, arg2);
  }

  private static ContentPreview getNotificationPinned (int res, int type, Tdlib tdlib, long chatId, TdApi.MessageSender sender, String senderName, String argument, boolean argumentTranslatable, int arg1, int arg2) {
    String text;
    if (StringUtils.isEmpty(argument)) {
      try {
        text = Lang.formatString(Strings.replaceBoldTokens(Lang.getString(res)).toString(), null, getSenderName(tdlib, sender, senderName)).toString();
      } catch (Throwable t) {
        text = Lang.getString(res);
      }
    } else {
      ContentPreview contentPreview = getNotificationPreview(type, tdlib, chatId, sender, senderName, argument, argumentTranslatable, arg1, arg2);
      String preview = contentPreview != null ? contentPreview.toString() : null;
      if (StringUtils.isEmpty(preview)) {
        preview = argument;
      }
      try {
        text = Lang.formatString(Strings.replaceBoldTokens(Lang.getString(R.string.ActionPinnedText)).toString(), null, getSenderName(tdlib, sender, senderName), preview).toString();
      } catch (Throwable t) {
        text = Lang.getString(R.string.ActionPinnedText);
      }
    }
    // TODO icon?
    return new ContentPreview(null, 0, new TdApi.FormattedText(text, null), true, true, EMOJI_PIN);
  }

  public static @NonNull ContentPreview getNotificationPreview (Tdlib tdlib, long chatId, TdApi.NotificationTypeNewPushMessage push, boolean allowContent) {
    switch (push.content.getConstructor()) {
      case TdApi.PushMessageContentHidden.CONSTRUCTOR:
        if (((TdApi.PushMessageContentHidden) push.content).isPinned)
          return getNotificationPinned(R.string.ActionPinnedNoText, TdApi.MessageText.CONSTRUCTOR, tdlib, chatId, push.senderId, push.senderName, null);
        else
          return new ContentPreview(Lang.plural(R.string.xNewMessages, 1), true);

      case TdApi.PushMessageContentText.CONSTRUCTOR:
        if (((TdApi.PushMessageContentText) push.content).isPinned)
          return getNotificationPinned(R.string.ActionPinnedNoText, TdApi.MessageText.CONSTRUCTOR, tdlib, chatId, push.senderId, push.senderName, ((TdApi.PushMessageContentText) push.content).text);
        else
          return getNotificationPreview(TdApi.MessageText.CONSTRUCTOR, tdlib, chatId, push.senderId, push.senderName, ((TdApi.PushMessageContentText) push.content).text);

      case TdApi.PushMessageContentMessageForwards.CONSTRUCTOR:
        return new ContentPreview(Lang.plural(R.string.xForwards, ((TdApi.PushMessageContentMessageForwards) push.content).totalCount), true);

      case TdApi.PushMessageContentPhoto.CONSTRUCTOR: {
        String caption = ((TdApi.PushMessageContentPhoto) push.content).caption;
        if (((TdApi.PushMessageContentPhoto) push.content).isPinned)
          return getNotificationPinned(R.string.ActionPinnedPhoto, TdApi.MessagePhoto.CONSTRUCTOR, tdlib, chatId, push.senderId, push.senderName, caption);
        else if (((TdApi.PushMessageContentPhoto) push.content).isSecret)
          return new ContentPreview(EMOJI_SECRET_PHOTO, R.string.SelfDestructPhoto, caption, false);
        else
          return getNotificationPreview(TdApi.MessagePhoto.CONSTRUCTOR, tdlib, chatId, push.senderId, push.senderName, caption);
      }

      case TdApi.PushMessageContentVideo.CONSTRUCTOR: {
        String caption = ((TdApi.PushMessageContentVideo) push.content).caption;
        if (((TdApi.PushMessageContentVideo) push.content).isPinned)
          return getNotificationPinned(R.string.ActionPinnedVideo, TdApi.MessageVideo.CONSTRUCTOR, tdlib, chatId, push.senderId, push.senderName, caption);
        else if (((TdApi.PushMessageContentVideo) push.content).isSecret)
          return new ContentPreview(EMOJI_SECRET_VIDEO, R.string.SelfDestructVideo, caption);
        else
          return getNotificationPreview(TdApi.MessageVideo.CONSTRUCTOR, tdlib, chatId, push.senderId, push.senderName, caption);
      }

      case TdApi.PushMessageContentAnimation.CONSTRUCTOR: {
        String caption = ((TdApi.PushMessageContentAnimation) push.content).caption;
        if (((TdApi.PushMessageContentAnimation) push.content).isPinned)
          return getNotificationPinned(R.string.ActionPinnedGif, TdApi.MessageAnimation.CONSTRUCTOR, tdlib, chatId, push.senderId, push.senderName, caption);
        else
          return getNotificationPreview(TdApi.MessageAnimation.CONSTRUCTOR, tdlib, chatId, push.senderId, push.senderName, caption);
      }

      case TdApi.PushMessageContentDocument.CONSTRUCTOR: {
        TdApi.Document media = ((TdApi.PushMessageContentDocument) push.content).document;
        String caption = null; // FIXME server ((TdApi.PushMessageContentDocument) push.content).caption;
        if (StringUtils.isEmpty(caption) && media != null) {
          caption = media.fileName;
        }
        if (((TdApi.PushMessageContentDocument) push.content).isPinned)
          return getNotificationPinned(R.string.ActionPinnedFile, TdApi.MessageDocument.CONSTRUCTOR, tdlib, chatId, push.senderId, push.senderName, caption);
        else
          return getNotificationPreview(TdApi.MessageDocument.CONSTRUCTOR, tdlib, chatId, push.senderId, push.senderName, caption);
      }

      case TdApi.PushMessageContentSticker.CONSTRUCTOR:
        if (((TdApi.PushMessageContentSticker) push.content).isPinned)
          return getNotificationPinned(R.string.ActionPinnedSticker, TdApi.MessageSticker.CONSTRUCTOR, tdlib, chatId, push.senderId, push.senderName, ((TdApi.PushMessageContentSticker) push.content).emoji);
        else if (((TdApi.PushMessageContentSticker) push.content).sticker != null && Td.isAnimated(((TdApi.PushMessageContentSticker) push.content).sticker.format))
          return getNotificationPreview(TdApi.MessageSticker.CONSTRUCTOR, tdlib, chatId, push.senderId, push.senderName, "animated" + ((TdApi.PushMessageContentSticker) push.content).emoji);
        else
          return getNotificationPreview(TdApi.MessageSticker.CONSTRUCTOR, tdlib, chatId, push.senderId, push.senderName, ((TdApi.PushMessageContentSticker) push.content).emoji);

      case TdApi.PushMessageContentLocation.CONSTRUCTOR:
        if (((TdApi.PushMessageContentLocation) push.content).isLive) {
          if (((TdApi.PushMessageContentLocation) push.content).isPinned)
            return getNotificationPinned(R.string.ActionPinnedGeoLive, TdApi.MessageLocation.CONSTRUCTOR, tdlib, chatId, push.senderId, push.senderName, null);
          else
            return getNotificationPreview(TdApi.MessageLocation.CONSTRUCTOR, tdlib, chatId, push.senderId, push.senderName, "live");
        } else {
          if (((TdApi.PushMessageContentLocation) push.content).isPinned)
            return getNotificationPinned(R.string.ActionPinnedGeo, TdApi.MessageLocation.CONSTRUCTOR, tdlib, chatId, push.senderId, push.senderName, null);
          else
            return getNotificationPreview(TdApi.MessageLocation.CONSTRUCTOR, tdlib, chatId, push.senderId, push.senderName, null);
        }

      case TdApi.PushMessageContentPoll.CONSTRUCTOR:
        if (((TdApi.PushMessageContentPoll) push.content).isPinned)
          return getNotificationPinned(((TdApi.PushMessageContentPoll) push.content).isRegular ? R.string.ActionPinnedPoll : R.string.ActionPinnedQuiz, TdApi.MessagePoll.CONSTRUCTOR, tdlib, chatId, push.senderId, push.senderName, ((TdApi.PushMessageContentPoll) push.content).question, ((TdApi.PushMessageContentPoll) push.content).isRegular ? ARG_NONE : ARG_POLL_QUIZ, 0);
        else
          return getNotificationPreview(TdApi.MessagePoll.CONSTRUCTOR, tdlib, chatId, push.senderId, push.senderName, ((TdApi.PushMessageContentPoll) push.content).question, ((TdApi.PushMessageContentPoll) push.content).isRegular ? ARG_NONE : ARG_POLL_QUIZ, 0);

      case TdApi.PushMessageContentAudio.CONSTRUCTOR: {
        TdApi.Audio audio = ((TdApi.PushMessageContentAudio) push.content).audio;
        String caption = null; // FIXME server ((TdApi.PushMessageContentAudio) push.content).caption;
        boolean translatable = false;
        if (StringUtils.isEmpty(caption) && audio != null) {
          caption = Lang.getString(R.string.ChatContentSong, TD.getTitle(audio), TD.getSubtitle(audio));
          translatable = !TD.hasTitle(audio) || !TD.hasSubtitle(audio);
        }
        if (((TdApi.PushMessageContentAudio) push.content).isPinned)
          return getNotificationPinned(R.string.ActionPinnedMusic, TdApi.MessageAudio.CONSTRUCTOR, tdlib, chatId, push.senderId, push.senderName, caption, translatable);
        else
          return getNotificationPreview(TdApi.MessageAudio.CONSTRUCTOR, tdlib, chatId, push.senderId, push.senderName, caption, translatable);
      }

      case TdApi.PushMessageContentVideoNote.CONSTRUCTOR: {
        String argument = null;
        boolean argumentTranslatable = false;
        TdApi.VideoNote videoNote = ((TdApi.PushMessageContentVideoNote) push.content).videoNote;
        if (videoNote != null && videoNote.duration > 0) {
          argument = Lang.getString(R.string.ChatContentVoiceDuration, Lang.getString(R.string.ChatContentRoundVideo), Strings.buildDuration(videoNote.duration));
          argumentTranslatable = true;
        }
        if (((TdApi.PushMessageContentVideoNote) push.content).isPinned)
          return getNotificationPinned(R.string.ActionPinnedRound, TdApi.MessageVideoNote.CONSTRUCTOR, tdlib, chatId, push.senderId, push.senderName, argument, argumentTranslatable);
        else
          return getNotificationPreview(TdApi.MessageVideoNote.CONSTRUCTOR, tdlib, chatId, push.senderId, push.senderName, argument, argumentTranslatable);
      }

      case TdApi.PushMessageContentStory.CONSTRUCTOR: {
        if (((TdApi.PushMessageContentStory) push.content).isPinned) {
          return getNotificationPinned(R.string.ActionPinnedStory, TdApi.MessageStory.CONSTRUCTOR, tdlib, chatId, push.senderId, push.senderName, null);
        } else {
          return getNotificationPreview(TdApi.MessageStory.CONSTRUCTOR, tdlib, chatId, push.senderId, push.senderName, null);
        }
      }

      case TdApi.PushMessageContentVoiceNote.CONSTRUCTOR: {
        String argument = null; // FIXME server ((TdApi.PushMessageContentVoiceNote) push.content).caption;
        boolean argumentTranslatable = false;
        if (StringUtils.isEmpty(argument)) {
          TdApi.VoiceNote voiceNote = ((TdApi.PushMessageContentVoiceNote) push.content).voiceNote;
          if (voiceNote != null && voiceNote.duration > 0) {
            argument = Lang.getString(R.string.ChatContentVoiceDuration, Lang.getString(R.string.ChatContentVoice), Strings.buildDuration(voiceNote.duration));
            argumentTranslatable = true;
          }
        }
        if (((TdApi.PushMessageContentVoiceNote) push.content).isPinned)
          return getNotificationPinned(R.string.ActionPinnedVoice, TdApi.MessageVoiceNote.CONSTRUCTOR, tdlib, chatId, push.senderId, push.senderName, argument, argumentTranslatable);
        else
          return getNotificationPreview(TdApi.MessageVoiceNote.CONSTRUCTOR, tdlib, chatId, push.senderId, push.senderName, argument, argumentTranslatable);
      }

      case TdApi.PushMessageContentGame.CONSTRUCTOR:
        if (((TdApi.PushMessageContentGame) push.content).isPinned) {
          String gameTitle = ((TdApi.PushMessageContentGame) push.content).title;
          return getNotificationPinned(StringUtils.isEmpty(gameTitle) ? R.string.ActionPinnedGameNoName : R.string.ActionPinnedGame, TdApi.MessageGame.CONSTRUCTOR, tdlib, chatId, push.senderId, push.senderName, StringUtils.isEmpty(gameTitle) ? null : gameTitle);
        } else
          return getNotificationPreview(TdApi.MessageGame.CONSTRUCTOR, tdlib, chatId, push.senderId, push.senderName, ((TdApi.PushMessageContentGame) push.content).title);

      case TdApi.PushMessageContentContact.CONSTRUCTOR:
        if (((TdApi.PushMessageContentContact) push.content).isPinned)
          return getNotificationPinned(R.string.ActionPinnedContact, TdApi.MessageContact.CONSTRUCTOR, tdlib, chatId, push.senderId, push.senderName, ((TdApi.PushMessageContentContact) push.content).name);
        else
          return getNotificationPreview(TdApi.MessageContact.CONSTRUCTOR, tdlib, chatId, push.senderId, push.senderName, ((TdApi.PushMessageContentContact) push.content).name);

      case TdApi.PushMessageContentInvoice.CONSTRUCTOR:
        if (((TdApi.PushMessageContentInvoice) push.content).isPinned)
          return getNotificationPinned(R.string.ActionPinnedNoText, TdApi.MessageInvoice.CONSTRUCTOR, tdlib, chatId, push.senderId, push.senderName, null); // TODO
        else
          return getNotificationPreview(TdApi.MessageInvoice.CONSTRUCTOR, tdlib, chatId, push.senderId, push.senderName, ((TdApi.PushMessageContentInvoice) push.content).price);

      case TdApi.PushMessageContentScreenshotTaken.CONSTRUCTOR:
        return getNotificationPreview(TdApi.MessageScreenshotTaken.CONSTRUCTOR, tdlib, chatId, push.senderId, push.senderName, null);

      case TdApi.PushMessageContentGameScore.CONSTRUCTOR: {
        TdApi.PushMessageContentGameScore score = (TdApi.PushMessageContentGameScore) push.content;
        if (score.isPinned) {
          return getNotificationPinned(R.string.ActionPinnedNoText, TdApi.MessageGameScore.CONSTRUCTOR, tdlib, chatId, push.senderId, push.senderName, null); // TODO
        } else {
          String gameTitle = score.title;
          if (!StringUtils.isEmpty(gameTitle))
            return new ContentPreview(EMOJI_GAME, 0, Lang.plural(R.string.game_ActionScoredInGame, score.score, gameTitle), true);
          else
            return new ContentPreview(EMOJI_GAME, 0, Lang.plural(R.string.game_ActionScored, score.score), true);
        }
      }

      case TdApi.PushMessageContentContactRegistered.CONSTRUCTOR:
        return getNotificationPreview(TdApi.MessageContactRegistered.CONSTRUCTOR, tdlib, chatId, push.senderId, push.senderName, null);

      case TdApi.PushMessageContentMediaAlbum.CONSTRUCTOR: {
        TdApi.PushMessageContentMediaAlbum album = ((TdApi.PushMessageContentMediaAlbum) push.content);
        int mediaTypeCount = 0;
        if (album.hasPhotos)
          mediaTypeCount++;
        if (album.hasVideos)
          mediaTypeCount++;
        if (album.hasAudios)
          mediaTypeCount++;
        if (album.hasDocuments)
          mediaTypeCount++;
        if (mediaTypeCount > 1 || mediaTypeCount == 0) {
          return new ContentPreview(EMOJI_ALBUM_MEDIA, 0, Lang.plural(R.string.xMedia, album.totalCount), true);
        } else if (album.hasDocuments) {
          return new ContentPreview(EMOJI_ALBUM_FILES, 0, Lang.plural(R.string.xFiles, album.totalCount), true);
        } else if (album.hasAudios) {
          return new ContentPreview(EMOJI_ALBUM_AUDIO, 0, Lang.plural(R.string.xAudios, album.totalCount), true);
        } else if (album.hasVideos) {
          return new ContentPreview(EMOJI_ALBUM_VIDEOS, 0, Lang.plural(R.string.xVideos, album.totalCount), true);
        } else {
          return new ContentPreview(EMOJI_ALBUM_PHOTOS, 0, Lang.plural(R.string.xPhotos, album.totalCount), true);
        }
      }

      case TdApi.PushMessageContentBasicGroupChatCreate.CONSTRUCTOR:
        return getNotificationPreview(TdApi.MessageBasicGroupChatCreate.CONSTRUCTOR, tdlib, chatId, push.senderId, push.senderName, null);

      case TdApi.PushMessageContentChatAddMembers.CONSTRUCTOR: {
        TdApi.PushMessageContentChatAddMembers info = (TdApi.PushMessageContentChatAddMembers) push.content;
        if (info.isReturned) {
          return new ContentPreview(EMOJI_GROUP, R.string.ChatContentGroupReturn);
        } else if (info.isCurrentUser) {
          return new ContentPreview(EMOJI_GROUP, R.string.ChatContentGroupAddYou);
        } else {
          return new ContentPreview(EMOJI_GROUP, 0, Lang.getString(R.string.ChatContentGroupAdd, info.memberName), true);
        }
      }

      case TdApi.PushMessageContentChatDeleteMember.CONSTRUCTOR: {
        TdApi.PushMessageContentChatDeleteMember info = (TdApi.PushMessageContentChatDeleteMember) push.content;
        if (info.isLeft) {
          return new ContentPreview(EMOJI_GROUP, R.string.ChatContentGroupLeft);
        } else if (info.isCurrentUser) {
          return new ContentPreview(EMOJI_GROUP, R.string.ChatContentGroupKickYou);
        } else {
          return new ContentPreview(EMOJI_GROUP, 0, Lang.getString(R.string.ChatContentGroupKick, info.memberName), true);
        }
      }

      case TdApi.PushMessageContentChatJoinByLink.CONSTRUCTOR:
        return getNotificationPreview(TdApi.MessageChatJoinByLink.CONSTRUCTOR, tdlib, chatId, push.senderId, push.senderName, null);
      case TdApi.PushMessageContentChatJoinByRequest.CONSTRUCTOR:
        return getNotificationPreview(TdApi.MessageChatJoinByRequest.CONSTRUCTOR, tdlib, chatId, push.senderId, push.senderName, null);
      case TdApi.PushMessageContentRecurringPayment.CONSTRUCTOR:
        return getNotificationPreview(TdApi.MessageInvoice.CONSTRUCTOR, tdlib, chatId, push.senderId, push.senderName, ((TdApi.PushMessageContentRecurringPayment) push.content).amount, ARG_RECURRING_PAYMENT, 0);

      case TdApi.PushMessageContentChatChangePhoto.CONSTRUCTOR:
        return getNotificationPreview(TdApi.MessageChatChangePhoto.CONSTRUCTOR, tdlib, chatId, push.senderId, push.senderName, null); // FIXME Server: Missing isRemoved

      case TdApi.PushMessageContentChatChangeTitle.CONSTRUCTOR:
        return getNotificationPreview(TdApi.MessageChatChangeTitle.CONSTRUCTOR, tdlib, chatId, push.senderId, push.senderName, ((TdApi.PushMessageContentChatChangeTitle) push.content).title);

      case TdApi.PushMessageContentChatSetTheme.CONSTRUCTOR:
        return getNotificationPreview(TdApi.MessageChatSetTheme.CONSTRUCTOR, tdlib, chatId, push.senderId, push.senderName, ((TdApi.PushMessageContentChatSetTheme) push.content).themeName);
      case TdApi.PushMessageContentChatSetBackground.CONSTRUCTOR:
        return getNotificationPreview(TdApi.MessageChatSetBackground.CONSTRUCTOR, tdlib, chatId, push.senderId, push.senderName, null, ((TdApi.PushMessageContentChatSetBackground) push.content).isSame ? ARG_TRUE : ARG_NONE, 0);
      case TdApi.PushMessageContentSuggestProfilePhoto.CONSTRUCTOR:
        return getNotificationPreview(TdApi.MessageSuggestProfilePhoto.CONSTRUCTOR, tdlib, chatId, push.senderId, push.senderName, null);

      case TdApi.PushMessageContentPremiumGiftCode.CONSTRUCTOR:
        return getNotificationPreview(TdApi.MessagePremiumGiftCode.CONSTRUCTOR, tdlib, chatId, push.senderId, push.senderName, null, ((TdApi.PushMessageContentPremiumGiftCode) push.content).monthCount, 0);
      case TdApi.PushMessageContentPremiumGiveaway.CONSTRUCTOR: {
        TdApi.PushMessageContentPremiumGiveaway giveaway = (TdApi.PushMessageContentPremiumGiveaway) push.content;
        if (giveaway.isPinned) {
          return getNotificationPinned(R.string.ActionPinnedGiveaway, TdApi.MessagePremiumGiveaway.CONSTRUCTOR, tdlib, chatId, push.senderId, push.senderName, null, giveaway.winnerCount, giveaway.monthCount);
        } else {
          return getNotificationPreview(TdApi.MessagePremiumGiveaway.CONSTRUCTOR, tdlib, chatId, push.senderId, push.senderName, null, giveaway.winnerCount, giveaway.monthCount);
        }
      }
      default:
        Td.assertPushMessageContent_b17e0a62();
        throw Td.unsupported(push.content);
    }
  }

  private static ContentPreview getNotificationPreview (@TdApi.MessageContent.Constructors int type, Tdlib tdlib, long chatId, TdApi.MessageSender sender, String senderName, String argument, boolean argumentTranslatable, int arg1, int arg2) {
    return getSimpleContentPreview(type, tdlib, chatId, sender, senderName, tdlib.isSelfSender(sender), false, new TdApi.FormattedText(argument, null), argumentTranslatable, arg1, arg2);
  }

  private static ContentPreview getNotificationPreview (@TdApi.MessageContent.Constructors int type, Tdlib tdlib, long chatId, TdApi.MessageSender sender, String senderName, String argument, boolean argumentTranslatable) {
    return getSimpleContentPreview(type, tdlib, chatId, sender, senderName, tdlib.isSelfSender(sender), false, new TdApi.FormattedText(argument, null), argumentTranslatable, 0, 0);
  }

  private static ContentPreview getNotificationPreview (@TdApi.MessageContent.Constructors int type, Tdlib tdlib, long chatId, TdApi.MessageSender sender, String senderName, String argument) {
    return getSimpleContentPreview(type, tdlib, chatId, sender, senderName, tdlib.isSelfSender(sender), false, new TdApi.FormattedText(argument, null), false, 0, 0);
  }

  private static ContentPreview getNotificationPreview (@TdApi.MessageContent.Constructors int type, Tdlib tdlib, long chatId, TdApi.MessageSender sender, String senderName, String argument, int arg1, int arg2) {
    return getSimpleContentPreview(type, tdlib, chatId, sender, senderName, tdlib.isSelfSender(sender), false, new TdApi.FormattedText(argument, null), false, arg1, arg2);
  }

  private static String getSenderName (Tdlib tdlib, TdApi.MessageSender sender, String senderName) {
    return StringUtils.isEmpty(senderName) ? tdlib.senderName(sender, true) : senderName;
  }

  private static @NonNull ContentPreview getSimpleContentPreview (@TdApi.MessageContent.Constructors int type, Tdlib tdlib, long chatId, TdApi.MessageSender sender, String senderName, boolean isOutgoing, boolean isChatsList, TdApi.FormattedText formattedArgument, boolean argumentTranslatable, int arg1, int arg2) {
    switch (type) {
      case TdApi.MessageText.CONSTRUCTOR:
        return new ContentPreview(arg1 == ARG_TRUE ? EMOJI_LINK : null, R.string.YouHaveNewMessage, formattedArgument, argumentTranslatable);
      case TdApi.MessageAnimatedEmoji.CONSTRUCTOR:
        return new ContentPreview(null, R.string.YouHaveNewMessage, formattedArgument, argumentTranslatable);
      case TdApi.MessagePhoto.CONSTRUCTOR:
        return new ContentPreview(EMOJI_PHOTO, R.string.ChatContentPhoto, formattedArgument, argumentTranslatable);
      case TdApi.MessageVideo.CONSTRUCTOR:
        return new ContentPreview(EMOJI_VIDEO, R.string.ChatContentVideo, formattedArgument, argumentTranslatable);
      case TdApi.MessageDocument.CONSTRUCTOR:
        return new ContentPreview(EMOJI_FILE, R.string.ChatContentFile, formattedArgument, argumentTranslatable);
      case TdApi.MessageAudio.CONSTRUCTOR:
        return new ContentPreview(EMOJI_AUDIO, 0, formattedArgument, argumentTranslatable); // FIXME: does it need a placeholder or argument is always non-null?
      case TdApi.MessageContact.CONSTRUCTOR:
        return new ContentPreview(EMOJI_CONTACT, R.string.AttachContact, formattedArgument, argumentTranslatable);
      case TdApi.MessagePoll.CONSTRUCTOR:
        if (arg1 == ARG_POLL_QUIZ)
          return new ContentPreview(EMOJI_QUIZ, R.string.Quiz, formattedArgument, argumentTranslatable);
        else
          return new ContentPreview(EMOJI_POLL, R.string.Poll, formattedArgument, argumentTranslatable);
      case TdApi.MessageVoiceNote.CONSTRUCTOR:
        return new ContentPreview(EMOJI_VOICE, R.string.ChatContentVoice, formattedArgument, argumentTranslatable);
      case TdApi.MessageVideoNote.CONSTRUCTOR:
        return new ContentPreview(EMOJI_ROUND_VIDEO, R.string.ChatContentRoundVideo, formattedArgument, argumentTranslatable);
      case TdApi.MessageAnimation.CONSTRUCTOR:
        return new ContentPreview(EMOJI_GIF, R.string.ChatContentAnimation, formattedArgument, argumentTranslatable);
      case TdApi.MessageLocation.CONSTRUCTOR:
        return new ContentPreview(EMOJI_LOCATION, "live".equals(Td.getText(formattedArgument)) ? R.string.AttachLiveLocation : R.string.Location);
      case TdApi.MessageVenue.CONSTRUCTOR:
        return new ContentPreview(EMOJI_LOCATION, R.string.Location);
      case TdApi.MessageSticker.CONSTRUCTOR: {
        String emoji = Td.getText(formattedArgument);
        boolean isAnimated = false;
        if (emoji != null && emoji.startsWith("animated")) {
          emoji = emoji.substring("animated".length());
          isAnimated = true;
        }
        return new ContentPreview(StringUtils.isEmpty(emoji) ? null : new Emoji(emoji, 0), isAnimated && !isChatsList ? R.string.AnimatedSticker : R.string.Sticker);
      }
      case TdApi.MessageScreenshotTaken.CONSTRUCTOR:
        if (isOutgoing)
          return new ContentPreview(EMOJI_SCREENSHOT, R.string.YouTookAScreenshot);
        else if (isChatsList)
          return new ContentPreview(EMOJI_SCREENSHOT, R.string.ChatContentScreenshot);
        else
          return new ContentPreview(EMOJI_SCREENSHOT, 0, Lang.getString(R.string.XTookAScreenshot, getSenderName(tdlib, sender, senderName)), true);
      case TdApi.MessageGame.CONSTRUCTOR:
        return new ContentPreview(EMOJI_GAME, 0, Lang.getString(ChatId.isMultiChat(chatId) ? (isOutgoing ? R.string.NotificationGame_group_outgoing : R.string.NotificationGame_group) : (isOutgoing ? R.string.NotificationGame_outgoing : R.string.NotificationGame), Td.getText(formattedArgument)), true);
      case TdApi.MessageInvoice.CONSTRUCTOR:
        if (arg1 == ARG_RECURRING_PAYMENT) {
          return new ContentPreview(EMOJI_INVOICE, R.string.RecurringPayment, Td.isEmpty(formattedArgument) ? null : Lang.getString(R.string.PaidX, Td.getText(formattedArgument)), true);
        } else {
          return new ContentPreview(EMOJI_INVOICE, R.string.Invoice, Td.isEmpty(formattedArgument) ? null : Lang.getString(R.string.InvoiceFor, Td.getText(formattedArgument)), true);
        }
      case TdApi.MessageContactRegistered.CONSTRUCTOR:
        return new ContentPreview(EMOJI_USER_JOINED, 0, Lang.getString(R.string.NotificationContactJoined, getSenderName(tdlib, sender, senderName)), true);
      case TdApi.MessageSupergroupChatCreate.CONSTRUCTOR:
        if (tdlib.isChannel(chatId))
          return new ContentPreview(EMOJI_CHANNEL, R.string.ActionCreateChannel);
        else
          return new ContentPreview(EMOJI_GROUP, isOutgoing ? R.string.ChatContentGroupCreate_outgoing : R.string.ChatContentGroupCreate);
      case TdApi.MessageBasicGroupChatCreate.CONSTRUCTOR:
        return new ContentPreview(EMOJI_GROUP, isOutgoing ? R.string.ChatContentGroupCreate_outgoing : R.string.ChatContentGroupCreate);
      case TdApi.MessageChatJoinByLink.CONSTRUCTOR:
        return new ContentPreview(EMOJI_GROUP, isOutgoing ? R.string.ChatContentGroupJoin_outgoing : R.string.ChatContentGroupJoin);
      case TdApi.MessageChatJoinByRequest.CONSTRUCTOR:
        return new ContentPreview(EMOJI_GROUP, isOutgoing ? R.string.ChatContentGroupAccept_outgoing : R.string.ChatContentGroupAccept);
      case TdApi.MessageChatChangePhoto.CONSTRUCTOR:
        if (tdlib.isChannel(chatId))
          return new ContentPreview(EMOJI_PHOTO, R.string.ActionChannelChangedPhoto);
        else
          return new ContentPreview(EMOJI_PHOTO, isOutgoing ? R.string.ChatContentGroupPhoto_outgoing : R.string.ChatContentGroupPhoto);
      case TdApi.MessageChatDeletePhoto.CONSTRUCTOR:
        if (tdlib.isChannel(chatId))
          return new ContentPreview(EMOJI_CHANNEL, R.string.ActionChannelRemovedPhoto);
        else
          return new ContentPreview(EMOJI_GROUP, isOutgoing ? R.string.ChatContentGroupPhotoRemove_outgoing : R.string.ChatContentGroupPhotoRemove);
      case TdApi.MessageChatChangeTitle.CONSTRUCTOR:
        if (tdlib.isChannel(chatId))
          return new ContentPreview(EMOJI_CHANNEL, 0, Lang.getString(R.string.ActionChannelChangedTitleTo, Td.getText(formattedArgument)), true);
        else
          return new ContentPreview(EMOJI_GROUP, 0, Lang.getString(isOutgoing ? R.string.ChatContentGroupName_outgoing : R.string.ChatContentGroupName, Td.getText(formattedArgument)), true);
      case TdApi.MessageChatSetTheme.CONSTRUCTOR:
        if (StringUtils.isEmpty(formattedArgument.text)) {
          if (isOutgoing)
            return new ContentPreview(EMOJI_THEME, R.string.ChatContentThemeDisabled_outgoing);
          else
            return new ContentPreview(EMOJI_THEME, R.string.ChatContentThemeDisabled);
        } else {
          if (isOutgoing)
            return new ContentPreview(EMOJI_THEME, 0, TD.toFormattedText(Lang.getStringBold(R.string.ChatContentThemeSet_outgoing, formattedArgument.text), true));
          else
            return new ContentPreview(EMOJI_THEME, 0, TD.toFormattedText(Lang.getStringBold(R.string.ChatContentThemeSet, formattedArgument.text), true));
        }
      case TdApi.MessageChatSetMessageAutoDeleteTime.CONSTRUCTOR: {
        if (arg1 > 0) {
          final int secondsRes, minutesRes, hoursRes, daysRes, weeksRes, monthsRes;
          if (ChatId.isUserChat(chatId)) {
            secondsRes = R.string.ChatContentTtlSeconds;
            minutesRes = R.string.ChatContentTtlMinutes;
            hoursRes = R.string.ChatContentTtlHours;
            daysRes = R.string.ChatContentTtlDays;
            weeksRes = R.string.ChatContentTtlWeeks;
            monthsRes = R.string.ChatContentTtlMonths;
          } else if (tdlib.isChannel(chatId)) {
            secondsRes = R.string.ChatContentChannelTtlSeconds;
            minutesRes = R.string.ChatContentChannelTtlMinutes;
            hoursRes = R.string.ChatContentChannelTtlHours;
            daysRes = R.string.ChatContentChannelTtlDays;
            weeksRes = R.string.ChatContentChannelTtlWeeks;
            monthsRes = R.string.ChatContentChannelTtlMonths;
          } else {
            secondsRes = R.string.ChatContentGroupTtlSeconds;
            minutesRes = R.string.ChatContentGroupTtlMinutes;
            hoursRes = R.string.ChatContentGroupTtlHours;
            daysRes = R.string.ChatContentGroupTtlDays;
            weeksRes = R.string.ChatContentGroupTtlWeeks;
            monthsRes = R.string.ChatContentGroupTtlMonths;
          }
          final CharSequence text = Lang.pluralDuration(arg1, TimeUnit.SECONDS, secondsRes, minutesRes, hoursRes, daysRes, weeksRes, monthsRes);
          return new ContentPreview(EMOJI_TIMER, 0, TD.toFormattedText(text, false), true);
        } else {
          final int stringRes;
          if (ChatId.isUserChat(chatId)) {
            stringRes = R.string.ChatContentTtlOff;
          } else if (tdlib.isChannel(chatId)) {
            stringRes = R.string.ChatContentChannelTtlOff;
          } else {
            stringRes = R.string.ChatContentGroupTtlOff;
          }
          return new ContentPreview(EMOJI_TIMER_OFF, stringRes);
        }
      }
      case TdApi.MessageDice.CONSTRUCTOR: {
        String diceEmoji = !Td.isEmpty(formattedArgument) && tdlib.isDiceEmoji(formattedArgument.text) ? formattedArgument.text : EMOJI_DICE.textRepresentation;
        if (EMOJI_DART.textRepresentation.equals(diceEmoji)) {
          return new ContentPreview(EMOJI_DART, getDartRes(arg1));
        }
        if (EMOJI_DICE.textRepresentation.equals(diceEmoji)) {
          if (arg1 >= 1 && arg1 <= 6) {
            switch (arg1) {
              case 1:
                return new ContentPreview(EMOJI_DICE_1, 0, Lang.plural(R.string.ChatContentDiceRolled, arg1), true);
              case 2:
                return new ContentPreview(EMOJI_DICE_2, 0, Lang.plural(R.string.ChatContentDiceRolled, arg1), true);
              case 3:
                return new ContentPreview(EMOJI_DICE_3, 0, Lang.plural(R.string.ChatContentDiceRolled, arg1), true);
              case 4:
                return new ContentPreview(EMOJI_DICE_4, 0, Lang.plural(R.string.ChatContentDiceRolled, arg1), true);
              case 5:
                return new ContentPreview(EMOJI_DICE_5, 0, Lang.plural(R.string.ChatContentDiceRolled, arg1), true);
              case 6:
                return new ContentPreview(EMOJI_DICE_6, 0, Lang.plural(R.string.ChatContentDiceRolled, arg1), true);
            }
          }
          return new ContentPreview(EMOJI_DICE, R.string.ChatContentDice);
        }
        return new ContentPreview(new Emoji(diceEmoji, 0), 0);
      }
      case TdApi.MessageExpiredPhoto.CONSTRUCTOR:
        return new ContentPreview(EMOJI_SECRET_PHOTO, R.string.AttachPhotoExpired);
      case TdApi.MessageExpiredVideo.CONSTRUCTOR:
        return new ContentPreview(EMOJI_SECRET_VIDEO, R.string.AttachVideoExpired);
      case TdApi.MessageCall.CONSTRUCTOR:
        switch (arg1) {
          case ARG_CALL_DECLINED:
            return new ContentPreview(EMOJI_CALL_DECLINED, isOutgoing ? R.string.OutgoingCall : R.string.CallMessageIncomingDeclined);
          case ARG_CALL_MISSED:
            return new ContentPreview(EMOJI_CALL_MISSED, isOutgoing ? R.string.CallMessageOutgoingMissed : R.string.MissedCall);
          default:
            if (arg1 > 0) {
              return new ContentPreview(EMOJI_CALL, 0, Lang.getString(R.string.ChatContentCallWithDuration, Lang.getString(isOutgoing ? R.string.OutgoingCall : R.string.IncomingCall), Lang.getDurationFull(arg1)), true);
            } else {
              return new ContentPreview(EMOJI_CALL, isOutgoing ? R.string.OutgoingCall : R.string.IncomingCall);
            }
        }
      case TdApi.MessageChatUpgradeFrom.CONSTRUCTOR:
      case TdApi.MessageChatUpgradeTo.CONSTRUCTOR:
        return new ContentPreview(EMOJI_GROUP, R.string.GroupUpgraded);

      case TdApi.MessagePremiumGiveawayCreated.CONSTRUCTOR:
        return new ContentPreview(EMOJI_GIFT, R.string.BoostingGiveawayJustStarted);
      case TdApi.MessagePremiumGiveawayCompleted.CONSTRUCTOR:
        return new ContentPreview(EMOJI_GIFT, 0, Lang.plural(R.string.BoostingGiveawayServiceWinnersSelected, arg1), true);

      case TdApi.MessagePremiumGiveaway.CONSTRUCTOR: {
        int winnerCount = arg1;
        int monthCount = arg2;
        String text;
        if (winnerCount > 0) {
          text = Lang.getString(R.string.format_giveawayInfo,
            Lang.getString(R.string.Giveaway),
            Lang.plural(R.string.xFutureWinners, winnerCount)
          );
        } else {
          text = Lang.getString(R.string.Giveaway);
        }
        return new ContentPreview(EMOJI_GIFT, 0, text, true);
      }
      case TdApi.MessagePremiumGiveawayWinners.CONSTRUCTOR: {
        int winnerCount = arg1;
        int monthCount = arg2;
        String text;
        if (winnerCount > 0) {
          text = Lang.getString(R.string.format_giveawayInfo,
            Lang.getString(R.string.Giveaway),
            Lang.plural(R.string.xPastWinners, winnerCount)
          );
        } else {
          text = Lang.getString(R.string.Giveaway);
        }
        return new ContentPreview(EMOJI_GIFT, 0, text, true);
      }


      // Must be supported by the caller and never passed to this method.
      case TdApi.MessageGiftedPremium.CONSTRUCTOR:
      case TdApi.MessageGameScore.CONSTRUCTOR:
      case TdApi.MessageProximityAlertTriggered.CONSTRUCTOR:
      case TdApi.MessageChatAddMembers.CONSTRUCTOR:
      case TdApi.MessageChatDeleteMember.CONSTRUCTOR:
      case TdApi.MessageCustomServiceAction.CONSTRUCTOR:
      case TdApi.MessageBotWriteAccessAllowed.CONSTRUCTOR:
      case TdApi.MessageWebAppDataSent.CONSTRUCTOR:
      case TdApi.MessageInviteVideoChatParticipants.CONSTRUCTOR:
      case TdApi.MessageVideoChatStarted.CONSTRUCTOR:
      case TdApi.MessageVideoChatEnded.CONSTRUCTOR:
      case TdApi.MessageVideoChatScheduled.CONSTRUCTOR:
      case TdApi.MessagePinMessage.CONSTRUCTOR:
      case TdApi.MessagePaymentSuccessful.CONSTRUCTOR:
        throw new IllegalArgumentException(Integer.toString(type));
        
      case TdApi.MessageStory.CONSTRUCTOR:
      case TdApi.MessageUsersShared.CONSTRUCTOR:
      case TdApi.MessageChatShared.CONSTRUCTOR:
      case TdApi.MessageSuggestProfilePhoto.CONSTRUCTOR:
      case TdApi.MessageForumTopicCreated.CONSTRUCTOR:
      case TdApi.MessageForumTopicEdited.CONSTRUCTOR:
      case TdApi.MessageForumTopicIsClosedToggled.CONSTRUCTOR:
      case TdApi.MessageForumTopicIsHiddenToggled.CONSTRUCTOR:
      case TdApi.MessagePassportDataSent.CONSTRUCTOR:
      case TdApi.MessageChatSetBackground.CONSTRUCTOR:
      case TdApi.MessagePremiumGiftCode.CONSTRUCTOR:
        // TODO support these previews
        return new ContentPreview(EMOJI_QUIZ, R.string.UnsupportedMessage);
        
      case TdApi.MessageUnsupported.CONSTRUCTOR:
        return new ContentPreview(EMOJI_QUIZ, R.string.UnsupportedMessageType);

      // Bots only. Unused
      case TdApi.MessagePassportDataReceived.CONSTRUCTOR:
      case TdApi.MessagePaymentSuccessfulBot.CONSTRUCTOR:
      case TdApi.MessageWebAppDataReceived.CONSTRUCTOR:
      default:
        Td.assertMessageContent_d40af239();
        throw new UnsupportedOperationException(Integer.toString(type));
    }
  }

  static int getDartRes (int value) {
    switch (value) {
      case 0:
        return R.string.ChatContentDart;
      case 1:
        return R.string.ChatContentDart1;
      case 2:
        return R.string.ChatContentDart2;
      case 3:
        return R.string.ChatContentDart3;
      case 4:
        return R.string.ChatContentDart4;
      case 6:
        return R.string.ChatContentDart6;
      case 5:
      default:
        return R.string.ChatContentDart5;
    }
  }

  @NonNull
  public String buildText (boolean allowIcon) {
    if (emoji == null || (allowIcon && emoji.iconRepresentation != 0)) {
      return Td.isEmpty(formattedText) ? (placeholderText != 0 ? Lang.getString(placeholderText) : "") : formattedText.text;
    } else if (Td.isEmpty(formattedText)) {
      return placeholderText != 0 ? emoji.textRepresentation + " " + Lang.getString(placeholderText) : emoji.textRepresentation;
    } else if (formattedText.text.startsWith(emoji.textRepresentation)) {
      return formattedText.text;
    } else {
      return emoji.textRepresentation + " " + formattedText.text;
    }
  }

  public TdApi.FormattedText buildFormattedText (boolean allowIcon) {
    if (emoji == null || (allowIcon && emoji.iconRepresentation != 0)) {
      return Td.isEmpty(formattedText) ? new TdApi.FormattedText(placeholderText != 0 ? Lang.getString(placeholderText) : "", null) : formattedText;
    } else if (Td.isEmpty(formattedText)) {
      return new TdApi.FormattedText(placeholderText != 0 ? emoji.textRepresentation + " " + Lang.getString(placeholderText) : emoji.textRepresentation, null);
    } else if (formattedText.text.startsWith(emoji.textRepresentation)) {
      return formattedText;
    } else {
      return TD.withPrefix(emoji.textRepresentation + " ", formattedText);
    }
  }

  @Override
  @NonNull
  public String toString () {
    return buildText(false);
  }

  private Refresher refresher;
  private boolean isMediaGroup;

  public ContentPreview setRefresher (Refresher refresher, boolean isMediaGroup) {
    this.refresher = refresher;
    this.isMediaGroup = isMediaGroup;
    return this;
  }

  public boolean hasRefresher () {
    return refresher != null;
  }

  public boolean isMediaGroup () {
    return isMediaGroup;
  }

  public void refreshContent (@NonNull RefreshCallback callback) {
    if (refresher != null) {
      refresher.runRefresher(this, callback);
    }
  }

  private Tdlib.Album album;

  @Nullable
  public Tdlib.Album getAlbum () {
    return album;
  }

  public interface Refresher {
    void runRefresher (ContentPreview oldPreview, RefreshCallback callback);
  }

  public interface RefreshCallback {
    void onContentPreviewChanged (long chatId, long messageId, ContentPreview newPreview, ContentPreview oldPreview);

    default void onContentPreviewNotChanged (long chatId, long messageId, ContentPreview oldContent) {}
  }

  public static final class Emoji {
    public final @NonNull String textRepresentation;
    public final @DrawableRes int iconRepresentation;

    public Emoji (@NonNull String textRepresentation, @DrawableRes int iconRepresentation) {
      this.textRepresentation = textRepresentation;
      this.iconRepresentation = iconRepresentation;
    }

    @NonNull
    @Override
    public String toString () {
      return textRepresentation;
    }

    public Emoji toNewEmoji (String newEmoji) {
      return StringUtils.equalsOrBothEmpty(this.textRepresentation, newEmoji) ? this : new Emoji(newEmoji, iconRepresentation);
    }
  }
}
