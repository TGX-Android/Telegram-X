/*
 * This file is a part of Telegram X
 * Copyright © 2014-2022 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 02/09/2022, 19:31.
 */

package org.thunderdog.challegram.data;

import android.text.Spanned;
import android.text.style.CharacterStyle;
import android.text.style.ClickableSpan;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.chat.MessagesManager;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.telegram.TdlibSender;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.ui.MapController;
import org.thunderdog.challegram.util.text.FormattedText;
import org.thunderdog.challegram.util.text.TextEntity;
import org.thunderdog.challegram.util.text.TextEntityCustom;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import me.vkryl.core.CurrencyUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.core.lambda.Filter;
import me.vkryl.core.lambda.RunnableData;
import me.vkryl.td.ChatId;
import me.vkryl.td.MessageId;
import me.vkryl.td.Td;

public class TGMessageService extends TGMessage {
  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.MessageContactRegistered contactRegistered) {
    super(context, msg);
    setTextCreator(() ->
      getText(
        R.string.NotificationContactJoined,
        new SenderArgument(sender)
      )
    );
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.MessageScreenshotTaken screenshotTaken) {
    super(context, msg);
    setTextCreator(() -> {
      if (msg.isOutgoing) {
        return getText(R.string.YouTookAScreenshot);
      } else {
        return getText(
          R.string.XTookAScreenshot,
          new SenderArgument(sender, true)
        );
      }
    });
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.MessageExpiredPhoto expiredPhoto) {
    super(context, msg);
    setTextCreator(() ->
      getText(R.string.AttachPhotoExpired)
    );
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.MessageExpiredVideo expiredVideo) {
    super(context, msg);
    setTextCreator(() ->
      getText(R.string.AttachVideoExpired)
    );
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.MessageProximityAlertTriggered proximityAlertTriggered) {
    super(context, msg);
    TdlibSender travelerSender = new TdlibSender(tdlib(), msg.chatId, proximityAlertTriggered.travelerId);
    TdlibSender watcherSender = new TdlibSender(tdlib(), msg.chatId, proximityAlertTriggered.watcherId);
    setTextCreator(() -> {
      boolean inKilometers = proximityAlertTriggered.distance >= 1000;
      int distance = inKilometers ?
        (proximityAlertTriggered.distance / 1000) :
        proximityAlertTriggered.distance;
      if (travelerSender.isSelf()) {
        return getPlural(
          inKilometers ?
            R.string.ProximityAlertYouKM :
            R.string.ProximityAlertYouM,
          distance,
          new SenderArgument(watcherSender)
        );
      } else if (watcherSender.isSelf()) {
        return getPlural(
          inKilometers ?
            R.string.ProximityAlertOtherKM :
            R.string.ProximityAlertOtherM,
          distance,
          new SenderArgument(travelerSender)
        );
      } else {
        return getPlural(
          inKilometers ?
            R.string.ProximityAlertKM :
            R.string.ProximityAlertM,
          distance,
          new SenderArgument(travelerSender),
          new SenderArgument(watcherSender)
        );
      }
    });
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.MessagePinMessage pinMessage) {
    super(context, msg);
    setTextCreator(() -> {
      if (msg.isChannelPost) {
        return getText(R.string.PinnedMessageChanged);
      } else if (msg.isOutgoing) {
        return getText(R.string.YouPinnedMessage);
      } else {
        return getText(
          R.string.NotificationActionPinnedNoTextChannel,
          new SenderArgument(sender)
        );
      }
    });
    setDisplayMessage(msg.chatId, pinMessage.messageId, message -> {
      // TODO: pin
      return false;
    });
  }

  // Group & Channel Messages

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.MessageBasicGroupChatCreate basicGroupCreate) {
    super(context, msg);
    setTextCreator(() -> {
      if (msg.isChannelPost) {
        return getText(R.string.channel_create_somebody, new BoldArgument(basicGroupCreate.title));
      } else if (msg.isOutgoing) {
        return getText(R.string.group_create_you, new BoldArgument(basicGroupCreate.title));
      } else {
        return getText(R.string.group_created, new SenderArgument(sender), new BoldArgument(basicGroupCreate.title));
      }
    });
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.MessageSupergroupChatCreate supergroupCreate) {
    super(context, msg);
    setTextCreator(() -> {
      if (msg.isChannelPost) {
        return getText(R.string.channel_create_somebody, new BoldArgument(supergroupCreate.title));
      } else if (msg.isOutgoing) {
        return getText(R.string.group_create_you, new BoldArgument(supergroupCreate.title));
      } else {
        return getText(R.string.group_created, new SenderArgument(sender), new BoldArgument(supergroupCreate.title));
      }
    });
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.MessageChatChangeTitle changeTitle) {
    super(context, msg);
    setTextCreator(() -> {
      if (msg.isChannelPost) {
        return getText(R.string.ChannelRenamed, new BoldArgument(changeTitle.title));
      } else if (msg.isOutgoing) {
        return getText(R.string.group_title_changed_you, new BoldArgument(changeTitle.title));
      } else {
        return getText(R.string.group_title_changed, new SenderArgument(sender), new BoldArgument(changeTitle.title));
      }
    });
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.MessageChatChangePhoto changePhoto) {
    super(context, msg);
    setTextCreator(() -> {
      if (msg.isChannelPost) {
        return getText(R.string.ActionChannelChangedPhoto);
      } else if (msg.isOutgoing) {
        return getText(R.string.group_photo_changed_you);
      } else {
        return getText(R.string.group_photo_changed, new SenderArgument(sender));
      }
    });
    setDisplayChatPhoto(changePhoto.photo);
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.MessageChatDeletePhoto deletePhoto) {
    super(context, msg);
    setTextCreator(() -> {
      if (msg.isChannelPost) {
        return getText(R.string.ActionChannelRemovedPhoto);
      } else if (msg.isOutgoing) {
        return getText(R.string.group_photo_deleted_you);
      } else {
        return getText(R.string.group_photo_deleted, new SenderArgument(sender));
      }
    });
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.MessageChatUpgradeTo upgradeToSupergroup) {
    super(context, msg);
    setTextCreator(() ->
      getText(R.string.GroupUpgraded)
    );
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.MessageChatUpgradeFrom upgradeFromBasicGroup) {
    super(context, msg);
    setTextCreator(() ->
      getText(
        upgradeFromBasicGroup.basicGroupId != 0 ?
          R.string.GroupUpgradedFrom :
          R.string.GroupUpgraded
      )
    );
    if (upgradeFromBasicGroup.basicGroupId != 0) {
      setOnClickListener(() ->
        tdlib.ui().openBasicGroupChat(controller(), upgradeFromBasicGroup.basicGroupId, new TdlibUi.ChatOpenParameters().urlOpenParameters(openParameters()))
      );
    }
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.MessageChatJoinByLink joinByLink) {
    super(context, msg);
    setTextCreator(() -> {
      if (msg.isOutgoing) {
        return getText(
          msg.isChannelPost ?
            R.string.YouJoinedByLink :
            R.string.group_user_join_by_link_self
        );
      } else {
        return getText(
          msg.isChannelPost ?
            R.string.XJoinedByLink :
            R.string.group_user_join_by_link,
          new SenderArgument(sender)
        );
      }
    });
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.MessageChatJoinByRequest joinByRequest) {
    super(context, msg);
    setTextCreator(() -> {
      if (msg.isOutgoing) {
        return getText(
          msg.isChannelPost ?
            R.string.YouAcceptedToChannel :
            R.string.YouAcceptedToGroup
        );
      } else {
        return getText(
          msg.isChannelPost ?
            R.string.XAcceptedToChannel :
            R.string.XAcceptedToGroup,
          new SenderArgument(sender)
        );
      }
    });
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.MessageChatAddMembers addMembers) {
    super(context, msg); // TODO: list
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.MessageChatDeleteMember deleteMember) {
    super(context, msg);
    TdlibSender targetSender = new TdlibSender(tdlib, msg.chatId, new TdApi.MessageSenderUser(deleteMember.userId));
    setTextCreator(() -> {
      if (sender.isSameSender(targetSender)) {
        if (sender.isSelf()) {
          return getText(
            msg.isChannelPost ?
              R.string.channel_user_remove_self :
              R.string.group_user_remove_self
          );
        } else {
          return getText(
            msg.isChannelPost ?
              R.string.channel_user_remove :
              R.string.group_user_remove,
            new SenderArgument(sender)
          );
        }
      } else {
        if (sender.isSelf()) {
          return getText(
            R.string.group_user_self_removed,
            new SenderArgument(targetSender)
          );
        } else if (targetSender.isSelf()) {
          return getText(
            R.string.group_user_removed_self,
            new SenderArgument(sender)
          );
        } else {
          return getText(
            R.string.group_user_removed,
            new SenderArgument(sender),
            new SenderArgument(targetSender)
          );
        }
      }
    });
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.MessageWebsiteConnected websiteConnected) {
    super(context, msg);
    setTextCreator(() ->
      getText(
        R.string.BotWebsiteAllowed,
        new BoldArgument(websiteConnected.domainName)
      )
    );
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.MessageChatSetTtl setTtl) {
    super(context, msg);
    setTextCreator(() -> {
      boolean isUserChat = ChatId.isUserChat(msg.chatId);
      if (setTtl.ttl == 0) {
        if (msg.isOutgoing) {
          return getText(
            isUserChat ?
              R.string.YouDisabledTimer :
              R.string.YouDisabledAutoDelete
          );
        } else {
          return getText(
            isUserChat ?
              R.string.XDisabledTimer :
            msg.isChannelPost ?
              R.string.XDisabledAutoDeletePosts :
              R.string.XDisabledAutoDelete,
            new SenderArgument(sender, true)
          );
        }
      } else if (isUserChat) {
        if (msg.isOutgoing) {
          return getDuration(
            R.string.YouSetTimerSeconds, R.string.YouSetTimerMinutes, R.string.YouSetTimerHours, R.string.YouSetTimerDays, R.string.YouSetTimerWeeks, R.string.YouSetTimerMonths,
            setTtl.ttl, TimeUnit.SECONDS
          );
        } else {
          return getDuration(
            R.string.XSetTimerSeconds, R.string.XSetTimerMinutes, R.string.XSetTimerHours, R.string.XSetTimerDays, R.string.XSetTimerWeeks, R.string.XSetTimerMonths,
            setTtl.ttl, TimeUnit.SECONDS,
            new SenderArgument(sender, true)
          );
        }
      } else if (msg.isChannelPost) {
        if (msg.isOutgoing) {
          return getDuration(
            R.string.YouSetAutoDeletePostsSeconds, R.string.YouSetAutoDeletePostsMinutes, R.string.YouSetAutoDeletePostsHours, R.string.YouSetAutoDeletePostsDays, R.string.YouSetAutoDeletePostsWeeks, R.string.YouSetAutoDeletePostsMonths,
            setTtl.ttl, TimeUnit.SECONDS
          );
        } else {
          return getDuration(
            R.string.XSetAutoDeletePostsSeconds, R.string.XSetAutoDeletePostsMinutes, R.string.XSetAutoDeletePostsHours, R.string.XSetAutoDeletePostsDays, R.string.XSetAutoDeletePostsWeeks, R.string.XSetAutoDeletePostsMonths,
            setTtl.ttl, TimeUnit.SECONDS,
            new SenderArgument(sender, true)
          );
        }
      } else {
        if (msg.isOutgoing) {
          return getDuration(
            R.string.YouSetAutoDeleteSeconds, R.string.YouSetAutoDeleteMinutes, R.string.YouSetAutoDeleteHours, R.string.YouSetAutoDeleteDays, R.string.YouSetAutoDeleteWeeks, R.string.YouSetAutoDeleteMonths,
            setTtl.ttl, TimeUnit.SECONDS
          );
        } else {
          return getDuration(
            R.string.XSetAutoDeleteSeconds, R.string.XSetAutoDeleteMinutes, R.string.XSetAutoDeleteHours, R.string.XSetAutoDeleteDays, R.string.XSetAutoDeleteWeeks, R.string.XSetAutoDeleteMonths,
            setTtl.ttl, TimeUnit.SECONDS,
            new SenderArgument(sender, true)
          );
        }
      }
    });
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.MessageGameScore gameScore) {
    super(context, msg);
    setTextCreator(() -> {
      if (msg.isOutgoing) {
        return getPlural(
          R.string.game_ActionYouScored,
          gameScore.score
        );
      } else {
        return getPlural(
          R.string.game_ActionUserScored,
          gameScore.score,
          new SenderArgument(sender)
        );
      }
    });
    if (gameScore.gameMessageId != 0) {
      setDisplayMessage(msg.chatId, gameScore.gameMessageId, (message) -> {
        if (message.content.getConstructor() != TdApi.MessageGame.CONSTRUCTOR) {
          return false;
        }
        setTextCreator(() -> {
          if (msg.isOutgoing) {
            return getPlural(
              R.string.game_ActionYouScoredInGame,
              gameScore.score,
              new GameArgument(message)
            );
          } else {
            return getPlural(
              R.string.game_ActionUserScoredInGame,
              gameScore.score,
              new SenderArgument(sender),
              new GameArgument(message)
            );
          }
        });
        return true;
      });
    }
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.MessagePaymentSuccessful paymentSuccessful) {
    super(context, msg); // TODO: update with recurring payment support
    String amount = CurrencyUtils.buildAmount(paymentSuccessful.currency, paymentSuccessful.totalAmount);
    setTextCreator(() ->
      getText(
        R.string.PaymentSuccessfullyPaidNoItem,
        new BoldArgument(amount),
        new SenderArgument(sender)
      )
    );
    if (paymentSuccessful.invoiceChatId != 0 && paymentSuccessful.invoiceMessageId != 0) {
      setDisplayMessage(
        paymentSuccessful.invoiceChatId,
        paymentSuccessful.invoiceMessageId,
        message -> {
          if (message.content.getConstructor() != TdApi.MessageInvoice.CONSTRUCTOR) {
            return false;
          }
          setTextCreator(() ->
            getText(
              R.string.PaymentSuccessfullyPaid,
              new BoldArgument(amount),
              new SenderArgument(sender),
              new InvoiceArgument(message)
            )
          );
          return true;
        }
      );
    }
  }

  // Video chats

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.MessageVideoChatStarted videoChatStarted) {
    super(context, msg);
    setTextCreator(() -> {
      if (msg.isOutgoing) {
        return getText(
          msg.isChannelPost ?
            R.string.LiveStreamStartedYou :
            R.string.VoiceChatStartedYou
        );
      } else if (sender.isAnonymousGroupAdmin()) {
        return getText(
          msg.isChannelPost ?
            R.string.LiveStreamStarted :
            R.string.VoiceChatStarted
        );
      } else {
        return getText(
          msg.isChannelPost ?
            R.string.LiveStreamStartedBy :
            R.string.VoiceChatStartedBy,
          new SenderArgument(sender)
        );
      }
    });
    setOnClickListener(() ->
      tdlib.ui().openVoiceChat(controller(), videoChatStarted.groupCallId, openParameters())
    );
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.MessageVideoChatScheduled videoChatScheduled) {
    super(context, msg);
    setTextCreator(() -> {
      String timestamp = Lang.getMessageTimestamp(videoChatScheduled.startDate, TimeUnit.SECONDS);
      return getText(
        msg.isChannelPost ?
          R.string.LiveStreamScheduledOn :
          R.string.VideoChatScheduledFor,
        new BoldArgument(timestamp)
      );
    });
    setOnClickListener(() ->
      tdlib.ui().openVoiceChat(controller(), videoChatScheduled.groupCallId, openParameters())
    );
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.MessageVideoChatEnded videoChatEnded) {
    super(context, msg);
    setTextCreator(() -> {
      String duration = Lang.getCallDuration(videoChatEnded.duration);
      if (msg.isOutgoing) {
        return getText(
          msg.isChannelPost ?
            R.string.LiveStreamEndedYou :
            R.string.VoiceChatEndedYou,
          new BoldArgument(duration)
        );
      } else if (sender.isAnonymousGroupAdmin()) {
        return getText(
          msg.isChannelPost ?
            R.string.LiveStreamEnded :
            R.string.VoiceChatEnded,
          new BoldArgument(duration)
        );
      } else {
        return getText(
          msg.isChannelPost ?
            R.string.LiveStreamEndedBy :
            R.string.VoiceChatEndedBy,
          new SenderArgument(sender),
          new BoldArgument(duration)
        );
      }
    });
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.MessageInviteVideoChatParticipants inviteVideoChatParticipants) {
    super(context, msg); // TODO: list
  }

  // Custom server's service message

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.MessageCustomServiceAction custom) {
    super(context, msg);
    setTextCreator(() ->
      new FormattedText(custom.text)
    );
  }

  // Recent actions (chat events)

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.ChatEventMessageDeleted messageDeleted) {
    super(context, msg);
    setTextCreator(() ->
      getText(
        R.string.EventLogDeletedMessages,
        new SenderArgument(sender)
      )
    );
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.ChatEventMessageEdited messageEdited) {
    super(context, msg);
    setTextCreator(() -> {
      if (messageEdited.newMessage.content.getConstructor() == TdApi.MessageText.CONSTRUCTOR ||
          messageEdited.newMessage.content.getConstructor() == TdApi.MessageAnimatedEmoji.CONSTRUCTOR) {
        return getText(R.string.EventLogEditedMessages, new SenderArgument(sender));
      } else if (Td.isEmpty(Td.textOrCaption(messageEdited.newMessage.content))) {
        return getText(R.string.EventLogRemovedCaption, new SenderArgument(sender));
      } else {
        return getText(R.string.EventLogEditedCaption, new SenderArgument(sender));
      }
    });
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.ChatEventPollStopped pollStopped) {
    super(context, msg);
    setTextCreator(() -> {
      final boolean isQuiz =
        pollStopped.message.content.getConstructor() == TdApi.MessagePoll.CONSTRUCTOR &&
        ((TdApi.MessagePoll) pollStopped.message.content).poll.type.getConstructor() == TdApi.PollTypeQuiz.CONSTRUCTOR;
      return getText(
        isQuiz ?
          R.string.EventLogQuizStopped :
          R.string.EventLogPollStopped,
        new SenderArgument(sender)
      );
    });
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.ChatEventMessageUnpinned messageUnpinned) {
    super(context, msg);
    setTextCreator(() ->
      getText(
        R.string.EventLogUnpinnedMessages,
        new SenderArgument(sender)
      )
    );
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.ChatEventInvitesToggled invitesToggled) {
    super(context, msg);
    setTextCreator(() ->
      getText(
        invitesToggled.canInviteUsers ?
          R.string.EventLogToggledInvitesOn :
          R.string.EventLogToggledInvitesOff,
        new SenderArgument(sender)
      )
    );
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.ChatEventSignMessagesToggled signMessagesToggled) {
    super(context, msg);
    setTextCreator(() ->
      getText(
        signMessagesToggled.signMessages ?
          R.string.EventLogToggledSignaturesOn :
          R.string.EventLogToggledSignaturesOff,
        new SenderArgument(sender)
      )
    );
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.ChatEventHasProtectedContentToggled protectedContentToggled) {
    super(context, msg);
    setTextCreator(() ->
      getText(
        protectedContentToggled.hasProtectedContent ?
          R.string.EventLogToggledProtectionOn :
          R.string.EventLogToggledProtectionOff,
        new SenderArgument(sender)
      )
    );
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.ChatEventIsAllHistoryAvailableToggled isAllHistoryAvailableToggled) {
    super(context, msg);
    setTextCreator(() ->
      getText(
        isAllHistoryAvailableToggled.isAllHistoryAvailable ?
          R.string.XMadeGroupHistoryVisible :
          R.string.XMadeGroupHistoryHidden,
        new SenderArgument(sender)
      )
    );
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.ChatEventVideoChatMuteNewParticipantsToggled newParticipantsToggled) {
    super(context, msg);
    setTextCreator(() -> {
      if (msg.isChannelPost) {
        if (msg.isOutgoing) {
          return getText(
            newParticipantsToggled.muteNewParticipants ?
              R.string.EventLogChannelMutedNewParticipantsYou :
              R.string.EventLogChannelUnmutedNewParticipantsYou
          );
        } else {
          return getText(
            newParticipantsToggled.muteNewParticipants ?
              R.string.EventLogChannelMutedNewParticipants :
              R.string.EventLogChannelUnmutedNewParticipants,
            new SenderArgument(sender)
          );
        }
      } else {
        if (msg.isOutgoing) {
          return getText(
            newParticipantsToggled.muteNewParticipants ?
              R.string.EventLogMutedNewParticipantsYou :
              R.string.EventLogUnmutedNewParticipantsYou
          );
        } else {
          return getText(
            newParticipantsToggled.muteNewParticipants ?
              R.string.EventLogMutedNewParticipants :
              R.string.EventLogUnmutedNewParticipants,
            new SenderArgument(sender)
          );
        }
      }
    });
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.ChatEventVideoChatParticipantIsMutedToggled videoChatParticipantIsMutedToggled) {
    super(context, msg);
    TdlibSender targetSender = new TdlibSender(tdlib, msg.chatId, videoChatParticipantIsMutedToggled.participantId);
    setTextCreator(() -> {
      if (msg.isChannelPost) {
        if (msg.isOutgoing) {
          return getText(
            videoChatParticipantIsMutedToggled.isMuted ?
              R.string.EventLogChannelMutedParticipantYou :
              R.string.EventLogChannelUnmutedParticipantYou,
            new SenderArgument(targetSender)
          );
        } else {
          return getText(
            videoChatParticipantIsMutedToggled.isMuted ?
              R.string.EventLogChannelMutedParticipant :
              R.string.EventLogChannelUnmutedParticipant,
            new SenderArgument(sender),
            new SenderArgument(targetSender)
          );
        }
      } else {
        if (msg.isOutgoing) {
          return getText(
            videoChatParticipantIsMutedToggled.isMuted ?
              R.string.EventLogMutedParticipantYou :
              R.string.EventLogUnmutedParticipantYou,
            new SenderArgument(targetSender)
          );
        } else {
          return getText(
            videoChatParticipantIsMutedToggled.isMuted ?
              R.string.EventLogMutedParticipant :
              R.string.EventLogUnmutedParticipant,
            new SenderArgument(sender),
            new SenderArgument(targetSender)
          );
        }
      }
    });
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.ChatEventVideoChatParticipantVolumeLevelChanged videoChatParticipantVolumeLevelChanged) {
    super(context, msg);
    TdlibSender targetSender = new TdlibSender(tdlib, msg.chatId, videoChatParticipantVolumeLevelChanged.participantId);
    setTextCreator(() -> {
      final Argument volume = new BoldArgument((videoChatParticipantVolumeLevelChanged.volumeLevel / 100) + "%");
      if (msg.isOutgoing) {
        return getText(
          R.string.EventLogChangedVolumeYou,
          new SenderArgument(targetSender),
          volume
        );
      } else if (targetSender.isSelf()) {
        return getText(
          R.string.EventLogChangedYourVolume,
          new SenderArgument(sender),
          volume
        );
      } else {
        return getText(
          R.string.EventLogChangedVolume,
          new SenderArgument(sender),
          new SenderArgument(targetSender),
          volume
        );
      }
    });
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.ChatEventUsernameChanged usernameChanged) {
    super(context, msg);
    setTextCreator(() -> {
      boolean hasUsername = !StringUtils.isEmpty(usernameChanged.newUsername);
      if (msg.isChannelPost) {
        return getText(
          hasUsername ?
            R.string.EventLogChangedChannelLink :
            R.string.EventLogRemovedChannelLink,
          new SenderArgument(sender)
        );
      } else {
        return getText(
          hasUsername ?
            R.string.EventLogChangedGroupLink :
            R.string.EventLogRemovedGroupLink,
          new SenderArgument(sender)
        );
      }
    });
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.ChatEventDescriptionChanged descriptionChanged) {
    super(context, msg);
    setTextCreator(() -> {
      boolean hasDescription = !StringUtils.isEmpty(descriptionChanged.newDescription);
      if (msg.isChannelPost) {
        return getText(
          hasDescription ?
            R.string.EventLogEditedChannelDescription :
            R.string.EventLogRemovedChannelDescription,
          new SenderArgument(sender)
        );
      } else {
        return getText(
          hasDescription ?
            R.string.EventLogEditedGroupDescription :
            R.string.EventLogRemovedGroupDescription,
          new SenderArgument(sender)
        );
      }
    });
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.ChatEventStickerSetChanged stickerSetChanged) {
    super(context, msg);
    setTextCreator(() ->
      getText(
        stickerSetChanged.newStickerSetId != 0 ?
          R.string.XChangedGroupStickerSet :
          R.string.XRemovedGroupStickerSet,
        new SenderArgument(sender)
      )
    );
    long stickerSetId = stickerSetChanged.newStickerSetId != 0 ?
      stickerSetChanged.newStickerSetId :
      stickerSetChanged.oldStickerSetId;
    if (stickerSetId != 0) {
      setOnClickListener(() ->
        tdlib.ui().showStickerSet(controller(), stickerSetId, openParameters())
      );
    }
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.ChatEventLinkedChatChanged linkedChatChanged) {
    super(context, msg);
    TdlibSender linkedChat = new TdlibSender(tdlib, msg.chatId, new TdApi.MessageSenderChat(
      linkedChatChanged.newLinkedChatId != 0 ?
        linkedChatChanged.newLinkedChatId :
        linkedChatChanged.oldLinkedChatId
    ));
    setTextCreator(() -> {
      boolean hasLinkedChat = linkedChatChanged.newLinkedChatId != 0;
      if (msg.isChannelPost) {
        return getText(
          hasLinkedChat ?
            R.string.EventLogLinkedGroupChanged :
            R.string.EventLogLinkedGroupRemoved,
          new SenderArgument(sender),
          new SenderArgument(linkedChat)
        );
      } else if (sender.isServiceChannelBot() || Td.getSenderId(msg) == msg.chatId) {
        return getText(
          hasLinkedChat ?
            R.string.EventLogLinkedChannelChangedUnknown :
            R.string.EventLogLinkedChannelRemovedUnknown,
          new SenderArgument(linkedChat)
        );
      } else {
        return getText(
          hasLinkedChat ?
            R.string.EventLogLinkedChannelChanged :
            R.string.EventLogLinkedChannelRemoved,
          new SenderArgument(sender),
          new SenderArgument(linkedChat)
        );
      }
    });
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.ChatEventLocationChanged locationChanged) {
    super(context, msg);
    setTextCreator(() -> {
      if (locationChanged.newLocation != null) {
        return getText(
          locationChanged.oldLocation != null ?
            R.string.EventLogLocationChanged :
            R.string.EventLogLocationSet,
          new SenderArgument(sender),
          new BoldArgument(locationChanged.newLocation.address)
        );
      } else {
        return getText( // TODO: display locationChanged.oldLocation.address
          R.string.EventLogLocationRemoved,
          new SenderArgument(sender)
        );
      }
    });
    TdApi.ChatLocation chatLocation = locationChanged.newLocation != null ?
      locationChanged.newLocation :
      locationChanged.oldLocation;
    if (chatLocation != null) {
      setOnClickListener(() ->
        tdlib.ui().openMap(this, new MapController.Args(
            chatLocation.location.latitude,
            chatLocation.location.longitude
          ).setChatId(msg.chatId, messagesController().getMessageThreadId())
            .setLocationOwnerChatId(msg.chatId)
            .setIsFaded(locationChanged.newLocation == null)
        )
      );
    }
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.ChatEventPhotoChanged photoChanged) {
    super(context, msg);
    setTextCreator(() -> {
      if (photoChanged.newPhoto != null) {
        if (msg.isChannelPost) {
          return getText(R.string.ActionChannelChangedPhoto);
        } else if (msg.isOutgoing) {
          return getText(R.string.group_photo_changed_you);
        } else {
          return getText(R.string.group_photo_changed, new SenderArgument(sender));
        }
      } else {
        if (msg.isChannelPost) {
          return getText(R.string.ActionChannelRemovedPhoto);
        } else if (msg.isOutgoing) {
          return getText(R.string.group_photo_deleted_you);
        } else {
          return getText(R.string.group_photo_deleted, new SenderArgument(sender));
        }
      }
    });
    TdApi.ChatPhoto chatPhoto = photoChanged.newPhoto != null ?
      photoChanged.newPhoto :
      photoChanged.oldPhoto;
    if (chatPhoto != null) {
      setDisplayChatPhoto(chatPhoto);
    }
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.ChatEventSlowModeDelayChanged slowModeDelayChanged) {
    super(context, msg);
    setTextCreator(() -> {
      if (slowModeDelayChanged.newSlowModeDelay != 0) {
        String duration = Lang.getDuration(slowModeDelayChanged.newSlowModeDelay);
        if (msg.isOutgoing) {
          return getText(
            R.string.EventLogSlowModeChangedYou,
            new BoldArgument(duration)
          );
        } else {
          return getText(
            R.string.EventLogSlowModeChanged,
            new SenderArgument(sender),
            new BoldArgument(duration)
          );
        }
      } else {
        return getText(
          R.string.EventLogSlowModeDisabled,
          new SenderArgument(sender)
        );
      }
    });
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.ChatEventMessagePinned messagePinned) {
    super(context, msg);
    setTextCreator(() ->
      getText(R.string.EventLogPinnedMessages, new SenderArgument(sender))
    );
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.ChatEventMemberJoinedByInviteLink joinedByInviteLink) {
    super(context, msg); // TODO: link
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.ChatEventMemberJoinedByRequest joinedByRequest) {
    super(context, msg); // TODO: link
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.ChatEventInviteLinkRevoked inviteLinkRevoked) {
    super(context, msg); // TODO: link
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.ChatEventInviteLinkDeleted inviteLinkDeleted) {
    super(context, msg); // TODO: link
  }

  // Pre-impl: utilities used by constructors

  protected interface Argument {
    FormattedText buildArgument ();
  }

  protected abstract class FormattedTextArgument implements Argument {
    protected abstract TdApi.FormattedText getFormattedText ();

    @Override
    public final FormattedText buildArgument () {
      TdApi.FormattedText formattedText = getFormattedText();
      return FormattedText.valueOf(controller(), formattedText, openParameters());
    }
  }

  protected abstract class TextEntityArgument extends FormattedTextArgument {
    protected abstract String getText ();
    protected abstract TdApi.TextEntityType getEntityType ();

    @Override
    protected final TdApi.FormattedText getFormattedText () {
      final String text = getText();
      if (text.length() > 0) {
        return new TdApi.FormattedText(text, new TdApi.TextEntity[] {
          new TdApi.TextEntity(0, text.length(), getEntityType())
        });
      } else {
        return new TdApi.FormattedText("", new TdApi.TextEntity[0]);
      }
    }
  }

  protected final class SenderArgument implements Argument {
    private final TdlibSender sender;
    private final boolean onlyFirstName;

    public SenderArgument (TdlibSender sender, boolean onlyFirstName) {
      this.sender = sender;
      this.onlyFirstName = onlyFirstName;
    }

    public SenderArgument (TdlibSender sender) {
      this(sender, false);
    }

    @Override
    public FormattedText buildArgument () {
      if (sender.isUser()) {
        // Easy path: treat like mentions
        return new UserArgument(sender.getUserId(), onlyFirstName).buildArgument();
      }
      // Harder path: open chat profile on click
      final String text = onlyFirstName ? sender.getNameShort() : sender.getName();
      if (text.isEmpty()) {
        return new FormattedText(text);
      }
      TextEntityCustom custom = new TextEntityCustom(controller(), tdlib, text, 0, text.length(), 0, openParameters());
      custom.setOnClickListener(new ClickableSpan() {
        @Override
        public void onClick (@NonNull View widget) {
          if (sender.isUser()) {
            tdlib.ui().openPrivateProfile(controller(), sender.getUserId(), openParameters());
          } else if (sender.isChat()) {
            tdlib.ui().openChatProfile(controller(), sender.getChatId(), null, openParameters());
          }
        }
      });
      // TODO: color
      return new FormattedText(text, new TextEntity[] {custom});
    }
  }

  protected final class UserArgument extends TextEntityArgument {
    private final long userId;
    private final boolean onlyFirstName;

    public UserArgument (long userId, boolean onlyFirstName) {
      this.userId = userId;
      this.onlyFirstName = onlyFirstName;
    }

    @Override
    protected String getText () {
      if (onlyFirstName) {
        return tdlib.cache().userFirstName(userId);
      } else {
        return tdlib.cache().userName(userId);
      }
    }

    @Override
    protected TdApi.TextEntityType getEntityType () {
      return new TdApi.TextEntityTypeMentionName(userId);
    }
  }

  protected class StyleArgument extends TextEntityArgument {
    private final String text;
    private final TdApi.TextEntityType entityType;

    public StyleArgument (String text, TdApi.TextEntityType entityType) {
      this.text = text;
      this.entityType = entityType;
    }

    @Override
    protected String getText () {
      return text;
    }

    @Override
    protected TdApi.TextEntityType getEntityType () {
      return entityType;
    }
  }

  protected final class BoldArgument extends StyleArgument {
    public BoldArgument (String text) {
      super(text, new TdApi.TextEntityTypeBold());
    }
  }

  protected class MessageArgument implements Argument { // TODO: message
    private final TdApi.Message message;

    public MessageArgument (TdApi.Message message) {
      this.message = message;
    }

    protected TdApi.FormattedText getPreview () {
      throw new UnsupportedOperationException();
    }

    @Override
    public final FormattedText buildArgument () {
      // TODO + onclick + update if album fetched
      throw new UnsupportedOperationException();
    }
  }

  protected final class InvoiceArgument extends MessageArgument { // TODO: invoice
    private final TdApi.MessageInvoice invoice;

    public InvoiceArgument (TdApi.Message message) {
      super(message);
      this.invoice = (TdApi.MessageInvoice) message.content;
    }

    @Override
    protected TdApi.FormattedText getPreview () {
      return new TdApi.FormattedText(
        invoice.title,
        null
      );
    }
  }

  protected final class GameArgument extends MessageArgument { // TODO: game
    private final TdApi.Game game;

    public GameArgument (TdApi.Message message) {
      super(message);
      this.game = ((TdApi.MessageGame) message.content).game;
    }

    @Override
    protected TdApi.FormattedText getPreview () {
      return new TdApi.FormattedText(
        TD.getGameName(game, false),
        null
      );
    }
  }

  private static FormattedText[] parseFormatArgs (Argument... args) {
    FormattedText[] formatArgs = new FormattedText[args.length];
    for (int i = 0; i < args.length; i++) {
      formatArgs[i] = args[i].buildArgument();
    }
    return formatArgs;
  }

  private FormattedText getText (@StringRes int resId, Argument... args) {
    if (args == null || args.length == 0) {
      return new FormattedText(Lang.getString(resId));
    }
    FormattedText[] formatArgs = parseFormatArgs(args);
    CharSequence text = Lang.getString(resId,
      (target, argStart, argEnd, argIndex, needFakeBold) -> args[argIndex],
      (Object[]) formatArgs
    );
    return formatText(text);
  }

  private FormattedText getPlural (@StringRes int resId, long num, Argument... args) {
    FormattedText[] formatArgs = parseFormatArgs(args);
    CharSequence text = Lang.plural(resId, num,
      (target, argStart, argEnd, argIndex, needFakeBold) -> argIndex == 0 ?
        Lang.boldCreator().onCreateSpan(target, argStart, argEnd, argIndex, needFakeBold) :
        args[argIndex - 1],
      (Object[]) formatArgs
    );
    return formatText(text);
  }

  private FormattedText getDuration (
    @StringRes int secondsRes,
    @StringRes int minutesRes,
    @StringRes int hoursRes,
    @StringRes int daysRes,
    @StringRes int weeksRes,
    @StringRes int monthsRes,
    final long duration,
    final TimeUnit durationUnit,
    Argument... args) {
    final long days = durationUnit.toDays(duration);
    final long months = days / 30;
    final long weeks = days / 7;
    if (monthsRes != 0 && months > 0) {
      return getPlural(monthsRes, months, args);
    }
    if (weeksRes != 0 && weeks > 0) {
      return getPlural(weeksRes, weeks, args);
    }
    if (daysRes != 0 && days > 0) {
      return getPlural(daysRes, days, args);
    }
    final long hours = durationUnit.toHours(duration);
    if (hoursRes != 0 && hours > 0) {
      return getPlural(hoursRes, hours, args);
    }
    final long minutes = durationUnit.toMinutes(duration);
    if (minutesRes != 0 && minutes > 0) {
      return getPlural(minutesRes, minutes, args);
    }
    final long seconds = durationUnit.toSeconds(duration);
    if (secondsRes != 0) {
      return getPlural(secondsRes, seconds, args);
    }
    throw new IllegalArgumentException("duration == " + durationUnit.toMillis(duration));
  }

  private FormattedText formatText (CharSequence text) {
    final String string = text.toString();
    if (!(text instanceof Spanned)) {
      return new FormattedText(string);
    }
    List<TextEntity> mixedEntities = null;
    Spanned spanned = (Spanned) text;
    Object[] spans = spanned.getSpans(
      0,
      spanned.length(),
      Object.class
    );
    for (Object span : spans) {
      final int spanStart = spanned.getSpanStart(span);
      final int spanEnd = spanned.getSpanEnd(span);
      if (spanStart == -1 || spanEnd == -1) {
        continue;
      }
      if (span instanceof FormattedText) {
        FormattedText formattedText = (FormattedText) span;
        if (formattedText.entities != null) {
          for (TextEntity entity : formattedText.entities) {
            entity.offset(spanStart);
            if (mixedEntities == null) {
              mixedEntities = new ArrayList<>();
            }
            mixedEntities.add(entity);
          }
        }
      } else if (span instanceof CharacterStyle) {
        TdApi.TextEntityType[] entityType = TD.toEntityType((CharacterStyle) span);
        if (entityType != null && entityType.length > 0) {
          TdApi.TextEntity[] telegramEntities = new TdApi.TextEntity[entityType.length];
          for (int i = 0; i < entityType.length; i++) {
            telegramEntities[i] = new TdApi.TextEntity(
              spanStart,
              spanEnd,
              entityType[i]
            );
          }
          TextEntity[] entities = TextEntity.valueOf(tdlib, string, telegramEntities, openParameters());
          if (entities != null && entities.length > 0) {
            if (mixedEntities == null) {
              mixedEntities = new ArrayList<>();
            }
            Collections.addAll(mixedEntities, entities);
          }
        }
      }
    }
    return new FormattedText(
      string,
      mixedEntities != null && !mixedEntities.isEmpty() ? mixedEntities.toArray(new TextEntity[0]) : null
    );
  }

  // Impl: code below doesn't know anything about the code above

  private interface ServiceMessageCreator {
    FormattedText createText ();
  }

  private ServiceMessageCreator textCreator;

  private void setTextCreator (ServiceMessageCreator textCreator) {
    this.textCreator = textCreator;
  }

  private interface OnClickListener {
    void onClick ();
  }

  private OnClickListener onClickListener;

  public void setOnClickListener (OnClickListener onClickListener) {
    this.onClickListener = onClickListener;
  }

  private TdApi.ChatPhoto chatPhoto;

  public void setDisplayChatPhoto (TdApi.ChatPhoto chatPhoto) {
    this.chatPhoto = chatPhoto;
  }

  private TdApi.Message otherMessage;

  public void setDisplayMessage (long chatId, long messageId, Filter<TdApi.Message> callback) {
    tdlib.client().send(new TdApi.GetMessage(chatId, messageId), result -> {
      if (result.getConstructor() == TdApi.Message.CONSTRUCTOR) {
        TdApi.Message message = (TdApi.Message) result;
        runOnUiThreadOptional(() -> {
          otherMessage = message;
          if (callback.accept(message)) {
            rebuildAndUpdateContent();
          }
        });
      }
    });
  }
}
