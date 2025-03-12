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
 * File created on 11/08/2015 at 18:30
 */
package org.thunderdog.challegram.helper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.data.InlineResult;
import org.thunderdog.challegram.data.InlineResultCommand;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.data.TGMessageBotInfo;
import org.thunderdog.challegram.telegram.TdlibCache;
import org.thunderdog.challegram.ui.MessagesController;

import java.util.ArrayList;

import me.vkryl.core.StringUtils;
import tgx.td.ChatId;
import tgx.td.data.MessageWithProperties;

public class BotHelper implements Runnable, InlineSearchContext.CommandListProvider, TdlibCache.UserDataChangeListener, TdlibCache.BasicGroupDataChangeListener, TdlibCache.SupergroupDataChangeListener {
  private static final int TYPE_PRIVATE = 0;
  private static final int TYPE_GROUP = 1;
  private static final int TYPE_SUPERGROUP = 2;

  private static final int FLAG_DESTROYED = 0x01;
  private static final int FLAG_LOADED = 0x02;

  private int flags;
  private final MessagesController context;

  private long chatId;
  private int type;

  private TGMessageBotInfo botInfoMessage;

  private long objectId;

  private ArrayList<InlineResult<?>> commands;

  public BotHelper (@NonNull MessagesController controller, TdApi.Chat chat) {
    this.context = controller;
    if (chat != null) {
      this.chatId = chat.id;
      switch (chat.type.getConstructor()) {
        case TdApi.ChatTypeBasicGroup.CONSTRUCTOR: {
          type = TYPE_GROUP;
          objectId = ChatId.toBasicGroupId(chat.id);
          break;
        }
        case TdApi.ChatTypeSupergroup.CONSTRUCTOR: {
          type = TYPE_SUPERGROUP;
          objectId = ChatId.toSupergroupId(chat.id);
          break;
        }
        case TdApi.ChatTypePrivate.CONSTRUCTOR: {
          type = TYPE_PRIVATE;
          objectId = TD.getUserId(chat);
          break;
        }
      }
      load();
      loadReplyMarkup(chat.replyMarkupMessageId);
    } else {
      loadReplyMarkup(0);
    }
  }

  public @Nullable InlineResult<?> findHelpCommand () {
    return findCommand("/help");
  }

  public @Nullable InlineResult<?> findSettingsCommand () {
    return findCommand("/settings");
  }

  public @Nullable InlineResult<?> findCommand (@NonNull String commandStr) {
    if (commands == null)
      return null;
    for (InlineResult<?> command : commands) {
      if (command instanceof InlineResultCommand && commandStr.equals(((InlineResultCommand) command).getCommand()))
        return command;
    }
    return null;
  }

  public boolean hasCommands () {
    return commands != null && !commands.isEmpty();
  }

  // Public utils

  public void destroy () {
    flags |= FLAG_DESTROYED;
    switch (type) {
      case TYPE_GROUP:
        context.tdlib().cache().unsubscribeFromGroupUpdates(objectId, this);
        break;
      case TYPE_PRIVATE:
        context.tdlib().cache().removeUserDataListener(objectId, this);
        break;
      case TYPE_SUPERGROUP:
        context.tdlib().cache().unsubscribeFromSupergroupUpdates(objectId, this);
        break;
    }
  }

  private String awaitQuery;
  private InlineSearchContext.QueryResultsChangeListener awaitListener;
  private boolean awaitListenerForCommands;

  private String lastPrefix, lastQuery;
  private ArrayList<InlineResult<?>> lastResult;

  private void resetResultsCache () {
    lastPrefix = lastQuery = null;
    lastResult = null;
  }

  @Override
  public ArrayList<InlineResult<?>> searchCommands (String prefix, String currentQuery, InlineSearchContext.QueryResultsChangeListener changeListener) {
    if ((flags & FLAG_LOADED) == 0) {
      awaitQuery = currentQuery;
      awaitListener = changeListener;
      awaitListenerForCommands = true;
      return null;
    }

    ArrayList<InlineResult<?>> commands = this.commands;
    ArrayList<InlineResult<?>> result = null;

    if (lastPrefix != null && lastQuery != null && prefix.startsWith(lastPrefix)) {
      commands = lastResult;
    }

    if (commands != null) {
      for (InlineResult<?> __command : commands) {
        InlineResultCommand command = (InlineResultCommand) __command;
        if (command.matchesPrefix(prefix)) {
          if (result == null) {
            result = new ArrayList<>(commands.size());
          }
          result.add(command);
        }
      }
    }

    lastQuery = currentQuery;
    lastPrefix = prefix;
    lastResult = result;

    return result;
  }

  // Private utils

  private void processUserFull (TdApi.UserFullInfo full) {
    if (full == null) {
      return;
    }

    if (full.botInfo != null) {
      if (!StringUtils.isEmpty(full.botInfo.description) && !context.areScheduledOnly()) {
        this.botInfoMessage = new TGMessageBotInfo(context.getManager(), chatId, full.botInfo.description);
      }

      if (full.botInfo.commands.length > 0) {
        ArrayList<InlineResult<?>> commands = new ArrayList<>(full.botInfo.commands.length);
        for (TdApi.BotCommand command : full.botInfo.commands) {
          commands.add(new InlineResultCommand(context.context(), context.tdlib(), objectId, command));
        }
        this.commands = commands;
        context.tdlib().uiExecute(this);
      } else if (botInfoMessage != null) {
        context.tdlib().uiExecute(this);
      }
    }
  }

  @Override
  public void onUserFullUpdated (long userId, TdApi.UserFullInfo userFull) {
    processUserFull(userFull);
  }

  @Override
  public void onBasicGroupUpdated (TdApi.BasicGroup basicGroup, boolean migratedToSupergroup) {
    context.tdlib().uiExecute(() -> setIsForcelyHidden(!TD.isMember(basicGroup.status)));
  }

  @Override
  public void onSupergroupUpdated (TdApi.Supergroup supergroup) {
    context.tdlib().uiExecute(() -> setIsForcelyHidden(!TD.isMember(supergroup.status)));
  }

  @Override
  public void onSupergroupFullUpdated (long supergroupId, TdApi.SupergroupFullInfo newSupergroupFull) {
    processSupergroupFull(newSupergroupFull);
  }

  @Override
  public void onBasicGroupFullUpdated (long basicGroupId, TdApi.BasicGroupFullInfo basicGroupFull) {
    processGroupFull(basicGroupFull);
  }

  private void load () {
    switch (type) {
      case TYPE_PRIVATE: {
        context.tdlib().cache().addUserDataListener(objectId, this);
        processUserFull(context.tdlib().cache().userFull(objectId));
        break;
      }
      case TYPE_GROUP: {
        context.tdlib().cache().subscribeToGroupUpdates(objectId, this);
        processGroupFull(context.tdlib().cache().basicGroupFull(objectId));
        break;
      }
      case TYPE_SUPERGROUP: {
        context.tdlib().cache().subscribeToSupergroupUpdates(objectId, this);
        processSupergroupFull(context.tdlib().cache().supergroupFull(objectId));
        break;
      }
    }
  }

  private void processGroupFull (TdApi.BasicGroupFullInfo groupFull) {
    if (groupFull != null) {
      parseCommands(groupFull.botCommands);
    }
  }

  private void processSupergroupFull (TdApi.SupergroupFullInfo supergroupFull) {
    if (supergroupFull != null) {
      parseCommands(supergroupFull.botCommands);
    }
  }

  private void parseCommands (TdApi.BotCommands[] botCommands) {
    ArrayList<InlineResult<?>> commandList = new ArrayList<>();

    int botsCount = 0;
    for (TdApi.BotCommands commands : botCommands) {
      if (commands.commands.length == 0)
        continue;
      TdApi.User user = context.tdlib().cache().user(commands.botUserId);
      if (user == null)
        continue;
      botsCount++;
      for (TdApi.BotCommand command : commands.commands) {
        commandList.add(new InlineResultCommand(context.context(), context.tdlib(), user, command));
      }
    }

    final int prevBotCount = this.botsCount;
    this.botsCount = botsCount;
    this.commands = commandList;
    if (botsCount > 0 || prevBotCount > 0) {
      context.tdlib().uiExecute(this);
    }

    context.tdlib().uiExecute(() -> {
      if (awaitListener != null && awaitQuery != null && !awaitListenerForCommands) {
        awaitListener.onQueryResultsChanged(awaitQuery);
        awaitQuery = null;
        awaitListener = null;
      }
    });
  }

  // Markup

  private void loadReplyMarkup (long messageId) {
    if (processedMarkupMessageId == 0 && messageId != 0) {
      loadMessage(chatId, messageId);
    }
  }

  private void loadMessage (long chatId, long messageId) {
    context.tdlib().send(new TdApi.GetMessage(chatId, messageId), (message, error) -> {
      if (message != null) {
        context.tdlib().send(new TdApi.GetMessageProperties(chatId, messageId), (properties, error1) -> {
          if (properties != null) {
            context.tdlib().uiExecute(() -> processReplyMarkup(new MessageWithProperties(message, properties), true));
          }
        });
      }
    });
  }

  public void updateReplyMarkup (long chatId, long messageId) {
    if (messageId == 0) {
      context.tdlib().uiExecute(() -> processReplyMarkup(null, true));
    } else {
      loadMessage(chatId, messageId);
    }
  }

  private boolean isForcelyHidden;

  private void setIsForcelyHidden (boolean isHidden) {
    if (this.isForcelyHidden != isHidden) {
      this.isForcelyHidden = isHidden;
      processReplyMarkup(isHidden ? null : lastReplyMarkup, false);
    }
  }

  private boolean shouldHideMarkup (long chatId) {
    TdApi.ChatMemberStatus status = context.tdlib().chatStatus(chatId);
    return status != null && !TD.isMember(status);
  }

  private long processedMarkupMessageId;
  private MessageWithProperties lastReplyMarkup;

  private void processReplyMarkup (MessageWithProperties message, boolean remember) {
    if (remember) {
      this.lastReplyMarkup = message;
      if (isForcelyHidden || (message != null && shouldHideMarkup(message.message.chatId))) {
        isForcelyHidden = true;
        message = null;
      }
    }
    TdApi.ReplyMarkup markup = message != null ? message.message.replyMarkup : null;
    long messageId = message == null ? 0 : message.message.id;
    if ((flags & FLAG_DESTROYED) != 0) return;
    if (messageId > processedMarkupMessageId) {
      processedMarkupMessageId = messageId;
    } else if (messageId != 0) {
      return;
    }
    if (messageId == 0 || markup == null) {
      context.onDestroyCommandKeyboard();
      return;
    }
    switch (markup.getConstructor()) {
      case TdApi.ReplyMarkupRemoveKeyboard.CONSTRUCTOR: {
        TdApi.ReplyMarkupRemoveKeyboard removeKeyboard = (TdApi.ReplyMarkupRemoveKeyboard) markup;
        processHideKeyboard(messageId, removeKeyboard.isPersonal);
        break;
      }
      case TdApi.ReplyMarkupForceReply.CONSTRUCTOR: {
        TdApi.ReplyMarkupForceReply forceReply = (TdApi.ReplyMarkupForceReply) markup;
        processForceReply(message, forceReply.isPersonal);
        context.setCustomBotPlaceholder(forceReply.inputFieldPlaceholder);
        break;
      }
      case TdApi.ReplyMarkupShowKeyboard.CONSTRUCTOR: {
        TdApi.ReplyMarkupShowKeyboard showKeyboard = (TdApi.ReplyMarkupShowKeyboard) markup;
        processShowKeyboard(messageId, showKeyboard);
        if (type == TYPE_GROUP || type == TYPE_SUPERGROUP) {
          context.showReply(message, null, false, false); // FIXME?
        }
        context.setCustomBotPlaceholder(showKeyboard.inputFieldPlaceholder);
        break;
      }
      case TdApi.ReplyMarkupInlineKeyboard.CONSTRUCTOR: {
        break;
      }
    }
  }

  private void processHideKeyboard (long messageId, boolean personal) {
    context.hideKeyboard(personal);
    if (messageId != 0) {
      context.tdlib().send(new TdApi.DeleteChatReplyMarkup(chatId, messageId), context.tdlib().typedOkHandler());
    }
  }

  private void processForceReply (MessageWithProperties message, boolean personal) {
    if (personal) {
      context.showKeyboard();
      context.showReply(message, null, false, false);
    } else if (type == TYPE_PRIVATE) {
      context.showReply(message, null, false, false);
    }
    if (message != null) {
      context.tdlib().send(new TdApi.DeleteChatReplyMarkup(chatId, message.message.id), context.tdlib().typedOkHandler());
    }
  }

  private void processShowKeyboard (long messageId, TdApi.ReplyMarkupShowKeyboard markup) {
    context.showKeyboard(messageId, markup);
  }

  public void onDestroyKeyboard (long messageId) {
    if (this.processedMarkupMessageId == messageId) {
      context.tdlib().send(new TdApi.DeleteChatReplyMarkup(chatId, messageId), context.tdlib().typedOkHandler());
    }
  }

  // Called when data's been loaded

  private int botsCount;

  @Override
  public void run () {
    if ((flags & FLAG_DESTROYED) != 0) return;
    flags |= FLAG_LOADED;

    if (awaitQuery != null && awaitListener != null && awaitListenerForCommands) {
      resetResultsCache();
      awaitListener.onQueryResultsChanged(awaitQuery);
      awaitQuery = null;
      awaitListener = null;
    }

    if (context != null) {
      if (botInfoMessage != null) {
        context.getManager().setHeaderMessage(botInfoMessage);
      } else {
        context.getManager().checkBotStart();
      }
      context.updateCommandButton((botsCount > 0 || type == TYPE_PRIVATE) && hasCommands());
    }
  }

  private boolean isDestroyed () {
    return (flags & FLAG_DESTROYED) != 0;
  }
}
