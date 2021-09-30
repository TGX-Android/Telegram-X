/**
 * File created on 03/05/15 at 11:24
 * Copyright Vyacheslav Krylov, 2014
 */
package org.thunderdog.challegram.data;

import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.RectF;
import android.text.BoringLayout;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.StringRes;

import org.drinkless.td.libcore.telegram.Client;
import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.chat.MessageView;
import org.thunderdog.challegram.component.chat.MessagesManager;
import org.thunderdog.challegram.component.dialogs.ChatView;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.emoji.Emoji;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.loader.Receiver;
import org.thunderdog.challegram.mediaview.MediaViewController;
import org.thunderdog.challegram.mediaview.MediaViewThumbLocation;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibSender;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.ui.MapController;
import org.thunderdog.challegram.ui.MessagesController;
import org.thunderdog.challegram.util.CustomTypefaceSpan;
import org.thunderdog.challegram.util.text.Text;

import java.util.concurrent.TimeUnit;

import me.vkryl.core.CurrencyUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.td.ChatId;
import me.vkryl.td.Td;
import me.vkryl.td.TdConstants;

public class TGMessageChat extends TGMessage implements Client.ResultHandler {
  public static final int TYPE_CREATE = 0;

  public static final int TYPE_CHANGE_TITLE = 1;
  public static final int TYPE_CHANGE_PHOTO = 2;
  public static final int TYPE_DELETE_PHOTO = 3;

  public static final int TYPE_ADD_MEMBERS = 4;
  public static final int TYPE_KICK_MEMBER = 5;
  public static final int TYPE_JOIN_BY_LINK = 6;

  // public static final int TYPE_CREATE_CHANNEL = 7;

  public static final int TYPE_MIGRATE_TO = 8;
  public static final int TYPE_MIGRATE_FROM = 9;
  public static final int TYPE_SCORE = 10;
  public static final int TYPE_SCREENSHOT_TAKEN = 11;
  public static final int TYPE_SELF_DESTRUCT_TIMER = 12;

  public static final int TYPE_PINNED_MESSAGE = 13;
  public static final int TYPE_CONTACT_REGISTERED = 14;
  public static final int TYPE_PHOTO_EXPIRED = 15;
  public static final int TYPE_VIDEO_EXPIRED = 16;

  public static final int TYPE_EVENT_MESSAGE_DELETED = 17;
  public static final int TYPE_EVENT_MESSAGE_PINNED = 18;
  public static final int TYPE_EVENT_MESSAGE_UNPINNED = 19;
  public static final int TYPE_EVENT_INVITES_TOGGLED = 20;
  public static final int TYPE_EVENT_SIGNATURES_TOGGLED = 21;
  public static final int TYPE_EVENT_LINK_CHANGED = 22;
  public static final int TYPE_EVENT_DESCRIPTION_CHANGED = 23;
  public static final int TYPE_EVENT_MESSAGE_EDITED = 24;
  public static final int TYPE_EVENT_CAPTION_REMOVED = 25;
  public static final int TYPE_EVENT_PREHISTORY_TOGGLED = 26;
  public static final int TYPE_EVENT_STICKER_PACK_CHANGED = 27;

  public static final int TYPE_PAYMENT_SUCCESSFUL = 28;
  public static final int TYPE_WEBSITE_CONNECTED = 29;
  public static final int TYPE_CUSTOM = 30;

  public static final int TYPE_EVENT_POLL_STOPPED = 31;

  public static final int TYPE_EVENT_SLOW_MODE_DELAY_CHANGED = 32;
  public static final int TYPE_EVENT_SLOW_MODE_DELAY_DISABLED = 33;

  public static final int TYPE_EVENT_LINKED_CHAT_CHANGED = 34;
  public static final int TYPE_EVENT_LINKED_CHAT_REMOVED = 35;

  public static final int TYPE_EVENT_LOCATION_SET = 36;
  public static final int TYPE_EVENT_LOCATION_CHANGED = 37;
  public static final int TYPE_EVENT_LOCATION_REMOVED = 38;

  public static final int TYPE_EVENT_VOICE_CHAT_MUTE_NEW_PARTICIPANTS_TOGGLED = 39;
  public static final int TYPE_EVENT_VOICE_CHAT_IS_MUTED_TOGGLED = 40;
  public static final int TYPE_EVENT_VOICE_CHAT_PARTICIPANT_VOLUME_CHANGED = 41;

  public static final int TYPE_VOICE_CHAT_STARTED = 49;
  public static final int TYPE_VOICE_CHAT_ENDED = 50;
  public static final int TYPE_INVITE_VOICE_CHAT_PARTICIPANTS = 51;
  public static final int TYPE_PROXIMITY_ALERT = 52;

  public static final int TYPE_EVENT_INVITE_LINK_REVOKED = 53;
  public static final int TYPE_EVENT_INVITE_LINK_DELETE = 54;

  // Data

  private int type;

  private String title;
  private TdApi.User actionUser;
  private TdlibSender actionSender;
  private long[] actionUserIds;
  private TdApi.ChatPhoto photo;
  private ImageFile avatar;

  private boolean boolValue;
  private long longValue;
  private String stringValue;
  private TdApi.Location locationValue;
  private TdApi.ChatInviteLink inviteLinkValue;

  // Layout

  private String customFormat;
  private Layout layout;

  private int pWidth;

  public TGMessageChat (MessagesManager context, TdApi.Message msg, int type) {
    super(context, msg);
    this.type = type;
  }

  public TGMessageChat (MessagesManager context, TdApi.Message msg, TdApi.MessageBasicGroupChatCreate create) {
    super(context, msg);
    this.type = TYPE_CREATE;
    this.title = create.title;
  }

  public TGMessageChat (MessagesManager context, TdApi.Message msg, TdApi.MessageChatChangeTitle change) {
    super(context, msg);
    this.type = TYPE_CHANGE_TITLE;
    this.title = change.title;
  }

  public TGMessageChat (MessagesManager context, TdApi.Message msg, TdApi.MessageChatJoinByLink joined) {
    super(context, msg);
    this.type = TYPE_JOIN_BY_LINK;
  }

  public TGMessageChat (MessagesManager context, TdApi.Message msg, TdApi.MessageContactRegistered registered) {
    super(context, msg);
    this.type = TYPE_CONTACT_REGISTERED;
  }

  public TGMessageChat (MessagesManager context, TdApi.Message msg, TdApi.MessageChatChangePhoto photo) {
    super(context, msg);
    //noinspection WrongConstant
    if (isEventLog() && msg.content.getConstructor() == TdApiExt.MessageChatEvent.CONSTRUCTOR) {
      TdApi.ChatEventAction action = ((TdApiExt.MessageChatEvent) msg.content).event.action;
      if (action.getConstructor() == TdApi.ChatEventPhotoChanged.CONSTRUCTOR && ((TdApi.ChatEventPhotoChanged) action).newPhoto == null) {
        this.type = TYPE_DELETE_PHOTO;
      } else {
        this.type = TYPE_CHANGE_PHOTO;
      }
    } else {
      this.type = TYPE_CHANGE_PHOTO;
    }
    this.photo = photo.photo;

    TdApi.ChatPhoto avatar = photo.photo;
    if (avatar != null) {
      TdApi.PhotoSize size = null;
      int lastWidth = 0, lastHeight = 0;

      for (TdApi.PhotoSize photoSize : avatar.sizes) {
        if (lastWidth == 0 || lastHeight == 0 || photoSize.width < lastWidth || photoSize.height < lastHeight) {
          size = photoSize;
          lastWidth = photoSize.width;
          lastHeight = photoSize.height;
        }
      }

      if (size != null) {
        this.avatar = new ImageFile(tdlib, size.photo);
        this.avatar.setSize(ChatView.getDefaultAvatarCacheSize());
      }
    }
  }

  public TGMessageChat (MessagesManager context, TdApi.Message msg, TdApi.MessageChatAddMembers add) {
    super(context, msg);
    this.type = TYPE_ADD_MEMBERS;
    this.actionUserIds = add.memberUserIds;
  }

  public TGMessageChat (MessagesManager context, TdApi.Message msg, TdApi.MessageChatDeleteMember delete) {
    super(context, msg);
    this.type = TYPE_KICK_MEMBER;
    this.actionUser = userForId(delete.userId);
  }

  public TGMessageChat (MessagesManager context, TdApi.Message msg, TdApi.MessageSupergroupChatCreate create) {
    super(context, msg);
    this.type = TYPE_CREATE;
    this.title = create.title;
  }

  public TGMessageChat (MessagesManager context, TdApi.Message msg, TdApi.MessageChatUpgradeTo migrateTo) {
    super(context, msg);
    this.type = TYPE_MIGRATE_TO;
    this.longValue = migrateTo.supergroupId;
  }

  public TGMessageChat (MessagesManager context, TdApi.Message msg, TdApi.MessageChatUpgradeFrom migrateFrom) {
    super(context, msg);
    this.type = TYPE_MIGRATE_FROM;
    this.longValue = migrateFrom.basicGroupId;
  }

  public TGMessageChat (MessagesManager context, TdApi.Message msg, TdApi.MessageScreenshotTaken screenshot) {
    super(context, msg);
    this.type = TYPE_SCREENSHOT_TAKEN;
  }

  public TGMessageChat (MessagesManager context, TdApi.Message msg, TdApi.MessagePinMessage pin) {
    super(context, msg);
    this.type = TYPE_PINNED_MESSAGE;

    if (pin.messageId != 0) {
      tdlib.client().send(new TdApi.GetMessage(msg.chatId, pin.messageId), this);
    }
  }

  public TGMessageChat (MessagesManager context, TdApi.Message msg, TdApi.MessageWebsiteConnected connected) {
    super(context, msg);
    this.type = TYPE_WEBSITE_CONNECTED;
    this.stringValue = connected.domainName;
  }

  private int ttl;

  public TGMessageChat (MessagesManager context, TdApi.Message msg, TdApi.MessageChatSetTtl ttl) {
    super(context, msg);
    this.type = TYPE_SELF_DESTRUCT_TIMER;
    this.ttl = ttl.ttl;
  }

  public TGMessageChat (MessagesManager context, TdApi.Message msg, TdApi.MessageExpiredPhoto ignored) {
    super(context, msg);
    this.type = TYPE_PHOTO_EXPIRED;
  }

  public TGMessageChat (MessagesManager context, TdApi.Message msg, TdApi.MessageExpiredVideo ignored) {
    super(context, msg);
    this.type = TYPE_VIDEO_EXPIRED;
  }

  public TGMessageChat (MessagesManager context, TdApi.Message msg, TdApi.MessageGameScore score) {
    super(context, msg);
    this.type = TYPE_SCORE;
    if (score.gameMessageId != 0) {
      tdlib.client().send(new TdApi.GetMessage(msg.chatId, score.gameMessageId), this);
    }
  }

  public TGMessageChat (MessagesManager context, TdApi.Message msg, TdApi.MessageVoiceChatStarted voiceChatStarted) {
    super(context, msg);
    this.type = TYPE_VOICE_CHAT_STARTED;
  }

  public TGMessageChat (MessagesManager context, TdApi.Message msg, TdApi.MessageVoiceChatEnded voiceChatEnded) {
    super(context, msg);
    this.type = TYPE_VOICE_CHAT_ENDED;
    this.longValue = voiceChatEnded.duration;
  }

  public TGMessageChat (MessagesManager context, TdApi.Message msg, TdApi.MessageInviteVoiceChatParticipants inviteVoiceChatParticipants) {
    super(context, msg);
    this.type = TYPE_INVITE_VOICE_CHAT_PARTICIPANTS;
    this.actionUserIds = inviteVoiceChatParticipants.userIds;
  }

  public TGMessageChat (MessagesManager context, TdApi.Message msg, TdApi.MessageProximityAlertTriggered proximityAlert) {
    super(context, msg);
    this.type = TYPE_PROXIMITY_ALERT;
    this.longValue = proximityAlert.distance;
  }

  public TGMessageChat (MessagesManager context, TdApi.Message msg, TdApi.ChatEvent chatEvent) {
    super(context, msg);
    switch (chatEvent.action.getConstructor()) {
      case TdApi.ChatEventMessageDeleted.CONSTRUCTOR: {
        this.type = TYPE_EVENT_MESSAGE_DELETED;
        break;
      }
      case TdApi.ChatEventMessagePinned.CONSTRUCTOR:
        this.type = TYPE_EVENT_MESSAGE_PINNED;
        break;
      case TdApi.ChatEventPollStopped.CONSTRUCTOR:
        this.type = TYPE_EVENT_POLL_STOPPED;
        break;
      case TdApi.ChatEventMessageUnpinned.CONSTRUCTOR:
        this.type = TYPE_EVENT_MESSAGE_UNPINNED;
        break;

      case TdApi.ChatEventInvitesToggled.CONSTRUCTOR:
        this.type = TYPE_EVENT_INVITES_TOGGLED;
        this.boolValue = ((TdApi.ChatEventInvitesToggled) chatEvent.action).canInviteUsers;
        break;
      case TdApi.ChatEventSignMessagesToggled.CONSTRUCTOR:
        this.type = TYPE_EVENT_SIGNATURES_TOGGLED;
        this.boolValue = ((TdApi.ChatEventSignMessagesToggled) chatEvent.action).signMessages;
        break;
      case TdApi.ChatEventUsernameChanged.CONSTRUCTOR:
        this.type = TYPE_EVENT_LINK_CHANGED;
        this.boolValue = !StringUtils.isEmpty(((TdApi.ChatEventUsernameChanged) chatEvent.action).newUsername);
        break;
      case TdApi.ChatEventDescriptionChanged.CONSTRUCTOR:
        this.type = TYPE_EVENT_DESCRIPTION_CHANGED;
        this.boolValue = !StringUtils.isEmpty(((TdApi.ChatEventDescriptionChanged) chatEvent.action).newDescription);
        break;
      case TdApi.ChatEventMemberJoinedByInviteLink.CONSTRUCTOR:
        this.type = TYPE_JOIN_BY_LINK;
        this.inviteLinkValue = ((TdApi.ChatEventMemberJoinedByInviteLink) chatEvent.action).inviteLink;
        this.actionUser = inviteLinkValue.creatorUserId != 0 ? userForId(inviteLinkValue.creatorUserId) : null;
        break;
      case TdApi.ChatEventInviteLinkRevoked.CONSTRUCTOR:
        this.type = TYPE_EVENT_INVITE_LINK_REVOKED;
        this.inviteLinkValue = ((TdApi.ChatEventInviteLinkRevoked) chatEvent.action).inviteLink;
        this.actionUser = inviteLinkValue.creatorUserId != 0 ? userForId(inviteLinkValue.creatorUserId) : null;
        break;
      case TdApi.ChatEventInviteLinkDeleted.CONSTRUCTOR:
        this.type = TYPE_EVENT_INVITE_LINK_DELETE;
        this.inviteLinkValue = ((TdApi.ChatEventInviteLinkDeleted) chatEvent.action).inviteLink;
        this.actionUser = inviteLinkValue.creatorUserId != 0 ? userForId(inviteLinkValue.creatorUserId) : null;
        break;
      case TdApi.ChatEventVoiceChatParticipantVolumeLevelChanged.CONSTRUCTOR: {
        this.type = TYPE_EVENT_VOICE_CHAT_PARTICIPANT_VOLUME_CHANGED;
        TdApi.ChatEventVoiceChatParticipantVolumeLevelChanged volumeChanged = (TdApi.ChatEventVoiceChatParticipantVolumeLevelChanged) chatEvent.action;
        this.actionSender = new TdlibSender(tdlib, msg.chatId, volumeChanged.participantId);
        this.longValue = volumeChanged.volumeLevel;
        break;
      }
      case TdApi.ChatEventIsAllHistoryAvailableToggled.CONSTRUCTOR:
        this.type = TYPE_EVENT_PREHISTORY_TOGGLED;
        this.boolValue = ((TdApi.ChatEventIsAllHistoryAvailableToggled) chatEvent.action).isAllHistoryAvailable;
        break;
      case TdApi.ChatEventStickerSetChanged.CONSTRUCTOR:
        this.type = TYPE_EVENT_STICKER_PACK_CHANGED;
        TdApi.ChatEventStickerSetChanged event = (TdApi.ChatEventStickerSetChanged) chatEvent.action;
        this.boolValue = event.newStickerSetId != 0;
        this.longValue = boolValue ? event.newStickerSetId : event.oldStickerSetId;
        break;

      case TdApi.ChatEventMessageEdited.CONSTRUCTOR:
        this.type = TYPE_EVENT_MESSAGE_EDITED;
        TdApi.Message newMessage = ((TdApi.ChatEventMessageEdited) chatEvent.action).newMessage;
        this.boolValue = newMessage.content.getConstructor() != TdApi.MessageText.CONSTRUCTOR;
        if (boolValue && Td.isEmpty(Td.textOrCaption(newMessage.content))) {
          this.type = TYPE_EVENT_CAPTION_REMOVED;
        }
        break;
      case TdApi.ChatEventSlowModeDelayChanged.CONSTRUCTOR:
        this.type = TYPE_EVENT_SLOW_MODE_DELAY_CHANGED;
        this.longValue = ((TdApi.ChatEventSlowModeDelayChanged) chatEvent.action).newSlowModeDelay;
        if (longValue == 0) {
          this.type = TYPE_EVENT_SLOW_MODE_DELAY_DISABLED;
        }
        break;
      case TdApi.ChatEventVoiceChatMuteNewParticipantsToggled.CONSTRUCTOR:
        this.type = TYPE_EVENT_VOICE_CHAT_MUTE_NEW_PARTICIPANTS_TOGGLED;
        this.boolValue = ((TdApi.ChatEventVoiceChatMuteNewParticipantsToggled) chatEvent.action).muteNewParticipants;
        break;
      case TdApi.ChatEventVoiceChatParticipantIsMutedToggled.CONSTRUCTOR:
        this.type = TYPE_EVENT_VOICE_CHAT_IS_MUTED_TOGGLED;
        this.boolValue = ((TdApi.ChatEventVoiceChatParticipantIsMutedToggled) chatEvent.action).isMuted;
        this.actionSender = new TdlibSender(tdlib, msg.chatId, ((TdApi.ChatEventVoiceChatParticipantIsMutedToggled) chatEvent.action).participantId);
        break;
      case TdApi.ChatEventLinkedChatChanged.CONSTRUCTOR: {
        this.type = TYPE_EVENT_LINKED_CHAT_CHANGED;
        TdApi.ChatEventLinkedChatChanged linkedChatChange = (TdApi.ChatEventLinkedChatChanged) chatEvent.action;
        this.longValue = linkedChatChange.newLinkedChatId;
        if (longValue == 0) {
          this.type = TYPE_EVENT_LINKED_CHAT_REMOVED;
          this.longValue = linkedChatChange.oldLinkedChatId;
        }
        break;
      }
      case TdApi.ChatEventLocationChanged.CONSTRUCTOR: {
        TdApi.ChatEventLocationChanged locationChange = (TdApi.ChatEventLocationChanged) chatEvent.action;
        if (locationChange.newLocation == null) {
          this.type = TYPE_EVENT_LOCATION_REMOVED;
          if (locationChange.oldLocation != null) {
            this.stringValue = locationChange.oldLocation.address;
            this.locationValue = locationChange.oldLocation.location;
          }
        } else {
          this.type = locationChange.oldLocation != null ? TYPE_EVENT_LOCATION_CHANGED : TYPE_EVENT_LOCATION_SET;
          this.stringValue = locationChange.newLocation.address;
          this.locationValue = locationChange.newLocation.location;
        }
        break;
      }
      default:
        throw new IllegalArgumentException("unsupported: " + chatEvent.action);
    }
  }

  public TGMessageChat (MessagesManager context, TdApi.Message msg, TdApi.MessagePaymentSuccessful successful) {
    super(context, msg);
    this.type = TYPE_PAYMENT_SUCCESSFUL;

    this.stringValue = CurrencyUtils.buildAmount(successful.currency, successful.totalAmount);

    if (successful.invoiceMessageId != 0) {
      tdlib.client().send(new TdApi.GetMessage(msg.chatId, successful.invoiceMessageId), this);
    }
  }

  public TGMessageChat (MessagesManager context, TdApi.Message msg, TdApi.MessageCustomServiceAction custom) {
    super(context, msg);
    this.type = TYPE_CUSTOM;
    this.customFormat = custom.text;
  }

  @Override
  public boolean onMessageClick (MessageView v, MessagesController c) {
    if (type == TYPE_EVENT_STICKER_PACK_CHANGED && longValue != 0) {
      tdlib.ui().showStickerSet(controller(), longValue);
      return true;
    }
    return false;
  }

  private TdApi.Game game;
  private TdApi.Message otherMessage;

  @Override
  public void onResult (TdApi.Object object) {
    switch (object.getConstructor()) {
      case TdApi.Message.CONSTRUCTOR: {
        final TdApi.Message message = (TdApi.Message) object;
        tdlib.ui().post(() -> {
          if (!isDestroyed()) {
            otherMessage = message;
            switch (type) {
              case TYPE_PINNED_MESSAGE:
                break;
              case TYPE_SCORE:
                if (message.content.getConstructor() == TdApi.MessageGame.CONSTRUCTOR) {
                  game = ((TdApi.MessageGame) message.content).game;
                }
                break;
            }
            rebuildAndUpdateContent();
          }
        });
        break;
      }
      case TdApi.Error.CONSTRUCTOR:
      default: {
        break;
      }
    }
  }

  private boolean isExpiredContentHint () {
    return type == TYPE_PHOTO_EXPIRED || type == TYPE_VIDEO_EXPIRED;
  }

  @Override
  public boolean canReplyTo () {
    return !isExpiredContentHint() && super.canReplyTo();
  }

  @Override
  protected boolean updateMessageContent (TdApi.Message message, TdApi.MessageContent newContent, boolean isBottomMessage) {
    msg.content = newContent;
    rebuildAndUpdateContent();
    return true;
  }

  private void setText () {
    switch (type) {
      // Channels and Groups
      case TYPE_CREATE:
        if (msg.isChannelPost)
          makeText(R.string.channel_create_somebody, new Arg(title));
        else if (msg.isOutgoing)
          makeText(R.string.group_create_you, new Arg(title));
        else
          makeText(R.string.group_created, new Arg(sender), new Arg(title));
        break;
      case TYPE_CHANGE_TITLE:
        if (msg.isChannelPost)
          makeText(R.string.ChannelRenamed, new Arg(title));
        else if (msg.isOutgoing)
          makeText(R.string.group_title_changed_you, new Arg(title));
        else
          makeText(R.string.group_title_changed, new Arg(sender), new Arg(title));
        break;
      case TYPE_CHANGE_PHOTO:
        if (msg.isChannelPost)
          makeText(R.string.ActionChannelChangedPhoto);
        else if (msg.isOutgoing)
          makeText(R.string.group_photo_changed_you);
        else
          makeText(R.string.group_photo_changed, new Arg(sender));
        break;
      case TYPE_DELETE_PHOTO:
        if (msg.isChannelPost)
          makeText(R.string.ActionChannelRemovedPhoto);
        else if (msg.isOutgoing)
          makeText(R.string.group_photo_deleted_you);
        else
          makeText(R.string.group_photo_deleted, new Arg(sender));
        break;

      // Private and Secret Chats

      case TYPE_SCREENSHOT_TAKEN:
        if (msg.isOutgoing)
          makeText(R.string.YouTookAScreenshot);
        else
          makeText(R.string.XTookAScreenshot, new Arg(sender, true));
        break;

      case TYPE_SCORE:
        int score = ((TdApi.MessageGameScore) msg.content).score;
        if (msg.isOutgoing) {
          if (game != null)
            makePlural(R.string.game_ActionYouScoredInGame, score, new Arg(TD.getGameName(game, false)).setActive(true));
          else
            makePlural(R.string.game_ActionYouScored, score);
        } else {
          if (game != null)
            makePlural(R.string.game_ActionUserScoredInGame, score, new Arg(sender), new Arg(TD.getGameName(game, false)).setActive(true));
          else
            makePlural(R.string.game_ActionUserScored, score, new Arg(sender));
        }
        break;

      case TYPE_ADD_MEMBERS: {
        if (actionUserIds.length == 1 && sender.getUserId() == actionUserIds[0]) {
          if (actionUserIds.length == 1 && tdlib.isSelfUserId(actionUserIds[0])) {
            makeText(msg.isChannelPost ? R.string.channel_user_add_self : R.string.group_user_add_self);
          } else {
            makeText(msg.isChannelPost ? R.string.channel_user_add : R.string.group_user_add, new Arg(this, actionUserIds));
          }
        } else if (sender.isSelf()) {
          makeText(R.string.group_user_self_added, new Arg(this, actionUserIds));
        } else if (actionUserIds.length == 1 && tdlib.isSelfUserId(actionUserIds[0])) {
          makeText(R.string.group_user_added_self, new Arg(sender));
        } else {
          makeText(R.string.group_user_added, new Arg(sender), new Arg(this, actionUserIds));
        }
        break;
      }
      case TYPE_KICK_MEMBER: {
        if (tdlib.isSelfUserId(actionUser.id)) {
          if (sender != null && sender.getUserId() != actionUser.id) {
            makeText(R.string.group_user_removed_self, new Arg(sender));
          } else {
            makeText(msg.isChannelPost ? R.string.channel_user_remove_self : R.string.group_user_remove_self);
          }
        } else if (sender == null || sender.getUserId() == actionUser.id) {
          makeText(msg.isChannelPost ? R.string.channel_user_remove : R.string.group_user_remove, new Arg(sender));
        } else if (sender.isSelf()) {
          makeText(R.string.group_user_self_removed, new Arg(actionUser));
        } else {
          makeText(R.string.group_user_removed, new Arg(sender), new Arg(actionUser));
        }
        break;
      }
      case TYPE_PINNED_MESSAGE: {
        Arg userArg;
        if (sender != null) {
          userArg = new Arg(sender, true);
        } else if (!StringUtils.isEmpty(msg.authorSignature)) {
          userArg = new Arg(msg.authorSignature);
        } else {
          userArg = null;
        }

        if (otherMessage == null) {
          if (userArg != null) {
            makeText(R.string.NotificationActionPinnedNoTextChannel, userArg);
          } else {
            makeText(R.string.PinnedMessageChanged);
          }
          break;
        }

        String text = TD.getTextFromMessage(otherMessage);
        if (!StringUtils.isEmpty(text)) {
          text = Strings.replaceNewLines(Strings.limit(text, 20));
        }
        if (userArg == null) {
          if (StringUtils.isEmpty(text))
            text = Lang.lowercase(TD.buildShortPreview(tdlib, otherMessage, true));
          makeText(R.string.NewPinnedMessage, new Arg(text).setActive(true));
          break;
        }
        if (!StringUtils.isEmpty(text)) {
          makeText(R.string.ActionPinnedText, userArg, new Arg(text).setActive(true));
          break;
        }
        int res = R.string.ActionPinnedNoText;
        switch (otherMessage.content.getConstructor()) {
          case TdApi.MessageAnimation.CONSTRUCTOR:
            res = R.string.ActionPinnedGif;
            break;
          case TdApi.MessageAudio.CONSTRUCTOR:
            res = R.string.ActionPinnedMusic;
            break;
          case TdApi.MessageDocument.CONSTRUCTOR:
            res = R.string.ActionPinnedFile;
            break;
          case TdApi.MessagePhoto.CONSTRUCTOR:
          case TdApi.MessageExpiredPhoto.CONSTRUCTOR:
            res = R.string.ActionPinnedPhoto;
            break;
          case TdApi.MessageVideo.CONSTRUCTOR:
          case TdApi.MessageExpiredVideo.CONSTRUCTOR:
            res = R.string.ActionPinnedVideo;
            break;
          case TdApi.MessageVoiceNote.CONSTRUCTOR:
            res = R.string.ActionPinnedVoice;
            break;
          case TdApi.MessageSticker.CONSTRUCTOR:
            res = R.string.ActionPinnedSticker;
            break;
          case TdApi.MessagePoll.CONSTRUCTOR:
            res = ((TdApi.MessagePoll) otherMessage.content).poll.type.getConstructor() == TdApi.PollTypeQuiz.CONSTRUCTOR ? R.string.ActionPinnedQuiz : R.string.ActionPinnedPoll;
            break;
          case TdApi.MessageLocation.CONSTRUCTOR:
            res = ((TdApi.MessageLocation) otherMessage.content).livePeriod > 0 ? R.string.ActionPinnedGeoLive : R.string.ActionPinnedGeo;
            break;
          case TdApi.MessageVenue.CONSTRUCTOR:
            res = R.string.ActionPinnedGeo;
            break;
          case TdApi.MessageVideoNote.CONSTRUCTOR:
            res = R.string.ActionPinnedRound;
            break;
          case TdApi.MessageGame.CONSTRUCTOR: {
            String gameName = TD.getGameName(((TdApi.MessageGame) otherMessage.content).game, true);
            if (!StringUtils.isEmpty(gameName)) {
              res = 0;
              makeText(R.string.ActionPinnedGame, userArg, new Arg(gameName).setActive(true));
            } else {
              res = R.string.ActionPinnedGameNoName;
            }
            break;
          }
          case TdApi.MessageBasicGroupChatCreate.CONSTRUCTOR:
          case TdApi.MessageCall.CONSTRUCTOR:
          case TdApi.MessageChatAddMembers.CONSTRUCTOR:
          case TdApi.MessageChatChangePhoto.CONSTRUCTOR:
          case TdApi.MessageChatChangeTitle.CONSTRUCTOR:
          case TdApi.MessageChatDeleteMember.CONSTRUCTOR:
          case TdApi.MessageChatDeletePhoto.CONSTRUCTOR:
          case TdApi.MessageChatJoinByLink.CONSTRUCTOR:
          case TdApi.MessageChatSetTtl.CONSTRUCTOR:
          case TdApi.MessageChatUpgradeFrom.CONSTRUCTOR:
          case TdApi.MessageChatUpgradeTo.CONSTRUCTOR:
          case TdApi.MessageContact.CONSTRUCTOR:
          case TdApi.MessageContactRegistered.CONSTRUCTOR:
          case TdApi.MessageCustomServiceAction.CONSTRUCTOR:
          case TdApi.MessageSupergroupChatCreate.CONSTRUCTOR:
          case TdApi.MessageText.CONSTRUCTOR:
          case TdApi.MessageUnsupported.CONSTRUCTOR:
          case TdApi.MessageGameScore.CONSTRUCTOR:
          case TdApi.MessageInvoice.CONSTRUCTOR:
          case TdApi.MessagePassportDataReceived.CONSTRUCTOR:
          case TdApi.MessagePassportDataSent.CONSTRUCTOR:
          case TdApi.MessagePaymentSuccessful.CONSTRUCTOR:
          case TdApi.MessagePaymentSuccessfulBot.CONSTRUCTOR:
          case TdApi.MessagePinMessage.CONSTRUCTOR:
          case TdApi.MessageScreenshotTaken.CONSTRUCTOR:
          case TdApi.MessageWebsiteConnected.CONSTRUCTOR:
            break;
        }
        if (res != 0) {
          String format = Lang.getString(res);
          int startIndex = format.indexOf("**");
          int endIndex = startIndex != -1 ? format.indexOf("**", startIndex + 2) : -1;
          if (startIndex != -1 && endIndex != -1) {
            String arg = format.substring(startIndex + 2, endIndex);
            format = format.substring(0, startIndex) + "%2$s" + format.substring(endIndex + 2);
            this.customFormat = format;
            makeText(0, userArg, new Arg(arg).setActive(true));
          } else {
            makeText(res, userArg);
          }
        }
        break;
      }
      case TYPE_SELF_DESTRUCT_TIMER: {
        if (ChatId.isUserChat(msg.chatId)) {
          if (ttl == 0) {
            if (msg.isOutgoing) {
              makeText(R.string.YouDisabledTimer);
            } else {
              makeText(R.string.XDisabledTimer, new Arg(sender, true));
            }
          } else {
            if (msg.isOutgoing) {
              makePluralDuration(R.string.YouSetTimerSeconds, R.string.YouSetTimerMinutes, R.string.YouSetTimerHours, R.string.YouSetTimerDays, R.string.YouSetTimerWeeks, ttl, TimeUnit.SECONDS);
            } else {
              makePluralDuration(R.string.XSetTimerSeconds, R.string.XSetTimerMinutes, R.string.XSetTimerHours, R.string.XSetTimerDays, R.string.XSetTimerWeeks, ttl, TimeUnit.SECONDS, new Arg(sender, true));
            }
          }
        } else {
          if (ttl == 0) {
            if (msg.isOutgoing) {
              makeText(R.string.YouDisabledAutoDelete);
            } else {
              makeText(R.string.XDisabledAutoDelete, new Arg(sender, true));
            }
          } else {
            if (msg.isOutgoing) {
              makePluralDuration(R.string.YouSetAutoDeleteSeconds, R.string.YouSetAutoDeleteMinutes, R.string.YouSetAutoDeleteHours, R.string.YouSetAutoDeleteDays, R.string.YouSetAutoDeleteWeeks, ttl, TimeUnit.SECONDS);
            } else {
              makePluralDuration(R.string.XSetAutoDeleteSeconds, R.string.XSetAutoDeleteMinutes, R.string.XSetAutoDeleteHours, R.string.XSetAutoDeleteDays, R.string.XSetAutoDeleteWeeks, ttl, TimeUnit.SECONDS, new Arg(sender, true));
            }
          }
        }
        break;
      }
      case TYPE_PAYMENT_SUCCESSFUL: {
        TdApi.Chat chat = getChat();
        if (chat == null) {
          chat = tdlib.chat(msg.chatId);
        }
        TdApi.User botUser = tdlib.chatUser(chat);
        String botName = botUser != null ? botUser.firstName : "bot";
        if (otherMessage != null && otherMessage.content.getConstructor() == TdApi.MessageInvoice.CONSTRUCTOR) {
          makeText(R.string.PaymentSuccessfullyPaid, new Arg(stringValue), new Arg(botName, botUser), new Arg(((TdApi.MessageInvoice) otherMessage.content).title).setActive(true));
        } else {
          makeText(R.string.PaymentSuccessfullyPaidNoItem, new Arg(stringValue), new Arg(botName, botUser));
        }
        break;
      }
      case TYPE_CUSTOM:
        makeText(0);
        break;
      case TYPE_EVENT_INVITE_LINK_REVOKED: {
        if (inviteLinkValue.isPrimary) {
          if (msg.isOutgoing) {
            makeText(R.string.LinkRevokePrimaryYou, new Arg(inviteLinkValue.inviteLink).setIsUrl(true));
          } else {
            makeText(R.string.LinkRevokePrimary, new Arg(sender), new Arg(inviteLinkValue.inviteLink).setIsUrl(true));
          }
        } else {
          if (msg.isOutgoing) {
            makeText(Td.isTemporary(inviteLinkValue) ? R.string.LinkRevokeTempYou : R.string.LinkRevokeOtherYou, new Arg(actionUser), new Arg(inviteLinkValue.inviteLink).setIsUrl(true));
          } else {
            makeText(Td.isTemporary(inviteLinkValue) ? R.string.LinkRevokeTemp : R.string.LinkRevokeOther, new Arg(sender), new Arg(actionUser), new Arg(inviteLinkValue.inviteLink).setIsUrl(true));
          }
        }
        break;
      }
      case TYPE_EVENT_INVITE_LINK_DELETE: {
        if (inviteLinkValue.isPrimary) {
          if (msg.isOutgoing) {
            makeText(R.string.LinkDeletePrimaryYou, new Arg(inviteLinkValue.inviteLink).setIsUrl(true));
          } else {
            makeText(R.string.LinkDeletePrimary, new Arg(sender), new Arg(inviteLinkValue.inviteLink).setIsUrl(true));
          }
        } else {
          if (msg.isOutgoing) {
            makeText(Td.isTemporary(inviteLinkValue) ? R.string.LinkDeleteTempYou : R.string.LinkDeleteOtherYou, new Arg(actionUser), new Arg(inviteLinkValue.inviteLink).setIsUrl(true));
          } else {
            makeText(Td.isTemporary(inviteLinkValue) ? R.string.LinkDeleteTemp : R.string.LinkDeleteOther, new Arg(sender), new Arg(actionUser), new Arg(inviteLinkValue.inviteLink).setIsUrl(true));
          }
        }
        break;
      }
      case TYPE_JOIN_BY_LINK: {
        if (inviteLinkValue != null) {
          if (inviteLinkValue.isPrimary) {
            if (msg.isOutgoing) {
              makeText(msg.isChannelPost ? R.string.LinkJoinChannelPrimaryYou : R.string.LinkJoinPrimaryYou, new Arg(inviteLinkValue.inviteLink).setIsUrl(true));
            } else {
              makeText(msg.isChannelPost ? R.string.LinkJoinChannelPrimary : R.string.LinkJoinPrimary, new Arg(sender), new Arg(inviteLinkValue.inviteLink).setIsUrl(true));
            }
          } else if (Td.isTemporary(inviteLinkValue)) {
            if (msg.isOutgoing) {
              makeText(msg.isChannelPost ? R.string.LinkJoinChannelTempYou : R.string.LinkJoinTempYou, new Arg(actionUser), new Arg(inviteLinkValue.inviteLink).setIsUrl(true));
            } else {
              makeText(msg.isChannelPost ? R.string.LinkJoinChannelTemp : R.string.LinkJoinTemp, new Arg(sender), new Arg(actionUser), new Arg(inviteLinkValue.inviteLink).setIsUrl(true));
            }
          } else {
            if (msg.isOutgoing) {
              makeText(msg.isChannelPost ? R.string.LinkJoinChannelOtherYou : R.string.LinkJoinOtherYou, new Arg(actionUser), new Arg(inviteLinkValue.inviteLink).setIsUrl(true));
            } else {
              makeText(msg.isChannelPost ? R.string.LinkJoinChannelOther : R.string.LinkJoinOther, new Arg(sender), new Arg(actionUser), new Arg(inviteLinkValue.inviteLink).setIsUrl(true));
            }
          }
        } else {
          if (msg.isOutgoing) {
            makeText(msg.isChannelPost ? R.string.YouJoinedByLink : R.string.group_user_join_by_link_self);
          } else {
            makeText(msg.isChannelPost ? R.string.XJoinedByLink : R.string.group_user_join_by_link, new Arg(sender));
          }
        }
        break;
      }
      case TYPE_MIGRATE_FROM:
        makeText(longValue != 0 ? R.string.GroupUpgradedFrom : R.string.GroupUpgraded);
        break;
      case TYPE_MIGRATE_TO:
        makeText(longValue != 0 ? R.string.GroupUpgradedTo : R.string.GroupUpgraded);
        break;
      case TYPE_CONTACT_REGISTERED:
        makeText(R.string.NotificationContactJoined, new Arg(sender));
        break;
      case TYPE_PHOTO_EXPIRED:
        makeText(R.string.AttachPhotoExpired);
        break;
      case TYPE_WEBSITE_CONNECTED:
        makeText(R.string.BotWebsiteAllowed, new Arg(stringValue));
        break;
      case TYPE_VIDEO_EXPIRED:
        makeText(R.string.AttachVideoExpired);
        break;
      case TYPE_EVENT_MESSAGE_DELETED:
        makeText(R.string.EventLogDeletedMessages, new Arg(sender));
        break;
      case TYPE_EVENT_MESSAGE_PINNED:
        makeText(R.string.EventLogPinnedMessages, new Arg(sender));
        break;
      case TYPE_EVENT_POLL_STOPPED: {
        boolean isQuiz = false;
        if (msg.content instanceof TdApiExt.MessageChatEvent && ((TdApiExt.MessageChatEvent) msg.content).event.action.getConstructor() == TdApi.ChatEventPollStopped.CONSTRUCTOR)  {
          TdApi.Message message = ((TdApi.ChatEventPollStopped) ((TdApiExt.MessageChatEvent) msg.content).event.action).message;
          isQuiz = message.content.getConstructor() == TdApi.MessagePoll.CONSTRUCTOR && ((TdApi.MessagePoll) message.content).poll.type.getConstructor() == TdApi.PollTypeQuiz.CONSTRUCTOR;
        }
        makeText(isQuiz ? R.string.EventLogQuizStopped : R.string.EventLogPollStopped, new Arg(sender));
        break;
      }
      case TYPE_EVENT_MESSAGE_UNPINNED:
        makeText(R.string.EventLogUnpinnedMessages, new Arg(sender));
        break;
      case TYPE_EVENT_SIGNATURES_TOGGLED:
        makeText(boolValue ? R.string.EventLogToggledSignaturesOn : R.string.EventLogToggledSignaturesOff, new Arg(sender));
        break;
      case TYPE_EVENT_INVITES_TOGGLED:
        makeText(boolValue ? R.string.EventLogToggledInvitesOn : R.string.EventLogToggledInvitesOff, new Arg(sender));
        break;
      case TYPE_EVENT_PREHISTORY_TOGGLED:
        makeText(boolValue ? R.string.XMadeGroupHistoryVisible : R.string.XMadeGroupHistoryHidden, new Arg(sender));
        break;
      case TYPE_EVENT_LINK_CHANGED:
        if (msg.isChannelPost) {
          makeText(boolValue ? R.string.EventLogChangedChannelLink : R.string.EventLogRemovedChannelLink, new Arg(sender));
        } else {
          makeText(boolValue ? R.string.EventLogChangedGroupLink : R.string.EventLogRemovedGroupLink, new Arg(sender));
        }
        break;
      case TYPE_EVENT_STICKER_PACK_CHANGED:
        makeText(boolValue ? R.string.XChangedGroupStickerSet : R.string.XRemovedGroupStickerSet, new Arg(sender));
        break;
      case TYPE_EVENT_DESCRIPTION_CHANGED:
        if (boolValue) {
          makeText(msg.isChannelPost ? R.string.EventLogEditedChannelDescription : R.string.EventLogEditedGroupDescription, new Arg(sender));
        } else {
          makeText(msg.isChannelPost ? R.string.EventLogRemovedChannelDescription : R.string.EventLogRemovedGroupDescription, new Arg(sender));
        }
        break;
      case TYPE_EVENT_MESSAGE_EDITED:
        makeText(boolValue ? R.string.EventLogEditedCaption : R.string.EventLogEditedMessages, new Arg(sender));
        break;
      case TYPE_EVENT_CAPTION_REMOVED:
        makeText(R.string.EventLogRemovedCaption, new Arg(sender));
        break;
      case TYPE_EVENT_SLOW_MODE_DELAY_CHANGED:
        makeText(R.string.EventLogSlowModeChanged, new Arg(sender), new Arg(Lang.getDuration((int) longValue)));
        break;
      case TYPE_EVENT_SLOW_MODE_DELAY_DISABLED:
        makeText(R.string.EventLogSlowModeDisabled, new Arg(sender));
        break;
      case TYPE_EVENT_LINKED_CHAT_CHANGED:
        if (msg.isChannelPost)
          makeText(R.string.EventLogLinkedGroupChanged, new Arg(sender), new Arg(tdlib, longValue));
        else if (Td.getSenderUserId(msg) == TdConstants.TELEGRAM_CHANNEL_BOT_ACCOUNT_ID || Td.getSenderId(msg) == msg.chatId)
          makeText(R.string.EventLogLinkedChannelChangedUnknown, new Arg(sender), new Arg(tdlib, longValue));
        else
          makeText(R.string.EventLogLinkedChannelChanged, new Arg(sender), new Arg(tdlib, longValue));
        break;
      case TYPE_EVENT_LINKED_CHAT_REMOVED:
        if (msg.isChannelPost)
          makeText(R.string.EventLogLinkedGroupRemoved, new Arg(sender), new Arg(tdlib, longValue));
        else if (Td.getSenderUserId(msg) == TdConstants.TELEGRAM_CHANNEL_BOT_ACCOUNT_ID || Td.getSenderId(msg) == msg.chatId)
          makeText(R.string.EventLogLinkedChannelRemovedUnknown, new Arg(tdlib, longValue));
        else
          makeText(R.string.EventLogLinkedChannelRemoved, new Arg(sender), new Arg(tdlib, longValue));
        break;
      case TYPE_EVENT_LOCATION_SET:
        makeText(R.string.EventLogLocationSet, new Arg(sender), new Arg(stringValue, locationValue));
        break;
      case TYPE_EVENT_VOICE_CHAT_PARTICIPANT_VOLUME_CHANGED: {
        Arg volume = new Arg((longValue / 100) + "%");
        if (msg.isOutgoing) {
          makeText(R.string.EventLogChangedVolumeYou, new Arg(actionSender), volume);
        } else if (actionSender.isSelf()) {
          makeText(R.string.EventLogChangedYourVolume, new Arg(sender), volume);
        } else {
          makeText(R.string.EventLogChangedVolume, new Arg(sender), new Arg(actionSender), volume);
        }
        break;
      }
      case TYPE_EVENT_VOICE_CHAT_MUTE_NEW_PARTICIPANTS_TOGGLED:
        if (msg.isOutgoing) {
          makeText(boolValue ? R.string.EventLogMutedNewParticipantsYou : R.string.EventLogUnmutedNewParticipantsYou);
        } else {
          makeText(boolValue ? R.string.EventLogMutedNewParticipants : R.string.EventLogUnmutedNewParticipants, new Arg(sender));
        }
        break;
      case TYPE_EVENT_VOICE_CHAT_IS_MUTED_TOGGLED:
        if (msg.isOutgoing) {
          makeText(boolValue ? R.string.EventLogMutedParticipantYou : R.string.EventLogUnmutedParticipantYou, new Arg(actionSender));
        } else {
          makeText(boolValue ? R.string.EventLogMutedParticipant : R.string.EventLogUnmutedParticipant, new Arg(sender), new Arg(actionSender));
        }
        break;
      case TYPE_EVENT_LOCATION_CHANGED:
        makeText(R.string.EventLogLocationChanged, new Arg(sender), new Arg(stringValue, locationValue));
        break;
      case TYPE_EVENT_LOCATION_REMOVED:
        makeText(R.string.EventLogLocationRemoved, new Arg(sender));
        break;
      case TYPE_VOICE_CHAT_STARTED:
        if (msg.isOutgoing) {
          makeText(R.string.VoiceChatStartedYou);
        } else if (!sender.isAnonymousGroupAdmin()) {
          makeText(R.string.VoiceChatStartedBy, new Arg(sender));
        } else {
          makeText(R.string.VoiceChatStarted);
        }
        break;
      case TYPE_VOICE_CHAT_ENDED:
        if (msg.isOutgoing) {
          makeText(R.string.VoiceChatEndedYou, new Arg(Lang.getCallDuration((int) longValue)));
        } else if (!sender.isAnonymousGroupAdmin()) {
          makeText(R.string.VoiceChatEndedBy, new Arg(sender), new Arg(Lang.getCallDuration((int) longValue)));
        } else {
          makeText(R.string.VoiceChatEnded, new Arg(Lang.getCallDuration((int) longValue)));
        }
        break;
      case TYPE_INVITE_VOICE_CHAT_PARTICIPANTS: {
        if (sender.isSelf() || msg.isOutgoing) {
          makeText(R.string.VoiceChatInviteOther, new Arg(this, actionUserIds));
        } else if (actionUserIds.length == 1 && tdlib.isSelfUserId(actionUserIds[0])) {
          makeText(R.string.VoiceChatInviteSelf, new Arg(sender));
        } else {
          makeText(R.string.VoiceChatInvite, new Arg(sender), new Arg(this, actionUserIds));
        }
        break;
      }
      case TYPE_PROXIMITY_ALERT: {
        TdApi.MessageProximityAlertTriggered alert = (TdApi.MessageProximityAlertTriggered) msg.content;
        int distance = (int) longValue;
        boolean km = distance >= 1000;
        if (km) {
          distance /= 1000;
        }
        if (tdlib.isSelfSender(alert.traveler)) {
          makePlural(km ? R.string.ProximityAlertYouKM : R.string.ProximityAlertYouM, distance, new Arg(new TdlibSender(tdlib, msg.chatId, alert.watcher)));
        } else if (tdlib.isSelfSender(alert.watcher)) {
          makePlural(km ? R.string.ProximityAlertOtherKM : R.string.ProximityAlertOtherM, distance, new Arg(new TdlibSender(tdlib, msg.chatId, alert.traveler)));
        } else {
          makePlural(km ? R.string.ProximityAlertKM : R.string.ProximityAlertM, distance, new Arg(new TdlibSender(tdlib, msg.chatId, alert.traveler)), new Arg(new TdlibSender(tdlib, msg.chatId, alert.watcher)));
        }
        break;
      }
      default:
        throw new IllegalArgumentException("unsupported " + type);
    }
  }

  @Override
  public MediaViewThumbLocation getMediaThumbLocation (long messageId, View view, int viewTop, int viewBottom, int top) {
    MediaViewThumbLocation location = new MediaViewThumbLocation();
    location.setNoBounce();
    location.setRoundings(getImageContentRadius(false));

    int actualTop = lastTop + viewTop;
    int imageSize = xGroupAvatarRadius * 2;
    int actualBottom = (view.getMeasuredHeight() - (lastTop + imageSize)) + viewBottom;

    location.set(lastLeft, lastTop + top, lastLeft + imageSize, lastTop + imageSize + top);
    location.setClip(0, actualTop < 0 ? -actualTop : 0, 0, actualBottom < 0 ? -actualBottom : 0);
    location.setColorId(manager().useBubbles() ? R.id.theme_color_placeholder : R.id.theme_color_chatBackground);
    return location;
  }
  
  private static String wrap (String str) {
    return Strings.wrapRtlLtr(str);
  }

  private static class Arg {
    private String string, originalString;
    private TdApi.User user;
    private TdlibSender sender;
    private TdApi.Location location;
    private boolean isActive, isUrl;

    private long[] userIds;
    private String[] strings;
    private long chatId;

    public Arg (TGMessage context, long[] userIds) {
      this.userIds = userIds;
      this.strings = new String[userIds.length];
      int i = 0;
      for (long userId : userIds) {
        strings[i] = wrap(TD.getUserName(userId, context.userForId(userId)));
        i++;
      }
    }

    public Arg (String string) {
      this.string = wrap(this.originalString = string);
    }

    public Arg (TdApi.User user) {
      this(user, false);
    }

    public Arg (TdlibSender sender) {
      this(sender, false);
    }

    public Arg (TdlibSender sender, boolean firstNameOnly) {
      this.string = firstNameOnly ? sender.getNameShort() : sender.getName();
      this.sender = sender;
    }

    public Arg (String address, TdApi.Location location) {
      this.string = address;
      this.location = location;
    }

    public Arg (Tdlib tdlib, long chatId) {
      this.string = tdlib.chatTitle(chatId);
      this.chatId = chatId;
    }

    public Arg (TdApi.User user, boolean firstNameOnly) {
      this.string = user != null ? (firstNameOnly && !TD.isUserDeleted(user) ? user.firstName : TD.getUserName(user)) : Lang.getString(R.string.Somebody);
      this.user = user;
    }

    public Arg (String string, TdApi.User user) {
      this.string = wrap(string);
      this.user = user;
    }

    public Arg setActive (boolean isActive) {
      this.isActive = isActive;
      return this;
    }

    public Arg setIsUrl (boolean isUrl) {
      this.isUrl = isUrl;
      if (isUrl) {
        string = StringUtils.urlWithoutProtocol(originalString);
      }
      return this;
    }

    public boolean isActive () {
      return user != null || sender != null || chatId != 0 || location != null || isUrl || isActive;
    }

    @Override
    public final String toString () {
      if (strings != null && strings.length > 0) {
        return " ";
      } else {
        return string;
      }
    }

    public void onClick (TGMessage context) {
      if (user != null) {
        context.tdlib.ui().openPrivateProfile(context.controller(), user.id, context.openParameters());
      } else if (sender != null) {
        if (sender.isUser())
          context.tdlib.ui().openPrivateProfile(context.controller(), sender.getUserId(), context.openParameters());
        else if (sender.isChat())
          context.tdlib.ui().openChatProfile(context.controller(), sender.getChatId(), null, context.openParameters());
      } else if (chatId != 0) {
        context.tdlib.ui().openChat(context.controller(), chatId, new TdlibUi.ChatOpenParameters().keepStack().removeDuplicates());
      } else if (location != null) {
        context.tdlib.ui().openMap(context, new MapController.Args(location.latitude, location.longitude).setChatId(context.msg.chatId, context.messagesController().getMessageThreadId()).setLocationOwnerChatId(context.msg.chatId));
      } else if (isUrl) {
        context.tdlib.ui().openUrlOptions(context.controller(), originalString, context.openParameters());
      }
    }
  }

  private CharSequence text;
  private boolean textFakeBold;

  private void setText (CharSequence text) {
    this.text = text;
    this.textFakeBold = Text.needFakeBold(text);
  }

  private void makeText (@StringRes int resId, Arg... args) {
    makeTextImpl(resId, false, 0, args);
  }

  private void makePluralDuration (@StringRes int secondsRes, @StringRes int minutesRes, @StringRes int hoursRes, @StringRes int daysRes, @StringRes int weeksRes, final long time, final TimeUnit unit, Arg... args) {
    int days = (int) unit.toDays(time);
    if (days >= 7) {
      makePlural(weeksRes, days / 7, args);
      return;
    }
    if (days > 0) {
      makePlural(daysRes, days, args);
      return;
    }
    int hours = (int) unit.toHours(time);
    if (hours > 0) {
      makePlural(hoursRes, hours, args);
      return;
    }
    int minutes = (int) unit.toMinutes(time);
    if (minutes > 0) {
      makePlural(minutesRes, minutes, args);
      return;
    }
    makePlural(secondsRes, (int) unit.toSeconds(time), args);
  }

  private void makePlural (@StringRes int pluralResId, int num, Arg... args) {
    makeTextImpl(pluralResId, true, num, args);
  }

  private void makeTextImpl (@StringRes int resId, boolean isPlural, int num, Arg... args) {
    if ((args == null || args.length == 0) && !isPlural) {
      setText(resId != 0 ? Lang.getString(resId) : customFormat);
      return;
    }

    final boolean needColoredNames = needColoredNames();

    Lang.SpanCreator spanCreator = (target, argStart, argEnd, argIndex, needFakeBold) -> {
      if (isPlural) {
        if (argIndex == 0) {
          CustomTypefaceSpan span = new CustomTypefaceSpan(needFakeBold ? Fonts.getRobotoRegular() : Fonts.getRobotoBold(), 0);
          span.setFakeBold(needFakeBold);
          return span;
        }
        argIndex--;
      }
      if (args == null || argIndex >= args.length) {
        return null;
      }
      Arg data = args[argIndex];
      if (data.userIds != null) {
        return new CustomTypefaceSpan(null, 0).setTag(data);
      }

      boolean isActive = data.isActive();

      CustomTypefaceSpan span = new CustomTypefaceSpan(needFakeBold ? Fonts.getRobotoRegular() : (useBubbles() || !isActive ? Fonts.getRobotoBold() : Fonts.getRobotoMedium()), !useBubbles() && isActive ? R.id.theme_color_messageAuthor : 0);
      span.setFakeBold(needFakeBold);

      if (isActive) {
        if (data.isActive) {
          span.setOnClickListener((view, span12) -> {
            if (type == TYPE_SCORE || type == TYPE_PINNED_MESSAGE || type == TYPE_PAYMENT_SUCCESSFUL) {
              long otherMessageId;
              switch (type) {
                case TYPE_PINNED_MESSAGE:
                  otherMessageId = ((TdApi.MessagePinMessage) msg.content).messageId;
                  break;
                case TYPE_SCORE:
                  otherMessageId = ((TdApi.MessageGameScore) msg.content).gameMessageId;
                  break;
                case TYPE_PAYMENT_SUCCESSFUL:
                  otherMessageId = msg.replyToMessageId;
                  break;
                default:
                  otherMessageId = 0;
                  break;
              }
              if (otherMessageId != 0) {
                highlightOtherMessage(otherMessageId);
              }
            } else {
              data.onClick(this);
            }
            return true;
          });
        } else {
          int colorId;
          if (needColoredNames && (data.user != null || data.sender != null || data.chatId != 0)) {
            colorId = data.sender != null ? data.sender.getNameColorId() : TD.getNameColorId(data.user != null ? TD.getAvatarColorId(data.user.id, tdlib.myUserId()) : tdlib.chatAvatarColorId(data.chatId));
          } else {
            colorId = R.id.theme_color_messageAuthor;
          }
          span.setTransparencyColorId(colorId, manager.controller().wallpaper());
          if (!useBubbles()) {
            span.setColorId(colorId);
          }
          span.setOnClickListener((view, span1) -> {
            data.onClick(this);
            return true;
          });
        }
      }

      return span;
    };

    if (resId != 0) {
      if (isPlural) {
        setText(Lang.plural(resId, num, spanCreator, (Object[]) args));
      } else {
        setText(Lang.getString(resId, spanCreator, (Object[]) args));
      }
    } else if (customFormat != null) {
      try {
        setText(Lang.formatString(customFormat, spanCreator, (Object[]) args));
      } catch (Throwable t) {
        Log.e("Broken string format: %s", t, customFormat);
        setText(customFormat);
      }
    }

    if (text instanceof SpannableStringBuilder && args != null && args.length > 0) {
      SpannableStringBuilder spanned = (SpannableStringBuilder) text;
      boolean hasList = false;
      for (Arg arg : args) {
        if (arg.userIds != null) {
          hasList = true;
          break;
        }
      }
      if (hasList) {
        CustomTypefaceSpan[] spans = spanned.getSpans(0, spanned.length(), CustomTypefaceSpan.class);
        if (spans != null && spans.length > 0) {
          for (CustomTypefaceSpan span : spans) {
            Arg data = (Arg) span.getTag();
            if (data != null && data.userIds != null) {
              int spanStart = spanned.getSpanStart(span);
              int spanEnd = spanned.getSpanEnd(span);
              spanned.removeSpan(span);
              if (spanStart >= 0 && spanEnd >= 0) {
                spanned.delete(spanStart, spanEnd);
                int i = spanStart;
                String concatSeparator = Lang.getConcatSeparator();
                String concatSeparatorLast = Lang.getConcatSeparatorLast(true);
                int index = 0;
                for (long userId : data.userIds) {
                  if (index > 0) {
                    if (index == data.userIds.length - 1) {
                      spanned.insert(i, concatSeparatorLast);
                      i += concatSeparatorLast.length();
                    } else {
                      spanned.insert(i, concatSeparator);
                      i += concatSeparator.length();
                    }
                  }
                  String str = data.strings[index];
                  boolean needFake = Text.needFakeBold(str);
                  int colorId;
                  if (needColoredNames) {
                    colorId = TD.getNameColorId(TD.getAvatarColorId(userId, tdlib.myUserId()));
                  } else {
                    colorId = R.id.theme_color_messageAuthor;
                  }
                  spanned.insert(i, str);

                  span = new CustomTypefaceSpan(needFake ? Fonts.getRobotoRegular() : (useBubbles() ? Fonts.getRobotoBold() : Fonts.getRobotoMedium()), useBubbles() ? 0 : colorId).setTransparencyColorId(colorId, manager.controller().wallpaper());
                  span.setFakeBold(needFake);
                  span.setOnClickListener((view, span13) -> {
                    tdlib.ui().openPrivateProfile(controller(), userId, openParameters());
                    return true;
                  });
                  spanned.setSpan(span, i, i + str.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                  i += str.length();
                  index++;
                }
              }
            }
          }
        }
      }
    }

    if (!StringUtils.isEmpty(text)) {
      text = Emoji.instance().replaceEmoji(text);
    }
  }

  private TextPaint getPaint (boolean needFakeBold, boolean willDraw) {
    if (willDraw) {
      return useBubbles() ? Paints.getBoldPaint13(needFakeBold, 0xffffffff) : Paints.getBoldPaint15(needFakeBold, Theme.textAccentColor());
    } else {
      return useBubbles() ? Paints.getBoldPaint13(needFakeBold) : Paints.getBoldPaint15(needFakeBold);
    }
  }

  @Override
  protected boolean headerDisabled () {
    return true;
  }

  @Override
  protected boolean disableBubble () {
    return true;
  }

  @Override
  protected void buildContent (int maxWidth) {
    if (xContentHeight == 0f) {
      initSizes();
    }

    pWidth = this.width - xContentPadding;

    setText();
    layoutText();
  }

  private void layoutText () {
    if (text != null) {
      TextPaint paint = getPaint(textFakeBold, false);
      BoringLayout.Metrics metrics = BoringLayout.isBoring(text, paint);
      if (metrics != null && metrics.width <= pWidth) {
        layout = new BoringLayout(text, paint, pWidth, Layout.Alignment.ALIGN_CENTER, 1.0f, Screen.dp(4f), metrics, false);
      } else {
        layout = new StaticLayout(text, 0, text.length(), paint, pWidth, Layout.Alignment.ALIGN_CENTER, 1.0f, Screen.dp(4f), false);
      }
    } else {
      layout = null;
    }
  }

  @Override
  public final int getImageContentRadius (boolean isPreview) {
    return xGroupAvatarRadius;
  }

  @Override
  public void requestImage (ImageReceiver receiver) {
    receiver.requestFile(avatar);
  }

  @Override
  protected void drawContent (MessageView view, Canvas c, int startX, int startY, int maxWidth, Receiver preview, Receiver receiver) {
    if (layout == null) {
      return;
    }
    c.save();
    c.translate(xLeftPadding, getTextY());
    int textColor;
    if (useBubbles()) {
      final int lineCount = layout.getLineCount();
      textColor = getBubbleDateTextColor();
      int color = getBubbleDateBackgroundColor();
      RectF rectF = Paints.getRectF();
      int padding = Screen.dp(8f);
      int offset = Screen.dp(5f);
      int height = Screen.dp(26f);
      final int radius = Screen.dp(Theme.getBubbleDateRadius());

      if (lineCount == 1) {
        rectF.set(layout.getLineLeft(0) - padding, layout.getLineTop(0) - offset, layout.getLineRight(0) + padding, layout.getLineTop(0) + height - offset);
        c.drawRoundRect(rectF, radius, radius, Paints.fillingPaint(color));
      } else {
        if (Config.BUBBLE_USE_SEPARATE_BACKGROUND_FOR_SERVICE_MESSAGES) {
          for (int i = 0; i < lineCount; i++) {
            boolean isFirst = i == 0;
            boolean isLast = i == lineCount - 1;

            float prevWidth = isFirst ? -1 : layout.getLineWidth(i - 1);
            float currentWidth = layout.getLineWidth(i);
            float nextWidth = isLast ? -1 : layout.getLineWidth(i + 1);

            rectF.left = layout.getLineLeft(i) - padding;
            rectF.right = layout.getLineRight(i) + padding;
            rectF.top = layout.getLineTop(i) - offset;
            rectF.bottom = rectF.top + height;

            c.save();

            if (currentWidth <= nextWidth && !isLast) {
              c.clipRect(rectF.left, rectF.top, rectF.right, layout.getLineTop(i + 1) - offset);
            } else if (currentWidth <= prevWidth && !isFirst) {
              c.clipRect(rectF.left, layout.getLineTop(i - 1) - offset + height, rectF.right, rectF.bottom);
            }
            if (currentWidth > prevWidth && !isFirst && currentWidth - prevWidth <= radius / 2) {
              c.clipRect(rectF);
              float origTop = rectF.top;
              int diff = (int) (layout.getLineTop(i - 1) + height - rectF.top);
              rectF.top -= radius - (currentWidth - prevWidth) - diff;
              rectF.top = Math.min(rectF.top, origTop);
            }
            if (currentWidth > nextWidth && !isLast && currentWidth - nextWidth <= radius / 2) {
              c.clipRect(rectF);
              float origBottom = rectF.bottom;
              int diff = (int) (rectF.bottom - (layout.getLineTop(i + 1) - offset));
              rectF.bottom += radius - (currentWidth - nextWidth) - diff;
              rectF.bottom = Math.max(rectF.bottom, origBottom);
            }

            c.drawRoundRect(rectF, radius, radius, Paints.fillingPaint(color));

            c.restore();
          }
        } else {
          rectF.set(0, 0, 0, 0);
          for (int i = 0; i < lineCount; i++) {
            float lineLeft = layout.getLineLeft(i);
            float lineRight = layout.getLineRight(i);

            if (rectF.left == 0 || rectF.left > lineLeft) {
              rectF.left = lineLeft;
            }
            if (rectF.right == 0 || rectF.right < lineRight) {
              rectF.right = lineRight;
            }
          }

          rectF.left -= padding;
          rectF.right += padding;

          rectF.top = layout.getLineTop(0) - offset;
          rectF.bottom = layout.getLineTop(lineCount - 1) - offset + height;

          c.drawRoundRect(rectF, radius, radius, Paints.fillingPaint(color));
        }
      }
    } else {
      textColor = Theme.textAccentColor();
    }
    layout.getPaint().setColor(textColor);
    layout.draw(c);
    c.restore();
    if (avatar != null) {
      int x = view.getMeasuredWidth() / 2;
      int y = startY + layout.getHeight() + Screen.dp(14f) + xGroupAvatarRadius;

      if (receiver.needPlaceholder()) {
        c.drawCircle(x, y, xGroupAvatarRadius, Paints.getPlaceholderPaint());
      }
      receiver.setBounds(lastLeft = x - xGroupAvatarRadius, lastTop = y - xGroupAvatarRadius, x + xGroupAvatarRadius, y + xGroupAvatarRadius);
      receiver.draw(c);
    }
  }

  private int lastLeft, lastTop;

  @Override
  protected int getContentHeight () {
    return layout.getHeight() + Screen.dp(useBubbles() ? .5f : 6.5f) + (avatar != null ? xGroupAvatarPadding + xGroupAvatarSize : 0);
  }

  @Override
  public boolean needImageReceiver () {
    return true;
  }

  private float currentX, currentY;
  private CustomTypefaceSpan caughtLink;
  private boolean caughtPhoto;

  private void clearTouchValues () {
    currentX = 0f;
    currentY = 0f;
    caughtPhoto = false;
    caughtLink = null;
  }

  @Override
  public boolean onTouchEvent (MessageView view, MotionEvent e) {
    float x = e.getX();
    float y = e.getY();

    int pGroupAvatarX = view.getMeasuredWidth() / 2;
    int pGroupAvatarY = getContentY() + layout.getHeight() + Screen.dp(14f) + xGroupAvatarRadius;

    switch (e.getAction()) {
      case MotionEvent.ACTION_DOWN: {
        clearTouchValues();

        if (avatar != null && x >= pGroupAvatarX - xGroupAvatarRadius && x <= pGroupAvatarX + xGroupAvatarRadius && y >= pGroupAvatarY - xGroupAvatarRadius && y <= pGroupAvatarY + xGroupAvatarRadius) {
          caughtPhoto = true;
          currentX = x;
          currentY = y;

          return true;
        }
        if (x > xLeftPadding && x < xLeftPadding + layout.getWidth() && y > getTextY() && y < getTextY() + layout.getHeight() && (caughtLink = catchText(e)) != null) {
          currentX = x;
          currentY = y;

          return true;
        }
        break;
      }
      case MotionEvent.ACTION_CANCEL: {
        clearTouchValues();
        break;
      }
      case MotionEvent.ACTION_UP: {
        if (caughtLink == null && !caughtPhoto) {
          return false;
        }

        boolean res = false;

        if (Math.abs(currentX - x) <= Screen.getTouchSlop() && Math.abs(currentY - y) <= Screen.getTouchSlop()) {
          currentX = 0f;
          currentY = 0f;
          if (caughtPhoto) {
            MediaViewController.openFromMessage(this);
            res = true;
          } else if (caughtLink != null) {
            caughtLink.onClick(view);
            res = true;
          }
        }

        clearTouchValues();

        if (res) {
          performClickSoundFeedback();
          return true;
        }

        break;
      }
    }
    return false;
  }

  private Path path;
  private RectF bounds;

  private int getTextY () {
    return getContentY() + (useBubbles() ? 0 : Screen.dp(3f));
  }

  public CustomTypefaceSpan catchText (MotionEvent e) {
    if (!(text instanceof Spannable))
      return null;
    Spannable spanned = (Spannable) text;
    CustomTypefaceSpan[] args = spanned.getSpans(0, text.length(), CustomTypefaceSpan.class);

    if (args.length == 0) {
      return null;
    }

    float x = e.getX();
    float y = e.getY();

    if (path == null) {
      path = new Path();
      bounds = new RectF();
    }

    for (CustomTypefaceSpan arg : args) {
      if (arg.getOnClickListener() == null) {
        continue;
      }

      int start = spanned.getSpanStart(arg);
      int end = spanned.getSpanEnd(arg);

      layout.getSelectionPath(start, end, path);
      path.computeBounds(bounds, true);
      bounds.offset(xLeftPadding, getTextY());

      if (bounds.contains(x, y)) {
        return arg;
      }
    }

    return null;
  }

  // Sizes

  private static int xContentHeight;
  private static int xContentPadding;
  private static int xLeftPadding;
  private static int xGroupAvatarSize, xGroupAvatarRadius, xGroupAvatarPadding;

  private static void initSizes () {
    xContentHeight = Screen.dp(38f);
    xLeftPadding = Screen.dp(12f);
    xContentPadding = xLeftPadding * 2;
    // xMessagePadding = Screen.dp(22f);

    xGroupAvatarRadius = Screen.dp(28f);
    xGroupAvatarSize = xGroupAvatarRadius * 2;
    xGroupAvatarPadding = Screen.dp(4f);
  }
}
