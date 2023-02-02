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
 * File created on 30/11/2016
 */
package org.thunderdog.challegram.helper;

import android.Manifest;
import android.content.Context;
import android.content.res.Resources;
import android.location.Location;
import android.os.Build;
import android.os.LocaleList;
import android.os.SystemClock;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputMethodSubtype;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import org.drinkless.td.libcore.telegram.Client;
import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.N;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.component.chat.InlineResultsWrap;
import org.thunderdog.challegram.component.popups.ModernOptions;
import org.thunderdog.challegram.component.sticker.TGStickerObj;
import org.thunderdog.challegram.core.Background;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.InlineResult;
import org.thunderdog.challegram.data.InlineResultButton;
import org.thunderdog.challegram.data.InlineResultCommon;
import org.thunderdog.challegram.data.InlineResultEmojiSuggestion;
import org.thunderdog.challegram.data.InlineResultHashtag;
import org.thunderdog.challegram.data.InlineResultMention;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.emoji.Emoji;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.player.TGPlayerController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Intents;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.util.CancellableResultHandler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import me.vkryl.android.LocaleUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.core.lambda.CancellableRunnable;
import me.vkryl.td.ChatId;
import me.vkryl.td.Td;

public class InlineSearchContext implements LocationHelper.LocationChangeListener, InlineResultsWrap.LoadMoreCallback {
  public static final int WARNING_OK = 0;
  public static final int WARNING_CONFIRM = 1;
  public static final int WARNING_BLOCK = 2;

  public interface Callback {
    long provideInlineSearchChatId ();
    TdApi.Chat provideInlineSearchChat ();
    TdApi.WebPage provideExistingWebPage (TdApi.FormattedText currentText);
    long provideInlineSearchChatUserId ();
    boolean isDisplayingItems ();
    void updateInlineMode (boolean isInInlineMode, boolean isInProgress);
    void showInlinePlaceholder (@NonNull String username, @NonNull String placeholder);
    void showInlineResults (ArrayList<InlineResult<?>> items, boolean isContent);
    void addInlineResults (ArrayList<InlineResult<?>> items);
    void hideInlineResults ();
    void showInlineStickers (ArrayList<TGStickerObj> stickers, boolean isMore);
    boolean needsLinkPreview ();
    boolean showLinkPreview (@Nullable String link, @Nullable TdApi.WebPage webPage);

    boolean needsInlineBots ();

    TdApi.FormattedText getOutputText (boolean applyMarkdown);

    int showLinkPreviewWarning (int contextId, @Nullable String link);
  }

  public interface CommandListProvider {
    ArrayList<InlineResult<?>> searchCommands (String prefix, String currentQuery, QueryResultsChangeListener changeListener);
  }

  public interface QueryResultsChangeListener {
    void onQueryResultsChanged (String queryText);
  }

  public static final int MODE_NONE = 0;
  public static final int MODE_MENTION = 1;
  public static final int MODE_HASHTAGS = 2;
  public static final int MODE_EMOJI_SUGGESTION = 3;
  public static final int MODE_STICKERS = 4;
  public static final int MODE_COMMAND = 5;
  public static final int MODE_INLINE_SEARCH = 6;

  private static final int FLAG_CAPTION = 1;
  private static final int FLAG_DISALLOW_INLINE_RESULTS = 1 << 1;

  private final BaseActivity context;
  private final Tdlib tdlib;
  private final Callback callback;
  private final LocationHelper locationTracker;
  private final QueryResultsChangeListener changeListener;

  private @Nullable CommandListProvider commandListProvider;

  private int flags;

  private int currentMode;
  private CharSequence currentCs;
  private String currentText;
  private boolean canHandlePositionChange;
  private int lastHandledPosition;
  private int lastKnownCursorPosition;
  private ViewController<?> boundController;

  public InlineSearchContext (BaseActivity context, Tdlib tdlib, @NonNull Callback callback, ViewController<?> boundController) {
    this.context = context;
    this.boundController = boundController;
    this.locationTracker = new LocationHelper(context, this, true, false);
    this.locationTracker.setPermissionRequester((skipAlert, onCancel, handler) -> {
      if (skipAlert) {
        context.requestLocationPermission(false, true, handler);
      } else {
        ModernOptions.showLocationAlert(boundController, getInlineUsername(), onCancel, () -> {
          context.requestLocationPermission(false, true, handler);
        });
      }
    });
    this.changeListener = InlineSearchContext.this::onQueryResultsChanged;
    this.tdlib = tdlib;
    this.callback = callback;
    this.currentText = "";
  }

  public void setIsCaption (boolean isCaption) {
    if (isCaption) {
      flags |= FLAG_CAPTION;
    } else {
      flags &= ~FLAG_CAPTION;
    }
  }

  public boolean isCaption () {
    return (flags & FLAG_CAPTION) != 0;
  }

  public void setDisallowInlineResults (boolean disallow, boolean needRefresh) {
    if (disallowInlineResults() != disallow) {
      if (disallow) {
        flags |= FLAG_DISALLOW_INLINE_RESULTS;
      } else {
        flags &= ~FLAG_DISALLOW_INLINE_RESULTS;
      }
      if (!currentText.trim().isEmpty() && needRefresh) {
        String text = currentText;
        CharSequence cs = currentCs;
        currentText = ""; currentCs = "";
        onTextChanged(cs, text, lastKnownCursorPosition);
      }
    }
  }

  private boolean disallowInlineResults () {
    return (flags & FLAG_DISALLOW_INLINE_RESULTS) != 0;
  }

  public void setCommandListProvider (@Nullable CommandListProvider commandListProvider) {
    this.commandListProvider = commandListProvider;
  }

  // Public entry

  public void forceCheck () {
    onQueryResultsChanged(currentText);
  }

  public void onQueryResultsChanged (String queryText) {
    if (!currentText.isEmpty() && currentText.equals(queryText)) {
      currentText = ""; currentCs = "";
      onTextChanged(queryText, queryText, lastKnownCursorPosition);
    }
  }

  public void onCursorPositionChanged (int newPosition) {
    lastKnownCursorPosition = newPosition;
    if (canHandlePositionChange && lastHandledPosition != newPosition) {
      cancelPendingQueries();
      searchOther(newPosition);
    }
  }

  public void onTextChanged (CharSequence newCs, String newText, @IntRange(from = -1, to = Integer.MAX_VALUE) int cursorPosition) {
    lastKnownCursorPosition = cursorPosition;
    if (StringUtils.equalsOrBothEmpty(this.currentText, newText)) {
      return;
    }
    this.currentText = newText;
    this.canHandlePositionChange = false;
    cancelPendingQueries();

    boolean probablyHasWebPagePreview;

    if (newText.trim().isEmpty()) {
      probablyHasWebPagePreview = false;
      clearInlineMode();

      // Do nothing with empty text
      setCurrentMode(MODE_NONE);
    } else if (Emoji.instance().isSingleEmoji(newCs, false)) {
      probablyHasWebPagePreview = false;
      clearInlineMode();

      // Display stickers in case of a single emoji
      if (isCaption() || disallowInlineResults()) {
        setCurrentMode(MODE_NONE);
      } else {
        setCurrentMode(MODE_STICKERS);
        searchStickers(newText, false, null);
      }
    } else {
      final String inlineUsername = getInlineUsername();
      if (inlineUsername != null) {
        probablyHasWebPagePreview = false;
        processInlineQueryOrOther(inlineUsername, currentText.substring(inlineUsername.length() + 2), cursorPosition);
      } else {
        clearInlineMode();
        probablyHasWebPagePreview = searchOther(cursorPosition);
      }
    }

    processLinkPreview(probablyHasWebPagePreview ? newText : "");
  }

  // Common UI

  private void hideResults () {
    callback.hideInlineResults();
  }

  // Mentions, commands and hashtags

  private static final int TYPE_INLINE_BOT = 1;
  private static final int TYPE_MENTIONS = 2;
  private static final int TYPE_COMMANDS = 3;
  private static final int TYPE_HASHTAGS = 4;
  private static final int TYPE_EMOJI_SUGGESTIONS = 5;

  private int lastInlineResultsType;

  private void showMentions (ArrayList<InlineResult<?>> mentions) {
    lastInlineResultsType = TYPE_MENTIONS;
    callback.showInlineResults(mentions, false);
  }

  private void showHashtags (ArrayList<InlineResult<?>> hashtags) {
    lastInlineResultsType = TYPE_HASHTAGS;
    callback.showInlineResults(hashtags, false);
  }

  private void showEmojiSuggestions (ArrayList<InlineResult<?>> suggestions) {
    lastInlineResultsType = TYPE_EMOJI_SUGGESTIONS;
    callback.showInlineResults(suggestions, false);
  }

  private void showCommands (ArrayList<InlineResult<?>> commands) {
    lastInlineResultsType = TYPE_COMMANDS;
    callback.showInlineResults(commands, true);
  }

  // Inline results UI

  public boolean isVisibleOrActive () {
    return inProgress || callback.isDisplayingItems();
  }

  private String currentInlineQuery;
  private @Nullable TdApi.Location currentQueryLocation;
  private @Nullable String currentNextOffset;

  private void resetInlineResultState () {
    currentInlineQuery = null;
    currentNextOffset = null;
    currentQueryLocation = null;
    setInProgress(false);
  }

  private void showInlineResults (String inlineQuery, @Nullable TdApi.Location location, @Nullable String nextOffset, ArrayList<InlineResult<?>> items) {
    this.currentInlineQuery = inlineQuery;
    this.currentNextOffset = nextOffset;
    this.currentQueryLocation = location;
    this.lastInlineResultsType = TYPE_INLINE_BOT;
    callback.showInlineResults(items, true);
    setInProgress(false);
  }

  private void addInlineResults (@Nullable String nextOffset, ArrayList<InlineResult<?>> items) {
    cancelInlineQueryMoreRequest();
    currentNextOffset = nextOffset;
    callback.addInlineResults(items);
    setInProgress(false);
  }

  @Override
  public void onLoadMoreRequested () {
    requestMoreResults();
  }

  // State management

  private void setCurrentMode (int mode) {
    if (this.currentMode != mode) {
      boolean oldIsInline = currentMode == MODE_INLINE_SEARCH;
      this.currentMode = mode;
      boolean isInline = mode == MODE_INLINE_SEARCH;
      if (oldIsInline != isInline) {
        callback.updateInlineMode(isInline, inProgress);
      }
      hideResults();
    }
  }

  private boolean inProgress;

  private void setInProgress (boolean inProgress) {
    if (this.inProgress != inProgress) {
      this.inProgress = inProgress;
      callback.updateInlineMode(currentMode == MODE_INLINE_SEARCH, inProgress);
    }
  }

  // Requests

  private void cancelPendingQueries () {
    cancelStickerRequest();
    cancelInlineQueryRequests();
    cancelLocationRequest();
    cancelHashtagsSearchQuery();
    cancelEmojiHandler();
    cancelMentionSearchHandler();
  }

  // Utils

  public @Nullable String getInlineUsername () {
    if (!isCaption() && !disallowInlineResults() && currentText.length() > 1 && currentText.charAt(0) == '@') {
      final int length = currentText.length();
      for (int i = 1; i < length; i++) {
        char c = currentText.charAt(i);
        if (!TD.matchUsername(c)) {
          return c == ' ' ? currentText.substring(1, i) : null;
        }
      }
    }
    return null;
  }

  private @Nullable String getInlineQuery () {
    String username = getInlineUsername();
    return username != null ? currentText.substring(username.length() + 2) : null;
  }

  // Stickers

  private CancellableResultHandler stickerRequest;

  private void cancelStickerRequest () {
    if (stickerRequest != null) {
      stickerRequest.cancel();
      stickerRequest = null;
    }
  }

  private void searchStickers (final String emoji, final boolean more, @Nullable final int[] ignoreStickerIds) {
    final int stickerMode = Settings.instance().getStickerMode();
    if (stickerMode == Settings.STICKER_MODE_NONE) {
      return;
    }
    if (stickerMode == Settings.STICKER_MODE_ALL && !more && tdlib.suggestOnlyApiStickers()) {
      searchStickers(emoji, true, ignoreStickerIds);
      return;
    }
    stickerRequest = new CancellableResultHandler() {
      @Override
      public void processResult (TdApi.Object object) {
        switch (object.getConstructor()) {
          case TdApi.Stickers.CONSTRUCTOR: {
            final TdApi.Stickers stickers = (TdApi.Stickers) object;
            final TdApi.Sticker[] displayingStickers;
            final int[] futureIgnoreStickerIds;
            if (ignoreStickerIds != null && ignoreStickerIds.length > 0) {
              List<TdApi.Sticker> filteredStickers = new ArrayList<>(stickers.stickers.length);
              for (TdApi.Sticker sticker : stickers.stickers) {
                if (Arrays.binarySearch(ignoreStickerIds, sticker.sticker.id) < 0) {
                  filteredStickers.add(sticker);
                }
              }
              displayingStickers = new TdApi.Sticker[filteredStickers.size()];
              filteredStickers.toArray(displayingStickers);
            } else {
              displayingStickers = stickers.stickers;
            }
            if (more) {
              futureIgnoreStickerIds = null;
            } else {
              futureIgnoreStickerIds = new int[displayingStickers.length];
              int i = 0;
              for (TdApi.Sticker sticker : displayingStickers) {
                futureIgnoreStickerIds[i] = sticker.sticker.id;
                i++;
              }
              Arrays.sort(futureIgnoreStickerIds);
            }
            tdlib.ui().post(() -> {
              if (!isCancelled()) {
                displayStickers(displayingStickers, emoji, more);
                if (!more && stickerMode == Settings.STICKER_MODE_ALL) {
                  searchStickers(emoji, true, futureIgnoreStickerIds);
                }
              }
            });
            break;
          }
        }
      }
    };
    final long chatId = callback.provideInlineSearchChatId();
    TdApi.Function<?> function;
    if (more) {
      function = new TdApi.SearchStickers(emoji, 1000);
    } else {
      function = new TdApi.GetStickers(new TdApi.StickerTypeRegular(), emoji, 1000, chatId);
    }
    tdlib.client().send(function, stickerRequest);
  }

  @UiThread
  private void displayStickers (TdApi.Sticker[] stickers, String foundByEmoji, boolean isMore) {
    ArrayList<TGStickerObj> list = new ArrayList<>(stickers.length);
    for (TdApi.Sticker sticker : stickers) {
      list.add(new TGStickerObj(tdlib, sticker, foundByEmoji, sticker.type));
    }
    callback.showInlineStickers(list, isMore);
  }

  // Inline query

  private String lastInlineUsername;
  private TdApi.User inlineBot;
  private boolean allowInlineLocation;
  private CancellableResultHandler inlineBotHandler;

  private void processInlineQueryOrOther (final String username, final String inlineQuery, final int cursorPosition) {
    if (lastInlineUsername == null || !lastInlineUsername.toLowerCase().equals(username.toLowerCase())) {
      lastInlineUsername = username;

      if (inlineBotHandler != null) {
        inlineBotHandler.cancel();
        inlineBotHandler = null;
      }

      TdApi.User user = tdlib.cache().searchUser(username.toLowerCase());

      if (user != null) {
        if (user.type.getConstructor() == TdApi.UserTypeBot.CONSTRUCTOR && ((TdApi.UserTypeBot) user.type).isInline) {
          if (tdlib.showRestriction(callback.provideInlineSearchChat(), R.id.right_sendStickersAndGifs, R.string.ChatDisabledBots, R.string.ChatRestrictedBots, R.string.ChatRestrictedBotsUntil)) {
            searchOther(cursorPosition);
          } else {
            applyInlineBot(user);
          }
        } else {
          searchOther(cursorPosition);
        }
        return;
      }

      searchOther(cursorPosition);

      inlineBotHandler = new CancellableResultHandler() {
        @Override
        public void processResult (TdApi.Object object) {
          final long chatId = TD.getChatId(object);
          if (chatId != 0) {
            final TdApi.User user = tdlib.chatUser((TdApi.Chat) object);
            if (user != null && user.type.getConstructor() == TdApi.UserTypeBot.CONSTRUCTOR && ((TdApi.UserTypeBot) user.type).isInline) {
              tdlib.ui().post(() -> {
                String currentUsername = getInlineUsername();
                if (!isCancelled() && username.equalsIgnoreCase(currentUsername) && Td.findUsername(user, currentUsername)) {
                  applyInlineBot(user);
                }
              });
            }
          }
        }
      };
      tdlib.client().send(new TdApi.SearchPublicChat(username), inlineBotHandler);
      return;
    }

    if (inlineBot != null) {
      if (allowInlineLocation) {
        searchInlineResultsLocated(inlineQuery, inlineQuery != null && inlineQuery.isEmpty(), false);
      } else {
        searchInlineResults(Td.primaryUsername(inlineBot.usernames), inlineQuery, null, false);
      }
    } else {
      searchOther(cursorPosition);
    }
  }

  private void applyInlineBot (TdApi.User user) {
    this.inlineBot = user;
    this.allowInlineLocation = ((TdApi.UserTypeBot) user.type).needLocation && Settings.instance().allowLocationForBot(inlineBot.id);
    String username = getInlineUsername();
    if (username == null) {
      username = Td.primaryUsername(user);
    }
    callback.showInlinePlaceholder(username, ((TdApi.UserTypeBot) user.type).inlineQueryPlaceholder);
    setCurrentMode(MODE_INLINE_SEARCH);
    requestLocationForInlineBot(currentText.substring(username.length() + 2), true);
  }

  private void clearInlineMode () {
    if (inlineBotHandler != null) {
      inlineBotHandler.cancel();
      inlineBotHandler = null;
    }
    callback.showInlinePlaceholder("", "");
    resetInlineResultState();
    inlineBot = null;
    lastInlineUsername = null;
  }

  // Inline query

  private void requestLocationForInlineBot (final String inlineQuery, final boolean firstQuery) {
    if (((TdApi.UserTypeBot) inlineBot.type).needLocation) {
      if (allowInlineLocation) {
        searchInlineResultsLocated(inlineQuery, true, false);
      } else {
        final boolean [] requested = new boolean[1];
        final String currentInlineUsername = getInlineUsername();

        final Runnable onCancel = () -> {
          if (!requested[0]) {
            requested[0] = true;
            if (currentInlineUsername != null && currentInlineUsername.equals(getInlineUsername())) {
              searchInlineResults(Td.primaryUsername(inlineBot), currentText.substring(currentInlineUsername.length() + 2), null, firstQuery);
            }
          }
        };

        final Runnable onConfirm = () -> {
          if (!requested[0]) {
            requested[0] = true;
            if (currentInlineUsername != null && currentInlineUsername.equals(getInlineUsername())) {
              allowInlineLocation = true;
              Settings.instance().setAllowLocationForBot(inlineBot.id);
              searchInlineResultsLocated(currentText.substring(currentInlineUsername.length() + 2), true, true);
            }
          }
        };

        ModernOptions.showLocationAlert(boundController, currentInlineUsername, onCancel, onConfirm);
      }
    } else {
      searchInlineResults(Td.primaryUsername(inlineBot), inlineQuery, null, firstQuery);
    }
  }

  private void cancelLocationRequest () {
    locationTracker.cancel();
  }

  private void searchInlineResultsLocated (final String inlineQuery, boolean allowResolution, boolean skipAlert) {
    setInProgress(true);
    locationTracker.receiveLocation(currentText, null, 7000, allowResolution, skipAlert);
  }

  @Override
  public void onLocationResult (LocationHelper context, @NonNull String arg, @Nullable Location location) {
    if (currentText.equals(arg)) {
      searchInlineResults(Td.primaryUsername(inlineBot), getInlineQuery(), location, false);
    }
  }

  @Override
  public void onLocationRequestFailed (LocationHelper context, int errorCode, @NonNull String arg, @Nullable Location savedLocation) {
    String inlineQuery = getInlineQuery();

    if (errorCode == LocationHelper.ERROR_CODE_PERMISSION && (inlineQuery == null || inlineQuery.isEmpty()) && !U.shouldShowPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
      Intents.openPermissionSettings();
    }

    if (currentText.equals(arg)) {
      searchInlineResults(Td.primaryUsername(inlineBot), inlineQuery, savedLocation, false);
    }
  }

  private CancellableResultHandler inlineQueryHandler, inlineQueryMoreHandler;

  private void cancelInlineQueryMoreRequest () {
    if (inlineQueryMoreHandler != null) {
      inlineQueryMoreHandler.cancel();
      inlineQueryMoreHandler = null;
    }
  }

  private void cancelInlineQueryRequests () {
    if (inlineQueryHandler != null) {
      inlineQueryHandler.cancel();
      inlineQueryHandler = null;
    }
    cancelInlineQueryMoreRequest();
  }

  private static class CommonPlayListBuilder implements TGPlayerController.PlayListBuilder {
    private final ArrayList<InlineResult<?>> results;
    private final TdApi.GetInlineQueryResults query;
    private final String nextOffset;

    public CommonPlayListBuilder (ArrayList<InlineResult<?>> results, TdApi.GetInlineQueryResults query, String nextOffset) {
      this.results = results;
      this.query = query;
      this.nextOffset = nextOffset;
    }

    @Nullable
    @Override
    public TGPlayerController.PlayList buildPlayList (TdApi.Message fromMessage) {
      if (results == null || results.isEmpty()) {
        return null;
      }

      int contentType = fromMessage.content.getConstructor();
      ArrayList<TdApi.Message> items = null;
      int foundIndex = -1;

      for (InlineResult<?> result : this.results) {
        if (!(result instanceof InlineResultCommon)) {
          continue;
        }
        TdApi.Message message = ((InlineResultCommon) result).getPlayPauseMessage();
        if (message != null && message.content.getConstructor() == contentType) {
          if (items == null) {
            items = new ArrayList<>();
          }
          if (TGPlayerController.compareTracks(message ,fromMessage)) {
            foundIndex = items.size();
          }
          items.add(message);
        }
      }
      if (foundIndex != -1) {
        return new TGPlayerController.PlayList(items, foundIndex).setInlineQuery(query, nextOffset);
      }
      return null;
    }

    @Override
    public boolean wouldReusePlayList (TdApi.Message fromMessage, boolean isReverse, boolean hasAltered, List<TdApi.Message> trackList, long playListChatId) {
      return false;
    }
  }

  private static ArrayList<InlineResult<?>> parseInlineResults (BaseActivity context, Tdlib tdlib, long inlineBotUserId, String inlineQuery, TdApi.InlineQueryResults results, @Nullable String switchPmText, @Nullable String switchPmParameter, TdApi.GetInlineQueryResults queryResults, String inlineNextOffset) {
    final ArrayList<InlineResult<?>> items = new ArrayList<>(results.results.length + (switchPmText != null && !switchPmText.isEmpty() ? 1 : 0));
    if (switchPmText != null && !switchPmText.isEmpty()) {
      items.add(new InlineResultButton(context, tdlib, inlineBotUserId, switchPmText, switchPmParameter));
    }
    TGPlayerController.PlayListBuilder builder = new CommonPlayListBuilder(items, queryResults, inlineNextOffset);
    for (TdApi.InlineQueryResult result : results.results) {
      InlineResult<?> resultItem = InlineResult.valueOf(context, tdlib, inlineQuery, result, builder);
      if (resultItem != null) {
        resultItem.setBoundList(items);
        resultItem.setQueryId(results.inlineQueryId);
        items.add(resultItem);
      }
    }
    return items;
  }

  private void searchInlineResults (final String botUsername, final String inlineQuery, final @Nullable Location userLocation, final boolean firstQuery) {
    setInProgress(true);
    cancelInlineQueryMoreRequest();
    final TdApi.Location location = userLocation != null ? new TdApi.Location(userLocation.getLatitude(), userLocation.getLongitude(), userLocation.getAccuracy()) : null;
    final long queryStartTime = SystemClock.uptimeMillis();
    final long botUserId = inlineBot.id;
    final long chatId = callback.provideInlineSearchChatId();
    final TdApi.GetInlineQueryResults function = new TdApi.GetInlineQueryResults(botUserId, chatId, location, inlineQuery, null);
    inlineQueryHandler = new CancellableResultHandler() {
      @Override
      public void processResult (TdApi.Object object) {
        switch (object.getConstructor()) {
          case TdApi.InlineQueryResults.CONSTRUCTOR: {
            final TdApi.InlineQueryResults results = (TdApi.InlineQueryResults) object;
            final long elapsed = SystemClock.uptimeMillis() - queryStartTime;
            final ArrayList<InlineResult<?>> inlineResults = parseInlineResults(context, tdlib, inlineBot != null ? inlineBot.id : 0, inlineQuery, results, results.switchPmText, results.switchPmParameter, function, results.nextOffset);
            tdlib.ui().postDelayed(() -> {
              if (!isCancelled() && getInlineUsername() != null) {
                showInlineResults(inlineQuery, location, results.nextOffset, inlineResults);
              }
            }, firstQuery && elapsed < 100 ? 100 - elapsed : 0);
            break;
          }
          case TdApi.Error.CONSTRUCTOR: {
            if (TD.errorCode(object) == 406) {
              break;
            }
            if (TD.errorCode(object) == 502) {
              UI.showBotDown(botUsername);
            }
            // else go to default:
          }
          default: {
            tdlib.ui().post(() -> {
              if (!isCancelled() && getInlineUsername() != null) {
                setInProgress(false);
                hideResults();
              }
            });
            break;
          }
        }
      }
    };

    if (chatId != 0 && StringUtils.isEmpty(inlineQuery) && Settings.instance().needTutorial(Settings.TUTORIAL_INLINE_SEARCH_SECRECY)) {
      // TdApi.Chat chat = callback.provideInlineSearchChat();
      if (ChatId.isSecret(callback.provideInlineSearchChatId())) {
        ViewController<?> c = UI.getCurrentStackItem();
        if (c != null) {
          c.openAlert(R.string.AppName, Lang.getString(R.string.SecretChatContextBotAlert), Lang.getString(R.string.Confirm), (dialog, which) -> Settings.instance().markTutorialAsComplete(Settings.TUTORIAL_INLINE_SEARCH_SECRECY), ViewController.ALERT_NO_CANCEL | ViewController.ALERT_NO_CANCELABLE);
        }
      }
    }

    tdlib.client().send(function, inlineQueryHandler);
  }

  public void requestMoreResults () {
    if (currentNextOffset == null || currentNextOffset.isEmpty() || inlineQueryMoreHandler != null) {
      return;
    }

    final String queryFinal = currentInlineQuery;
    final TdApi.GetInlineQueryResults query = new TdApi.GetInlineQueryResults(inlineBot.id, callback.provideInlineSearchChatId(), currentQueryLocation, queryFinal, currentNextOffset);
    final String lastNextOffset = this.currentNextOffset;
    inlineQueryMoreHandler = new CancellableResultHandler() {
      @Override
      public void processResult (TdApi.Object object) {
        switch (object.getConstructor()) {
          case TdApi.InlineQueryResults.CONSTRUCTOR: {
            final TdApi.InlineQueryResults results = (TdApi.InlineQueryResults) object;
            final ArrayList<InlineResult<?>> inlineResults = parseInlineResults(context, tdlib, inlineBot != null ? inlineBot.id : 0, queryFinal, results, null, null, query, results.nextOffset);
            tdlib.ui().post(() -> {
              if (!isCancelled() && currentNextOffset != null && lastNextOffset.equals(currentNextOffset)) {
                cancelInlineQueryMoreRequest();
                addInlineResults(results.nextOffset, inlineResults);
              }
            });
            break;
          }
          default: {
            tdlib.ui().post(() -> {
              if (!isCancelled() && currentNextOffset != null && lastNextOffset.equals(currentNextOffset)) {
                cancelInlineQueryMoreRequest();
                setInProgress(false);
              }
            });
            break;
          }
        }
      }
    };

    setInProgress(true);
    tdlib.client().send(query, inlineQueryMoreHandler);
  }

  // Other

  private final int[] bounds = new int[2];

  private interface WordFilter {
    boolean filter (char c);
  }

  // private static final WordFilter usernameFilter = TD::matchUsername;

  private static final WordFilter hashtagFilter = TD::matchHashtag;

  private static final WordFilter nameFilter = Character::isLetterOrDigit;

  private static final WordFilter emojiSuggestionFilter = c -> c == '_' || Character.isLetterOrDigit(c);

  private static void searchWord (char startChar, String text, int cursorPosition, int[] output, WordFilter filter) {
    char c;

    boolean cursorUsed = cursorPosition != -1 && cursorPosition >= 0 && cursorPosition <= text.length();
    int endIndex = cursorUsed ? cursorPosition : text.length();
    int startIndex = endIndex - 1;
    while (startIndex >= 0 && (c = text.charAt(startIndex)) != startChar) {
      startIndex--;
      if (!filter.filter(c)) { // (startChar == '@' && !TGUtils.matchUsername(c)) || (startChar == '#' && !TGUtils.matchHashtag(c))
        startIndex = -1;
        break;
      }
    }
    if (startIndex != -1 && (text.charAt(startIndex) != startChar || (startIndex > 0 && !Strings.isSeparator(text.charAt(startIndex - 1))))) {
      startIndex = -1;
    }
    if (startIndex != -1 && !cursorUsed) {
      final int length = text.length();
      while (endIndex < length && (c = text.charAt(endIndex)) != ' ' && c != '\n') {
        endIndex++;
        if (!filter.filter(c)) {
          endIndex = -1;
          break;
        }
      }
    }
    output[0] = startIndex;
    output[1] = endIndex;
  }

  /**
   *
   * @param cursorPosition position of the cursor in the input view
   * @return Whether text probably contains WebPage
   */
  private boolean searchOther (int cursorPosition) {
    this.canHandlePositionChange = true;
    this.lastHandledPosition = cursorPosition;
    if (currentText.charAt(0) == '/') {
      boolean isOk = true;

      final int length = currentText.length();
      for (int i = 1; i < length; i++) {
        if (!TD.matchCommand(currentText.charAt(i))) {
          isOk = false;
          break;
        }
      }

      if (isOk) {
        setCurrentMode(MODE_COMMAND);
        searchCommands(currentText.substring(1));
        return false;
      }
    }

    searchWord('@', currentText, cursorPosition, bounds, nameFilter);

    if (bounds[0] != -1 && bounds[1] != -1) {
      boolean delayed = currentMode == MODE_INLINE_SEARCH;
      setCurrentMode(MODE_MENTION);
      searchMentions(bounds[0], bounds[1], currentText, currentText.substring(bounds[0] + 1, bounds[1]), delayed);
      return true;
    }

    searchWord('#', currentText, cursorPosition, bounds, hashtagFilter);
    if (bounds[0] != -1 && bounds[1] != -1) {
      setCurrentMode(MODE_HASHTAGS);
      searchHashtags(bounds[0], bounds[1], currentText, currentText.substring(bounds[0] + 1, bounds[1]));
      return true;
    }

    searchWord(':', currentText, cursorPosition, bounds, emojiSuggestionFilter);
    if (bounds[0] != -1 && bounds[1] != -1) {
      final int emojiSuggestionLength = bounds[1] - bounds[0] - 1;
      if (emojiSuggestionLength > 0) {
        int maxLength = N.getEmojiSuggestionMaxLength() - 5;
        if (emojiSuggestionLength <= maxLength) {
          setCurrentMode(MODE_EMOJI_SUGGESTION);
          searchEmoji(bounds[0], bounds[1], currentText, currentText.substring(bounds[0] + 1, bounds[1]));
          return true;
        }
      }
    }

    setCurrentMode(MODE_NONE);

    return true;
  }

  // Mentions

  private ArrayList<InlineResult<?>> inlineBots;
  private String inlineBotsAwaitText;

  public void resetInlineBotsCache () {
    if (inlineBots != null) {
      inlineBots = null;
      inlineBotsAwaitText = null;
    }
  }

  public void removeInlineBot (long userId) {
    if (inlineBots != null) {
      int i = 0;
      for (InlineResult<?> result : inlineBots) {
        if (result instanceof InlineResultMention && ((InlineResultMention) result).getUserId() == userId) {
          inlineBots.remove(i);
          break;
        }
        i++;
      }
    }
  }

  private CancellableResultHandler mentionSearchHandler;

  private void cancelMentionSearchHandler () {
    if (mentionSearchHandler != null) {
      mentionSearchHandler.cancel();
      mentionSearchHandler = null;
    }
  }

  private static int indexOfMention (ArrayList<InlineResult<?>> items, long userId) {
    if (items == null || items.isEmpty()) {
      return -1;
    }
    int i = 0;
    for (InlineResult<?> result : items) {
      if (result instanceof InlineResultMention && ((InlineResultMention) result).getUserId() == userId) {
        return i;
      }
      i++;
    }
    return -1;
  }

  private void searchMentions (final int startIndex, final int endIndex, final String currentText, final String prefix, final boolean delayed) {
    if (delayed) {
      hideResults();
      tdlib.ui().postDelayed(() -> onQueryResultsChanged(currentText), 100);
      return;
    }

    final ArrayList<InlineResult<?>> results = new ArrayList<>();

    final boolean needInlineBots = startIndex == 0 && !isCaption() && callback.needsInlineBots();
    if (needInlineBots) {
      if (inlineBots == null) {
        boolean needLoad = inlineBotsAwaitText == null;
        inlineBotsAwaitText = currentText;
        if (needLoad) {
          tdlib.client().send(new TdApi.GetTopChats(new TdApi.TopChatCategoryInlineBots(), 10), new Client.ResultHandler() {
            @Override
            public void onResult (TdApi.Object object) {
              final ArrayList<InlineResult<?>> inlineBotResults = new ArrayList<>();
              switch (object.getConstructor()) {
                case TdApi.Chats.CONSTRUCTOR: {
                  long[] chatIds = ((TdApi.Chats) object).chatIds;
                  List<TdApi.Chat> chats = tdlib.chats(chatIds);
                  inlineBotResults.ensureCapacity(chats.size());
                  for (TdApi.Chat chat : chats) {
                    TdApi.User user = tdlib.chatUser(chat);
                    if (user != null) {
                      inlineBotResults.add(new InlineResultMention(context, tdlib, user, true));
                    }
                  }
                  if (chats.isEmpty()) {
                    tdlib.client().send(new TdApi.GetRecentInlineBots(), this);
                    return;
                  }
                  break;
                }
                case TdApi.Users.CONSTRUCTOR: {
                  long[] userIds = ((TdApi.Users) object).userIds;
                  ArrayList<TdApi.User> users = tdlib.cache().users(userIds);
                  inlineBotResults.ensureCapacity(userIds.length);
                  for (TdApi.User user : users) {
                    inlineBotResults.add(new InlineResultMention(context, tdlib, user, true));
                  }
                  break;
                }
              }
              tdlib.ui().post(() -> {
                inlineBots = inlineBotResults;
                if (currentText.equals(inlineBotsAwaitText)) {
                  onQueryResultsChanged(currentText);
                }
              });
            }
          });
        }
        return;
      }

      for (InlineResult<?> inlineBot : inlineBots) {
        if (prefix.isEmpty() || ((InlineResultMention) inlineBot).matchesPrefix(prefix, false)) {
          inlineBot.setTarget(startIndex, endIndex);
          results.add(inlineBot);
        }
      }
    }

    TdApi.Chat chat = callback.provideInlineSearchChat();
    if (/*needInlineBots && */chat != null && TD.isChannel(chat.type)) {
      if (results.isEmpty()) {
        hideResults();
      } else {
        showMentions(results);
      }
      return;
    }

    if (!results.isEmpty()) {
      showMentions(results);
    } else if (lastInlineResultsType != TYPE_MENTIONS) {
      hideResults();
    }

    final TdApi.Function<?> function;
    if (callback.provideInlineSearchChatUserId() != 0) {
      if (needInlineBots) {
        if (results.isEmpty()) {
          hideResults();
        }
        return;
      }
      function = new TdApi.SearchContacts(prefix, 50);
    } else {
      function = new TdApi.SearchChatMembers(callback.provideInlineSearchChatId(), prefix, 20, null);
    }

    tdlib.client().send(function, mentionSearchHandler = new CancellableResultHandler() {
      @Override
      public void processResult (TdApi.Object object) {
        ArrayList<InlineResult<?>> inlineBotsLocal = needInlineBots ? inlineBots : null;
        final ArrayList<InlineResult<?>> addedResults = new ArrayList<>();
        switch (object.getConstructor()) {
          case TdApi.Users.CONSTRUCTOR: {
            long[] userIds = ((TdApi.Users) object).userIds;
            if (userIds.length > 0) {
              ArrayList<TdApi.User> users = tdlib.cache().users(userIds);
              addedResults.ensureCapacity(userIds.length);
              for (TdApi.User user : users) {
                if (/*InlineResultMention.matchesPrefix(user, prefix, true) &&*/ indexOfMention(inlineBotsLocal, user.id) == -1) {
                  InlineResultMention mention = new InlineResultMention(context, tdlib, user, false);
                  mention.setTarget(startIndex, endIndex);
                  addedResults.add(mention);
                }
              }
            }
            break;
          }
          case TdApi.ChatMembers.CONSTRUCTOR: {
            TdApi.ChatMember[] members = ((TdApi.ChatMembers) object).members;
            if (members.length > 0) {
              addedResults.ensureCapacity(members.length);
              for (TdApi.ChatMember member : members) {
                if (member.memberId.getConstructor() != TdApi.MessageSenderUser.CONSTRUCTOR || indexOfMention(inlineBotsLocal, ((TdApi.MessageSenderUser) member.memberId).userId) != -1) {
                  continue;
                }
                TdApi.User user = tdlib.cache().user(((TdApi.MessageSenderUser) member.memberId).userId);
                InlineResultMention mention = new InlineResultMention(context, tdlib, user, false);
                mention.setTarget(startIndex, endIndex);
                addedResults.add(mention);
              }
            }
            break;
          }
          case TdApi.Error.CONSTRUCTOR: {
            Log.w("Cannot invoke %s: %s", function.toString(), TD.toErrorString(object));
            break;
          }
        }
        tdlib.ui().post(() -> {
          if (!isCancelled() && StringUtils.equalsOrBothEmpty(InlineSearchContext.this.currentText, currentText)) {
            if (addedResults.isEmpty()) {
              if (results.isEmpty()) {
                hideResults();
              }
            } else if (results.isEmpty()) {
              showMentions(addedResults);
            } else {
              results.ensureCapacity(results.size() + addedResults.size());
              results.addAll(addedResults);
              showMentions(results);
            }
          }
        });
      }
    });
  }

  // Commands

  private void searchCommands (String command) {
    if (commandListProvider != null) {
      ArrayList<InlineResult<?>> commands = commandListProvider.searchCommands(command, currentText, changeListener);
      if (commands != null && !commands.isEmpty()) {
        showCommands(commands);
        return;
      }
    }
    hideResults();
  }

  // Hashtags

  private CancellableResultHandler hashtagHandler;

  private void cancelHashtagsSearchQuery () {
    if (hashtagHandler != null) {
      hashtagHandler.cancel();
      hashtagHandler = null;
    }
  }

  private void searchHashtags (final int startIndex, final int endIndex, final String currentText, final String hashtagPrefix) {
    if (lastInlineResultsType != TYPE_HASHTAGS) {
      hideResults();
    }

    tdlib.client().send(new TdApi.SearchHashtags(hashtagPrefix, 50), new CancellableResultHandler() {
      @Override
      public void processResult (TdApi.Object object) {
        switch (object.getConstructor()) {
          case TdApi.Hashtags.CONSTRUCTOR: {
            final String[] hashtags = ((TdApi.Hashtags) object).hashtags;
            final ArrayList<InlineResult<?>> inlineResults;
            if (hashtags.length != 0) {
              inlineResults = new ArrayList<>(hashtags.length);
              for (String hashtag : hashtags) {
                InlineResultHashtag result = new InlineResultHashtag(context, tdlib, hashtag, hashtagPrefix);
                result.setTarget(startIndex, endIndex);
                inlineResults.add(result);
              }
            } else {
              inlineResults = null;
            }
            tdlib.ui().post(() -> {
              if (!isCancelled() && StringUtils.equalsOrBothEmpty(InlineSearchContext.this.currentText, currentText)) {
                if (inlineResults == null || inlineResults.isEmpty()) {
                  hideResults();
                } else {
                  showHashtags(inlineResults);
                }
              }
            });
            break;
          }
        }
      }
    });
  }

  // Emoji

  private CancellableRunnable emojiHandler;

  private void cancelEmojiHandler () {
    if (emojiHandler != null) {
      emojiHandler.cancel();
      emojiHandler = null;
    }
  }

  private static String toLanguageCode (InputMethodSubtype ims) {
    if (ims != null) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        String languageTag = ims.getLanguageTag();
        if (!StringUtils.isEmpty(languageTag)) {
          return languageTag;
        }
      }
      String locale = ims.getLocale();
      if (!StringUtils.isEmpty(locale)) {
        Locale l = U.getDisplayLocaleOfSubtypeLocale(locale);
        if (l != null) {
          return LocaleUtils.toBcp47Language(l);
        }
      }
    }
    return null;
  }

  private void searchEmoji (final int startIndex, final int endIndex, final String currentInput, final String suggestionQuery) {
    if (lastInlineResultsType != TYPE_EMOJI_SUGGESTIONS) {
      hideResults();
    }

    final List<String> inputLanguages = new ArrayList<>();
    InputMethodManager imm = (InputMethodManager) UI.getAppContext().getSystemService(Context.INPUT_METHOD_SERVICE);
    if (imm != null) {
      String inputLanguageCode = null;
      try {
        inputLanguageCode = toLanguageCode(imm.getCurrentInputMethodSubtype());
      } catch (Throwable ignored) { }
      if (StringUtils.isEmpty(inputLanguageCode)) {
        try {
          inputLanguageCode = toLanguageCode(imm.getLastInputMethodSubtype());
        } catch (Throwable ignored) { }
      }
      if (!StringUtils.isEmpty(inputLanguageCode)) {
        inputLanguages.add(inputLanguageCode);
      }

      /*if (Strings.isEmpty(inputLanguageCode)) {
        try {
          String id = android.provider.Settings.Secure.getString(
            UI.getAppContext().getContentResolver(),
            android.provider.Settings.Secure.DEFAULT_INPUT_METHOD
          );
          if (!Strings.isEmpty(id)) {
            List<InputMethodInfo> list = imm.getInputMethodList();
            lookup:
            for (InputMethodInfo info : list) {
              if (id.equals(info.getId())) {
                List<InputMethodSubtype> subtypes = imm.getEnabledInputMethodSubtypeList(info, true);
                for (InputMethodSubtype subtype : subtypes) {
                  String languageCode = toLanguageCode(subtype);
                  if (!Strings.isEmpty(languageCode)) {
                    inputLanguageCode = languageCode;
                    break lookup;
                  }
                }
              }
            }
          }
        } catch (Throwable ignored) { }
      }
      if (Strings.isEmpty(inputLanguageCode) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        try {
          LocaleList localeList = ((InputView) callback).getImeHintLocales();
          if (localeList != null) {
            for (int i = 0; i < localeList.size(); i++) {
              inputLanguageCode = U.toBcp47Language(localeList.get(i));
              if (!Strings.isEmpty(inputLanguageCode))
                break;
            }
          }
        } catch (Throwable ignored) { }
      }*/
    }
    if (inputLanguages.isEmpty()) {
      try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
          LocaleList locales = Resources.getSystem().getConfiguration().getLocales();
          for (int i = 0; i < locales.size(); i++) {
            String code = LocaleUtils.toBcp47Language(locales.get(i));
            if (!StringUtils.isEmpty(code) && !inputLanguages.contains(code))
              inputLanguages.add(code);
          }
        } else {
          String code = LocaleUtils.toBcp47Language(Resources.getSystem().getConfiguration().locale);
          if (!StringUtils.isEmpty(code)) {
            inputLanguages.add(code);
          }
        }
      } catch (Throwable ignored) { }
    }

    Background.instance().post(new CancellableRunnable() {
      @Override
      public void act () {
        String query = suggestionQuery.toLowerCase();
        if (suggestionQuery.length() == 1 && Character.isUpperCase(suggestionQuery.charAt(0))) {
          char c = suggestionQuery.charAt(0);
          switch (c) {
            case 'P': // :P
            case 'D': // :D
            case 'S': // :S
            case 'O': // :O
              query = null;
            break;
          }
        }
        final ArrayList<InlineResult<?>> inlineResults;
        if (StringUtils.isEmpty(query)) {
          inlineResults = null;
        } else {
          N.Suggestion[] suggestions;
          int maxLength = N.getEmojiSuggestionMaxLength();
          suggestions = query.length() < maxLength ? N.getEmojiSuggestions(query) : null;
          if (suggestions != null && suggestions.length > 0) {
            inlineResults = new ArrayList<>(suggestions.length);
            for (N.Suggestion suggestion : suggestions) {
              inlineResults.add(new InlineResultEmojiSuggestion(context, tdlib, suggestion, suggestionQuery).setTarget(startIndex, endIndex));
            }
          } else {
            inlineResults = null;
          }
        }

        if (!StringUtils.isEmpty(query)) {
          tdlib.client().send(new TdApi.SearchEmojis(query, false, inputLanguages.isEmpty() ? null : inputLanguages.toArray(new String[0])), result -> {
            if (result.getConstructor() == TdApi.Emojis.CONSTRUCTOR) {
              TdApi.Emojis emojis = (TdApi.Emojis) result;
              ArrayList<InlineResult<?>> addedResults = new ArrayList<>(emojis.emojis.length);
              for (String emoji : emojis.emojis) {
                boolean found = false;
                if (inlineResults != null) {
                  for (InlineResult<?> existingResult : inlineResults) {
                    if (existingResult instanceof InlineResultEmojiSuggestion && Emoji.equals(emoji, ((InlineResultEmojiSuggestion) existingResult).getEmoji())) {
                      found = true;
                      break;
                    }
                  }
                }
                if (!found) {
                  addedResults.add(new InlineResultEmojiSuggestion(context, tdlib, new N.Suggestion(emoji, null, null), null).setTarget(startIndex, endIndex));
                }
              }
              if (!addedResults.isEmpty()) {
                tdlib.ui().post(() -> {
                  if (isPending() && StringUtils.equalsOrBothEmpty(currentInput, currentText)) {
                    if (inlineResults == null || inlineResults.isEmpty()) {
                      showEmojiSuggestions(addedResults);
                    } else {
                      inlineResults.addAll(addedResults);
                      showEmojiSuggestions(inlineResults);
                    }
                  }
                });
              }
            }
          });
        }

        tdlib.ui().post(() -> {
          if (isPending() && StringUtils.equalsOrBothEmpty(currentInput, currentText)) {
            if (inlineResults == null || inlineResults.isEmpty()) {
              hideResults();
            } else {
              showEmojiSuggestions(inlineResults);
            }
          }
        });

      }
    });
  }

  // Links processor

  private final ArrayList<String> lastLinks = new ArrayList<>(5);
  private final ArrayList<String> currentLinks = new ArrayList<>(5);
  private int linkContextId;

  private void processLinkPreview (String input) {
    currentLinks.clear();

    if (!StringUtils.isEmpty(input)) {
      List<String> links = TD.findUrls(callback.getOutputText(true));
      if (links != null && !links.isEmpty()) {
        currentLinks.addAll(links);
      }
    }

    int size = currentLinks.size();
    if (lastLinks.size() == size) {
      if (size == 0) {
        return;
      }
      boolean changed = false;
      int j = 0;
      for (String str : lastLinks) {
        if (!currentLinks.get(j++).equals(str)) {
          changed = true;
          break;
        }
      }
      if (!changed) {
        return;
      }
    }

    lastLinks.clear();
    lastLinks.addAll(currentLinks);

    final int contextId = ++linkContextId;
    if (size == 0 || !callback.needsLinkPreview()) {
      closeLinkPreview();
    } else {
      processLinkPreview(contextId, lastLinks.get(0));
    }
  }

  public final void processLinkPreview (final int contextId, final String link) {
    int result = WARNING_BLOCK;
    if (!StringUtils.isEmpty(link)) {
      result = callback.showLinkPreviewWarning(contextId, link);
    }
    if (result != WARNING_CONFIRM && callback.showLinkPreview(link, null)) {
      switch (result) {
        case WARNING_BLOCK: {
          dispatchLinkPreview(contextId, null, null);
          break;
        }
        case WARNING_OK: {
          TdApi.WebPage webPage = callback.provideExistingWebPage(callback.getOutputText(false));
          if (webPage != null) {
            dispatchLinkPreview(contextId, link, webPage);
            return;
          }
          UI.post(() -> {
            if (linkContextId == contextId) {
              tdlib.client().send(new TdApi.GetWebPagePreview(callback.getOutputText(true)), object -> {
                switch (object.getConstructor()) {
                  case TdApi.WebPage.CONSTRUCTOR: {
                    dispatchLinkPreview(contextId, link, (TdApi.WebPage) object);
                    break;
                  }
                  case TdApi.Error.CONSTRUCTOR: {
                    TdApi.Error error = (TdApi.Error) object;
                    if (error.code != 404) { // 404 is "Web page is empty". Maybe something interesting
                      Log.w("Cannot load link preview: %s", TD.toErrorString(object));
                    }
                    dispatchLinkPreview(contextId, null, null);
                    break;
                  }
                  default: {
                    Log.unexpectedTdlibResponse(object, TdApi.GetWebPagePreview.class, TdApi.WebPage.class);
                    break;
                  }
                }
              });
            }
          }, 400l);
          break;
        }
      }
    }
  }

  private void dispatchLinkPreview (final int contextId, final @Nullable String link, final @Nullable TdApi.WebPage webPage) {
    UI.post(() -> {
      if (linkContextId == contextId) {
        callback.showLinkPreview(link, webPage);
      }
    });
  }

  private void closeLinkPreview () {
    callback.showLinkPreview(null, null);
  }
}
