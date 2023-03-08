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
 * File created on 24/09/2015 at 23:57
 */
package org.thunderdog.challegram.component.chat;

import android.os.SystemClock;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.collection.LongSparseArray;

import org.drinkless.td.libcore.telegram.Client;
import org.drinkless.td.libcore.telegram.TdApi;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.thunderdog.challegram.BuildConfig;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Background;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.ChatEventUtil;
import org.thunderdog.challegram.data.SponsoredMessageUtils;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.data.TGMessage;
import org.thunderdog.challegram.data.TdApiExt;
import org.thunderdog.challegram.data.ThreadInfo;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibDelegate;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.util.CancellableResultHandler;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

import me.vkryl.core.DateUtils;
import me.vkryl.core.MathUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.core.lambda.RunnableData;
import me.vkryl.td.ChatId;
import me.vkryl.td.MessageId;
import me.vkryl.td.Td;
import me.vkryl.td.TdConstants;

public class MessagesLoader implements Client.ResultHandler {
  private static final int CHUNK_SIZE_BIG = 50;
  private static final int CHUNK_SIZE_SMALL = 19;

  private static final int CHUNK_SEARCH_OFFSET = -19;
  private static final int CHUNK_SIZE_SEARCH = -CHUNK_SEARCH_OFFSET + 14;

  private static final int CHUNK_SIZE_BOTTOM = 31;
  private static final int CHUNK_BOTTOM_OFFSET = -30;

  public static final int MODE_INITIAL = 0;
  public static final int MODE_MORE_TOP = 1;
  public static final int MODE_MORE_BOTTOM = 2;
  public static final int MODE_REPEAT_INITIAL = 3;

  private final MessagesManager manager;
  private final Tdlib tdlib;

  private boolean canLoadTop;
  private boolean canLoadBottom;

  private boolean isLoading;
  private int loadingMode;

  private boolean isLoadingSponsoredMessage;

  // private TGMessage edgeMessage;

  private int knownTotalMessageCount = -1;
  private MessageId scrollMessageId;
  private int scrollHighlightMode;

  public static final int SPECIAL_MODE_NONE = 0;
  public static final int SPECIAL_MODE_EVENT_LOG = 1;
  public static final int SPECIAL_MODE_SEARCH = 2;
  public static final int SPECIAL_MODE_RESTRICTED = 3;
  public static final int SPECIAL_MODE_SCHEDULED = 4;

  private int specialMode;

  private String searchQuery;
  private TdApi.MessageSender searchSender;
  private TdApi.SearchMessagesFilter searchFilter;

  private @Nullable TdApi.Chat chat;
  private @Nullable ThreadInfo messageThread;

  private CancellableResultHandler sponsoredResultHandler;
  private final MessagesSearchManagerMiddleware searchManagerMiddleware;

  private long contextId;

  private boolean canShowSponsoredMessage (long chatId) {
    return tdlib.isChannel(chatId) && !manager.controller().isInForceTouchMode() && !manager.controller().inPreviewMode() && !manager.controller().areScheduledOnly() && !manager.controller().arePinnedMessages();
  }

  // Callback is called only on successful load
  public void requestSponsoredMessage (long chatId, RunnableData<TdApi.SponsoredMessages> callback) {
    if (!canShowSponsoredMessage(chatId) || isLoadingSponsoredMessage) {
      return;
    }

    isLoadingSponsoredMessage = true;
    sponsoredResultHandler = new CancellableResultHandler() {
      @Override
      public void processResult (TdApi.Object object) {
        UI.post(() -> {
          isLoadingSponsoredMessage = false;
          sponsoredResultHandler = null;

          TdApi.SponsoredMessages message;

          if (object.getConstructor() == TdApi.SponsoredMessages.CONSTRUCTOR) {
            message = ((TdApi.SponsoredMessages) object);
          } else if (tdlib.account().isDebug()) {
            message = SponsoredMessageUtils.generateSponsoredMessages(tdlib);
          } else {
            message = null;
          }

          if (chatId == getChatId()) {
            callback.runWithData(message);
          }
        });
      }
    };

    tdlib.client().send(new TdApi.GetChatSponsoredMessages(chatId), sponsoredResultHandler);
  }

  public MessagesLoader (MessagesManager manager, MessagesSearchManagerMiddleware searchMiddleware) {
    this.manager = manager;
    this.tdlib = manager.controller().tdlib();
    this.searchManagerMiddleware = searchMiddleware;
    reuse();
  }

  public void setChat (@Nullable TdApi.Chat chat, @Nullable ThreadInfo messageThread, int mode, TdApi.SearchMessagesFilter filter) {
    this.chat = chat;
    this.messageThread = messageThread;
    this.specialMode = mode;
    this.searchFilter = filter;
  }

  public void setSearchParameters (String query, TdApi.MessageSender sender, TdApi.SearchMessagesFilter filter) {
    this.searchQuery = query;
    this.searchSender = sender;
    this.searchFilter = filter;
    this.lastSearchNextOffset = null;
    this.lastSearchNextFromMessageId = 0;
  }

  public int getSpecialMode () {
    return specialMode;
  }

  public long getChatId () {
    return messageThread != null ? messageThread.getChatId() : chat != null ? chat.id : 0;
  }

  public long getMessageThreadId () {
    return messageThread != null ? messageThread.getMessageThreadId() : 0;
  }

  @Nullable
  public ThreadInfo getMessageThread () {
    return messageThread;
  }

  private static final int MERGE_MODE_NONE = 0;
  private static final int MERGE_MODE_TOP = 1;
  private static final int MERGE_MODE_BOTTOM = 2;

  private volatile Client.ResultHandler lastHandler;
  private TdApi.Message[] mergeChunk;
  private int mergeMode;

  private Client.ResultHandler newHandler (final boolean allowMoreTop, final boolean allowMoreBottom, boolean needFindUnread) {
    final long currentContextId = contextId;
    if (lastHandler != null) {
      throw new IllegalStateException("lastHandler != null");
    }
    return lastHandler = new Client.ResultHandler() {
      @Override
      public void onResult (final TdApi.Object object) {
        if (contextId != currentContextId) {
          Log.i(Log.TAG_MESSAGES_LOADER, "Ignoring messages result, because contextId has changed");
          return;
        }
        if (lastHandler != this) {
          Log.i(Log.TAG_MESSAGES_LOADER, "Ignoring messages result, because lastHandler has changed");
          return;
        }

        long ms = SystemClock.elapsedRealtime() - lastRequestTime;

        // int constructor = object.getConstructor();

        final int knownTotalCount;
        TdApi.Message[] messages;
        final String nextSearchOffset;
        final long nextSearchFromMessageId;
        switch (object.getConstructor()) {
          case TdApi.Messages.CONSTRUCTOR: {
            if (Log.isEnabled(Log.TAG_MESSAGES_LOADER)) {
              Log.i(Log.TAG_MESSAGES_LOADER, "Received %d messages in %dms", ((TdApi.Messages) object).messages.length, ms);
            }
            TdApi.Messages result = (TdApi.Messages) object;
            messages = result.messages;
            knownTotalCount = result.totalCount;
            nextSearchOffset = null; nextSearchFromMessageId = 0;
            break;
          }
          case TdApi.FoundChatMessages.CONSTRUCTOR: {
            if (Log.isEnabled(Log.TAG_MESSAGES_LOADER)) {
              Log.i(Log.TAG_MESSAGES_LOADER, "Received %d found messages in %dms", ((TdApi.FoundChatMessages) object).messages.length, ms);
            }
            TdApi.FoundChatMessages result = (TdApi.FoundChatMessages) object;
            messages = result.messages;
            knownTotalCount = result.totalCount;
            nextSearchFromMessageId = result.nextFromMessageId;
            nextSearchOffset = null;
            break;
          }
          case TdApi.FoundMessages.CONSTRUCTOR: {
            if (Log.isEnabled(Log.TAG_MESSAGES_LOADER)) {
              Log.i(Log.TAG_MESSAGES_LOADER, "Received %d secret messages in %dms", ((TdApi.FoundMessages) object).messages.length, ms);
            }
            TdApi.FoundMessages foundMessages = (TdApi.FoundMessages) object;
            messages = foundMessages.messages;
            nextSearchOffset = foundMessages.nextOffset;
            knownTotalCount = foundMessages.totalCount;
            nextSearchFromMessageId = 0;
            break;
          }
          case TdApi.Error.CONSTRUCTOR: {
            Log.w(Log.TAG_MESSAGES_LOADER, "Received error: %s", TD.toErrorString(object));
            messages = new TdApi.Message[0];
            knownTotalCount = -1;
            nextSearchOffset = null; nextSearchFromMessageId = 0;
            break;
          }
          case TdApi.ChatEvents.CONSTRUCTOR: {
            if (Log.isEnabled(Log.TAG_MESSAGES_LOADER)) {
              Log.i(Log.TAG_MESSAGES_LOADER, "Received %d events in %dms", ((TdApi.ChatEvents) object).events.length, ms);
            }
            messages = parseChatEvents(getChatId(), ((TdApi.ChatEvents) object).events);
            knownTotalCount = -1;
            nextSearchOffset = null; nextSearchFromMessageId = 0;
            break;
          }
          default: {
            synchronized (lock) {
              lastHandler = null;
            }
            Log.unexpectedTdlibResponse(object,
              TdApi.GetChatHistory.class,
              TdApi.Messages.class, TdApi.FoundMessages.class, TdApi.FoundChatMessages.class,
              TdApi.ChatEvents.class,
              TdApi.Error.class
            );
            return;
          }
        }

        boolean needMoreTop = allowMoreTop;
        boolean needMoreBottom = allowMoreBottom;
        TdApi.Message[] mergingChunk = mergeChunk;
        int mergingMode = mergeMode;

        if (mergingMode == MERGE_MODE_TOP) {
          needMoreTop = false;
          long mediaGroupId = mergingChunk[mergeChunk.length - 1].mediaAlbumId;
          long messageId = mergingChunk[mergeChunk.length - 1].id;
          int skipCount = 0;
          int addCount = 0;
          for (int i = 0; i < messages.length; i++) {
            TdApi.Message message = messages[i];
            if (message.id >= messageId) {
              skipCount++;
            } else if (message.mediaAlbumId == mediaGroupId) {
              addCount++;
            } else if (message.mediaAlbumId == 0) {
              if (Config.ALLOW_MORE_CACHED_MESSAGES) {
                addCount++;
              } else {
                break;
              }
            } else {
              if (!Config.ALLOW_MORE_CACHED_MESSAGES) {
                break;
              }
              int attemptCount = 1;
              long attemptMediaId = message.mediaAlbumId;
              while (i + 1 < messages.length) {
                TdApi.Message nextMessage = messages[i + 1];
                if (nextMessage.mediaAlbumId != attemptMediaId) {
                  addCount += attemptCount;
                  i++;
                  while (i < messages.length && (attemptMediaId = messages[i].mediaAlbumId) == 0) {
                    addCount++;
                    i++;
                  }
                  attemptCount = 1;
                } else {
                  attemptCount++;
                  i++;
                }
              }
              if (attemptCount == TdConstants.MAX_MESSAGE_GROUP_SIZE) {
                addCount += attemptCount;
              }
              break;
            }
          }
          Log.i(Log.TAG_MESSAGES_LOADER, "Found %d good messages on top, totalCount: %d", addCount, mergingChunk.length + addCount);
          if (addCount > 0) {
            TdApi.Message[] resultChunk = new TdApi.Message[mergingChunk.length + addCount];
            System.arraycopy(mergingChunk, 0, resultChunk, 0, mergingChunk.length);
            System.arraycopy(messages, skipCount, resultChunk, mergingChunk.length, addCount);
            messages = resultChunk;
          } else {
            messages = mergingChunk;
          }
        } else if (mergingMode == MERGE_MODE_BOTTOM) {
          needMoreBottom = false;
          needMoreTop = false;

          long mediaGroupId = mergingChunk[0].mediaAlbumId;
          long messageId = mergingChunk[0].id;

          int skipCount = 0;
          int addCount = 0;

          for (int i = messages.length - 1; i >= 0; i--) {
            TdApi.Message message = messages[i];
            if (message.id <= messageId) {
              skipCount++;
            } else if (message.mediaAlbumId == mediaGroupId) {
              addCount++;
            } else if (message.mediaAlbumId == 0) {
              if (Config.ALLOW_MORE_CACHED_MESSAGES) {
                addCount++;
              } else {
                break;
              }
            } else {
              if (!Config.ALLOW_MORE_CACHED_MESSAGES) {
                break;
              }
              int attemptCount = 1;
              long attemptMediaId = message.mediaAlbumId;
              while (i - 1 >= 0) {
                TdApi.Message prevMessage = messages[i - 1];
                if (prevMessage.mediaAlbumId != attemptMediaId) {
                  addCount += attemptCount;
                  i--;
                  while (i >= 0 && (attemptMediaId = messages[i].mediaAlbumId) == 0) {
                    addCount++;
                    i--;
                  }
                  attemptCount = 1;
                } else {
                  attemptCount++;
                  i--;
                }
              }
              if (attemptCount == TdConstants.MAX_MESSAGE_GROUP_SIZE) {
                addCount += attemptCount;
              }
              break;
            }
          }

          Log.i(Log.TAG_MESSAGES_LOADER, "Found %d good messages on bottom, totalCount: %d", addCount, mergingChunk.length + addCount);
          if (addCount > 0) {
            TdApi.Message[] resultChunk = new TdApi.Message[mergingChunk.length + addCount];
            System.arraycopy(messages, messages.length - skipCount - addCount, resultChunk, 0, addCount);
            System.arraycopy(mergingChunk, 0, resultChunk, addCount, mergingChunk.length);
            messages = resultChunk;
          } else {
            messages = mergingChunk;
          }
        }

        List<List<TdApi.Message>> missingAlbums = null;

        if (hasSearchFilter()) {
          if (searchFilter instanceof TdApi.SearchMessagesFilterPinned) {
            // FIXME why the fuck server thinks it's client's job to fetch these messages
            for (int i = 0; i < messages.length; i++) {
              TdApi.Message message = messages[i];
              if (message.mediaAlbumId != 0) {
                int albumSize = 1;
                while (i + albumSize < messages.length && messages[i + albumSize].mediaAlbumId == message.mediaAlbumId) {
                  albumSize++;
                }

                int remainSize = TdConstants.MAX_MESSAGE_GROUP_SIZE - albumSize;
                if (remainSize > 0) {
                  List<TdApi.Message> albumMessages = new ArrayList<>(albumSize);
                  for (int index = 0; index < albumSize; index++) {
                    albumMessages.add(messages[i + index]);
                  }
                  if (missingAlbums == null) {
                    missingAlbums = new ArrayList<>();
                  }
                  missingAlbums.add(albumMessages);
                }
                i += albumSize - 1;
              }
            }
          }
        } else if (Config.NEED_MEDIA_GROUP_MERGE_REQUESTS && messages.length != 0 && (needMoreTop || needMoreBottom)) {
          // Let's check if we need to load anything more

          TdApi.Message oldestMessage = messages[messages.length - 1];
          TdApi.Message newestMessage = messages[0];

          int loadMoreTopCount = 0;
          int loadMoreBottomCount = 0;

          int availableMessageCount = messages.length;
          if (needMoreTop && oldestMessage.mediaAlbumId != 0) {
            loadMoreTopCount = TdConstants.MAX_MESSAGE_GROUP_SIZE - 1;
            long mediaGroupId = oldestMessage.mediaAlbumId;
            for (int i = messages.length - 2; i >= 0 && loadMoreTopCount > 0; i--) {
              if (messages[i].mediaAlbumId == mediaGroupId) {
                loadMoreTopCount--;
                availableMessageCount--;
              } else {
                break;
              }
            }
          }
          if (needMoreBottom && availableMessageCount > 0 && newestMessage.mediaAlbumId != 0) {
            loadMoreBottomCount = TdConstants.MAX_MESSAGE_GROUP_SIZE - 1;
            long mediaGroupId = newestMessage.mediaAlbumId;
            for (TdApi.Message message : messages) {
              if (message.mediaAlbumId == mediaGroupId) {
                loadMoreBottomCount--;
              } else {
                break;
              }
            }
          }

          if (loadMoreTopCount > 0) {
            mergeMode = MERGE_MODE_TOP;
            mergeChunk = messages;
            Log.i(Log.TAG_MESSAGES_LOADER, "Loading more groupped messages on the top, count: %d, fromMessageId: %d", loadMoreTopCount, oldestMessage.id);
            tdlib.client().send(new TdApi.GetChatHistory(messages[0].chatId, oldestMessage.id, 0, loadMoreTopCount, true), this);
            return;
          } else if (loadMoreBottomCount > 0) {
            mergeMode = MERGE_MODE_BOTTOM;
            mergeChunk = messages;
            Log.i(Log.TAG_MESSAGES_LOADER, "Loading more groupped messages on the bottom, count: %d, fromMessageId: %d", loadMoreBottomCount + 1, newestMessage.id);
            tdlib.client().send(new TdApi.GetChatHistory(messages[0].chatId, newestMessage.id, -loadMoreBottomCount, loadMoreBottomCount + 1, true), this);
            return;
          }
        }

        mergeMode = MERGE_MODE_NONE;
        mergeChunk = null;
        synchronized (lock) {
          lastHandler = null;
        }

        processMessages(currentContextId,
          messages, knownTotalCount, nextSearchOffset, nextSearchFromMessageId,
          needFindUnread && object.getConstructor() == TdApi.Messages.CONSTRUCTOR, missingAlbums);
      }
    };
  }

  public void reuse () {
    scrollMessageId = null;
    scrollHighlightMode = 0;

    knownTotalMessageCount = -1;

    foundUnreadAtLeastOnce = false;

    canLoadTop = false;
    canLoadBottom = false;

    if (contextId == Long.MAX_VALUE) {
      contextId = 0;
    }
    contextId++;

    mergeMode = MERGE_MODE_NONE;
    mergeChunk = null;

    if (sponsoredResultHandler != null) {
      sponsoredResultHandler.cancel();
    }

    synchronized (lock) {
      lastHandler = null;
      isLoading = false;
    }
  }

  public void loadPreviewMessages () {
    reuse();

    canLoadTop = false;
    canLoadBottom = false;
    isLoading = true;
    loadingMode = MODE_INITIAL;

    Background.instance().post(() -> {
      List<TdApi.Message> messages = new ArrayList<>();
      LongSparseArray<TdApi.User> participants = new LongSparseArray<>();
      boolean isGroupChat = fillPreviewMessages(manager.controller(), messages, participants);

      manager.setDemoParticipants(participants, isGroupChat);

      TdApi.Message[] messagesArray = new TdApi.Message[messages.size()];
      messages.toArray(messagesArray);
      processMessages(
        contextId, messagesArray,
        0, null, 0,
        true, null
      );
    });
  }

  private static class PreviewMessage {
    public final int date;
    public final int after;
    public final boolean out;
    public final int senderUserId;
    public final TdApi.MessageContent content;

    public PreviewMessage (int date, int after, boolean out, int senderUserId, TdApi.MessageContent content) {
      this.date = date;
      this.after = after;
      this.out = out;
      this.senderUserId = senderUserId;
      this.content = content;
    }
  }

  private static @StringRes int getPreviewStringKey (String key) {
    switch (key) {
      case "json_1_name": return R.string.json_1_name;
      case "json_1_audioTitle": return R.string.json_1_audioTitle;
      case "json_1_audioPerformer": return R.string.json_1_audioPerformer;
      case "json_1_text1": return R.string.json_1_text1;
      case "json_1_text2": return R.string.json_1_text2;
      case "json_1_text3": return R.string.json_1_text3;
      case "json_1_text4": return R.string.json_1_text4;
      case "json_2_name1": return R.string.json_2_name1;
      case "json_2_name2": return R.string.json_2_name2;
      case "json_2_name3": return R.string.json_2_name3;
      case "json_2_name4": return R.string.json_2_name4;
      case "json_2_title": return R.string.json_2_title;
      case "json_2_text1": return R.string.json_2_text1;
      case "json_2_text2": return R.string.json_2_text2;
      case "json_2_text3": return R.string.json_2_text3;
      case "json_2_text4": return R.string.json_2_text4;
      case "json_2_text5": return R.string.json_2_text5;

      case "json_3_name": return R.string.json_3_name;
      case "json_3_text1": return R.string.json_3_text1;
      case "json_3_text2": return R.string.json_3_text2;
      case "json_3_text3": return R.string.json_3_text3;
      case "json_3_text4": return R.string.json_3_text4;
      case "json_3_text5": return R.string.json_3_text5;
      case "json_3_text6": return R.string.json_3_text6;
      case "json_3_text7": return R.string.json_3_text7;

      case "json_4_name": return R.string.json_4_name;
      case "json_4_text1": return R.string.json_4_text1;
      case "json_4_text2": return R.string.json_4_text2;
    }
    return 0;
  }

  private static String parsePreviewString (String string, int lang) {
    if (lang != 0) {
      String key = "json_" + lang + "_" + string;
      int resId = getPreviewStringKey(key);
      if (resId != 0)
        return Lang.getString(resId);
      String result = Lang.getStringByKey(key);
      if (result == null)
        throw new IllegalArgumentException("Invalid variable: json_" + lang + "_" + string);
      return result;
    }
    return string;
  }

  private static TdApi.User parsePreviewUser (Tdlib tdlib, JSONArray data, int lang) throws JSONException {
    int dataArrayLength = data.length();
    int userId = data.getInt(0);
    TdApi.User user = TD.newFakeUser(userId, parsePreviewString(data.getString(1), lang), dataArrayLength > 2 ? parsePreviewString(data.getString(2), lang) : null);
    String remoteId = dataArrayLength > 3 ? data.getString(3) : null;
    if (!StringUtils.isEmpty(remoteId) && !Strings.isValidLink(remoteId)) {
      TdApi.File remoteFile = tdlib.getRemoteFile(remoteId, new TdApi.FileTypeProfilePhoto(), 0);
      if (remoteFile != null) {
        user.profilePhoto = new TdApi.ProfilePhoto(0, remoteFile, remoteFile, null, false, true);
      }
    }
    return user;
  }

  private static class SceneScore {
    public int score;
    public JSONObject data;

    public SceneScore (int score, JSONObject data) {
      this.score = score;
      this.data = data;
    }
  }

  private static boolean parsePreviewMessages (TdlibDelegate context, List<TdApi.Message> out, LongSparseArray<TdApi.User> participants, String json) throws Throwable {
    Tdlib tdlib = context.tdlib();
    boolean isGroupChat = false;
    final long myUserId = tdlib.myUserId();

    JSONObject chat = null;
    JSONArray chatsArray = new JSONArray(json.startsWith("[") && json.endsWith("]") ? json : "[" + json + "]");
    if (chatsArray.length() > 0) {
      int length = chatsArray.length();
      List<SceneScore> picked = null;
      List<JSONObject> available = null;
      for (int i = 0; i < length; i++) {
        chat = chatsArray.getJSONObject(i);
        if (chat.has("min_version") && BuildConfig.ORIGINAL_VERSION_CODE < chat.getInt("min_version"))
          continue;
        if (chat.has("max_version") && BuildConfig.ORIGINAL_VERSION_CODE > chat.getInt("max_version"))
          continue;
        boolean isTest = chat.has("guide") && chat.getInt("guide") == 1;
        if (isTest && !(BuildConfig.DEBUG || TD.isRawLanguage(Lang.packId())))
          continue;
        if (isTest && !Settings.instance().needTutorial(Settings.TUTORIAL_CHAT_DEMO_TRANSLATION))
          continue;
        if (chat.has("on_date")) {
          JSONArray date = chat.getJSONArray("on_date");
          int dateLength = date.length();
          int day = date.getInt(0);
          int month = dateLength > 1 ? date.getInt(1) : -1;
          int year = dateLength > 2 ? date.getInt(2) : -1;
          int hour = dateLength > 3 ? date.getInt(3) : -1;
          int minute = dateLength > 4 ? date.getInt(4) : -1;

          int score = 0;

          Calendar c = DateUtils.getNowCalendar();
          if ((day == -1 || (day == c.get(Calendar.DAY_OF_MONTH) && ++score > 0)) &&
            (month == -1 || (month == c.get(Calendar.MONTH) + 1 && ++score > 0)) &&
            (year == -1 || (year == c.get(Calendar.YEAR) && ++score > 0)) &&
            (hour == -1 || (hour == c.get(Calendar.HOUR_OF_DAY) && ++score > 0)) &&
            (minute == -1 || (minute == c.get(Calendar.MINUTE) && ++score > 0))) {
            if (picked == null)
              picked = new ArrayList<>();
            picked.add(new SceneScore(score, chat));
            break;
          }
          continue;
        }
        if (isTest) {
          available = null;
          if (picked == null)
            picked = new ArrayList<>();
          else
            picked.clear();
          picked.add(new SceneScore(0, chat));
          Settings.instance().markTutorialAsComplete(Settings.TUTORIAL_CHAT_DEMO_TRANSLATION);
          break;
        }
        if (available == null)
          available = new ArrayList<>(length);
        available.add(chat);
      }
      if (picked != null && !picked.isEmpty()) {
        if (picked.size() > 1) {
          Collections.sort(picked, (a, b) -> Integer.compare(b.score, a.score));
          if (picked.get(0).score == picked.get(1).score) {
            int count = 1;
            int score = picked.get(0).score;
            while (picked.get(count).score == score) {
              count++;
            }
            chat = picked.get(MathUtils.random(0, count)).data;
          } else {
            chat = picked.get(0).data;
          }
        } else {
          chat = picked.get(0).data;
        }
      } else if (available != null && !available.isEmpty()) {
        chat = available.get(MathUtils.random(0, available.size() - 1));
      } else {
        chat = null;
      }
    }

    if (chat == null)
      throw new JSONException("No chat available");

    int lang = chat.has("lang") ? chat.getInt("lang") : 0;
    if (chat.has("member")) {
      Object memberRaw = chat.get("member");
      if (memberRaw instanceof String) {
        participants.put(0, TD.newFakeUser(0, parsePreviewString((String) memberRaw, lang), null));
      } else if (memberRaw instanceof JSONArray) {
        TdApi.User user = parsePreviewUser(tdlib, (JSONArray) memberRaw, lang);
        participants.put(user.id, user);
      } else {
        throw new JSONException("Invalid member type: " + memberRaw);
      }
    } else if (chat.has("members")) {
      isGroupChat = true;
      JSONArray membersArray = chat.getJSONArray("members");
      int length = membersArray.length();
      for (int i = 0; i < length; i++) {
        JSONArray dataArray = membersArray.getJSONArray(i);
        TdApi.User user = parsePreviewUser(tdlib, dataArray, lang);
        participants.put(user.id, user);
      }
    } else {
      throw new IllegalStateException();
    }
    JSONArray messagesArray = chat.getJSONArray("_");
    int length = messagesArray.length();
    if (length <= 0 || length >= 1000)
      throw new JSONException("Invalid message count: " + length);

    int afterSum = 0;
    int lastKnownDate = 0;

    List<PreviewMessage> messages = new ArrayList<>();
    for (int i = 0; i < length; i++) {
      JSONObject data = messagesArray.getJSONObject(i);

      boolean isOut = false;
      int date = 0;
      int after = 60;
      int senderUserId = 0;
      TdApi.FormattedText text = null;
      TdApi.Photo photo = null;
      TdApi.Sticker sticker = null;
      TdApi.Audio audio = null;
      TdApi.VoiceNote voice = null;
      TdApi.MessageChatDeleteMember left = null;
      TdApi.MessageSupergroupChatCreate created = null;
      TdApi.MessageChatSetMessageAutoDeleteTime autoDeleteTime = null;

      if (data.has("out"))
        isOut = data.getInt("out") == 1;

      if (data.has("date"))
        date = data.getInt("date");

      if (data.has("after"))
        after = Math.max(0, data.getInt("after"));

      if (data.has("author"))
        senderUserId = data.getInt("author");

      if (data.has("text")) {
        String message; int flags;

        Object rawItem = data.get("text");
        if (rawItem instanceof String) {
          message = parsePreviewString((String) rawItem, lang);
          flags = 0;
        } else if (rawItem instanceof JSONArray) {
          JSONArray item = (JSONArray) rawItem;
          int itemLength = item.length();
          message = parsePreviewString(item.getString(0), lang);
          flags = itemLength > 1 ? item.getInt(1) : 0;
        } else {
          throw new JSONException("Invalid text value: " + rawItem);
        }
        boolean needMarkdown = (flags & 1) == 1;

        if (needMarkdown) {
          text = TD.newText(Strings.buildMarkdown(context, message, null));
          Td.addDefaultEntities(text);
        } else {
          text = new TdApi.FormattedText(message, Td.findEntities(message));
        }
      }

      if (data.has("audio")) {
        int duration; String title; String performer;

        JSONArray item = data.getJSONArray("audio");
        duration = item.getInt(0);
        title = parsePreviewString(item.getString(1), lang).trim();
        performer = parsePreviewString(item.getString(2), lang).trim();
        if (duration <= 0)
          throw new JSONException("audio.duration <= 0");
        if (title.isEmpty())
          throw new JSONException("audio.title is empty");
        if (performer.isEmpty())
          throw new JSONException("audio.performer is empty");
        audio = new TdApi.Audio(duration, title, performer, "audio.mp3", "audio/mp3", null, null, null, new TdApi.File());
      }

      if (data.has("voice")) {
        int duration;

        Object rawItem = data.get("voice");
        if (rawItem instanceof Integer) {
          duration = (Integer) rawItem;
        } else if (rawItem instanceof JSONArray) {
          JSONArray item = (JSONArray) rawItem;
          duration = item.getInt(0);
        } else {
          throw new JSONException("Invalid voice value: " + rawItem);
        }
        voice = new TdApi.VoiceNote(duration, TD.newRandomWaveform(), "audio/ogg", null, new TdApi.File());
      }

      if (data.has("photo")) {
        String query; int width, height;

        Object rawItem = data.get("photo");
        if (rawItem instanceof String) {
          query = (String) rawItem;
          width = height = 0;
        } else if (rawItem instanceof JSONArray) {
          JSONArray item = (JSONArray) rawItem;
          int itemLength = item.length();
          query = item.getString(0).trim();
          if (query.isEmpty())
            throw new JSONException("photo query is empty");
          width = itemLength > 2 ? item.getInt(1) : 0;
          height = itemLength > 2 ? item.getInt(2) : 0;
        } else {
          throw new JSONException("Invalid photo value: " + rawItem);
        }
        if (width <= 0 || height <= 0) {
          width = 640;
          height = 360;
        }
        photo = new TdApi.Photo(false, null, new TdApi.PhotoSize[] {new TdApi.PhotoSize(query, new TdApi.File(), width, height, null)});
      }

      if (data.has("sticker")) {
        // Blahaj {\"sticker\":[512,512,\"AAQCABPgWrYOAARorGGPXaunph-dAAIC\",\"CAADAgADuQcAAhhC7ghaNsLzTm3DpgI\",643524467607207965],\"out\":1}
        int width, height; String thumbFileId, fileId; long setId;

        JSONArray item = data.getJSONArray("sticker");
        width = item.getInt(0);
        height = item.getInt(1);
        thumbFileId = item.getString(2);
        fileId = item.getString(3);
        setId = item.length() > 4 ? item.getLong(4) : 0;

        if (Strings.isValidLink(thumbFileId) || Strings.isValidLink(fileId))
          throw new JSONException("Can't use HTTP links in stickers");
        TdApi.File thumbFile = tdlib.getRemoteFile(thumbFileId, new TdApi.FileTypeThumbnail(), 0);
        TdApi.File file = tdlib.getRemoteFile(fileId, new TdApi.FileTypeSticker(), 0);
        if (thumbFile == null || file == null)
          throw new JSONException("sticker.thumbFile == null || sticker.file == null");
        sticker = new TdApi.Sticker(
          file.id,
          setId,
          width,
          height,
          null,
          new TdApi.StickerFormatWebp(), new TdApi.StickerFullTypeRegular(),
          null,
          new TdApi.Thumbnail(new TdApi.ThumbnailFormatWebp(), width, height, thumbFile),
          file
        );
      }

      if (data.has("left")) {
        int userId;

        userId = data.getInt("left");
        left = new TdApi.MessageChatDeleteMember(userId);
        senderUserId = userId;
      }

      if (data.has("created")) {
        String title;

        title = parsePreviewString(data.getString("created"),lang);
        created = new TdApi.MessageSupergroupChatCreate(title);
      }

      if (data.has("ttl")) {
        int seconds;

        seconds = data.getInt("ttl");
        autoDeleteTime = new TdApi.MessageChatSetMessageAutoDeleteTime(seconds, 0);
      }

      if (date == 0) {
        if (lastKnownDate != 0)
          date = lastKnownDate = lastKnownDate + after;
        else
          afterSum += after;
      } else {
        lastKnownDate = date;
      }

      TdApi.MessageContent content;
      if (photo != null)
        content = new TdApi.MessagePhoto(photo, text, false, false);
      else if (sticker != null)
        content = new TdApi.MessageSticker(sticker, false);
      else if (audio != null)
        content = new TdApi.MessageAudio(audio, text);
      else if (voice != null)
        content = new TdApi.MessageVoiceNote(voice, text, isOut);
      else if (left != null)
        content = left;
      else if (created != null)
        content = created;
      else if (autoDeleteTime != null)
        content = autoDeleteTime;
      else if (text != null)
        content = new TdApi.MessageText(text, null);
      else
        throw new JSONException("Invalid message: " + data);
      messages.add(new PreviewMessage(date, after, isOut, senderUserId, content));
    }

    if (!messages.isEmpty()) {
      int maxDate = (int) (System.currentTimeMillis() / 1000l - 60l);
      int minDate = maxDate - afterSum;
      long maxId = 1000;
      int i = 0;
      for (int a = 0; a < messages.size(); a++) {
        PreviewMessage message = messages.get(a);
        TdApi.Message msg = new TdApi.Message();
        boolean isLast = a == messages.size() - 1;
        msg.id = maxId - messages.size() + i;
        msg.date = message.date != 0 ? message.date : (minDate = minDate + message.after);
        msg.isOutgoing = message.out;
        msg.senderId = new TdApi.MessageSenderUser(message.out ? myUserId : message.senderUserId);
        msg.content = message.content;
        if (isLast) {
          msg.interactionInfo = new TdApi.MessageInteractionInfo();
          msg.interactionInfo.reactions = new TdApi.MessageReaction[]{
            new TdApi.MessageReaction(new TdApi.ReactionTypeEmoji("\uD83D\uDC4D"), 5, true, new TdApi.MessageSender[0])
          };
        }
        out.add(msg);
        i++;
      }
      Collections.reverse(out);
      return isGroupChat;
    }

    return false;
  }

  private static boolean fillPreviewMessages (TdlibDelegate context, List<TdApi.Message> out, LongSparseArray<TdApi.User> participants) {
    String json = Lang.getString(R.string.json_ChatDemo);
    if (!StringUtils.isEmpty(json) && !json.equals("0")) {
      try {
        return parsePreviewMessages(context, out, participants, json);
      } catch (Throwable t) {
        Log.e("Cannot parse chat demo content", t);
        out.clear();
        participants.clear();
      }
    }
    json = "[{\"lang\":1,\"member\":\"name\",\"_\":[{\"sticker\":[512,512,\"AAQCABMktIENAAQ7jAMIc7zJ7Xd8AAIC\",\"CAADAgADQQIAAkf7CQw2YkvFWC1DowI\",867500685606780933]},{\"text\":[\"text1\",1]},{\"text\":[\"text2\",1],\"out\":1},{\"voice\":3,\"out\":1},{\"text\":[\"text3\",1]},{\"audio\":[243,\"audioTitle\",\"audioPerformer\"]},{\"text\":[\"text4\",1],\"out\":1}]},{\"lang\":2,\"members\":[[6,\"name1\"],[7,\"name2\"],[3,\"name3\"],[1,\"name4\"]],\"_\":[{\"created\":\"title\",\"out\":1},{\"text\":[\"text1\",1],\"author\":6},{\"text\":[\"text2\",1],\"out\":1},{\"text\":[\"text3\",1],\"author\":7},{\"text\":[\"text4\",1],\"author\":3},{\"left\":3},{\"text\":[\"text5\",1],\"author\":1},{\"sticker\":[467,512,\"AAQCABNfgVkqAAT-l09kzZivgi08AAIC\",\"CAADAgAD3gAD9HsZAAG9he9u98XOPQI\",7173162320003073],\"out\":1}]},{\"lang\":3,\"member\":[0,\"name\"],\"_\":[{\"sticker\":[512,512,\"AAQCABOzP0sNAATjTYV36dMVWNVCAQABAg\",\"CAADAgADfgUAAvoLtghVynd3kd-TuAI\"]},{\"text\":[\"text1\",1],\"out\":1},{\"text\":[\"text2\",1]},{\"text\":[\"text3\",1],\"out\":1},{\"text\":[\"text4\",1]},{\"text\":[\"text5\",1],\"out\":1},{\"text\":[\"text6\",1]},{\"ttl\":15},{\"text\":[\"text7\",1],\"out\":1}]},{\"lang\":4,\"member\":\"name\",\"_\":[{\"text\":[\"text1\",1]},{\"text\":[\"text2\",1],\"out\":1}],\"guide\":1}]";
    try {
      return parsePreviewMessages(context, out, participants, json);
    } catch (Throwable t) {
      Log.e(t);
      out.clear();
      participants.clear();
      return false;
    }
  }

  public void loadFromStart (MessageId startMessageId) {
    reuse();

    canLoadTop = true;
    canLoadBottom = false;
    scrollMessageId = startMessageId;
    scrollHighlightMode = MessagesManager.HIGHLIGHT_MODE_NONE;

    load(startMessageId, 0, CHUNK_SIZE_SMALL, MODE_INITIAL, true, true, true);
  }

  public void loadFromMessage (MessageId messageId, final int highlightMode, boolean force) {
    reuse();

    canLoadTop = canLoadBottom = force;
    scrollMessageId = messageId;
    scrollHighlightMode = highlightMode;

    load(messageId, CHUNK_SEARCH_OFFSET, CHUNK_SIZE_SEARCH, force ? MODE_INITIAL : MODE_REPEAT_INITIAL, false, true, true);
  }

  public boolean isLoading () {
    return isLoading;
  }

  private long lastRequestTime;

  private boolean loadingLocal, loadingAllowMoreTop, loadingAllowMoreBottom;

  private MessageId lastFromMessageId;
  private String lastSearchNextOffset;
  private long lastSearchNextFromMessageId;
  private int lastOffset, lastLimit;
  private boolean foundUnreadAtLeastOnce;
  private final Object lock = new Object();

  private boolean hasSearchFilter () {
    return searchFilter != null;
  }

  private void load (final MessageId fromMessageId, final int offset, final int limit, int mode, boolean onlyLocal, boolean allowMoreTop, boolean allowMoreBottom) {
    synchronized (lock) {
      if (isLoading)
        return;
      if (specialMode == SPECIAL_MODE_RESTRICTED) {
        canLoadTop = canLoadBottom = false;
        UI.post(() -> {
          manager.onNetworkRequestSent();
          manager.displayMessages(new ArrayList<>(), mode, 0, null, null, MessagesManager.HIGHLIGHT_MODE_NONE, false, false);
        });
        return;
      }

      final long sourceChatId = fromMessageId.getChatId() != 0 ? fromMessageId.getChatId() : messageThread != null ? messageThread.getChatId() : getChatId();

      isLoading = true;

      loadingMode = mode;
      loadingLocal = onlyLocal;
      loadingAllowMoreTop = allowMoreTop;
      loadingAllowMoreBottom = allowMoreBottom;

      TdApi.Function<?> function;

      switch (specialMode) {
        case SPECIAL_MODE_EVENT_LOG:
          function = new TdApi.GetChatEventLog(sourceChatId, manager.getEventLogQuery(), (lastFromMessageId = fromMessageId).getMessageId(), lastLimit = limit, manager.getEventLogFilters(), manager.getEventLogUserIds());
          break;
        case SPECIAL_MODE_SEARCH: {
          long chatId = getChatId();
          if (ChatId.isSecret(chatId)) {
            function = new TdApi.SearchSecretMessages(sourceChatId, searchQuery, lastSearchNextOffset, limit, searchFilter);
          } else {
            function = new TdApi.SearchChatMessages(sourceChatId, searchQuery, searchSender, (lastFromMessageId = fromMessageId).getMessageId(), lastOffset = offset, lastLimit = limit, searchFilter, messageThread != null ? messageThread.getMessageThreadId() : 0);
          }
          break;
        }
        case SPECIAL_MODE_SCHEDULED:
          loadingAllowMoreBottom = loadingAllowMoreTop = loadingLocal = allowMoreBottom = allowMoreTop = onlyLocal = false;
          function = new TdApi.GetChatScheduledMessages(sourceChatId);
          break;
        default:
          if (hasSearchFilter()) {
            loadingLocal = false;
            function = new TdApi.SearchChatMessages(sourceChatId, null, null, (lastFromMessageId = fromMessageId).getMessageId(), lastOffset = offset, lastLimit = limit, searchFilter, messageThread != null ? messageThread.getMessageThreadId() : 0);
          } else if (messageThread != null) {
            loadingLocal = false;
            function = new TdApi.GetMessageThreadHistory(sourceChatId, messageThread.getOldestMessageId(), (lastFromMessageId = fromMessageId).getMessageId(), lastOffset = offset, lastLimit = limit);
          } else {
            function = new TdApi.GetChatHistory(sourceChatId, (lastFromMessageId = fromMessageId).getMessageId(), lastOffset = offset, lastLimit = limit, loadingLocal);
          }
          break;
      }

      if (Log.isEnabled(Log.TAG_MESSAGES_LOADER)) {
        lastRequestTime = SystemClock.elapsedRealtime();
        Log.i(Log.TAG_MESSAGES_LOADER, "allowMoreTop:%b, allowMoreBottom:%b. Invoking %s, onlyLocal:%b", allowMoreTop, allowMoreBottom, function, loadingLocal);
      }

      Client.ResultHandler handler = newHandler(allowMoreTop, allowMoreBottom, (mode != MODE_MORE_TOP && mode != MODE_MORE_BOTTOM) || !foundUnreadAtLeastOnce);
      //noinspection SwitchIntDef
      switch (function.getConstructor()) {
        case TdApi.SearchSecretMessages.CONSTRUCTOR: {
          searchManagerMiddleware.search((TdApi.SearchSecretMessages) function, searchSender, handler);
          break;
        }
        case TdApi.SearchChatMessages.CONSTRUCTOR: {
          searchManagerMiddleware.search((TdApi.SearchChatMessages) function, handler);
          break;
        }
        default: {
          tdlib.client().send(function, handler);
          break;
        }
      }
    }
  }

  public boolean loadMoreInAnyDirection () {
    return loadMore(canLoadTop());
  }

  public boolean loadMore (boolean fromTop) {
    return loadMore(fromTop, fromTop ? CHUNK_SIZE_BIG : CHUNK_SIZE_BOTTOM, false);
  }

  private boolean loadMore (boolean fromTop, int count, boolean onlyLocal) {
    if (isLoading || getChatId() == 0) {
      return false;
    }
    if (fromTop) {
      final MessageId startTop = getStartTop();
      if (canLoadTop() && startTop != null) {
        if (Log.isEnabled(Log.TAG_MESSAGES_LOADER)) {
          Log.i(Log.TAG_MESSAGES_LOADER, "Loading more messages on top.");
        }
        load(startTop, 0, count, MODE_MORE_TOP, onlyLocal, true, false);
        return true;
      }
    } else {
      final MessageId startBottom = getStartBottom();
      if (canLoadBottom() && startBottom != null) {
        if (Log.isEnabled(Log.TAG_MESSAGES_LOADER)) {
          Log.i(Log.TAG_MESSAGES_LOADER, "Loading more messages on bottom.");
        }

        // Parameter limit must be greater than -offset
        // -offset
        load(startBottom, CHUNK_BOTTOM_OFFSET, count, MODE_MORE_BOTTOM, onlyLocal, false, true);
        return true;
      }
    }
    return false;
  }

  // Processing

  private TdApi.Message newMessage (final long chatId, final boolean isChannel, final TdApi.ChatEvent event) {
    return new TdApi.Message(
      event.id,
      event.memberId,
      chatId,
      null,
      null,
      tdlib.isSelfSender(event.memberId),
      false, false,
      false, false,
      false, false, false,
      false, false, false,
      false, false, false,
      isChannel, false,
      false,
      event.date, 0,
      null, null, null,
      0, 0, 0,
      0, 0, 0,
      0, null,
      0,
      null,
      null,
      null
    );
  }

  private TdApi.Message[] parseChatEvents (final long chatId, final TdApi.ChatEvent[] events) {
    final ArrayList<TdApi.Message> out = new ArrayList<>(events.length);

    final boolean isChannel = tdlib.isChannel(chatId);

    for (TdApi.ChatEvent event : events) {
      TdApi.Message m;
      switch (ChatEventUtil.getActionMessageMode(event.action)) {
        case ChatEventUtil.ActionMessageMode.ONLY_FULL: {
          // Only full message
          m = newMessage(chatId, isChannel, event);
          m.content = new TdApiExt.MessageChatEvent(event, true, false);
          break;
        }
        case ChatEventUtil.ActionMessageMode.ONLY_SERVICE: {
          // Only service message
          m = newMessage(chatId, isChannel, event);
          m.content = new TdApiExt.MessageChatEvent(event, false, false);
          break;
        }
        case ChatEventUtil.ActionMessageMode.SERVICE_AND_FULL: {
          // Service + full message

          m = newMessage(chatId, isChannel, event);
          m.content = new TdApiExt.MessageChatEvent(event, true, true);
          out.add(m);

          m = newMessage(chatId, isChannel, event);
          m.content = new TdApiExt.MessageChatEvent(event, false, false);
          break;
        }
        default:
          throw new UnsupportedOperationException(event.action.toString());
      }
      out.add(m);
    }
    TdApi.Message[] array = new TdApi.Message[out.size()];
    out.toArray(array);
    return array;
  }

  private long measuredStartTime;
  private long measuredTotalMs;
  private int stepsCount;

  private void startMeasureStep () {
    measuredStartTime = SystemClock.uptimeMillis();
  }

  private void endMeasureStep (TGMessage result, long messageId, int size) {
    long ms = SystemClock.uptimeMillis() - measuredStartTime;
    Log.i(Log.TAG_MESSAGES_LOADER, "message_id=%d (size: %d) took %dms (%s)", messageId, size, ms, result != null ? result.getClass().getName() : "combination");
    measuredTotalMs += ms;
    stepsCount++;
  }

  private void completeMeasure () {
    Log.i(Log.TAG_MESSAGES_LOADER, "processed %d steps in %dms (average %dms per step)", stepsCount, measuredTotalMs, stepsCount == 0 ? -1 : (measuredTotalMs / stepsCount));
    measuredTotalMs = 0;
    stepsCount = 0;
  }

  private void processMessages (final long currentContextId, TdApi.Message[] messages, int knownTotalMessageCount,
                                String nextSearchOffset, long nextSearchFromMessageId,
                                boolean needFindUnread, @Nullable List<List<TdApi.Message>> missingAlbums) {
    if (Log.isEnabled(Log.TAG_MESSAGES_LOADER)) {
      Log.v(Log.TAG_MESSAGES_LOADER, "Processing %d messages...", messages.length);
    }

    final ArrayList<TGMessage> items = new ArrayList<>(messages.length);

    final long chatId, lastReadOutboxMessageId, lastReadInboxMessageId;
    final boolean hasUnreadMessages;
    final LongSparseArray<TdApi.ChatAdministrator> chatAdmins = manager.getChatAdmins();
    if (messageThread != null) {
      chatId = messageThread.getChatId();
      lastReadOutboxMessageId = messageThread.getLastReadOutboxMessageId();
      lastReadInboxMessageId = messageThread.getLastReadInboxMessageId();
      hasUnreadMessages = messageThread.hasUnreadMessages(chat);
    } else if (chat != null) {
      chatId = chat.id;
      lastReadOutboxMessageId = chat.lastReadOutboxMessageId;
      lastReadInboxMessageId = chat.lastReadInboxMessageId;
      hasUnreadMessages = chat.unreadCount > 0;
    } else {
      chatId = lastReadInboxMessageId = lastReadOutboxMessageId = 0;
      hasUnreadMessages = false;
    }

    long id = 0;

    TGMessage cur = null, top = null;

    boolean lookForInbox = false;

    boolean isStub = chat == null;
    boolean isSupergroup = chat != null && TD.isSupergroup(chat.type);
    boolean isChannel = chat != null && !isSupergroup && TD.isChannel(chat.type);

    int scrollItemIndex = -1;
    TGMessage scrollItem = null;

    final boolean needMeasureSpeed = Log.isEnabled(Log.TAG_MESSAGES_LOADER) && Log.checkLogLevel(Log.LEVEL_INFO);

    final TGMessage bottomMessage = manager.getAdapter().getBottomMessage();
    final long startBottom = bottomMessage != null ? bottomMessage.getBiggestId() : 0;
    final TGMessage topMessage = manager.getAdapter().getTopMessage();
    final long startTop = topMessage != null ? topMessage.getSmallestId() : 0;

    long adminUserId = 0;
    TdApi.ChatAdministrator administrator = null;

    int minIndex = 0;
    int maxIndex = messages.length - 1;

    final List<TdApi.Message> combineWithMessages = new ArrayList<>();

    if (needMeasureSpeed) {
      startMeasureStep();
    }
    if (messages.length > 0) {
      switch (loadingMode) {
        case MODE_MORE_BOTTOM: {
          while (maxIndex >= 0) {
            if (messages[maxIndex].id > startBottom) {
              if (bottomMessage == null || !bottomMessage.wouldCombineWith(messages[maxIndex]))
                break;
              combineWithMessages.add(messages[maxIndex]);
            }
            maxIndex--;
          }
          break;
        }
        case MODE_MORE_TOP: {
          while (minIndex < messages.length) {
            if (messages[minIndex].id < startTop) {
              if (topMessage == null || !topMessage.wouldCombineWith(messages[minIndex]))
                break;
              combineWithMessages.add(messages[minIndex]);
            }
            minIndex++;
          }
          break;
        }
      }
    }
    if (needMeasureSpeed) {
      endMeasureStep(null, 0, 0);
    }

    if (!combineWithMessages.isEmpty()) {
      final boolean bottom = loadingMode == MODE_MORE_BOTTOM;
      UI.post(() -> {
        for (TdApi.Message message : combineWithMessages) {
          if (bottom) {
            bottomMessage.combineWith(message, true);
          } else {
            topMessage.combineWith(message, false);
          }
        }
      });
    }

    boolean unreadFound = !needFindUnread;
    TGMessage unreadBadged = null;

    for (int j = maxIndex; j >= minIndex; j--) {
      if (needMeasureSpeed) {
        startMeasureStep();
      }
      boolean containsScrollingMessage = false;
      try {
        if (chatAdmins != null) {
          if (adminUserId != Td.getSenderUserId(messages[j])) {
            adminUserId = Td.getSenderUserId(messages[j]);
            administrator = chatAdmins.get(adminUserId);
          }
        }
        cur = TGMessage.valueOf(manager, messages[j], chat, messageThread, administrator);
        if (cur != null) {
          if (!containsScrollingMessage && scrollMessageId != null && scrollMessageId.compareTo(messages[j].chatId, messages[j].id)) {
            containsScrollingMessage = true;
          }
          if (j > minIndex) {
            while (j > minIndex && cur.combineWith(messages[j - 1], true)) {
              if (!containsScrollingMessage && scrollMessageId != null && scrollMessageId.compareTo(messages[j - 1].chatId, messages[j - 1].id)) {
                containsScrollingMessage = true;
              }
              j--;
            }
          }
        }
      } catch (Throwable t) {
        Log.critical("Couldn't parse message", t);
        if (needMeasureSpeed) {
          endMeasureStep(null, 0, -1);
        }
        continue;
      }

      if (!isChannel) {
        if (cur.isOutgoing()) {
          if (!isStub) {
            cur.setUnread(lastReadOutboxMessageId);
          }
        } else if (!isStub) {
          cur.setUnread(lastReadInboxMessageId);
        }
      }

      if (hasUnreadMessages) {
        if (lookForInbox) {
          if (!cur.isOutgoing()) {
            lookForInbox = false;
            cur.setShowUnreadBadge(true);
            unreadBadged = cur;
          }
        } else if (!unreadFound) {
          if (top != null && top.getBiggestId() >= lastReadInboxMessageId ||
              (messageThread != null && cur.getBiggestId() > lastReadInboxMessageId)) {
            unreadFound = true;
            if (cur.isOutgoing()) {
              lookForInbox = true;
            } else {
              cur.setShowUnreadBadge(true);
              unreadBadged = cur;
            }
          }
        }
      }

      if (id == 0l) {
        id = cur.getChatId();
      }

      cur.mergeWith(top, j == minIndex);
      cur.prepareLayout();

      items.add(0, cur);

      if (containsScrollingMessage) {
        scrollItemIndex = items.size();
        scrollItem = cur;
      }

      top = cur;

      if (needMeasureSpeed) {
        endMeasureStep(cur, cur.getId(), cur.getMessageCount());
      }
    }

    if (needMeasureSpeed) {
      completeMeasure();
    }

    if (unreadFound && lookForInbox) {
      unreadFound = false;
    }


    if (scrollItemIndex == -1 && scrollMessageId != null && (scrollHighlightMode == MessagesManager.HIGHLIGHT_MODE_NORMAL || scrollHighlightMode == MessagesManager.HIGHLIGHT_MODE_NORMAL_NEXT) && specialMode == SPECIAL_MODE_SEARCH) {
      TGMessage highlightItem = null;
      long minDistance = -1;
      for (TGMessage item: items) {
        long distance = (item.getId() - scrollMessageId.getMessageId());
        if (distance >= 0 && (minDistance == -1 || distance < minDistance)) {
          minDistance = distance;
          highlightItem = item;
        }
      }
      if (highlightItem != null) {
        int i = items.indexOf(highlightItem);
        if (i != -1) {
          scrollMessageId = highlightItem.toMessageId();
          scrollItemIndex = items.size() - 1 - i;
          scrollItem = highlightItem;
        }
      }
    }

    if (scrollItemIndex == -1 && (scrollHighlightMode == MessagesManager.HIGHLIGHT_MODE_NORMAL || scrollHighlightMode == MessagesManager.HIGHLIGHT_MODE_NORMAL_NEXT) && unreadBadged != null) {
      scrollItem = unreadBadged;
      int i = items.indexOf(unreadBadged);
      if (i == -1)
        throw new AssertionError();
      scrollItemIndex = items.size() - i;
    }

    int totalCount = items.size();

    if (scrollItemIndex != -1) {
      scrollItemIndex = items.size() - scrollItemIndex;
      if (scrollHighlightMode == MessagesManager.HIGHLIGHT_MODE_NORMAL_NEXT && scrollItemIndex > 0) {
        scrollItemIndex--;
        scrollItem = items.get(scrollItemIndex);
      }
      switch (scrollHighlightMode) {
        case MessagesManager.HIGHLIGHT_MODE_NONE: {
          break;
        }
        case MessagesManager.HIGHLIGHT_MODE_POSITION_RESTORE: {
          if (Log.isEnabled(Log.TAG_MESSAGES_LOADER)) {
            Log.i(Log.TAG_MESSAGES_LOADER, "Restoring scroll position for message: %s", scrollMessageId);
          }
          break;
        }
        case MessagesManager.HIGHLIGHT_MODE_NORMAL:
        case MessagesManager.HIGHLIGHT_MODE_NORMAL_NEXT: {
          scrollItem.highlight(false);
          break;
        }
        case MessagesManager.HIGHLIGHT_MODE_UNREAD:
        case MessagesManager.HIGHLIGHT_MODE_UNREAD_NEXT: {
          if (scrollItemIndex > 0) {
            scrollItemIndex--;
          }
          scrollItem = items.get(scrollItemIndex);
          break;
        }
      }
    }

    final boolean couldLoadTop = canLoadTop;
    final boolean couldLoadBottom = canLoadBottom;

    switch (loadingMode) {
      case MODE_INITIAL:
      case MODE_REPEAT_INITIAL: {
        TGMessage suitableMessage = null;

        if (!items.isEmpty()) {
          for (TGMessage message : items) {
            if (!message.isSponsored()) {
              suitableMessage = message;
              break;
            }
          }
        }

        final boolean canLoadMore = loadingLocal || (totalCount != 0 && getChatId() != 0);
        canLoadTop = specialMode != SPECIAL_MODE_SCHEDULED && (scrollMessageId == null || !scrollMessageId.isHistoryStart()) && canLoadMore && !isLoadingFromThreadStart(scrollMessageId);
        canLoadBottom = specialMode != SPECIAL_MODE_SCHEDULED && (scrollMessageId == null || !scrollMessageId.isHistoryEnd()) && canLoadMore && suitableMessage != null && !isEndReached(new MessageId(suitableMessage.getChatId(), suitableMessage.getId()));

        if (Log.isEnabled(Log.TAG_MESSAGES_LOADER)) {
          Log.i(Log.TAG_MESSAGES_LOADER, "Received initial chunk, startTop:%s startBottom:%s canLoadTop:%b canLoadBottom:%b", getStartTop(), getStartBottom(), canLoadTop, canLoadBottom);
        }
        break;
      }
      case MODE_MORE_BOTTOM: {
        if (!loadingLocal && (totalCount == 0 || totalCount == 1)) {
          canLoadBottom = false;
          if (Log.isEnabled(Log.TAG_MESSAGES_LOADER)) {
            Log.i(Log.TAG_MESSAGES_LOADER, "Bottom end reached.");
          }
          UI.post(() -> {
            synchronized (lock) {
              isLoading = false;
            }
            manager.onBottomEndLoaded();
            manager.onBottomEndChecked();
          });
          return;
        }

        if (messages.length > 0) {
          long lastMessageId = messageThread != null ? messageThread.getLastMessageId() : chat.lastMessage != null ? chat.lastMessage.id : 0;
          if (lastMessageId != 0 && lastMessageId == messages[0].id) {
            UI.post(manager::onBottomEndChecked);
          }
        }

        if (Log.isEnabled(Log.TAG_MESSAGES_LOADER)) {
          Log.i(Log.TAG_MESSAGES_LOADER, "Received more bottom messages, new startBottom:%s canLoadTop:%b canLoadBottom:%b", getStartBottom(), canLoadTop, canLoadBottom);
        }
        break;
      }
      case MODE_MORE_TOP: {
        if (totalCount == 0 && !loadingLocal) {
          canLoadTop = false;
          if (Log.isEnabled(Log.TAG_MESSAGES_LOADER)) {
            Log.i(Log.TAG_MESSAGES_LOADER, "Top end reached.");
          }
          UI.post(() -> {
            synchronized (lock) {
              isLoading = false;
            }
            manager.onTopEndLoaded();
            if (!canLoadBottom) {
              manager.onBottomEndChecked();
            }
          });
          return;
        }

        if (Log.isEnabled(Log.TAG_MESSAGES_LOADER)) {
          Log.i(Log.TAG_MESSAGES_LOADER, "Received more top messages, new startTop:%s canLoadTop:%b canLoadBottom:%b", getStartTop(), canLoadTop, canLoadBottom);
        }
        break;
      }
    }

    final int scrollPosition = scrollItemIndex == -1 ? 0 : scrollItemIndex;
    final MessageId scrollMessageId;
    if (unreadBadged != null && !isLoadingFromThreadStart(this.scrollMessageId)) {
      scrollMessageId = new MessageId(unreadBadged.getChatId(), unreadBadged.getSmallestId());
    } else {
      scrollMessageId = this.scrollMessageId;
    }
    final int scrollItemIndexFinal = scrollItemIndex;
    final TGMessage scrollItemView = scrollItem;
    boolean unreadFoundFinal = unreadFound;

    UI.post(() -> {
      if (contextId != currentContextId || getChatId() != chatId) {
        return;
      }

      setKnownTotalMessageCount(knownTotalMessageCount);
      lastSearchNextOffset = nextSearchOffset;
      lastSearchNextFromMessageId = nextSearchFromMessageId;

      foundUnreadAtLeastOnce = foundUnreadAtLeastOnce || unreadFoundFinal;

      if (missingAlbums != null) {
        for (List<TdApi.Message> missingAlbum : missingAlbums) {
          fetchAlbum(missingAlbum);
        }
      }

      if (loadingLocal) {
        if (items.isEmpty()) {
          manager.onNetworkRequestSent();
          synchronized (lock) {
            isLoading = false;
          }
          load(lastFromMessageId, lastOffset, lastLimit, loadingMode, false, loadingAllowMoreTop, loadingAllowMoreBottom);
          return;
        }
      }

      final int chunkSize = scrollItemIndexFinal == -1 ? CHUNK_SIZE_SMALL : CHUNK_SIZE_SEARCH;
      boolean willTryAgain = (loadingMode == MODE_INITIAL || loadingMode == MODE_REPEAT_INITIAL) && items.size() < chunkSize && items.size() > 0;
      manager.displayMessages(items, loadingMode, scrollPosition, scrollItemView, scrollMessageId, scrollHighlightMode, willTryAgain && loadingLocal, canLoadTop);

      synchronized (lock) {
        isLoading = false;
      }

      boolean ignoreEndCheck = false;

      if (loadingMode == MODE_INITIAL || loadingMode == MODE_REPEAT_INITIAL) {
        int count = items.size();
        if (count > 0 && count < chunkSize) {
          if (Log.isEnabled(Log.TAG_MESSAGES_LOADER)) {
            Log.i(Log.TAG_MESSAGES_LOADER, "Loading more messages, because we received too few messages");
          }
          ignoreEndCheck = true;
          loadMore(true, chunkSize - count, willTryAgain && loadingLocal);
        }
      }

      if (canLoadTop != couldLoadTop && !canLoadTop) {
        manager.onTopEndLoaded();
      }
      if (canLoadBottom != couldLoadBottom && !canLoadBottom) {
        manager.onBottomEndLoaded();
      }
      if (!canLoadBottom && !ignoreEndCheck) {
        manager.onBottomEndChecked();
      }
      manager.ensureContentHeight();
    });
  }

  private void fetchAlbum (List<TdApi.Message> album) {
    tdlib.getAlbum(album, true, null, localAlbum -> {
      if (localAlbum.messages.size() != album.size()) {
        tdlib.ui().post(() ->
          manager.updateAlbum(album, localAlbum)
        );
        // TODO update album
      }
      if (localAlbum.mayHaveMoreItems()) {
        tdlib.getAlbum(localAlbum.messages, false, localAlbum, remoteAlbum -> {
          if (remoteAlbum.messages.size() != localAlbum.messages.size()) {
            // TODO update album

          }
        });
      }
    });
  }

  public boolean isSecretChat () {
    return chat != null && chat.type.getConstructor() == TdApi.ChatTypeSecret.CONSTRUCTOR;
  }

  public int getKnownTotalMessageCount () {
    return knownTotalMessageCount;
  }

  private void setKnownTotalMessageCount (int knownTotalMessageCount) {
    if (this.knownTotalMessageCount != knownTotalMessageCount) {
      this.knownTotalMessageCount = knownTotalMessageCount;
      manager.controller().onKnownMessageCountChanged(getChatId(), knownTotalMessageCount);
    }
  }

  public boolean isChannel () {
    return tdlib.isChannelChat(chat);
  }

  @Override
  public void onResult (TdApi.Object object) {
    if (object.getConstructor() != TdApi.Ok.CONSTRUCTOR) {
      UI.showToast("Weird viewMessage response: " + object.toString(), Toast.LENGTH_LONG);
    }
  }

  public boolean canLoadTop () {
    return canLoadTop;
  }

  @Nullable
  private MessageId getStartBottom () {
    TGMessage msg = manager.getAdapter().getBottomMessage();
    return msg != null ? new MessageId(msg.getChatId(), msg.getBiggestId()) : null;
  }

  @Nullable
  private MessageId getStartTop () {
    TGMessage msg = manager.getAdapter().getTopMessage();
    return msg != null ? new MessageId(msg.getChatId(), msg.getSmallestId()) : null;
  }

  private boolean isLoadingFromThreadStart (MessageId messageId) {
    if (messageId != null && messageThread != null) {
      return messageId.isHistoryStart() ||
        messageThread.getOldestMessageId() <= messageId.getMessageId() &&
        messageThread.getNewestMessageId() >= messageId.getMessageId();
    }
    return false;
  }

  private boolean isEndReached () {
    return isEndReached(getStartBottom());
  }

  private boolean isEndReached (MessageId messageId) {
    if (specialMode == SPECIAL_MODE_RESTRICTED)
      return true;
    if (messageId != null) {
      if (specialMode == SPECIAL_MODE_SCHEDULED)
        return true;
      if (getChatId() == messageId.getChatId()) {
        if (messageThread != null) {
          if (messageId.getMessageId() >= messageThread.getLastMessageId()) {
            return true;
          }
        } else if (chat != null) {
          if (chat.lastMessage != null && messageId.getMessageId() >= chat.lastMessage.id) {
            return true;
          }
        }
        if (searchFilter != null && searchFilter.getConstructor() == TdApi.SearchMessagesFilterPinned.CONSTRUCTOR && manager.maxPinnedMessageId() != 0 && messageId.getMessageId() >= manager.maxPinnedMessageId()) {
          return true;
        }
      }
    }
    return false;
  }

  public void setCanLoadBottom () {
    canLoadBottom = true;
  }

  public boolean canLoadBottom () {
    if (canLoadBottom) {
      if (isEndReached()) {
        canLoadBottom = false;
        manager.onBottomEndLoaded();
        return false;
      }
      return true;
    }
    return false;
  }
}
