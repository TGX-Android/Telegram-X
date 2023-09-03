package org.thunderdog.challegram.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.Settings;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class StickerSuggestionsProvider {
  private final Tdlib tdlib;
  private CancellableResultHandler suggestionsHandler;

  private String suggestionsEmoji;
  private TdApi.StickerType suggestionsType;
  private Callback suggestionsCallback;
  private long chatId;

  private TdApi.Stickers suggestionsFromLocal;
  private TdApi.Stickers suggestionsFromServer;
  private Set<Long> ignoredStickerIds;

  public interface Callback {
    default void onResultPart (TdApi.Stickers stickers, TdApi.StickerType type, boolean isLocal) {}
    default void onResultFull (Result result) {}
  }

  public StickerSuggestionsProvider (Tdlib tdlib) {
    this.tdlib = tdlib;
  }

  @UiThread
  public void getSuggestions (String emoji, TdApi.StickerType type, long chatId, Callback callback) {
    cancel();

    this.chatId = chatId;
    this.suggestionsEmoji = emoji;
    this.suggestionsFromLocal = null;
    this.suggestionsFromServer = null;
    this.suggestionsType = type;
    this.ignoredStickerIds = new HashSet<>();
    this.suggestionsCallback = callback;

    getSuggestionsImpl(true);
  }

  @UiThread
  public void cancel () {
    if (suggestionsHandler != null) {
      suggestionsHandler.cancel();
      suggestionsHandler = null;
      suggestionsCallback = null;
    }
  }

  @UiThread
  public void getSuggestionsImpl (boolean isLocal) {
    final int stickerMode = getSearchStickersMode(suggestionsType);
    if (stickerMode == Settings.STICKER_MODE_NONE) {
      return;
    }

    if (stickerMode == Settings.STICKER_MODE_ALL && isLocal && tdlib.suggestOnlyApiStickers()) {
      getSuggestionsImpl(false);
      return;
    }

    TdApi.Function<?> function = isLocal ?
      new TdApi.GetStickers(suggestionsType, suggestionsEmoji, 1000, chatId):
      new TdApi.SearchStickers(suggestionsType, suggestionsEmoji, 1000);

    tdlib.client().send(function, suggestionsHandler = suggestionsHandler(isLocal));
  }

  private CancellableResultHandler suggestionsHandler (final boolean isLocal) {
    return new CancellableResultHandler() {
      @Override
      public void processResult (TdApi.Object object) {
        if (object.getConstructor() != TdApi.Stickers.CONSTRUCTOR) {
          return;
        }
        UI.post(() -> applyStickerSuggestions((TdApi.Stickers) object, isLocal));
      }
    };
  }

  @UiThread
  private void applyStickerSuggestions (TdApi.Stickers stickers, boolean isLocal) {
    ArrayList<TdApi.Sticker> stickersToAdd = new ArrayList<>(stickers.stickers.length);
    for (TdApi.Sticker sticker: stickers.stickers) {
      if (ignoredStickerIds.contains(sticker.id)) {
        continue;
      }
      stickersToAdd.add(sticker);
      ignoredStickerIds.add(sticker.id);
    }

    TdApi.Stickers result = new TdApi.Stickers(stickersToAdd.toArray(new TdApi.Sticker[0]));
    suggestionsCallback.onResultPart(result, suggestionsType, isLocal);

    boolean isFinish = !isLocal || getSearchStickersMode(suggestionsType) != Settings.STICKER_MODE_ALL;
    if (isLocal) {
      suggestionsFromLocal = result;
    } else {
      suggestionsFromServer = result;
    }
    if (isFinish) {
      suggestionsCallback.onResultFull(new Result(suggestionsFromLocal, suggestionsFromServer, suggestionsType));
    } else {
      getSuggestionsImpl(false);
    }
  }

  private static int getSearchStickersMode (TdApi.StickerType type) {
    if (type.getConstructor() == TdApi.StickerTypeCustomEmoji.CONSTRUCTOR) {
      return Settings.instance().getNewSetting(Settings.SETTING_FLAG_NO_SUGGEST_ANIMATED_EMOJI) ?
        Settings.STICKER_MODE_NONE: Settings.STICKER_MODE_ALL;
    } else {
      return Settings.instance().getStickerMode();
    }
  }

  public static class Result {
    public final @NonNull TdApi.Stickers stickersFromLocal;
    public final @NonNull TdApi.Stickers stickersFromServer;
    public final @NonNull TdApi.StickerType type;

    public Result (@Nullable TdApi.Stickers stickersFromLocal, @Nullable TdApi.Stickers stickersFromServer, @NonNull TdApi.StickerType type) {
      this.stickersFromLocal = stickersFromLocal != null ? stickersFromLocal: new TdApi.Stickers(new TdApi.Sticker[0]);
      this.stickersFromServer = stickersFromServer != null ? stickersFromServer: new TdApi.Stickers(new TdApi.Sticker[0]);
      this.type = type;
    }

    public boolean isEmpty () {
      return size() == 0;
    }

    public int size () {
      return stickersFromLocal.stickers.length + stickersFromServer.stickers.length;
    }
  }
}
