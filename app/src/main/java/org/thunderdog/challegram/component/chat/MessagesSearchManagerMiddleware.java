package org.thunderdog.challegram.component.chat;

import android.annotation.SuppressLint;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.collection.LongSparseArray;

import org.drinkless.td.libcore.telegram.Client;
import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibThread;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.ui.MessagesController;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Objects;

import me.vkryl.core.StringUtils;
import me.vkryl.td.Td;

public class MessagesSearchManagerMiddleware {
  public static final int FILTER_NONE = 1;
  public static final int FILTER_BY_USER = 2;
  public static final int FILTER_BY_TYPE = 3;

  private final Tdlib tdlib;
  private String currentContextId = null;

  public MessagesSearchManagerMiddleware (Tdlib tdlib) {
    this.tdlib = tdlib;
  }

  // Middleware for Chats

  private final LongSparseArray<TdApi.Message> currentSearchResultsArr = new LongSparseArray<>();
  private final ArrayList<SendSearchRequestFilterChunkInfo> filteredChunksInfo = new ArrayList<>();
  private int totalCount = -1;
  private int discardedCount = 0;
  private BaseSearchResultManager searchResultManager;

  @UiThread
  public void search (TdApi.SearchChatMessages query, Client.ResultHandler resultHandler) {
    final String contextId = makeContextId(query);
    if (checkContextId(contextId)) {
      this.searchResultManager = new BaseSearchResultManager(tdlib, query);
    }

    sendSearchRequest(contextId, query, resultHandler);
  }

  @UiThread
  private void sendSearchRequest (String contextId, TdApi.SearchChatMessages query, Client.ResultHandler resultHandler) {
    final boolean needSendUserFilterToServer = query.senderId != null && !tdlib.isUserChat(query.chatId);
    final boolean needSendTypeFilterToServer = query.filter != null && !isFilterPolyfill(query.filter);
    final boolean needSendDoubleRequestToServer = needSendTypeFilterToServer && needSendUserFilterToServer;
    final boolean needLocalFilterByUser = query.senderId != null && tdlib.isUserChat(query.chatId);
    final boolean needLocalFilterByType = isFilterPolyfill(query.filter);
    final boolean isBottomChunkRequest = query.offset < 0 && (1 - query.offset == query.limit);
    final boolean isTopChunkRequest = query.offset == 0;
    final boolean needUseLocalFilter = needLocalFilterByType || needLocalFilterByUser;

    if (!needSendDoubleRequestToServer && !needUseLocalFilter) {
      tdlib.client().send(query, resultHandler);
      return;
    }

    if (!isTopChunkRequest && !isBottomChunkRequest) {
      Client.ResultHandler[] handlers = new Client.ResultHandler[2];
      waitMany(handlers, (o) -> onChunkedRequestResult(resultHandler, o[0], o[1]));
      search(cloneSearchChatQuery(query, query.fromMessageId, 1 - query.limit), handlers[0]);
      search(cloneSearchChatQuery(query, query.fromMessageId, 0), handlers[1]);
      return;
    }

    SendSearchRequestArguments args = new SendSearchRequestArguments(
      contextId, query, resultHandler, searchResultManager,
      isTopChunkRequest ? SEARCH_DIRECTION_TOP : SEARCH_DIRECTION_BOTTOM, 5);
    sendSearchRequest(args);
  }

  private static void onChunkedRequestResult (Client.ResultHandler handler, TdApi.Object newestMessagesObj, TdApi.Object oldestMessagesObj) {
    if (newestMessagesObj.getConstructor() == TdApi.Error.CONSTRUCTOR) {
      handler.onResult(newestMessagesObj);
      return;
    }
    if (oldestMessagesObj.getConstructor() == TdApi.Error.CONSTRUCTOR) {
      handler.onResult(oldestMessagesObj);
      return;
    }
    if (newestMessagesObj.getConstructor() != TdApi.Messages.CONSTRUCTOR) return;
    if (oldestMessagesObj.getConstructor() != TdApi.Messages.CONSTRUCTOR) return;

    TdApi.Messages newestMessages = (TdApi.Messages) newestMessagesObj;
    TdApi.Messages oldestMessages = (TdApi.Messages) oldestMessagesObj;

    final int totalCount = Math.min(newestMessages.totalCount, oldestMessages.totalCount);
    TdApi.Message[] newestMessageArr = newestMessages.messages != null ? newestMessages.messages : new TdApi.Message[0];
    TdApi.Message[] oldestMessageArr = oldestMessages.messages != null ? oldestMessages.messages : new TdApi.Message[0];

    handler.onResult(new TdApi.Messages(totalCount, mergeMessageArrays(newestMessageArr, oldestMessageArr)));
  }

  @SuppressLint("DefaultLocale")
  private static String makeContextId (TdApi.SearchChatMessages query) {
    return String.format("chat_%d_%d_%d_%d_%s", query.chatId, Td.getSenderId(query.senderId), query.filter != null ? query.filter.getConstructor() : 0, query.messageThreadId, query.query);
  }

  public static class BaseSearchResultManager implements SendSearchRequestManager {
    private final boolean needLocalFilterByUser;
    private final boolean needLocalFilterByType;
    private final boolean needSendDoubleRequest;
    private final TdApi.MessageSender senderFilter;
    private final TdApi.SearchMessagesFilter typeFilter;
    private final Tdlib tdlib;

    private boolean doubleRequestWithoutSenderId = false;
    private boolean doubleRequestWithoutFilter = false;

    public BaseSearchResultManager (Tdlib tdlib, TdApi.SearchChatMessages query) {
      boolean needSendUserFilterToServer = query.senderId != null && !tdlib.isUserChat(query.chatId);
      boolean needSendTypeFilterToServer = query.filter != null && !isFilterPolyfill(query.filter);

      this.needLocalFilterByUser = query.senderId != null && tdlib.isUserChat(query.chatId);
      this.needLocalFilterByType = isFilterPolyfill(query.filter);
      this.needSendDoubleRequest = needSendTypeFilterToServer && needSendUserFilterToServer;
      this.senderFilter = query.senderId;
      this.typeFilter = query.filter;
      this.tdlib = tdlib;
    }

    @Override
    @UiThread
    public void send (TdApi.SearchChatMessages function, Client.ResultHandler handler) {
      if (needSendDoubleRequest && !doubleRequestWithoutSenderId && !doubleRequestWithoutFilter) {
        getDoubleRequestFilter(function, handler);
        return;
      }

      if (needSendDoubleRequest) {
        tdlib.client().send(safeSearchChatQuery(function, doubleRequestWithoutSenderId, doubleRequestWithoutFilter), handler);
        return;
      }
      tdlib.client().send(safeSearchChatQuery(function, needLocalFilterByUser, needLocalFilterByType), handler);
    }

    private void getDoubleRequestFilter (TdApi.SearchChatMessages function, Client.ResultHandler handler) {
      Client.ResultHandler[] handlers = new Client.ResultHandler[2];
      waitMany(handlers, (o) -> onDoubleRequestFilterResult(handler, o[0], o[1]));
      tdlib.client().send(safeSearchChatQuery(function, true, false), handlers[0]);
      tdlib.client().send(safeSearchChatQuery(function, false, true), handlers[1]);
    }

    private void onDoubleRequestFilterResult (Client.ResultHandler handler, TdApi.Object withoutSender, TdApi.Object withoutFilter) {
      UI.post(() -> {
        TdApi.Messages messagesWithoutFilter = getMessages(withoutFilter, handler);
        TdApi.Messages messagesWithoutSender = getMessages(withoutSender, handler);
        if (messagesWithoutFilter == null || messagesWithoutSender == null) return;

        if (messagesWithoutSender.totalCount < messagesWithoutFilter.totalCount) {
          doubleRequestWithoutSenderId = true;
          doubleRequestWithoutFilter = false;
          tdlib.runOnTdlibThread(() -> handler.onResult(messagesWithoutSender));
        } else {
          doubleRequestWithoutSenderId = false;
          doubleRequestWithoutFilter = true;
          tdlib.runOnTdlibThread(() -> handler.onResult(messagesWithoutFilter));
        }
      });
    }

    @Override
    public boolean filter (TdApi.Message message) {
      boolean result = true;
      if (needLocalFilterByUser || doubleRequestWithoutSenderId) {
        result = filterBySender(message, senderFilter);
      }
      if (needLocalFilterByType || doubleRequestWithoutFilter) {
        result &= filterByType(tdlib, message, typeFilter);
      }
      return result;
    }
  }



  // Абстрактная реализация

  public static final int SEARCH_DIRECTION_TOP = 0;
  public static final int SEARCH_DIRECTION_BOTTOM = 1;

  interface SendSearchRequestManager {
    void send (TdApi.SearchChatMessages function, Client.ResultHandler handler);
    boolean filter (TdApi.Message messages);
  }

  public static class SendSearchRequestArguments {
    public final String contextId;
    public TdApi.SearchChatMessages function;
    public final SendSearchRequestManager manager;
    public final Client.ResultHandler handler;
    public final int direction;
    public final int minLimit;

    public SendSearchRequestArguments (String contextId, TdApi.SearchChatMessages function, Client.ResultHandler handler, SendSearchRequestManager manager, int direction, int minLimit) {
      this.contextId = contextId;
      this.function = function;
      this.handler = handler;
      this.direction = direction;
      this.minLimit = minLimit;
      this.manager = manager;
    }

    public TdApi.Message[] foundMessages = new TdApi.Message[0];
    public long fromMessageId = 0;
  }

  public static class SendSearchRequestFilterChunkInfo {
    public long minId;
    public long maxId;
    public int count;

    public SendSearchRequestFilterChunkInfo(long minId, long maxId, int count) {
      this.minId = minId;
      this.maxId = maxId;
      this.count = count;
    }

    public boolean isChunkPart (long id) {
      return minId <= id && id <= maxId;
    }
  }

  @UiThread
  public void sendSearchRequest (SendSearchRequestArguments args) {
    TdApi.SearchChatMessages query = cloneSearchChatQuery(args.function);
    query.limit = Math.min(query.limit, 25);
    query.offset = args.direction == SEARCH_DIRECTION_TOP ? 0 : 1 - query.limit;
    query.fromMessageId = args.fromMessageId != 0 ? args.fromMessageId : query.fromMessageId;

    args.manager.send(query, o -> onSendSearchRequestResult(o, args));
  }

  @TdlibThread
  private void onSendSearchRequestResult (TdApi.Object object, SendSearchRequestArguments args) {
    if (!args.contextId.equals(currentContextId)) { return; }

    TdApi.Messages messages = getMessages(object, args.handler);
    if (messages == null) return;

    if (messages.messages == null || messages.messages.length == 0) {                                   // Finished
      args.handler.onResult(makeMessages(args, messages.totalCount));
      return;
    }

    if (args.direction == SEARCH_DIRECTION_BOTTOM && messages.messages[0].id == args.fromMessageId) {   // Finished
      args.handler.onResult(makeMessages(args, messages.totalCount));
      return;
    }

    TdApi.Message[] filtered = filterSearchRequestResult(messages.messages, args.manager);
    args.foundMessages = mergeMessages(filtered, args.foundMessages, args.direction);
    args.fromMessageId = args.direction == SEARCH_DIRECTION_TOP ?
      messages.messages[messages.messages.length - 1].id :
      messages.messages[0].id;

    if (args.foundMessages.length >= args.minLimit) {
      args.handler.onResult(makeMessages(args, messages.totalCount));
    } else {
      tdlib.ui().postDelayed(() -> sendSearchRequest(args), 300);
    }
  }

  @TdlibThread
  private TdApi.Message[] filterSearchRequestResult (TdApi.Message[] messages, SendSearchRequestManager manager) {
    final long minId = messages[messages.length - 1].id;
    final long maxId = messages[0].id;
    int discardedCount = 0;

    ArrayList<TdApi.Message> filteredArr = new ArrayList<>();
    for (TdApi.Message message: messages) {
      final boolean isFiltered = manager.filter(message);
      if (isFiltered) {
        filteredArr.add(message);
        currentSearchResultsArr.append(message.id, message);
      } else if (!isWasDiscardedBefore(message.id)) {
        discardedCount += 1;
      }
    }

    if (discardedCount > 0) {
      this.filteredChunksInfo.add(new SendSearchRequestFilterChunkInfo(minId, maxId, discardedCount));
      this.discardedCount += discardedCount;
    }

    return filteredArr.toArray(new TdApi.Message[0]);
  }

  private boolean isWasDiscardedBefore (long id) {
    for (SendSearchRequestFilterChunkInfo part: filteredChunksInfo) {
      if (part.isChunkPart(id)) return true;
    }

    return false;
  }

  private int getTotalCount (SendSearchRequestArguments args, int messagesTotalCount) {
    int newTotalCount = Math.max(messagesTotalCount - this.discardedCount, args.foundMessages.length);
    if (totalCount != newTotalCount) {
      totalCount = newTotalCount;
      tdlib.ui().post(() -> {
        if (delegate != null) {
          delegate.onUpdateTotalCount(newTotalCount);
        }
      });
    }
    return newTotalCount;
  }

  public int getCachedMessagesCount () {
    return currentSearchResultsArr.size();
  }

  private TdApi.Messages makeMessages (SendSearchRequestArguments args, int totalCount) {
    return new TdApi.Messages(getTotalCount(args, totalCount), args.foundMessages);
  }

  private static TdApi.Message[] mergeMessages (TdApi.Message[] newFoundMessages, TdApi.Message[] foundMessages, int direction) {
    return direction == SEARCH_DIRECTION_TOP ?
      mergeMessageArrays(foundMessages, newFoundMessages):
      mergeMessageArrays(newFoundMessages, foundMessages);
  }





  // Middleware for Secret chats

  private String lastSecretNextOffset = null;
  private int discardedSecretMessages = 0;
  private final HashMap<String, TdApi.FoundMessages> secretMessagesCache = new HashMap<>();

  public void search (TdApi.SearchSecretMessages query, @Nullable TdApi.MessageSender sender, Client.ResultHandler resultHandler) {
    final String contextId = makeContextId(query, sender);
    checkContextId(contextId);

    TdApi.FoundMessages cached = secretMessagesCache.get(query.offset != null ? query.offset: "");
    if (cached != null) {
      resultHandler.onResult(cached);
      return;
    }

    if (!Objects.equals(lastSecretNextOffset, query.offset)) {
      TdApi.Error error = new TdApi.Error(400, "INCORRECT OFFSET");
      UI.showError(error);
      resultHandler.onResult(error);
      return;
    }

    final SecretSearchContext context = new SecretSearchContext(contextId, query, sender, resultHandler);
    tdlib.client().send(safeSearchSecretQuery(context.query), (object) -> this.onSearchSecretMessagesResult(context, object));
  }

  private void onSearchSecretMessagesResult (final SecretSearchContext context, TdApi.Object object) {
    switch (object.getConstructor()) {
      case TdApi.FoundMessages.CONSTRUCTOR: {
        onSearchSecretMessagesResultImpl(context, (TdApi.FoundMessages) object);
        break;
      }
      case TdApi.Messages.CONSTRUCTOR:
        onSearchSecretMessagesResultImpl(context, messagesToFoundMessages((TdApi.Messages) object));
        break;
      case TdApi.FoundChatMessages.CONSTRUCTOR:
        onSearchSecretMessagesResultImpl(context, messagesToFoundMessages((TdApi.FoundChatMessages) object));
        break;
      case TdApi.Error.CONSTRUCTOR: {
        context.resultHandler.onResult(object);
        break;
      }
    }
  }

  private void onSearchSecretMessagesResultImpl (final SecretSearchContext context, TdApi.FoundMessages foundMessages) {
    if (!context.contextId.equals(currentContextId)) { return; }

    lastSecretNextOffset = foundMessages.nextOffset;
    TdApi.Message[] messages = foundMessages.messages != null ?  foundMessages.messages : new TdApi.Message[0];
    int messagesCount = messages.length;

    if (context.sender != null) {
      messages = filterBySender(messages, context.sender);
    }
    if (isFilterPolyfill(context.query.filter)) {
      messages = filterByType(tdlib, messages, context.query.filter);
    }

    discardedSecretMessages += (messagesCount - messages.length);
    int totalCount = Math.max(foundMessages.totalCount - discardedSecretMessages, 0);

    if (StringUtils.isEmpty(lastSecretNextOffset)) {  // No more messages
      TdApi.FoundMessages result = new TdApi.FoundMessages(totalCount, messages, lastSecretNextOffset);
      secretMessagesCache.put(context.query.offset != null ? context.query.offset : "", result);
      context.resultHandler.onResult(result);
      return;
    }

    if (messages.length == 0) {
      search(cloneSearchSecretQuery(context.query, lastSecretNextOffset), context.sender, context.resultHandler);
      return;
    }

    TdApi.FoundMessages result = new TdApi.FoundMessages(totalCount, messages, lastSecretNextOffset);
    secretMessagesCache.put(context.query.offset != null ? context.query.offset : "", result);
    context.resultHandler.onResult(result);
  }

  private static class SecretSearchContext {
    public final String contextId;
    public final TdApi.SearchSecretMessages query;
    public final @Nullable TdApi.MessageSender sender;
    public final Client.ResultHandler resultHandler;

    public SecretSearchContext(String contextId, TdApi.SearchSecretMessages query, @Nullable TdApi.MessageSender sender, Client.ResultHandler resultHandler) {
      this.contextId = contextId;
      this.query = query;
      this.sender = sender;
      this.resultHandler = resultHandler;
    }
  }

  @SuppressLint("DefaultLocale")
  private static String makeContextId (TdApi.SearchSecretMessages query, @Nullable TdApi.MessageSender sender) {
    return String.format("secret_%d_%d_%d_%s", query.chatId, Td.getSenderId(sender), query.filter != null ? query.filter.getConstructor() : 0, query.query);
  }



  // Delegate

  interface Delegate {
    void onUpdateTotalCount (int newTotalCount);
  }

  private Delegate delegate;

  public void setDelegate (Delegate delegate) {
    this.delegate = delegate;
  }



  // Reset

  public void dismiss () {
    this.currentContextId = null;
    this.reset();
  }

  private boolean checkContextId (String contextId) {
    if (!Objects.equals(contextId, currentContextId)) {
      currentContextId = contextId;
      Log.i("SEARCH_MIDDLEWARE", "RESET");
      reset();
      return true;
    }
    return false;
  }

  private void reset () {
    this.lastSecretNextOffset = null;
    this.discardedSecretMessages = 0;
    this.secretMessagesCache.clear();

    this.filteredChunksInfo.clear();
    this.currentSearchResultsArr.clear();
    this.discardedCount = 0;
    this.totalCount = -1;
  }



  // Utils

  private static TdApi.Function<?> safeSearchSecretQuery (TdApi.SearchSecretMessages query) {
    final TdApi.SearchMessagesFilter safeFilter = safeFilter(query.filter);
    final boolean hasMediaFilter = query.filter != null && safeFilter != null && safeFilter.getConstructor() != TdApi.SearchMessagesFilterEmpty.CONSTRUCTOR;
    final boolean queryIsEmpty = StringUtils.isEmpty(query.query);

    if (queryIsEmpty) {
      if (hasMediaFilter) {
        return new TdApi.SearchChatMessages(query.chatId, query.query, null, !StringUtils.isEmpty(query.offset) ? Long.parseLong(query.offset): 0, 0, query.limit, safeFilter, 0);
      } else {
        return new TdApi.GetChatHistory(query.chatId, !StringUtils.isEmpty(query.offset) ? Long.parseLong(query.offset): 0, 0, query.limit, false);
      }
    }

    return new TdApi.SearchSecretMessages(query.chatId, query.query, query.offset, query.limit, safeFilter);
  }

  private static TdApi.SearchSecretMessages cloneSearchSecretQuery (TdApi.SearchSecretMessages query, String newOffset) {
    return new TdApi.SearchSecretMessages(query.chatId, query.query, newOffset, query.limit, query.filter);
  }

  private static TdApi.SearchChatMessages safeSearchChatQuery (TdApi.SearchChatMessages query, boolean withoutSenderId, boolean withoutFilter) {
    return new TdApi.SearchChatMessages(query.chatId, query.query, withoutSenderId ? null : query.senderId, query.fromMessageId, query.offset, query.limit, withoutFilter ? null: safeFilter(query.filter), query.messageThreadId);
  }

  private static TdApi.SearchChatMessages cloneSearchChatQuery (TdApi.SearchChatMessages query, long newFromMessageId, int newOffset) {
    return new TdApi.SearchChatMessages(query.chatId, query.query, query.senderId, newFromMessageId, newOffset, query.limit, query.filter, query.messageThreadId);
  }

  private static TdApi.SearchChatMessages cloneSearchChatQuery (TdApi.SearchChatMessages query) {
    return new TdApi.SearchChatMessages(query.chatId, query.query, query.senderId, query.fromMessageId, query.offset, query.limit, query.filter, query.messageThreadId);
  }

  private static TdApi.FoundMessages messagesToFoundMessages (TdApi.FoundChatMessages messages) {
    String nextOffset = messages.nextFromMessageId != 0 ? Long.toString(messages.nextFromMessageId) : "";
    return new TdApi.FoundMessages(messages.totalCount, messages.messages, nextOffset);
  }

  private static TdApi.FoundMessages messagesToFoundMessages (TdApi.Messages messages) {
    String nextOffset = (messages.messages != null && messages.messages.length > 0) ? Long.toString(messages.messages[messages.messages.length - 1].id) : "";
    return new TdApi.FoundMessages(messages.totalCount, messages.messages, nextOffset);
  }

  private static @Nullable TdApi.Messages getMessages (TdApi.Object object, Client.ResultHandler handler) {
    switch (object.getConstructor()) {
      case TdApi.FoundChatMessages.CONSTRUCTOR: {
        TdApi.FoundChatMessages foundChatMessages = (TdApi.FoundChatMessages) object;
        // TODO use foundChatMessages.nextFromMessageId
        return new TdApi.Messages(foundChatMessages.totalCount, foundChatMessages.messages);
      }
      case TdApi.FoundMessages.CONSTRUCTOR: {
        TdApi.FoundMessages foundMessages = (TdApi.FoundMessages) object;
        // TODO use foundMessages.nextOffset
        return new TdApi.Messages(foundMessages.totalCount, foundMessages.messages);
      }
      case TdApi.Messages.CONSTRUCTOR: {
        return ((TdApi.Messages) object);
      }
    }
    handler.onResult(object);
    return null;
  }

  private static TdApi.Message[] mergeMessageArrays (TdApi.Message[] arr1, TdApi.Message[] arr2) {
    if (arr1 == null || arr1.length == 0) return arr2;
    if (arr2 == null || arr2.length == 0) return arr1;

    ArrayList<TdApi.Message> merged = new ArrayList<>(arr1.length + arr2.length);
    Collections.addAll(merged, arr1);

    long id = arr1[arr1.length - 1].id;
    for (TdApi.Message message : arr2) {
      if (message.id >= id) continue;
      merged.add(message);
    }

    return merged.toArray(new TdApi.Message[0]);
  }



  // Filter functions

  public static TdApi.Message[] filterBySender (@Nullable final TdApi.Message[] messages, final TdApi.MessageSender sender) {
    if (messages == null) return new TdApi.Message[0];
    ArrayList<TdApi.Message> filtered = new ArrayList<>(messages.length);
    for (TdApi.Message message : messages) {
      if (filterBySender(message, sender)) {
        filtered.add(message);
      }
    }

    return filtered.toArray(new TdApi.Message[0]);
  }

  public static boolean filterBySender (final TdApi.Message message, final TdApi.MessageSender sender) {
    return Td.getSenderId(message.senderId) == Td.getSenderId(sender);
  }

  public static TdApi.Message[] filterByType (Tdlib tdlib, @Nullable final TdApi.Message[] messages, final TdApi.SearchMessagesFilter filter) {
    if (messages == null) return new TdApi.Message[0];
    ArrayList<TdApi.Message> filtered = new ArrayList<>(messages.length);
    for (TdApi.Message message : messages) {
      if (filterByType(tdlib, message, filter)) {
        filtered.add(message);
      }
    }

    return filtered.toArray(new TdApi.Message[0]);
  }

  public static boolean filterByType (Tdlib tdlib, final TdApi.Message message, final TdApi.SearchMessagesFilter filter) {
    return MessagesController.getMessageType(tdlib, message, message.content) == filter.getConstructor();
  }



  // Send utils

  interface SendManyHandler {
    void onResult (TdApi.Object[] results);
  }

  private static void waitMany (Client.ResultHandler[] handlers, SendManyHandler resultHandler) {
    TdApi.Object[] results = new TdApi.Object[handlers.length];
    for (int i = 0; i < handlers.length; i++) {
      final int index = i;
      handlers[i] = o -> sendManyOnResult(resultHandler, results, o, index);
    }
  }

  private static void sendManyOnResult (SendManyHandler handler, TdApi.Object[] results, TdApi.Object object, int index) {
    results[index] = object;
    for (TdApi.Object result : results) {
      if (result == null) return;
    }
    handler.onResult(results);
  }



  // Text Filter Polyfill

  @SuppressLint("WrongConstant")
  public static boolean isFilterPolyfill (@Nullable TdApi.SearchMessagesFilter filter) {
    return filter != null && filter.getConstructor() == SearchMessagesFilterTextPolyfill.CONSTRUCTOR;
  }

  public static @Nullable TdApi.SearchMessagesFilter safeFilter (@Nullable TdApi.SearchMessagesFilter filter) {
    if (isFilterPolyfill(filter)) {
      return new TdApi.SearchMessagesFilterEmpty();
    } else return filter;
  }

  public static class SearchMessagesFilterTextPolyfill extends TdApi.SearchMessagesFilter {
    public SearchMessagesFilterTextPolyfill() {}

    public static final int CONSTRUCTOR = 1;

    @SuppressLint("WrongConstant") @Override
    public int getConstructor() {
      return CONSTRUCTOR;
    }
  }
}
