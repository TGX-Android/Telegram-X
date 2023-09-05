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
 * File created on 02/09/2022, 22:32.
 */

package org.thunderdog.challegram.data;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.chat.MessagesManager;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.util.text.Text;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import me.vkryl.core.DateUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.core.lambda.RunnableData;
import me.vkryl.td.Td;

/**
 * Steps to support any new ChatEventAction constructor:
 *
 * 1. Decide how it should look
 * 2. Add corresponding return value to the {@link #getActionMessageMode}
 * 3. Add construction to the {@link #serviceMessage}, {@link #fullMessage}, or both, depending on the value used in the previous step
 * 4. If action can be represented as fake {@link TdApi.MessageContent}, then refer to {@link #convertToNativeMessageContent}
 */
public class ChatEventUtil {
  @NonNull
  public static TGMessage newMessage (MessagesManager context, TdApi.Message message, TdApiExt.MessageChatEvent event) {
    TGMessage actionMessage;
    if (event.isFull) {
      actionMessage = fullMessage(context, message, event.event);
    } else {
      actionMessage = serviceMessage(context, message, event.event.action);
    }
    return actionMessage.setIsEventLog(event, 0);
  }

  @Retention(RetentionPolicy.SOURCE)
  @IntDef({
    ActionMessageMode.ONLY_SERVICE,
    ActionMessageMode.ONLY_FULL,
    ActionMessageMode.SERVICE_AND_FULL
  })
  public @interface ActionMessageMode {
    int ONLY_SERVICE = 1, ONLY_FULL = 2, SERVICE_AND_FULL = 3;
  }

  @ActionMessageMode
  public static int getActionMessageMode (TdApi.ChatEventAction action) {
    switch (action.getConstructor()) {
      // service + full message
      case TdApi.ChatEventDescriptionChanged.CONSTRUCTOR:
      case TdApi.ChatEventMessageDeleted.CONSTRUCTOR:
      case TdApi.ChatEventMessageEdited.CONSTRUCTOR:
      case TdApi.ChatEventMessagePinned.CONSTRUCTOR:
      case TdApi.ChatEventUsernameChanged.CONSTRUCTOR:
      case TdApi.ChatEventPollStopped.CONSTRUCTOR:
        return ActionMessageMode.SERVICE_AND_FULL;
      // only service message
      case TdApi.ChatEventPhotoChanged.CONSTRUCTOR:
      case TdApi.ChatEventMessageUnpinned.CONSTRUCTOR:
      case TdApi.ChatEventInvitesToggled.CONSTRUCTOR:
      case TdApi.ChatEventSignMessagesToggled.CONSTRUCTOR:
      case TdApi.ChatEventHasProtectedContentToggled.CONSTRUCTOR:
      case TdApi.ChatEventIsAllHistoryAvailableToggled.CONSTRUCTOR:
      case TdApi.ChatEventStickerSetChanged.CONSTRUCTOR:
      case TdApi.ChatEventLinkedChatChanged.CONSTRUCTOR:
      case TdApi.ChatEventSlowModeDelayChanged.CONSTRUCTOR:
      case TdApi.ChatEventLocationChanged.CONSTRUCTOR:
      case TdApi.ChatEventVideoChatMuteNewParticipantsToggled.CONSTRUCTOR:
      case TdApi.ChatEventMemberJoinedByInviteLink.CONSTRUCTOR:
      case TdApi.ChatEventMemberJoinedByRequest.CONSTRUCTOR:
      case TdApi.ChatEventInviteLinkRevoked.CONSTRUCTOR:
      case TdApi.ChatEventInviteLinkDeleted.CONSTRUCTOR:
      case TdApi.ChatEventVideoChatParticipantVolumeLevelChanged.CONSTRUCTOR:
      case TdApi.ChatEventVideoChatParticipantIsMutedToggled.CONSTRUCTOR:
      case TdApi.ChatEventHasAggressiveAntiSpamEnabledToggled.CONSTRUCTOR:
      case TdApi.ChatEventIsForumToggled.CONSTRUCTOR:
      case TdApi.ChatEventActiveUsernamesChanged.CONSTRUCTOR:
      case TdApi.ChatEventForumTopicCreated.CONSTRUCTOR:
      case TdApi.ChatEventForumTopicDeleted.CONSTRUCTOR:
      case TdApi.ChatEventForumTopicEdited.CONSTRUCTOR:
      case TdApi.ChatEventForumTopicPinned.CONSTRUCTOR:
      case TdApi.ChatEventForumTopicToggleIsClosed.CONSTRUCTOR:
      case TdApi.ChatEventForumTopicToggleIsHidden.CONSTRUCTOR:
        return ActionMessageMode.ONLY_SERVICE;
      // only full (native)
      case TdApi.ChatEventMessageAutoDeleteTimeChanged.CONSTRUCTOR:
      case TdApi.ChatEventVideoChatCreated.CONSTRUCTOR:
      case TdApi.ChatEventVideoChatEnded.CONSTRUCTOR:
      case TdApi.ChatEventMemberJoined.CONSTRUCTOR:
      case TdApi.ChatEventMemberLeft.CONSTRUCTOR:
      case TdApi.ChatEventTitleChanged.CONSTRUCTOR:
      // only full
      case TdApi.ChatEventMemberPromoted.CONSTRUCTOR:
      case TdApi.ChatEventMemberRestricted.CONSTRUCTOR:
      case TdApi.ChatEventMemberInvited.CONSTRUCTOR:
      case TdApi.ChatEventPermissionsChanged.CONSTRUCTOR:
      case TdApi.ChatEventInviteLinkEdited.CONSTRUCTOR:
      case TdApi.ChatEventAvailableReactionsChanged.CONSTRUCTOR:
        return ActionMessageMode.ONLY_FULL;
    }
    throw new UnsupportedOperationException(action.toString());
  }

  @NonNull
  private static TGMessage serviceMessage (MessagesManager context, TdApi.Message msg, TdApi.ChatEventAction action) {
    switch (action.getConstructor()) {
      // service + full message
      case TdApi.ChatEventDescriptionChanged.CONSTRUCTOR:
        return new TGMessageService(context, msg, (TdApi.ChatEventDescriptionChanged) action);
      case TdApi.ChatEventMessageDeleted.CONSTRUCTOR:
        return new TGMessageService(context, msg, (TdApi.ChatEventMessageDeleted) action);
      case TdApi.ChatEventMessageEdited.CONSTRUCTOR:
        return new TGMessageService(context, msg, (TdApi.ChatEventMessageEdited) action);
      case TdApi.ChatEventMessagePinned.CONSTRUCTOR:
        return new TGMessageService(context, msg, (TdApi.ChatEventMessagePinned) action);
      case TdApi.ChatEventUsernameChanged.CONSTRUCTOR:
        return new TGMessageService(context, msg, (TdApi.ChatEventUsernameChanged) action);
      case TdApi.ChatEventPollStopped.CONSTRUCTOR:
        return new TGMessageService(context, msg, (TdApi.ChatEventPollStopped) action);
      // only service message
      case TdApi.ChatEventPhotoChanged.CONSTRUCTOR:
        return new TGMessageService(context, msg, (TdApi.ChatEventPhotoChanged) action);
      case TdApi.ChatEventMessageUnpinned.CONSTRUCTOR:
        return new TGMessageService(context, msg, (TdApi.ChatEventMessageUnpinned) action);
      case TdApi.ChatEventInvitesToggled.CONSTRUCTOR:
        return new TGMessageService(context, msg, (TdApi.ChatEventInvitesToggled) action);
      case TdApi.ChatEventSignMessagesToggled.CONSTRUCTOR:
        return new TGMessageService(context, msg, (TdApi.ChatEventSignMessagesToggled) action);
      case TdApi.ChatEventHasProtectedContentToggled.CONSTRUCTOR:
        return new TGMessageService(context, msg, (TdApi.ChatEventHasProtectedContentToggled) action);
      case TdApi.ChatEventIsAllHistoryAvailableToggled.CONSTRUCTOR:
        return new TGMessageService(context, msg, (TdApi.ChatEventIsAllHistoryAvailableToggled) action);
      case TdApi.ChatEventStickerSetChanged.CONSTRUCTOR:
        return new TGMessageService(context, msg, (TdApi.ChatEventStickerSetChanged) action);
      case TdApi.ChatEventLinkedChatChanged.CONSTRUCTOR:
        return new TGMessageService(context, msg, (TdApi.ChatEventLinkedChatChanged) action);
      case TdApi.ChatEventSlowModeDelayChanged.CONSTRUCTOR:
        return new TGMessageService(context, msg, (TdApi.ChatEventSlowModeDelayChanged) action);
      case TdApi.ChatEventLocationChanged.CONSTRUCTOR:
        return new TGMessageService(context, msg, (TdApi.ChatEventLocationChanged) action);
      case TdApi.ChatEventVideoChatMuteNewParticipantsToggled.CONSTRUCTOR:
        return new TGMessageService(context, msg, (TdApi.ChatEventVideoChatMuteNewParticipantsToggled) action);
      case TdApi.ChatEventMemberJoinedByInviteLink.CONSTRUCTOR:
        return new TGMessageService(context, msg, (TdApi.ChatEventMemberJoinedByInviteLink) action);
      case TdApi.ChatEventMemberJoinedByRequest.CONSTRUCTOR:
        return new TGMessageService(context, msg, (TdApi.ChatEventMemberJoinedByRequest) action);
      case TdApi.ChatEventInviteLinkRevoked.CONSTRUCTOR:
        return new TGMessageService(context, msg, (TdApi.ChatEventInviteLinkRevoked) action);
      case TdApi.ChatEventInviteLinkDeleted.CONSTRUCTOR:
        return new TGMessageService(context, msg, (TdApi.ChatEventInviteLinkDeleted) action);
      case TdApi.ChatEventVideoChatParticipantVolumeLevelChanged.CONSTRUCTOR:
        return new TGMessageService(context, msg, (TdApi.ChatEventVideoChatParticipantVolumeLevelChanged) action);
      case TdApi.ChatEventVideoChatParticipantIsMutedToggled.CONSTRUCTOR:
        return new TGMessageService(context, msg, (TdApi.ChatEventVideoChatParticipantIsMutedToggled) action);
      case TdApi.ChatEventHasAggressiveAntiSpamEnabledToggled.CONSTRUCTOR:
        return new TGMessageService(context, msg, (TdApi.ChatEventHasAggressiveAntiSpamEnabledToggled) action);
      case TdApi.ChatEventActiveUsernamesChanged.CONSTRUCTOR:
        return new TGMessageService(context, msg, (TdApi.ChatEventActiveUsernamesChanged) action);
      case TdApi.ChatEventIsForumToggled.CONSTRUCTOR:
        return new TGMessageService(context, msg, (TdApi.ChatEventIsForumToggled) action);
      case TdApi.ChatEventForumTopicCreated.CONSTRUCTOR:
        return new TGMessageService(context, msg, (TdApi.ChatEventForumTopicCreated) action);
      case TdApi.ChatEventForumTopicDeleted.CONSTRUCTOR:
        return new TGMessageService(context, msg, (TdApi.ChatEventForumTopicDeleted) action);
      case TdApi.ChatEventForumTopicPinned.CONSTRUCTOR:
        return new TGMessageService(context, msg, (TdApi.ChatEventForumTopicPinned) action);
      case TdApi.ChatEventForumTopicEdited.CONSTRUCTOR:
        return new TGMessageService(context, msg, (TdApi.ChatEventForumTopicEdited) action);
      case TdApi.ChatEventForumTopicToggleIsClosed.CONSTRUCTOR:
        return new TGMessageService(context, msg, (TdApi.ChatEventForumTopicToggleIsClosed) action);
      case TdApi.ChatEventForumTopicToggleIsHidden.CONSTRUCTOR:
        return new TGMessageService(context, msg, (TdApi.ChatEventForumTopicToggleIsHidden) action);
      // only full (native)
      case TdApi.ChatEventMessageAutoDeleteTimeChanged.CONSTRUCTOR:
      case TdApi.ChatEventVideoChatCreated.CONSTRUCTOR:
      case TdApi.ChatEventVideoChatEnded.CONSTRUCTOR:
      case TdApi.ChatEventMemberJoined.CONSTRUCTOR:
      case TdApi.ChatEventMemberLeft.CONSTRUCTOR:
      case TdApi.ChatEventTitleChanged.CONSTRUCTOR:
        // only full
      case TdApi.ChatEventMemberPromoted.CONSTRUCTOR:
      case TdApi.ChatEventMemberRestricted.CONSTRUCTOR:
      case TdApi.ChatEventMemberInvited.CONSTRUCTOR:
      case TdApi.ChatEventPermissionsChanged.CONSTRUCTOR:
      case TdApi.ChatEventInviteLinkEdited.CONSTRUCTOR:
      case TdApi.ChatEventAvailableReactionsChanged.CONSTRUCTOR:
        throw new IllegalArgumentException(action.toString());
    }
    throw new UnsupportedOperationException(action.toString());
  }

  @NonNull
  private static TGMessage fullMessage (MessagesManager context, TdApi.Message msg, TdApi.ChatEvent event) {
    final TdApi.ChatEventAction action = event.action;
    final Tdlib tdlib = context.controller().tdlib();
    final TGMessage fullMessage;
    switch (action.getConstructor()) {
      case TdApi.ChatEventMessageAutoDeleteTimeChanged.CONSTRUCTOR:
      case TdApi.ChatEventVideoChatCreated.CONSTRUCTOR:
      case TdApi.ChatEventVideoChatEnded.CONSTRUCTOR:
      case TdApi.ChatEventMemberJoined.CONSTRUCTOR:
      case TdApi.ChatEventMemberLeft.CONSTRUCTOR:
      case TdApi.ChatEventTitleChanged.CONSTRUCTOR:
      case TdApi.ChatEventPhotoChanged.CONSTRUCTOR:
      case TdApi.ChatEventPermissionsChanged.CONSTRUCTOR:
      case TdApi.ChatEventAvailableReactionsChanged.CONSTRUCTOR:
      case TdApi.ChatEventInviteLinkEdited.CONSTRUCTOR: {
        return TGMessage.valueOf(context, msg, convertToNativeMessageContent(event));
      }
      case TdApi.ChatEventMessagePinned.CONSTRUCTOR: {
        TdApi.ChatEventMessagePinned e = (TdApi.ChatEventMessagePinned) action;
        return TGMessage.valueOf(context, e.message);
      }
      case TdApi.ChatEventPollStopped.CONSTRUCTOR: {
        TdApi.ChatEventPollStopped e = (TdApi.ChatEventPollStopped) action;
        return TGMessage.valueOf(context, e.message);
      }
      case TdApi.ChatEventMessageDeleted.CONSTRUCTOR: {
        TdApi.ChatEventMessageDeleted e = (TdApi.ChatEventMessageDeleted) action;
        return TGMessage.valueOf(context, e.message);
      }

      case TdApi.ChatEventUsernameChanged.CONSTRUCTOR: {
        TdApi.ChatEventUsernameChanged changed = (TdApi.ChatEventUsernameChanged) action;
        TdApi.FormattedText text;
        if (StringUtils.isEmpty(changed.newUsername)) {
          text = new TdApi.FormattedText("", null);
        } else {
          String link = tdlib.tMeUrl(changed.newUsername);
          text = new TdApi.FormattedText(link, new TdApi.TextEntity[] {new TdApi.TextEntity(0, link.length(), new TdApi.TextEntityTypeUrl())});
        }
        fullMessage = new TGMessageText(context, msg, text);
        if (!StringUtils.isEmpty(changed.oldUsername)) {
          String link = tdlib.tMeUrl(changed.oldUsername);
          fullMessage.setFooter(Lang.getString(R.string.EventLogPreviousLink), link, new TdApi.TextEntity[] {new TdApi.TextEntity(0, link.length(), new TdApi.TextEntityTypeUrl())});
        }
        break;
      }
      case TdApi.ChatEventDescriptionChanged.CONSTRUCTOR: {
        TdApi.ChatEventDescriptionChanged e = (TdApi.ChatEventDescriptionChanged) action;

        TdApi.FormattedText text;
        if (StringUtils.isEmpty(e.newDescription)) {
          text = new TdApi.FormattedText("", null);
        } else {
          text = new TdApi.FormattedText(e.newDescription, Text.findEntities(e.newDescription, Text.ENTITY_FLAGS_ALL_NO_COMMANDS));
        }

        fullMessage = new TGMessageText(context, msg, text);
        if (!StringUtils.isEmpty(e.oldDescription)) {
          fullMessage.setFooter(Lang.getString(R.string.EventLogPreviousGroupDescription), e.oldDescription, null);
        }
        break;
      }

      case TdApi.ChatEventMemberInvited.CONSTRUCTOR: {
        TdApi.ChatEventMemberInvited e = (TdApi.ChatEventMemberInvited) action;
        TdApi.User user = tdlib.cache().user(e.userId);
        String userName = TD.getUserName(user);
        StringBuilder b = new StringBuilder(Lang.getString(R.string.group_user_added, "", userName).trim());
        int start = b.lastIndexOf(userName);
        ArrayList<TdApi.TextEntity> entities = new ArrayList<>(3);
        entities.add(new TdApi.TextEntity(0, start - 1, new TdApi.TextEntityTypeItalic()));
        entities.add(new TdApi.TextEntity(start, userName.length(), new TdApi.TextEntityTypeMentionName(e.userId)));
        if (user != null && Td.hasUsername(user)) {
          String username = Td.primaryUsername(user);
          b.append(" / ");
          entities.add(new TdApi.TextEntity(b.length(), username.length() + 1, new TdApi.TextEntityTypeMention()));
          b.append('@');
          b.append(username);
        }
        TdApi.TextEntity[] array = new TdApi.TextEntity[entities.size()];
        entities.toArray(array);
        TdApi.FormattedText text = new TdApi.FormattedText(b.toString(), array);
        fullMessage = new TGMessageText(context, msg, text);
        break;
      }

      case TdApi.ChatEventMessageEdited.CONSTRUCTOR: {
        TdApi.ChatEventMessageEdited e = (TdApi.ChatEventMessageEdited) action;
        fullMessage = TGMessage.valueOf(context, TD.removeWebPage(e.newMessage));
        int footerRes;
        TdApi.Message oldMessage = TD.removeWebPage(e.oldMessage);
        TdApi.FormattedText originalText = Td.textOrCaption(oldMessage.content);
        switch (oldMessage.content.getConstructor()) {
          case TdApi.MessageText.CONSTRUCTOR:
            footerRes = R.string.EventLogOriginalMessages;
            break;
          default:
            footerRes = R.string.EventLogOriginalCaption;
            break;
        }
        //noinspection UnsafeOptInUsageError
        String text = Td.isEmpty(originalText) ? Lang.getString(R.string.EventLogOriginalCaptionEmpty) : originalText.text;
        fullMessage.setFooter(Lang.getString(footerRes), text, originalText != null ? originalText.entities : null);
        break;
      }
      case TdApi.ChatEventMemberPromoted.CONSTRUCTOR:
      case TdApi.ChatEventMemberRestricted.CONSTRUCTOR: {
        final TdApi.MessageSender memberId;

        StringBuilder b = new StringBuilder();
        ArrayList<TdApi.TextEntity> entities = new ArrayList<>();
        final TdApi.ChatMemberStatus oldStatus, newStatus;
        final boolean isPromote, isTransferOwnership;
        boolean isAnonymous = false;

        final int stringRes;
        int restrictedUntil = 0;

        if (action.getConstructor() == TdApi.ChatEventMemberPromoted.CONSTRUCTOR) {
          TdApi.ChatEventMemberPromoted e = (TdApi.ChatEventMemberPromoted) action;
          memberId = new TdApi.MessageSenderUser(e.userId);

          isPromote = true;

          // TYPE_EDIT = 0, TYPE_ASSIGN = 1, TYPE_REMOVE = 2, TYPE_TRANSFER = 3
          int type = 0;

          if (e.oldStatus.getConstructor() != TdApi.ChatMemberStatusCreator.CONSTRUCTOR && e.newStatus.getConstructor() == TdApi.ChatMemberStatusCreator.CONSTRUCTOR) {
            isTransferOwnership = true;
            oldStatus = e.oldStatus;
            newStatus = new TdApi.ChatMemberStatusCreator();
            type = 3;
          } else if (e.oldStatus.getConstructor() == TdApi.ChatMemberStatusCreator.CONSTRUCTOR && e.newStatus.getConstructor() != TdApi.ChatMemberStatusCreator.CONSTRUCTOR) {
            isTransferOwnership = true;
            oldStatus = e.oldStatus;
            newStatus = new TdApi.ChatMemberStatusCreator();
            type = 4;
          } else {
            isTransferOwnership = false;

            if (e.oldStatus.getConstructor() == TdApi.ChatMemberStatusCreator.CONSTRUCTOR && e.newStatus.getConstructor() == TdApi.ChatMemberStatusCreator.CONSTRUCTOR) {
              type = 5;
              isAnonymous = ((TdApi.ChatMemberStatusCreator) e.oldStatus).isAnonymous != ((TdApi.ChatMemberStatusCreator) e.newStatus).isAnonymous;
            }

            switch (e.oldStatus.getConstructor()) {
              case TdApi.ChatMemberStatusAdministrator.CONSTRUCTOR:
                oldStatus = e.oldStatus;
                break;
              default:
                if (!isAnonymous) {
                  type = 1;
                  oldStatus = new TdApi.ChatMemberStatusAdministrator(null, false, new TdApi.ChatAdministratorRights());
                } else {
                  oldStatus = e.oldStatus;
                }
                break;
            }

            switch (e.newStatus.getConstructor()) {
              case TdApi.ChatMemberStatusAdministrator.CONSTRUCTOR:
                newStatus = e.newStatus;
                break;
              default:
                if (!isAnonymous) {
                  type = 2;
                  newStatus = new TdApi.ChatMemberStatusAdministrator(null, false, new TdApi.ChatAdministratorRights());
                } else {
                  newStatus = e.newStatus;
                }
                break;
            }
          }

          switch (type) {
            case 1:
              stringRes = R.string.EventLogPromotedNew;
              break;
            case 2:
              stringRes = R.string.EventLogUnpromoted;
              break;
            case 3:
              stringRes = R.string.EventLogTransferredOwnership;
              break;
            case 4:
              stringRes = R.string.EventLogNoLongerCreator;
              break;
            default:
              stringRes = R.string.EventLogPromoted;
              break;
          }

        } else {
          TdApi.ChatEventMemberRestricted e = (TdApi.ChatEventMemberRestricted) action;

          memberId = e.memberId;
          isPromote = false;
          isTransferOwnership = false;

          if (msg.isChannelPost) {
            oldStatus = null;
            newStatus = null;
            if (e.newStatus.getConstructor() == TdApi.ChatMemberStatusBanned.CONSTRUCTOR) {
              stringRes = R.string.EventLogChannelRestricted;
            } else {
              stringRes = R.string.EventLogChannelUnrestricted;
            }
          } else {
            oldStatus = e.oldStatus;
            newStatus = e.newStatus;
            // STATUS_NORMAL = 0, STATUS_BANNED = 1, STATUS_RESTRICTED = 2
            int prevState = 0, newState = 0;

            switch (e.oldStatus.getConstructor()) {
              case TdApi.ChatMemberStatusBanned.CONSTRUCTOR:
                prevState = 1;
                break;
              case TdApi.ChatMemberStatusRestricted.CONSTRUCTOR:
                prevState = 2;
                break;
            }

            switch (e.newStatus.getConstructor()) {
              case TdApi.ChatMemberStatusBanned.CONSTRUCTOR:
                newState = 1;
                break;
              case TdApi.ChatMemberStatusRestricted.CONSTRUCTOR:
                newState = 2;
                break;
            }

            if (e.oldStatus.getConstructor() == TdApi.ChatMemberStatusCreator.CONSTRUCTOR && e.newStatus.getConstructor() == TdApi.ChatMemberStatusCreator.CONSTRUCTOR) {
              isAnonymous = ((TdApi.ChatMemberStatusCreator) e.oldStatus).isAnonymous != ((TdApi.ChatMemberStatusCreator) e.newStatus).isAnonymous;
              stringRes = R.string.EventLogPromoted;
            } else if (newState == 1 && prevState == 0) {
              stringRes = R.string.EventLogGroupBanned;
              restrictedUntil = ((TdApi.ChatMemberStatusBanned) newStatus).bannedUntilDate;
            } else if (newState != 0) {
              stringRes = prevState != 0 ? R.string.EventLogRestrictedUntil : R.string.EventLogRestrictedNew;
              restrictedUntil = newStatus.getConstructor() == TdApi.ChatMemberStatusBanned.CONSTRUCTOR ? ((TdApi.ChatMemberStatusBanned) newStatus).bannedUntilDate : ((TdApi.ChatMemberStatusRestricted) newStatus).restrictedUntilDate;
            } else {
              stringRes = R.string.EventLogRestrictedDeleted;
            }
            /* else {
              throw new IllegalArgumentException("server bug: " + action);
            }*/
          }
        }

        String userName = tdlib.senderName(memberId);
        String username = tdlib.senderUsername(memberId);

        b.append(Lang.getString(stringRes));

        int start = b.indexOf("%1$s");
        if (start != -1) {
          entities.add(new TdApi.TextEntity(0, start - 1, new TdApi.TextEntityTypeItalic()));

          b.replace(start, start + 4, "");
          b.insert(start, userName);
          if (memberId.getConstructor() == TdApi.MessageSenderUser.CONSTRUCTOR) {
            // TODO support for MessageSenderChat
            entities.add(new TdApi.TextEntity(start, userName.length(), new TdApi.TextEntityTypeMentionName(((TdApi.MessageSenderUser) memberId).userId)));
          }
          start += userName.length();
          if (!StringUtils.isEmpty(username)) {
            b.insert(start, " / @");
            start += 3;
            b.insert(start + 1, username);
            entities.add(new TdApi.TextEntity(start, username.length() + 1, new TdApi.TextEntityTypeMention()));
            start += username.length() + 1;
          }
        }

        int i = b.indexOf("%2$s");
        if (i != -1) {
          int duration = restrictedUntil - event.date;
          String str;

          if (restrictedUntil == 0) {
            str = Lang.getString(R.string.UserRestrictionsUntilForever);
          } else if (Lang.preferTimeForDuration(duration)) {
            str = Lang.getString(R.string.UntilX, Lang.getDate(restrictedUntil, TimeUnit.SECONDS));
          } else {
            str = Lang.getDuration(duration, 0, 0, false);
          }

          b.replace(i, i + 4, str);
        }

        if (start < b.length() - 1) {
          entities.add(new TdApi.TextEntity(start, b.length() - start, new TdApi.TextEntityTypeItalic()));
        }

        b.append('\n');

        if (isTransferOwnership) {
          // no need to show anything
        } else if (isAnonymous) {
          appendRight(b, R.string.EventLogPromotedRemainAnonymous, ((TdApi.ChatMemberStatusCreator) oldStatus).isAnonymous, ((TdApi.ChatMemberStatusCreator) newStatus).isAnonymous, false);
        } else if (isPromote) {
          final TdApi.ChatMemberStatusAdministrator oldAdmin = (TdApi.ChatMemberStatusAdministrator) oldStatus;
          final TdApi.ChatMemberStatusAdministrator newAdmin = (TdApi.ChatMemberStatusAdministrator) newStatus;
          appendRight(b, msg.isChannelPost ? R.string.EventLogPromotedManageChannel : R.string.EventLogPromotedManageGroup, oldAdmin.rights.canManageChat, newAdmin.rights.canManageChat, false);
          appendRight(b, msg.isChannelPost ? R.string.EventLogPromotedChangeChannelInfo : R.string.EventLogPromotedChangeGroupInfo, oldAdmin.rights.canChangeInfo, newAdmin.rights.canChangeInfo, false);
          if (msg.isChannelPost) {
            appendRight(b, R.string.EventLogPromotedPostMessages, oldAdmin.rights.canChangeInfo, newAdmin.rights.canChangeInfo, false);
            appendRight(b, R.string.EventLogPromotedEditMessages, oldAdmin.rights.canEditMessages, newAdmin.rights.canEditMessages, false);
          }
          appendRight(b, R.string.EventLogPromotedDeleteMessages, oldAdmin.rights.canDeleteMessages, newAdmin.rights.canDeleteMessages, false);
          appendRight(b, R.string.EventLogPromotedBanUsers, oldAdmin.rights.canRestrictMembers, newAdmin.rights.canRestrictMembers, false);
          appendRight(b, R.string.EventLogPromotedAddUsers, oldAdmin.rights.canInviteUsers, newAdmin.rights.canInviteUsers, false);
          if (!msg.isChannelPost) {
            appendRight(b, R.string.EventLogPromotedPinMessages, oldAdmin.rights.canPinMessages, newAdmin.rights.canPinMessages, false);
          }
          appendRight(b, msg.isChannelPost ? R.string.EventLogPromotedManageLiveStreams : R.string.EventLogPromotedManageVoiceChats, oldAdmin.rights.canManageVideoChats, newAdmin.rights.canManageVideoChats, false);
          if (!msg.isChannelPost) {
            appendRight(b, R.string.EventLogPromotedRemainAnonymous, oldAdmin.rights.isAnonymous, newAdmin.rights.isAnonymous, false);
          }
          appendRight(b, R.string.EventLogPromotedAddAdmins, oldAdmin.rights.canPromoteMembers, newAdmin.rights.canPromoteMembers, false);
          appendRight(b, R.string.EventLogPromotedTitle, R.string.EventLogPromotedTitleChange, oldAdmin.customTitle, newAdmin.customTitle, false);
        } else if (oldStatus != null && newStatus != null) {
          final boolean oldCanReadMessages = oldStatus.getConstructor() != TdApi.ChatMemberStatusBanned.CONSTRUCTOR;
          final boolean newCanReadMessages = newStatus.getConstructor() != TdApi.ChatMemberStatusBanned.CONSTRUCTOR;

          final TdApi.ChatMemberStatusRestricted oldBan = oldStatus.getConstructor() == TdApi.ChatMemberStatusRestricted.CONSTRUCTOR ? (TdApi.ChatMemberStatusRestricted) oldStatus : null;
          final TdApi.ChatMemberStatusRestricted newBan = newStatus.getConstructor() == TdApi.ChatMemberStatusRestricted.CONSTRUCTOR ? (TdApi.ChatMemberStatusRestricted) newStatus : null;

          if (memberId.getConstructor() == TdApi.MessageSenderUser.CONSTRUCTOR) {
            appendRight(b, R.string.EventLogRestrictedReadMessages, oldCanReadMessages, newCanReadMessages, false);
          }
          appendRight(b, R.string.EventLogRestrictedSendMessages, oldBan != null ? oldBan.permissions.canSendBasicMessages : oldCanReadMessages, newBan != null ? newBan.permissions.canSendBasicMessages : newCanReadMessages, false);
          appendRight(b, R.string.EventLogRestrictedSendPhoto, oldBan != null ? oldBan.permissions.canSendPhotos : oldCanReadMessages, newBan != null ? newBan.permissions.canSendPhotos : newCanReadMessages, false);
          appendRight(b, R.string.EventLogRestrictedSendVideo, oldBan != null ? oldBan.permissions.canSendVideos : oldCanReadMessages, newBan != null ? newBan.permissions.canSendVideos : newCanReadMessages, false);
          appendRight(b, R.string.EventLogRestrictedSendAudio, oldBan != null ? oldBan.permissions.canSendAudios : oldCanReadMessages, newBan != null ? newBan.permissions.canSendAudios : newCanReadMessages, false);
          appendRight(b, R.string.EventLogRestrictedSendDocs, oldBan != null ? oldBan.permissions.canSendDocuments : oldCanReadMessages, newBan != null ? newBan.permissions.canSendDocuments : newCanReadMessages, false);
          appendRight(b, R.string.EventLogRestrictedSendVoiceNotes, oldBan != null ? oldBan.permissions.canSendVoiceNotes : oldCanReadMessages, newBan != null ? newBan.permissions.canSendVoiceNotes : newCanReadMessages, false);
          appendRight(b, R.string.EventLogRestrictedSendVideoNotes, oldBan != null ? oldBan.permissions.canSendVideoNotes : oldCanReadMessages, newBan != null ? newBan.permissions.canSendVideoNotes : newCanReadMessages, false);
          appendRight(b, R.string.EventLogRestrictedSendStickers, oldBan != null ? oldBan.permissions.canSendOtherMessages : oldCanReadMessages, newBan != null ? newBan.permissions.canSendOtherMessages : newCanReadMessages, false);
          appendRight(b, R.string.EventLogRestrictedSendPolls, oldBan != null ? oldBan.permissions.canSendOtherMessages : oldCanReadMessages, newBan != null ? newBan.permissions.canSendOtherMessages : newCanReadMessages, false);
          appendRight(b, R.string.EventLogRestrictedSendEmbed, oldBan != null ? oldBan.permissions.canAddWebPagePreviews : oldCanReadMessages, newBan != null ? newBan.permissions.canAddWebPagePreviews : newCanReadMessages, false);
          appendRight(b, R.string.EventLogRestrictedAddUsers, oldBan != null ? oldBan.permissions.canInviteUsers : oldCanReadMessages, newBan != null ? newBan.permissions.canInviteUsers : newCanReadMessages, false);
          appendRight(b, R.string.EventLogRestrictedPinMessages, oldBan != null ? oldBan.permissions.canPinMessages : oldCanReadMessages, newBan != null ? newBan.permissions.canPinMessages : newCanReadMessages, false);
          appendRight(b, R.string.EventLogRestrictedChangeInfo, oldBan != null ? oldBan.permissions.canChangeInfo : oldCanReadMessages, newBan != null ? newBan.permissions.canChangeInfo : newCanReadMessages, false);
        }

        TdApi.FormattedText formattedText = new TdApi.FormattedText(b.toString().trim(), null);
        if (!entities.isEmpty()) {
          formattedText.entities = new TdApi.TextEntity[entities.size()];
          entities.toArray(formattedText.entities);
        }
        fullMessage = new TGMessageText(context, msg, formattedText);
        break;
      }
      // only service message
      case TdApi.ChatEventMessageUnpinned.CONSTRUCTOR:
      case TdApi.ChatEventInvitesToggled.CONSTRUCTOR:
      case TdApi.ChatEventSignMessagesToggled.CONSTRUCTOR:
      case TdApi.ChatEventHasProtectedContentToggled.CONSTRUCTOR:
      case TdApi.ChatEventIsAllHistoryAvailableToggled.CONSTRUCTOR:
      case TdApi.ChatEventStickerSetChanged.CONSTRUCTOR:
      case TdApi.ChatEventLinkedChatChanged.CONSTRUCTOR:
      case TdApi.ChatEventSlowModeDelayChanged.CONSTRUCTOR:
      case TdApi.ChatEventLocationChanged.CONSTRUCTOR:
      case TdApi.ChatEventVideoChatMuteNewParticipantsToggled.CONSTRUCTOR:
      case TdApi.ChatEventMemberJoinedByInviteLink.CONSTRUCTOR:
      case TdApi.ChatEventMemberJoinedByRequest.CONSTRUCTOR:
      case TdApi.ChatEventInviteLinkRevoked.CONSTRUCTOR:
      case TdApi.ChatEventInviteLinkDeleted.CONSTRUCTOR:
      case TdApi.ChatEventVideoChatParticipantVolumeLevelChanged.CONSTRUCTOR:
      case TdApi.ChatEventVideoChatParticipantIsMutedToggled.CONSTRUCTOR:
      case TdApi.ChatEventHasAggressiveAntiSpamEnabledToggled.CONSTRUCTOR:
      case TdApi.ChatEventActiveUsernamesChanged.CONSTRUCTOR:
      case TdApi.ChatEventIsForumToggled.CONSTRUCTOR:
      case TdApi.ChatEventForumTopicCreated.CONSTRUCTOR:
      case TdApi.ChatEventForumTopicDeleted.CONSTRUCTOR:
      case TdApi.ChatEventForumTopicEdited.CONSTRUCTOR:
      case TdApi.ChatEventForumTopicPinned.CONSTRUCTOR:
      case TdApi.ChatEventForumTopicToggleIsClosed.CONSTRUCTOR:
      case TdApi.ChatEventForumTopicToggleIsHidden.CONSTRUCTOR:
        throw new IllegalArgumentException(action.toString());
      default:
        throw new UnsupportedOperationException(action.toString());
    }
    return fullMessage;
  }

  private static void appendRight (StringBuilder b, int res, boolean oldValue, boolean value, boolean needEqual) {
    if (oldValue != value) {
      b.append('\n');
      b.append(value ? '+' : '-');
      b.append(' ');
      b.append(Lang.getString(res));
    } else if (needEqual && value) {
      b.append('\n');
      b.append(Lang.getString(res));
      if (!value)
        b.append(" (-)");
    }
  }

  private static void appendRight (StringBuilder b, int res, int doubleRes, String oldValue, String newValue, boolean needEqual) {
    if (!StringUtils.equalsOrBothEmpty(oldValue, newValue)) {
      b.append('\n');
      if (StringUtils.isEmpty(oldValue)) {
        b.append("+ ").append(Lang.getString(res, newValue));
      } else if (StringUtils.isEmpty(newValue)) {
        b.append("- ").append(Lang.getString(res, oldValue));
      } else {
        b.append("+ ").append(Lang.getString(doubleRes, oldValue, newValue));
      }
    } else if (needEqual && !StringUtils.isEmpty(newValue)) {
      b.append(Lang.getString(res, newValue));
    }
  }

  private static TdApi.MessageContent convertToNativeMessageContent (TdApi.ChatEvent event) {
    switch (event.action.getConstructor()) {
      case TdApi.ChatEventMessageAutoDeleteTimeChanged.CONSTRUCTOR:
        return new TdApi.MessageChatSetMessageAutoDeleteTime(((TdApi.ChatEventMessageAutoDeleteTimeChanged) event.action).newMessageAutoDeleteTime, 0);
      case TdApi.ChatEventVideoChatCreated.CONSTRUCTOR:
        return new TdApi.MessageVideoChatStarted(((TdApi.ChatEventVideoChatCreated) event.action).groupCallId);
      case TdApi.ChatEventVideoChatEnded.CONSTRUCTOR:
        return new TdApi.MessageVideoChatEnded(0);
      case TdApi.ChatEventMemberJoined.CONSTRUCTOR: {
        final long userId = Td.getSenderUserId(event.memberId);
        return new TdApi.MessageChatAddMembers(new long[] {userId});
      }
      case TdApi.ChatEventMemberLeft.CONSTRUCTOR: {
        final long userId = Td.getSenderUserId(event.memberId);
        return new TdApi.MessageChatDeleteMember(userId);
      }
      case TdApi.ChatEventTitleChanged.CONSTRUCTOR: {
        TdApi.ChatEventTitleChanged e = (TdApi.ChatEventTitleChanged) event.action;
        return new TdApi.MessageChatChangeTitle(e.newTitle);
      }
      case TdApi.ChatEventPhotoChanged.CONSTRUCTOR: {
        TdApi.ChatEventPhotoChanged e = (TdApi.ChatEventPhotoChanged) event.action;
        if (e.newPhoto != null || e.oldPhoto != null) {
          return new TdApi.MessageChatChangePhoto(e.newPhoto != null ? e.newPhoto : e.oldPhoto);
        } else {
          return new TdApi.MessageChatDeletePhoto();
        }
      }
      case TdApi.ChatEventPermissionsChanged.CONSTRUCTOR: {
        StringBuilder b = new StringBuilder(Lang.getString(R.string.EventLogPermissions));
        int length = b.length();

        b.append("\n");

        TdApi.ChatEventPermissionsChanged permissions = (TdApi.ChatEventPermissionsChanged) event.action;

        appendRight(b, R.string.EventLogPermissionSendMessages, permissions.oldPermissions.canSendBasicMessages, permissions.newPermissions.canSendBasicMessages, true);
        appendRight(b, R.string.EventLogPermissionSendPhoto, permissions.oldPermissions.canSendPhotos, permissions.newPermissions.canSendPhotos, true);
        appendRight(b, R.string.EventLogPermissionSendVideo, permissions.oldPermissions.canSendVideos, permissions.newPermissions.canSendVideos, true);
        appendRight(b, R.string.EventLogPermissionSendAudio, permissions.oldPermissions.canSendAudios, permissions.newPermissions.canSendAudios, true);
        appendRight(b, R.string.EventLogPermissionSendDocs, permissions.oldPermissions.canSendDocuments, permissions.newPermissions.canSendDocuments, true);
        appendRight(b, R.string.EventLogPermissionSendVoiceNotes, permissions.oldPermissions.canSendVoiceNotes, permissions.newPermissions.canSendVoiceNotes, true);
        appendRight(b, R.string.EventLogPermissionSendVideoNotes, permissions.oldPermissions.canSendVideoNotes, permissions.newPermissions.canSendVideoNotes, true);
        appendRight(b, R.string.EventLogPermissionSendStickers, permissions.oldPermissions.canSendOtherMessages, permissions.newPermissions.canSendOtherMessages, true);
        appendRight(b, R.string.EventLogPermissionSendPolls, permissions.oldPermissions.canSendPolls, permissions.newPermissions.canSendPolls, true);
        appendRight(b, R.string.EventLogPermissionSendEmbed, permissions.oldPermissions.canAddWebPagePreviews, permissions.newPermissions.canAddWebPagePreviews, true);
        appendRight(b, R.string.EventLogPermissionAddUsers, permissions.oldPermissions.canInviteUsers, permissions.newPermissions.canInviteUsers, true);
        appendRight(b, R.string.EventLogPermissionPinMessages, permissions.oldPermissions.canPinMessages, permissions.newPermissions.canPinMessages, true);
        appendRight(b, R.string.EventLogPermissionChangeInfo, permissions.oldPermissions.canChangeInfo, permissions.newPermissions.canChangeInfo, true);

        TdApi.FormattedText formattedText = new TdApi.FormattedText(b.toString().trim(), new TdApi.TextEntity[] {new TdApi.TextEntity(0, length, new TdApi.TextEntityTypeItalic())});

        return new TdApi.MessageText(formattedText, null);
      }
      case TdApi.ChatEventAvailableReactionsChanged.CONSTRUCTOR: {
        TdApi.ChatEventAvailableReactionsChanged e = (TdApi.ChatEventAvailableReactionsChanged) event.action;

        boolean hasAll = false;
        Set<String> newReactions = new LinkedHashSet<>();

        switch (e.newAvailableReactions.getConstructor()) {
          case TdApi.ChatAvailableReactionsAll.CONSTRUCTOR:
            hasAll = true;
            break;
          case TdApi.ChatAvailableReactionsSome.CONSTRUCTOR:
            TdApi.ChatAvailableReactionsSome some = (TdApi.ChatAvailableReactionsSome) e.newAvailableReactions;
            for (TdApi.ReactionType type : some.reactions) {
              newReactions.add(TD.makeReactionKey(type));
            }
            break;
          default:
            throw new UnsupportedOperationException(e.newAvailableReactions.toString());
        }

        boolean hadAll = false;
        Set<String> oldReactions = new LinkedHashSet<>();
        switch (e.oldAvailableReactions.getConstructor()) {
          case TdApi.ChatAvailableReactionsAll.CONSTRUCTOR:
            hadAll = true;
            break;
          case TdApi.ChatAvailableReactionsSome.CONSTRUCTOR:
            TdApi.ChatAvailableReactionsSome some = (TdApi.ChatAvailableReactionsSome) e.oldAvailableReactions;
            for (TdApi.ReactionType type : some.reactions) {
              oldReactions.add(TD.makeReactionKey(type));
            }
            break;
        }

        List<TdApi.TextEntity> entities = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        RunnableData<Set<String>> emojiAdder = set -> {
          boolean first = true;
          for (String reactionKey : set) {
            text.append(first ? "\n" : " ");
            TdApi.ReactionType reactionType = TD.toReactionType(reactionKey);
            switch (reactionType.getConstructor()) {
              case TdApi.ReactionTypeEmoji.CONSTRUCTOR:
                text.append(((TdApi.ReactionTypeEmoji) reactionType).emoji);
                break;
              case TdApi.ReactionTypeCustomEmoji.CONSTRUCTOR: {
                long customEmojiId = ((TdApi.ReactionTypeCustomEmoji) reactionType).customEmojiId;
                String emoji = "⁉️";
                int offset = text.length();
                text.append(emoji);
                entities.add(new TdApi.TextEntity(offset, emoji.length(), new TdApi.TextEntityTypeCustomEmoji(customEmojiId)));
                break;
              }
            }
            first = false;
          }
        };

        int headerLength = 0;

        if (hasAll == hadAll && !hasAll) {
          Set<String> addedReactions = new LinkedHashSet<>(newReactions);
          addedReactions.removeAll(oldReactions);

          Set<String> removedReactions = new LinkedHashSet<>(oldReactions);
          removedReactions.removeAll(newReactions);
          removedReactions.removeAll(addedReactions);

          text.append(Lang.getString(
            newReactions.isEmpty() ? R.string.EventLogReactionsDisabled :
              oldReactions.isEmpty() ? R.string.EventLogReactionsEnabled :
                R.string.EventLogReactionsChanged
          ));
          headerLength = text.length();

          if (!newReactions.isEmpty() && !oldReactions.isEmpty()) {
            emojiAdder.runWithData(newReactions);
          }

          if (!addedReactions.isEmpty()) {
            text.append("\n\n");
            text.append(Lang.getString(R.string.EventLogReactionsAdded2));
            emojiAdder.runWithData(addedReactions);
          }
          if (!removedReactions.isEmpty()) {
            text.append("\n\n");
            text.append(Lang.getString(R.string.EventLogReactionsRemoved2));
            emojiAdder.runWithData(removedReactions);
          }
        } else if (hasAll) {
          text.append(Lang.getString(R.string.EventLogReactionsEnabledAll));
          headerLength = text.length();
        } else if (newReactions.isEmpty()) {
          text.append(Lang.getString(R.string.EventLogReactionsDisabled));
          headerLength = text.length();
          if (!oldReactions.isEmpty()) {
            text.append("\n\n");
            text.append(Lang.getString(R.string.EventLogReactionsRemoved2));
            emojiAdder.runWithData(oldReactions);
          }
        } else {
          text.append(Lang.getString(hadAll ? R.string.EventLogReactionsLimited : R.string.EventLogReactionsChanged));
          headerLength = text.length();
          emojiAdder.runWithData(newReactions);
        }

        if (headerLength > 0) {
          entities.add(0, new TdApi.TextEntity(0, headerLength, new TdApi.TextEntityTypeItalic()));
        }

        TdApi.FormattedText formattedText = new TdApi.FormattedText(text.toString(), entities.toArray(new TdApi.TextEntity[0]));

        return new TdApi.MessageText(formattedText, null);
      }
      case TdApi.ChatEventInviteLinkEdited.CONSTRUCTOR: {
        TdApi.ChatEventInviteLinkEdited e = (TdApi.ChatEventInviteLinkEdited) event.action;

        boolean changedLimit = e.oldInviteLink.memberLimit != e.newInviteLink.memberLimit;
        boolean changedExpires = e.oldInviteLink.expirationDate != e.newInviteLink.expirationDate;

        String link = StringUtils.urlWithoutProtocol(e.newInviteLink.inviteLink);
        String prevLimit = e.oldInviteLink.memberLimit != 0 ? Strings.buildCounter(e.oldInviteLink.memberLimit) : Lang.getString(R.string.EventLogEditedInviteLinkNoLimit);
        String newLimit = e.newInviteLink.memberLimit != 0 ? Strings.buildCounter(e.newInviteLink.memberLimit) : Lang.getString(R.string.EventLogEditedInviteLinkNoLimit);

        String text;
        if (changedLimit && changedExpires) {
          String expires;
          if (e.newInviteLink.expirationDate == 0) {
            expires = Lang.getString(R.string.LinkExpiresNever);
          } else if (DateUtils.isToday(e.newInviteLink.expirationDate, TimeUnit.SECONDS)) {
            expires = Lang.getString(R.string.LinkExpiresTomorrow, Lang.time(e.newInviteLink.expirationDate, TimeUnit.SECONDS));
          } else if (DateUtils.isTomorrow(e.newInviteLink.expirationDate, TimeUnit.SECONDS)) {
            expires = Lang.getString(R.string.LinkExpiresTomorrow, Lang.time(e.newInviteLink.expirationDate, TimeUnit.SECONDS));
          } else {
            expires = Lang.getString(R.string.LinkExpiresFuture, Lang.getDate(e.newInviteLink.expirationDate, TimeUnit.SECONDS), Lang.time(e.newInviteLink.expirationDate, TimeUnit.SECONDS));
          }
          text = Lang.getString(R.string.EventLogEditedInviteLink, link, prevLimit, newLimit, expires);
        } else if (changedLimit) {
          text = Lang.getString(R.string.EventLogEditedInviteLinkLimit, link, prevLimit, newLimit);
        } else {
          if (e.newInviteLink.expirationDate == 0) {
            text = Lang.getString(R.string.EventLogEditedInviteLinkExpireNever, link);
          } else if (DateUtils.isToday(e.newInviteLink.expirationDate, TimeUnit.SECONDS)) {
            text = Lang.getString(R.string.EventLogEditedInviteLinkExpireToday, link, Lang.time(e.newInviteLink.expirationDate, TimeUnit.SECONDS));
          } else if (DateUtils.isTomorrow(e.newInviteLink.expirationDate, TimeUnit.SECONDS)) {
            text = Lang.getString(R.string.EventLogEditedInviteLinkExpireTomorrow, link, Lang.time(e.newInviteLink.expirationDate, TimeUnit.SECONDS));
          } else {
            text = Lang.getString(R.string.EventLogEditedInviteLinkExpireFuture, link, Lang.getDate(e.newInviteLink.expirationDate, TimeUnit.SECONDS), Lang.time(e.newInviteLink.expirationDate, TimeUnit.SECONDS));
          }
        }

        TdApi.FormattedText formattedText = new TdApi.FormattedText(text, Td.findEntities(text));
        return new TdApi.MessageText(formattedText, null);
      }

      // No native message interpretation.
      case TdApi.ChatEventDescriptionChanged.CONSTRUCTOR:
      case TdApi.ChatEventHasProtectedContentToggled.CONSTRUCTOR:
      case TdApi.ChatEventInviteLinkDeleted.CONSTRUCTOR:
      case TdApi.ChatEventInviteLinkRevoked.CONSTRUCTOR:
      case TdApi.ChatEventInvitesToggled.CONSTRUCTOR:
      case TdApi.ChatEventMemberInvited.CONSTRUCTOR:
      case TdApi.ChatEventIsAllHistoryAvailableToggled.CONSTRUCTOR:
      case TdApi.ChatEventLinkedChatChanged.CONSTRUCTOR:
      case TdApi.ChatEventLocationChanged.CONSTRUCTOR:
      case TdApi.ChatEventMemberJoinedByInviteLink.CONSTRUCTOR:
      case TdApi.ChatEventMemberJoinedByRequest.CONSTRUCTOR:
      case TdApi.ChatEventMemberPromoted.CONSTRUCTOR:
      case TdApi.ChatEventMemberRestricted.CONSTRUCTOR:
      case TdApi.ChatEventMessageDeleted.CONSTRUCTOR:
      case TdApi.ChatEventMessageEdited.CONSTRUCTOR:
      case TdApi.ChatEventMessagePinned.CONSTRUCTOR:
      case TdApi.ChatEventMessageUnpinned.CONSTRUCTOR:
      case TdApi.ChatEventPollStopped.CONSTRUCTOR:
      case TdApi.ChatEventSignMessagesToggled.CONSTRUCTOR:
      case TdApi.ChatEventSlowModeDelayChanged.CONSTRUCTOR:
      case TdApi.ChatEventStickerSetChanged.CONSTRUCTOR:
      case TdApi.ChatEventUsernameChanged.CONSTRUCTOR:
      case TdApi.ChatEventVideoChatMuteNewParticipantsToggled.CONSTRUCTOR:
      case TdApi.ChatEventVideoChatParticipantIsMutedToggled.CONSTRUCTOR:
      case TdApi.ChatEventVideoChatParticipantVolumeLevelChanged.CONSTRUCTOR:
      case TdApi.ChatEventHasAggressiveAntiSpamEnabledToggled.CONSTRUCTOR:
      case TdApi.ChatEventActiveUsernamesChanged.CONSTRUCTOR:
      case TdApi.ChatEventIsForumToggled.CONSTRUCTOR:
      case TdApi.ChatEventForumTopicCreated.CONSTRUCTOR:
      case TdApi.ChatEventForumTopicDeleted.CONSTRUCTOR:
      case TdApi.ChatEventForumTopicEdited.CONSTRUCTOR:
      case TdApi.ChatEventForumTopicPinned.CONSTRUCTOR:
      case TdApi.ChatEventForumTopicToggleIsHidden.CONSTRUCTOR:
      case TdApi.ChatEventForumTopicToggleIsClosed.CONSTRUCTOR:
        throw new IllegalArgumentException(event.action.toString());

        // Unsupported
      default:
        throw new UnsupportedOperationException(event.action.toString());
    }
  }
}
